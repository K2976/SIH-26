@file:Suppress("MagicNumber", "ReturnCount")

package com.fitforge.app.pose

import kotlin.math.abs

/** Below this many degrees of swing between rest and the demo reps' extreme, calibration is untrustworthy. */
private const val MIN_RANGE_OF_MOTION_DEG = 20.0

/**
 * Below this fraction of demo-rep/hold-still frames having a usable angle,
 * confidence is too low to trust — refuse rather than calibrate against
 * mostly-missing data. Shared with [RepCounterViewModel] so the UI can
 * report the same number it was refused on.
 */
const val MIN_VISIBLE_FRAME_RATIO = 0.5

sealed class CalibrationResult {
    data class Success(val profile: CalibrationProfile) : CalibrationResult()

    /** The demo reps didn't move enough to trust — ask the user to repeat them, don't guess. */
    data class InsufficientRange(val achievedDeg: Double, val requiredDeg: Double) : CalibrationResult()

    /** Too few frames had a usable angle to trust the reading at all — ask the user to reposition, don't guess. */
    data class LowConfidence(val visibleFraction: Double) : CalibrationResult()
}

/** Fraction of [samples] with a usable (visible on at least one side) angle. */
fun visibleFraction(samples: List<AngleSample>): Double {
    if (samples.isEmpty()) return 0.0
    return samples.count { representativeAngle(it) != null }.toDouble() / samples.size
}

/** Mean of whichever side(s) were visible each frame — used for the pre-rep "hold still" rest reading. */
fun averageRestAngle(samples: List<AngleSample>): Double? {
    val values = samples.mapNotNull(::representativeAngle)
    return if (values.isEmpty()) null else values.average()
}

/**
 * Derives a user's personal top/bottom thresholds from a few supervised demo
 * reps: [restAngleDeg] is measured while they hold still before moving;
 * [demoRepSamples] is the smoothed angle series through every demo rep,
 * concatenated. The single point farthest from rest across all of them
 * becomes the calibrated peak — taking the best of 2-3 reps is more robust
 * than trusting whichever one rep a single-rep flow happened to catch. No
 * I/O, no clock reads — [nowMs] is passed in.
 *
 * Deliberately uses [representativeAngle] (the left/right average), not the
 * both-joint-agreement rule live counting uses — see DECISIONS.md. A
 * supervised demo rep is cooperative (both sides genuinely move together),
 * so the average is a reasonable summary of "this lifter's own range," not
 * the anti-cheat concern the agreement rule exists for during live counting.
 */
fun deriveCalibration(
    exerciseSlug: String,
    restAngleDeg: Double,
    demoRepSamples: List<AngleSample>,
    nowMs: Long,
): CalibrationResult {
    val confidence = visibleFraction(demoRepSamples)
    if (confidence < MIN_VISIBLE_FRAME_RATIO) {
        return CalibrationResult.LowConfidence(confidence)
    }

    val demoRepAngles = demoRepSamples.mapNotNull(::representativeAngle)
    val extreme = demoRepAngles.maxByOrNull { abs(it - restAngleDeg) }
        ?: return CalibrationResult.InsufficientRange(0.0, MIN_RANGE_OF_MOTION_DEG)

    val rangeOfMotion = abs(extreme - restAngleDeg)
    return if (rangeOfMotion < MIN_RANGE_OF_MOTION_DEG) {
        CalibrationResult.InsufficientRange(rangeOfMotion, MIN_RANGE_OF_MOTION_DEG)
    } else {
        CalibrationResult.Success(CalibrationProfile(exerciseSlug, restAngleDeg, extreme, nowMs))
    }
}

/**
 * Generic starting-point ranges for the exercises that support camera
 * counting, used only until a lifter completes their own calibration — see
 * DECISIONS.md for where these numbers came from (the same sanity bounds
 * recorded when the rep-counting state machine was fixed). Deliberately
 * generic, not measured for any one body; per-user calibration always wins
 * once it exists (see [resolveCalibration]).
 */
private val DEFAULT_CALIBRATION_DEG: Map<String, Pair<Double, Double>> = mapOf(
    "bodyweight-squat" to (170.0 to 95.0),
    "push-up" to (170.0 to 95.0),
    "dumbbell-bicep-curl" to (170.0 to 45.0),
)

/** The generic fallback profile for [exerciseSlug], or null if this exercise has no recorded default. */
fun defaultCalibration(exerciseSlug: String, nowMs: Long): CalibrationProfile? =
    DEFAULT_CALIBRATION_DEG[exerciseSlug]?.let { (rest, peak) ->
        CalibrationProfile(exerciseSlug, rest, peak, calibratedAtMs = nowMs, isUserCalibrated = false)
    }

/**
 * The read-path contract live counting relies on: the user's own calibration
 * when it exists, the generic default only when it doesn't. Never the other
 * way around, and never an average of the two.
 */
fun resolveCalibration(stored: CalibrationProfile?, exerciseSlug: String, nowMs: Long): CalibrationProfile? =
    stored ?: defaultCalibration(exerciseSlug, nowMs)
