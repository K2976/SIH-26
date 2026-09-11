package com.fitforge.app.feature.food

import com.fitforge.app.core.database.FoodItemEntity
import com.fitforge.app.data.repository.FOOD_SOURCE_PHOTO
import org.junit.Assert.assertNull
import org.junit.Test

class FoodLogStepTest {
    private val food = FoodItemEntity(
        slug = "banana",
        name = "Banana",
        kcalPer100g = 89.0,
        proteinG = 1.09,
        carbsG = 22.84,
        fatG = 0.33,
        defaultPortionG = 118.0,
        portionLabel = "1 medium banana ~118g",
        source = "USDA FoodData Central",
        isActive = true,
        updatedAtVersion = 1,
    )

    @Test
    fun `label cannot bypass mandatory portion step and log`() {
        assertNull(FoodLogStep.Label(candidates = listOf(food)).loggableNutrition())
        assertNull(
            FoodLogStep.Portion(food, FOOD_SOURCE_PHOTO, grams = "118")
                .loggableNutrition()
        )
    }
}
