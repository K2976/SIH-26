import pytest
from django.contrib.auth import get_user_model
from django.core.management import call_command
from rest_framework.test import APIClient

from apps.accounts.management.commands.seed_demo_account import DEMO_EMAIL, DEMO_PASSWORD
from apps.diary.models import CycleLog, MealItem, MealLog, SetLog, WeightLog, WorkoutSession
from apps.engine.service import compute_user_state

User = get_user_model()

pytestmark = pytest.mark.django_db


def _row_counts(user):
    return {
        "workouts": WorkoutSession.objects.filter(user=user).count(),
        "sets": SetLog.objects.filter(user=user).count(),
        "meals": MealLog.objects.filter(user=user).count(),
        "meal_items": MealItem.objects.filter(user=user).count(),
        "weights": WeightLog.objects.filter(user=user).count(),
        "cycle_logs": CycleLog.objects.filter(user=user).count(),
    }


class TestSeedDemoAccount:
    def test_is_idempotent(self):
        call_command("seed_demo_account")
        user = User.objects.get(email=DEMO_EMAIL)
        first_counts = _row_counts(user)
        assert all(count > 0 for count in first_counts.values())

        call_command("seed_demo_account")
        user = User.objects.get(email=DEMO_EMAIL)  # re-fetch: the row was deleted and recreated
        assert _row_counts(user) == first_counts
        assert User.objects.filter(email=DEMO_EMAIL).count() == 1

    def test_logs_in_through_the_normal_auth_endpoint(self):
        call_command("seed_demo_account")
        response = APIClient().post(
            "/api/v1/auth/login/",
            {"email": DEMO_EMAIL, "password": DEMO_PASSWORD},
            format="json",
        )
        assert response.status_code == 200
        assert "access" in response.data

    def test_engine_computes_real_output_from_seeded_rows(self):
        """The whole point of this command: nothing here is hardcoded, so the
        calculator/engine must produce real numbers from the seeded diary."""
        call_command("seed_demo_account")
        user = User.objects.get(email=DEMO_EMAIL)

        state = compute_user_state(user)

        # Enough profile + goal data was seeded for maintenance and a target.
        assert state["energy"]["maintenance_kcal"] is not None
        assert state["energy"]["target_kcal_today"] is not None
        # Weeks of varied weigh-ins produce a real regression, not a gap.
        assert state["body"]["weight_trend_kg_per_week"] is not None
        assert state["body"]["trend_confidence"] is not None
        # Weeks of workouts produce real weekly training volume.
        assert state["training"]["week_volume_completed"] is not None
        # The adaptive engine ran end-to-end (budgets + floors) and gave a
        # real reason, not the "no maintenance estimate" empty-state.
        assert state["plan_delta"] is not None
        assert state["plan_delta"]["reason"]
        # tracks_cycle=True and real CycleLog history — enabled, but phase
        # derivation (§10) isn't built yet, so it stays honestly null for
        # every account, demo included. See docs/DECISIONS.md.
        assert state["cycle"]["enabled"] is True
        assert state["cycle"]["phase"] is None
