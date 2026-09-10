import json
from decimal import Decimal

import pytest
from django.core.management import call_command

from apps.diary.nutrition import nutrition_for_portion
from apps.reference.management.commands.seed_exercises import DEFAULT_SEED_PATH
from apps.reference.management.commands.seed_foods import DEFAULT_SEED_PATH as FOOD_SEED_PATH
from apps.reference.management.commands.seed_recipes import (
    DEFAULT_SEED_PATH as RECIPE_SEED_PATH,
)
from apps.reference.models import Exercise, FoodItem, Recipe, ReferenceVersion

pytestmark = pytest.mark.django_db


def _seed_exercise_count():
    return len(json.loads(DEFAULT_SEED_PATH.read_text())["exercises"])


class TestSeedExercises:
    def test_seed_creates_every_row_in_the_file(self):
        call_command("seed_exercises")
        assert Exercise.objects.count() == _seed_exercise_count()

    def test_seed_uses_slug_as_the_natural_key_no_duplicates(self):
        call_command("seed_exercises")
        call_command("seed_exercises")
        assert Exercise.objects.count() == _seed_exercise_count()

    def test_reseeding_unchanged_rows_is_a_no_op(self):
        call_command("seed_exercises")
        exercise = Exercise.objects.first()
        version_before = exercise.updated_at_version

        call_command("seed_exercises")

        exercise.refresh_from_db()
        assert exercise.updated_at_version == version_before

    def test_reseeding_corrects_a_drifted_field_without_duplicating(self):
        call_command("seed_exercises")
        count_before = Exercise.objects.count()
        exercise = Exercise.objects.first()
        exercise.met_value = Decimal("99.9")
        exercise.save()

        call_command("seed_exercises")

        exercise.refresh_from_db()
        assert Exercise.objects.count() == count_before
        assert exercise.met_value != Decimal("99.9")

    def test_every_seeded_exercise_has_a_met_value(self):
        call_command("seed_exercises")
        assert not Exercise.objects.filter(met_value__isnull=True).exists()


class TestExportReferenceBundle:
    def test_writes_active_exercises_with_current_version(self, tmp_path):
        call_command("seed_exercises")
        exercise = Exercise.objects.first()
        exercise.is_active = False
        exercise.save()

        output = tmp_path / "bundle.json"
        call_command("export_reference_bundle", output=str(output))

        bundle = json.loads(output.read_text())
        assert bundle["reference_version"] == ReferenceVersion.current()
        assert len(bundle["exercises"]) == Exercise.objects.filter(is_active=True).count()
        assert exercise.slug not in {row["slug"] for row in bundle["exercises"]}

    def test_exported_rows_have_no_numeric_id(self, tmp_path):
        call_command("seed_exercises")
        output = tmp_path / "bundle.json"
        call_command("export_reference_bundle", output=str(output))

        bundle = json.loads(output.read_text())
        assert "id" not in bundle["exercises"][0]


class TestSeedFoods:
    def test_seed_is_idempotent_and_includes_portion_defaults(self):
        expected = len(json.loads(FOOD_SEED_PATH.read_text())["foods"])
        call_command("seed_foods")
        call_command("seed_foods")

        assert FoodItem.objects.count() == expected
        assert not FoodItem.objects.filter(default_portion_g__lte=0).exists()

    def test_seed_contains_the_verified_complete_ifct_export(self):
        data = json.loads(FOOD_SEED_PATH.read_text())
        call_command("seed_foods")

        assert data["metadata"]["source_rows"] == 542
        assert data["metadata"]["source_columns"] == 421
        assert FoodItem.objects.count() == 542
        assert set(FoodItem.objects.values_list("source", flat=True)) == {"ifct_2017"}

    def test_export_includes_foods_without_numeric_ids(self, tmp_path):
        call_command("seed_foods")
        output = tmp_path / "bundle.json"
        call_command("export_reference_bundle", output=str(output))

        foods = json.loads(output.read_text())["foods"]
        assert len(foods) == FoodItem.objects.filter(is_active=True).count()
        assert all("id" not in food for food in foods)


class TestSeedRecipes:
    def test_seeds_at_least_fifteen_real_composed_recipes_idempotently(self):
        seed_rows = json.loads(RECIPE_SEED_PATH.read_text())["recipes"]
        expected = len(seed_rows)
        call_command("seed_foods")
        call_command("seed_recipes")
        versions = dict(Recipe.objects.values_list("slug", "updated_at_version"))

        call_command("seed_recipes")

        assert expected >= 15
        assert Recipe.objects.count() == expected
        assert dict(Recipe.objects.values_list("slug", "updated_at_version")) == versions
        assert Recipe.objects.filter(slug="vegetable-biryani").exists()
        assert all(recipe.ingredients.exists() for recipe in Recipe.objects.all())
        assert all(
            key not in row
            for row in seed_rows
            for key in ("kcal_per_100g", "protein_g", "carbs_g", "fat_g")
        )
        assert all(
            nutrition_for_portion(recipe, recipe.serving_grams_total).calories > 0
            for recipe in Recipe.objects.all()
        )

    def test_export_includes_computed_recipes_and_direct_portion_defaults(self, tmp_path):
        call_command("seed_foods")
        call_command("seed_recipes")
        output = tmp_path / "bundle.json"
        call_command("export_reference_bundle", output=str(output))

        recipes = json.loads(output.read_text())["recipes"]
        biryani = next(recipe for recipe in recipes if recipe["slug"] == "vegetable-biryani")
        assert len(recipes) == Recipe.objects.count()
        assert biryani["item_type"] == "recipe"
        assert biryani["default_portion_g"] == biryani["serving_grams_total"]
        assert biryani["portion_label"] == biryani["serving_description"]
        assert Decimal(biryani["kcal_per_100g"]) > 0
