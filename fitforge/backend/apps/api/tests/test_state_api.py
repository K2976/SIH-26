import uuid
from datetime import timedelta
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from apps.accounts.models import Goal, UserProfile
from apps.diary.models import MealItem, MealLog, SetLog, WeightLog, WorkoutSession
from apps.reference.models import Exercise

User = get_user_model()
pytestmark = pytest.mark.django_db


def _diary_kwargs(**overrides):
    kwargs = {
        "client_uuid": uuid.uuid4(),
        "occurred_at": timezone.now(),
        "utc_offset": "+00:00",
        "source": "manual",
    }
    kwargs.update(overrides)
    return kwargs


@pytest.fixture
def user():
    # Mirrors SignupSerializer.create(): every real user gets an (initially
    # empty) profile, so this fixture matches what onboarding produces.
    created = User.objects.create_user(
        username="state@example.com", email="state@example.com", password="password"
    )
    UserProfile.objects.create(user=created)
    return created


@pytest.fixture
def api_client(user):
    client = APIClient()
    client.force_authenticate(user=user)
    return client


class TestStateEndpointEmptyUser:
    def test_returns_a_coherent_all_null_object(self, api_client):
        resp = api_client.get(reverse("state"))
        assert resp.status_code == 200
        data = resp.data

        assert data["profile"]["age"] is None
        assert data["goal"]["energy_direction"] is None

        energy = data["energy"]
        assert energy["maintenance_kcal"] is None
        assert energy["maintenance_source"] is None
        assert energy["consumed_kcal_today"] is None
        assert energy["burned_kcal_today"] is None
        assert energy["week_balance_kcal"] is None
        assert energy["data_sufficiency"] == "insufficient"

        training = data["training"]
        assert training["week_volume_planned"] is None
        assert training["week_volume_completed"] is None
        assert training["volume_by_muscle_group"] is None
        assert training["adherence_7d"] is None
        # No diary data at all, ever: unlike a "0 days since rest" user who
        # has logged something else, this user gets null, not a fabricated 0.
        assert training["days_since_rest"] is None

        body = data["body"]
        assert body["latest_weight_kg"] is None
        assert body["weight_trend_kg_per_week"] is None
        assert body["trend_confidence"] is None

        cycle = data["cycle"]
        assert cycle["enabled"] is False
        assert cycle["phase"] is None  # never a default middle value

        flags = data["flags"]
        assert flags["missing_food_days_7d"] == 7
        assert flags["missing_weight_days_14d"] == 14
        assert flags["goal_recently_changed"] is False
        assert flags["insufficient_data_for_adaptation"] is True

        assert data["plan_delta"] is None

    def test_requires_auth(self):
        resp = APIClient().get(reverse("state"))
        assert resp.status_code == 401


class TestStateEndpointSeededUser:
    @pytest.fixture
    def seeded_user(self, user):
        profile = user.profile
        profile.date_of_birth = timezone.now().date().replace(year=1996)
        profile.sex_at_birth = UserProfile.SexAtBirth.MALE
        profile.height_cm = Decimal("180.0")
        profile.current_weight_kg = Decimal("80.0")
        profile.activity_level = UserProfile.ActivityLevel.MODERATELY_ACTIVE
        profile.save()

        Goal.objects.create(
            user=user,
            energy_direction=Goal.EnergyDirection.CUT,
            training_focus=Goal.TrainingFocus.HYPERTROPHY,
            target_rate_kg_per_week=Decimal("0.50"),
        )

        exercise = Exercise.objects.create(
            slug="bench-press",
            name={"en": "Bench Press"},
            instructions={"en": "..."},
            primary_muscles=["chest"],
            movement_pattern=Exercise.MovementPattern.PUSH_HORIZONTAL,
            met_value=Decimal("6.0"),
        )

        now = timezone.now()
        for days_ago, weight in ((8, "81.5"), (4, "80.8"), (1, "80.2")):
            WeightLog.objects.create(
                user=user,
                weight_kg=Decimal(weight),
                **_diary_kwargs(occurred_at=now - timedelta(days=days_ago)),
            )

        session = WorkoutSession.objects.create(
            user=user, duration_minutes=45, **_diary_kwargs(occurred_at=now)
        )
        SetLog.objects.create(
            user=user,
            session=session,
            exercise=exercise,
            set_index=1,
            reps=8,
            weight_kg=Decimal("60.0"),
            **_diary_kwargs(occurred_at=now),
        )

        meal = MealLog.objects.create(user=user, **_diary_kwargs(occurred_at=now))
        MealItem.objects.create(
            user=user,
            log=meal,
            food_slug="chicken-rice",
            quantity=Decimal("300.0"),
            calories=Decimal("650.0"),
            protein_g=Decimal("50.0"),
            carbs_g=Decimal("70.0"),
            fat_g=Decimal("12.0"),
            **_diary_kwargs(occurred_at=now),
        )
        return user

    def test_returns_a_complete_object(self, api_client, seeded_user):
        resp = api_client.get(reverse("state"))
        assert resp.status_code == 200
        data = resp.data

        assert data["profile"]["age"] is not None
        assert data["goal"]["energy_direction"] == "cut"

        energy = data["energy"]
        assert energy["maintenance_kcal"] is not None
        assert energy["maintenance_source"] == "equation"
        assert energy["maintenance_confidence"] is not None
        assert energy["target_kcal_today"] < energy["maintenance_kcal"]  # cut goal
        assert energy["consumed_kcal_today"] == pytest.approx(650.0)
        assert energy["burned_kcal_today"] is not None

        training = data["training"]
        assert training["week_volume_completed"] == pytest.approx(60.0 * 8)
        assert training["volume_by_muscle_group"] == {"chest": pytest.approx(480.0)}
        assert training["days_since_rest"] == 1  # trained today: a 1-day streak

        body = data["body"]
        assert body["latest_weight_kg"] == pytest.approx(80.2)
        assert body["weight_trend_kg_per_week"] is not None
        assert body["weight_trend_kg_per_week"] < 0  # losing weight
        assert body["trend_confidence"] is not None

        assert data["flags"]["missing_weight_days_14d"] < 14

        plan_delta = data["plan_delta"]
        assert plan_delta is not None
        assert plan_delta["target_kcal"] is not None
        assert len(plan_delta["reason"]) > 0
        assert isinstance(plan_delta["clamped_by"], list)
        assert training["readiness_ceiling_today"] is not None
