from drf_spectacular.utils import extend_schema
from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView

from apps.accounts.models import UserProfile, current_goal
from apps.accounts.serializers import (
    EmailTokenObtainPairSerializer,
    GoalSerializer,
    SignupSerializer,
    TokenPairSerializer,
    UserProfileSerializer,
)


class SignupView(APIView):
    permission_classes = [permissions.AllowAny]

    @extend_schema(request=SignupSerializer, responses=TokenPairSerializer)
    def post(self, request):
        serializer = SignupSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        refresh = RefreshToken.for_user(user)
        return Response(
            {"access": str(refresh.access_token), "refresh": str(refresh)},
            status=status.HTTP_201_CREATED,
        )


class LoginView(TokenObtainPairView):
    permission_classes = [permissions.AllowAny]
    serializer_class = EmailTokenObtainPairSerializer


class ProfileView(generics.RetrieveUpdateAPIView):
    serializer_class = UserProfileSerializer

    def get_object(self):
        profile, _ = UserProfile.objects.get_or_create(user=self.request.user)
        return profile


class GoalView(generics.GenericAPIView):
    serializer_class = GoalSerializer

    def get(self, request):
        goal = current_goal(request.user)
        if goal is None:
            return Response(None)
        return Response(self.get_serializer(goal).data)

    def post(self, request):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        goal = serializer.save()
        return Response(self.get_serializer(goal).data, status=status.HTTP_201_CREATED)
