@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ReturnCount")

package com.fitforge.app.pose

import kotlin.math.abs

/**
 * Progress from 0 (at [CalibrationProfile.restAngleDeg]) to 1 (at
 * [CalibrationProfile.peakAngleDeg]), regardless of whether the peak angle
 * is numerically larger or smaller than rest. Can go negative or past 1 if
 * the lifter overshoots calibration in either direction — callers clamp
 * where that matters.
 */
fun calibrationProgress(angleDeg: Double, calibration: CalibrationProfile): Double {
    val range = calibration.peakAngleDeg - calibration.restAngleDeg
    if (range == 0.0) return 0.0
    return (angleDeg - calibration.restAngleDeg) / range
}

/** Average of whichever side(s) are visible this frame; null if neither is. */
fun representativeAngle(sample: AngleSample): Double? = when {
    sample.leftDeg != null && sample.rightDeg != null -> (sample.leftDeg + sample.rightDeg) / 2.0
    sample.leftDeg != null -> sample.leftDeg
    sample.rightDeg != null -> sample.rightDeg
    else -> null
}

// A rep is a peak-then-valley in normalized progress, not a single threshold
// crossing: a threshold-crossing detector can't also grade *how close to
// true rest* the return got, because it always fires the instant progress
// dips under the threshold — so the "did they lock out" reading would just
// re-measure the same threshold and be trivially true or false by
// construction. Tracking the running peak (depth) and running trough
// (how far back toward rest they actually got) separately is what makes
// lockout a real, independent measurement.
private const val RESTING_BAND = 0.3
private const val DEPTH_MIN = 0.5
private const val DEPTH_FULL = 0.8
private const val LOCKOUT_FULL = 0.15

// Reversal must exceed this to flip descending/ascending — absorbs residual
// noise the smoothing pass didn't fully remove, so a flat-ish plateau at the
// bottom of a rep doesn't register as several tiny reps.
private const val MOVEMENT_EPS = 0.03

// If the lifter settles near rest and simply doesn't move again (end of a
// set, or a paused rep), there's no future reversal to close the rep on —
// so once the trough has held for this long, close eagerly.
private const val SETTLE_MS = 200

enum class RejectionReason {
    INSUFFICIENT_DEPTH,
    TOO_FAST,
    TOO_SLOW,
}

data class CompletedRep(
    val startMs: Long,
    val endMs: Long,
    val tempoMs: Long,
    val depthReached: Boolean,
    val lockoutReached: Boolean,
    /** Degrees of left/right deviation at the rep's deepest point; null when not assessable from this camera angle. */
    val symmetryDeviationDeg: Double?,
) {
    /** Maps onto `SetLog.rom_achieved` — the only two values this slice writes. */
    val romAchieved: String get() = if (depthReached && lockoutReached) "full" else "partial"
}

data class RejectedRep(val startMs: Long, val endMs: Long, val reason: RejectionReason)

sealed class RepEvent {
    data class Completed(val rep: CompletedRep) : RepEvent()
    data class Rejected(val rep: RejectedRep) : RepEvent()
}

enum class Phase { RESTING, DESCENDING, ASCENDING }

data class RepMachineState(
    val phase: Phase = Phase.RESTING,
    val excursionStartMs: Long? = null,
    val peakProgress: Double = 0.0,
    val peakLeftDeg: Double? = null,
    val peakRightDeg: Double? = null,
    val troughProgress: Double = 0.0,
    val troughMs: Long? = null,
    val troughStableSinceMs: Long? = null,
)

data class StepResult(val state: RepMachineState, val event: RepEvent?)

/**
 * One pure transition: given the current state and one new smoothed angle
 * sample, returns the next state and, when a rep just closed, the event for
 * it. No I/O, no clock reads — the caller supplies every timestamp, which is
 * what makes tempo deterministic in tests. This is the live-streaming
 * primitive; it never finalizes a still-open rep on its own (see
 * [finalizeRepStateMachine] for that — a live session doesn't know a rep is
 * "the last one" until the user says the set is over).
 */
