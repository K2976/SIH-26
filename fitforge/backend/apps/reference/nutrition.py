from collections.abc import Iterable
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

TWO_PLACES = Decimal("0.01")


@dataclass(frozen=True)
class NutritionValues:
    calories: Decimal
    protein_g: Decimal
    carbs_g: Decimal
    fat_g: Decimal


@dataclass(frozen=True)
class RecipeComponent:
    grams: Decimal
    kcal_per_100g: Decimal
    protein_g_per_100g: Decimal
    carbs_g_per_100g: Decimal
    fat_g_per_100g: Decimal


def _rounded(value: Decimal) -> Decimal:
    return value.quantize(TWO_PLACES, rounding=ROUND_HALF_UP)


def compose_recipe_nutrition(components: Iterable[RecipeComponent]) -> NutritionValues:
    """Pure composition: sum each ingredient's per-100g values at its grams."""
    calories = protein = carbs = fat = Decimal(0)
    found_component = False
    for component in components:
        found_component = True
        if component.grams <= 0:
            raise ValueError("ingredient grams must be greater than zero")
        factor = component.grams / Decimal(100)
        calories += component.kcal_per_100g * factor
        protein += component.protein_g_per_100g * factor
        carbs += component.carbs_g_per_100g * factor
        fat += component.fat_g_per_100g * factor
    if not found_component:
        raise ValueError("a recipe must contain at least one ingredient")
    return NutritionValues(
        calories=_rounded(calories),
        protein_g=_rounded(protein),
        carbs_g=_rounded(carbs),
        fat_g=_rounded(fat),
    )


def recipe_serving_nutrition(recipe) -> NutritionValues:
    """Thin ORM adapter around the pure composition function."""
    rows = recipe.ingredients.select_related("ingredient").all()
    return compose_recipe_nutrition(
        RecipeComponent(
            grams=row.grams_in_serving,
            kcal_per_100g=row.ingredient.kcal_per_100g,
            protein_g_per_100g=row.ingredient.protein_g,
            carbs_g_per_100g=row.ingredient.carbs_g,
            fat_g_per_100g=row.ingredient.fat_g,
        )
        for row in rows
    )


def recipe_nutrition_per_100g(recipe) -> NutritionValues:
    if recipe.serving_grams_total <= 0:
        raise ValueError("serving_grams_total must be greater than zero")
    serving = recipe_serving_nutrition(recipe)
    factor = Decimal(100) / recipe.serving_grams_total
    return NutritionValues(
        calories=_rounded(serving.calories * factor),
        protein_g=_rounded(serving.protein_g * factor),
        carbs_g=_rounded(serving.carbs_g * factor),
        fat_g=_rounded(serving.fat_g * factor),
    )
