"""Pure functions for the cycle module (§10): phase derivation, period
forecast, pattern learning, and the energy availability check.

Same contract as apps/engine/calculator.py: plain data in, plain data out.
No ORM, no I/O, no `datetime.now()`. The impure boundary is
apps/engine/cycle_service.py.

Two paths, and they are different (§10):
  - Phase (derive_phase) is display/forecast/pattern-analysis only. It is
    NOT a field on UserState (apps/engine/types.py) and no budget reads it.
  - Symptoms and energy_rating feed the readiness ceiling directly, via
    UserState.self_reported_energy / self_reported_symptoms — see
    apps/engine/budgets.py. That wiring lives in cycle_service.py /
    service.py, not here.

Phase is only ever a label when tracking_mode == "natural": hormonal
contraception, pregnancy, postpartum, perimenopause, "not cycling", and
"unknown" all make a period-start-derived phase meaningless (§10). Pattern
learning (compute_cycle_pattern) is NOT gated this way — a user on hormonal
contraception still logs symptoms and energy, and her own data can still
show a consistent per-cycle-day effect worth surfacing.
"""

from __future__ import annotations

import statistics
from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta

from apps.engine.constants import (
    CYCLE_OVERDUE_UNCERTAIN_BUFFER_DAYS,
    CYCLE_PATTERN_FULL_CONFIDENCE_CYCLES,
    DEFAULT_PERIOD_DAYS,
    DEFICIT_STREAK_DAYS_THRESHOLD,
    ENERGY_AVAILABILITY_FLAG,
    FULL_CONFIDENCE_CYCLES,
    LUTEAL_PHASE_DAYS,
    MIN_CONFIDENCE_FOR_PHASE,
    MIN_PERIOD_STARTS_FOR_TYPICAL_LENGTH,
    OVULATORY_WINDOW_DAYS,
    PERIOD_OVERDUE_DAYS_THRESHOLD,
)

NATURAL = "natural"


@dataclass(frozen=True)
class TypicalLengthResult:
    typical_length_days: float | None
    confidence: float
    sample_intervals: int


@dataclass(frozen=True)
class PhaseResult:
    phase: str | None  # "menstrual" | "follicular" | "ovulatory" | "luteal" | None
    cycle_day: int | None
    confidence: float | None
    reason: str


@dataclass(frozen=True)
class ForecastResult:
    predicted_date: date | None
    days_until: int | None
    confidence: float | None


@dataclass(frozen=True)
class CycleDayRecord:
    """One day's data, tagged with which cycle it fell in and how far into
    that cycle. Built by cycle_service.py from CycleLog + DailyState."""

    cycle_index: int
    cycle_day: int
    energy_rating: int | None
    symptoms: tuple[str, ...]
    adherence: float | None
    completed_volume_kg: float | None


@dataclass(frozen=True)
class CyclePatternRow:
    cycle_day: int
    avg_energy_rating: float | None
    symptom_frequency: dict[str, float]
    avg_adherence: float | None
    avg_completed_volume_kg: float | None
    sample_cycles: int
    confidence: float


def infer_typical_length(period_starts: Sequence[date]) -> TypicalLengthResult:
    """Median interval between consecutive period starts.

    Median, not mean, so one anomalous cycle (illness, travel) doesn't drag
    the estimate around the way a single outlier would a mean.
    """
    distinct = sorted(set(period_starts))
    if len(distinct) < MIN_PERIOD_STARTS_FOR_TYPICAL_LENGTH:
        return TypicalLengthResult(None, 0.0, 0)
    intervals = [(b - a).days for a, b in zip(distinct, distinct[1:], strict=False)]
    typical = statistics.median(intervals)
    confidence = min(len(intervals) / FULL_CONFIDENCE_CYCLES, 1.0)
    return TypicalLengthResult(float(typical), confidence, len(intervals))


def derive_phase(
    period_starts: Sequence[date],
    as_of: date,
    tracking_mode: str,
    typical_period_days: int | None = None,
) -> PhaseResult:
    """Derive today's cycle phase from her own period-start history.

    Returns phase=None (never a default middle value) when: tracking_mode
    isn't "natural", there's no history, the history is too sparse to be
    confident, or the current cycle has run well past its typical length
    (an overdue period — see cycle_service.py's energy availability check,
    which is exactly what watches for this situation).
    """
    if tracking_mode != NATURAL:
        return PhaseResult(None, None, None, "phase is not meaningful for this tracking mode")

    starts_before = [d for d in period_starts if d <= as_of]
    if not starts_before:
        return PhaseResult(None, None, None, "no period-start history")

    last_start = max(starts_before)
    cycle_day = (as_of - last_start).days + 1

    typical = infer_typical_length(period_starts)
    if typical.typical_length_days is None or typical.confidence < MIN_CONFIDENCE_FOR_PHASE:
        reason = "insufficient cycle history for a confident phase"
        return PhaseResult(None, cycle_day, typical.confidence, reason)

    if cycle_day > typical.typical_length_days + CYCLE_OVERDUE_UNCERTAIN_BUFFER_DAYS:
        reason = "cycle day is past typical length; phase uncertain"
        return PhaseResult(None, cycle_day, typical.confidence, reason)

    period_days = typical_period_days or DEFAULT_PERIOD_DAYS
    ovulation_day = typical.typical_length_days - LUTEAL_PHASE_DAYS

    if cycle_day <= period_days:
        phase = "menstrual"
    elif cycle_day < ovulation_day - OVULATORY_WINDOW_DAYS:
        phase = "follicular"
    elif cycle_day <= ovulation_day + OVULATORY_WINDOW_DAYS:
        phase = "ovulatory"
    else:
        phase = "luteal"

    reason = f"cycle day {cycle_day} of ~{typical.typical_length_days:.0f}"
    return PhaseResult(phase, cycle_day, typical.confidence, reason)


