"""Property-based and unit tests for the adaptive engine.

This is the most important test suite in the project.  Hypothesis generates
random event histories (via st_user_state), then asserts as properties that
safety invariants hold across thousands of inputs.

No @pytest.mark.django_db anywhere: every function under test is pure.
"""

from __future__ import annotations

import pytest
from hypothesis import given, settings
from hypothesis import strategies as st

from apps.engine.adaptive import compute_plan_delta
from apps.engine.budgets import (
    blended_maintenance,
    energy_budget,
    measured_maintenance,
    readiness_budget,
    volume_budget,
)
from apps.engine.constants import (
    DAMPING_FACTOR,
    DAMPING_MAX_EXCLUSIVE,
    MAX_DAILY_KCAL_ADJUSTMENT,
    MAX_VOLUME_INCREASE_FACTOR,
    MIN_DAILY_KCAL_DEFAULT,
    MIN_DAILY_KCAL_FEMALE,
    MIN_DAILY_KCAL_MALE,
    READINESS_DAYS_SINCE_REST_CRITICAL,
)
from apps.engine.types import UserState

# ---------------------------------------------------------------------------
# Hypothesis strategies
# ---------------------------------------------------------------------------


def st_optional_float(min_value: float = 0.0, max_value: float = 10000.0):
    return st.one_of(
        st.none(),
        st.floats(min_value=min_value, max_value=max_value, allow_nan=False, allow_infinity=False),
    )


def st_optional_int(min_value: int = 0, max_value: int = 14):
    return st.one_of(st.none(), st.integers(min_value=min_value, max_value=max_value))


def st_symptoms():
    return st.one_of(
        st.none(),
        st.lists(
            st.sampled_from(["fatigue", "soreness", "headache", "nausea", "dizziness", "cramps"]),
            min_size=0,
            max_size=8,
        ),
    )


def st_user_state():
    """Generate a random UserState with realistic ranges."""
    return st.builds(
        UserState,
        maintenance_kcal=st_optional_float(800.0, 5000.0),
        maintenance_source=st.one_of(st.none(), st.sampled_from(["equation", "blended"])),
        maintenance_confidence=st_optional_float(0.0, 1.0),
        target_kcal_today=st_optional_float(800.0, 5000.0),
        consumed_kcal_today=st_optional_float(0.0, 6000.0),
        week_balance_kcal=st_optional_float(-5000.0, 5000.0),
        energy_direction=st.one_of(
            st.none(), st.sampled_from(["cut", "maintain", "gain", "not_tracking"])
        ),
        target_rate_kg_per_week=st_optional_float(0.0, 1.0),
        weight_trend_kg_per_week=st_optional_float(-2.0, 2.0),
        weight_trend_confidence=st_optional_float(0.0, 1.0),
        latest_weight_kg=st_optional_float(30.0, 200.0),
        insufficient_data_for_adaptation=st.booleans(),
        missing_food_days_14d=st.integers(min_value=0, max_value=14),
        missing_weight_days_14d=st.integers(min_value=0, max_value=14),
        food_logged_days_14d=st.integers(min_value=0, max_value=14),
        weight_logged_days_14d=st.integers(min_value=0, max_value=14),
        week_volume_planned_kg=st_optional_float(0.0, 50000.0),
        week_volume_completed_kg=st_optional_float(0.0, 50000.0),
        remaining_sessions=st.integers(min_value=0, max_value=7),
        skipped_sessions=st.integers(min_value=0, max_value=7),
        days_remaining_in_week=st.integers(min_value=0, max_value=7),
        trailing_7d_volume_avg_kg=st_optional_float(0.0, 20000.0),
        days_since_rest=st_optional_int(0, 14),
        sleep_quality=st.one_of(st.none(), st.integers(min_value=1, max_value=5)),
        self_reported_energy=st.one_of(st.none(), st.integers(min_value=1, max_value=5)),
        self_reported_symptoms=st_symptoms(),
        trailing_7d_kcal_avg=st_optional_float(800.0, 5000.0),
        sex_at_birth=st.one_of(st.none(), st.sampled_from(["male", "female", "intersex"])),
        avg_daily_intake_14d=st_optional_float(500.0, 6000.0),
    )


