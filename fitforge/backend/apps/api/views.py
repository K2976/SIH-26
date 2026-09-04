from django.conf import settings
from drf_spectacular.utils import extend_schema
from rest_framework import serializers
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView


class AppConfigSerializer(serializers.Serializer):
    min_supported_version = serializers.CharField()


class AppConfigView(APIView):
    permission_classes = [AllowAny]

    @extend_schema(responses=AppConfigSerializer)
    def get(self, request):
        return Response({"min_supported_version": settings.MIN_SUPPORTED_VERSION})
