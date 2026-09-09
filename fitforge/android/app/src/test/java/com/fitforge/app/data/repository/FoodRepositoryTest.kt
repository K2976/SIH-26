package com.fitforge.app.data.repository

import com.fitforge.app.core.database.FoodItemEntity
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodRepositoryTest {
    private val food = FoodItemEntity(
        slug = "vegetable-biryani",
        name = "Vegetable biryani",
        kcalPer100g = 151.0,
        proteinG = 3.5,
        carbsG = 25.7,
        fatG = 3.8,
        defaultPortionG = 250.0,
        portionLabel = "1 plate ~250g",
        source = "Published meal composition table",
        isActive = true,
        updatedAtVersion = 1,
    )

    @Test
    fun `photo search and barcode rows differ only by source`() {
        val preview = nutritionForPortion(food, 250.0)
        val now = Instant.parse("2026-09-10T10:00:00Z")
        val sources = listOf(FOOD_SOURCE_PHOTO, FOOD_SOURCE_SEARCH, FOOD_SOURCE_BARCODE)
        val rows = sources.map {
            buildMealRows(preview, it, now, logUuid = "log", itemUuid = "item")
        }

        rows.forEachIndexed { index, candidate ->
            assertEquals(sources[index], candidate.log.diary.source)
            assertEquals(sources[index], candidate.item.diary.source)
            assertEquals(rows.first().log, candidate.log.copy(diary = candidate.log.diary.copy(source = "photo")))
            assertEquals(rows.first().item, candidate.item.copy(diary = candidate.item.diary.copy(source = "photo")))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero portion cannot advance to nutrition`() {
        nutritionForPortion(food, 0.0)
    }

    @Test
    fun `stored snapshot is unchanged after food reference macros change`() {
        val snapshot = nutritionForPortion(food, 250.0)
        val logged = buildMealRows(
            snapshot,
            FOOD_SOURCE_SEARCH,
            Instant.parse("2026-09-10T10:00:00Z"),
            "log",
            "item",
        ).item

        val correctedFood = food.copy(kcalPer100g = 999.0, proteinG = 99.0)
        nutritionForPortion(correctedFood, 250.0)

        assertEquals(377.5, logged.calories, 0.0)
        assertEquals(8.75, logged.proteinG, 0.0)
        assertEquals(64.25, logged.carbsG, 0.0)
        assertEquals(9.5, logged.fatG, 0.0)
    }
}
