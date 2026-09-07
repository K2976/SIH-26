import pytest
from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from django.db import IntegrityError
from rest_framework.test import APIClient

from apps.accounts.models import UserProfile
from apps.social.models import SocialProfile

User = get_user_model()

pytestmark = pytest.mark.django_db


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def user_a():
    return User.objects.create_user(
        username="user_a@example.com",
        email="user_a@example.com",
        password="password123",
    )


@pytest.fixture
def user_b():
    return User.objects.create_user(
        username="user_b@example.com",
        email="user_b@example.com",
        password="password123",
    )


@pytest.fixture
def auth_client(client, user_a):
    resp = client.post(
        "/api/v1/auth/login/",
        {"email": user_a.email, "password": "password123"},
        format="json",
    )
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return client


@pytest.fixture
def auth_client_b(client, user_b):
    resp = client.post(
        "/api/v1/auth/login/",
        {"email": user_b.email, "password": "password123"},
        format="json",
    )
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return client


class TestSocialProfileModel:
    def test_social_profile_creation(self, user_a):
        profile = SocialProfile.objects.create(
            user=user_a,
            username="iron_lifter",
            display_name="Iron Lifter",
            bio="Focusing on hypertrophy and strength.",
        )
        assert profile.id is not None
        assert profile.user == user_a
        assert profile.username == "iron_lifter"
        assert profile.display_name == "Iron Lifter"
        assert profile.bio == "Focusing on hypertrophy and strength."
        assert profile.created_at is not None
        assert profile.updated_at is not None

    def test_one_profile_per_user(self, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="lifter1",
            display_name="Lifter One",
        )
        with pytest.raises(IntegrityError):
            SocialProfile.objects.create(
                user=user_a,
                username="lifter2",
                display_name="Lifter Two",
            )

    def test_username_database_uniqueness(self, user_a, user_b):
        SocialProfile.objects.create(
            user=user_a,
            username="same_handle",
            display_name="First User",
        )
        with pytest.raises(IntegrityError):
            SocialProfile.objects.create(
                user=user_b,
                username="same_handle",
                display_name="Second User",
            )

    def test_username_model_validation_valid(self, user_a):
        profile = SocialProfile(
            user=user_a,
            username="valid_user_123",
            display_name="Valid",
        )
        profile.full_clean()  # Should not raise

    def test_username_model_validation_too_short(self, user_a):
        profile = SocialProfile(
            user=user_a,
            username="ab",
            display_name="Short",
        )
        with pytest.raises(ValidationError):
            profile.full_clean()

    def test_username_model_validation_too_long(self, user_a):
        profile = SocialProfile(
            user=user_a,
            username="a" * 33,
            display_name="Long",
        )
        with pytest.raises(ValidationError):
            profile.full_clean()

    def test_username_model_validation_invalid_characters(self, user_a):
        invalid_usernames = ["user@name", "user name", "user-name", "user!", "user#1"]
        for invalid in invalid_usernames:
            profile = SocialProfile(
                user=user_a,
                username=invalid,
                display_name="Invalid",
            )
            with pytest.raises(ValidationError):
                profile.full_clean()

    def test_str_representation(self, user_a):
        profile = SocialProfile.objects.create(
            user=user_a,
            username="iron_sam",
            display_name="Sam Smith",
        )
        assert str(profile) == "@iron_sam (Sam Smith)"

    def test_auth_user_remains_independent_and_untouched(self, user_a):
        original_username = user_a.username
        original_email = user_a.email

        SocialProfile.objects.create(
            user=user_a,
            username="public_sammy",
            display_name="Sammy",
        )

        user_a.refresh_from_db()
        assert user_a.username == original_username
        assert user_a.email == original_email
        assert user_a.social_profile.username == "public_sammy"


