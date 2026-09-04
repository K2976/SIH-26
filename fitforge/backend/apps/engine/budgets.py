"""Three budget functions — the adaptive core of the engine.

Each budget is a pure function: UserState in, Proposal out, no side effects.
The service layer is responsible for building UserState from the ORM; nothing
here touches the database or the clock.

Budget 1: energy_budget   — weekly energy balance → target_kcal_today
Budget 2: volume_budget   — weekly training volume → volume_today_kg
Budget 3: readiness_budget — daily readiness ceiling → 0..1 multiplier
"""

from __future__ import annotations

from apps.engine.constants import (
    BLENDED_MAINTENANCE_MIN_FOOD_DAYS,
    BLENDED_MAINTENANCE_MIN_WEIGHT_POINTS,
    DAMPING_FACTOR,
    KCAL_PER_KG,
    MAX_SAFE_VOLUME_RECOVERY_DAYS,
    MAX_VOLUME_INCREASE_FACTOR,
    READINESS_DAYS_SINCE_REST_CRITICAL,
    READINESS_LOAD_WEIGHT,
    READINESS_MAX_SYMPTOM_COUNT,
    READINESS_REST_WEIGHT,
    READINESS_SLEEP_WEIGHT,
    READINESS_SYMPTOM_WEIGHT,
)
from apps.engine.types import EnergyProposal, ReadinessProposal, UserState, VolumeProposal


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def measured_maintenance(
    avg_daily_intake: float, weight_trend_kg_per_week: float
) -> float:
    """Solve maintenance backwards from logged intake and weight trend.

    If you eat `intake` kcal/day and your weight changes at `trend` kg/week,
    then your true TDEE (maintenance) is:

        maintenance = intake - (trend × KCAL_PER_KG / 7)

    A negative trend (losing weight) means maintenance > intake.
    A positive trend (gaining weight) means maintenance < intake.
    """
    daily_change_kcal = weight_trend_kg_per_week * float(KCAL_PER_KG) / 7.0
    return avg_daily_intake - daily_change_kcal


def blended_maintenance(
    equation: float, measured: float, confidence: float
) -> float:
    """Linearly blend equation-derived and measured maintenance.

    `confidence` is the weight on the measured value (0 = equation only,
    1 = measured only). In practice it comes from weight_trend_confidence,
    already capped to [0, 1] by the calculator.
    """
    c = max(0.0, min(1.0, confidence))
    return c * measured + (1.0 - c) * equation


def _sufficiency_gate_passes(state: UserState) -> bool:
    """Check whether the 14-day window has enough data for blended maintenance.

    Both food-logged days and weight measurements must meet their thresholds.
    Below the gate, equation-only maintenance is used.
    """
    return (
        state.weight_logged_days_14d >= BLENDED_MAINTENANCE_MIN_WEIGHT_POINTS
        and state.food_logged_days_14d >= BLENDED_MAINTENANCE_MIN_FOOD_DAYS
    )


# ---------------------------------------------------------------------------
# Budget 1: Weekly energy balance
# ---------------------------------------------------------------------------


