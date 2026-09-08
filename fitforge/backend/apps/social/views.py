import re

from django.db.models import Count, Q
from django.shortcuts import get_object_or_404
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.social.models import (
    USERNAME_REGEX,
    Community,
    CommunityMembership,
    CommunityRole,
    CommunityVisibility,
    SocialProfile,
)
from apps.social.permissions import (
    CanViewCommunityDetail,
    IsCommunityAdminOrOwner,
    IsCommunityOwner,
)
from apps.social.serializers import (
    CommunityCreateSerializer,
    CommunityDetailSerializer,
    CommunityListSerializer,
    CommunityMemberSerializer,
    CommunityUpdateSerializer,
    SocialProfileCreateSerializer,
    SocialProfileSerializer,
    SocialProfileUpdateSerializer,
    UsernameCheckResponseSerializer,
)



class MySocialProfileView(APIView):
    """Endpoints for managing the authenticated user's social profile."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        operation_id="social_my_profile_retrieve",
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
        operation_id="social_public_profile_retrieve",
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

    @extend_schema(exclude=True)
    def put(self, request, username):
        return Response(
            {"detail": "You do not have permission to modify this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )

    @extend_schema(exclude=True)
    def patch(self, request, username):
        return Response(
            {"detail": "You do not have permission to modify this profile."},
            status=status.HTTP_403_FORBIDDEN,
        )

    @extend_schema(exclude=True)
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
            200: UsernameCheckResponseSerializer,
            400: UsernameCheckResponseSerializer,
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


class CommunityListCreateView(APIView):
    """List and create fitness communities."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        tags=["community"],
        summary="List communities",
        parameters=[
            OpenApiParameter(
                name="filter",
                type=str,
                location=OpenApiParameter.QUERY,
                description="Filter options: 'my' (joined communities) or 'discover' (public communities)",
                default="discover",
            ),
            OpenApiParameter(
                name="search",
                type=str,
                location=OpenApiParameter.QUERY,
                description="Search keyword for community name or description",
            ),
        ],
        responses={200: CommunityListSerializer(many=True)},
    )
    def get(self, request):
        filter_mode = request.query_params.get("filter", "discover").strip().lower()
        search_query = request.query_params.get("search", "").strip()

        if filter_mode == "my":
            qs = Community.objects.filter(memberships__user=request.user)
        else:
            qs = Community.objects.filter(visibility=CommunityVisibility.PUBLIC)

        if search_query:
            qs = qs.filter(
                Q(name__icontains=search_query) | Q(description__icontains=search_query)
            )

        qs = qs.annotate(members_count=Count("memberships", distinct=True)).order_by("-created_at")
        serializer = CommunityListSerializer(qs, many=True, context={"request": request})
        return Response(serializer.data)

    @extend_schema(
        tags=["community"],
        summary="Create a new community",
        request=CommunityCreateSerializer,
        responses={
            201: CommunityDetailSerializer,
            400: dict,
        },
    )
    def post(self, request):
        serializer = CommunityCreateSerializer(data=request.data, context={"request": request})
        serializer.is_valid(raise_exception=True)
        community = serializer.save()
        community_with_count = Community.objects.annotate(
            members_count=Count("memberships", distinct=True)
        ).get(pk=community.pk)
        return Response(
            CommunityDetailSerializer(community_with_count, context={"request": request}).data,
            status=status.HTTP_201_CREATED,
        )


