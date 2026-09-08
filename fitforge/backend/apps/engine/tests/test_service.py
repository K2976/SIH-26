import uuid
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from apps.accounts.models import UserProfile
from apps.engine.models import DailyState
from apps.engine.service import rebuild_all_daily_states, save_daily_state

User = get_user_model()
pytestmark = pytest.mark.django_db


@pytest.fixture
def user():
    created = User.objects.create_user(
        username="engine@example.com", email="engine@example.com", password="password"
    )
    UserProfile.objects.create(user=created)
    return created


class TestSaveDailyState:
    def test_writes_a_cache_row_for_an_empty_day(self, user):
        today = timezone.now().date()
        obj = save_daily_state(user, today)

        assert obj.user_id == user.id
        assert obj.local_date == today
        assert obj.consumed_kcal is None  # nothing logged: null, not 0
        assert obj.missing_food_days_7d == 7
        assert obj.partial_fields == []

    def test_is_idempotent_per_user_per_day(self, user):
        today = timezone.now().date()
        save_daily_state(user, today)
        save_daily_state(user, today)
        assert DailyState.objects.filter(user=user, local_date=today).count() == 1


class TestRebuildCommand:
    def test_rebuild_all_daily_states_covers_the_users_diary_span(self, user):
        from apps.diary.models import WeightLog

        now = timezone.now()
        WeightLog.objects.create(
            user=user,
            weight_kg=Decimal("75.0"),
            client_uuid=uuid.uuid4(),
            occurred_at=now,
            utc_offset="+00:00",
            source="manual",
        )
        count = rebuild_all_daily_states()
        assert count >= 1
        assert DailyState.objects.filter(user=user, local_date=now.date()).exists()

    def test_management_command_runs(self, user):
        call_command("rebuild_daily_state")  # smoke test: no diary data, should just no-op


class TestSyncTriggersRecompute:
    def test_successful_sync_writes_a_daily_state_row(self, user):
        client = APIClient()
        client.force_authenticate(user=user)
        now = timezone.now()

        resp = client.post(
            "/api/v1/sync/",
            {
                "events": [
                    {
                        "type": "weight_log",
                        "client_uuid": str(uuid.uuid4()),
                        "occurred_at": now.isoformat(),
                        "utc_offset": "+00:00",
                        "weight_kg": "79.5",
                    }
                ]
            },
            format="json",
        )
        assert resp.status_code == 200
        assert resp.data["success_count"] == 1
        assert DailyState.objects.filter(user=user, local_date=now.date()).exists()

    def test_state_endpoint_reflects_synced_data_immediately(self, user):
        client = APIClient()
        client.force_authenticate(user=user)
        now = timezone.now()
        client.post(
            "/api/v1/sync/",
            {
                "events": [
                    {
                        "type": "weight_log",
                        "client_uuid": str(uuid.uuid4()),
                        "occurred_at": now.isoformat(),
                        "utc_offset": "+00:00",
                        "weight_kg": "79.5",
                    }
                ]
            },
            format="json",
        )
        resp = client.get(reverse("state"))
        assert resp.data["body"]["latest_weight_kg"] == pytest.approx(79.5)
