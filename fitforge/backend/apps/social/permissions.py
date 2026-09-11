from rest_framework import permissions

from apps.social.models import (
    ChallengeStatus,
    CommunityMembership,
    CommunityRole,
    CommunityVisibility,
)


class IsCommunityOwner(permissions.BasePermission):
    """Permission check for community owner."""

    def has_object_permission(self, request, view, obj):
        if not request.user or not request.user.is_authenticated:
            return False
        return obj.owner == request.user


class IsCommunityAdminOrOwner(permissions.BasePermission):
    """Permission check for community admin or owner."""

    def has_object_permission(self, request, view, obj):
        if not request.user or not request.user.is_authenticated:
            return False
        if obj.owner == request.user:
            return True
        return CommunityMembership.objects.filter(
            community=obj,
            user=request.user,
            role__in=[CommunityRole.OWNER, CommunityRole.ADMIN],
        ).exists()


class CanViewCommunityDetail(permissions.BasePermission):
    """Permission to view community details.

    Public communities can be viewed by anyone. Private communities can only
    be viewed by authenticated members.
    """

    def has_object_permission(self, request, view, obj):
        if obj.visibility == CommunityVisibility.PUBLIC:
            return True
        if not request.user or not request.user.is_authenticated:
            return False
        return CommunityMembership.objects.filter(community=obj, user=request.user).exists()


class IsChallengeCreatorOrCommunityAdmin(permissions.BasePermission):
    """Permission check for challenge creator or community admin/owner."""

    def has_object_permission(self, request, view, obj):
        if not request.user or not request.user.is_authenticated:
            return False
        if obj.created_by == request.user:
            return True
        # Check if user is community admin or owner
        return CommunityMembership.objects.filter(
            community=obj.community,
            user=request.user,
            role__in=[CommunityRole.OWNER, CommunityRole.ADMIN],
        ).exists()


class CanViewChallengeDetail(permissions.BasePermission):
    """Permission to view challenge details.

    Challenges in public communities can be viewed by anyone. Challenges in
    private communities can only be viewed by authenticated members.
    """

    def has_object_permission(self, request, view, obj):
        if obj.community.visibility == CommunityVisibility.PUBLIC:
            return True
        if not request.user or not request.user.is_authenticated:
            return False
        return CommunityMembership.objects.filter(
            community=obj.community, user=request.user
        ).exists()


class CanJoinChallenge(permissions.BasePermission):
    """Permission to join a challenge.

    User must be a member of the community and challenge must be joinable.
    """

    def has_object_permission(self, request, view, obj):
        if not request.user or not request.user.is_authenticated:
            return False
        # Must be community member
        is_member = CommunityMembership.objects.filter(
            community=obj.community, user=request.user
        ).exists()
        if not is_member:
            return False
        # Challenge must be upcoming or active (not completed/cancelled)
        if obj.status in [ChallengeStatus.COMPLETED, ChallengeStatus.CANCELLED]:
            return False
        return True
