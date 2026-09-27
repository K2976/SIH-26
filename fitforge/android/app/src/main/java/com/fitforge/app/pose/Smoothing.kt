@file:Suppress("ReturnCount")

package com.fitforge.app.pose

import kotlin.math.abs

/**
 * Raw landmark angles jitter frame to frame; an un-smoothed signal crosses
 * rep thresholds spuriously. Exponential moving average, applied per side
 * before anything touches the state machine.
 *
 * ponytail: EMA, not a windowed filter — one multiply-add per sample, no
 * buffer to size. Upgrade to a median/Kalman filter only if a real device
 * trace shows EMA lag causing missed reps at fast tempo.
 */
private const val SMOOTHING_ALPHA = 0.35
private const val ADAPTIVE_MIN_ALPHA = 0.25
private const val ADAPTIVE_MAX_ALPHA = 0.65
private const val VELOCITY_SCALE = 0.002 // scales deg/sec into dynamic alpha boost

fun smoothAngleSample(previous: AngleSample?, raw: AngleSample, alpha: Double = SMOOTHING_ALPHA): AngleSample =
    AngleSample(
        timestampMs = raw.timestampMs,
        leftDeg = emaStep(previous?.leftDeg, raw.leftDeg, alpha),
        rightDeg = emaStep(previous?.rightDeg, raw.rightDeg, alpha),
    )

/**
 * Velocity-adaptive smoothing: scales alpha dynamically based on joint angular
 * velocity (deg/sec). When stationary or moving slowly, uses heavy smoothing
 * (alpha ~ 0.25) to reject jitter. During explosive reversals or rapid reps,
 * increases alpha (up to 0.65) to eliminate phase lag and missed peak detection.
 */
fun adaptiveSmoothAngleSample(previous: AngleSample?, raw: AngleSample): AngleSample {
    if (previous == null) return raw
    val dtSec = (raw.timestampMs - previous.timestampMs).coerceAtLeast(1) / 1000.0

    val leftAlpha = dynamicAlpha(previous.leftDeg, raw.leftDeg, dtSec)
    val rightAlpha = dynamicAlpha(previous.rightDeg, raw.rightDeg, dtSec)

    return AngleSample(
        timestampMs = raw.timestampMs,
        leftDeg = emaStep(previous.leftDeg, raw.leftDeg, leftAlpha),
        rightDeg = emaStep(previous.rightDeg, raw.rightDeg, rightAlpha),
    )
}

private fun dynamicAlpha(prevDeg: Double?, currDeg: Double?, dtSec: Double): Double {
    if (prevDeg == null || currDeg == null || dtSec <= 0.0) return SMOOTHING_ALPHA
    val velocityDegPerSec = abs(currDeg - prevDeg) / dtSec
    val alphaBoost = velocityDegPerSec * VELOCITY_SCALE
    return (ADAPTIVE_MIN_ALPHA + alphaBoost).coerceIn(ADAPTIVE_MIN_ALPHA, ADAPTIVE_MAX_ALPHA)
}

private fun emaStep(previous: Double?, current: Double?, alpha: Double): Double? {
    if (current == null) return null
    if (previous == null) return current
    return alpha * current + (1 - alpha) * previous
}

