from datetime import timedelta
from decimal import Decimal

import pytest
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APIClient

from apps.diary.models import WorkoutSession
from apps.social.models import (
    Challenge,
    ChallengeParticipant,
    ChallengeStatus,
    ChallengeType,
    Community,
    CommunityMembership,
    CommunityRole,
    CommunityVisibility,
    SocialProfile,
)

User = get_user_model()
pytestmark = pytest.mark.django_db


@pytest.fixture
def client():
    return APIClient()


@pytest.fixture
def user_owner():
    user = User.objects.create_user(
        username="owner@example.com",
        email="owner@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="owner_user",
        display_name="Owner User",
    )
    return user


@pytest.fixture
def user_admin():
    user = User.objects.create_user(
        username="admin@example.com",
        email="admin@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="admin_user",
        display_name="Admin User",
    )
    return user


@pytest.fixture
def user_member():
    user = User.objects.create_user(
        username="member@example.com",
        email="member@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="member_user",
        display_name="Member User",
    )
    return user


@pytest.fixture
def user_outsider():
    user = User.objects.create_user(
        username="outsider@example.com",
        email="outsider@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="outsider_user",
        display_name="Outsider User",
    )
    return user


@pytest.fixture
def community(user_owner, user_admin, user_member):
    comm = Community.objects.create(
        name="Iron Warriors",
        description="Elite lifters community",
        owner=user_owner,
        visibility=CommunityVisibility.PUBLIC,
    )
    CommunityMembership.objects.create(
        community=comm,
        user=user_owner,
        role=CommunityRole.OWNER,
    )
    CommunityMembership.objects.create(
        community=comm,
        user=user_admin,
        role=CommunityRole.ADMIN,
    )
    CommunityMembership.objects.create(
        community=comm,
        user=user_member,
        role=CommunityRole.MEMBER,
    )
    return comm


@pytest.fixture
def auth_owner(client, user_owner):
    client.force_authenticate(user=user_owner)
    return client


@pytest.fixture
def auth_admin(client, user_admin):
    client.force_authenticate(user=user_admin)
    return client


@pytest.fixture
def auth_member(client, user_member):
    client.force_authenticate(user=user_member)
    return client


@pytest.fixture
def auth_outsider(client, user_outsider):
    client.force_authenticate(user=user_outsider)
    return client


class TestChallengeCreation:
    def test_owner_can_create_challenge(self, auth_owner, community):
        now = timezone.now()
        data = {
            "title": "30-Day Workout Blitz",
            "description": "Log 20 workouts in 30 days",
            "challenge_type": ChallengeType.WORKOUT_COUNT,
            "target_value": "20.00",
            "start_date": now.isoformat(),
            "end_date": (now + timedelta(days=30)).isoformat(),
        }
        res = auth_owner.post(f"/api/v1/social/communities/{community.pk}/challenges/", data)
        assert res.status_code == status.HTTP_201_CREATED
        assert res.data["title"] == "30-Day Workout Blitz"
        assert res.data["status"] == ChallengeStatus.ACTIVE
        assert Challenge.objects.filter(title="30-Day Workout Blitz").exists()

    def test_admin_can_create_challenge(self, auth_admin, community):
        now = timezone.now()
        data = {
            "title": "Minute Crusher",
            "description": "500 minutes of exercise",
            "challenge_type": ChallengeType.WORKOUT_MINUTES,
            "target_value": "500.00",
            "start_date": now.isoformat(),
            "end_date": (now + timedelta(days=14)).isoformat(),
        }
        res = auth_admin.post(f"/api/v1/social/communities/{community.pk}/challenges/", data)
        assert res.status_code == status.HTTP_201_CREATED
        assert res.data["title"] == "Minute Crusher"

    def test_regular_member_cannot_create_challenge(self, auth_member, community):
        now = timezone.now()
        data = {
            "title": "Unauthorized Challenge",
            "challenge_type": ChallengeType.WORKOUT_COUNT,
            "target_value": "10.00",
            "start_date": now.isoformat(),
            "end_date": (now + timedelta(days=7)).isoformat(),
        }
        res = auth_member.post(f"/api/v1/social/communities/{community.pk}/challenges/", data)
        assert res.status_code == status.HTTP_403_FORBIDDEN

    def test_validation_rejects_end_date_before_start_date(self, auth_owner, community):
        now = timezone.now()
        data = {
            "title": "Time Traveler Challenge",
            "challenge_type": ChallengeType.WORKOUT_COUNT,
            "target_value": "10.00",
            "start_date": (now + timedelta(days=10)).isoformat(),
            "end_date": now.isoformat(),
        }
        res = auth_owner.post(f"/api/v1/social/communities/{community.pk}/challenges/", data)
        assert res.status_code == status.HTTP_400_BAD_REQUEST

    def test_validation_rejects_negative_or_zero_target(self, auth_owner, community):
        now = timezone.now()
        data = {
            "title": "Zero Target Challenge",
            "challenge_type": ChallengeType.WORKOUT_COUNT,
            "target_value": "0.00",
            "start_date": now.isoformat(),
            "end_date": (now + timedelta(days=7)).isoformat(),
        }
        res = auth_owner.post(f"/api/v1/social/communities/{community.pk}/challenges/", data)
        assert res.status_code == status.HTTP_400_BAD_REQUEST


