from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model

from apps.accounts.constants import MAX_TARGET_RATE_KG_PER_WEEK, MIN_TARGET_RATE_KG_PER_WEEK
from apps.accounts.models import Goal, UserProfile, clamp_target_rate, create_goal, current_goal

User = get_user_model()

pytestmark = pytest.mark.django_db


@pytest.fixture
def user():
    return User.objects.create_user(username="a@example.com", email="a@example.com")


class TestFieldState:
    def test_not_asked_when_null_and_not_declined(self, user):
        profile = UserProfile.objects.create(user=user)
        assert profile.field_state("height_cm") == UserProfile.NOT_ASKED

    def test_known_when_value_present(self, user):
        profile = UserProfile.objects.create(user=user, height_cm=Decimal("170.0"))
        assert profile.field_state("height_cm") == UserProfile.KNOWN

    def test_declined_when_null_and_in_declined_fields(self, user):
        profile = UserProfile.objects.create(user=user, declined_fields=["height_cm"])
        assert profile.field_state("height_cm") == UserProfile.DECLINED

    def test_declined_takes_precedence_regardless_of_stored_value(self, user):
        # Declining clears the value in normal flow, but the state helper
        # must not silently collapse to "known" if that invariant is ever
        # violated upstream — declined_fields is authoritative.
        profile = UserProfile.objects.create(
            user=user, height_cm=Decimal("170.0"), declined_fields=["height_cm"]
        )
        assert profile.field_state("height_cm") == UserProfile.DECLINED

    def test_rejects_non_optional_field(self, user):
        profile = UserProfile.objects.create(user=user)
        with pytest.raises(ValueError):
            profile.field_state("timezone")

    def test_three_states_are_distinct(self, user):
        profile = UserProfile.objects.create(
            user=user, waist_cm=Decimal("80.0"), declined_fields=["resting_hr"]
        )
        assert profile.field_state("waist_cm") == UserProfile.KNOWN
        assert profile.field_state("resting_hr") == UserProfile.DECLINED
        assert profile.field_state("sex_at_birth") == UserProfile.NOT_ASKED

    def test_current_weight_kg_three_states(self, user):
        profile = UserProfile.objects.create(user=user)
        assert profile.field_state("current_weight_kg") == UserProfile.NOT_ASKED

        profile.current_weight_kg = Decimal("72.5")
        profile.save()
        assert profile.field_state("current_weight_kg") == UserProfile.KNOWN

        profile.current_weight_kg = None
        profile.declined_fields = ["current_weight_kg"]
        profile.save()
        assert profile.field_state("current_weight_kg") == UserProfile.DECLINED

    def test_activity_level_three_states(self, user):
        profile = UserProfile.objects.create(user=user)
        assert profile.field_state("activity_level") == UserProfile.NOT_ASKED

        profile.activity_level = "moderately_active"
        profile.save()
        assert profile.field_state("activity_level") == UserProfile.KNOWN

        profile.activity_level = None
        profile.declined_fields = ["activity_level"]
        profile.save()
        assert profile.field_state("activity_level") == UserProfile.DECLINED


class TestGoalVersioning:
    def test_create_goal_opens_first_row(self, user):
        goal = create_goal(
            user=user,
            energy_direction=Goal.EnergyDirection.CUT,
            training_focus=Goal.TrainingFocus.STRENGTH,
            target_rate_kg_per_week=Decimal("0.3"),
            auto_adjust=True,
        )
        assert goal.ended_at is None
        assert current_goal(user) == goal

    def test_changing_goal_closes_old_row_and_opens_new_one(self, user):
        first = create_goal(
            user=user,
            energy_direction=Goal.EnergyDirection.CUT,
            training_focus=Goal.TrainingFocus.STRENGTH,
            target_rate_kg_per_week=Decimal("0.3"),
            auto_adjust=True,
        )
        second = create_goal(
            user=user,
            energy_direction=Goal.EnergyDirection.MAINTAIN,
            training_focus=Goal.TrainingFocus.HYPERTROPHY,
            target_rate_kg_per_week=Decimal("0.0"),
            auto_adjust=True,
        )

        first.refresh_from_db()
        assert first.ended_at is not None
        assert first.energy_direction == Goal.EnergyDirection.CUT  # never mutated in place

        assert second.ended_at is None
        assert current_goal(user) == second
        assert Goal.objects.filter(user=user).count() == 2

    def test_energy_direction_and_training_focus_are_independent_axes(self, user):
        # maintain + hypertrophy is recomp — no combined enum value needed.
        goal = create_goal(
            user=user,
            energy_direction=Goal.EnergyDirection.MAINTAIN,
            training_focus=Goal.TrainingFocus.HYPERTROPHY,
            target_rate_kg_per_week=Decimal("0.0"),
            auto_adjust=True,
        )
        assert goal.energy_direction == Goal.EnergyDirection.MAINTAIN
        assert goal.training_focus == Goal.TrainingFocus.HYPERTROPHY

    def test_current_goal_is_none_when_no_goal_exists(self, user):
        assert current_goal(user) is None


class TestClampTargetRate:
    def test_within_range_is_unchanged(self):
        value, reason = clamp_target_rate(Decimal("0.5"))
        assert value == Decimal("0.5")
        assert reason is None

    def test_above_max_is_clamped_down_with_reason(self):
        value, reason = clamp_target_rate(MAX_TARGET_RATE_KG_PER_WEEK + Decimal("5"))
        assert value == MAX_TARGET_RATE_KG_PER_WEEK
        assert reason is not None

    def test_below_min_is_clamped_up_with_reason(self):
        value, reason = clamp_target_rate(MIN_TARGET_RATE_KG_PER_WEEK - Decimal("5"))
        assert value == MIN_TARGET_RATE_KG_PER_WEEK
        assert reason is not None

    def test_create_goal_stores_clamped_value_and_reason_on_instance(self, user):
        goal = create_goal(
            user=user,
            energy_direction=Goal.EnergyDirection.CUT,
            training_focus=Goal.TrainingFocus.GENERAL,
            target_rate_kg_per_week=MAX_TARGET_RATE_KG_PER_WEEK + Decimal("10"),
            auto_adjust=True,
        )
        assert goal.target_rate_kg_per_week == MAX_TARGET_RATE_KG_PER_WEEK
        assert goal.clamp_reason is not None
