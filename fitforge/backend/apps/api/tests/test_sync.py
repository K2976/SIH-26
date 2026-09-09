import datetime
import uuid

import pytest
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from apps.diary.models import MealItem, WorkoutSession

User = get_user_model()


@pytest.fixture
def user():
    return User.objects.create_user(
        username="testuser", email="test@example.com", password="password"
    )


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

    payload = {"events": [valid_event, invalid_event]}

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
    past_date = datetime.datetime(1980, 1, 1, tzinfo=datetime.UTC)

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
            },
        ]
    }

    response = api_client.post(url, payload, format="json")
    assert response.status_code == 200
    assert response.data["success_count"] == 0
    assert len(response.data["errors"]) == 2
    error_texts = [e["error"] for e in response.data["errors"]]
    assert any("more than one day in the future" in e for e in error_texts)
    assert any("implausibly far in the past" in e for e in error_texts)


@pytest.mark.django_db
def test_photo_search_and_barcode_create_equivalent_meal_items(api_client, user):
    now = timezone.now().isoformat()
    events = []
    for source in ("photo", "search", "barcode"):
        meal_uuid = str(uuid.uuid4())
        events.extend(
            [
                {
                    "type": "meal_log",
                    "client_uuid": meal_uuid,
                    "occurred_at": now,
                    "source": source,
                },
                {
                    "type": "meal_item",
                    "client_uuid": str(uuid.uuid4()),
                    "occurred_at": now,
                    "source": source,
                    "log": meal_uuid,
                    "food_slug": "vegetable-biryani",
                    "quantity": "250.00",
                    "calories": "355.53",
                    "protein_g": "10.50",
                    "carbs_g": "47.00",
                    "fat_g": "14.00",
                },
            ]
        )

    response = api_client.post(reverse("sync"), {"events": events}, format="json")

    assert response.data == {"success_count": 6, "errors": []}
    items = list(MealItem.objects.filter(user=user).order_by("source"))
    assert [item.source for item in items] == ["barcode", "photo", "search"]
    snapshots = {
        (
            item.food_slug,
            item.quantity,
            item.calories,
            item.protein_g,
            item.carbs_g,
            item.fat_g,
        )
        for item in items
    }
    assert len(snapshots) == 1
