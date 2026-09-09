from drf_spectacular.utils import extend_schema
from rest_framework import serializers, status
from rest_framework.parsers import FormParser, MultiPartParser
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.reference.food_label import (
    GeminiConfigurationError,
    GeminiLabelError,
    detect_food_labels,
)
from apps.reference.models import FoodItem

MAX_IMAGE_BYTES = 8 * 1024 * 1024
ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"}


class FoodLabelResponseSerializer(serializers.Serializer):
    candidates = serializers.ListField(child=serializers.CharField())


class FoodLabelRequestSerializer(serializers.Serializer):
    image = serializers.ImageField()


class FoodLabelView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser]

    @extend_schema(request=FoodLabelRequestSerializer, responses=FoodLabelResponseSerializer)
    def post(self, request):
        uploaded = request.FILES.get("image")
        if uploaded is None:
            return Response({"error": "image is required"}, status=status.HTTP_400_BAD_REQUEST)
        if uploaded.content_type not in ALLOWED_IMAGE_TYPES:
            uploaded.close()
            return Response(
                {"error": "image must be JPEG, PNG, WebP, HEIC, or HEIF"},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if uploaded.size > MAX_IMAGE_BYTES:
            uploaded.close()
            return Response(
                {"error": "image must be 8 MB or smaller"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            image = uploaded.read()
            names = [
                food.localized_name()
                for food in FoodItem.objects.filter(is_active=True).order_by("slug")
            ]
            candidates = detect_food_labels(image, uploaded.content_type, names)
        except GeminiConfigurationError as exc:
            return Response({"error": str(exc)}, status=status.HTTP_503_SERVICE_UNAVAILABLE)
        except GeminiLabelError as exc:
            return Response({"error": str(exc)}, status=status.HTTP_502_BAD_GATEWAY)
        finally:
            # Django may spool multipart bodies temporarily, but closing the
            # upload removes that request-scoped file. It is never persisted.
            uploaded.close()

        return Response({"candidates": candidates})
