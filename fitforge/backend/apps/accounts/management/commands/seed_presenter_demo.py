"""Seed the separate presenter demo through FitForge's public write paths.

This is deliberately not an extension of ``seed_demo_account``.  That command
backs the generic Try demo button; this one creates Priya Menon's presenter
account with the same signup, onboarding, cycle-settings, and diary-sync views
that the Android app uses.  The only direct write is deleting this account
before a re-run, which makes the command idempotent.  In particular it never
writes UserState, DailyState, or CyclePattern values.
"""

from __future__ import annotations

import datetime
import uuid
from decimal import Decimal
from unittest.mock import patch
from zoneinfo import ZoneInfo

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone
from rest_framework.test import APIRequestFactory, force_authenticate

from apps.accounts.views import GoalView, ProfileView, SignupView
from apps.api.cycle import CycleProfileView
from apps.api.sync import SyncView
from apps.diary.utils import resolve_local_day
from apps.engine.cycle_service import recompute_cycle_pattern_for_user

User = get_user_model()

# Separate from the public Try demo credential in seed_demo_account.py.
PRESENTER_DEMO_EMAIL = "priya.menon.demo@fitforge.app"
PRESENTER_DEMO_PASSWORD = "PriyaFitForge!2026"  # noqa: S105 -- public demo credential
PRESENTER_TIMEZONE = "Asia/Kolkata"
UTC_OFFSET = "+05:30"
HISTORY_DAYS = 61


def _local_timestamp(local_date: datetime.date, hour: int) -> datetime.datetime:
    """A diary timestamp after the product's 04:00 local-day boundary."""
    local = datetime.datetime.combine(local_date, datetime.time(hour), ZoneInfo(PRESENTER_TIMEZONE))
    return local.astimezone(datetime.UTC)


def _event_uuid(kind: str, local_date: datetime.date, detail: str = "") -> str:
    """Stable UUIDs make the event payload itself safe to retry, like Room sync."""
    return str(
        uuid.uuid5(uuid.NAMESPACE_URL, f"{PRESENTER_DEMO_EMAIL}:{kind}:{local_date}:{detail}")
    )


def _request(view, method: str, path: str, data: dict, user=None):
    factory = APIRequestFactory()
    request = getattr(factory, method)(path, data, format="json")
    if user is not None:
        force_authenticate(request, user=user)
    response = view.as_view()(request)
    if not 200 <= response.status_code < 300:
        raise CommandError(
            f"{method.upper()} {path} failed: {response.status_code} {response.data}"
        )
    return response


def _create_through_signup_and_onboarding(history_start: datetime.date):
    """Replay the same endpoints and ordering as the Android onboarding wizard."""
    _request(
        SignupView,
        "post",
        "/api/v1/auth/signup/",
        {"email": PRESENTER_DEMO_EMAIL, "password": PRESENTER_DEMO_PASSWORD},
    )
    user = User.objects.get(email=PRESENTER_DEMO_EMAIL)

    _request(
        ProfileView,
        "patch",
        "/api/v1/profile/",
        {
            "date_of_birth": "1996-02-18",
            "sex_at_birth": "female",
            "height_cm": "164.0",
            "current_weight_kg": "68.5",  # the weight entered on day one, not a derived trend
            "unit_preference": "metric",
            "timezone": PRESENTER_TIMEZONE,
        },
        user,
    )
    _request(ProfileView, "patch", "/api/v1/profile/", {"tracks_cycle": True}, user)

    # These two posts are the onboarding Goal and Target Rate steps.  Pin the
    # action time to the beginning of the story without bypassing create_goal.
    onboarding_time = _local_timestamp(history_start, 10)
    with patch("apps.accounts.models.timezone.now", return_value=onboarding_time):
        _request(
            GoalView,
            "post",
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "hypertrophy",
                "target_rate_kg_per_week": "0.50",
                "auto_adjust": True,
            },
            user,
        )
        _request(
            GoalView,
            "post",
            "/api/v1/goal/",
            {
                "energy_direction": "cut",
                "training_focus": "hypertrophy",
                "target_rate_kg_per_week": "0.35",
                "auto_adjust": True,
            },
            user,
        )

    _request(
        ProfileView,
        "patch",
        "/api/v1/profile/",
        {
            "waist_cm": "78.0",
            "resting_hr": 64,
            "activity_level": "moderately_active",
            "onboarding_completed_at": onboarding_time.isoformat(),
        },
        user,
    )
    # Cycle settings are a separate, authenticated settings endpoint after
    # the opt-in question.  The stated 27-day typical length is an input, not
    # a calculated value; the log below records the observed variation.
    _request(
        CycleProfileView,
        "patch",
        "/api/v1/cycle/profile/",
        {
            "tracking_mode": "natural",
            "typical_length_days": 27,
            "typical_period_days": 5,
            "confidence": 0.6,
        },
        user,
    )
    return user


