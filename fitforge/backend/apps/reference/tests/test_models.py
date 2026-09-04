from decimal import Decimal

import pytest

from apps.reference.models import Exercise, ReferenceVersion

pytestmark = pytest.mark.django_db


def make_exercise(slug="test-exercise", **overrides):
    fields = {
        "name": {"en": "Test Exercise"},
        "instructions": {"en": "Do the thing."},
        "movement_pattern": Exercise.MovementPattern.SQUAT,
        "met_value": Decimal("5.0"),
    }
    fields.update(overrides)
    return Exercise.objects.create(slug=slug, **fields)


class TestReferenceVersion:
    def test_starts_at_zero(self):
        assert ReferenceVersion.current() == 0

    def test_bump_increments_and_returns_new_value(self):
        assert ReferenceVersion.bump() == 1
        assert ReferenceVersion.bump() == 2
        assert ReferenceVersion.current() == 2


class TestExerciseVersioning:
    def test_save_stamps_current_reference_version(self):
        exercise = make_exercise()
        assert exercise.updated_at_version == ReferenceVersion.current()

    def test_each_save_bumps_the_global_version(self):
        exercise = make_exercise()
        first_version = exercise.updated_at_version
        exercise.met_value = Decimal("6.0")
        exercise.save()
        assert exercise.updated_at_version == first_version + 1


class TestLocalizedFallback:
    def test_falls_back_to_english_for_missing_locale(self):
        exercise = make_exercise(name={"en": "Squat", "hi": "Squat (hi)"})
        assert exercise.localized_name("fr") == "Squat"
        assert exercise.localized_name("hi") == "Squat (hi)"
        assert exercise.localized_name() == "Squat"
