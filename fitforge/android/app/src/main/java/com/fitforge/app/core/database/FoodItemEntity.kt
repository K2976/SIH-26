package com.fitforge.app.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class FoodItemEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val kcalPer100g: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val defaultPortionG: Double,
    val portionLabel: String,
    val source: String,
    val barcode: String? = null,
    val isActive: Boolean,
    val updatedAtVersion: Int,
)

@Entity(tableName = "food_search")
@Fts4
data class FoodSearchEntity(val slug: String, val name: String)
