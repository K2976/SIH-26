from decimal import Decimal

import pytest
from rest_framework.test import APIClient

from apps.reference.models import Exercise

pytestmark = pytest.mark.django_db


def make_exercise(slug, **overrides):
    fields = {
        "name": {"en": slug},
        "instructions": {"en": "Do the thing."},
        "movement_pattern": Exercise.MovementPattern.SQUAT,
        "met_value": Decimal("5.0"),
    }
    fields.update(overrides)
    return Exercise.objects.create(slug=slug, **fields)


@pytest.fixture
def client():
    return APIClient()


class TestManifest:
    def test_reports_version_and_table_counts(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/manifest/")
        assert resp.status_code == 200
        assert resp.data["reference_version"] == 2
        assert resp.data["table_counts"]["exercises"] == 2

    def test_does_not_require_auth(self, client):
        resp = client.get("/api/v1/reference/manifest/")
        assert resp.status_code == 200


class TestExerciseDelta:
    def test_since_omitted_returns_everything(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/exercises/")
        assert resp.status_code == 200
        assert {row["slug"] for row in resp.data} == {"a", "b"}

    def test_since_returns_only_rows_changed_after_that_version(self, client):
        first = make_exercise("a")  # version 1
        make_exercise("b")  # version 2

        resp = client.get(f"/api/v1/reference/exercises/?since={first.updated_at_version}")

        assert resp.status_code == 200
        assert [row["slug"] for row in resp.data] == ["b"]

    def test_since_current_version_returns_nothing(self, client):
        make_exercise("a")
        make_exercise("b")
        resp = client.get("/api/v1/reference/exercises/?since=2")
        assert resp.data == []

    def test_touching_a_row_makes_it_reappear_in_the_delta(self, client):
        first = make_exercise("a")
        make_exercise("b")
        watermark = first.updated_at_version + 1  # = "b"'s version

        first.met_value = Decimal("6.0")
        first.save()

        resp = client.get(f"/api/v1/reference/exercises/?since={watermark}")
        assert [row["slug"] for row in resp.data] == ["a"]

    def test_deactivation_appears_in_the_delta(self, client):
        exercise = make_exercise("a")
        watermark = exercise.updated_at_version

        exercise.is_active = False
        exercise.save()

        resp = client.get(f"/api/v1/reference/exercises/?since={watermark}")
        assert len(resp.data) == 1
        assert resp.data[0]["slug"] == "a"
        assert resp.data[0]["is_active"] is False

    def test_deactivated_rows_are_not_deleted(self, client):
        exercise = make_exercise("a")
        exercise.is_active = False
        exercise.save()
        assert Exercise.objects.filter(slug="a").exists()

    def test_invalid_since_is_a_400(self, client):
        resp = client.get("/api/v1/reference/exercises/?since=not-a-number")
        assert resp.status_code == 400

    def test_response_never_exposes_the_numeric_id(self, client):
        make_exercise("a")
        resp = client.get("/api/v1/reference/exercises/")
        assert "id" not in resp.data[0]

    def test_does_not_require_auth(self, client):
        resp = client.get("/api/v1/reference/exercises/")
        assert resp.status_code == 200
