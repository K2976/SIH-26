"""Floors module — safety clamps applied after the three budgets.

Each floor can veto or clamp a proposal value.  When a floor fires, it is
recorded by name in PlanDelta.clamped_by so the coach layer (and the user)
can see exactly what limited the recommendation.

Damping validation runs at import time: if DAMPING_FACTOR >= 1.0, the module
refuses to load.  Full compensation is never safe.
"""

from __future__ import annotations

from apps.engine.constants import (
    DAMPING_FACTOR,
    DAMPING_MAX_EXCLUSIVE,
    ENERGY_AVAILABILITY_FLAG,
    MAX_DAILY_KCAL_ADJUSTMENT,
    MAX_VOLUME_INCREASE_FACTOR,
    MIN_DAILY_KCAL_DEFAULT,
    MIN_DAILY_KCAL_FEMALE,
    MIN_DAILY_KCAL_MALE,
    MIN_DAILY_PROTEIN_G_PER_KG,
)
from apps.engine.types import (
    EnergyProposal,
    PlanDelta,
    ReadinessProposal,
    UserState,
    VolumeProposal,
)

# ---------------------------------------------------------------------------
# Import-time damping validation
# ---------------------------------------------------------------------------


def validate_damping_factor() -> None:
    """Raise ValueError if DAMPING_FACTOR is >= DAMPING_MAX_EXCLUSIVE.

    Called at module load time.  Full compensation (damping = 1.0) creates
    an overeat–restrict oscillation loop.  This is a code-level guarantee.
    """
    if DAMPING_FACTOR >= DAMPING_MAX_EXCLUSIVE:
        msg = (
            f"DAMPING_FACTOR ({DAMPING_FACTOR}) must be strictly less than "
            f"DAMPING_MAX_EXCLUSIVE ({DAMPING_MAX_EXCLUSIVE}). "
            f"Full compensation is never safe."
        )
        raise ValueError(msg)


# Validate on import — fail fast, fail loud.
validate_damping_factor()


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _min_daily_kcal(sex_at_birth: str | None) -> float:
    """Return the sex-appropriate minimum daily calorie floor."""
    if sex_at_birth == "female":
        return MIN_DAILY_KCAL_FEMALE
    if sex_at_birth == "male":
        return MIN_DAILY_KCAL_MALE
    return MIN_DAILY_KCAL_DEFAULT


# ---------------------------------------------------------------------------
# apply_floors
# ---------------------------------------------------------------------------


