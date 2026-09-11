package com.fitforge.app.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleMathTest {
    @Test
    fun `angleBetweenDeg reports a right angle for perpendicular limbs`() {
        val vertex = Landmark(0.5f, 0.5f, 0f, 1f)
        val up = Landmark(0.5f, 0.2f, 0f, 1f)
        val right = Landmark(0.8f, 0.5f, 0f, 1f)
        assertEquals(90.0, angleBetweenDeg(up, vertex, right), 0.01)
    }

    @Test
    fun `angleBetweenDeg reports 180 for a straight limb`() {
        val vertex = Landmark(0.5f, 0.5f, 0f, 1f)
        val up = Landmark(0.5f, 0.2f, 0f, 1f)
        val down = Landmark(0.5f, 0.8f, 0f, 1f)
        assertEquals(180.0, angleBetweenDeg(up, vertex, down), 0.01)
    }

    @Test
    fun `computeAngleSample synthesizes the requested squat knee angle`() {
        val frame = squatFrame(timestampMs = 0, kneeAngleDeg = 123.0)
        val sample = computeAngleSample(frame, SQUAT_CONFIG)
        assertEquals(123.0, sample.leftDeg!!, 0.01)
        assertNull("side camera frame has no right-leg landmarks", sample.rightDeg)
    }

    @Test
    fun `checkVisibility accepts a side view with only the left leg visible`() {
        val frame = squatFrame(timestampMs = 0, kneeAngleDeg = 170.0)
        val result = checkVisibility(frame, SQUAT_CONFIG)
        assertTrue(result is VisibilityResult.Visible)
        assertEquals(setOf(Side.LEFT), (result as VisibilityResult.Visible).usableSides)
    }

    @Test
    fun `checkVisibility refuses when neither side's joints are visible`() {
        val emptyFrame = PoseFrame(0, emptyMap())
        val result = checkVisibility(emptyFrame, SQUAT_CONFIG)
        assertTrue(result is VisibilityResult.NotVisible)
        val missing = (result as VisibilityResult.NotVisible).missing
        assertTrue(missing.contains(PoseLandmark.LEFT_KNEE))
        assertTrue(missing.contains(PoseLandmark.RIGHT_KNEE))
    }

    @Test
    fun `checkVisibility rejects a landmark below the visibility floor`() {
        val frame = squatFrame(timestampMs = 0, kneeAngleDeg = 170.0)
        val dimKnee = frame.landmarks[PoseLandmark.LEFT_KNEE]!!.copy(visibility = 0.1f)
        val degraded = frame.copy(landmarks = frame.landmarks + (PoseLandmark.LEFT_KNEE to dimKnee))
        val result = checkVisibility(degraded, SQUAT_CONFIG)
        assertTrue(result is VisibilityResult.NotVisible)
    }
}
