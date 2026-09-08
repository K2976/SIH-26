import datetime
import uuid

import pytest
from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework.test import APIClient

from apps.accounts.models import UserProfile
from apps.diary.models import CycleLog, CycleProfile
from apps.engine.models import CyclePattern

User = get_user_model()
pytestmark = pytest.mark.django_db


@pytest.fixture
def user():
    created = User.objects.create_user(
        username="cycleapi@example.com", email="cycleapi@example.com", password="password"
    )
    UserProfile.objects.create(user=created, tracks_cycle=True)
    return created


@pytest.fixture
def client(user):
    api_client = APIClient()
    api_client.force_authenticate(user=user)
    return api_client


class TestCycleProfileView:
    def test_get_creates_a_default_profile(self, client):
        response = client.get(reverse("cycle-profile"))
        assert response.status_code == 200
        assert response.data["tracking_mode"] == "unknown"

    def test_patch_sets_tracking_mode(self, client, user):
        response = client.patch(reverse("cycle-profile"), {"tracking_mode": "natural"})
        assert response.status_code == 200
        assert CycleProfile.objects.get(user=user).tracking_mode == "natural"


class TestCycleDataView:
    def _log(self, user):
        return CycleLog.objects.create(
            user=user,
            client_uuid=uuid.uuid4(),
            occurred_at=datetime.datetime.now(datetime.UTC),
            utc_offset="+00:00",
            source="manual",
            date=datetime.date.today(),
            event="period_start",
        )

    def test_export_includes_profile_logs_and_patterns(self, client, user):
        CycleProfile.objects.create(user=user, tracking_mode="natural")
        self._log(user)
        CyclePattern.objects.create(user=user, cycle_day=1, sample_cycles=2, confidence=0.5)

        response = client.get(reverse("cycle-data"))

        assert response.status_code == 200
        assert response.data["profile"]["tracking_mode"] == "natural"
        assert len(response.data["logs"]) == 1
        assert len(response.data["learned_pattern"]) == 1

    def test_export_is_empty_shaped_with_no_data(self, client):
        response = client.get(reverse("cycle-data"))
        assert response.status_code == 200
        assert response.data["profile"] is None
        assert response.data["logs"] == []
        assert response.data["learned_pattern"] == []

    def test_delete_removes_everything(self, client, user):
        CycleProfile.objects.create(user=user, tracking_mode="natural")
        self._log(user)
        CyclePattern.objects.create(user=user, cycle_day=1, sample_cycles=2, confidence=0.5)

        response = client.delete(reverse("cycle-data"))

        assert response.status_code == 204
        assert not CycleLog.objects.filter(user=user).exists()
        assert not CyclePattern.objects.filter(user=user).exists()
        assert not CycleProfile.objects.filter(user=user).exists()

    def test_delete_does_not_touch_other_users_data(self, client, user):
        other = User.objects.create_user(
            username="other@example.com", email="other@example.com", password="password"
        )
        self._log(other)

        client.delete(reverse("cycle-data"))

        assert CycleLog.objects.filter(user=other).exists()

    def test_requires_authentication(self):
        anon = APIClient()
        assert anon.get(reverse("cycle-data")).status_code == 401
        assert anon.get(reverse("cycle-profile")).status_code == 401
