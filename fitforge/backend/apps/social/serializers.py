import re

from rest_framework import serializers

from apps.social.models import USERNAME_REGEX, SocialProfile


class SocialProfileSerializer(serializers.ModelSerializer):
    """Serializer for reading social profiles."""

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
        read_only_fields = ["id", "created_at", "updated_at"]


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