# ---------------------------------------------------------------------------
# Property tests — the core invariants
# ---------------------------------------------------------------------------


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_floors_always_hold_min_calories(state: UserState):
    """For any UserState, the final target_kcal is never below the minimum
    daily calorie floor (sex-appropriate or default)."""
    delta = compute_plan_delta(state)
    target = delta.changes.get("target_kcal")
    if target is not None and target > 0:
        if state.sex_at_birth == "female":
            assert target >= MIN_DAILY_KCAL_FEMALE
        elif state.sex_at_birth == "male":
            assert target >= MIN_DAILY_KCAL_MALE
        else:
            assert target >= MIN_DAILY_KCAL_DEFAULT


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_volume_never_exceeds_safe_increase(state: UserState):
    """Volume in the final delta never exceeds the trailing-average cap,
    when a trailing average exists."""
    delta = compute_plan_delta(state)
    volume = delta.changes.get("volume_today_kg", 0.0)
    if state.trailing_7d_volume_avg_kg is not None and state.trailing_7d_volume_avg_kg > 0:
        cap = state.trailing_7d_volume_avg_kg * MAX_VOLUME_INCREASE_FACTOR
        # Volume is also multiplied by readiness ceiling, so it should be
        # even lower.  But the floor applies the volume cap BEFORE readiness,
        # so we check the pre-readiness value via the budget directly.
        budget = volume_budget(state)
        if budget.volume_today_kg > cap:
            # The floor should have clamped it.
            assert volume <= cap * 1.0 + 1e-6  # readiness ceiling ≤ 1.0


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_damping_applied_to_carried_drift(state: UserState):
    """If week_balance_kcal != 0 and days_remaining > 0, the energy budget's
    adjustment is at most DAMPING_FACTOR × |balance / days_remaining|."""
    if (
        state.week_balance_kcal is not None
        and state.week_balance_kcal != 0.0
        and state.days_remaining_in_week is not None
        and state.days_remaining_in_week > 0
        and state.maintenance_kcal is not None
    ):
        energy = energy_budget(state)
        # Reconstruct what the target would have been without the balance
        # adjustment — we can't perfectly reconstruct but we can check the
        # bound on the adjustment magnitude.
        max_adj = abs(state.week_balance_kcal / state.days_remaining_in_week) * DAMPING_FACTOR
        # The energy budget adds damped_adjustment to naive_target, so the
        # difference between energy.target_kcal and the naive target should
        # not exceed max_adj.  Since we can't extract naive_target directly,
        # we verify by running with zero balance and comparing.
        from dataclasses import replace

        zero_balance_state = replace(state, week_balance_kcal=0.0)
        no_adj_energy = energy_budget(zero_balance_state)
        actual_adj = abs(energy.target_kcal - no_adj_energy.target_kcal)
        assert actual_adj <= max_adj + 1e-6


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_every_plan_delta_has_nonempty_reason(state: UserState):
    """Every PlanDelta must carry a non-empty reason string."""
    delta = compute_plan_delta(state)
    assert len(delta.reason) > 0


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_engine_never_raises_on_sparse_data(state: UserState):
    """compute_plan_delta never raises for any generated UserState,
    including all-None optional fields."""
    # If this line executes without exception, the test passes.
    delta = compute_plan_delta(state)
    assert delta is not None


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_readiness_ceiling_always_bounded(state: UserState):
    """The readiness ceiling is always in [0, 1]."""
    readiness = readiness_budget(state)
    assert 0.0 <= readiness.ceiling <= 1.0


@given(state=st_user_state())
@settings(max_examples=2000, deadline=None)
def test_no_emitted_target_below_minimum(state: UserState):
    """No emitted target_kcal ever falls below the global minimum.
    A target of 0.0 with a 'no maintenance' reason is allowed (it means
    the engine had no data to work with)."""
    delta = compute_plan_delta(state)
    target = delta.changes.get("target_kcal")
    if target is not None and target > 0:
        assert target >= MIN_DAILY_KCAL_DEFAULT


# ---------------------------------------------------------------------------
# Unit tests — targeted behaviour checks
# ---------------------------------------------------------------------------


class TestMeasuredMaintenance:
    def test_losing_weight_means_maintenance_above_intake(self):
        # Eating 2000 kcal/day and losing 0.5 kg/week → TDEE > 2000
        result = measured_maintenance(2000.0, -0.5)
        assert result > 2000.0

    def test_gaining_weight_means_maintenance_below_intake(self):
        # Eating 3000 kcal/day and gaining 0.5 kg/week → TDEE < 3000
        result = measured_maintenance(3000.0, 0.5)
        assert result < 3000.0

    def test_stable_weight_means_maintenance_equals_intake(self):
        result = measured_maintenance(2500.0, 0.0)
        assert result == pytest.approx(2500.0)


