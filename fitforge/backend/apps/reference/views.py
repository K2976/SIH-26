from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics, permissions
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.reference.models import Exercise, ReferenceVersion
from apps.reference.serializers import ExerciseSerializer, ManifestSerializer


class ManifestView(APIView):
    permission_classes = [permissions.AllowAny]

    @extend_schema(responses=ManifestSerializer)
    def get(self, request):
        return Response(
            {
                "reference_version": ReferenceVersion.current(),
                "table_counts": {"exercises": Exercise.objects.count()},
            }
        )


class ExerciseDeltaView(generics.ListAPIView):
    serializer_class = ExerciseSerializer
    permission_classes = [permissions.AllowAny]
    pagination_class = None

    @extend_schema(
        parameters=[
            OpenApiParameter(
                "since",
                int,
                description="Return rows changed after this reference_version. Default 0.",
            )
        ]
    )
    def get(self, request, *args, **kwargs):
        return super().get(request, *args, **kwargs)

    def get_queryset(self):
        raw_since = self.request.query_params.get("since", "0")
        try:
            since = int(raw_since)
        except ValueError as exc:
            raise ValidationError({"since": "Must be an integer."}) from exc
        return Exercise.objects.filter(updated_at_version__gt=since).order_by("updated_at_version")
