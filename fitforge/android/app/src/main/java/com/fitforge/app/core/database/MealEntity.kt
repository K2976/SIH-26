package com.fitforge.app.core.database

import androidx.room.Embedded
import androidx.room.Entity

@Entity(tableName = "meal_logs", primaryKeys = ["clientUuid"])
data class MealLogEntity(@Embedded val diary: BaseDiaryColumns)

@Entity(tableName = "meal_items", primaryKeys = ["clientUuid"])
data class MealItemEntity(
    @Embedded val diary: BaseDiaryColumns,
    val logClientUuid: String,
    val foodSlug: String,
    val quantity: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