class TestSocialProfileAPI:
    def test_get_own_profile_not_created_yet_returns_404(self, auth_client):
        resp = auth_client.get("/api/v1/social/profile/")
        assert resp.status_code == 404
        assert "not found" in resp.data["detail"].lower()

    def test_create_own_profile_success(self, auth_client, user_a):
        payload = {
            "username": "iron_athlete",
            "display_name": "Iron Athlete",
            "bio": "Lifting heavy weights every day.",
        }
        resp = auth_client.post("/api/v1/social/profile/", payload, format="json")
        assert resp.status_code == 201
        assert resp.data["username"] == "iron_athlete"
        assert resp.data["display_name"] == "Iron Athlete"
        assert resp.data["bio"] == "Lifting heavy weights every day."
        assert "id" in resp.data

        # Database verification
        profile = SocialProfile.objects.get(user=user_a)
        assert profile.username == "iron_athlete"

    def test_create_duplicate_own_profile_returns_400(self, auth_client, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="existing_handle",
            display_name="Existing",
        )
        resp = auth_client.post(
            "/api/v1/social/profile/",
            {"username": "new_handle", "display_name": "New"},
            format="json",
        )
        assert resp.status_code == 400
        assert "already has a social profile" in resp.data["detail"].lower()

    def test_create_profile_duplicate_username_returns_400(self, auth_client, user_b):
        SocialProfile.objects.create(
            user=user_b,
            username="taken_handle",
            display_name="User B",
        )
        resp = auth_client.post(
            "/api/v1/social/profile/",
            {"username": "taken_handle", "display_name": "User A"},
            format="json",
        )
        assert resp.status_code == 400
        assert "already exists" in str(resp.data)

    def test_create_profile_case_insensitive_duplicate_username_returns_400(
        self, auth_client, user_b
    ):
        SocialProfile.objects.create(
            user=user_b,
            username="taken_handle",
            display_name="User B",
        )
        resp = auth_client.post(
            "/api/v1/social/profile/",
            {"username": "TAKEN_HANDLE", "display_name": "User A"},
            format="json",
        )
        assert resp.status_code == 400
        assert "already exists" in str(resp.data)

    def test_create_profile_invalid_username_format_returns_400(self, auth_client):
        invalid_cases = [
            "ab",  # too short
            "user@domain.com",  # email format forbidden
            "user with space",
            "a" * 33,  # too long
            "user-with-dashes",
            "user!special",
        ]
        for invalid_handle in invalid_cases:
            resp = auth_client.post(
                "/api/v1/social/profile/",
                {"username": invalid_handle, "display_name": "Test"},
                format="json",
            )
            assert resp.status_code == 400, f"Expected 400 for {invalid_handle}"

    def test_get_own_profile_success(self, auth_client, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="my_unique_handle",
            display_name="My Name",
            bio="My bio text",
        )
        resp = auth_client.get("/api/v1/social/profile/")
        assert resp.status_code == 200
        assert resp.data["username"] == "my_unique_handle"
        assert resp.data["display_name"] == "My Name"
        assert resp.data["bio"] == "My bio text"

    def test_update_own_profile_success(self, auth_client, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="initial_handle",
            display_name="Initial Name",
            bio="Old bio",
        )
        resp = auth_client.patch(
            "/api/v1/social/profile/",
            {"display_name": "Updated Name", "bio": "New bio"},
            format="json",
        )
        assert resp.status_code == 200
        assert resp.data["display_name"] == "Updated Name"
        assert resp.data["bio"] == "New bio"
        assert resp.data["username"] == "initial_handle"

    def test_update_own_username_success(self, auth_client, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="handle_v1",
            display_name="Name",
        )
        resp = auth_client.patch(
            "/api/v1/social/profile/",
            {"username": "handle_v2"},
            format="json",
        )
        assert resp.status_code == 200
        assert resp.data["username"] == "handle_v2"

    def test_update_own_username_to_duplicate_returns_400(self, auth_client, user_a, user_b):
        SocialProfile.objects.create(
            user=user_a,
            username="handle_a",
            display_name="User A",
        )
        SocialProfile.objects.create(
            user=user_b,
            username="handle_b",
            display_name="User B",
        )
        resp = auth_client.patch(
            "/api/v1/social/profile/",
            {"username": "handle_b"},
            format="json",
        )
        assert resp.status_code == 400
        assert "already exists" in str(resp.data)

    def test_unauthenticated_requests_to_own_profile_returns_401(self, client):
        assert client.get("/api/v1/social/profile/").status_code == 401
        assert (
            client.post(
                "/api/v1/social/profile/",
                {"username": "test", "display_name": "Test"},
            ).status_code
            == 401
        )
        assert client.patch("/api/v1/social/profile/", {}).status_code == 401

    def test_get_profile_by_username_success(self, client, user_b):
        SocialProfile.objects.create(
            user=user_b,
            username="public_champion",
            display_name="Public Champion",
            bio="Powerlifting and conditioning",
        )
        resp = client.get("/api/v1/social/profile/public_champion/")
        assert resp.status_code == 200
        assert resp.data["username"] == "public_champion"
        assert resp.data["display_name"] == "Public Champion"
        assert resp.data["bio"] == "Powerlifting and conditioning"

    def test_get_profile_by_username_with_at_prefix(self, client, user_b):
        SocialProfile.objects.create(
            user=user_b,
            username="at_champion",
            display_name="At Champion",
        )
        resp = client.get("/api/v1/social/profile/@at_champion/")
        assert resp.status_code == 200
        assert resp.data["username"] == "at_champion"

    def test_get_nonexistent_profile_by_username_returns_404(self, client):
        resp = client.get("/api/v1/social/profile/ghost_user/")
        assert resp.status_code == 404
        assert "not found" in resp.data["detail"].lower()

    def test_unauthorized_modification_of_other_user_profile_returns_403(self, auth_client, user_b):
        SocialProfile.objects.create(
            user=user_b,
            username="user_b_handle",
            display_name="User B",
        )
        # Attempting to PUT or PATCH another user's profile endpoint
        put_resp = auth_client.put(
            "/api/v1/social/profile/user_b_handle/",
            {"display_name": "Hacked Name"},
            format="json",
        )
        assert put_resp.status_code == 403

        patch_resp = auth_client.patch(
            "/api/v1/social/profile/user_b_handle/",
            {"display_name": "Hacked Name"},
            format="json",
        )
        assert patch_resp.status_code == 403

        del_resp = auth_client.delete("/api/v1/social/profile/user_b_handle/")
        assert del_resp.status_code == 403

    def test_search_profiles_by_username_and_display_name(self, client, user_a, user_b):
        SocialProfile.objects.create(
            user=user_a,
            username="alex_lift",
            display_name="Alexander Great",
        )
        SocialProfile.objects.create(
            user=user_b,
            username="barbell_bob",
            display_name="Bob Alexander",
        )

        # Search by username substring
        resp1 = client.get("/api/v1/social/search/?q=alex_")
        assert resp1.status_code == 200
        assert len(resp1.data) == 1
        assert resp1.data[0]["username"] == "alex_lift"

        # Search by display name substring
        resp2 = client.get("/api/v1/social/search/?q=Alexander")
        assert resp2.status_code == 200
        assert len(resp2.data) == 2

        # Search empty
        resp3 = client.get("/api/v1/social/search/?q=")
        assert resp3.status_code == 200
        assert resp3.data == []

    def test_check_username_available(self, client):
        resp = client.get("/api/v1/social/check-username/?username=available_handle")
        assert resp.status_code == 200
        assert resp.data["available"] is True

    def test_check_username_already_taken(self, client, user_a):
        SocialProfile.objects.create(
            user=user_a,
            username="taken_handle",
            display_name="Taken",
        )
        resp = client.get("/api/v1/social/check-username/?username=taken_handle")
        assert resp.status_code == 200
        assert resp.data["available"] is False
        assert resp.data["reason"] == "already_taken"

    def test_check_username_invalid_format(self, client):
        resp = client.get("/api/v1/social/check-username/?username=bad@handle")
        assert resp.status_code == 400
        assert resp.data["available"] is False
        assert resp.data["reason"] == "invalid_format"

    def test_check_username_missing_parameter(self, client):
        resp = client.get("/api/v1/social/check-username/")
        assert resp.status_code == 400
        assert resp.data["reason"] == "missing_username"


