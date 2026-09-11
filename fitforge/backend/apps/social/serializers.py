import re
from decimal import Decimal

from django.db import transaction
from django.utils import timezone
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from apps.social.models import (
    USERNAME_REGEX,
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


class ChallengeParticipantSerializer(serializers.ModelSerializer):
    """Serializer for challenge participant."""

    user = CommunityMemberUserSerializer(read_only=True)
    progress = serializers.SerializerMethodField()
    is_completed = serializers.BooleanField(read_only=True)

    class Meta:
        model = ChallengeParticipant
        fields = ["id", "user", "joined_at", "completed_at", "progress", "is_completed"]
        read_only_fields = fields

    @extend_schema_field(serializers.DictField)
    def get_progress(self, obj):
        return obj.progress


class ChallengeListSerializer(serializers.ModelSerializer):
    """Serializer for challenge cards and listings."""

    community = serializers.IntegerField(source="community_id", read_only=True)
    created_by = CommunityMemberUserSerializer(read_only=True)
    participants_count = serializers.IntegerField(read_only=True)
    current_user_participating = serializers.SerializerMethodField()
    current_user_progress = serializers.SerializerMethodField()
    is_participant = serializers.SerializerMethodField()
    user_progress = serializers.SerializerMethodField()
    status_display = serializers.SerializerMethodField()

    class Meta:
        model = Challenge
        fields = [
            "id",
            "community",
            "title",
            "description",
            "challenge_type",
            "target_value",
            "start_date",
            "end_date",
            "status",
            "status_display",
            "created_by",
            "participants_count",
            "current_user_participating",
            "current_user_progress",
            "is_participant",
            "user_progress",
            "created_at",
        ]
        read_only_fields = fields

    @extend_schema_field(serializers.CharField)
    def get_status_display(self, obj):
        return obj.get_status_display()

    @extend_schema_field(serializers.BooleanField)
    def get_current_user_participating(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return False
        return obj.participants.filter(user=request.user).exists()

    @extend_schema_field(serializers.BooleanField)
    def get_is_participant(self, obj):
        return self.get_current_user_participating(obj)

    @extend_schema_field(serializers.DictField(allow_null=True))
    def get_current_user_progress(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return None
        participation = obj.participants.filter(user=request.user).first()
        if participation:
            return participation.progress
        return None

    @extend_schema_field(serializers.DictField(allow_null=True))
    def get_user_progress(self, obj):
        return self.get_current_user_progress(obj)


class ChallengeDetailSerializer(serializers.ModelSerializer):
    """Full detail serializer for a challenge."""

    community = serializers.IntegerField(source="community_id", read_only=True)
    created_by = CommunityMemberUserSerializer(read_only=True)
    participants_count = serializers.IntegerField(read_only=True)
    current_user_participating = serializers.SerializerMethodField()
    current_user_progress = serializers.SerializerMethodField()
    is_participant = serializers.SerializerMethodField()
    user_progress = serializers.SerializerMethodField()
    status_display = serializers.SerializerMethodField()
    participants = ChallengeParticipantSerializer(many=True, read_only=True)

    class Meta:
        model = Challenge
        fields = [
            "id",
            "community",
            "title",
            "description",
            "challenge_type",
            "target_value",
            "start_date",
            "end_date",
            "status",
            "status_display",
            "created_by",
            "participants_count",
            "current_user_participating",
            "current_user_progress",
            "is_participant",
            "user_progress",
            "participants",
            "created_at",
            "updated_at",
        ]
        read_only_fields = fields

    @extend_schema_field(serializers.CharField)
    def get_status_display(self, obj):
        return obj.get_status_display()

    @extend_schema_field(serializers.BooleanField)
    def get_current_user_participating(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return False
        return obj.participants.filter(user=request.user).exists()

    @extend_schema_field(serializers.BooleanField)
    def get_is_participant(self, obj):
        return self.get_current_user_participating(obj)

    @extend_schema_field(serializers.DictField(allow_null=True))
    def get_current_user_progress(self, obj):
        request = self.context.get("request")
        if not request or not request.user.is_authenticated:
            return None
        participation = obj.participants.filter(user=request.user).first()
        if participation:
            return participation.progress
        return None

    @extend_schema_field(serializers.DictField(allow_null=True))
    def get_user_progress(self, obj):
        return self.get_current_user_progress(obj)


class ChallengeCreateSerializer(serializers.ModelSerializer):
    title = serializers.CharField(max_length=128, min_length=2)
    description = serializers.CharField(required=False, allow_blank=True, default="")
    challenge_type = serializers.ChoiceField(choices=ChallengeType.choices)
    target_value = serializers.DecimalField(
        max_digits=10, decimal_places=2, min_value=Decimal("0.01")
    )
    start_date = serializers.DateTimeField()
    end_date = serializers.DateTimeField()

    class Meta:
        model = Challenge
        fields = [
            "title",
            "description",
            "challenge_type",
            "target_value",
            "start_date",
            "end_date",
        ]

    def validate(self, attrs):
        start_date = attrs.get("start_date")
        end_date = attrs.get("end_date")
        if start_date and end_date and end_date <= start_date:
            raise serializers.ValidationError("End date must be after start date.")
        return attrs

    def create(self, validated_data):
        request = self.context.get("request")
        community = self.context.get("community")
        now = timezone.now()
        start = validated_data["start_date"]
        end = validated_data["end_date"]
        initial_status = (
            ChallengeStatus.ACTIVE
            if start <= now <= end
            else (ChallengeStatus.COMPLETED if end < now else ChallengeStatus.UPCOMING)
        )
        return Challenge.objects.create(
            community=community,
            created_by=request.user,
            status=initial_status,
            **validated_data,
        )


class ChallengeUpdateSerializer(serializers.ModelSerializer):
    title = serializers.CharField(max_length=128, min_length=2, required=False)
    description = serializers.CharField(required=False, allow_blank=True)
    challenge_type = serializers.ChoiceField(choices=ChallengeType.choices, required=False)
    target_value = serializers.DecimalField(
        max_digits=10, decimal_places=2, min_value=Decimal("0.01"), required=False
    )
    start_date = serializers.DateTimeField(required=False)
    end_date = serializers.DateTimeField(required=False)
    status = serializers.ChoiceField(choices=ChallengeStatus.choices, required=False)

    class Meta:
        model = Challenge
        fields = [
            "title",
            "description",
            "challenge_type",
            "target_value",
            "start_date",
            "end_date",
            "status",
        ]

    def validate(self, attrs):
        start_date = attrs.get("start_date")
        end_date = attrs.get("end_date")
        instance = self.instance

        # Use instance values if not provided
        if start_date is None and instance:
            start_date = instance.start_date
        if end_date is None and instance:
            end_date = instance.end_date

        if start_date and end_date and end_date <= start_date:
            raise serializers.ValidationError("End date must be after start date.")
        return attrs
