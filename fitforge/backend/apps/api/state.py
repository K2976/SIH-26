from drf_spectacular.utils import extend_schema
from rest_framework import serializers
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.engine.service import compute_user_state


class ProfileStateSerializer(serializers.Serializer):
    age = serializers.IntegerField(allow_null=True)
    sex_at_birth = serializers.CharField(allow_null=True)
    height_cm = serializers.FloatField(allow_null=True)
    unit_preference = serializers.CharField(allow_null=True)
    tracks_cycle = serializers.BooleanField(allow_null=True)
    timezone = serializers.CharField(allow_null=True)


class GoalStateSerializer(serializers.Serializer):
    energy_direction = serializers.CharField(allow_null=True)
    training_focus = serializers.CharField(allow_null=True)
    target_rate_kg_per_week = serializers.FloatField(allow_null=True)
    auto_adjust = serializers.BooleanField(allow_null=True)


class EnergyStateSerializer(serializers.Serializer):
    maintenance_kcal = serializers.FloatField(allow_null=True)
    maintenance_source = serializers.CharField(allow_null=True)
    maintenance_confidence = serializers.FloatField(allow_null=True)
    target_kcal_today = serializers.FloatField(allow_null=True)
    consumed_kcal_today = serializers.FloatField(allow_null=True)
    consumed_kcal_today_partial = serializers.BooleanField()
    burned_kcal_today = serializers.FloatField(allow_null=True)
    burned_kcal_today_partial = serializers.BooleanField()
    week_balance_kcal = serializers.FloatField(allow_null=True)
    week_balance_kcal_partial = serializers.BooleanField()
    data_sufficiency = serializers.CharField()


class TrainingStateSerializer(serializers.Serializer):
    week_volume_planned = serializers.FloatField(allow_null=True)
    week_volume_planned_partial = serializers.BooleanField()
    week_volume_completed = serializers.FloatField(allow_null=True)
    week_volume_completed_partial = serializers.BooleanField()
    volume_by_muscle_group = serializers.DictField(
        child=serializers.FloatField(), allow_null=True
    )
    volume_by_muscle_group_partial = serializers.BooleanField()
    adherence_7d = serializers.FloatField(allow_null=True)
    adherence_7d_partial = serializers.BooleanField()
    readiness_ceiling_today = serializers.FloatField(allow_null=True)
    days_since_rest = serializers.IntegerField(allow_null=True)
    days_since_rest_partial = serializers.BooleanField()


class BodyStateSerializer(serializers.Serializer):
    latest_weight_kg = serializers.FloatField(allow_null=True)
    weight_trend_kg_per_week = serializers.FloatField(allow_null=True)
    trend_confidence = serializers.FloatField(allow_null=True)
    fitness_age = serializers.FloatField(allow_null=True)
    fitness_age_inputs = serializers.JSONField(allow_null=True)


class CycleStateSerializer(serializers.Serializer):
    enabled = serializers.BooleanField()
    tracking_mode = serializers.CharField(allow_null=True)
    phase = serializers.CharField(allow_null=True)
    cycle_day = serializers.IntegerField(allow_null=True)
    confidence = serializers.FloatField(allow_null=True)
    days_until_predicted_period = serializers.IntegerField(allow_null=True)
    learned_pattern = serializers.JSONField(allow_null=True)
    energy_availability_flag = serializers.CharField(allow_null=True)


class FlagsStateSerializer(serializers.Serializer):
    missing_food_days_7d = serializers.IntegerField()
    missing_weight_days_14d = serializers.IntegerField()
    goal_recently_changed = serializers.BooleanField()
    insufficient_data_for_adaptation = serializers.BooleanField()


class UserStateSerializer(serializers.Serializer):
    as_of = serializers.DateField()
    profile = ProfileStateSerializer()
    goal = GoalStateSerializer()
    energy = EnergyStateSerializer()
    training = TrainingStateSerializer()
    body = BodyStateSerializer()
    cycle = CycleStateSerializer()
    flags = FlagsStateSerializer()


class StateView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(responses=UserStateSerializer)
    def get(self, request):
        state = compute_user_state(request.user)
        return Response(UserStateSerializer(state).data)