class CommunityDetailView(APIView):
    """Retrieve, update, or delete a specific community."""

    permission_classes = [permissions.IsAuthenticated]

    def get_object(self, pk):
        return get_object_or_404(
            Community.objects.annotate(members_count=Count("memberships", distinct=True)),
            pk=pk,
        )

    @extend_schema(
        tags=["community"],
        summary="Retrieve community details",
        responses={
            200: CommunityDetailSerializer,
            403: dict,
            404: dict,
        },
    )
    def get(self, request, pk):
        community = self.get_object(pk)
        if community.visibility == CommunityVisibility.PRIVATE:
            is_member = community.memberships.filter(user=request.user).exists()
            if not is_member:
                return Response(
                    {"detail": "This is a private community. Membership is required to view full details."},
                    status=status.HTTP_403_FORBIDDEN,
                )
        return Response(CommunityDetailSerializer(community, context={"request": request}).data)

    @extend_schema(
        tags=["community"],
        summary="Update community details (owner/admin only)",
        request=CommunityUpdateSerializer,
        responses={
            200: CommunityDetailSerializer,
            403: dict,
            404: dict,
        },
    )
    def patch(self, request, pk):
        community = self.get_object(pk)
        has_perm = IsCommunityAdminOrOwner().has_object_permission(request, self, community)
        if not has_perm:
            return Response(
                {"detail": "You do not have permission to edit this community."},
                status=status.HTTP_403_FORBIDDEN,
            )
        serializer = CommunityUpdateSerializer(
            instance=community,
            data=request.data,
            partial=True,
            context={"request": request},
        )
        serializer.is_valid(raise_exception=True)
        updated = serializer.save()
        # Re-fetch with annotation
        updated_with_count = Community.objects.annotate(
            members_count=Count("memberships", distinct=True)
        ).get(pk=updated.pk)
        return Response(CommunityDetailSerializer(updated_with_count, context={"request": request}).data)

    @extend_schema(
        tags=["community"],
        summary="Replace community details (owner/admin only)",
        request=CommunityUpdateSerializer,
        responses={
            200: CommunityDetailSerializer,
            403: dict,
            404: dict,
        },
    )
    def put(self, request, pk):
        return self.patch(request, pk)

    @extend_schema(
        tags=["community"],
        summary="Delete community (owner only)",
        responses={
            204: None,
            403: dict,
            404: dict,
        },
    )
    def delete(self, request, pk):
        community = self.get_object(pk)
        if community.owner != request.user:
            return Response(
                {"detail": "Only the community owner can delete this community."},
                status=status.HTTP_403_FORBIDDEN,
            )
        community.delete()
        return Response(status=status.HTTP_204_NO_CONTENT)


class CommunityMembersView(APIView):
    """Retrieve community member roster."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        tags=["community"],
        summary="List community members",
        responses={
            200: CommunityMemberSerializer(many=True),
            403: dict,
            404: dict,
        },
    )
    def get(self, request, pk):
        community = get_object_or_404(Community, pk=pk)
        if community.visibility == CommunityVisibility.PRIVATE:
            is_member = community.memberships.filter(user=request.user).exists()
            if not is_member:
                return Response(
                    {"detail": "This is a private community. Membership is required to view members."},
                    status=status.HTTP_403_FORBIDDEN,
                )
        members = community.memberships.select_related("user", "user__social_profile").order_by("joined_at")
        return Response(CommunityMemberSerializer(members, many=True).data)


class CommunityJoinView(APIView):
    """Join a public community."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        tags=["community"],
        summary="Join a community",
        request=None,
        responses={
            201: CommunityMemberSerializer,
            400: dict,
            403: dict,
            404: dict,
        },
    )
    def post(self, request, pk):
        community = get_object_or_404(Community, pk=pk)

        existing = CommunityMembership.objects.filter(community=community, user=request.user).first()
        if existing:
            return Response(
                {"detail": "You are already a member of this community."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if community.visibility == CommunityVisibility.PRIVATE:
            return Response(
                {"detail": "Private communities cannot be joined directly in this phase."},
                status=status.HTTP_403_FORBIDDEN,
            )

        membership = CommunityMembership.objects.create(
            community=community,
            user=request.user,
            role=CommunityRole.MEMBER,
        )
        return Response(
            CommunityMemberSerializer(membership).data,
            status=status.HTTP_201_CREATED,
        )


class CommunityLeaveView(APIView):
    """Leave a community."""

    permission_classes = [permissions.IsAuthenticated]

    @extend_schema(
        tags=["community"],
        summary="Leave a community",
        request=None,
        responses={
            200: dict,
            400: dict,
            404: dict,
        },
    )
    def post(self, request, pk):

        community = get_object_or_404(Community, pk=pk)
        membership = CommunityMembership.objects.filter(community=community, user=request.user).first()

        if not membership:
            return Response(
                {"detail": "You are not a member of this community."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if membership.role == CommunityRole.OWNER:
            has_other_members = community.memberships.exclude(user=request.user).exists()
            if has_other_members:
                return Response(
                    {
                        "detail": (
                            "The community owner cannot leave while other members remain. "
                            "Please transfer ownership or delete the community."
                        )
                    },
                    status=status.HTTP_400_BAD_REQUEST,
                )
            community.delete()
            return Response(
                {"detail": "Community removed as the sole owner left."},
                status=status.HTTP_200_OK,
            )

        membership.delete()
        return Response(
            {"detail": "Successfully left the community."},
            status=status.HTTP_200_OK,
        )

