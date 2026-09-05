"""Creates (or resets) the one fixed demo account with realistic diary history.

Idempotent: re-running deletes the demo user — which cascades through every
diary table via FK — and recreates it from scratch, so repeated taps on the
Android "Try demo" button (or repeated runs before a live demo) never
duplicate or drift. Every diary row is written through the normal model
layer, exactly like `apps/api/sync.py` would write it for a real device;
nothing here touches UserState or DailyState directly.

Rows are batched with `bulk_create` rather than one `.create()` per row:
this command exists to be re-run right before a live demo, and ~500 diary
rows one-at-a-time against a remote Postgres (Neon) took several minutes in
practice — plainly too slow for that purpose. `bulk_create` on PostgreSQL
still populates each object's `pk`, which is all `SetLog`/`MealItem` need to
link back to the `WorkoutSession`/`MealLog` bulk-created just before them.

DailyState is intentionally left untouched: it's a pure cache nothing reads
yet (`GET /api/v1/state/` always computes live — see docs/DECISIONS.md,
"DailyState is write-only in this slice"), and the nightly rebuild command
catches it up regardless. Populating it here would only add back the
per-day query cost this rewrite exists to cut.
"""

import datetime
import uuid
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.core.management.base import BaseCommand
from django.db import transaction
from django.utils import timezone

from apps.accounts.models import Goal, UserProfile
from apps.diary.models import CycleLog, MealItem, MealLog, SetLog, WeightLog, WorkoutSession

User = get_user_model()

# Not a secret: this is the public credential behind the app's own "Try
# demo" button, so keeping it out of source would buy nothing.
DEMO_EMAIL = "demo@fitforge.app"
DEMO_PASSWORD = "FitForgeDemo!2026"  # noqa: S105

UTC_OFFSET = "+00:00"
HISTORY_WEEKS = 6
_Q2 = Decimal("0.01")
_Q1 = Decimal("0.1")

# Small deterministic wobble layered on the weight trend so 6 weeks of
# weigh-ins isn't a suspiciously perfect line — real water-weight noise,
# not measurement error. Re-running the command reproduces it exactly.
_WEIGHT_NOISE_KG = [0.3, -0.1, 0.2, -0.4, 0.1, -0.2, 0.0]
_WEIGHT_START_KG = Decimal("70.0")
_WEIGHT_WEEKLY_LOSS_KG = Decimal("0.5")

# (weekday, [(exercise_slug, n_sets, reps, start_kg | None, weekly_progress_kg)])
# start_kg=None marks a genuinely bodyweight exercise (Exercise.is_bodyweight).
_PROGRAM = [
    (0, [  # Monday — push
        ("barbell-bench-press", 4, 8, Decimal("40.0"), Decimal("1.25")),
        ("overhead-press", 3, 8, Decimal("25.0"), Decimal("1.0")),
        ("push-up", 3, 12, None, None),
        ("triceps-pushdown", 3, 12, Decimal("20.0"), Decimal("0.5")),
    ]),
    (1, [  # Tuesday — pull
        ("barbell-row", 4, 8, Decimal("35.0"), Decimal("1.25")),
        ("lat-pulldown", 3, 10, Decimal("30.0"), Decimal("1.0")),
        ("pull-up", 3, 6, None, None),
        ("dumbbell-bicep-curl", 3, 10, Decimal("8.0"), Decimal("0.25")),
    ]),
    (3, [  # Thursday — legs
        ("barbell-back-squat", 4, 6, Decimal("45.0"), Decimal("1.5")),
        ("barbell-deadlift", 3, 5, Decimal("55.0"), Decimal("1.5")),
        ("leg-extension", 3, 12, Decimal("25.0"), Decimal("0.5")),
        ("standing-calf-raise", 3, 15, Decimal("15.0"), Decimal("0.5")),
    ]),
    (5, [  # Saturday — full body / conditioning
        ("kettlebell-swing", 3, 15, Decimal("16.0"), Decimal("0.0")),
        ("mountain-climber", 3, 20, None, None),
        ("burpee", 3, 12, None, None),
    ]),
]

