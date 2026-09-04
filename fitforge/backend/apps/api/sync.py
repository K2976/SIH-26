import datetime
from django.utils import timezone
from drf_spectacular.utils import extend_schema
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from apps.diary.models import (
    PlannedSession, WorkoutSession, SetLog, MealLog, MealItem,
    WeightLog, SleepLog, CycleLog
)
from apps.reference.models import Exercise

MODEL_MAP = {
    "planned_session": PlannedSession,
    "workout_session": WorkoutSession,
    "set_log": SetLog,
    "meal_log": MealLog,
    "meal_item": MealItem,
    "weight_log": WeightLog,
    "sleep_log": SleepLog,
    "cycle_log": CycleLog,
}

# Imported here, after MODEL_MAP, to avoid a circular import: serializers.py
# reads MODEL_MAP from this module at load time.
from apps.api.serializers import SyncRequestSerializer, SyncResponseSerializer  # noqa: E402


class SyncView(APIView):
    permission_classes = [IsAuthenticated]

    @extend_schema(request=SyncRequestSerializer, responses=SyncResponseSerializer)
    def post(self, request):
        events = request.data.get("events", [])
        if not isinstance(events, list):
            return Response({"error": "events must be a list"}, status=400)

        results = {
            "success_count": 0,
            "errors": []
        }

        now = timezone.now()
        max_future = now + datetime.timedelta(days=1)
        # Implausibly far past, e.g. before 1990
        min_past = datetime.datetime(1990, 1, 1, tzinfo=datetime.timezone.utc)

        for i, event in enumerate(events):
            try:
                event_type = event.get("type")
                if not event_type or event_type not in MODEL_MAP:
                    raise ValueError(f"Unknown or missing type: {event_type}")

                ModelClass = MODEL_MAP[event_type]
                
                client_uuid = event.get("client_uuid")
                if not client_uuid:
                    raise ValueError("client_uuid is required")
                
                occurred_at_str = event.get("occurred_at")
                if not occurred_at_str:
                    raise ValueError("occurred_at is required")
                    
                try:
                    occurred_at = datetime.datetime.fromisoformat(occurred_at_str.replace('Z', '+00:00'))
                except ValueError:
                    raise ValueError("invalid occurred_at format")

                if occurred_at > max_future:
                    raise ValueError("occurred_at cannot be more than one day in the future")
                if occurred_at < min_past:
                    raise ValueError("occurred_at is implausibly far in the past")

                utc_offset = event.get("utc_offset", "+00:00")
                source = event.get("source", "manual")
                supersedes_uuid = event.get("supersedes")

                supersedes = None
                if supersedes_uuid:
                    try:
                        supersedes = ModelClass.objects.get(client_uuid=supersedes_uuid, user=request.user)
                    except ModelClass.DoesNotExist:
                        # Allow setting to null if not found, or error?
                        # Usually better to error so the client knows their supersedes is invalid.
                        raise ValueError(f"supersedes target {supersedes_uuid} not found")

                # Extract model-specific fields
                defaults = {
                    "occurred_at": occurred_at,
                    "utc_offset": utc_offset,
                    "source": source,
                    "supersedes": supersedes,
                }
                
                if event_type == "planned_session":
                    defaults["date"] = event.get("date")
                    defaults["focus"] = event.get("focus", "")
                    defaults["planned_volume"] = event.get("planned_volume", {})
                    defaults["status"] = event.get("status", "pending")
                elif event_type == "workout_session":
                    planned_session_uuid = event.get("planned_session")
                    if planned_session_uuid:
                        try:
                            defaults["planned_session"] = PlannedSession.objects.get(client_uuid=planned_session_uuid, user=request.user)
                        except PlannedSession.DoesNotExist:
                            raise ValueError("planned_session target not found")
                    defaults["duration_minutes"] = event.get("duration_minutes")
                    defaults["notes"] = event.get("notes", "")
                elif event_type == "set_log":
                    session_uuid = event.get("session")
                    if not session_uuid:
                        raise ValueError("session is required for set_log")
                    try:
                        defaults["session"] = WorkoutSession.objects.get(client_uuid=session_uuid, user=request.user)
                    except WorkoutSession.DoesNotExist:
                        raise ValueError("session target not found")
                    
                    exercise_slug = event.get("exercise")
                    if not exercise_slug:
                        raise ValueError("exercise slug is required")
                    defaults["exercise_id"] = exercise_slug # to_field="slug" on ForeignKey uses exercise_id column to store slug
                    
                    defaults["set_index"] = event.get("set_index")
                    defaults["reps"] = event.get("reps")
                    defaults["weight_kg"] = event.get("weight_kg")
                    defaults["rpe"] = event.get("rpe")
                    defaults["is_warmup"] = event.get("is_warmup", False)
                    defaults["rom_achieved"] = event.get("rom_achieved")
                    defaults["tempo_ms"] = event.get("tempo_ms")
                elif event_type == "meal_item":
                    log_uuid = event.get("log")
                    if not log_uuid:
                        raise ValueError("log is required for meal_item")
                    try:
                        defaults["log"] = MealLog.objects.get(client_uuid=log_uuid, user=request.user)
                    except MealLog.DoesNotExist:
                        raise ValueError("log target not found")
                    
                    defaults["food_slug"] = event.get("food_slug", "")
                    defaults["quantity"] = event.get("quantity")
                    defaults["calories"] = event.get("calories")
                    defaults["protein_g"] = event.get("protein_g")
                    defaults["carbs_g"] = event.get("carbs_g")
                    defaults["fat_g"] = event.get("fat_g")
                elif event_type == "weight_log":
                    defaults["weight_kg"] = event.get("weight_kg")
                elif event_type == "sleep_log":
                    defaults["bedtime"] = event.get("bedtime")
                    defaults["wake"] = event.get("wake")
                    defaults["quality"] = event.get("quality")
                elif event_type == "cycle_log":
                    defaults["date"] = event.get("date")
                    defaults["event"] = event.get("event", "")
                    defaults["flow"] = event.get("flow", "")
                    defaults["symptoms"] = event.get("symptoms", [])
                    defaults["energy_rating"] = event.get("energy_rating")

                # Idempotent save
                obj, created = ModelClass.objects.update_or_create(
                    user=request.user,
                    client_uuid=client_uuid,
                    defaults=defaults
                )
                results["success_count"] += 1
            except Exception as e:
                results["errors"].append({
                    "index": i,
                    "client_uuid": event.get("client_uuid"),
                    "error": str(e)
                })

        # Return 200 even if there are errors (partial success)
        return Response(results, status=200)
