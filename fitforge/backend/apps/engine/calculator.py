"""Pure derived-value functions for UserState.

Every function here takes plain data in and returns plain data out: no ORM
queries, no network calls, no `datetime.now()`. Callers (apps/engine/service.py)
are responsible for pulling diary rows, bucketing them into local days via
apps.diary.utils.resolve_local_day, and passing the results in.

Missing is not zero (CLAUDE.md hard rule 7): an unlogged day contributes
`None`, never 0, and any aggregate spanning missing days comes back wrapped
in `Derived` with `partial=True` rather than silently averaging a gap away.
"""

import statistics
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta

from apps.engine.constants import (
    ACTIVITY_MULTIPLIERS,
    ADHERENCE_WINDOW_DAYS,
    ENERGY_SUFFICIENT_MAX_MISSING_FOOD_DAYS,
    EQUATION_MAINTENANCE_CONFIDENCE,
    FOOD_GAP_WINDOW_DAYS,
    GOAL_RECENTLY_CHANGED_WINDOW_DAYS,
    INSUFFICIENT_MAX_MISSING_FOOD_DAYS_7D,
    INSUFFICIENT_MAX_MISSING_WEIGHT_DAYS_14D,
    KCAL_PER_KG,
    MIN_WEIGHINS_FOR_FULL_TREND_CONFIDENCE,
    WEIGHT_GAP_WINDOW_DAYS,
)


@dataclass(frozen=True)
class Derived[T]:
    """A value that may be unknown, or known-but-built-on-a-gap.

    value=None means "cannot be computed at all". partial=True means a real
    value was computed but at least one contributing day was missing from
    the diary, so it's a lower/incomplete figure, not a wrong one.
    """

    value: T | None
    partial: bool = False


@dataclass(frozen=True)
class WeightTrend:
    kg_per_week: float | None
    confidence: float | None


@dataclass(frozen=True)
class MaintenanceResult:
    kcal: float | None
    source: str | None  # "equation" | None (a future slice adds "blended")
    confidence: float | None


@dataclass(frozen=True)
class PlannedSessionRecord:
    local_date: date
    status: str  # "pending" | "completed" | "skipped" | "moved"
    planned_volume_kg: float | None


@dataclass(frozen=True)
class SetRecord:
    local_date: date
    weight_kg: float | None
    reps: int
    is_warmup: bool
    is_bodyweight: bool
    primary_muscles: tuple[str, ...]


@dataclass(frozen=True)
class SessionBurnInput:
    mean_met: float | None
    duration_hours: float | None
    bodyweight_kg: float | None


def _sum_or_none(values: Sequence[float | None]) -> Derived[float]:
    """Sum the known values. Empty or all-gap input is "no signal", not zero."""
    known = [v for v in values if v is not None]
    if not known:
        return Derived(None)
    return Derived(sum(known), partial=len(known) < len(values))


def calendar_week_bounds(as_of: date) -> tuple[date, date]:
    """Monday-Sunday week containing as_of. Exported: service.py needs the
    same bounds to know which diary rows to fetch."""
    start = as_of - timedelta(days=as_of.weekday())  # Monday
    return start, start + timedelta(days=6)


def _set_volume_kg(s: SetRecord) -> float | None:
    if s.weight_kg is not None:
        return s.weight_kg * s.reps
    # A bodyweight exercise legitimately has no external weight: 0 kg-volume
    # is a real measurement. Any other exercise missing weight_kg is a gap.
    return 0.0 if s.is_bodyweight else None


def adherence_7d(sessions: Sequence[PlannedSessionRecord], as_of: date) -> Derived[float]:
    window_start = as_of - timedelta(days=ADHERENCE_WINDOW_DAYS - 1)
    in_window = [s for s in sessions if window_start <= s.local_date <= as_of]
    if not in_window:
        return Derived(None)
    # A still-"pending" session whose date has passed has an unknown outcome
    # — it must not silently count as a miss (0) or a hit (1).
    resolved = [s for s in in_window if s.status != "pending"]
    if not resolved:
        return Derived(None)
    completed = sum(1 for s in resolved if s.status == "completed")
    return Derived(completed / len(resolved), partial=len(resolved) < len(in_window))


def week_volume_planned(sessions: Sequence[PlannedSessionRecord], as_of: date) -> Derived[float]:
    start, end = calendar_week_bounds(as_of)
    in_week = [s for s in sessions if start <= s.local_date <= end]
    if not in_week:
        return Derived(None)
    return _sum_or_none([s.planned_volume_kg for s in in_week])


def week_volume_completed(sets: Sequence[SetRecord], as_of: date) -> Derived[float]:
    start, end = calendar_week_bounds(as_of)
    in_week = [s for s in sets if start <= s.local_date <= end and not s.is_warmup]
    if not in_week:
        return Derived(None)
    return _sum_or_none([_set_volume_kg(s) for s in in_week])


def volume_by_muscle_group(sets: Sequence[SetRecord], as_of: date) -> Derived[dict[str, float]]:
    start, end = calendar_week_bounds(as_of)
    in_week = [s for s in sets if start <= s.local_date <= end and not s.is_warmup]
    if not in_week:
        return Derived(None)
    totals: dict[str, float] = {}
    contributed = False
    partial = False
    for s in in_week:
        vol = _set_volume_kg(s)
        if vol is None:
            partial = True
            continue
        contributed = True
        for muscle in s.primary_muscles:
            totals[muscle] = totals.get(muscle, 0.0) + vol
    if not contributed:
        return Derived(None)
    return Derived(totals, partial=partial)