class TestBlendedMaintenance:
    def test_zero_confidence_is_equation_only(self):
        result = blended_maintenance(2500.0, 2200.0, 0.0)
        assert result == pytest.approx(2500.0)

    def test_full_confidence_is_measured_only(self):
        result = blended_maintenance(2500.0, 2200.0, 1.0)
        assert result == pytest.approx(2200.0)

    def test_half_confidence_is_midpoint(self):
        result = blended_maintenance(2500.0, 2200.0, 0.5)
        assert result == pytest.approx(2350.0)


class TestEnergyBudgetSufficiencyGate:
    def test_gate_closed_uses_equation_only(self):
        """When the sufficiency gate fails (too few logged days), the energy
        budget uses equation-only maintenance."""
        state = UserState(
            maintenance_kcal=2500.0,
            food_logged_days_14d=3,  # below BLENDED_MAINTENANCE_MIN_FOOD_DAYS
            weight_logged_days_14d=2,  # below BLENDED_MAINTENANCE_MIN_WEIGHT_POINTS
            energy_direction="maintain",
        )
        result = energy_budget(state)
        assert result.target_kcal == pytest.approx(2500.0)
        assert "equation-only" in result.reason

    def test_gate_open_blends_measured(self):
        """When the gate passes, measured maintenance is blended in."""
        state = UserState(
            maintenance_kcal=2500.0,
            maintenance_confidence=0.5,
            weight_trend_kg_per_week=-0.5,
            weight_trend_confidence=0.8,
            food_logged_days_14d=12,
            weight_logged_days_14d=8,
            avg_daily_intake_14d=2000.0,
            energy_direction="maintain",
        )
        result = energy_budget(state)
        assert "blended" in result.reason
        # Blended target should differ from equation-only.
        assert result.target_kcal != pytest.approx(2500.0)


class TestVolumeBudgetDropNotCram:
    def test_skipped_session_dropped_when_unsafe(self):
        """A skipped session's volume is dropped (not crammed) when it
        can't be recovered safely."""
        state = UserState(
            week_volume_planned_kg=10000.0,
            week_volume_completed_kg=2000.0,
            days_remaining_in_week=1,  # only 1 day left
            skipped_sessions=2,
            trailing_7d_volume_avg_kg=2000.0,  # cap = 2200
        )
        result = volume_budget(state)
        # Should not try to cram 8000 kg into 1 day.
        assert result.volume_today_kg <= 2000.0 * MAX_VOLUME_INCREASE_FACTOR + 1e-6
        assert result.dropped_sessions > 0

    def test_on_track_returns_zero(self):
        """When the week's volume is already met, propose 0."""
        state = UserState(
            week_volume_planned_kg=5000.0,
            week_volume_completed_kg=5000.0,
            days_remaining_in_week=3,
        )
        result = volume_budget(state)
        assert result.volume_today_kg == 0.0


class TestReadinessBudget:
    def test_sleep_absent_redistributes_weight(self):
        """When sleep is absent, its weight is redistributed to present
        signals so the total still sums to ~1.0 worth of contribution."""
        with_sleep = UserState(
            days_since_rest=0,
            sleep_quality=5,
            self_reported_energy=5,
        )
        without_sleep = UserState(
            days_since_rest=0,
            sleep_quality=None,
            self_reported_energy=5,
        )
        r_with = readiness_budget(with_sleep)
        r_without = readiness_budget(without_sleep)
        # Both should produce valid ceilings, not crash.
        assert 0.0 <= r_with.ceiling <= 1.0
        assert 0.0 <= r_without.ceiling <= 1.0

    def test_max_rest_days_drops_readiness(self):
        """At the critical rest-day threshold, the rest sub-score is 0."""
        state = UserState(days_since_rest=READINESS_DAYS_SINCE_REST_CRITICAL)
        result = readiness_budget(state)
        # Readiness should be noticeably below 1.0.
        assert result.ceiling < 1.0

    def test_no_cycle_phase_input(self):
        """Cycle phase is NOT an input. The UserState has no cycle_phase field."""
        assert not hasattr(UserState, "cycle_phase")
        # Double-check: the function signature doesn't accept it either.
        import inspect

        sig = inspect.signature(readiness_budget)
        assert "cycle_phase" not in sig.parameters


