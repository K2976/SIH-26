"""Adaptive engine orchestrator — the single entry point for the service layer.

compute_plan_delta(state) calls the three budgets, runs the floors module,
and returns a PlanDelta.  Pure function: UserState in, PlanDelta out.
"""

from __future__ import annotations

from apps.engine.budgets import energy_budget, readiness_budget, volume_budget
from apps.engine.floors import apply_floors
from apps.engine.types import PlanDelta, UserState


def compute_plan_delta(state: UserState) -> PlanDelta:
    """Run all three budgets and apply safety floors.

    This is the only function the service layer needs to call. It never
    raises — sparse or empty UserState produces a valid PlanDelta with an
    explanatory reason.
    """
    energy = energy_budget(state)
    volume = volume_budget(state)
    readiness = readiness_budget(state)
    return apply_floors(energy, volume, readiness, state)
