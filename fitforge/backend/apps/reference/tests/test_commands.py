import json
from decimal import Decimal

import pytest
from django.core.management import call_command

from apps.reference.management.commands.seed_exercises import DEFAULT_SEED_PATH
from apps.reference.management.commands.seed_foods import DEFAULT_SEED_PATH as FOOD_SEED_PATH
from apps.reference.models import Exercise, FoodItem, ReferenceVersion

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

    def test_export_includes_foods_without_numeric_ids(self, tmp_path):
        call_command("seed_foods")
        output = tmp_path / "bundle.json"
        call_command("export_reference_bundle", output=str(output))

        foods = json.loads(output.read_text())["foods"]
        assert len(foods) == FoodItem.objects.filter(is_active=True).count()
        assert all("id" not in food for food in foods)
