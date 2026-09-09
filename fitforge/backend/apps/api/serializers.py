from rest_framework import serializers

from apps.api.sync import MODEL_MAP

# Schema-only: SyncView parses request.data by hand (see sync.py) so that one
# endpoint can accept any diary event type. This serializer exists purely so
# drf-spectacular can generate a typed Retrofit client; it is never called by
# the view. Fields cover workout_session/set_log (this slice) plus the common
# DiaryEntry envelope; other event types add their fields when their slice lands.


class SyncEventSerializer(serializers.Serializer):
    type = serializers.ChoiceField(choices=list(MODEL_MAP.keys()))
    client_uuid = serializers.UUIDField()
    occurred_at = serializers.DateTimeField()
    utc_offset = serializers.CharField(required=False, default="+00:00")
    source = serializers.CharField(required=False, default="manual")
    supersedes = serializers.UUIDField(required=False, allow_null=True)

    # workout_session
    planned_session = serializers.UUIDField(required=False, allow_null=True)
    duration_minutes = serializers.IntegerField(required=False, allow_null=True)
    notes = serializers.CharField(required=False, allow_blank=True)

    # set_log
    session = serializers.UUIDField(required=False, allow_null=True)
    exercise = serializers.SlugField(required=False)
    set_index = serializers.IntegerField(required=False)
    reps = serializers.IntegerField(required=False)
    weight_kg = serializers.DecimalField(
        max_digits=6, decimal_places=2, required=False, allow_null=True
    )
    rpe = serializers.DecimalField(max_digits=3, decimal_places=1, required=False, allow_null=True)
    is_warmup = serializers.BooleanField(required=False, default=False)
    rom_achieved = serializers.CharField(required=False, allow_null=True)
    tempo_ms = serializers.IntegerField(required=False, allow_null=True)

    # cycle_log
    date = serializers.DateField(required=False)
    event = serializers.CharField(required=False, allow_blank=True)
    flow = serializers.CharField(required=False, allow_blank=True)
    symptoms = serializers.ListField(child=serializers.CharField(), required=False, default=list)
    energy_rating = serializers.IntegerField(
        required=False, allow_null=True, min_value=1, max_value=5
    )


class SyncRequestSerializer(serializers.Serializer):
    events = SyncEventSerializer(many=True)


class SyncErrorSerializer(serializers.Serializer):
    index = serializers.IntegerField()
    client_uuid = serializers.CharField(allow_null=True)
    error = serializers.CharField()


class SyncResponseSerializer(serializers.Serializer):
    success_count = serializers.IntegerField()
    errors = SyncErrorSerializer(many=True)
