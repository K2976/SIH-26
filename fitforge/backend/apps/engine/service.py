"""The impure boundary around apps/engine/calculator.py.

Everything here talks to the ORM and reads the clock (via django.utils.timezone,
exactly once, to resolve "today"). It gathers diary rows, buckets them into
local days with apps.diary.utils.resolve_local_day, converts them into the
calculator's plain input records, and calls the pure functions.

Both GET /api/v1/state/ (live) and DailyState (cache) go through
_compute_daily_metrics so they can never drift from each other — the cache
is provably just a persisted call to the same function the endpoint uses.
"""

import datetime

from django.contrib.auth import get_user_model
from django.utils import timezone

from apps.accounts.models import Goal
from apps.diary.models import MealItem, MealLog, PlannedSession, SetLog, WeightLog, WorkoutSession
from apps.diary.utils import resolve_local_day
from apps.engine import calculator as calc
from apps.engine.constants import ADHERENCE_WINDOW_DAYS, WEIGHT_TREND_WINDOW_DAYS
from apps.engine.models import DailyState

_DIARY_ENTRY_MODELS = (WorkoutSession, MealLog, WeightLog, PlannedSession)


def _active(model, user):
    """Diary rows are never edited in place — corrections supersede (CLAUDE.md).
    Only rows nothing else points back to via `supersedes` are current."""
    superseded_ids = (
        model.objects.filter(user=user)
        .exclude(supersedes__isnull=True)
        .values_list("supersedes_id", flat=True)
    )
    return model.objects.filter(user=user).exclude(pk__in=superseded_ids)


def _local_date(occurred_at, utc_offset) -> datetime.date:
    return resolve_local_day(occurred_at, utc_offset)


def _diary_edge_date(user, *, latest: bool) -> datetime.date | None:
    edge = None
    for model in _DIARY_ENTRY_MODELS:
        order = "-occurred_at" if latest else "occurred_at"
        row = (
            _active(model, user)
            .order_by(order)
            .values("occurred_at", "utc_offset")
            .first()
        )
        if row is None:
            continue
        d = _local_date(row["occurred_at"], row["utc_offset"])
        if edge is None or (latest and d > edge) or (not latest and d < edge):
            edge = d
    return edge


def _planned_volume_kg(raw) -> float | None:
    # No plan generator exists yet to populate planned_volume, so its shape
    # is provisional: {"total_kg": <number>}. Anything else is a gap, not 0.
    value = raw.get("total_kg") if isinstance(raw, dict) else None
    return float(value) if isinstance(value, int | float) else None


def _latest_weight_kg(user, as_of: datetime.date) -> float | None:
    upper = datetime.datetime.combine(
        as_of + datetime.timedelta(days=2), datetime.time.max, tzinfo=datetime.UTC
    )
    candidates = (
        _active(WeightLog, user).filter(occurred_at__lte=upper).order_by("-occurred_at")[:10]
    )
    for row in candidates:
        if _local_date(row.occurred_at, row.utc_offset) <= as_of:
            return float(row.weight_kg)
    profile = getattr(user, "profile", None)
    if profile is not None and profile.current_weight_kg is not None:
        return float(profile.current_weight_kg)
    return None