class TestBackwardCompatibility:
    def test_existing_auth_signup_and_login_still_works(self, client):
        signup_resp = client.post(
            "/api/v1/auth/signup/",
            {"email": "compat_user@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        assert signup_resp.status_code == 201
        assert "access" in signup_resp.data
        assert "refresh" in signup_resp.data

        # Verify auth_user was created with username == email
        user = User.objects.get(email="compat_user@example.com")
        assert user.username == "compat_user@example.com"
        assert UserProfile.objects.filter(user=user).exists()
        # Ensure social profile was NOT automatically forced or corrupted
        has_social = (
            hasattr(user, "social_profile") and SocialProfile.objects.filter(user=user).exists()
        )
        assert not has_social

        # Login using email
        login_resp = client.post(
            "/api/v1/auth/login/",
            {"email": "compat_user@example.com", "password": "correct-horse-battery-staple"},
            format="json",
        )
        assert login_resp.status_code == 200
        assert "access" in login_resp.data

    def test_existing_accounts_userprofile_remains_intact(self, client, user_a):
        # Existing accounts userprofile
        profile = UserProfile.objects.create(
            user=user_a,
            timezone="America/New_York",
            unit_preference=UserProfile.UnitPreference.IMPERIAL,
        )
        # Add a social profile
        social_profile = SocialProfile.objects.create(
            user=user_a,
            username="social_compat",
            display_name="Social Compat",
        )

        profile.refresh_from_db()
        social_profile.refresh_from_db()
        user_a.refresh_from_db()

        assert profile.timezone == "America/New_York"
        assert profile.unit_preference == UserProfile.UnitPreference.IMPERIAL
        assert user_a.profile == profile
        assert user_a.social_profile == social_profile
        assert user_a.username == "user_a@example.com"