class TestChallengeParticipation:
    @pytest.fixture
    def active_challenge(self, user_owner, community):
        now = timezone.now()
        return Challenge.objects.create(
            community=community,
            created_by=user_owner,
            title="Active 10 Workouts",
            challenge_type=ChallengeType.WORKOUT_COUNT,
            target_value=Decimal("10.00"),
            start_date=now - timedelta(days=1),
            end_date=now + timedelta(days=10),
            status=ChallengeStatus.ACTIVE,
        )

    def test_community_member_can_join_challenge(self, auth_member, community, active_challenge):
        res = auth_member.post(
            f"/api/v1/social/communities/{community.pk}/challenges/{active_challenge.pk}/join/"
        )
        assert res.status_code == status.HTTP_201_CREATED
        assert ChallengeParticipant.objects.filter(
            challenge=active_challenge, user__username="member@example.com"
        ).exists()

    def test_prevent_duplicate_challenge_participation(
        self, auth_member, community, active_challenge
    ):
        # First join
        auth_member.post(
            f"/api/v1/social/communities/{community.pk}/challenges/{active_challenge.pk}/join/"
        )
        # Duplicate join attempt
        res = auth_member.post(
            f"/api/v1/social/communities/{community.pk}/challenges/{active_challenge.pk}/join/"
        )
        assert res.status_code == status.HTTP_400_BAD_REQUEST
        assert "already participating" in res.data["detail"].lower()

    def test_outsider_cannot_join_challenge(self, auth_outsider, community, active_challenge):
        res = auth_outsider.post(
            f"/api/v1/social/communities/{community.pk}/challenges/{active_challenge.pk}/join/"
        )
        assert res.status_code == status.HTTP_403_FORBIDDEN

    def test_member_can_leave_challenge(
        self, auth_member, community, active_challenge, user_member
    ):
        ChallengeParticipant.objects.create(challenge=active_challenge, user=user_member)
        res = auth_member.post(
            f"/api/v1/social/communities/{community.pk}/challenges/{active_challenge.pk}/leave/"
        )
        assert res.status_code == status.HTTP_200_OK
        assert not ChallengeParticipant.objects.filter(
            challenge=active_challenge, user=user_member
        ).exists()


class TestServerDerivedProgress:
    def test_progress_derived_from_stored_workout_sessions(
        self, auth_member, community, user_member
    ):
        now = timezone.now()
        start = now - timedelta(days=5)
        end = now + timedelta(days=5)

        challenge = Challenge.objects.create(
            community=community,
            created_by=community.owner,
            title="Complete 3 Workouts",
            challenge_type=ChallengeType.WORKOUT_COUNT,
            target_value=Decimal("3.00"),
            start_date=start,
            end_date=end,
            status=ChallengeStatus.ACTIVE,
        )
        ChallengeParticipant.objects.create(challenge=challenge, user=user_member)

        # Initial progress before workouts
        res = auth_member.get(
            f"/api/v1/social/communities/{community.pk}/challenges/{challenge.pk}/progress/"
        )
        assert res.status_code == status.HTTP_200_OK
        assert res.data["current"] == 0.0
        assert res.data["target"] == 3.0
        assert res.data["percentage"] == 0.0
        assert res.data["completed"] is False

        # Add 2 valid stored workouts within the challenge dates
        import uuid

        WorkoutSession.objects.create(
            user=user_member,
            client_uuid=uuid.uuid4(),
            occurred_at=now - timedelta(days=2),
            utc_offset="+00:00",
            source="manual",
            duration_minutes=45,
        )
        WorkoutSession.objects.create(
            user=user_member,
            client_uuid=uuid.uuid4(),
            occurred_at=now - timedelta(days=1),
            utc_offset="+00:00",
            source="manual",
            duration_minutes=60,
        )

        # Progress should now derive 2.0 / 3.0 (66.7%)
        res = auth_member.get(
            f"/api/v1/social/communities/{community.pk}/challenges/{challenge.pk}/progress/"
        )
        assert res.status_code == status.HTTP_200_OK
        assert res.data["current"] == 2.0
        assert res.data["percentage"] == 66.7
        assert res.data["completed"] is False

        # Add 3rd workout to achieve target
        WorkoutSession.objects.create(
            user=user_member,
            client_uuid=uuid.uuid4(),
            occurred_at=now,
            utc_offset="+00:00",
            source="manual",
            duration_minutes=30,
        )

        res = auth_member.get(
            f"/api/v1/social/communities/{community.pk}/challenges/{challenge.pk}/progress/"
        )
        assert res.status_code == status.HTTP_200_OK
        assert res.data["current"] == 3.0
        assert res.data["percentage"] == 100.0
        assert res.data["completed"] is True

        # Verify completed_at was set
        participant = ChallengeParticipant.objects.get(challenge=challenge, user=user_member)
        assert participant.completed_at is not None
