from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

from apps.accounts.constants import MAX_TARGET_RATE_KG_PER_WEEK
from apps.accounts.models import UserProfile

User = get_user_model()

pytestmark = pytest.mark.django_db


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def auth_client(client):
    client.post(
        "/api/v1/auth/signup/",
        {"email": "a@example.com", "password": "correct-horse-battery-staple"},
        format="json",
    )
    resp = client.post(
        "/api/v1/auth/login/",
        {"email": "a@example.com", "password": "correct-horse-battery-staple"},
        format="json",
    )
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return client


class TestAuth:
    def test_signup_creates_user_and_profile(self, client):
        resp = client.post(
            "/api/v1/auth/signup/",
            {"email": "new@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        assert resp.status_code == 201
        assert "access" in resp.data and "refresh" in resp.data
        user = User.objects.get(email="new@example.com")
        assert UserProfile.objects.filter(user=user).exists()

    def test_signup_rejects_duplicate_email(self, client):
        client.post(
            "/api/v1/auth/signup/",
            {"email": "dup@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        resp = client.post(
            "/api/v1/auth/signup/",
            {"email": "dup@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        assert resp.status_code == 400

    def test_login_with_email_and_password_returns_tokens(self, client):
        client.post(
            "/api/v1/auth/signup/",
            {"email": "login@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        resp = client.post(
            "/api/v1/auth/login/",
            {"email": "login@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        assert resp.status_code == 200
        assert "access" in resp.data and "refresh" in resp.data

    def test_login_rejects_wrong_password(self, client):
        client.post(
            "/api/v1/auth/signup/",
            {"email": "wrong@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        resp = client.post(
            "/api/v1/auth/login/",
            {"email": "wrong@example.com", "password": "nope"},
            format="json",
        )
        assert resp.status_code == 401

    def test_token_refresh_returns_new_access_token(self, client):
        client.post(
            "/api/v1/auth/signup/",
            {"email": "refresh@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        login = client.post(
            "/api/v1/auth/login/",
            {"email": "refresh@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        resp = client.post(
            "/api/v1/auth/token/refresh/", {"refresh": login.data["refresh"]}, format="json"
        )
        assert resp.status_code == 200
        assert "access" in resp.data


class TestProfile:
    def test_get_profile_reports_three_states(self, auth_client):
        resp = auth_client.get("/api/v1/profile/")
        assert resp.status_code == 200
        assert resp.data["field_states"]["height_cm"] == UserProfile.NOT_ASKED

    def test_put_sets_a_known_value(self, auth_client):
        resp = auth_client.put("/api/v1/profile/", {"height_cm": "170.0"}, format="json")
        assert resp.status_code == 200
        assert resp.data["height_cm"] == "170.0"
        assert resp.data["field_states"]["height_cm"] == UserProfile.KNOWN

    def test_put_declines_a_field_and_clears_its_value(self, auth_client):
        auth_client.put("/api/v1/profile/", {"height_cm": "170.0"}, format="json")
        resp = auth_client.put(
            "/api/v1/profile/", {"declined_fields": ["height_cm"]}, format="json"
        )
        assert resp.status_code == 200
        assert resp.data["height_cm"] is None
        assert resp.data["field_states"]["height_cm"] == UserProfile.DECLINED

    def test_profile_requires_auth(self, client):
        resp = client.get("/api/v1/profile/")
        assert resp.status_code == 401

    def test_patch_sets_current_weight(self, auth_client):
        resp = auth_client.patch(
            "/api/v1/profile/", {"current_weight_kg": "72.5"}, format="json"
        )
        assert resp.status_code == 200
        assert resp.data["current_weight_kg"] == "72.5"
        assert resp.data["field_states"]["current_weight_kg"] == UserProfile.KNOWN

    def test_patch_sets_activity_level(self, auth_client):
        resp = auth_client.patch(
            "/api/v1/profile/", {"activity_level": "moderately_active"}, format="json"
        )
        assert resp.status_code == 200
        assert resp.data["activity_level"] == "moderately_active"
        assert resp.data["field_states"]["activity_level"] == UserProfile.KNOWN

    def test_patch_declines_activity_level(self, auth_client):
        auth_client.patch(
            "/api/v1/profile/", {"activity_level": "sedentary"}, format="json"
        )
        resp = auth_client.patch(
            "/api/v1/profile/", {"declined_fields": ["activity_level"]}, format="json"
        )
        assert resp.status_code == 200
        assert resp.data["activity_level"] is None
        assert resp.data["field_states"]["activity_level"] == UserProfile.DECLINED

    def test_new_fields_start_as_not_asked(self, auth_client):
        resp = auth_client.get("/api/v1/profile/")
        assert resp.data["field_states"]["current_weight_kg"] == UserProfile.NOT_ASKED
        assert resp.data["field_states"]["activity_level"] == UserProfile.NOT_ASKED


class TestOnboardingFlow:
    """Simulates the full onboarding sequence the Android client will perform."""

    def test_full_onboarding_creates_profile_and_goal(self, auth_client):
        # Step 1 — basics
        resp = auth_client.patch(
            "/api/v1/profile/",
            {
                "date_of_birth": "1995-06-15",
                "sex_at_birth": "female",
                "height_cm": "165.0",
                "current_weight_kg": "60.0",
                "unit_preference": "metric",
                "timezone": "Asia/Kolkata",
            },
            format="json",
        )
        assert resp.status_code == 200

        # Step 2 — cycle tracking
        resp = auth_client.patch(
            "/api/v1/profile/", {"tracks_cycle": True}, format="json"
        )
        assert resp.status_code == 200
        assert resp.data["tracks_cycle"] is True

        # Step 3 — goal
        resp = auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "hypertrophy",
                "target_rate_kg_per_week": "0.5",
                "auto_adjust": True,
            },
            format="json",
        )
        assert resp.status_code == 201

        # Step 5 — fitness inputs
        resp = auth_client.patch(
            "/api/v1/profile/",
            {
                "waist_cm": "70.0",
                "resting_hr": 62,
                "activity_level": "moderately_active",
            },
            format="json",
        )
        assert resp.status_code == 200

        # Final — mark complete
        resp = auth_client.patch(
            "/api/v1/profile/",
            {"onboarding_completed_at": "2026-09-04T12:00:00Z"},
            format="json",
        )
        assert resp.status_code == 200
        assert resp.data["onboarding_completed_at"] is not None

        # Verify everything landed
        profile = auth_client.get("/api/v1/profile/")
        assert profile.data["field_states"]["height_cm"] == UserProfile.KNOWN
        assert profile.data["field_states"]["current_weight_kg"] == UserProfile.KNOWN
        assert profile.data["field_states"]["activity_level"] == UserProfile.KNOWN
        assert profile.data["field_states"]["tracks_cycle"] == UserProfile.KNOWN

        goal = auth_client.get("/api/v1/goal/")
        assert goal.data["energy_direction"] == "cut"
        assert goal.data["training_focus"] == "hypertrophy"

    def test_skipped_fields_are_declined_not_null(self, auth_client):
        # Step 1 — basics (required)
        auth_client.patch(
            "/api/v1/profile/",
            {
                "date_of_birth": "1990-01-01",
                "sex_at_birth": "male",
                "height_cm": "180.0",
                "current_weight_kg": "85.0",
                "unit_preference": "imperial",
                "timezone": "America/New_York",
            },
            format="json",
        )

        # Step 2 — skip cycle tracking
        auth_client.patch(
            "/api/v1/profile/", {"declined_fields": ["tracks_cycle"]}, format="json"
        )

        # Step 5 — skip fitness inputs
        auth_client.patch(
            "/api/v1/profile/",
            {"declined_fields": ["tracks_cycle", "waist_cm", "resting_hr", "activity_level"]},
            format="json",
        )

        profile = auth_client.get("/api/v1/profile/")
        assert profile.data["field_states"]["tracks_cycle"] == UserProfile.DECLINED
        assert profile.data["field_states"]["waist_cm"] == UserProfile.DECLINED
        assert profile.data["field_states"]["resting_hr"] == UserProfile.DECLINED
        assert profile.data["field_states"]["activity_level"] == UserProfile.DECLINED

    def test_rerunning_onboarding_updates_not_duplicates(self, auth_client):
        # First run
        auth_client.patch(
            "/api/v1/profile/",
            {"height_cm": "170.0", "current_weight_kg": "70.0"},
            format="json",
        )
        auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "strength",
                "target_rate_kg_per_week": "0.3",
                "auto_adjust": True,
            },
            format="json",
        )

        # Second run — re-onboarding
        auth_client.patch(
            "/api/v1/profile/",
            {"height_cm": "171.0", "current_weight_kg": "69.0"},
            format="json",
        )
        auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "maintain",
                "training_focus": "general",
                "target_rate_kg_per_week": "0.0",
                "auto_adjust": True,
            },
            format="json",
        )

        # Profile was updated, not duplicated (it's a OneToOneField)
        profile = auth_client.get("/api/v1/profile/")
        assert profile.data["height_cm"] == "171.0"
        assert profile.data["current_weight_kg"] == "69.0"

        # Goal was versioned — the current one is the latest
        goal = auth_client.get("/api/v1/goal/")
        assert goal.data["energy_direction"] == "maintain"


class TestGoal:
    def test_get_goal_is_404_before_any_goal_set(self, auth_client):
        # Not 200+null: DRF's JSONRenderer renders a None payload as an
        # empty body, not the JSON literal `null`, which the generated
        # Retrofit client (typed as non-nullable Goal) can't decode either
        # way. 404 needs no body and Retrofit's null-body-on-error handling
        # already treats a missing goal the same as any other null field.
        resp = auth_client.get("/api/v1/goal/")
        assert resp.status_code == 404

    def test_post_creates_a_goal(self, auth_client):
        resp = auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "strength",
                "target_rate_kg_per_week": "0.3",
                "auto_adjust": True,
            },
            format="json",
        )
        assert resp.status_code == 201
        assert resp.data["energy_direction"] == "cut"
        assert resp.data["training_focus"] == "strength"
        assert resp.data["clamp_reason"] is None

    def test_changing_goal_versions_instead_of_updating(self, auth_client):
        auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "strength",
                "target_rate_kg_per_week": "0.3",
                "auto_adjust": True,
            },
            format="json",
        )
        resp = auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "maintain",
                "training_focus": "hypertrophy",
                "target_rate_kg_per_week": "0.0",
                "auto_adjust": True,
            },
            format="json",
        )
        assert resp.status_code == 201
        current = auth_client.get("/api/v1/goal/")
        assert current.data["energy_direction"] == "maintain"
        assert current.data["training_focus"] == "hypertrophy"

    def test_clamp_reason_is_visible_when_rate_is_out_of_range(self, auth_client):
        too_high = MAX_TARGET_RATE_KG_PER_WEEK + Decimal("10")
        resp = auth_client.post(
            "/api/v1/goal/",
            {
                "energy_direction": "gain",
                "training_focus": "general",
                "target_rate_kg_per_week": str(too_high),
                "auto_adjust": True,
            },
            format="json",
        )
        assert resp.status_code == 201
        assert Decimal(resp.data["target_rate_kg_per_week"]) == MAX_TARGET_RATE_KG_PER_WEEK
        assert resp.data["clamp_reason"] is not None

    def test_goal_requires_auth(self, client):
        resp = client.get("/api/v1/goal/")
        assert resp.status_code == 401


class TestAppConfig:
    def test_returns_min_supported_version(self, client):
        resp = client.get("/api/v1/app-config/")
        assert resp.status_code == 200
        assert "min_supported_version" in resp.data

    def test_does_not_require_auth(self, client):
        resp = client.get("/api/v1/app-config/")
        assert resp.status_code == 200
