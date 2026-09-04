from rest_framework import serializers

from apps.reference.models import Exercise


class ExerciseSerializer(serializers.ModelSerializer):
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


class ManifestSerializer(serializers.Serializer):
    reference_version = serializers.IntegerField()
    table_counts = serializers.DictField(child=serializers.IntegerField())
