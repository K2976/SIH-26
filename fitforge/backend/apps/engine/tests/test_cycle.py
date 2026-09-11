"""Unit tests for the cycle module's pure functions (§10).

No @pytest.mark.django_db anywhere: everything here is a pure function,
same contract as apps/engine/tests/test_calculator.py.
"""

from __future__ import annotations

import dataclasses
from datetime import date

from apps.engine.constants import ENERGY_AVAILABILITY_FLAG
from apps.engine.cycle import (
    CycleDayRecord,
    compute_cycle_pattern,
    derive_phase,
    energy_availability_flag,
    forecast_next_period,
    infer_typical_length,
)
from apps.engine.types import UserState

NATURAL_HISTORY = [
    date(2026, 6, 1),
    date(2026, 6, 29),  # 28 days
    date(2026, 7, 27),  # 28 days
    date(2026, 8, 24),  # 28 days
]


class TestPhaseIsNotAnEngineInput:
    """Done-when criterion: phase provably does not move readiness."""

    def test_userstate_has_no_phase_field(self):
        field_names = {f.name for f in dataclasses.fields(UserState)}
        assert "phase" not in field_names
        assert "cycle_phase" not in field_names

    def test_userstate_has_no_cycle_phase_attribute(self):
        assert not hasattr(UserState, "phase")
        assert not hasattr(UserState, "cycle_phase")


class TestDerivePhase:
    def test_null_for_hormonal_contraception(self):
        result = derive_phase(NATURAL_HISTORY, date(2026, 9, 1), "hormonal_contraception")
        assert result.phase is None

    def test_null_for_every_non_natural_mode(self):
        for mode in ("pregnant", "postpartum", "perimenopause", "not_cycling", "unknown"):
            result = derive_phase(NATURAL_HISTORY, date(2026, 9, 1), mode)
            assert result.phase is None, mode

    def test_null_for_sparse_history(self):
        """Natural tracking, but only one period logged — too sparse."""
        result = derive_phase([date(2026, 8, 24)], date(2026, 9, 1), "natural")
        assert result.phase is None
        # cycle_day can still be reported even when phase can't be labeled.
        assert result.cycle_day == 9

    def test_null_for_no_history_at_all(self):
        result = derive_phase([], date(2026, 9, 1), "natural")
        assert result.phase is None
        assert result.cycle_day is None

    def test_phase_with_confident_history(self):
        # Last period started 2026-08-24, typical length ~28 days.
        result = derive_phase(NATURAL_HISTORY, date(2026, 8, 25), "natural")
        assert result.phase == "menstrual"
        assert result.cycle_day == 2
        assert result.confidence is not None and result.confidence > 0

    def test_null_when_significantly_overdue(self):
        # Cycle day is far past typical_length_days -> uncertain, not luteal.
        result = derive_phase(NATURAL_HISTORY, date(2026, 9, 30), "natural")
        assert result.phase is None


class TestInferTypicalLength:
    def test_needs_minimum_period_starts(self):
        result = infer_typical_length([date(2026, 6, 1), date(2026, 6, 29)])
        assert result.typical_length_days is None

    def test_confidence_scales_with_sample_size(self):
        few = infer_typical_length(NATURAL_HISTORY[:3])
        more = infer_typical_length(NATURAL_HISTORY)
        assert 0.0 < few.confidence <= more.confidence <= 1.0


class TestForecastNextPeriod:
    def test_gated_to_natural(self):
        result = forecast_next_period(NATURAL_HISTORY, date(2026, 9, 1), "hormonal_contraception")
        assert result.predicted_date is None

    def test_predicts_from_history(self):
        result = forecast_next_period(NATURAL_HISTORY, date(2026, 9, 1), "natural")
        assert result.predicted_date == date(2026, 9, 21)
        assert result.days_until == 20


class TestEnergyAvailabilityFlag:
    def test_requires_natural_tracking(self):
        assert energy_availability_flag("hormonal_contraception", 15, 20, True) is None

    def test_requires_period_overdue(self):
        assert energy_availability_flag("natural", 2, 20, True) is None
        assert energy_availability_flag("natural", None, 20, True) is None

    def test_requires_sustained_deficit(self):
        assert energy_availability_flag("natural", 15, 3, True) is None

    def test_requires_high_training_load(self):
        assert energy_availability_flag("natural", 15, 20, False) is None

    def test_fires_when_all_conditions_hold(self):
        assert energy_availability_flag("natural", 15, 20, True) == ENERGY_AVAILABILITY_FLAG

    def test_does_not_diagnose(self):
        """The flag is a key, not a diagnosis — no condition name, no score."""
        flag = energy_availability_flag("natural", 15, 20, True)
        assert isinstance(flag, str)
        for banned in ("amenorrhea", "RED-S", "disorder", "syndrome"):
            assert banned.lower() not in flag.lower()


class TestComputeCyclePattern:
    def test_reports_sample_size_and_confidence(self):
        records = [
            CycleDayRecord(
                cycle_index=1,
                cycle_day=3,
                energy_rating=2,
                symptoms=("cramps",),
                adherence=0.5,
                completed_volume_kg=100.0,
            ),
            CycleDayRecord(
                cycle_index=2,
                cycle_day=3,
                energy_rating=3,
                symptoms=("cramps",),
                adherence=1.0,
                completed_volume_kg=150.0,
            ),
            CycleDayRecord(
                cycle_index=3,
                cycle_day=3,
                energy_rating=None,
                symptoms=(),
                adherence=None,
                completed_volume_kg=None,
            ),
        ]
        rows = compute_cycle_pattern(records)
        assert len(rows) == 1
        row = rows[0]
        assert row.cycle_day == 3
        assert row.sample_cycles == 3
        assert row.symptom_frequency["cramps"] == 2 / 3
        assert row.avg_energy_rating == 2.5
        assert 0.0 < row.confidence <= 1.0

    def test_empty_input_reports_nothing(self):
        assert compute_cycle_pattern([]) == []
