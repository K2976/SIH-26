import datetime

import pytest
from django.contrib.auth import get_user_model
from django.core.management import call_command
from rest_framework.test import APIClient

from apps.accounts.management.commands.seed_presenter_demo import (
    PRESENTER_DEMO_EMAIL,
    PRESENTER_DEMO_PASSWORD,
)
from apps.diary.models import CycleLog, MealLog, PlannedSession, SetLog
from apps.engine.models import CyclePattern, DailyState
from apps.engine.service import compute_user_state

User = get_user_model()

pytestmark = pytest.mark.django_db


class TestSeedPresenterDemo:
    def test_populates_real_paths_and_computes_the_safety_flag(self):
        call_command("seed_presenter_demo")
        user = User.objects.get(email=PRESENTER_DEMO_EMAIL)

        login = APIClient().post(
            "/api/v1/auth/login/",
            {"email": PRESENTER_DEMO_EMAIL, "password": PRESENTER_DEMO_PASSWORD},
            format="json",
        )
        assert login.status_code == 200

        assert CycleLog.objects.filter(user=user).count() == 61
        assert MealLog.objects.filter(user=user).count() == 57  # four genuine food-log gaps
        assert PlannedSession.objects.filter(user=user, status="skipped").count() >= 5
        assert set(
            SetLog.objects.filter(user=user, source="camera").values_list("exercise_id", flat=True)
        ) >= {"barbell-back-squat", "push-up", "dumbbell-bicep-curl"}
        assert DailyState.objects.filter(user=user).count() >= 61
        assert CyclePattern.objects.filter(user=user).exists()

        state = compute_user_state(user)
        assert state["energy"]["data_sufficiency"] == "sufficient"
        assert state["cycle"]["energy_availability_flag"] == "energy_availability_review"
        assert any(
            clamp.startswith("energy_availability_floor")
            for clamp in state["plan_delta"]["clamped_by"]
        )
        assert state["training"]["days_since_rest"] >= 6

    def test_is_idempotent_and_keeps_dates_in_the_requested_window(self):
        call_command("seed_presenter_demo")
        first = User.objects.get(email=PRESENTER_DEMO_EMAIL)
        first_counts = (
            MealLog.objects.filter(user=first).count(),
            SetLog.objects.filter(user=first).count(),
        )

        call_command("seed_presenter_demo")
        user = User.objects.get(email=PRESENTER_DEMO_EMAIL)
        assert User.objects.filter(email=PRESENTER_DEMO_EMAIL).count() == 1
        assert (
            MealLog.objects.filter(user=user).count(),
            SetLog.objects.filter(user=user).count(),
        ) == first_counts

        today = compute_user_state(user)["as_of"]
        start = today - datetime.timedelta(days=60)
        dates = CycleLog.objects.filter(user=user).values_list("date", flat=True)
        assert min(dates) == start
        assert max(dates) == today
