from django.urls import path

from apps.social.views import (
    CheckUsernameView,
    MySocialProfileView,
    SocialProfileDetailView,
    SocialSearchView,
)

app_name = "social"

urlpatterns = [
    path("profile/", MySocialProfileView.as_view(), name="my-profile"),
    path("profile/<str:username>/", SocialProfileDetailView.as_view(), name="profile-detail"),
    path("search/", SocialSearchView.as_view(), name="search"),
    path("check-username/", CheckUsernameView.as_view(), name="check-username"),
]