def _compute_daily_metrics(user, as_of: datetime.date) -> dict:
    profile = getattr(user, "profile", None)
    goal = Goal.objects.current(user)

    trend_start = as_of - datetime.timedelta(days=WEIGHT_TREND_WINDOW_DAYS - 1)
    week_start, week_end = calc.calendar_week_bounds(as_of)
    overall_start = min(trend_start, week_start)
    overall_end = max(as_of, week_end)
    padded_lo = datetime.datetime.combine(
        overall_start - datetime.timedelta(days=2), datetime.time.min, tzinfo=datetime.UTC
    )
    padded_hi = datetime.datetime.combine(
        overall_end + datetime.timedelta(days=2), datetime.time.max, tzinfo=datetime.UTC
    )

    def in_window(model):
        return _active(model, user).filter(occurred_at__gte=padded_lo, occurred_at__lte=padded_hi)

    # --- Planned sessions: adherence_7d, week_volume_planned ---
    planned_records = [
        calc.PlannedSessionRecord(
            local_date=_local_date(row.occurred_at, row.utc_offset),
            status=row.status,
            planned_volume_kg=_planned_volume_kg(row.planned_volume),
        )
        for row in in_window(PlannedSession)
    ]

    # --- Workout sessions + sets: week volume, muscle groups, burn ---
    all_sessions = list(_active(WorkoutSession, user).only("id", "occurred_at", "utc_offset"))
    training_days = frozenset(_local_date(s.occurred_at, s.utc_offset) for s in all_sessions)

    window_sessions = list(in_window(WorkoutSession))
    session_local_date = {s.pk: _local_date(s.occurred_at, s.utc_offset) for s in window_sessions}
    sets_qs = list(
        _active(SetLog, user).filter(session_id__in=session_local_date).select_related("exercise")
    )

    set_records = [
        calc.SetRecord(
            local_date=session_local_date[s.session_id],
            weight_kg=float(s.weight_kg) if s.weight_kg is not None else None,
            reps=s.reps,
            is_warmup=s.is_warmup,
            is_bodyweight=s.exercise.is_bodyweight,
            primary_muscles=tuple(s.exercise.primary_muscles),
        )
        for s in sets_qs
    ]

    bodyweight_today = _latest_weight_kg(user, as_of)
    burn_inputs = []
    for session in window_sessions:
        if session_local_date[session.pk] != as_of:
            continue
        mets = [
            float(s.exercise.met_value)
            for s in sets_qs
            if s.session_id == session.pk and not s.is_warmup
        ]
        mean_met = sum(mets) / len(mets) if mets else None
        duration_hours = (
            session.duration_minutes / 60 if session.duration_minutes is not None else None
        )
        burn_inputs.append(calc.SessionBurnInput(mean_met, duration_hours, bodyweight_today))

    # --- Meals: consumed_kcal_today, week_balance_kcal ---
    meal_local_date = {
        row.pk: _local_date(row.occurred_at, row.utc_offset) for row in in_window(MealLog)
    }
    calories_by_day: dict[datetime.date, list[float]] = {}
    for item in _active(MealItem, user).filter(log_id__in=meal_local_date):
        d = meal_local_date.get(item.log_id)
        if d is not None:
            calories_by_day.setdefault(d, []).append(float(item.calories))

    # --- Weights: trend window + 14-day gap flag ---
    weights_by_day: dict[datetime.date, list[float]] = {}
    for row in in_window(WeightLog):
        d = _local_date(row.occurred_at, row.utc_offset)
        weights_by_day.setdefault(d, []).append(float(row.weight_kg))
    weight_points = [
        ((d - trend_start).days, sum(vals) / len(vals))
        for d, vals in weights_by_day.items()
        if trend_start <= d <= as_of
    ]

    earliest_diary_date = _diary_edge_date(user, latest=False)

    # --- calculator calls ---
    adherence = calc.adherence_7d(planned_records, as_of)
    vol_planned = calc.week_volume_planned(planned_records, as_of)
    vol_completed = calc.week_volume_completed(set_records, as_of)
    vol_by_muscle = calc.volume_by_muscle_group(set_records, as_of)
    rest = calc.days_since_rest(training_days, as_of, earliest_diary_date)
    consumed = calc.consumed_kcal_today(calories_by_day.get(as_of, []))
    burned = calc.burned_kcal_today(burn_inputs)
    trend = calc.weight_trend(weight_points)

    age = calc.age_on(as_of, profile.date_of_birth) if profile else None
    maintenance = calc.maintenance_kcal(
        age=age,
        sex_at_birth=profile.sex_at_birth if profile else None,
        height_cm=float(profile.height_cm) if profile and profile.height_cm is not None else None,
        weight_kg=bodyweight_today,
        activity_level=profile.activity_level if profile else None,
    )
    target = calc.target_kcal_today(
        maintenance.kcal,
        goal.energy_direction if goal else None,
        float(goal.target_rate_kg_per_week) if goal else None,
    )

    balance_pairs = [
        (
            sum(calories_by_day[d]) if d in calories_by_day else None,
            maintenance.kcal,
        )
        for d in (as_of - datetime.timedelta(days=i) for i in range(ADHERENCE_WINDOW_DAYS))
    ]
    balance = calc.week_balance_kcal(balance_pairs)

    missing_food = calc.missing_food_days_7d(frozenset(calories_by_day), as_of)
    missing_weight = calc.missing_weight_days_14d(frozenset(weights_by_day), as_of)
    recently_changed = calc.goal_recently_changed(
        goal.started_at.date() if goal else None, as_of
    )
    insufficient = calc.insufficient_data_for_adaptation(missing_food, missing_weight)
    sufficiency = calc.energy_data_sufficiency(missing_food, maintenance.kcal is not None)

    return {
        "energy": {
            "maintenance_kcal": maintenance.kcal,
            "maintenance_source": maintenance.source,
            "maintenance_confidence": maintenance.confidence,
            "target_kcal_today": target,
            "consumed_kcal_today": consumed.value,
            "consumed_kcal_today_partial": consumed.partial,
            "burned_kcal_today": burned.value,
            "burned_kcal_today_partial": burned.partial,
            "week_balance_kcal": balance.value,
            "week_balance_kcal_partial": balance.partial,
            "data_sufficiency": sufficiency,
        },
        "training": {
            "week_volume_planned": vol_planned.value,
            "week_volume_planned_partial": vol_planned.partial,
            "week_volume_completed": vol_completed.value,
            "week_volume_completed_partial": vol_completed.partial,
            "volume_by_muscle_group": vol_by_muscle.value,
            "volume_by_muscle_group_partial": vol_by_muscle.partial,
            "adherence_7d": adherence.value,
            "adherence_7d_partial": adherence.partial,
            # Budget 3 (§7) isn't built yet — a future slice's job, and
            # CLAUDE.md is explicit that cycle phase must never feed it.
            "readiness_ceiling_today": None,
            "days_since_rest": rest.value,
            "days_since_rest_partial": rest.partial,
        },
        "body_trend": {
            "latest_weight_kg": bodyweight_today,
            "weight_trend_kg_per_week": trend.kg_per_week,
            "trend_confidence": trend.confidence,
        },
        "flags": {
            "missing_food_days_7d": missing_food,
            "missing_weight_days_14d": missing_weight,
            "goal_recently_changed": recently_changed,
            "insufficient_data_for_adaptation": insufficient,
        },
    }