# (hour, [(food_slug, qty, kcal, protein_g, carbs_g, fat_g)] x3 variants)
_MEALS = [
    (8, [
        ("oatmeal-berries", Decimal(1), Decimal(380), Decimal(14), Decimal(62), Decimal(8)),
        ("greek-yogurt-granola", Decimal(1), Decimal(340), Decimal(24), Decimal(40), Decimal(9)),
        ("veggie-egg-scramble", Decimal(1), Decimal(360), Decimal(28), Decimal(18), Decimal(20)),
    ]),
    (13, [
        ("chicken-rice-bowl", Decimal(1), Decimal(560), Decimal(42), Decimal(65), Decimal(12)),
        ("salmon-quinoa-salad", Decimal(1), Decimal(540), Decimal(38), Decimal(48), Decimal(18)),
        ("turkey-wrap", Decimal(1), Decimal(500), Decimal(34), Decimal(52), Decimal(14)),
    ]),
    (19, [
        ("salmon-veggies", Decimal(1), Decimal(520), Decimal(40), Decimal(28), Decimal(22)),
        ("lean-beef-stirfry", Decimal(1), Decimal(580), Decimal(44), Decimal(46), Decimal(20)),
        ("tofu-veggie-curry", Decimal(1), Decimal(480), Decimal(26), Decimal(50), Decimal(18)),
    ]),
]

_CYCLE_LENGTH_DAYS = 28
_PERIOD_FLOW_BY_DAY = ["medium", "heavy", "heavy", "medium", "light"]
_FIRST_PERIOD_OFFSET = 3


def _dt(local_date: datetime.date, hour: int) -> datetime.datetime:
    return datetime.datetime.combine(local_date, datetime.time(hour), tzinfo=datetime.UTC)


def _weight_kg_for_offset(offset: int) -> Decimal:
    trend = _WEIGHT_START_KG - _WEIGHT_WEEKLY_LOSS_KG * Decimal(offset) / Decimal(7)
    noise = Decimal(str(_WEIGHT_NOISE_KG[offset % len(_WEIGHT_NOISE_KG)]))
    return (trend + noise).quantize(_Q1)


def _seed_workouts(user, history_start, as_of) -> set[datetime.date]:
    touched = set()
    sessions = []
    session_sets = []  # [(session, [set_kwargs, ...]), ...]
    total_days = (as_of - history_start).days + 1
    for offset in range(total_days):
        local_date = history_start + datetime.timedelta(days=offset)
        program = next((ex for weekday, ex in _PROGRAM if weekday == local_date.weekday()), None)
        if program is None:
            continue
        week_index = offset // 7
        session = WorkoutSession(
            user=user, client_uuid=uuid.uuid4(), occurred_at=_dt(local_date, 7),
            utc_offset=UTC_OFFSET, source="manual", duration_minutes=55,
        )
        sets_for_session = []
        for exercise_slug, n_sets, reps, start_kg, weekly_progress in program:
            work_weight = start_kg + weekly_progress * week_index if start_kg is not None else None
            for set_index in range(1, n_sets + 1):
                is_warmup = set_index == 1 and work_weight is not None
                if is_warmup:
                    weight_kg = (work_weight * Decimal("0.6")).quantize(_Q2)
                else:
                    weight_kg = work_weight
                sets_for_session.append(
                    dict(
                        exercise_id=exercise_slug, set_index=set_index, reps=reps,
                        weight_kg=weight_kg, is_warmup=is_warmup,
                    )
                )
        sessions.append(session)
        session_sets.append((session, sets_for_session))
        touched.add(local_date)

    WorkoutSession.objects.bulk_create(sessions)  # populates .id on Postgres

    set_logs = [
        SetLog(
            user=user, client_uuid=uuid.uuid4(), occurred_at=session.occurred_at,
            utc_offset=UTC_OFFSET, source="manual", session=session, **kwargs,
        )
        for session, sets_for_session in session_sets
        for kwargs in sets_for_session
    ]
    SetLog.objects.bulk_create(set_logs)
    return touched


def _seed_meals(user, history_start, as_of) -> set[datetime.date]:
    touched = set()
    logs = []
    log_items = []  # [(log, [item_kwargs, ...]), ...]
    total_days = (as_of - history_start).days + 1
    for offset in range(total_days):
        local_date = history_start + datetime.timedelta(days=offset)
        days_ago = (as_of - local_date).days
        if days_ago != 0 and days_ago % 7 == 3:
            continue  # a realistic missed-logging day; never skips "today"
        log = MealLog(
            user=user, client_uuid=uuid.uuid4(), occurred_at=_dt(local_date, 8),
            utc_offset=UTC_OFFSET, source="manual",
        )
        variant = local_date.toordinal() % 3
        items_for_log = []
        for hour, options in _MEALS:
            food_slug, quantity, calories, protein_g, carbs_g, fat_g = options[variant]
            items_for_log.append(
                dict(
                    occurred_at=_dt(local_date, hour), food_slug=food_slug, quantity=quantity,
                    calories=calories, protein_g=protein_g, carbs_g=carbs_g, fat_g=fat_g,
                )
            )
        logs.append(log)
        log_items.append((log, items_for_log))
        touched.add(local_date)

    MealLog.objects.bulk_create(logs)  # populates .id on Postgres

    meal_items = [
        MealItem(
            user=user, client_uuid=uuid.uuid4(), utc_offset=UTC_OFFSET, source="manual",
            log=log, **kwargs,
        )
        for log, items_for_log in log_items
        for kwargs in items_for_log
    ]
    MealItem.objects.bulk_create(meal_items)
    return touched