fun stepRepStateMachine(
    state: RepMachineState,
    config: ExercisePoseConfig,
    calibration: CalibrationProfile,
    sample: AngleSample,
): StepResult {
    val start = state.excursionStartMs
    if (state.phase != Phase.RESTING && start != null) {
        val elapsedMs = sample.timestampMs - start
        if (elapsedMs > (config.maxRepSeconds * 1000).toLong()) {
            val rejected = RejectedRep(start, sample.timestampMs, RejectionReason.TOO_SLOW)
            return StepResult(RepMachineState(), RepEvent.Rejected(rejected))
        }
    }

    val angle = representativeAngle(sample) ?: return StepResult(state, null)
    val progress = calibrationProgress(angle, calibration)

    return when (state.phase) {
        Phase.RESTING ->
            if (progress > RESTING_BAND) {
                val next = RepMachineState(
                    phase = Phase.DESCENDING,
                    excursionStartMs = sample.timestampMs,
                    peakProgress = progress,
                    peakLeftDeg = sample.leftDeg,
                    peakRightDeg = sample.rightDeg,
                )
                StepResult(next, null)
            } else {
                StepResult(state, null)
            }

        Phase.DESCENDING -> when {
            progress > state.peakProgress ->
                StepResult(
                    state.copy(
                        peakProgress = progress,
                        peakLeftDeg = sample.leftDeg,
                        peakRightDeg = sample.rightDeg,
                    ),
                    null,
                )

            progress < state.peakProgress - MOVEMENT_EPS ->
                StepResult(
                    state.copy(
                        phase = Phase.ASCENDING,
                        troughProgress = progress,
                        troughMs = sample.timestampMs,
                        troughStableSinceMs = sample.timestampMs,
                    ),
                    null,
                )

            else -> StepResult(state, null)
        }

        Phase.ASCENDING -> when {
            progress < state.troughProgress ->
                StepResult(
                    state.copy(
                        troughProgress = progress,
                        troughMs = sample.timestampMs,
                        troughStableSinceMs = sample.timestampMs,
                    ),
                    null,
                )

            progress > state.troughProgress + MOVEMENT_EPS -> {
                // Reversed back downward before settling: the previous rep ended at its
                // trough; this sample is already the start of the next descent.
                val event = closeRep(state, config, state.troughMs ?: sample.timestampMs)
                val next = RepMachineState(
                    phase = Phase.DESCENDING,
                    excursionStartMs = state.troughMs ?: sample.timestampMs,
                    peakProgress = progress,
                    peakLeftDeg = sample.leftDeg,
                    peakRightDeg = sample.rightDeg,
                )
                StepResult(next, event)
            }

            progress <= RESTING_BAND &&
                state.troughStableSinceMs != null &&
                sample.timestampMs - state.troughStableSinceMs >= SETTLE_MS -> {
                val event = closeRep(state, config, state.troughMs ?: sample.timestampMs)
                StepResult(RepMachineState(), event)
            }

            else -> StepResult(state, null)
        }
    }
}

/**
 * Closes out whatever rep is still open — call this when the user ends the
 * set. Live streaming alone can't know the last rep is "done" (there's no
 * future reversal to detect it by), so this uses whatever peak/trough has
 * been tracked so far, as of right now.
 */
fun finalizeRepStateMachine(state: RepMachineState, config: ExercisePoseConfig): RepEvent? {
    val start = state.excursionStartMs ?: return null
    if (state.phase == Phase.RESTING) return null
    val endMs = if (state.phase == Phase.ASCENDING) state.troughMs ?: start else start
    return closeRep(state, config, endMs)
}

private fun closeRep(state: RepMachineState, config: ExercisePoseConfig, endMs: Long): RepEvent {
    val startMs = state.excursionStartMs ?: endMs
    val tempoMs = endMs - startMs
    return when {
        state.peakProgress < DEPTH_MIN ->
            RepEvent.Rejected(RejectedRep(startMs, endMs, RejectionReason.INSUFFICIENT_DEPTH))

        tempoMs < (config.minRepSeconds * 1000).toLong() ->
            RepEvent.Rejected(RejectedRep(startMs, endMs, RejectionReason.TOO_FAST))

        else -> {
            val symmetryDeviationDeg =
                if (
                    config.cameraPosition != CameraPosition.SIDE &&
                    state.peakLeftDeg != null &&
                    state.peakRightDeg != null
                ) {
                    abs(state.peakLeftDeg - state.peakRightDeg)
                } else {
                    null
                }
            RepEvent.Completed(
                CompletedRep(
                    startMs = startMs,
                    endMs = endMs,
                    tempoMs = tempoMs,
                    depthReached = state.peakProgress >= DEPTH_FULL,
                    lockoutReached = state.troughProgress <= LOCKOUT_FULL,
                    symmetryDeviationDeg = symmetryDeviationDeg,
                ),
            )
        }
    }
}

data class RepMachineResult(val completed: List<CompletedRep>, val rejected: List<RejectedRep>)

/**
 * Folds [stepRepStateMachine] over a full recorded sample sequence and
 * finalizes whatever's left open at the end — the shape a completed set's
 * worth of samples takes in tests and in post-hoc analysis. A live session
 * instead calls [stepRepStateMachine] per frame and [finalizeRepStateMachine]
 * once, when the user ends the set.
 */
fun runRepStateMachine(
    config: ExercisePoseConfig,
    calibration: CalibrationProfile,
    samples: List<AngleSample>,
): RepMachineResult {
    val completed = mutableListOf<CompletedRep>()
    val rejected = mutableListOf<RejectedRep>()
    var state = RepMachineState()

    fun collect(event: RepEvent?) {
        when (event) {
            is RepEvent.Completed -> completed += event.rep
            is RepEvent.Rejected -> rejected += event.rep
            null -> Unit
        }
    }

    for (sample in samples) {
        val result = stepRepStateMachine(state, config, calibration, sample)
        state = result.state
        collect(result.event)
    }
    collect(finalizeRepStateMachine(state, config))
    return RepMachineResult(completed, rejected)
}
