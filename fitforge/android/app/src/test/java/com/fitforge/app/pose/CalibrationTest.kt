package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationTest {
    @Test
    fun `deriveCalibration succeeds for a full range-of-motion demo rep`() {
        val demoAngles = (170 downTo 90 step 5).map { it.toDouble() }
        val result = deriveCalibration(
            "bodyweight-squat",
            restAngleDeg = 170.0,
            demoRepAngles = demoAngles,
            nowMs = 1000,
        )
        assertTrue(result is CalibrationResult.Success)
        val profile = (result as CalibrationResult.Success).profile
        assertEquals(170.0, profile.restAngleDeg, 0.01)
        assertEquals(90.0, profile.peakAngleDeg, 0.01)
        assertEquals(1000, profile.calibratedAtMs)
    }

    @Test
    fun `deriveCalibration rejects a shallow demo rep`() {
        val demoAngles = (170 downTo 155 step 5).map { it.toDouble() } // only 15deg of swing
        val result = deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepAngles = demoAngles, nowMs = 0)
        assertTrue(result is CalibrationResult.InsufficientRange)
        assertEquals(15.0, (result as CalibrationResult.InsufficientRange).achievedDeg, 0.01)
    }

    @Test
    fun `deriveCalibration rejects an empty demo rep`() {
        val result = deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepAngles = emptyList(), nowMs = 0)
        assertTrue(result is CalibrationResult.InsufficientRange)
    }

    @Test
    fun `averageRestAngle averages the representative angle across a hold-still window`() {
        val samples = listOf(
            AngleSample(0, 168.0, null),
            AngleSample(33, 170.0, null),
            AngleSample(66, 172.0, null),
        )
        assertEquals(170.0, averageRestAngle(samples)!!, 0.01)
    }

    @Test
    fun `averageRestAngle is null when nothing was visible`() {
        val samples = listOf(AngleSample(0, null, null), AngleSample(33, null, null))
        assertNull(averageRestAngle(samples))
    }
}
