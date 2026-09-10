from django.urls import include, path
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

from apps.accounts.views import GoalView, LoginView, ProfileView, RefreshView, SignupView
from apps.api.cycle import CycleDataView, CycleProfileView
from apps.api.food import FoodLabelView
from apps.api.state import StateView
from apps.api.sync import SyncView
from apps.api.views import AppConfigView
from apps.reference.views import (
    ExerciseDeltaView,
    FoodItemDeltaView,
    ManifestView,
    RecipeDeltaView,
    TrainingMusicMappingDeltaView,
)

urlpatterns = [
    path("auth/signup/", SignupView.as_view(), name="signup"),
    path("auth/login/", LoginView.as_view(), name="login"),
    path("auth/token/refresh/", RefreshView.as_view(), name="token-refresh"),
    path("profile/", ProfileView.as_view(), name="profile"),
    path("goal/", GoalView.as_view(), name="goal"),
    path("sync/", SyncView.as_view(), name="sync"),
    path("state/", StateView.as_view(), name="state"),
    path("cycle/profile/", CycleProfileView.as_view(), name="cycle-profile"),
    path("cycle/data/", CycleDataView.as_view(), name="cycle-data"),
    path("food/label/", FoodLabelView.as_view(), name="food-label"),
    path("app-config/", AppConfigView.as_view(), name="app-config"),
    path("reference/manifest/", ManifestView.as_view(), name="reference-manifest"),
    path("reference/exercises/", ExerciseDeltaView.as_view(), name="reference-exercises"),
    path("reference/foods/", FoodItemDeltaView.as_view(), name="reference-foods"),
    path("reference/recipes/", RecipeDeltaView.as_view(), name="reference-recipes"),
    path(
        "reference/music/",
        TrainingMusicMappingDeltaView.as_view(),
        name="reference-music",
    ),
    path("social/", include("apps.social.urls")),
    path("schema/", SpectacularAPIView.as_view(), name="schema"),
    path("docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="docs"),
]
