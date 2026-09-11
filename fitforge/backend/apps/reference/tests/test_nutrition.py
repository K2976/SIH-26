from decimal import Decimal

import pytest

from apps.reference.nutrition import RecipeComponent, compose_recipe_nutrition


def test_compose_recipe_nutrition_is_the_sum_of_scaled_ingredients():
    result = compose_recipe_nutrition(
        [
            RecipeComponent(
                grams=Decimal("80"),
                kcal_per_100g=Decimal("350"),
                protein_g_per_100g=Decimal("8"),
                carbs_g_per_100g=Decimal("75"),
                fat_g_per_100g=Decimal("1"),
            ),
            RecipeComponent(
                grams=Decimal("10"),
                kcal_per_100g=Decimal("900"),
                protein_g_per_100g=Decimal("0"),
                carbs_g_per_100g=Decimal("0"),
                fat_g_per_100g=Decimal("100"),
            ),
        ]
    )

    assert result.calories == Decimal("370.00")
    assert result.protein_g == Decimal("6.40")
    assert result.carbs_g == Decimal("60.00")
    assert result.fat_g == Decimal("10.80")


def test_recipe_component_grams_must_be_positive():
    with pytest.raises(ValueError, match="ingredient grams"):
        compose_recipe_nutrition(
            [
                RecipeComponent(
                    grams=Decimal("0"),
                    kcal_per_100g=Decimal("1"),
                    protein_g_per_100g=Decimal("1"),
                    carbs_g_per_100g=Decimal("1"),
                    fat_g_per_100g=Decimal("1"),
                )
            ]
        )
