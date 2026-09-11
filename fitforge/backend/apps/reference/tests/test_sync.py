from decimal import Decimal

import pytest
from rest_framework.test import APIClient

from apps.reference.models import Exercise, FoodItem, Recipe, RecipeIngredient

pytestmark = pytest.mark.django_db


def make_exercise(slug, **overrides):
    fields = {
        "name": {"en": slug},
        "instructions": {"en": "Do the thing."},
        "movement_pattern": Exercise.MovementPattern.SQUAT,
        "met_value": Decimal("5.0"),
    }
    fields.update(overrides)
    return Exercise.objects.create(slug=slug, **fields)


@pytest.fixture
def client():
    return APIClient()


class TestManifest:
    def test_reports_version_and_table_counts(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/manifest/")
        assert resp.status_code == 200
        assert resp.data["reference_version"] == 2
        assert resp.data["table_counts"]["exercises"] == 2
        assert resp.data["table_counts"]["foods"] == 0
        assert resp.data["table_counts"]["recipes"] == 0
        assert resp.data["table_counts"]["recipe_ingredients"] == 0

    def test_does_not_require_auth(self, client):
        resp = client.get("/api/v1/reference/manifest/")
        assert resp.status_code == 200


class TestExerciseDelta:
    def test_since_omitted_returns_everything(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/exercises/")
        assert resp.status_code == 200
        assert {row["slug"] for row in resp.data} == {"a", "b"}

    def test_since_returns_only_rows_changed_after_that_version(self, client):
        first = make_exercise("a")  # version 1
        make_exercise("b")  # version 2

        resp = client.get(f"/api/v1/reference/exercises/?since={first.updated_at_version}")

        assert resp.status_code == 200
        assert [row["slug"] for row in resp.data] == ["b"]

    def test_since_current_version_returns_nothing(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/exercises/?since=2")
        assert resp.data == []

    def test_touching_a_row_makes_it_reappear_in_the_delta(self, client):
        first = make_exercise("a")
        make_exercise("b")
        watermark = first.updated_at_version + 1  # = "b"'s version

        first.met_value = Decimal("6.0")
        first.save()

        resp = client.get(f"/api/v1/reference/exercises/?since={watermark}")
        assert [row["slug"] for row in resp.data] == ["a"]

    def test_deactivation_appears_in_the_delta(self, client):
        exercise = make_exercise("a")
        watermark = exercise.updated_at_version

        exercise.is_active = False
        exercise.save()

        resp = client.get(f"/api/v1/reference/exercises/?since={watermark}")
        assert len(resp.data) == 1
        assert resp.data[0]["slug"] == "a"
        assert resp.data[0]["is_active"] is False

    def test_deactivated_rows_are_not_deleted(self, client):
        exercise = make_exercise("a")
        exercise.is_active = False
        exercise.save()
        assert Exercise.objects.filter(slug="a").exists()

    def test_invalid_since_is_a_400(self, client):
        resp = client.get("/api/v1/reference/exercises/?since=not-a-number")
        assert resp.status_code == 400

    def test_response_never_exposes_the_numeric_id(self, client):
        make_exercise("a")
        resp = client.get("/api/v1/reference/exercises/")
        assert "id" not in resp.data[0]

    def test_does_not_require_auth(self, client):
        resp = client.get("/api/v1/reference/exercises/")
        assert resp.status_code == 200


class TestFoodDelta:
    def test_foods_are_available_for_offline_sync(self, client):
        food = FoodItem.objects.create(
            slug="banana",
            name={"en": "Banana"},
            kcal_per_100g=Decimal("89"),
            protein_g=Decimal("1.09"),
            carbs_g=Decimal("22.84"),
            fat_g=Decimal("0.33"),
            default_portion_g=Decimal("118"),
            portion_label="1 medium banana ~118g",
            source="IFCT 2017",
            barcode="8900000000001",
        )
        resp = client.get("/api/v1/reference/foods/")
        assert resp.status_code == 200
        assert resp.data[0]["slug"] == food.slug
        assert resp.data[0]["default_portion_g"] == "118.00"
        assert resp.data[0]["barcode"] == "8900000000001"
        assert resp.data[0]["item_type"] == "ingredient"
        assert "id" not in resp.data[0]


class TestRecipeDelta:
    def test_recipe_response_computes_macros_and_uses_serving_as_default(self, client):
        ingredient = FoodItem.objects.create(
            slug="ifct-a015",
            name={"en": "Rice, raw, milled"},
            kcal_per_100g=Decimal("350.00"),
            protein_g=Decimal("8.00"),
            carbs_g=Decimal("75.00"),
            fat_g=Decimal("1.00"),
            default_portion_g=Decimal("100.00"),
            portion_label="100 g",
            source="ifct_2017",
        )
        recipe = Recipe.objects.create(
            slug="plain-rice",
            name={"en": "Plain rice"},
            serving_description="1 bowl, ~270g",
            serving_grams_total=Decimal("270.00"),
        )
        RecipeIngredient.objects.create(
            recipe=recipe,
            ingredient=ingredient,
            grams_in_serving=Decimal("90.00"),
        )

        response = client.get("/api/v1/reference/recipes/")

        assert response.status_code == 200
        row = response.data[0]
        assert row["item_type"] == "recipe"
        assert row["default_portion_g"] == "270.00"
        assert row["portion_label"] == "1 bowl, ~270g"
        assert row["kcal_per_100g"] == "116.67"
        assert row["ingredients"] == [{"ingredient_slug": "ifct-a015", "grams_in_serving": "90.00"}]

    def test_ingredient_change_republishes_recipe_with_new_computation(self, client):
        ingredient = FoodItem.objects.create(
            slug="ifct-b010",
            name={"en": "Green gram, dal"},
            kcal_per_100g=Decimal("300.00"),
            protein_g=Decimal("20.00"),
            carbs_g=Decimal("50.00"),
            fat_g=Decimal("1.00"),
            default_portion_g=Decimal("100.00"),
            portion_label="100 g",
            source="ifct_2017",
        )
        recipe = Recipe.objects.create(
            slug="moong-dal",
            name={"en": "Moong dal"},
            serving_description="1 katori, ~200g",
            serving_grams_total=Decimal("200.00"),
        )
        RecipeIngredient.objects.create(
            recipe=recipe,
            ingredient=ingredient,
            grams_in_serving=Decimal("50.00"),
        )
        recipe.refresh_from_db()
        watermark = recipe.updated_at_version

        ingredient.kcal_per_100g = Decimal("400.00")
        ingredient.save()
        response = client.get(f"/api/v1/reference/recipes/?since={watermark}")

        assert len(response.data) == 1
        assert response.data[0]["slug"] == "moong-dal"
        assert response.data[0]["kcal_per_100g"] == "100.00"