def energy_budget(state: UserState) -> EnergyProposal:
    """Compute today's kcal target from goal, maintenance, and the week's
    running balance.

    When the sufficiency gate passes, maintenance is blended between the
    published equation and a measured value solved backwards from logged
    intake against weight trend. Below the gate, equation only.
    """
    # No maintenance at all → can't compute a target.
    if state.maintenance_kcal is None:
        return EnergyProposal(
            target_kcal=0.0,
            reason="no maintenance estimate available; cannot compute target",
        )

    equation_maint = state.maintenance_kcal
    reasons: list[str] = []

    # --- Determine effective maintenance ---
    if _sufficiency_gate_passes(state) and state.avg_daily_intake_14d is not None and state.weight_trend_kg_per_week is not None:
        meas = measured_maintenance(
            state.avg_daily_intake_14d, state.weight_trend_kg_per_week
        )
        confidence = state.weight_trend_confidence if state.weight_trend_confidence is not None else 0.0
        effective_maint = blended_maintenance(equation_maint, meas, confidence)
        reasons.append(
            f"blended maintenance ({confidence:.0%} measured, "
            f"{1 - confidence:.0%} equation): {effective_maint:.0f} kcal"
        )
    else:
        effective_maint = equation_maint
        reasons.append(
            f"equation-only maintenance: {effective_maint:.0f} kcal "
            "(insufficient data for blending)"
        )

    # --- Derive naïve target from goal ---
    if state.energy_direction in ("maintain", "not_tracking") or state.energy_direction is None:
        naive_target = effective_maint
        reasons.append("goal: maintain / not tracking")
    elif state.target_rate_kg_per_week is not None:
        daily_delta = state.target_rate_kg_per_week * float(KCAL_PER_KG) / 7.0
        if state.energy_direction == "cut":
            naive_target = effective_maint - daily_delta
            reasons.append(f"cut @ {state.target_rate_kg_per_week:.2f} kg/wk → −{daily_delta:.0f} kcal/day")
        elif state.energy_direction == "gain":
            naive_target = effective_maint + daily_delta
            reasons.append(f"gain @ {state.target_rate_kg_per_week:.2f} kg/wk → +{daily_delta:.0f} kcal/day")
        else:
            naive_target = effective_maint
    else:
        naive_target = effective_maint

    # --- Apply week-balance rebalancing with damping ---
    if (
        state.week_balance_kcal is not None
        and state.days_remaining_in_week is not None
        and state.days_remaining_in_week > 0
    ):
        # week_balance_kcal is consumed − maintenance summed over the week so
        # far. A positive balance means over-target; negative means under.
        raw_adjustment = -state.week_balance_kcal / state.days_remaining_in_week
        damped_adjustment = raw_adjustment * DAMPING_FACTOR
        target = naive_target + damped_adjustment
        reasons.append(
            f"week balance {state.week_balance_kcal:+.0f} kcal → "
            f"raw adj {raw_adjustment:+.0f}/day, damped ×{DAMPING_FACTOR} → "
            f"{damped_adjustment:+.0f}/day"
        )
    else:
        target = naive_target
        damped_adjustment = 0.0

    return EnergyProposal(target_kcal=target, reason="; ".join(reasons))


# ---------------------------------------------------------------------------
# Budget 2: Weekly training volume
# ---------------------------------------------------------------------------


def volume_budget(state: UserState) -> VolumeProposal:
    """Distribute remaining weekly volume across remaining days.

    A skipped session is a shortfall. If the shortfall cannot be recovered
    safely (i.e. spread across enough remaining days without exceeding the
    volume-increase cap), it is DROPPED, not crammed.
    """
    # No plan or no remaining days → nothing to propose.
    if (
        state.week_volume_planned_kg is None
        or state.days_remaining_in_week is None
        or state.days_remaining_in_week <= 0
    ):
        return VolumeProposal(
            volume_today_kg=0.0,
            dropped_sessions=0,
            reason="no planned volume or no remaining days",
        )

    completed = state.week_volume_completed_kg if state.week_volume_completed_kg is not None else 0.0
    remaining_volume = max(0.0, state.week_volume_planned_kg - completed)

    if remaining_volume <= 0.0:
        return VolumeProposal(
            volume_today_kg=0.0,
            dropped_sessions=0,
            reason="weekly volume target already met",
        )

    # Even distribution across remaining days.
    per_day = remaining_volume / state.days_remaining_in_week

    # --- Volume-increase safety cap ---
    trailing_avg = state.trailing_7d_volume_avg_kg
    if trailing_avg is not None and trailing_avg > 0.0:
        cap = trailing_avg * MAX_VOLUME_INCREASE_FACTOR
    else:
        # No trailing average → no cap (first week of training).
        cap = None

    dropped = 0

    if cap is not None and per_day > cap:
        # Can we spread it across enough days safely?
        if state.days_remaining_in_week >= MAX_SAFE_VOLUME_RECOVERY_DAYS:
            # Re-distribute at the cap; the excess becomes the "dropped" portion.
            safe_total = cap * state.days_remaining_in_week
            dropped_volume = remaining_volume - safe_total
            per_day = cap
            dropped = state.skipped_sessions if dropped_volume > 0 else 0
        else:
            # Not enough days to recover safely → drop the excess entirely.
            per_day = cap
            dropped = state.skipped_sessions

    return VolumeProposal(
        volume_today_kg=per_day,
        dropped_sessions=dropped,
        reason=(
            f"remaining {remaining_volume:.0f} kg over {state.days_remaining_in_week} days"
            + (f"; {dropped} session(s) dropped (unsafe to cram)" if dropped else "")
        ),
    )


