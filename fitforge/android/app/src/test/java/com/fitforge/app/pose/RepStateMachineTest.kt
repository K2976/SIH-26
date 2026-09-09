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
        val curlCalibration = CalibrationProfile(
            "dumbbell-bicep-curl",
            restAngleDeg = 170.0,
            peakAngleDeg = 45.0,
            calibratedAtMs = 0,
        )
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

    @Test
    fun `standing still with one hallucinated side counts zero reps`() {
        // Root cause of "standing still counts reps": representativeAngle used to average
        // both sides. Left is genuinely motionless at rest the whole time; right is a
        // single-side tracking glitch that swings toward the calibrated peak and back,
        // exactly as a confused/occluded landmark can while a lifter never moves at all.
        // Averaging let the still side get dragged along into phantom mid-range "progress."
        val curlCalibration = CalibrationProfile(
            "dumbbell-bicep-curl",
            restAngleDeg = 170.0,
            peakAngleDeg = 45.0,
            calibratedAtMs = 0,
        )
        val samples = twoSidedRamp(170.0, 170.0, 170.0, 20.0, startMs = 0, durationMs = 800) +
            twoSidedRamp(170.0, 170.0, 20.0, 170.0, startMs = 800, durationMs = 800)

        val result = runRepStateMachine(CURL_CONFIG, curlCalibration, samples)

        assertTrue("a still lifter must not be credited a rep off one noisy side", result.completed.isEmpty())
    }

    @Test
    fun `realistic per-frame noise on a still pose produces zero reps`() {
        // Small, continuous per-frame wobble (a few degrees), not the earlier test's extreme
        // 35deg single-frame spikes — this is what a genuinely still lifter's landmark stream
        // looks like after MediaPipe's own upstream smoothing. Should never get near the
        // resting band (squat range is 80deg; this wobble is +/-3deg), and the velocity gate
        // is a second line of defense if it ever did.
        val samples = (0 until 150).map { i ->
            val wobble = 3.0 * kotlin.math.sin(i * 0.4)
            AngleSample(i * 33L, 170.0 + wobble, null)
        }

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertTrue("noise around rest must never be counted", result.completed.isEmpty())
        assertTrue("noise around rest must never even be rejected as an attempted rep", result.rejected.isEmpty())
    }

    @Test
    fun `a deliberately slow controlled rep still counts`() {
        // Guards against an over-aggressive velocity floor: a slow, fully controlled rep
        // (3s down, 3s up) must clear MIN_VELOCITY_PER_SEC at every transition, not just fast ones.
        val samples = rampAngles(170.0, 90.0, startMs = 0, durationMs = 3000) +
            rampAngles(90.0, 170.0, startMs = 3000, durationMs = 3000)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertEquals(1, result.completed.size)
        assertTrue(result.rejected.isEmpty())
        assertTrue(result.completed.single().depthReached)
        assertTrue(result.completed.single().lockoutReached)
    }

    @Test
    fun `a mid-rep confidence drop rejects the rep with a reason instead of crashing`() {
        val descending = rampAngles(170.0, 90.0, startMs = 0, durationMs = 500)
        val blackout = (1..20).map { i -> AngleSample(500 + i * 33L, null, null) }

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, descending + blackout)

        assertTrue(result.completed.isEmpty())
        assertEquals(1, result.rejected.size)
        assertEquals(RejectionReason.LOST_TRACKING, result.rejected.single().reason)
    }

    @Test
    fun `a brief single-frame occlusion mid-rep does not reject the rep`() {
        val samples = rampAngles(170.0, 90.0, startMs = 0, durationMs = 500) +
            listOf(AngleSample(533, null, null)) +
            rampAngles(90.0, 170.0, startMs = 566, durationMs = 500)

        val result = runRepStateMachine(SQUAT_CONFIG, SQUAT_CALIBRATION, samples)

        assertEquals(1, result.completed.size)
        assertTrue(result.rejected.isEmpty())
    }
}
