from django.urls import path

from apps.social.views import (
    ChallengeDetailView,
    ChallengeJoinView,
    ChallengeLeaveView,
    ChallengeListCreateView,
    ChallengeProgressView,
    CheckUsernameView,
    CommunityDetailView,
    CommunityJoinView,
    CommunityLeaveView,
    CommunityListCreateView,
    CommunityMembersView,
    MySocialProfileView,
    SocialProfileDetailView,
    SocialSearchView,
)

app_name = "social"

urlpatterns = [
    # Phase 1: Social Profile
    path("profile/", MySocialProfileView.as_view(), name="my-profile"),
    path("profile/<str:username>/", SocialProfileDetailView.as_view(), name="profile-detail"),
    path("search/", SocialSearchView.as_view(), name="search"),
    path("check-username/", CheckUsernameView.as_view(), name="check-username"),
    # Phase 2: Communities
    path("communities/", CommunityListCreateView.as_view(), name="community-list-create"),
    path("communities/<int:pk>/", CommunityDetailView.as_view(), name="community-detail"),
    path("communities/<int:pk>/members/", CommunityMembersView.as_view(), name="community-members"),
    path("communities/<int:pk>/join/", CommunityJoinView.as_view(), name="community-join"),
    path("communities/<int:pk>/leave/", CommunityLeaveView.as_view(), name="community-leave"),
    # Phase 3: Challenges
    path(
        "communities/<int:community_pk>/challenges/",
        ChallengeListCreateView.as_view(),
        name="challenge-list-create",
    ),
    path(
        "communities/<int:community_pk>/challenges/<int:pk>/",
        ChallengeDetailView.as_view(),
        name="challenge-detail",
    ),
    path(
        "communities/<int:community_pk>/challenges/<int:pk>/join/",
        ChallengeJoinView.as_view(),
        name="challenge-join",
    ),
    path(
        "communities/<int:community_pk>/challenges/<int:pk>/leave/",
        ChallengeLeaveView.as_view(),
        name="challenge-leave",
    ),
    path(
        "communities/<int:community_pk>/challenges/<int:pk>/progress/",
        ChallengeProgressView.as_view(),
        name="challenge-progress",
    ),
]
