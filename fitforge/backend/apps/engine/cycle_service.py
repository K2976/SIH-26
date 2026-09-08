"""The impure boundary around apps/engine/cycle.py — same role for the
cycle module (§10) that apps/engine/service.py plays for the rest of the
engine: gathers diary rows, buckets them into local days, calls the pure
functions, and persists CyclePattern.

apps/engine/service.py imports this module for two things:
  1. self_reported_cycle_inputs() — feeds Budget 3 (readiness) directly.
  2. compute_energy_availability_flag() — feeds the energy-availability
     floor (apps/engine/floors.py).

Everything else here (phase, forecast, learned pattern) is display/
forecast/pattern-analysis only and is assembled by build_cycle_state() for
the UserState "cycle" section. It never touches a budget.
"""

from __future__ import annotations

import datetime
from collections import defaultdict

from django.utils import timezone

from apps.diary.models import CycleLog, CycleProfile, PlannedSession, SetLog, WorkoutSession
from apps.diary.utils import active_rows, resolve_local_day
from apps.engine import calculator as calc
from apps.engine import cycle
from apps.engine.constants import DEFICIT_MARGIN_KCAL, HIGH_TRAINING_LOAD_DAYS_SINCE_REST
from apps.engine.models import CyclePattern, DailyState

EMPTY_CYCLE_STATE = {
    "enabled": False,
    "tracking_mode": None,
    "phase": None,
    "cycle_day": None,
    "confidence": None,
    "days_until_predicted_period": None,
    "learned_pattern": None,
    "energy_availability_flag": None,
}


def _all_period_starts(user) -> list[datetime.date]:
    rows = active_rows(CycleLog, user).filter(event="period_start")
    return [resolve_local_day(r.occurred_at, r.utc_offset) for r in rows]


def _cycle_profile(user) -> CycleProfile | None:
    return CycleProfile.objects.filter(user=user).first()


def self_reported_cycle_inputs(
    user, as_of: datetime.date
) -> tuple[int | None, list[str] | None]:
    """Today's self-reported energy rating and symptoms, or (None, None) if
    nothing was logged today — missing is not zero (CLAUDE.md rule 7). A
    logged entry with an explicitly empty symptom list is a real "no
    symptoms today" signal, distinct from not having logged at all.
    """
    rows = [
        r
        for r in active_rows(CycleLog, user)
        if resolve_local_day(r.occurred_at, r.utc_offset) == as_of
    ]
    if not rows:
        return None, None
    rows.sort(key=lambda r: r.occurred_at)
    energy = next((r.energy_rating for r in reversed(rows) if r.energy_rating is not None), None)
    symptoms: set[str] = set()
    for r in rows:
        symptoms.update(r.symptoms)
    return energy, sorted(symptoms)


def _days_period_overdue(user, as_of: datetime.date, tracking_mode: str) -> int | None:
    starts = _all_period_starts(user)
    forecast = cycle.forecast_next_period(starts, as_of, tracking_mode)
    if forecast.predicted_date is None:
        return None
    overdue = (as_of - forecast.predicted_date).days
    return max(overdue, 0)


def _deficit_streak_days(user, as_of: datetime.date) -> int:
    """Consecutive prior days (from the DailyState cache, not including
    today) where logged intake sat meaningfully below maintenance. A day
    with no cached data, or without both figures, ends the streak — an
    unlogged day is not assumed to be a deficit day.
    """
    streak = 0
    cursor = as_of - datetime.timedelta(days=1)
    while True:
        row = DailyState.objects.filter(user=user, local_date=cursor).first()
        if row is None or row.consumed_kcal is None or row.maintenance_kcal is None:
            break
        if row.consumed_kcal < row.maintenance_kcal - DEFICIT_MARGIN_KCAL:
            streak += 1
            cursor -= datetime.timedelta(days=1)
        else:
            break
    return streak


def compute_energy_availability_flag(
    user, as_of: datetime.date, tracking_mode: str, days_since_rest: int | None
) -> str | None:
    overdue = _days_period_overdue(user, as_of, tracking_mode)
    streak = _deficit_streak_days(user, as_of)
    high_load = (
        days_since_rest is not None and days_since_rest >= HIGH_TRAINING_LOAD_DAYS_SINCE_REST
    )
    return cycle.energy_availability_flag(tracking_mode, overdue, streak, high_load)


def _learned_pattern_summary(user) -> dict | None:
    rows = list(CyclePattern.objects.filter(user=user).order_by("cycle_day"))
    if not rows:
        return None
    return {
        "sample_cycles": max(r.sample_cycles for r in rows),
        "confidence": max(r.confidence for r in rows),
        "by_cycle_day": {
            str(r.cycle_day): {
                "avg_energy_rating": r.avg_energy_rating,
                "symptom_frequency": r.symptom_frequency,
                "avg_adherence": r.avg_adherence,
                "avg_completed_volume_kg": r.avg_completed_volume_kg,
                "sample_cycles": r.sample_cycles,
                "confidence": r.confidence,
            }
            for r in rows
        },
    }


def build_cycle_state(
    user, as_of: datetime.date, profile, energy_availability_flag: str | None
) -> dict:
    """Assemble the "cycle" section of UserState. Display/forecast/pattern
    only — nothing here is fed to a budget."""
    enabled = bool(profile.tracks_cycle) if profile else False
    if not enabled:
        return dict(EMPTY_CYCLE_STATE)

    cycle_profile = _cycle_profile(user)
    unknown = CycleProfile.TrackingMode.UNKNOWN
    tracking_mode = cycle_profile.tracking_mode if cycle_profile else unknown
    typical_period_days = cycle_profile.typical_period_days if cycle_profile else None

    starts = _all_period_starts(user)
    phase_result = cycle.derive_phase(starts, as_of, tracking_mode, typical_period_days)
    forecast = cycle.forecast_next_period(starts, as_of, tracking_mode)

    return {
        "enabled": True,
        "tracking_mode": tracking_mode,
        "phase": phase_result.phase,
        "cycle_day": phase_result.cycle_day,
        "confidence": phase_result.confidence,
        "days_until_predicted_period": forecast.days_until,
        "learned_pattern": _learned_pattern_summary(user),
        "energy_availability_flag": energy_availability_flag,
    }


