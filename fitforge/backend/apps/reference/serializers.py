from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from apps.reference.models import Exercise, TrainingMusicMapping


@extend_schema_field({"type": "object"})
class NullableJSONField(serializers.JSONField):
    """A bare `JSONField(allow_null=True)` — untyped and nullable, with no
    $ref or type keyword — makes drf-spectacular emit `{nullable: true}`
    with nothing else. The openapi-generator Kotlin template can't infer a
    type for that and falls back to generating NullEnum, a zero-entry enum
    that's a syntax error on its own and fails the whole module's compile.
    Forcing `type: object` here doesn't change runtime behavior at all."""


class ExerciseSerializer(serializers.ModelSerializer):
    # Plain CharField, not the auto-derived ChoiceField — see the identical
    # note on TrainingMusicMappingSerializer.cycle_phase below. Read-only
    # endpoint (ExerciseDeltaView is a ListAPIView), so there's no write-side
    # validation to preserve.
    camera_position = serializers.CharField(allow_null=True, required=False)
    pose_config = NullableJSONField(allow_null=True, required=False)

    class Meta:
        model = Exercise
        # No `id` — slug is the identifier clients store.
        fields = [
            "slug",
            "name",
            "instructions",
            "primary_muscles",
            "secondary_muscles",
            "equipment",
            "movement_pattern",
            "form_cues",
            "met_value",
            "is_bodyweight",
            "pose_config",
            "camera_position",
            "is_active",
            "updated_at_version",
        ]


class TrainingMusicMappingSerializer(serializers.ModelSerializer):
    # Plain CharField, not the auto-derived ChoiceField: a *nullable* choices
    # field makes drf-spectacular emit an enum+blank oneOf union that the
    # Kotlin generator turns into an empty wrapper class with no accessible
    # value (see Exercise.camera_position for the same pre-existing shape).
    # A plain nullable string sidesteps that landmine for this new field.
    cycle_phase = serializers.CharField(allow_null=True, required=False)

    class Meta:
        model = TrainingMusicMapping
        fields = [
            "training_focus",
            "cycle_phase",
            "genre",
            "youtube_playlist_id",
            "is_active",
            "updated_at_version",
        ]


class ManifestSerializer(serializers.Serializer):
    reference_version = serializers.IntegerField()
    table_counts = serializers.DictField(child=serializers.IntegerField())
