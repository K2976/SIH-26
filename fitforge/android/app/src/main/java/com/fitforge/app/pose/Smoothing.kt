@file:Suppress("ReturnCount")

package com.fitforge.app.pose

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

fun smoothAngleSample(previous: AngleSample?, raw: AngleSample, alpha: Double = SMOOTHING_ALPHA): AngleSample =
    AngleSample(
        timestampMs = raw.timestampMs,
        leftDeg = emaStep(previous?.leftDeg, raw.leftDeg, alpha),
        rightDeg = emaStep(previous?.rightDeg, raw.rightDeg, alpha),
    )

private fun emaStep(previous: Double?, current: Double?, alpha: Double): Double? {
    if (current == null) return null
    if (previous == null) return current
    return alpha * current + (1 - alpha) * previous
}
