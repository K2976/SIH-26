import pytest
from django.contrib.auth import get_user_model
from django.db import IntegrityError
from rest_framework.test import APIClient

from apps.social.models import (
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
def user_a():
    user = User.objects.create_user(
        username="user_a@example.com",
        email="user_a@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="athlete_a",
        display_name="Athlete Alpha",
        bio="Lifting heavy weights",
    )
    return user


@pytest.fixture
def user_b():
    user = User.objects.create_user(
        username="user_b@example.com",
        email="user_b@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="athlete_b",
        display_name="Athlete Bravo",
        bio="Running marathon",
    )
    return user


@pytest.fixture
def user_c():
    user = User.objects.create_user(
        username="user_c@example.com",
        email="user_c@example.com",
        password="password123",
    )
    SocialProfile.objects.create(
        user=user,
        username="athlete_c",
        display_name="Athlete Charlie",
    )
    return user


@pytest.fixture
def auth_client(user_a):
    c = APIClient()
    resp = c.post(
        "/api/v1/auth/login/",
        {"email": user_a.email, "password": "password123"},
        format="json",
    )
    c.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return c


@pytest.fixture
def auth_client_b(user_b):
    c = APIClient()
    resp = c.post(
        "/api/v1/auth/login/",
        {"email": user_b.email, "password": "password123"},
        format="json",
    )
    c.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return c


@pytest.fixture
def auth_client_c(user_c):
    c = APIClient()
    resp = c.post(
        "/api/v1/auth/login/",
        {"email": user_c.email, "password": "password123"},
        format="json",
    )
    c.credentials(HTTP_AUTHORIZATION=f"Bearer {resp.data['access']}")
    return c



class TestCommunityModels:
    def test_community_creation_and_str(self, user_a):
        community = Community.objects.create(
            name="Iron Gym Club",
            description="Morning lifting accountability group",
            owner=user_a,
            visibility=CommunityVisibility.PUBLIC,
        )
        assert community.name == "Iron Gym Club"
        assert str(community) == "Iron Gym Club"
        assert community.visibility == CommunityVisibility.PUBLIC

    def test_membership_unique_constraint(self, user_a):
        community = Community.objects.create(
            name="Hostel Fitness",
            owner=user_a,
            visibility=CommunityVisibility.PUBLIC,
        )
        CommunityMembership.objects.create(
            community=community,
            user=user_a,
            role=CommunityRole.OWNER,
        )
        with pytest.raises(IntegrityError):
            CommunityMembership.objects.create(
                community=community,
                user=user_a,
                role=CommunityRole.MEMBER,
            )


class TestCommunityListCreateAPI:
    def test_create_community_success(self, auth_client, user_a):
        payload = {
            "name": "Spartan 6AM Club",
            "description": "Early morning workout crew",
            "visibility": "public",
        }
        resp = auth_client.post("/api/v1/social/communities/", payload, format="json")
        assert resp.status_code == 201
        assert resp.data["name"] == "Spartan 6AM Club"
        assert resp.data["visibility"] == "public"
        assert resp.data["members_count"] == 1
        assert resp.data["current_user_role"] == "owner"
        assert resp.data["is_member"] is True

        # Check in DB
        community = Community.objects.get(name="Spartan 6AM Club")
        assert community.owner == user_a
        assert community.memberships.filter(user=user_a, role=CommunityRole.OWNER).exists()

    def test_create_community_unauthenticated_fails(self, client):
        payload = {"name": "Unauthorized Club", "visibility": "public"}
        resp = client.post("/api/v1/social/communities/", payload, format="json")
        assert resp.status_code == 401

    def test_create_community_validation_too_short(self, auth_client):
        payload = {"name": "A", "visibility": "public"}
        resp = auth_client.post("/api/v1/social/communities/", payload, format="json")
        assert resp.status_code == 400
        assert "name" in resp.data

    def test_list_discover_public_communities(self, auth_client, user_a, user_b):
        c1 = Community.objects.create(
            name="Public Runners",
            owner=user_a,
            visibility=CommunityVisibility.PUBLIC,
        )
        CommunityMembership.objects.create(community=c1, user=user_a, role=CommunityRole.OWNER)

        c2 = Community.objects.create(
            name="Secret Society",
            owner=user_b,
            visibility=CommunityVisibility.PRIVATE,
        )
        CommunityMembership.objects.create(community=c2, user=user_b, role=CommunityRole.OWNER)

        resp = auth_client.get("/api/v1/social/communities/?filter=discover")
        assert resp.status_code == 200
        names = [item["name"] for item in resp.data]
        assert "Public Runners" in names
        assert "Secret Society" not in names

    def test_list_my_communities(self, auth_client, auth_client_b, user_a, user_b):
        c1 = Community.objects.create(name="Alpha Group", owner=user_a)
        CommunityMembership.objects.create(community=c1, user=user_a, role=CommunityRole.OWNER)

        c2 = Community.objects.create(name="Bravo Group", owner=user_b)
        CommunityMembership.objects.create(community=c2, user=user_b, role=CommunityRole.OWNER)

        resp_a = auth_client.get("/api/v1/social/communities/?filter=my")
        assert resp_a.status_code == 200
        names_a = [item["name"] for item in resp_a.data]
        assert "Alpha Group" in names_a
        assert "Bravo Group" not in names_a

    def test_search_communities(self, auth_client, user_a):
        c1 = Community.objects.create(name="CrossFit Warriors", description="High intensity", owner=user_a)
        CommunityMembership.objects.create(community=c1, user=user_a, role=CommunityRole.OWNER)

        c2 = Community.objects.create(name="Calisthenics Park", description="Bodyweight only", owner=user_a)
        CommunityMembership.objects.create(community=c2, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client.get("/api/v1/social/communities/?search=crossfit")
        assert resp.status_code == 200
        assert len(resp.data) == 1
        assert resp.data[0]["name"] == "CrossFit Warriors"


class TestCommunityDetailAPI:
    def test_get_public_community_detail(self, auth_client, user_a):
        c = Community.objects.create(name="Open Gym", description="Everyone welcome", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client.get(f"/api/v1/social/communities/{c.id}/")
        assert resp.status_code == 200
        assert resp.data["name"] == "Open Gym"
        assert resp.data["owner"]["username"] == "athlete_a"
        assert resp.data["members_count"] == 1
        assert resp.data["current_user_role"] == "owner"

    def test_get_private_community_detail_non_member_forbidden(self, auth_client_b, user_a):
        c = Community.objects.create(
            name="Private Vault",
            owner=user_a,
            visibility=CommunityVisibility.PRIVATE,
        )
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client_b.get(f"/api/v1/social/communities/{c.id}/")
        assert resp.status_code == 403

    def test_update_community_owner_allowed(self, auth_client, user_a):
        c = Community.objects.create(name="Original Name", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        payload = {"name": "Updated Community Name", "description": "New description"}
        resp = auth_client.patch(f"/api/v1/social/communities/{c.id}/", payload, format="json")
        assert resp.status_code == 200
        assert resp.data["name"] == "Updated Community Name"
        assert resp.data["description"] == "New description"

    def test_update_community_admin_allowed(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Managed Group", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.ADMIN)

        payload = {"description": "Updated by admin"}
        resp = auth_client_b.patch(f"/api/v1/social/communities/{c.id}/", payload, format="json")
        assert resp.status_code == 200
        assert resp.data["description"] == "Updated by admin"

    def test_update_community_member_forbidden(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Member Group", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.MEMBER)

        payload = {"name": "Hacked Name"}
        resp = auth_client_b.patch(f"/api/v1/social/communities/{c.id}/", payload, format="json")
        assert resp.status_code == 403

    def test_delete_community_owner_allowed(self, auth_client, user_a):
        c = Community.objects.create(name="To Delete", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client.delete(f"/api/v1/social/communities/{c.id}/")
        assert resp.status_code == 204
        assert not Community.objects.filter(id=c.id).exists()

    def test_delete_community_non_owner_forbidden(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Protected Group", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.ADMIN)

        resp = auth_client_b.delete(f"/api/v1/social/communities/{c.id}/")
        assert resp.status_code == 403


class TestCommunityJoinLeaveAPI:
    def test_join_public_community_success(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Public Workout", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client_b.post(f"/api/v1/social/communities/{c.id}/join/")
        assert resp.status_code == 201
        assert resp.data["role"] == "member"
        assert resp.data["user"]["username"] == "athlete_b"
        assert CommunityMembership.objects.filter(community=c, user=user_b).exists()

    def test_join_already_member_fails(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Public Workout", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.MEMBER)

        resp = auth_client_b.post(f"/api/v1/social/communities/{c.id}/join/")
        assert resp.status_code == 400
        assert "already a member" in resp.data["detail"].lower()

    def test_join_private_community_forbidden(self, auth_client_b, user_a):
        c = Community.objects.create(
            name="Secret Club",
            owner=user_a,
            visibility=CommunityVisibility.PRIVATE,
        )
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client_b.post(f"/api/v1/social/communities/{c.id}/join/")
        assert resp.status_code == 403

    def test_leave_community_member_success(self, auth_client_b, user_a, user_b):
        c = Community.objects.create(name="Active Club", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.MEMBER)

        resp = auth_client_b.post(f"/api/v1/social/communities/{c.id}/leave/")
        assert resp.status_code == 200
        assert not CommunityMembership.objects.filter(community=c, user=user_b).exists()

    def test_owner_cannot_leave_while_others_remain(self, auth_client, user_a, user_b):
        c = Community.objects.create(name="Active Club", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.MEMBER)

        resp = auth_client.post(f"/api/v1/social/communities/{c.id}/leave/")
        assert resp.status_code == 400
        assert "owner cannot leave" in resp.data["detail"].lower()

    def test_owner_leave_as_sole_member_removes_community(self, auth_client, user_a):
        c = Community.objects.create(name="Solo Club", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client.post(f"/api/v1/social/communities/{c.id}/leave/")
        assert resp.status_code == 200
        assert not Community.objects.filter(id=c.id).exists()


class TestCommunityMembersAPI:
    def test_list_members_contains_public_profile_info(self, auth_client, user_a, user_b):
        c = Community.objects.create(name="Gym Roster", owner=user_a)
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)
        CommunityMembership.objects.create(community=c, user=user_b, role=CommunityRole.MEMBER)

        resp = auth_client.get(f"/api/v1/social/communities/{c.id}/members/")
        assert resp.status_code == 200
        assert len(resp.data) == 2

        usernames = [m["user"]["username"] for m in resp.data]
        assert "athlete_a" in usernames
        assert "athlete_b" in usernames

        # Ensure NO private sensitive data like email or password leaked
        first_member = resp.data[0]
        assert "email" not in first_member["user"]
        assert "password" not in first_member["user"]

    def test_list_members_private_community_non_member_forbidden(self, auth_client_c, user_a, user_b):
        c = Community.objects.create(
            name="Secret Club",
            owner=user_a,
            visibility=CommunityVisibility.PRIVATE,
        )
        CommunityMembership.objects.create(community=c, user=user_a, role=CommunityRole.OWNER)

        resp = auth_client_c.get(f"/api/v1/social/communities/{c.id}/members/")
        assert resp.status_code == 403