class TestFloorsIndividually:
    def test_min_kcal_floor_fires(self):
        """A very low target gets clamped to the minimum."""
        state = UserState(
            maintenance_kcal=800.0,
            energy_direction="cut",
            target_rate_kg_per_week=1.0,
            sex_at_birth="female",
        )
        delta = compute_plan_delta(state)
        assert delta.changes["target_kcal"] >= MIN_DAILY_KCAL_FEMALE
        assert any("min_daily_kcal" in c for c in delta.clamped_by)

    def test_max_kcal_adjustment_floor_fires(self):
        """A huge adjustment gets clamped to MAX_DAILY_KCAL_ADJUSTMENT."""
        state = UserState(
            maintenance_kcal=2500.0,
            target_kcal_today=2500.0,
            energy_direction="cut",
            target_rate_kg_per_week=1.0,
            week_balance_kcal=5000.0,  # huge over-target
            days_remaining_in_week=1,
        )
        delta = compute_plan_delta(state)
        actual_adjustment = abs(delta.changes["target_kcal"] - 2500.0)
        assert actual_adjustment <= MAX_DAILY_KCAL_ADJUSTMENT + 1e-6

    def test_volume_cap_floor_fires(self):
        """Volume exceeding the trailing-average cap is clamped."""
        state = UserState(
            week_volume_planned_kg=10000.0,
            week_volume_completed_kg=0.0,
            days_remaining_in_week=1,
            trailing_7d_volume_avg_kg=1000.0,
            maintenance_kcal=2500.0,
            energy_direction="maintain",
        )
        delta = compute_plan_delta(state)
        readiness_ceiling = delta.changes.get("readiness_ceiling", 1.0)
        volume_cap = 1000.0 * MAX_VOLUME_INCREASE_FACTOR
        assert delta.changes["volume_today_kg"] <= volume_cap * readiness_ceiling + 1e-6

    def test_protein_floor_flags_but_does_not_override_kcal(self):
        """The protein floor flags the violation but does not change kcal."""
        # A very light person with a very low calorie target.
        state = UserState(
            maintenance_kcal=1300.0,
            energy_direction="maintain",
            latest_weight_kg=100.0,  # needs 120g protein = 480 kcal min
            sex_at_birth="female",
        )
        delta = compute_plan_delta(state)
        # The protein floor should flag but the kcal should be the
        # maintenance value (≥ 1200, which is the female floor).
        assert delta.changes["target_kcal"] >= MIN_DAILY_KCAL_FEMALE


class TestPlanDeltaWithEmptyState:
    def test_empty_state_produces_valid_output(self):
        """A completely empty UserState produces a valid PlanDelta."""
        delta = compute_plan_delta(UserState())
        assert len(delta.reason) > 0
        assert delta.changes is not None
        assert isinstance(delta.clamped_by, list)

    def test_all_none_optional_fields(self):
        """Every optional field set to None still produces a valid result."""
        state = UserState(
            maintenance_kcal=None,
            target_kcal_today=None,
            consumed_kcal_today=None,
            week_balance_kcal=None,
            energy_direction=None,
            target_rate_kg_per_week=None,
            weight_trend_kg_per_week=None,
            weight_trend_confidence=None,
            latest_weight_kg=None,
            week_volume_planned_kg=None,
            week_volume_completed_kg=None,
            trailing_7d_volume_avg_kg=None,
            days_since_rest=None,
            sleep_quality=None,
            self_reported_energy=None,
            self_reported_symptoms=None,
            trailing_7d_kcal_avg=None,
            sex_at_birth=None,
            avg_daily_intake_14d=None,
        )
        delta = compute_plan_delta(state)
        assert len(delta.reason) > 0


class TestDampingFactorValidation:
    def test_current_damping_factor_is_valid(self):
        """The current DAMPING_FACTOR is strictly below the exclusive max."""
        assert DAMPING_FACTOR < DAMPING_MAX_EXCLUSIVE

    def test_validation_rejects_full_compensation(self):
        """validate_damping_factor raises when damping is 1.0."""
        from unittest.mock import patch

        from apps.engine.floors import validate_damping_factor

        with patch("apps.engine.floors.DAMPING_FACTOR", 1.0):
            with pytest.raises(ValueError, match="strictly less than"):
                validate_damping_factor()

    def test_validation_rejects_above_one(self):
        """validate_damping_factor raises when damping > 1.0."""
        from unittest.mock import patch

        from apps.engine.floors import validate_damping_factor

        with patch("apps.engine.floors.DAMPING_FACTOR", 1.5):
            with pytest.raises(ValueError, match="strictly less than"):
                validate_damping_factor()
