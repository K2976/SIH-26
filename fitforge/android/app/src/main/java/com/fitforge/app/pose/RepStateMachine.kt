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

/**
 * Average of whichever side(s) are visible this frame; null if neither is.
 * Calibration-time use only — see [agreementProgress] for why live counting
 * never averages.
 */
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

// A phase transition (arming out of rest, or a peak/trough reversal) must be
// backed by real motion, not noise that happened to cross a band. Expressed
// as normalized progress-per-second, so it scales with each exercise's own
// calibrated range instead of a fixed degree count. Deliberately low — see
// DECISIONS.md — a deliberately slow rep must still clear it comfortably.
private const val MIN_VELOCITY_PER_SEC = 0.05

// A single dropped/occluded frame mid-rep is normal camera noise and must not
// nuke an in-progress rep; only a sustained loss does. See DECISIONS.md.
private const val CONFIDENCE_LOSS_TOLERANCE_MS = 500

enum class RejectionReason {
    INSUFFICIENT_DEPTH,
    TOO_FAST,
    TOO_SLOW,
    LOST_TRACKING,
}

/**
 * Both-joint agreement, in place of silently averaging left and right: a rep
 * only counts what BOTH visible sides agree happened. [forDepth] is the
 * shallower (lower-progress) side — depth is graded on whichever joint went
 * *least* deep, since a rep isn't deep unless every tracked joint got there.
 * [forLockout] is the more-bent (higher-progress) side — lockout is graded on
 * whichever joint is *still least* returned to rest. Concretely: one knee
 * locking out while the other doesn't means the rep is stricter-joint-gated,
 * not averaged into a phantom mid-range reading. This is also what stops a
 * single noisy/occluded side from manufacturing motion a still lifter never
 * made — the genuinely-still side caps both signals near zero. See
 * DECISIONS.md for the full write-up of this rule.
 */
private fun agreementProgress(sample: AngleSample, calibration: CalibrationProfile): Pair<Double, Double>? {
    val left = sample.leftDeg?.let { calibrationProgress(it, calibration) }
    val right = sample.rightDeg?.let { calibrationProgress(it, calibration) }
    val values = listOfNotNull(left, right)
    if (values.isEmpty()) return null
    return (values.min()) to (values.max())
}

/**
 * Normalized-progress-per-second between this sample and whatever was last
 * tracked; unbounded (no gate) when there's no prior sample to compare against.
 */
private fun velocityPerSec(state: RepMachineState, progress: Double, timestampMs: Long): Double {
    val lastProgress = state.lastProgress ?: return Double.MAX_VALUE
    val lastMs = state.lastProgressMs ?: return Double.MAX_VALUE
    val dtSec = (timestampMs - lastMs) / 1000.0
    if (dtSec <= 0.0) return Double.MAX_VALUE
    return abs(progress - lastProgress) / dtSec
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
    /** Progress/timestamp last fed to the velocity gate — not necessarily the running peak or trough. */
    val lastProgress: Double? = null,
    val lastProgressMs: Long? = null,
    /** First timestamp of the current unbroken run of unusable (both-sides-unresolvable) frames, mid-rep. */
    val unresolvedSinceMs: Long? = null,
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

    val agreement = agreementProgress(sample, calibration) ?: return handleUnresolvedFrame(state, sample.timestampMs)
    val (forDepth, forLockout) = agreement
    // Descending (and arming out of rest) is graded on the shallower side; ascending is
    // graded on the more-bent side — see agreementProgress's doc comment.
    val tracked = if (state.phase == Phase.ASCENDING) forLockout else forDepth
    val velocity = velocityPerSec(state, tracked, sample.timestampMs)
    fun carryForward(next: RepMachineState) =
        next.copy(lastProgress = tracked, lastProgressMs = sample.timestampMs, unresolvedSinceMs = null)

    return when (state.phase) {
        Phase.RESTING ->
            if (forDepth > RESTING_BAND && velocity >= MIN_VELOCITY_PER_SEC) {
                val next = RepMachineState(
                    phase = Phase.DESCENDING,
                    excursionStartMs = sample.timestampMs,
                    peakProgress = forDepth,
                    peakLeftDeg = sample.leftDeg,
                    peakRightDeg = sample.rightDeg,
                )
                StepResult(carryForward(next), null)
            } else {
                StepResult(carryForward(state), null)
            }

        Phase.DESCENDING -> when {
            forDepth > state.peakProgress ->
                StepResult(
                    carryForward(
                        state.copy(
                            peakProgress = forDepth,
                            peakLeftDeg = sample.leftDeg,
                            peakRightDeg = sample.rightDeg,
                        ),
                    ),
                    null,
                )

            forDepth < state.peakProgress - MOVEMENT_EPS && velocity >= MIN_VELOCITY_PER_SEC ->
                StepResult(
                    carryForward(
                        state.copy(
                            phase = Phase.ASCENDING,
                            troughProgress = forLockout,
                            troughMs = sample.timestampMs,
                            troughStableSinceMs = sample.timestampMs,
                        ),
                    ),
                    null,
                )

            else -> StepResult(carryForward(state), null)
        }

        Phase.ASCENDING -> when {
            forLockout < state.troughProgress ->
                StepResult(
                    carryForward(
                        state.copy(
                            troughProgress = forLockout,
                            troughMs = sample.timestampMs,
                            troughStableSinceMs = sample.timestampMs,
                        ),
                    ),
                    null,
                )

            forLockout > state.troughProgress + MOVEMENT_EPS && velocity >= MIN_VELOCITY_PER_SEC -> {
                // Reversed back downward before settling: the previous rep ended at its
                // trough; this sample is already the start of the next descent.
                val event = closeRep(state, config, state.troughMs ?: sample.timestampMs)
                val next = RepMachineState(
                    phase = Phase.DESCENDING,
                    excursionStartMs = state.troughMs ?: sample.timestampMs,
                    peakProgress = forDepth,
                    peakLeftDeg = sample.leftDeg,
                    peakRightDeg = sample.rightDeg,
                )
                StepResult(carryForward(next), event)
            }

            forLockout <= RESTING_BAND &&
                state.troughStableSinceMs != null &&
                sample.timestampMs - state.troughStableSinceMs >= SETTLE_MS -> {
                val event = closeRep(state, config, state.troughMs ?: sample.timestampMs)
                StepResult(RepMachineState(), event)
            }

            else -> StepResult(carryForward(state), null)
        }
    }
}

/**
 * A frame where neither side's angle could be resolved (occlusion, low
 * confidence, or nothing detected). While resting there's no rep to lose, so
 * it's a no-op. Mid-rep, a single dropped frame is normal camera noise and is
 * tolerated; a sustained loss past [CONFIDENCE_LOSS_TOLERANCE_MS] rejects the
 * in-progress rep with [RejectionReason.LOST_TRACKING] instead of either
 * silently freezing forever or guessing through the gap.
 */
private fun handleUnresolvedFrame(state: RepMachineState, timestampMs: Long): StepResult {
    if (state.phase == Phase.RESTING) return StepResult(state, null)
    val start = state.excursionStartMs ?: return StepResult(state, null)
    val unresolvedSince = state.unresolvedSinceMs ?: timestampMs
    return if (timestampMs - unresolvedSince >= CONFIDENCE_LOSS_TOLERANCE_MS) {
        val rejected = RejectedRep(start, timestampMs, RejectionReason.LOST_TRACKING)
        StepResult(RepMachineState(), RepEvent.Rejected(rejected))
    } else {
        StepResult(state.copy(unresolvedSinceMs = unresolvedSince), null)
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