def compute_user_state(user, as_of: datetime.date | None = None) -> dict:
    profile = getattr(user, "profile", None)
    goal = Goal.objects.current(user)
    if as_of is None:
        tz_name = profile.timezone if profile else "UTC"
        as_of = resolve_local_day(timezone.now(), tz_name)

    metrics = _compute_daily_metrics(user, as_of)

    return {
        "as_of": as_of,
        "profile": {
            "age": calc.age_on(as_of, profile.date_of_birth) if profile else None,
            "sex_at_birth": profile.sex_at_birth if profile else None,
            "height_cm": (
                float(profile.height_cm) if profile and profile.height_cm is not None else None
            ),
            "unit_preference": profile.unit_preference if profile else None,
            "tracks_cycle": profile.tracks_cycle if profile else None,
            "timezone": profile.timezone if profile else None,
        },
        "goal": {
            "energy_direction": goal.energy_direction if goal else None,
            "training_focus": goal.training_focus if goal else None,
            "target_rate_kg_per_week": float(goal.target_rate_kg_per_week) if goal else None,
            "auto_adjust": goal.auto_adjust if goal else None,
        },
        "energy": metrics["energy"],
        "training": metrics["training"],
        "body": {
            "latest_weight_kg": metrics["body_trend"]["latest_weight_kg"],
            "weight_trend_kg_per_week": metrics["body_trend"]["weight_trend_kg_per_week"],
            "trend_confidence": metrics["body_trend"]["trend_confidence"],
            # No pose/VO2max model built yet (§11) — a future slice's job.
            "fitness_age": None,
            "fitness_age_inputs": None,
        },
        # Cycle logic (§10) is a separate, not-yet-built slice. `phase` stays
        # null rather than defaulting to a guessed middle value (CLAUDE.md).
        "cycle": {
            "enabled": bool(profile.tracks_cycle) if profile else False,
            "tracking_mode": None,
            "phase": None,
            "cycle_day": None,
            "confidence": None,
            "days_until_predicted_period": None,
            "learned_pattern": None,
            "energy_availability_flag": None,
        },
        "flags": metrics["flags"],
    }


