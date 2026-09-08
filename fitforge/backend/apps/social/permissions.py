from rest_framework import permissions

from apps.social.models import CommunityMembership, CommunityRole, CommunityVisibility


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
