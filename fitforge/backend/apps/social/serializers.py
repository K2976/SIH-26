import re

from django.db import transaction
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from apps.social.models import (
    USERNAME_REGEX,
    Community,
    CommunityMembership,
    CommunityRole,
    CommunityVisibility,
    SocialProfile,
)


class SocialProfileSerializer(serializers.ModelSerializer):
    """Serializer for reading social profiles.

    Every usage is `SocialProfileSerializer(instance).data` — never used to
    validate a write. `bio` and `is_private` have model-level defaults
    (blank="", False) so ModelSerializer marks them not-required for
    writes, which drf-spectacular reflects by omitting them from the
    response schema's `required` list too — even though a read response
    always includes them. That made the generated Kotlin fields nullable
    (`String?`, `Boolean?`) when they're actually always present, forcing
    every consumer to null-check values that can't be null. Declaring the
    whole serializer read-only fixes the schema to match reality.
    `profile_image` is genuinely nullable (model field has null=True) and
    deliberately excluded here."""

    class Meta:
        model = SocialProfile
        fields = [
            "id",
            "username",
            "display_name",
            "bio",
            "profile_image",
            "is_private",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "bio", "is_private", "created_at", "updated_at"]


class SocialProfileCreateSerializer(serializers.ModelSerializer):
    """Serializer for creating a new social profile for the authenticated user."""

    username = serializers.CharField(
        max_length=32,
        min_length=3,
        help_text="Unique public handle (3-32 alphanumeric characters and underscores).",
    )
    display_name = serializers.CharField(max_length=64, required=True)
    bio = serializers.CharField(required=False, allow_blank=True, default="")
    profile_image = serializers.ImageField(required=False, allow_null=True)
    is_private = serializers.BooleanField(required=False, default=False)

    class Meta:
        model = SocialProfile
        fields = ["username", "display_name", "bio", "profile_image", "is_private"]

    def validate_username(self, value):
        cleaned = value.strip()
        if not re.match(USERNAME_REGEX, cleaned):
            raise serializers.ValidationError(
                "Username must be 3-32 characters long and contain only letters, "
                "numbers, and underscores."
            )
        if SocialProfile.objects.filter(username__iexact=cleaned).exists():
            raise serializers.ValidationError("A social profile with this username already exists.")
        return cleaned

    def validate(self, attrs):
        request = self.context.get("request")
        if request and hasattr(request, "user") and request.user.is_authenticated:
            if SocialProfile.objects.filter(user=request.user).exists():
                raise serializers.ValidationError("User already has a social profile.")
        return attrs

    def create(self, validated_data):
        request = self.context.get("request")
        return SocialProfile.objects.create(user=request.user, **validated_data)


class SocialProfileUpdateSerializer(serializers.ModelSerializer):
    """Serializer for updating the authenticated user's social profile."""

    username = serializers.CharField(
        max_length=32,
        min_length=3,
        required=False,
        help_text="Unique public handle (3-32 alphanumeric characters and underscores).",
    )
    display_name = serializers.CharField(max_length=64, required=False)
    bio = serializers.CharField(required=False, allow_blank=True)
    profile_image = serializers.ImageField(required=False, allow_null=True)
    is_private = serializers.BooleanField(required=False)

    class Meta:
        model = SocialProfile
        fields = ["username", "display_name", "bio", "profile_image", "is_private"]

    def validate_username(self, value):
        cleaned = value.strip()
        if not re.match(USERNAME_REGEX, cleaned):
            raise serializers.ValidationError(
                "Username must be 3-32 characters long and contain only letters, "
                "numbers, and underscores."
            )
        request = self.context.get("request")
        user = request.user if request else None
        qs = SocialProfile.objects.filter(username__iexact=cleaned)
        if user and user.is_authenticated:
            qs = qs.exclude(user=user)
        if qs.exists():
            raise serializers.ValidationError("A social profile with this username already exists.")
        return cleaned


class UsernameCheckSerializer(serializers.Serializer):
    """Serializer for checking username availability."""

    username = serializers.CharField(max_length=32, min_length=3)


class UsernameCheckResponseSerializer(serializers.Serializer):
    """Response shape for CheckUsernameView — declared so drf-spectacular
    generates a typed model (UsernameCheckResponse) instead of a bare
    `dict`/`Map<String, Any>` with no accessible fields. Documentation only:
    the view still builds and returns its own dict; this changes nothing
    about the actual response body, only what the generated OpenAPI schema
    (and therefore the Android client) says its shape is."""

    available = serializers.BooleanField()
    message = serializers.CharField()
    reason = serializers.CharField(required=False)


