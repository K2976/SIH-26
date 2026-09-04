import uuid
import datetime
import pytest
from django.urls import reverse
from rest_framework.test import APIClient
from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.diary.models import WorkoutSession, PlannedSession

User = get_user_model()

@pytest.fixture
def user():
    return User.objects.create_user(username="testuser", email="test@example.com", password="password")

@pytest.fixture
def api_client(user):
    client = APIClient()
    client.force_authenticate(user=user)
    return client

@pytest.mark.django_db
def test_sync_idempotency_and_partial_failure(api_client, user):
    url = reverse("sync")
    session_uuid = str(uuid.uuid4())
    
    now = timezone.now()
    valid_event = {
        "type": "workout_session",
        "client_uuid": session_uuid,
        "occurred_at": now.isoformat(),
        "utc_offset": "+00:00",
        "duration_minutes": 60,
    }
    
    # Invalid event missing client_uuid
    invalid_event = {
        "type": "workout_session",
        "occurred_at": now.isoformat(),
    }
    
    payload = {
        "events": [valid_event, invalid_event]
    }
    
    # First request
    response = api_client.post(url, payload, format="json")
    assert response.status_code == 200
    assert response.data["success_count"] == 1
    assert len(response.data["errors"]) == 1
    assert WorkoutSession.objects.filter(client_uuid=session_uuid).count() == 1
    
    # Second request (Idempotency check)
    response = api_client.post(url, payload, format="json")
    assert response.status_code == 200
    assert response.data["success_count"] == 1
    # Count should still be 1
    assert WorkoutSession.objects.filter(client_uuid=session_uuid).count() == 1

@pytest.mark.django_db
def test_sync_timestamp_rejection(api_client, user):
    url = reverse("sync")
    
    now = timezone.now()
    future_date = now + datetime.timedelta(days=2)
    past_date = datetime.datetime(1980, 1, 1, tzinfo=datetime.timezone.utc)
    
    payload = {
        "events": [
            {
                "type": "workout_session",
                "client_uuid": str(uuid.uuid4()),
                "occurred_at": future_date.isoformat(),
            },
            {
                "type": "workout_session",
                "client_uuid": str(uuid.uuid4()),
                "occurred_at": past_date.isoformat(),
            }
        ]
    }
    
    response = api_client.post(url, payload, format="json")
    assert response.status_code == 200
    assert response.data["success_count"] == 0
    assert len(response.data["errors"]) == 2
    
    error_texts = [e["error"] for e in response.data["errors"]]
    assert any("more than one day in the future" in e for e in error_texts)
    assert any("implausibly far in the past" in e for e in error_texts)