def forecast_next_period(
    period_starts: Sequence[date],
    as_of: date,
    tracking_mode: str,
    typical_length_days: int | None = None,
    typical_length_confidence: float | None = None,
) -> ForecastResult:
    """Predict the next period start from observed or directly reported history.

    Gated to tracking_mode == "natural" for the same reason as phase: a
    forecast built on hormonal-contraception or perimenopause history would
    present false precision (CLAUDE.md rule 2).
    """
    if tracking_mode != NATURAL or not period_starts:
        return ForecastResult(None, None, None)

    typical = infer_typical_length(period_starts)
    observed_length = typical.typical_length_days
    # CycleProfile is a direct onboarding/settings input, not a fabricated
    # population default.  It lets a new tracker get an honestly lower-
    # confidence forecast before the three period starts required to infer a
    # personal median.  As soon as enough starts exist, observed history
    # takes precedence.
    length = observed_length if observed_length is not None else typical_length_days
    if length is None:
        return ForecastResult(None, None, None)

    starts_before = [d for d in period_starts if d <= as_of]
    if not starts_before:
        return ForecastResult(None, None, None)

    last_start = max(starts_before)
    predicted = last_start + timedelta(days=round(length))
    confidence = typical.confidence if observed_length is not None else typical_length_confidence
    return ForecastResult(predicted, (predicted - as_of).days, confidence)


def energy_availability_flag(
    tracking_mode: str,
    days_period_overdue: int | None,
    deficit_streak_days: int,
    high_training_load: bool,
) -> str | None:
    """The most important safety feature in the product (§10).

    Fires only when all hold: natural tracking, a significantly overdue
    period, a sustained deficit, and high training load. Returns a flag key
    — never a diagnosis, a condition name, or a score.
    """
    if tracking_mode != NATURAL:
        return None
    if days_period_overdue is None or days_period_overdue < PERIOD_OVERDUE_DAYS_THRESHOLD:
        return None
    if deficit_streak_days < DEFICIT_STREAK_DAYS_THRESHOLD:
        return None
    if not high_training_load:
        return None
    return ENERGY_AVAILABILITY_FLAG


def compute_cycle_pattern(records: Sequence[CycleDayRecord]) -> list[CyclePatternRow]:
    """Aggregate logged days by cycle_day across however many cycles exist.

    From her data, never from a textbook (§10). Every row carries
    sample_cycles and confidence so the UI never presents a pattern as more
    certain than the data supports.
    """
    by_day: dict[int, list[CycleDayRecord]] = defaultdict(list)
    for r in records:
        by_day[r.cycle_day].append(r)

    rows: list[CyclePatternRow] = []
    for cycle_day, day_records in sorted(by_day.items()):
        cycle_indices = {r.cycle_index for r in day_records}
        sample_cycles = len(cycle_indices)

        energies = [r.energy_rating for r in day_records if r.energy_rating is not None]
        avg_energy = statistics.fmean(energies) if energies else None

        symptom_cycles: dict[str, set[int]] = defaultdict(set)
        for r in day_records:
            for symptom in r.symptoms:
                symptom_cycles[symptom].add(r.cycle_index)
        symptom_frequency = (
            {symptom: len(idxs) / sample_cycles for symptom, idxs in symptom_cycles.items()}
            if sample_cycles
            else {}
        )

        adherences = [r.adherence for r in day_records if r.adherence is not None]
        avg_adherence = statistics.fmean(adherences) if adherences else None

        volumes = [r.completed_volume_kg for r in day_records if r.completed_volume_kg is not None]
        avg_volume = statistics.fmean(volumes) if volumes else None

        confidence = min(sample_cycles / CYCLE_PATTERN_FULL_CONFIDENCE_CYCLES, 1.0)

        rows.append(
            CyclePatternRow(
                cycle_day=cycle_day,
                avg_energy_rating=avg_energy,
                symptom_frequency=symptom_frequency,
                avg_adherence=avg_adherence,
                avg_completed_volume_kg=avg_volume,
                sample_cycles=sample_cycles,
                confidence=confidence,
            )
        )
    return rows