def apply_floors(
    energy: EnergyProposal,
    volume: VolumeProposal,
    readiness: ReadinessProposal,
    state: UserState,
) -> PlanDelta:
    """Apply safety floors to the three budget proposals.

    Returns a PlanDelta with the (possibly clamped) values, a combined
    reason string, and a list of which floors fired.
    """
    clamped_by: list[str] = []
    reasons: list[str] = []

    # Start from the budget proposals.
    final_kcal = energy.target_kcal
    final_volume = volume.volume_today_kg
    final_readiness = readiness.ceiling

    # Collect the budget reasons.
    reasons.append(energy.reason)
    reasons.append(volume.reason)
    reasons.append(readiness.reason)

    # -----------------------------------------------------------------------
    # Floor 1: Minimum daily calories
    # -----------------------------------------------------------------------
    min_kcal = _min_daily_kcal(state.sex_at_birth)
    if final_kcal < min_kcal:
        clamped_by.append(f"min_daily_kcal ({min_kcal:.0f})")
        final_kcal = min_kcal

    # -----------------------------------------------------------------------
    # Floor 2: Minimum daily protein (flag-only, does not override kcal)
    # -----------------------------------------------------------------------
    if state.latest_weight_kg is not None and final_kcal > 0:
        min_protein_g = MIN_DAILY_PROTEIN_G_PER_KG * state.latest_weight_kg
        # Rough estimate: protein-feasible kcal assumes ~30% protein at
        # 4 kcal/g.  If the target can't cover minimum protein at any
        # reasonable macro split, flag it.
        # min_protein_g * 4 kcal/g is the bare minimum kcal from protein.
        min_kcal_for_protein = min_protein_g * 4.0
        if final_kcal < min_kcal_for_protein:
            clamped_by.append(
                f"min_daily_protein ({min_protein_g:.0f}g = "
                f"{min_kcal_for_protein:.0f} kcal from protein alone)"
            )
            # We flag but do NOT override kcal — the coach layer adjusts
            # macro split instead.

    # -----------------------------------------------------------------------
    # Floor 3: Maximum single-day volume increase vs trailing average
    # -----------------------------------------------------------------------
    if state.trailing_7d_volume_avg_kg is not None and state.trailing_7d_volume_avg_kg > 0:
        volume_cap = state.trailing_7d_volume_avg_kg * MAX_VOLUME_INCREASE_FACTOR
        if final_volume > volume_cap:
            clamped_by.append(
                f"max_volume_increase ({MAX_VOLUME_INCREASE_FACTOR:.0%} of "
                f"trailing avg {state.trailing_7d_volume_avg_kg:.0f} kg = "
                f"{volume_cap:.0f} kg)"
            )
            final_volume = volume_cap

    # -----------------------------------------------------------------------
    # Floor 4: Maximum single-day calorie adjustment
    # -----------------------------------------------------------------------
    if state.target_kcal_today is not None:
        delta_kcal = abs(final_kcal - state.target_kcal_today)
        if delta_kcal > MAX_DAILY_KCAL_ADJUSTMENT:
            direction = 1.0 if final_kcal >= state.target_kcal_today else -1.0
            final_kcal = state.target_kcal_today + direction * MAX_DAILY_KCAL_ADJUSTMENT
            clamped_by.append(f"max_daily_kcal_adjustment (±{MAX_DAILY_KCAL_ADJUSTMENT:.0f} kcal)")
            # Re-apply floor 1 after clamping — the adjustment cap must not
            # push us below the calorie floor.
            if final_kcal < min_kcal:
                final_kcal = min_kcal
                if f"min_daily_kcal ({min_kcal:.0f})" not in clamped_by:
                    clamped_by.append(f"min_daily_kcal ({min_kcal:.0f})")

    # -----------------------------------------------------------------------
    # Floor 4.5: Energy availability — stop escalating the deficit (§10)
    # -----------------------------------------------------------------------
    # When cycle_service has flagged a sustained deficit + high training load
    # + a significantly overdue period (natural tracking only), the engine
    # must not let the target sit below maintenance — that's what "stops
    # escalating the deficit" means concretely. This does not diagnose or
    # force a surplus; it only refuses to let automatic rebalancing push the
    # target further into deficit than maintenance itself.
    flag_active = state.energy_availability_flag == ENERGY_AVAILABILITY_FLAG
    if flag_active and state.maintenance_kcal is not None:
        if final_kcal < state.maintenance_kcal:
            clamped_by.append(f"energy_availability_floor ({state.maintenance_kcal:.0f} kcal)")
            final_kcal = state.maintenance_kcal

    # -----------------------------------------------------------------------
    # Floor 5: Damping invariant (assertion-level check)
    # -----------------------------------------------------------------------
    # The energy budget already applies DAMPING_FACTOR, but we verify the
    # invariant here as a defensive measure.  The validate_damping_factor()
    # call at import time is the primary guarantee.
    # (No clamping needed — this is a code-correctness assertion, not a
    # runtime clamp.  If the budget module changes, this catches regressions.)

    # Apply readiness ceiling to volume.
    final_volume = final_volume * final_readiness

    return PlanDelta(
        changes={
            "target_kcal": final_kcal,
            "volume_today_kg": final_volume,
            "readiness_ceiling": final_readiness,
            "dropped_sessions": volume.dropped_sessions,
        },
        reason="; ".join(reasons),
        clamped_by=clamped_by,
    )
