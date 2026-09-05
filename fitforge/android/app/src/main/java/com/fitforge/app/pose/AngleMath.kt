package com.fitforge.app.pose

import kotlin.math.acos
import kotlin.math.sqrt

private const val MIN_VISIBILITY = 0.5f

/** Interior angle at [b] formed by rays b->a and b->c, in degrees, on the x/y plane. */
fun angleBetweenDeg(a: Landmark, b: Landmark, c: Landmark): Double {
    val abx = (a.x - b.x).toDouble()
    val aby = (a.y - b.y).toDouble()
    val cbx = (c.x - b.x).toDouble()
    val cby = (c.y - b.y).toDouble()

    val dot = abx * cbx + aby * cby
    val magAb = sqrt(abx * abx + aby * aby)
    val magCb = sqrt(cbx * cbx + cby * cby)
    if (magAb == 0.0 || magCb == 0.0) return 0.0

    val cosAngle = (dot / (magAb * magCb)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(cosAngle))
}

enum class Side { LEFT, RIGHT }

sealed class VisibilityResult {
    /** At least one side's full angle-joint triple is visible; usable names which. */
    data class Visible(val usableSides: Set<Side>) : VisibilityResult()
    data class NotVisible(val missing: Set<PoseLandmark>) : VisibilityResult()
}

/**
 * A side camera only ever sees one leg/arm clearly — requiring both sides
 * would refuse every side-on setup. So visibility is satisfied by *either*
 * the left or the right angle-joint triple being fully visible, not both.
 * [VisibilityResult.NotVisible.missing] unions both sides' gaps so the
 * calibration screen can say exactly what's out of frame.
 */
fun checkVisibility(frame: PoseFrame, config: ExercisePoseConfig): VisibilityResult {
    val leftMissing = missingFrom(frame, config.angleJoint)
    val rightMissing = missingFrom(frame, config.angleJoint.mirrored())
    val usable = buildSet {
        if (leftMissing.isEmpty()) add(Side.LEFT)
        if (rightMissing.isEmpty()) add(Side.RIGHT)
    }
    return if (usable.isNotEmpty()) {
        VisibilityResult.Visible(usable)
    } else {
        VisibilityResult.NotVisible(leftMissing + rightMissing)
    }
}

private fun missingFrom(frame: PoseFrame, joint: AngleJoint): Set<PoseLandmark> =
    setOf(joint.from, joint.vertex, joint.to).filterTo(mutableSetOf()) { landmark ->
        val point = frame.landmarks[landmark]
        point == null || point.visibility < MIN_VISIBILITY
    }

/**
 * Raw per-side angle for this frame, or null on a side whose three joint
 * landmarks aren't all visible. Never estimates a missing side.
 */
fun computeAngleSample(frame: PoseFrame, config: ExercisePoseConfig): AngleSample {
    val left = angleForJoint(frame, config.angleJoint)
    val right = angleForJoint(frame, config.angleJoint.mirrored())
    return AngleSample(frame.timestampMs, left, right)
}

private fun angleForJoint(frame: PoseFrame, joint: AngleJoint): Double? {
    val a = frame.landmarks[joint.from] ?: return null
    val b = frame.landmarks[joint.vertex] ?: return null
    val c = frame.landmarks[joint.to] ?: return null
    if (a.visibility < MIN_VISIBILITY || b.visibility < MIN_VISIBILITY || c.visibility < MIN_VISIBILITY) return null
    return angleBetweenDeg(a, b, c)
}
