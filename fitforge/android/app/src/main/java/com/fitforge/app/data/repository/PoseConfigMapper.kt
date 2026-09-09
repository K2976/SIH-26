@file:Suppress("ReturnCount")

package com.fitforge.app.data.repository

import com.fitforge.app.core.database.ExerciseEntity
import com.fitforge.app.pose.AngleJoint
import com.fitforge.app.pose.CameraPosition
import com.fitforge.app.pose.ExercisePoseConfig
import com.fitforge.app.pose.PoseLandmark

/**
 * Reassembles the pure [ExercisePoseConfig] from an [ExerciseEntity]'s flattened
 * pose columns, or null if the exercise has none.
 */
fun ExerciseEntity.toPoseConfig(): ExercisePoseConfig? {
    val from = poseAngleFrom?.let(::toPoseLandmark) ?: return null
    val vertex = poseAngleVertex?.let(::toPoseLandmark) ?: return null
    val to = poseAngleTo?.let(::toPoseLandmark) ?: return null
    val camera = cameraPosition?.let(::toCameraPosition) ?: return null
    val minRepSeconds = poseMinRepSeconds ?: return null
    val maxRepSeconds = poseMaxRepSeconds ?: return null
    val symmetryToleranceDeg = poseSymmetryToleranceDeg ?: return null

    return ExercisePoseConfig(
        cameraPosition = camera,
        angleJoint = AngleJoint(from, vertex, to),
        minRepSeconds = minRepSeconds,
        maxRepSeconds = maxRepSeconds,
        symmetryToleranceDeg = symmetryToleranceDeg,
    )
}

private fun toPoseLandmark(name: String): PoseLandmark? = runCatching { PoseLandmark.valueOf(name) }.getOrNull()

private fun toCameraPosition(value: String): CameraPosition? = when (value) {
    "front" -> CameraPosition.FRONT
    "side" -> CameraPosition.SIDE
    "45_degree" -> CameraPosition.FORTY_FIVE_DEGREE
    else -> null
}
