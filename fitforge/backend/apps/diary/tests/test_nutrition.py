import datetime
import uuid
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model

from apps.diary.models import MealItem, MealLog
from apps.diary.nutrition import nutrition_for_portion
from apps.reference.models import FoodItem, Recipe, RecipeIngredient

pytestmark = pytest.mark.django_db


def test_logged_macro_snapshot_does_not_change_when_food_item_changes():
    food = FoodItem.objects.create(
        slug="ifct-e012",
        name={"en": "Banana, ripe, robusta"},
        kcal_per_100g=Decimal("105.16"),
        protein_g=Decimal("1.23"),
        carbs_g=Decimal("23.63"),
        fat_g=Decimal("0.33"),
        default_portion_g=Decimal("100.00"),
        portion_label="100 g",
        source="ifct_2017",
    )
    snapshot = nutrition_for_portion(food, Decimal("100.00"))
    user = get_user_model().objects.create_user(username="snapshot", password="password")
    common = {
        "user": user,
        "occurred_at": datetime.datetime.now(datetime.UTC),
        "utc_offset": "+00:00",
        "source": "search",
    }
    log = MealLog.objects.create(client_uuid=uuid.uuid4(), **common)
    item = MealItem.objects.create(
        client_uuid=uuid.uuid4(),
        log=log,
        food_slug=food.slug,
        quantity=Decimal("100.00"),
        calories=snapshot.calories,
        protein_g=snapshot.protein_g,
        carbs_g=snapshot.carbs_g,
        fat_g=snapshot.fat_g,
        **common,
    )

    food.kcal_per_100g = Decimal("999.00")
    food.protein_g = Decimal("99.00")
    food.carbs_g = Decimal("99.00")
    food.fat_g = Decimal("99.00")
    food.save()
    item.refresh_from_db()

    assert item.calories == Decimal("105.16")
    assert item.protein_g == Decimal("1.23")
    assert item.carbs_g == Decimal("23.63")
    assert item.fat_g == Decimal("0.33")


def test_portion_is_required_and_positive():
    food = FoodItem(
        kcal_per_100g=Decimal("100"),
        protein_g=Decimal("1"),
        carbs_g=Decimal("1"),
        fat_g=Decimal("1"),
    )
    with pytest.raises(ValueError, match="portion_g"):
        nutrition_for_portion(food, Decimal("0"))


def test_recipe_changes_affect_future_computation_not_past_meal_snapshot():
    ingredient = FoodItem.objects.create(
        slug="ifct-b010",
        name={"en": "Green gram, dal"},
        kcal_per_100g=Decimal("325.76"),
        protein_g=Decimal("23.88"),
        carbs_g=Decimal("52.59"),
        fat_g=Decimal("1.35"),
        default_portion_g=Decimal("100.00"),
        portion_label="100 g",
        source="ifct_2017",
    )
    recipe = Recipe.objects.create(
        slug="moong-dal",
        name={"en": "Moong dal"},
        serving_description="1 katori, ~220g",
        serving_grams_total=Decimal("220.00"),
    )
    component = RecipeIngredient.objects.create(
        recipe=recipe,
        ingredient=ingredient,
        grams_in_serving=Decimal("50.00"),
    )
    initial = nutrition_for_portion(recipe, recipe.serving_grams_total)
    user = get_user_model().objects.create_user(username="recipe-snapshot", password="password")
    common = {
        "user": user,
        "occurred_at": datetime.datetime.now(datetime.UTC),
        "utc_offset": "+00:00",
        "source": "photo",
    }
    log = MealLog.objects.create(client_uuid=uuid.uuid4(), **common)
    item = MealItem.objects.create(
        client_uuid=uuid.uuid4(),
        log=log,
        food_slug=recipe.slug,
        quantity=recipe.serving_grams_total,
        calories=initial.calories,
        protein_g=initial.protein_g,
        carbs_g=initial.carbs_g,
        fat_g=initial.fat_g,
        **common,
    )

    ingredient.kcal_per_100g = Decimal("400.00")
    ingredient.protein_g = Decimal("30.00")
    ingredient.save()
    after_ifct_correction = nutrition_for_portion(recipe, recipe.serving_grams_total)
    assert after_ifct_correction != initial

    component.grams_in_serving = Decimal("60.00")
    component.save()
    after_quantity_correction = nutrition_for_portion(recipe, recipe.serving_grams_total)
    assert after_quantity_correction != after_ifct_correction

    item.refresh_from_db()
    assert item.calories == initial.calories
    assert item.protein_g == initial.protein_g
    assert item.carbs_g == initial.carbs_g
    assert item.fat_g == initial.fat_g
