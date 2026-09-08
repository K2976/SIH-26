import datetime
import uuid

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.accounts.models import UserProfile
from apps.diary.models import CycleLog, CycleProfile
from apps.engine import cycle_service
from apps.engine.constants import ENERGY_AVAILABILITY_FLAG
from apps.engine.models import CyclePattern, DailyState
from apps.engine.service import compute_user_state

User = get_user_model()
pytestmark = pytest.mark.django_db


@pytest.fixture
def user():
    created = User.objects.create_user(
        username="cycle@example.com", email="cycle@example.com", password="password"
    )
    UserProfile.objects.create(
        user=created,
        tracks_cycle=True,
        date_of_birth=datetime.date(1996, 1, 1),
        sex_at_birth="female",
        height_cm=165,
        current_weight_kg=60,
        activity_level="moderately_active",
    )
    return created


def _log_period_start(user, local_date):
    occurred_at = datetime.datetime.combine(local_date, datetime.time(12, 0), tzinfo=datetime.UTC)
    return CycleLog.objects.create(
        user=user,
        client_uuid=uuid.uuid4(),
        occurred_at=occurred_at,
        utc_offset="+00:00",
        source="manual",
        date=local_date,
        event="period_start",
    )


def _log_symptoms(user, local_date, energy_rating=None, symptoms=None):
    occurred_at = datetime.datetime.combine(local_date, datetime.time(9, 0), tzinfo=datetime.UTC)
    return CycleLog.objects.create(
        user=user,
        client_uuid=uuid.uuid4(),
        occurred_at=occurred_at,
        utc_offset="+00:00",
        source="manual",
        date=local_date,
        event="",
        symptoms=symptoms or [],
        energy_rating=energy_rating,
    )


class TestSelfReportedCycleInputs:
    def test_missing_when_nothing_logged_today(self, user):
        today = timezone.now().date()
        energy, symptoms = cycle_service.self_reported_cycle_inputs(user, today)
        assert energy is None
        assert symptoms is None

    def test_returns_logged_values(self, user):
        today = timezone.now().date()
        _log_symptoms(user, today, energy_rating=2, symptoms=["cramps", "fatigue"])
        energy, symptoms = cycle_service.self_reported_cycle_inputs(user, today)
        assert energy == 2
        assert symptoms == ["cramps", "fatigue"]

    def test_explicit_empty_symptom_list_is_not_missing(self, user):
        today = timezone.now().date()
        _log_symptoms(user, today, energy_rating=4, symptoms=[])
        energy, symptoms = cycle_service.self_reported_cycle_inputs(user, today)
        assert energy == 4
        assert symptoms == []  # a real "logged, no symptoms" signal, not None


