package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepStateMachineTest {
    @Test
    fun `three clean squat reps count through the full landmark pipeline`() {
        var timeMs = 0L
        val frames = mutableListOf<PoseFrame>()
        repeat(3) {
            val cycle = squatCycleFrames(startMs = timeMs)
            frames += cycle
            timeMs = cycle.last().timestampMs + 33
        }

        val result = runFramePipeline(SQUAT_CONFIG, SQUAT_CALIBRATION, frames)

        assertEquals(3, result.completed.size)
        assertTrue(result.rejected.isEmpty())
        result.completed.forEach { rep ->
            assertTrue(rep.depthReached)
            assertTrue(rep.lockoutReached)
            assertEquals("full", rep.romAchieved)
        }
    }

    @Test
    fun `a squat that never reaches depth is rejected, not counted`() {
        val samples = rampAngles(170.0, 140.0, startMs = 0, durationMs = 500) +
            rampAngles(140.0, 170.0, startMs = 500, durationMs = 500)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertTrue(result.completed.isEmpty())
        assertEquals(1, result.rejected.size)
        assertEquals(RejectionReason.INSUFFICIENT_DEPTH, result.rejected.single().reason)
    }

    @Test
    fun `a rep completed faster than physically plausible is rejected as too fast`() {
        val samples = rampAngles(170.0, 90.0, startMs = 0, durationMs = 50, dtMs = 10) +
            rampAngles(90.0, 170.0, startMs = 50, durationMs = 50, dtMs = 10)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertTrue(result.completed.isEmpty())
        assertEquals(1, result.rejected.size)
        assertEquals(RejectionReason.TOO_FAST, result.rejected.single().reason)
    }

    @Test
    fun `a rep held indefinitely at depth times out as too slow`() {
        val samples = rampAngles(170.0, 90.0, startMs = 0, durationMs = 500) +
            holdAngle(90.0, startMs = 500, durationMs = 8500)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        // The stuck rep never completes. (Holding indefinitely at depth, past the point of
        // giving up, can also leave a tiny near-zero-duration remainder once the timeout
        // resets tracking — that remainder is itself correctly rejected, never counted.)
        assertTrue(result.completed.isEmpty())
        assertTrue(result.rejected.any { it.reason == RejectionReason.TOO_SLOW })
    }

    @Test
    fun `a rep that returns only partway counts but is flagged as partial lockout`() {
        val samples = rampAngles(170.0, 90.0, startMs = 0, durationMs = 500) +
            rampAngles(90.0, 154.0, startMs = 500, durationMs = 500)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertEquals(1, result.completed.size)
        assertTrue(result.rejected.isEmpty())
        val rep = result.completed.single()
        assertTrue(rep.depthReached)
        assertFalse(rep.lockoutReached)
        assertEquals("partial", rep.romAchieved)
    }

    @Test
    fun `left-right symmetry deviation is measured from the front but not from the side`() {
        val curlCalibration = CalibrationProfile("dumbbell-bicep-curl", restAngleDeg = 170.0, peakAngleDeg = 45.0, calibratedAtMs = 0)
        val samples = twoSidedRamp(170.0, 40.0, 170.0, 55.0, startMs = 0, durationMs = 500) +
            twoSidedRamp(40.0, 170.0, 55.0, 170.0, startMs = 500, durationMs = 500)

        val fromFront = runRepStateMachine(CURL_CONFIG, curlCalibration, samples)
        assertEquals(1, fromFront.completed.size)
        assertEquals(15.0, fromFront.completed.single().symmetryDeviationDeg!!, 3.0)

        val sideConfig = CURL_CONFIG.copy(cameraPosition = CameraPosition.SIDE)
        val fromSide = runRepStateMachine(sideConfig, curlCalibration, samples)
        assertEquals(1, fromSide.completed.size)
        assertNull("symmetry can't be assessed from a side angle", fromSide.completed.single().symmetryDeviationDeg)
    }

    @Test
    fun `jittery landmark noise around rest produces no reps once smoothed`() {
        val jittery = mutableListOf<AngleSample>()
        var t = 0L
        repeat(90) { i ->
            val raw = if (i % 2 == 0) 170.0 else 135.0 // 35deg single-frame spikes, alternating
            jittery += AngleSample(t, raw, null)
            t += 33
        }

        var previousSmoothed: AngleSample? = null
        val smoothedSamples = jittery.map { raw ->
            val smoothed = smoothAngleSample(previousSmoothed, raw)
            previousSmoothed = smoothed
            smoothed
        }
        val smoothedResult = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, smoothedSamples)
        assertTrue("smoothing must prevent false reps from raw jitter", smoothedResult.completed.isEmpty())
        assertTrue("smoothing must also prevent spurious rejected-rep churn", smoothedResult.rejected.isEmpty())

        // Contrast: the same raw noise fed straight to the state machine (no smoothing)
        // does register spurious activity — this is why smoothing runs first in the real pipeline.
        val unsmoothedResult = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, jittery)
        assertTrue(unsmoothedResult.rejected.isNotEmpty() || unsmoothedResult.completed.isNotEmpty())
    }
}
