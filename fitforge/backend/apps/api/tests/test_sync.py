import datetime
import json
import uuid
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from apps.diary.models import PoseLandmarkSequence, WorkoutSession
from apps.reference.models import Exercise

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
def test_sync_pose_landmark_sequence_stores_json_text_and_skips_daily_state(api_client, user):
    exercise = Exercise.objects.create(
        slug="bodyweight-squat",
        name={"en": "Bodyweight Squat"},
        instructions={"en": "..."},
        primary_muscles=["quads"],
        movement_pattern=Exercise.MovementPattern.SQUAT,
        met_value=Decimal("5.0"),
    )
    url = reverse("sync")
    sequence_uuid = str(uuid.uuid4())
    frames = json.dumps([{"t_ms": 0, "landmarks": {"LEFT_KNEE": [0.5, 0.5, 0.0, 0.9]}}])
    labels = json.dumps(
        [{"start_ms": 0, "end_ms": 500, "outcome": "completed", "quality": "clean"}]
    )

    payload = {
        "events": [
            {
                "type": "pose_landmark_sequence",
                "client_uuid": sequence_uuid,
                "occurred_at": timezone.now().isoformat(),
                "exercise": exercise.slug,
                "camera_position": "side",
                "calibration_source": "user_calibrated",
                "landmark_frames": frames,
                "rep_labels": labels,
            },
        ]
    }

    response = api_client.post(url, payload, format="json")

    assert response.status_code == 200
    assert response.data["success_count"] == 1
    stored = PoseLandmarkSequence.objects.get(client_uuid=sequence_uuid)
    assert stored.landmark_frames == frames
    assert stored.rep_labels == labels
    assert json.loads(stored.landmark_frames)[0]["landmarks"]["LEFT_KNEE"] == [0.5, 0.5, 0.0, 0.9]


@pytest.mark.django_db
def test_sync_pose_landmark_sequence_rejects_non_json_payload(api_client, user):
    Exercise.objects.create(
        slug="bodyweight-squat",
        name={"en": "Bodyweight Squat"},
        instructions={"en": "..."},
        primary_muscles=["quads"],
        movement_pattern=Exercise.MovementPattern.SQUAT,
        met_value=Decimal("5.0"),
    )
    url = reverse("sync")

    payload = {
        "events": [
            {
                "type": "pose_landmark_sequence",
                "client_uuid": str(uuid.uuid4()),
                "occurred_at": timezone.now().isoformat(),
                "exercise": "bodyweight-squat",
                "camera_position": "side",
                "calibration_source": "user_calibrated",
                "landmark_frames": "not json",
            },
        ]
    }

    response = api_client.post(url, payload, format="json")

    assert response.status_code == 200
    assert response.data["success_count"] == 0
    assert "JSON-encoded string" in response.data["errors"][0]["error"]
