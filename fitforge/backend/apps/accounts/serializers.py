from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.exceptions import AuthenticationFailed
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from apps.accounts.models import Goal, UserProfile, create_goal

User = get_user_model()


class SignupSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True)

    def validate_email(self, value):
        if User.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("An account with this email already exists.")
        return value

    def validate_password(self, value):
        validate_password(value)
        return value

    def create(self, validated_data):
        user = User.objects.create_user(
            username=validated_data["email"],
            email=validated_data["email"],
            password=validated_data["password"],
        )
        UserProfile.objects.create(user=user)
        return user


class EmailTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Login by email instead of username, on top of the default User model."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        del self.fields[self.username_field]
        self.fields["email"] = serializers.EmailField()

    def validate(self, attrs):
        try:
            user = User.objects.get(email__iexact=attrs["email"])
        except User.DoesNotExist as exc:
            raise AuthenticationFailed(
                "No active account found with the given credentials"
            ) from exc
        attrs[self.username_field] = user.get_username()
        return super().validate(attrs)


class TokenPairSerializer(serializers.Serializer):
    access = serializers.CharField()
    refresh = serializers.CharField()


class UserProfileSerializer(serializers.ModelSerializer):
    field_states = serializers.SerializerMethodField()

    class Meta:
        model = UserProfile
        fields = [
            "date_of_birth",
            "sex_at_birth",
            "height_cm",
            "waist_cm",
            "resting_hr",
            "timezone",
            "unit_preference",
            "tracks_cycle",
            "declined_fields",
            "onboarding_completed_at",
            "field_states",
        ]

    def get_field_states(self, obj) -> dict[str, str]:
        return {field: obj.field_state(field) for field in UserProfile.OPTIONAL_FIELDS}

    def validate_declined_fields(self, value):
        invalid = set(value) - set(UserProfile.OPTIONAL_FIELDS)
        if invalid:
            raise serializers.ValidationError(f"Not optional fields: {sorted(invalid)}")
        return value

    def update(self, instance, validated_data):
        declined_fields = validated_data.pop("declined_fields", None)
        for field, value in validated_data.items():
            setattr(instance, field, value)
            if value is not None and field in instance.declined_fields:
                instance.declined_fields.remove(field)
        if declined_fields is not None:
            instance.declined_fields = declined_fields
            for field in declined_fields:
                setattr(instance, field, None)
        instance.save()
        return instance


class GoalSerializer(serializers.ModelSerializer):
    clamp_reason = serializers.SerializerMethodField()

    class Meta:
        model = Goal
        fields = [
            "energy_direction",
            "training_focus",
            "target_rate_kg_per_week",
            "auto_adjust",
            "started_at",
            "ended_at",
            "clamp_reason",
        ]
        read_only_fields = ["started_at", "ended_at"]

    def get_clamp_reason(self, obj) -> str | None:
        return getattr(obj, "clamp_reason", None)

    def create(self, validated_data):
        return create_goal(user=self.context["request"].user, **validated_data)