def days_since_rest(
    training_days: frozenset[date], as_of: date, earliest_diary_date: date | None
) -> Derived[int]:
    if earliest_diary_date is None:
        return Derived(None)
    streak = 0
    cursor = as_of
    while cursor in training_days:
        streak += 1
        cursor -= timedelta(days=1)
        if cursor < earliest_diary_date:
            # Ran off the edge of known history without finding a rest day:
            # the true streak may be longer than what we can see.
            return Derived(streak, partial=True)
    return Derived(streak)


def consumed_kcal_today(meal_item_calories: Sequence[float]) -> Derived[float]:
    if not meal_item_calories:
        return Derived(None)
    return Derived(sum(meal_item_calories))


def burned_kcal_today(sessions: Sequence[SessionBurnInput]) -> Derived[float]:
    contributions = [
        s.mean_met * s.bodyweight_kg * s.duration_hours
        if s.mean_met is not None and s.duration_hours is not None and s.bodyweight_kg is not None
        else None
        for s in sessions
    ]
    return _sum_or_none(contributions)


def week_balance_kcal(
    daily_pairs: Sequence[tuple[float | None, float | None]],
) -> Derived[float]:
    """Consumed minus maintenance, summed over the trailing window.

    Deliberately NOT minus burned_kcal_today: maintenance already bakes in
    an activity factor (Mifflin-St Jeor + activity level), so subtracting
    logged exercise burn on top would double-count it. See DECISIONS.md.
    """
    diffs = [
        consumed - maintenance if consumed is not None and maintenance is not None else None
        for consumed, maintenance in daily_pairs
    ]
    return _sum_or_none(diffs)


def weight_trend(points: Sequence[tuple[float, float]]) -> WeightTrend:
    """Linear regression over (day_offset, weight_kg) points — not
    last-minus-first, so a single-day water-weight spike among several
    real points barely moves the slope.
    """
    n = len(points)
    if n < 2:
        return WeightTrend(None, None)
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    if len(set(xs)) < 2:
        return WeightTrend(None, None)  # no x-variance: can't fit a line
    slope, _intercept = statistics.linear_regression(xs, ys)
    size_factor = min(n / MIN_WEIGHINS_FOR_FULL_TREND_CONFIDENCE, 1.0)
    if len(set(ys)) < 2:
        # Perfectly flat weight: a real, well-supported trend (slope 0),
        # not an undefined correlation (statistics.correlation would raise).
        confidence = size_factor
    else:
        r = statistics.correlation(xs, ys)
        confidence = (r**2) * size_factor
    return WeightTrend(slope * 7, confidence)


def age_on(as_of: date, date_of_birth: date | None) -> int | None:
    if date_of_birth is None:
        return None
    years = as_of.year - date_of_birth.year
    if (as_of.month, as_of.day) < (date_of_birth.month, date_of_birth.day):
        years -= 1
    return years


def maintenance_kcal(
    age: int | None,
    sex_at_birth: str | None,
    height_cm: float | None,
    weight_kg: float | None,
    activity_level: str | None,
) -> MaintenanceResult:
    """Mifflin-St Jeor BMR times a standard TDEE activity multiplier.

    Mifflin-St Jeor is sex-specific (male/female terms only); intersex or
    unknown sex has no third branch to fall back on — per CLAUDE.md rule 2,
    that means None, not an averaged guess.
    """
    if age is None or height_cm is None or weight_kg is None or activity_level is None:
        return MaintenanceResult(None, None, None)
    if sex_at_birth not in ("male", "female"):
        return MaintenanceResult(None, None, None)
    multiplier = ACTIVITY_MULTIPLIERS.get(activity_level)
    if multiplier is None:
        return MaintenanceResult(None, None, None)
    bmr = 10 * weight_kg + 6.25 * height_cm - 5 * age
    bmr += 5 if sex_at_birth == "male" else -161
    return MaintenanceResult(bmr * float(multiplier), "equation", EQUATION_MAINTENANCE_CONFIDENCE)


def target_kcal_today(
    maintenance: float | None,
    energy_direction: str | None,
    target_rate_kg_per_week: float | None,
) -> float | None:
    if maintenance is None or energy_direction is None:
        return None
    if energy_direction in ("maintain", "not_tracking"):
        return maintenance
    if target_rate_kg_per_week is None:
        return None
    daily_delta = target_rate_kg_per_week * float(KCAL_PER_KG) / 7
    if energy_direction == "cut":
        return maintenance - daily_delta
    if energy_direction == "gain":
        return maintenance + daily_delta
    return None


def _missing_days(logged_days: frozenset[date], as_of: date, window_days: int) -> int:
    window = {as_of - timedelta(days=i) for i in range(window_days)}
    return len(window - logged_days)


def missing_food_days_7d(logged_days: frozenset[date], as_of: date) -> int:
    return _missing_days(logged_days, as_of, FOOD_GAP_WINDOW_DAYS)


def missing_weight_days_14d(logged_days: frozenset[date], as_of: date) -> int:
    return _missing_days(logged_days, as_of, WEIGHT_GAP_WINDOW_DAYS)


def goal_recently_changed(goal_started_at: date | None, as_of: date) -> bool:
    if goal_started_at is None:
        return False
    return (as_of - goal_started_at).days < GOAL_RECENTLY_CHANGED_WINDOW_DAYS


def insufficient_data_for_adaptation(missing_food_days: int, missing_weight_days: int) -> bool:
    return (
        missing_food_days > INSUFFICIENT_MAX_MISSING_FOOD_DAYS_7D
        or missing_weight_days > INSUFFICIENT_MAX_MISSING_WEIGHT_DAYS_14D
    )


def energy_data_sufficiency(missing_food_days: int, has_maintenance: bool) -> str:
    if has_maintenance and missing_food_days <= ENERGY_SUFFICIENT_MAX_MISSING_FOOD_DAYS:
        return "sufficient"
    return "insufficient"
