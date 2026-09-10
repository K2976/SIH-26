package com.fitforge.app.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index

/**
 * One completed camera-tracked set's landmark sequence, captured for future
 * ML classifier training (Prompt C) — never read back by this app. Mirrors
 * [SetLogEntity]'s diary shape; [landmarkFramesJson]/[repLabelsJson] are
 * pre-serialized JSON text (see `feature/repcounter/PoseSequenceRecording.kt`)
 * rather than structured columns, since Room has no native JSON column type
 * and the same string is reused verbatim as the sync wire payload.
 */
@Entity(tableName = "pose_landmark_sequences", primaryKeys = ["clientUuid"], indices = [Index("exerciseSlug")])
data class PoseLandmarkSequenceEntity(
    @Embedded val diary: BaseDiaryColumns,
    val exerciseSlug: String,
    val cameraPosition: String,
    val calibrationSource: String,
    val landmarkFramesJson: String,
    val repLabelsJson: String,
)
