package com.fitforge.app.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "set_logs",
    primaryKeys = ["clientUuid"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["clientUuid"],
            childColumns = ["sessionClientUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionClientUuid"), Index("exerciseSlug")],
)
data class SetLogEntity(
    @Embedded val diary: BaseDiaryColumns,
    val sessionClientUuid: String,
    val exerciseSlug: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val romAchieved: String? = null,
    val tempoMs: Int? = null,
)