def _add_event(
    events: list[dict],
    kind: str,
    local_date: datetime.date,
    *,
    hour: int,
    detail: str = "",
    **fields,
) -> str:
    client_uuid = _event_uuid(kind, local_date, detail)
    events.append(
        {
            "type": kind,
            "client_uuid": client_uuid,
            "occurred_at": _local_timestamp(local_date, hour).isoformat(),
            "utc_offset": UTC_OFFSET,
            **fields,
        }
    )
    return client_uuid


def _training_exercises(local_date: datetime.date) -> list[tuple[str, int, Decimal | None, str]]:
    """A believable rotation; camera rows retain the values its save path emits."""
    choices = {
        0: [
            ("barbell-back-squat", 8, Decimal("42.5"), "manual"),
            ("barbell-back-squat", 9, None, "camera"),
            ("push-up", 12, None, "camera"),
        ],
        1: [("barbell-bench-press", 8, Decimal("35.0"), "manual"), ("push-up", 14, None, "camera")],
        2: [
            ("lat-pulldown", 10, Decimal("32.5"), "manual"),
            ("dumbbell-bicep-curl", 11, None, "camera"),
        ],
        3: [
            ("barbell-back-squat", 6, Decimal("45.0"), "manual"),
            ("dumbbell-bicep-curl", 10, Decimal("8.0"), "manual"),
        ],
        4: [("barbell-bench-press", 9, Decimal("37.5"), "manual"), ("push-up", 10, None, "manual")],
        5: [
            ("barbell-back-squat", 8, Decimal("40.0"), "camera"),
            ("lat-pulldown", 10, Decimal("30.0"), "manual"),
        ],
        6: [("push-up", 12, None, "camera"), ("dumbbell-bicep-curl", 12, Decimal("8.0"), "manual")],
    }
    return choices[local_date.weekday()]


def _add_workout(
    events: list[dict], local_date: datetime.date, planned_session: str | None = None
) -> None:
    session_uuid = _add_event(
        events,
        "workout_session",
        local_date,
        hour=18,
        detail="session",
        source="manual",
        duration_minutes=47 if local_date.weekday() in (0, 2, 4) else 31,
        notes="Logged after work.",
        **({"planned_session": planned_session} if planned_session else {}),
    )
    for index, (exercise, reps, weight, source) in enumerate(
        _training_exercises(local_date), start=1
    ):
        # Two sets makes the logbook useful while preserving camera's real
        # contract: camera saveSet sends weight=null plus ROM and tempo.
        for set_number in (1, 2):
            camera = source == "camera"
            _add_event(
                events,
                "set_log",
                local_date,
                hour=18,
                detail=f"{exercise}-{set_number}",
                source="camera" if camera else "manual",
                session=session_uuid,
                exercise=exercise,
                set_index=index * 10 + set_number,
                reps=reps - (1 if set_number == 2 else 0),
                weight_kg=None if camera else str(weight) if weight is not None else None,
                rpe="7.0" if not camera else None,
                is_warmup=False,
                rom_achieved="full" if camera else None,
                tempo_ms=2100 if camera else None,
            )


def _add_meals(
    events: list[dict], local_date: datetime.date, calories: tuple[int, int, int]
) -> None:
    # Matches FoodRepository's confirmed-search write path rather than
    # inventing a special seed-only source.
    log_uuid = _add_event(events, "meal_log", local_date, hour=8, detail="day", source="search")
    # Snapshots are the values actually confirmed when logging, which is why
    # MealItem stores them rather than recalculating later from a food table.
    foods = (
        ("ifct-a001", 1, 25, 62, 12),
        ("ifct-b004", 1, 42, 58, 16),
        ("ifct-c008", 1, 38, 44, 19),
    )
    for index, ((slug, quantity, protein, carbs, fat), kcal) in enumerate(
        zip(foods, calories, strict=True)
    ):
        _add_event(
            events,
            "meal_item",
            local_date,
            hour=(8, 13, 20)[index],
            detail=f"{slug}-{index}",
            source="search",
            log=log_uuid,
            food_slug=slug,
            quantity=str(quantity),
            calories=str(kcal),
            protein_g=str(protein),
            carbs_g=str(carbs),
            fat_g=str(fat),
        )


def _add_cycle_day(
    events: list[dict], local_date: datetime.date, cycle_start: datetime.date
) -> None:
    cycle_day = (local_date - cycle_start).days + 1
    symptoms: list[str] = []
    energy = 4
    flow = ""
    event = ""
    if cycle_day == 1:
        event, flow, symptoms, energy = "period_start", "medium", ["cramps", "fatigue"], 2
    elif cycle_day == 2:
        flow, symptoms, energy = "heavy", ["cramps", "fatigue"], 2
    elif cycle_day == 3:
        flow, symptoms, energy = "medium", ["cramps"], 3
    elif cycle_day in (4, 5):
        flow, energy = "light", 3
    elif cycle_day >= 25:
        symptoms, energy = ["bloating", "low_energy"], 3
    elif cycle_day in (13, 14):
        symptoms, energy = ["mild_cramping"], 4
    _add_event(
        events,
        "cycle_log",
        local_date,
        hour=21,
        detail="daily",
        source="manual",
        date=local_date.isoformat(),
        event=event,
        flow=flow,
        symptoms=symptoms,
        energy_rating=energy,
    )


