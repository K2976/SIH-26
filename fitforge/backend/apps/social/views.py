import re

from django.db.models import Q
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.social.models import USERNAME_REGEX, SocialProfile
from apps.social.serializers import (
    SocialProfileCreateSerializer,
    SocialProfileSerializer,
    SocialProfileUpdateSerializer,
)


class MySocialProfileView(APIView):
    """Endpoints for managing the authenticated user's social profile."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        summary="Get current user's social profile",
        responses={
            200: SocialProfileSerializer,
            404: dict,
        },
    )
    def get(self, request):
        try:
            profile = request.user.social_profile
        except SocialProfile.DoesNotExist:
            return Response(
                {"detail": "Social profile not found. Please create one."},
                status=status.HTTP_404_NOT_FOUND,
            )
        return Response(SocialProfileSerializer(profile).data)

    @extend_schema(
        summary="Create current user's social profile",
        request=SocialProfileCreateSerializer,
        responses={
            201: SocialProfileSerializer,
            400: dict,
        },
    )
    def post(self, request):
        if hasattr(request.user, "social_profile"):
            return Response(
                {"detail": "User already has a social profile."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        serializer = SocialProfileCreateSerializer(data=request.data, context={"request": request})
        serializer.is_valid(raise_exception=True)
        profile = serializer.save()
        return Response(
            SocialProfileSerializer(profile).data,
            status=status.HTTP_201_CREATED,
        )

    @extend_schema(
        summary="Update current user's social profile",
        request=SocialProfileUpdateSerializer,
        responses={
            200: SocialProfileSerializer,
            404: dict,
        },
    )
    def patch(self, request):
        try:
            profile = request.user.social_profile
        except SocialProfile.DoesNotExist:
            return Response(
                {"detail": "Social profile not found. Please create one."},
                status=status.HTTP_404_NOT_FOUND,
            )
        serializer = SocialProfileUpdateSerializer(
            instance=profile,
            data=request.data,
            partial=True,
            context={"request": request},
        )
        serializer.is_valid(raise_exception=True)
        updated = serializer.save()
        return Response(SocialProfileSerializer(updated).data)

    @extend_schema(
        summary="Replace current user's social profile",
        request=SocialProfileUpdateSerializer,
        responses={
            200: SocialProfileSerializer,
            404: dict,
        },
    )
    def put(self, request):
        return self.patch(request)


class SocialProfileDetailView(APIView):
    """Retrieve public social profile by username."""

    permission_classes = [permissions.AllowAny]

    @extend_schema(
        summary="Retrieve social profile by username",
        parameters=[
            OpenApiParameter(
                name="username",
                type=str,
                location=OpenApiParameter.PATH,
                description="Unique public handle (e.g. iron_lifter99)",
            )
        ],
        responses={
            200: SocialProfileSerializer,
            404: dict,
        },
    )
    def get(self, request, username):
        # Strip leading '@' if client provided it
        clean_handle = username.lstrip("@")
        profile = SocialProfile.objects.filter(username__iexact=clean_handle).first()
        if not profile:
            return Response(
                {"detail": f"Social profile '@{clean_handle}' not found."},
                status=status.HTTP_404_NOT_FOUND,
            )
        return Response(SocialProfileSerializer(profile).data)

    def put(self, request, username):
        return Response(
            {"detail": "You do not have permission to modify this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )

    def patch(self, request, username):
        return Response(
            {"detail": "You do not have permission to modify this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )

    def delete(self, request, username):
        return Response(
            {"detail": "You do not have permission to modify this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )


class SocialSearchView(APIView):
    """Search for users by username or display name."""

    permission_classes = [permissions.AllowAny]

    @extend_schema(
        summary="Search social profiles",
        parameters=[
            OpenApiParameter(
                name="q",
                type=str,
                location=OpenApiParameter.QUERY,
                description="Search term (matches username or display name)",
            )
        ],
        responses={200: SocialProfileSerializer(many=True)},
    )
    def get(self, request):
        query = request.query_params.get("q", "").strip().lstrip("@")
        if not query:
            return Response([])
        matches = SocialProfile.objects.filter(
            Q(username__icontains=query) | Q(display_name__icontains=query)
        )[:20]
        return Response(SocialProfileSerializer(matches, many=True).data)


class CheckUsernameView(APIView):
    """Check whether a social username is valid and available."""

    permission_classes = [permissions.AllowAny]

    @extend_schema(
        summary="Check username availability",
        parameters=[
            OpenApiParameter(
                name="username",
                type=str,
                location=OpenApiParameter.QUERY,
                description="Candidate public handle to check",
            )
        ],
        responses={
            200: dict,
            400: dict,
        },
    )
    def get(self, request):
        candidate = request.query_params.get("username", "").strip().lstrip("@")
        if not candidate:
            return Response(
                {
                    "available": False,
                    "reason": "missing_username",
                    "message": "Username query parameter is required.",
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not re.match(USERNAME_REGEX, candidate):
            return Response(
                {
                    "available": False,
                    "reason": "invalid_format",
                    "message": (
                        "Username must be 3-32 characters long and contain only letters, "
                        "numbers, and underscores."
                    ),
                },
                status=status.HTTP_400_BAD_REQUEST,
            )

        taken = SocialProfile.objects.filter(username__iexact=candidate).exists()
        if taken:
            return Response(
                {
                    "available": False,
                    "reason": "already_taken",
                    "message": f"Username '@{candidate}' is already taken.",
                },
                status=status.HTTP_200_OK,
            )

        return Response(
            {
                "available": True,
                "message": f"Username '@{candidate}' is available.",
            },
            status=status.HTTP_200_OK,
        )