# ---------------------------------------------------------------------------
# Budget 3: Daily readiness ceiling
# ---------------------------------------------------------------------------


def _readiness_sub_scores(state: UserState) -> tuple[dict[str, float], dict[str, float]]:
    """Compute each readiness sub-score and its effective weight.

    When optional signals (sleep, symptoms) are absent, their weight is
    redistributed equally across the present signals so the total always
    sums to 1.0.

    Returns (scores, weights) where both are dicts keyed by signal name.
    """
    scores: dict[str, float] = {}
    weights: dict[str, float] = {}
    absent_weight = 0.0

    # --- Load sub-score: how much of the trailing average today's work is ---
    # Higher recent volume → lower readiness. No trailing data → neutral.
    scores["load"] = 1.0  # default: no penalty
    weights["load"] = READINESS_LOAD_WEIGHT

    # --- Rest sub-score: days since last rest day ---
    if state.days_since_rest is not None:
        critical = READINESS_DAYS_SINCE_REST_CRITICAL
        rest_score = max(0.0, 1.0 - state.days_since_rest / critical)
        scores["rest"] = rest_score
        weights["rest"] = READINESS_REST_WEIGHT
    else:
        scores["rest"] = 1.0
        weights["rest"] = READINESS_REST_WEIGHT

    # --- Sleep sub-score: 1..5 → 0..1 ---
    if state.sleep_quality is not None:
        scores["sleep"] = max(0.0, min(1.0, (state.sleep_quality - 1) / 4.0))
        weights["sleep"] = READINESS_SLEEP_WEIGHT
    else:
        absent_weight += READINESS_SLEEP_WEIGHT

    # --- Symptom + energy sub-score ---
    if state.self_reported_energy is not None or state.self_reported_symptoms is not None:
        energy_score = 0.5  # neutral default
        if state.self_reported_energy is not None:
            energy_score = max(0.0, min(1.0, (state.self_reported_energy - 1) / 4.0))

        symptom_penalty = 0.0
        if state.self_reported_symptoms is not None:
            n = min(len(state.self_reported_symptoms), READINESS_MAX_SYMPTOM_COUNT)
            symptom_penalty = n / READINESS_MAX_SYMPTOM_COUNT

        # Blend: energy lifts, symptoms drag down.
        combined = max(0.0, energy_score - symptom_penalty * 0.5)
        scores["symptoms"] = combined
        weights["symptoms"] = READINESS_SYMPTOM_WEIGHT
    else:
        absent_weight += READINESS_SYMPTOM_WEIGHT

    # --- Redistribute absent weight ---
    if absent_weight > 0.0 and weights:
        present_keys = list(weights.keys())
        bonus = absent_weight / len(present_keys)
        for k in present_keys:
            weights[k] += bonus

    return scores, weights


def readiness_budget(state: UserState) -> ReadinessProposal:
    """Compute a 0..1 readiness multiplier from recent load, rest days,
    sleep quality, and self-reported symptoms/energy.

    Cycle phase is NOT an input. Do not add one.
    """
    scores, weights = _readiness_sub_scores(state)

    # Weighted sum.
    ceiling = sum(scores[k] * weights[k] for k in scores if k in weights)

    # Hard clamp to [0, 1].
    ceiling = max(0.0, min(1.0, ceiling))

    parts = [f"{k}={scores[k]:.2f}×{weights[k]:.2f}" for k in scores if k in weights]
    reason = "readiness: " + ", ".join(parts) + f" → {ceiling:.2f}"

    return ReadinessProposal(ceiling=ceiling, reason=reason)
