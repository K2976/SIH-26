package com.fitforge.app.data.repository

import com.fitforge.app.core.database.FoodItemEntity
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodRepositoryTest {
    private val recipe = FoodItemEntity(
        slug = "vegetable-biryani",
        name = "Vegetable biryani",
        kcalPer100g = 150.0,
        proteinG = 4.0,
        carbsG = 25.0,
        fatG = 4.0,
        defaultPortionG = 300.0,
        portionLabel = "1 plate, ~300g",
        source = "recipe_computed",
        isActive = true,
        updatedAtVersion = 1,
    )

    @Test
    fun `photo search and barcode rows differ only by source`() {
        val preview = nutritionForPortion(recipe, recipe.defaultPortionG)
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
        nutritionForPortion(recipe, 0.0)
    }

    @Test
    fun `stored recipe snapshot is unchanged after computed reference macros change`() {
        val snapshot = nutritionForPortion(recipe, recipe.defaultPortionG)
        val logged = buildMealRows(
            snapshot,
            FOOD_SOURCE_SEARCH,
            Instant.parse("2026-09-10T10:00:00Z"),
            "log",
            "item",
        ).item

        val recomputedRecipe = recipe.copy(kcalPer100g = 999.0, proteinG = 99.0)
        nutritionForPortion(recomputedRecipe, recipe.defaultPortionG)

        assertEquals(450.0, logged.calories, 0.0)
        assertEquals(12.0, logged.proteinG, 0.0)
        assertEquals(75.0, logged.carbsG, 0.0)
        assertEquals(12.0, logged.fatG, 0.0)
    }
}
