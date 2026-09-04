package com.fitforge.app.core.database

import androidx.room.Embedded
import androidx.room.Entity

/**
 * A logged workout. `durationMinutes == null` means the session is still in
 * progress — there's no separate status column, matching the backend model.
 */
@Entity(tableName = "workout_sessions", primaryKeys = ["clientUuid"])
data class WorkoutSessionEntity(
    @Embedded val diary: BaseDiaryColumns,
    val plannedSessionClientUuid: String? = null,
    val durationMinutes: Int? = null,
    val notes: String = "",
)
