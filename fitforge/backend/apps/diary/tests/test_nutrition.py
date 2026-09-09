import datetime
import uuid
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model

from apps.diary.models import MealItem, MealLog
from apps.diary.nutrition import nutrition_for_portion
from apps.reference.models import FoodItem

pytestmark = pytest.mark.django_db


def test_logged_macro_snapshot_does_not_change_when_food_item_changes():
    food = FoodItem.objects.create(
        slug="vegetable-biryani",
        name={"en": "Vegetable biryani"},
        kcal_per_100g=Decimal("151.00"),
        protein_g=Decimal("3.50"),
        carbs_g=Decimal("25.70"),
        fat_g=Decimal("3.80"),
        default_portion_g=Decimal("250.00"),
        portion_label="1 plate ~250g",
        source="Published meal composition table",
    )
    snapshot = nutrition_for_portion(food, Decimal("250.00"))
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
        quantity=Decimal("250.00"),
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

    assert item.calories == Decimal("377.50")
    assert item.protein_g == Decimal("8.75")
    assert item.carbs_g == Decimal("64.25")
    assert item.fat_g == Decimal("9.50")


def test_portion_is_required_and_positive():
    food = FoodItem(
        kcal_per_100g=Decimal("100"),
        protein_g=Decimal("1"),
        carbs_g=Decimal("1"),
        fat_g=Decimal("1"),
    )
    with pytest.raises(ValueError, match="portion_g"):
        nutrition_for_portion(food, Decimal("0"))
