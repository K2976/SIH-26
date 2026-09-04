"""Data types for the adaptive engine layer.

Every type here is a frozen dataclass — pure data in, pure data out.  The
budgets and floors modules operate entirely on these; the ORM never appears.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class UserState:
    """Flat snapshot of all derived state the budget functions need.

    Built by the service layer from calculator outputs + profile/goal data.
    Optional fields are None when the diary doesn't have enough signal, never
    defaulted to zero (CLAUDE.md rule 7: missing is not zero).
    """

    # --- Energy ---
    maintenance_kcal: float | None = None
    maintenance_source: str | None = None  # "equation" | "blended"
    maintenance_confidence: float | None = None
    target_kcal_today: float | None = None
    consumed_kcal_today: float | None = None
    week_balance_kcal: float | None = None
    energy_direction: str | None = None  # "cut" | "maintain" | "gain" | "not_tracking"
    target_rate_kg_per_week: float | None = None

    # --- Weight trend (14-day window) ---
    weight_trend_kg_per_week: float | None = None
    weight_trend_confidence: float | None = None
    latest_weight_kg: float | None = None

    # --- Data sufficiency (for the blended-maintenance gate) ---
    insufficient_data_for_adaptation: bool = True
    missing_food_days_14d: int = 14
    missing_weight_days_14d: int = 14
    food_logged_days_14d: int = 0
    weight_logged_days_14d: int = 0

    # --- Training ---
    week_volume_planned_kg: float | None = None
    week_volume_completed_kg: float | None = None
    remaining_sessions: int = 0
    skipped_sessions: int = 0
    days_remaining_in_week: int = 0
    trailing_7d_volume_avg_kg: float | None = None

    # --- Readiness inputs ---
    days_since_rest: int | None = None
    sleep_quality: int | None = None  # 1..5
    self_reported_energy: int | None = None  # 1..5
    self_reported_symptoms: list[str] | None = None

    # --- Calorie trailing average (for max-adjustment floor) ---
    trailing_7d_kcal_avg: float | None = None

    # --- Profile (for sex-specific calorie floors) ---
    sex_at_birth: str | None = None  # "male" | "female" | "intersex"

    # --- Avg daily intake over the 14-day window (for measured maintenance) ---
    avg_daily_intake_14d: float | None = None


@dataclass(frozen=True)
class EnergyProposal:
    """Output of the energy budget: a daily kcal target with rationale."""

    target_kcal: float
    reason: str


@dataclass(frozen=True)
class VolumeProposal:
    """Output of the volume budget: today's training volume with rationale."""

    volume_today_kg: float
    dropped_sessions: int
    reason: str


@dataclass(frozen=True)
class ReadinessProposal:
    """Output of the readiness budget: a 0..1 multiplier with rationale."""

    ceiling: float
    reason: str


@dataclass(frozen=True)
class PlanDelta:
    """Final engine output after floors are applied.

    Every delta names *why* it fired (reason) and *what limited it*
    (clamped_by).  An empty clamped_by list means no floor intervened.
    """

    changes: dict[str, float | int | None] = field(default_factory=dict)
    reason: str = ""
    clamped_by: list[str] = field(default_factory=list)
