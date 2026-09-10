package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun samplesAt(vararg degrees: Double): List<AngleSample> =
    degrees.mapIndexed { i, deg -> AngleSample(i * 33L, deg, null) }

class CalibrationTest {
    @Test
    fun `deriveCalibration succeeds for a full range-of-motion demo rep`() {
        val demoAngles = samplesAt(*(170 downTo 90 step 5).map { it.toDouble() }.toDoubleArray())
        val result = deriveCalibration(
            "bodyweight-squat",
            restAngleDeg = 170.0,
            demoRepSamples = demoAngles,
            nowMs = 1000,
        )
        assertTrue(result is CalibrationResult.Success)
        val profile = (result as CalibrationResult.Success).profile
        assertEquals(170.0, profile.restAngleDeg, 0.01)
        assertEquals(90.0, profile.peakAngleDeg, 0.01)
        assertEquals(1000, profile.calibratedAtMs)
        assertTrue("a derived profile is always a real user calibration", profile.isUserCalibrated)
    }

    @Test
    fun `deriveCalibration rejects a shallow demo rep`() {
        val demoAngles = samplesAt(*(170 downTo 155 step 5).map { it.toDouble() }.toDoubleArray()) // 15deg of swing
        val result = deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepSamples = demoAngles, nowMs = 0)
        assertTrue(result is CalibrationResult.InsufficientRange)
        assertEquals(15.0, (result as CalibrationResult.InsufficientRange).achievedDeg, 0.01)
    }

    @Test
    fun `deriveCalibration refuses an empty demo rep as low confidence, not insufficient range`() {
        // Zero frames is a confidence problem (nothing to measure), not a range-of-motion one.
        val result =
            deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepSamples = emptyList(), nowMs = 0)
        assertTrue(result is CalibrationResult.LowConfidence)
        assertEquals(0.0, (result as CalibrationResult.LowConfidence).visibleFraction, 0.01)
    }

    @Test
    fun `deriveCalibration refuses to calibrate when confidence is low throughout`() {
        // Only 2 of 10 frames have a usable angle — below MIN_VISIBLE_FRAME_RATIO.
        val samples = listOf(
            AngleSample(0, 170.0, null),
            AngleSample(33, null, null),
            AngleSample(66, null, null),
            AngleSample(99, null, null),
            AngleSample(132, null, null),
            AngleSample(165, 90.0, null),
            AngleSample(198, null, null),
            AngleSample(231, null, null),
            AngleSample(264, null, null),
            AngleSample(297, null, null),
        )
        val result = deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepSamples = samples, nowMs = 0)
        assertTrue(result is CalibrationResult.LowConfidence)
        assertEquals(0.2, (result as CalibrationResult.LowConfidence).visibleFraction, 0.01)
    }

    @Test
    fun `a real calibration visibly differs from the default for the same exercise`() {
        // This lifter's own range (knee only reaches 120deg) is nowhere near the generic default.
        val demoAngles = samplesAt(*(170 downTo 120 step 5).map { it.toDouble() }.toDoubleArray())
        val result =
            deriveCalibration("bodyweight-squat", restAngleDeg = 170.0, demoRepSamples = demoAngles, nowMs = 500)
        val calibrated = (result as CalibrationResult.Success).profile
        val default = defaultCalibration("bodyweight-squat", nowMs = 500)!!

        assertNotEquals(default.peakAngleDeg, calibrated.peakAngleDeg)
        assertTrue("a real calibration is marked as such", calibrated.isUserCalibrated)
        assertTrue("the default is marked as such", !default.isUserCalibrated)
    }

    @Test
    fun `resolveCalibration prefers the stored profile over the default`() {
        val stored = CalibrationProfile("bodyweight-squat", 170.0, 120.0, calibratedAtMs = 0)
        val resolved = resolveCalibration(stored, "bodyweight-squat", nowMs = 999)
        assertEquals(stored, resolved)
    }

    @Test
    fun `resolveCalibration falls back to the default only when nothing is stored`() {
        val resolved = resolveCalibration(null, "bodyweight-squat", nowMs = 999)
        assertEquals(defaultCalibration("bodyweight-squat", nowMs = 999), resolved)
    }

    @Test
    fun `resolveCalibration is null for an exercise with neither a stored nor a default profile`() {
        assertNull(resolveCalibration(null, "unicycle-juggling", nowMs = 0))
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