class CommunityMemberUserSerializer(serializers.Serializer):
    """Exposes only public social profile information for community members."""

    id = serializers.IntegerField(read_only=True)
    username = serializers.SerializerMethodField()
    display_name = serializers.SerializerMethodField()
    profile_image = serializers.SerializerMethodField()
    bio = serializers.SerializerMethodField()

    @extend_schema_field(serializers.CharField)
    def get_username(self, user):
        if hasattr(user, "social_profile"):
            return user.social_profile.username
        return user.email.split("@")[0]

    @extend_schema_field(serializers.CharField)
    def get_display_name(self, user):
        if hasattr(user, "social_profile"):
            return user.social_profile.display_name
        return user.email.split("@")[0]

    @extend_schema_field(serializers.CharField(allow_null=True))
    def get_profile_image(self, user):
        if hasattr(user, "social_profile") and user.social_profile.profile_image:
            return user.social_profile.profile_image.url
        return None

    @extend_schema_field(serializers.CharField)
    def get_bio(self, user):
        if hasattr(user, "social_profile"):
            return user.social_profile.bio
        return ""


class CommunityMemberSerializer(serializers.ModelSerializer):
    """Serializer for a community membership entry."""

    user = CommunityMemberUserSerializer(read_only=True)

    class Meta:
        model = CommunityMembership
        fields = ["id", "role", "joined_at", "user"]
        read_only_fields = ["id", "role", "joined_at", "user"]


class CommunityListSerializer(serializers.ModelSerializer):
    """Serializer for community cards and listings."""

    members_count = serializers.IntegerField(read_only=True)
    current_user_role = serializers.SerializerMethodField()
    is_member = serializers.SerializerMethodField()

    class Meta:
        model = Community
        fields = [
            "id",
            "name",
            "description",
            "visibility",
            "members_count",
            "current_user_role",
            "is_member",
            "created_at",
        ]
        read_only_fields = fields

    @extend_schema_field(serializers.CharField(allow_null=True))
    def get_current_user_role(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return None
        membership = obj.memberships.filter(user=request.user).first()
        return membership.role if membership else None

    @extend_schema_field(serializers.BooleanField)
    def get_is_member(self, obj):
        return self.get_current_user_role(obj) is not None


class CommunityDetailSerializer(serializers.ModelSerializer):
    """Full detail serializer for a community."""

    owner = CommunityMemberUserSerializer(read_only=True)
    members_count = serializers.IntegerField(read_only=True)
    current_user_role = serializers.SerializerMethodField()
    is_member = serializers.SerializerMethodField()

    class Meta:
        model = Community
        fields = [
            "id",
            "name",
            "description",
            "visibility",
            "owner",
            "members_count",
            "current_user_role",
            "is_member",
            "created_at",
            "updated_at",
        ]
        read_only_fields = fields

    @extend_schema_field(serializers.CharField(allow_null=True))
    def get_current_user_role(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return None
        membership = obj.memberships.filter(user=request.user).first()
        return membership.role if membership else None

    @extend_schema_field(serializers.BooleanField)
    def get_is_member(self, obj):
        return self.get_current_user_role(obj) is not None


class CommunityCreateSerializer(serializers.ModelSerializer):
    name = serializers.CharField(max_length=128, min_length=2)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    visibility = serializers.ChoiceField(
        choices=CommunityVisibility.choices,
        default=CommunityVisibility.PUBLIC,
    )

    class Meta:
        model = Community
        fields = ["name", "description", "visibility"]

    def create(self, validated_data):
        request = self.context.get("request")
        with transaction.atomic():
            community = Community.objects.create(
                owner=request.user,
                **validated_data,
            )
            CommunityMembership.objects.create(
                community=community,
                user=request.user,
                role=CommunityRole.OWNER,
            )
            return community


class CommunityUpdateSerializer(serializers.ModelSerializer):
    name = serializers.CharField(max_length=128, min_length=2, required=False)
    description = serializers.CharField(required=False, allow_blank=True)
    visibility = serializers.ChoiceField(
        choices=CommunityVisibility.choices,
        required=False,
    )

    class Meta:
        model = Community
        fields = ["name", "description", "visibility"]