def _seed_weights(user, history_start, as_of) -> set[datetime.date]:
    touched = set()
    weight_logs = []
    total_days = (as_of - history_start).days + 1
    for offset in range(total_days):
        local_date = history_start + datetime.timedelta(days=offset)
        days_ago = (as_of - local_date).days
        if days_ago != 0 and days_ago % 7 in (2, 5):
            continue  # a realistic missed-logging day; never skips "today"
        weight_logs.append(
            WeightLog(
                user=user, client_uuid=uuid.uuid4(), occurred_at=_dt(local_date, 7),
                utc_offset=UTC_OFFSET, source="manual", weight_kg=_weight_kg_for_offset(offset),
            )
        )
        touched.add(local_date)
    WeightLog.objects.bulk_create(weight_logs)
    return touched


def _seed_cycle(user, history_start, as_of) -> set[datetime.date]:
    touched = set()
    cycle_logs = []
    total_days = (as_of - history_start).days + 1
    offset = _FIRST_PERIOD_OFFSET
    while offset < total_days:
        for day_in_period, flow in enumerate(_PERIOD_FLOW_BY_DAY):
            period_offset = offset + day_in_period
            if period_offset >= total_days:
                break
            local_date = history_start + datetime.timedelta(days=period_offset)
            cycle_logs.append(
                CycleLog(
                    user=user, client_uuid=uuid.uuid4(), occurred_at=_dt(local_date, 9),
                    utc_offset=UTC_OFFSET, source="manual", date=local_date,
                    event="period_start" if day_in_period == 0 else "", flow=flow,
                    symptoms=["cramps", "fatigue"] if day_in_period < 2 else [],
                    energy_rating=2 if day_in_period < 2 else 3,
                )
            )
            touched.add(local_date)
        ovulation_offset = offset + 14
        if ovulation_offset < total_days:
            local_date = history_start + datetime.timedelta(days=ovulation_offset)
            cycle_logs.append(
                CycleLog(
                    user=user, client_uuid=uuid.uuid4(), occurred_at=_dt(local_date, 9),
                    utc_offset=UTC_OFFSET, source="manual", date=local_date,
                    event="", flow="", symptoms=["mild_cramping"], energy_rating=4,
                )
            )
            touched.add(local_date)
        offset += _CYCLE_LENGTH_DAYS
    CycleLog.objects.bulk_create(cycle_logs)
    return touched


class Command(BaseCommand):
    help = "Create or reset the fixed demo account (demo@fitforge.app) with realistic diary history"

    def handle(self, *args, **options):
        # Guarantees the exercise slugs this command seeds against exist,
        # even against a brand-new database — idempotent, same as any
        # normal deploy step.
        call_command("seed_exercises")

        now = timezone.now()
        as_of = now.date()
        history_start = as_of - datetime.timedelta(days=HISTORY_WEEKS * 7 - 1)

        with transaction.atomic():
            User.objects.filter(email__iexact=DEMO_EMAIL).delete()
            user = User.objects.create_user(
                username=DEMO_EMAIL, email=DEMO_EMAIL, password=DEMO_PASSWORD
            )

            touched: set[datetime.date] = set()
            touched |= _seed_workouts(user, history_start, as_of)
            touched |= _seed_meals(user, history_start, as_of)
            touched |= _seed_weights(user, history_start, as_of)
            touched |= _seed_cycle(user, history_start, as_of)

            UserProfile.objects.create(
                user=user,
                date_of_birth=datetime.date(1994, 6, 15),
                sex_at_birth=UserProfile.SexAtBirth.FEMALE,
                height_cm=Decimal("165.0"),
                current_weight_kg=_weight_kg_for_offset((as_of - history_start).days),
                waist_cm=Decimal("74.0"),
                resting_hr=58,
                timezone="UTC",
                unit_preference=UserProfile.UnitPreference.METRIC,
                activity_level=UserProfile.ActivityLevel.MODERATELY_ACTIVE,
                tracks_cycle=True,
                onboarding_completed_at=_dt(history_start, 8),
            )
            Goal.objects.create(
                user=user,
                energy_direction=Goal.EnergyDirection.CUT,
                training_focus=Goal.TrainingFocus.HYPERTROPHY,
                target_rate_kg_per_week=Decimal("0.40"),
                auto_adjust=True,
                started_at=_dt(history_start, 8),
            )

        self.stdout.write(
            self.style.SUCCESS(
                f"seed_demo_account: reset {DEMO_EMAIL} with {len(touched)} day(s) of diary history"
            )
        )
