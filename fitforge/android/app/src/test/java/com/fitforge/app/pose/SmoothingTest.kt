package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothingTest {

    @Test
    fun `smoothAngleSample returns raw on first frame when previous is null`() {
        val raw = AngleSample(timestampMs = 1000L, leftDeg = 90.0, rightDeg = 92.0)
        val smoothed = smoothAngleSample(previous = null, raw = raw)

        assertEquals(1000L, smoothed.timestampMs)
        assertEquals(90.0, smoothed.leftDeg!!, 0.001)
        assertEquals(92.0, smoothed.rightDeg!!, 0.001)
    }

    @Test
    fun `smoothAngleSample preserves null for unresolvable side`() {
        val prev = AngleSample(timestampMs = 1000L, leftDeg = 90.0, rightDeg = null)
        val raw = AngleSample(timestampMs = 1033L, leftDeg = 95.0, rightDeg = null)
        val smoothed = smoothAngleSample(previous = prev, raw = raw, alpha = 0.35)

        assertNotNull(smoothed.leftDeg)
        assertNull(smoothed.rightDeg)
    }

    @Test
    fun `smoothAngleSample dampens instantaneous single-frame spike`() {
        val prev = AngleSample(timestampMs = 1000L, leftDeg = 170.0, rightDeg = 170.0)
        val spike = AngleSample(timestampMs = 1033L, leftDeg = 135.0, rightDeg = 135.0)
        val smoothed = smoothAngleSample(previous = prev, raw = spike, alpha = 0.35)

        // 0.35 * 135 + 0.65 * 170 = 47.25 + 110.5 = 157.75
        assertEquals(157.75, smoothed.leftDeg!!, 0.01)
        assertEquals(157.75, smoothed.rightDeg!!, 0.01)
    }

    @Test
    fun `adaptiveSmoothAngleSample applies higher alpha on fast movements`() {
        val prev = AngleSample(timestampMs = 1000L, leftDeg = 170.0, rightDeg = 170.0)
        // 50 degree jump in 33ms = ~1515 deg/sec (high explosive velocity)
        val fastRaw = AngleSample(timestampMs = 1033L, leftDeg = 120.0, rightDeg = 120.0)

        val adaptive = adaptiveSmoothAngleSample(previous = prev, raw = fastRaw)
        val standard = smoothAngleSample(previous = prev, raw = fastRaw, alpha = 0.35)

        // Adaptive smoothing should react faster (closer to 120.0) than standard alpha 0.35
        assertTrue(adaptive.leftDeg!! < standard.leftDeg!!)
    }

    @Test
    fun `adaptiveSmoothAngleSample applies lower alpha when near stationary`() {
        val prev = AngleSample(timestampMs = 1000L, leftDeg = 90.0, rightDeg = 90.0)
        // 0.5 degree drift in 33ms = ~15 deg/sec (near stationary micro-jitter)
        val slowRaw = AngleSample(timestampMs = 1033L, leftDeg = 90.5, rightDeg = 90.5)

        val adaptive = adaptiveSmoothAngleSample(previous = prev, raw = slowRaw)
        val standard = smoothAngleSample(previous = prev, raw = slowRaw, alpha = 0.35)

        // Under near-stationary jitter, adaptive alpha (~0.25) dampens more heavily
        // than standard (0.35), so it stays closer to 90.0.
        val adaptiveDelta = kotlin.math.abs(adaptive.leftDeg!! - 90.0)
        val standardDelta = kotlin.math.abs(standard.leftDeg!! - 90.0)
        assertTrue(adaptiveDelta <= standardDelta)
    }
}