# ---------------------------------------------------------------------------
# Nightly pattern recompute (management command boundary)
# ---------------------------------------------------------------------------


def _cycle_log_by_day(user) -> tuple[dict[datetime.date, set[str]], dict[datetime.date, int]]:
    rows = sorted(active_rows(CycleLog, user), key=lambda r: r.occurred_at)
    energy_by_day: dict[datetime.date, int] = {}
    symptoms_by_day: dict[datetime.date, set[str]] = defaultdict(set)
    for r in rows:
        d = resolve_local_day(r.occurred_at, r.utc_offset)
        if r.energy_rating is not None:
            energy_by_day[d] = r.energy_rating  # ascending order: last write wins == latest
        if r.symptoms:
            symptoms_by_day[d].update(r.symptoms)
    return symptoms_by_day, energy_by_day


def _adherence_by_day(user) -> dict[datetime.date, float]:
    by_day: dict[datetime.date, list[str]] = defaultdict(list)
    for r in active_rows(PlannedSession, user):
        d = resolve_local_day(r.occurred_at, r.utc_offset)
        by_day[d].append(r.status)
    result: dict[datetime.date, float] = {}
    for d, statuses in by_day.items():
        resolved = [s for s in statuses if s != "pending"]
        if resolved:
            result[d] = sum(1 for s in resolved if s == "completed") / len(resolved)
    return result


def _completed_volume_by_day(user) -> dict[datetime.date, float]:
    sessions = list(active_rows(WorkoutSession, user).only("id", "occurred_at", "utc_offset"))
    session_day = {s.pk: resolve_local_day(s.occurred_at, s.utc_offset) for s in sessions}
    sets_qs = (
        active_rows(SetLog, user)
        .filter(session_id__in=session_day, is_warmup=False)
        .select_related("exercise")
    )
    by_day: dict[datetime.date, float] = defaultdict(float)
    contributed: set[datetime.date] = set()
    for s in sets_qs:
        d = session_day.get(s.session_id)
        if d is None:
            continue
        vol = calc._set_volume_kg(
            calc.SetRecord(
                local_date=d,
                weight_kg=float(s.weight_kg) if s.weight_kg is not None else None,
                reps=s.reps,
                is_warmup=s.is_warmup,
                is_bodyweight=s.exercise.is_bodyweight,
                primary_muscles=tuple(s.exercise.primary_muscles),
            )
        )
        if vol is not None:
            by_day[d] += vol
            contributed.add(d)
    return {d: v for d, v in by_day.items() if d in contributed}


def recompute_cycle_pattern_for_user(user) -> int:
    """Rebuild every CyclePattern row for one user from scratch, wholesale —
    same "pure cache" contract as apps.engine.service.save_daily_state.
    Intended for the nightly cron command (§10: "recomputed nightly").
    """
    starts = sorted(_all_period_starts(user))
    if len(starts) < 2:
        CyclePattern.objects.filter(user=user).delete()
        return 0

    today = timezone.now().date()
    cycle_bounds = list(zip(starts, starts[1:], strict=False))
    open_cycle_end = today + datetime.timedelta(days=1)  # still-open current cycle
    cycle_bounds.append((starts[-1], open_cycle_end))

    symptoms_by_day, energy_by_day = _cycle_log_by_day(user)
    adherence_by_day = _adherence_by_day(user)
    volume_by_day = _completed_volume_by_day(user)

    records: list[cycle.CycleDayRecord] = []
    for cycle_index, (start, end) in enumerate(cycle_bounds, start=1):
        day = start
        while day < end:
            energy = energy_by_day.get(day)
            symptoms = symptoms_by_day.get(day)
            adherence = adherence_by_day.get(day)
            volume = volume_by_day.get(day)
            # Skip days with no logged signal at all — a calendar day inside
            # a cycle's date range is not the same as a day with data
            # (rule 7: missing is not zero, and here also not a sample).
            if energy is not None or symptoms or adherence is not None or volume is not None:
                records.append(
                    cycle.CycleDayRecord(
                        cycle_index=cycle_index,
                        cycle_day=(day - start).days + 1,
                        energy_rating=energy,
                        symptoms=tuple(sorted(symptoms)) if symptoms else (),
                        adherence=adherence,
                        completed_volume_kg=volume,
                    )
                )
            day += datetime.timedelta(days=1)

    rows = cycle.compute_cycle_pattern(records)
    seen_days = set()
    for row in rows:
        CyclePattern.objects.update_or_create(
            user=user,
            cycle_day=row.cycle_day,
            defaults={
                "avg_energy_rating": row.avg_energy_rating,
                "symptom_frequency": row.symptom_frequency,
                "avg_adherence": row.avg_adherence,
                "avg_completed_volume_kg": row.avg_completed_volume_kg,
                "sample_cycles": row.sample_cycles,
                "confidence": row.confidence,
            },
        )
        seen_days.add(row.cycle_day)
    CyclePattern.objects.filter(user=user).exclude(cycle_day__in=seen_days).delete()
    return len(rows)


def recompute_all_cycle_patterns() -> int:
    from django.contrib.auth import get_user_model

    count = 0
    for user in get_user_model().objects.filter(profile__tracks_cycle=True):
        count += recompute_cycle_pattern_for_user(user)
    return count
