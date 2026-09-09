from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from apps.reference.models import FoodItem

TWO_PLACES = Decimal("0.01")


@dataclass(frozen=True)
class NutritionSnapshot:
    calories: Decimal
    protein_g: Decimal
    carbs_g: Decimal
    fat_g: Decimal


def nutrition_for_portion(food: FoodItem, portion_g: Decimal) -> NutritionSnapshot:
    """Derive the immutable values copied to MealItem at confirmation time."""
    if portion_g <= 0:
        raise ValueError("portion_g must be greater than zero")
    factor = portion_g / Decimal(100)

    def scaled(value: Decimal) -> Decimal:
        return (value * factor).quantize(TWO_PLACES, rounding=ROUND_HALF_UP)

    return NutritionSnapshot(
        calories=scaled(food.kcal_per_100g),
        protein_g=scaled(food.protein_g),
        carbs_g=scaled(food.carbs_g),
        fat_g=scaled(food.fat_g),
    )