def _seed_diary_through_sync(user, history_start: datetime.date, as_of: datetime.date) -> int:
    events: list[dict] = []
    missed_week_start = history_start + datetime.timedelta(days=22)
    current_week_start = as_of - datetime.timedelta(days=as_of.weekday())
    planned_by_date: dict[datetime.date, str] = {}
    workout_dates: set[datetime.date] = set()

    # Three scheduled sessions a week; the fourth week is deliberately lost
    # to travel and a chest infection.  Current-week skips are left in the
    # plan for PlanDelta to redistribute rather than being hidden.
    day = history_start
    while day <= as_of:
        if day.weekday() in (0, 2, 4):
            if missed_week_start <= day < missed_week_start + datetime.timedelta(days=7):
                status = "skipped"
            elif day >= current_week_start and day <= as_of and day.weekday() in (2, 4):
                status = "skipped"
            else:
                status = "completed"
                workout_dates.add(day)
            planned_by_date[day] = _add_event(
                events,
                "planned_session",
                day,
                hour=9,
                detail="hypertrophy-plan",
                source="generated",
                date=day.isoformat(),
                focus=("lower" if day.weekday() == 0 else "upper"),
                planned_volume={"total_kg": 3100 if day.weekday() == 0 else 2400},
                status=status,
            )
        day += datetime.timedelta(days=1)

    # A stressful six-day run immediately before the demo supplies the real
    # high-load condition for the safety check.  It is intentionally not her
    # normal weekly rhythm.
    for offset in range(6):
        workout_dates.add(as_of - datetime.timedelta(days=offset))
    for workout_day in sorted(workout_dates):
        _add_workout(events, workout_day, planned_by_date.get(workout_day))

    # Food is mostly present but has explicit earlier gaps.  The last 14 days
    # are deliberately complete and below maintenance, so a sustained
    # deficit can be computed rather than inferred from missing days.
    food_gaps = {history_start + datetime.timedelta(days=n) for n in (9, 18, 31, 42)}
    current_cycle_start = history_start + datetime.timedelta(days=22)
    for offset in range(HISTORY_DAYS):
        local_date = history_start + datetime.timedelta(days=offset)
        if local_date not in food_gaps:
            calories = (
                (430, 620, 570)
                if local_date >= as_of - datetime.timedelta(days=13)
                else (470, 690, 650)
            )
            _add_meals(events, local_date, calories)
        # Weight entries are diary inputs, with ordinary water-weight noise.
        if offset % 9 != 4:
            weight = (
                Decimal("68.5")
                - Decimal("0.026") * offset
                + Decimal((offset % 5) - 2) * Decimal("0.08")
            )
            _add_event(
                events,
                "weight_log",
                local_date,
                hour=7,
                detail="morning",
                source="manual",
                weight_kg=str(weight.quantize(Decimal("0.1"))),
            )
        # First observed cycle lasted 22 days; the present cycle is now 39
        # days.  Every day contains an explicit energy/symptom observation.
        _add_cycle_day(
            events,
            local_date,
            history_start if local_date < current_cycle_start else current_cycle_start,
        )

    response = _request(SyncView, "post", "/api/v1/sync/", {"events": events}, user)
    if response.data["errors"]:
        error_count = len(response.data["errors"])
        sample_errors = response.data["errors"][:3]
        raise CommandError(f"sync rejected {error_count} seeded event(s): {sample_errors}")
    return response.data["success_count"]


class Command(BaseCommand):
    help = "Create/reset Priya Menon's separate presenter demo through signup, onboarding, and sync"

    def handle(self, *args, **options):
        call_command("seed_exercises")

        as_of = resolve_local_day(timezone.now(), PRESENTER_TIMEZONE)
        history_start = as_of - datetime.timedelta(days=HISTORY_DAYS - 1)
        with transaction.atomic():
            User.objects.filter(email__iexact=PRESENTER_DEMO_EMAIL).delete()
            user = _create_through_signup_and_onboarding(history_start)
            event_count = _seed_diary_through_sync(user, history_start, as_of)
            pattern_count = recompute_cycle_pattern_for_user(user)

        message = (
            f"seed_presenter_demo: reset {PRESENTER_DEMO_EMAIL}; synced {event_count} events "
            f"and computed {pattern_count} cycle patterns"
        )
        self.stdout.write(self.style.SUCCESS(message))
