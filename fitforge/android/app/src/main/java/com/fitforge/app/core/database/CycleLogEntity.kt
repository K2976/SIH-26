package com.fitforge.app.core.database

import androidx.room.Embedded
import androidx.room.Entity
import kotlinx.datetime.LocalDate

/**
 * One cycle-module log entry: a period event and/or a daily symptom check-in.
 * `date` is the local day this entry is *about*, distinct from `diary.occurredAt`
 * (when it was logged) — matches the backend's CycleLog.date field.
 */
@Entity(tableName = "cycle_logs", primaryKeys = ["clientUuid"])
data class CycleLogEntity(
    @Embedded val diary: BaseDiaryColumns,
    val date: LocalDate,
    /** "period_start" | "period_end" | "spotting" | "" (symptom-only check-in). */
    val event: String = "",
    val flow: String = "",
    val symptoms: List<String> = emptyList(),
    /** 1..5, null if not logged this entry. */
    val energyRating: Int? = null,
)
