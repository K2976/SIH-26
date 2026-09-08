"""Cycle-module endpoints that don't belong on the generic diary sync path
(apps/api/sync.py already handles writing CycleLog rows): settings
(CycleProfile) and the privacy requirements from §10 — full export, full
delete.
"""

from drf_spectacular.utils import extend_schema
from rest_framework import generics, serializers
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.diary.models import CycleLog, CycleProfile
from apps.engine.models import CyclePattern


class CycleProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = CycleProfile
        fields = ["tracking_mode", "typical_length_days", "typical_period_days", "confidence"]


class CycleProfileView(generics.RetrieveUpdateAPIView):
    """Tracking mode and self-reported cycle-length settings. Never inferred
    — set only by a direct question in onboarding/settings (§10)."""

    serializer_class = CycleProfileSerializer
    permission_classes = [IsAuthenticated]

    def get_object(self):
        profile, _ = CycleProfile.objects.get_or_create(user=self.request.user)
        return profile


class CycleLogExportSerializer(serializers.ModelSerializer):
    class Meta:
        model = CycleLog
        fields = [
            "client_uuid",
            "occurred_at",
            "utc_offset",
            "source",
            "created_at",
            "date",
            "event",
            "flow",
            "symptoms",
            "energy_rating",
        ]


class CyclePatternExportSerializer(serializers.ModelSerializer):
    class Meta:
        model = CyclePattern
        fields = [
            "cycle_day",
            "computed_at",
            "avg_energy_rating",
            "symptom_frequency",
            "avg_adherence",
            "avg_completed_volume_kg",
            "sample_cycles",
            "confidence",
        ]


class CycleExportSerializer(serializers.Serializer):
    profile = CycleProfileSerializer(allow_null=True)
    logs = CycleLogExportSerializer(many=True)
    learned_pattern = CyclePatternExportSerializer(many=True)


class CycleDataView(APIView):
    """GET: full export of every cycle-module row the user owns.
    DELETE: full, irreversible delete of every cycle-module row.

    Both requirements from §10: "Full export and full delete are
    requirements." Nothing here is partial — every CycleLog row (including
    ones superseded by a correction) is included/removed, since a
    superseded row is still cycle data sitting on the server.
    """

    permission_classes = [IsAuthenticated]

    @extend_schema(responses=CycleExportSerializer)
    def get(self, request):
        profile = CycleProfile.objects.filter(user=request.user).first()
        logs = CycleLog.objects.filter(user=request.user).order_by("date")
        patterns = CyclePattern.objects.filter(user=request.user).order_by("cycle_day")
        data = {
            "profile": CycleProfileSerializer(profile).data if profile else None,
            "logs": CycleLogExportSerializer(logs, many=True).data,
            "learned_pattern": CyclePatternExportSerializer(patterns, many=True).data,
        }
        return Response(data)

    @extend_schema(responses={204: None})
    def delete(self, request):
        CycleLog.objects.filter(user=request.user).delete()
        CyclePattern.objects.filter(user=request.user).delete()
        CycleProfile.objects.filter(user=request.user).delete()
        return Response(status=204)
