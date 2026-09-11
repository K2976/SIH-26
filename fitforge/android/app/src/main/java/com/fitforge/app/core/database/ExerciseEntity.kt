package com.fitforge.app.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Local cache of the reference exercise library. Seeded from the assets
 * bundle on first launch, kept fresh by the daily delta-sync worker.
 * Muscle/cue lists are display-only, so they're stored comma-joined rather
 * than as structured JSON — nothing here parses them back apart.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val instructions: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val equipment: String,
    val movementPattern: String,
    val metValue: Double,
    val isBodyweight: Boolean,
    val isActive: Boolean,
    val updatedAtVersion: Int,
    // Flattened `pose_config` + `camera_position` — null on every field means camera
    // counting isn't supported for this exercise. Flattened rather than stored as one
    // JSON blob because the two source formats (bundled asset JSON vs. the generated
    // API model's `Any?`) decode pose_config differently; typed columns need only one
    // mapping shape on the read side. See PoseConfigMapper.kt.
    val cameraPosition: String? = null,
    val poseAngleFrom: String? = null,
    val poseAngleVertex: String? = null,
    val poseAngleTo: String? = null,
    val poseMinRepSeconds: Double? = null,
    val poseMaxRepSeconds: Double? = null,
    val poseSymmetryToleranceDeg: Double? = null,
)

/** Standalone FTS4 index over exercise names, kept in sync by ExerciseDao. */
@Entity(tableName = "exercise_search")
@Fts4
data class ExerciseSearchEntity(
    val slug: String,
    val name: String,
)