_PARTIAL_FIELD_MAP = (
    ("consumed_kcal", "energy", "consumed_kcal_today_partial"),
    ("burned_kcal", "energy", "burned_kcal_today_partial"),
    ("week_balance_kcal", "energy", "week_balance_kcal_partial"),
    ("week_volume_planned_kg", "training", "week_volume_planned_partial"),
    ("week_volume_completed_kg", "training", "week_volume_completed_partial"),
    ("volume_by_muscle_group", "training", "volume_by_muscle_group_partial"),
    ("adherence_7d", "training", "adherence_7d_partial"),
    ("days_since_rest", "training", "days_since_rest_partial"),
)


def save_daily_state(user, local_date: datetime.date) -> DailyState:
    metrics = _compute_daily_metrics(user, local_date)
    energy, training, flags, trend = (
        metrics["energy"],
        metrics["training"],
        metrics["flags"],
        metrics["body_trend"],
    )
    partial_fields = [
        field_name
        for field_name, section, flag_key in _PARTIAL_FIELD_MAP
        if metrics[section][flag_key]
    ]

    obj, _created = DailyState.objects.update_or_create(
        user=user,
        local_date=local_date,
        defaults={
            "consumed_kcal": energy["consumed_kcal_today"],
            "burned_kcal": energy["burned_kcal_today"],
            "maintenance_kcal": energy["maintenance_kcal"],
            "maintenance_source": energy["maintenance_source"],
            "maintenance_confidence": energy["maintenance_confidence"],
            "target_kcal": energy["target_kcal_today"],
            "week_balance_kcal": energy["week_balance_kcal"],
            "week_volume_planned_kg": training["week_volume_planned"],
            "week_volume_completed_kg": training["week_volume_completed"],
            "volume_by_muscle_group": training["volume_by_muscle_group"],
            "adherence_7d": training["adherence_7d"],
            "days_since_rest": training["days_since_rest"],
            "weight_trend_kg_per_week": trend["weight_trend_kg_per_week"],
            "weight_trend_confidence": trend["trend_confidence"],
            "missing_food_days_7d": flags["missing_food_days_7d"],
            "missing_weight_days_14d": flags["missing_weight_days_14d"],
            "goal_recently_changed": flags["goal_recently_changed"],
            "insufficient_data_for_adaptation": flags["insufficient_data_for_adaptation"],
            "energy_data_sufficiency": energy["data_sufficiency"],
            "partial_fields": partial_fields,
        },
    )
    return obj


def recompute_daily_state_for_days(user, local_dates) -> None:
    for local_date in local_dates:
        save_daily_state(user, local_date)


def rebuild_all_daily_states() -> int:
    """Wipe-and-recompute every user's DailyState, from their first diary
    entry to their last. Intended for the nightly cron command.

    ponytail: one save_daily_state() call (a handful of queries) per
    user-day — fine at MVP scale, worth batching if the diary ever gets big
    enough for this to show up in a profile.
    """
    count = 0
    for user in get_user_model().objects.all():
        earliest = _diary_edge_date(user, latest=False)
        if earliest is None:
            continue
        latest = _diary_edge_date(user, latest=True)
        day = earliest
        while day <= latest:
            save_daily_state(user, day)
            count += 1
            day += datetime.timedelta(days=1)
    return count