class TestEnergyAvailabilityFlag:
    """Done-when criterion: fires on a constructed history, not on a normal one."""

    def _seed_overdue_natural_history(self, user, as_of):
        # Last period started 43 days ago; typical length ~28 days, so the
        # predicted next period was due 15 days ago — well past the
        # PERIOD_OVERDUE_DAYS_THRESHOLD.
        last_start = as_of - datetime.timedelta(days=15 + 28)
        starts = [last_start - datetime.timedelta(days=28 * i) for i in range(3, -1, -1)]
        for d in starts:
            _log_period_start(user, d)
        CycleProfile.objects.create(user=user, tracking_mode=CycleProfile.TrackingMode.NATURAL)
        return last_start

    def _seed_deficit_streak(self, user, as_of, days, deficit=True):
        for i in range(1, days + 1):
            d = as_of - datetime.timedelta(days=i)
            DailyState.objects.create(
                user=user,
                local_date=d,
                consumed_kcal=1400.0 if deficit else 2100.0,
                maintenance_kcal=2000.0,
                missing_food_days_7d=0,
                missing_weight_days_14d=0,
                goal_recently_changed=False,
                insufficient_data_for_adaptation=False,
                energy_data_sufficiency="sufficient",
            )

    def test_fires_on_constructed_history(self, user):
        as_of = timezone.now().date()
        self._seed_overdue_natural_history(user, as_of)
        self._seed_deficit_streak(user, as_of, days=14, deficit=True)

        flag = cycle_service.compute_energy_availability_flag(
            user, as_of, CycleProfile.TrackingMode.NATURAL, days_since_rest=6
        )
        assert flag == ENERGY_AVAILABILITY_FLAG

    def test_does_not_fire_when_period_is_not_overdue(self, user):
        as_of = timezone.now().date()
        last_start = as_of - datetime.timedelta(days=2)  # period just started, not overdue
        for i in range(3, -1, -1):
            _log_period_start(user, last_start - datetime.timedelta(days=28 * i))
        self._seed_deficit_streak(user, as_of, days=14, deficit=True)

        flag = cycle_service.compute_energy_availability_flag(
            user, as_of, CycleProfile.TrackingMode.NATURAL, days_since_rest=6
        )
        assert flag is None

    def test_does_not_fire_on_a_normal_history(self, user):
        """Overdue period, but intake is normal (no deficit) and training
        load is low — a realistic non-alarming pattern."""
        as_of = timezone.now().date()
        self._seed_overdue_natural_history(user, as_of)
        self._seed_deficit_streak(user, as_of, days=14, deficit=False)

        flag = cycle_service.compute_energy_availability_flag(
            user, as_of, CycleProfile.TrackingMode.NATURAL, days_since_rest=1
        )
        assert flag is None

    def test_does_not_fire_for_hormonal_contraception(self, user):
        as_of = timezone.now().date()
        self._seed_overdue_natural_history(user, as_of)
        self._seed_deficit_streak(user, as_of, days=14, deficit=True)

        flag = cycle_service.compute_energy_availability_flag(
            user, as_of, CycleProfile.TrackingMode.HORMONAL_CONTRACEPTION, days_since_rest=6
        )
        assert flag is None


class TestSymptomsMoveReadinessCeiling:
    """Done-when criterion: symptoms measurably move the readiness ceiling."""

    def test_logged_symptoms_change_the_ceiling_end_to_end(self, user):
        today = timezone.now().date()
        state_without = compute_user_state(user, as_of=today)

        _log_symptoms(user, today, energy_rating=1, symptoms=["cramps", "fatigue", "headache"])
        state_with = compute_user_state(user, as_of=today)

        ceiling_without = state_without["training"]["readiness_ceiling_today"]
        ceiling_with = state_with["training"]["readiness_ceiling_today"]
        assert ceiling_without is not None
        assert ceiling_with is not None
        assert ceiling_with < ceiling_without

    def test_plan_delta_never_carries_a_phase_field(self, user):
        today = timezone.now().date()
        state = compute_user_state(user, as_of=today)
        if state["plan_delta"]:
            assert "phase" not in state["plan_delta"]
        # CycleProfile was never created for this user -> tracking_mode
        # defaults to unknown -> phase stays null (never a guessed value).
        assert state["cycle"]["phase"] is None


class TestRecomputeCyclePattern:
    def test_reports_sample_size(self, user):
        as_of = timezone.now().date()
        starts = [as_of - datetime.timedelta(days=28 * i) for i in range(3, -1, -1)]
        for d in starts:
            _log_period_start(user, d)
        # Log the same cycle-day (day 3 of each cycle) with a symptom.
        for start in starts[:-1]:
            day = start + datetime.timedelta(days=2)
            _log_symptoms(user, day, energy_rating=2, symptoms=["cramps"])

        count = cycle_service.recompute_cycle_pattern_for_user(user)
        assert count > 0

        row = CyclePattern.objects.get(user=user, cycle_day=3)
        assert row.sample_cycles == len(starts) - 1
        assert 0.0 < row.confidence <= 1.0
        assert row.symptom_frequency.get("cramps") == 1.0

    def test_clears_patterns_when_history_too_sparse(self, user):
        CyclePattern.objects.create(user=user, cycle_day=1, sample_cycles=5, confidence=1.0)
        count = cycle_service.recompute_cycle_pattern_for_user(user)
        assert count == 0
        assert not CyclePattern.objects.filter(user=user).exists()
