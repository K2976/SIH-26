from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from apps.reference.models import FoodItem, Recipe
from apps.reference.nutrition import recipe_serving_nutrition

TWO_PLACES = Decimal("0.01")


@dataclass(frozen=True)
class NutritionSnapshot:
    calories: Decimal
    protein_g: Decimal
    carbs_g: Decimal
    fat_g: Decimal


def nutrition_for_portion(food: FoodItem | Recipe, portion_g: Decimal) -> NutritionSnapshot:
    """Derive the immutable values copied to MealItem at confirmation time."""
    if portion_g <= 0:
        raise ValueError("portion_g must be greater than zero")
    if isinstance(food, Recipe):
        base = recipe_serving_nutrition(food)
        factor = portion_g / food.serving_grams_total
        values = (base.calories, base.protein_g, base.carbs_g, base.fat_g)
    else:
        factor = portion_g / Decimal(100)
        values = (food.kcal_per_100g, food.protein_g, food.carbs_g, food.fat_g)

    def scaled(value: Decimal) -> Decimal:
        return (value * factor).quantize(TWO_PLACES, rounding=ROUND_HALF_UP)

    return NutritionSnapshot(
        calories=scaled(values[0]),
        protein_g=scaled(values[1]),
        carbs_g=scaled(values[2]),
        fat_g=scaled(values[3]),
    )
