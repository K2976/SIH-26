package com.fitforge.app.pose

import kotlin.math.abs

/** Below this many degrees of swing between rest and the demo rep's extreme, calibration is untrustworthy. */
private const val MIN_RANGE_OF_MOTION_DEG = 20.0

sealed class CalibrationResult {
    data class Success(val profile: CalibrationProfile) : CalibrationResult()

    /** The demo rep didn't move enough to trust — ask the user to repeat it, don't guess. */
    data class InsufficientRange(val achievedDeg: Double, val requiredDeg: Double) : CalibrationResult()
}

/** Mean of whichever side(s) were visible each frame — used for the pre-rep "hold still" rest reading. */
fun averageRestAngle(samples: List<AngleSample>): Double? {
    val values = samples.mapNotNull(::representativeAngle)
    return if (values.isEmpty()) null else values.average()
}

/**
 * Derives a user's personal top/bottom thresholds from one supervised demo
 * rep: [restAngleDeg] is measured while they hold still before moving;
 * [demoRepAngles] is the smoothed angle series through the rep itself. The
 * extreme point farthest from rest becomes the calibrated peak. No I/O, no
 * clock reads — [nowMs] is passed in.
 */
fun deriveCalibration(
    exerciseSlug: String,
    restAngleDeg: Double,
    demoRepAngles: List<Double>,
    nowMs: Long,
): CalibrationResult {
    val extreme = demoRepAngles.maxByOrNull { abs(it - restAngleDeg) }
        ?: return CalibrationResult.InsufficientRange(0.0, MIN_RANGE_OF_MOTION_DEG)

    val rangeOfMotion = abs(extreme - restAngleDeg)
    return if (rangeOfMotion < MIN_RANGE_OF_MOTION_DEG) {
        CalibrationResult.InsufficientRange(rangeOfMotion, MIN_RANGE_OF_MOTION_DEG)
    } else {
        CalibrationResult.Success(CalibrationProfile(exerciseSlug, restAngleDeg, extreme, nowMs))
    }
}
