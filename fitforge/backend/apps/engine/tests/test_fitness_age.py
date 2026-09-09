"""Unit tests for apps/engine/fitness_age.py against hand-built fixtures.

No @pytest.mark.django_db: fitness_age.py is pure, plain data in and out.
"""

import math

import pytest
from hypothesis import given
from hypothesis import strategies as st

from apps.engine import fitness_age

SED = "sedentary"


class TestBmiFromHeightWeight:
    def test_known_value(self):
        # 80kg at 180cm -> 24.69...
        assert fitness_age.bmi_from_height_weight(180, 80) == pytest.approx(80 / 1.8**2)

    def test_missing_inputs_are_none(self):
        assert fitness_age.bmi_from_height_weight(None, 80) is None
        assert fitness_age.bmi_from_height_weight(180, None) is None
        assert fitness_age.bmi_from_height_weight(0, 80) is None


class TestEstimateCardiorespiratoryFitness:
    def test_hand_calculated_example(self):
        """Independently hand-verified against Table 5's NASA-cohort
        coefficients: 18.07 + 2.77(male) - 0.10(45) - 0.17(25.0) - 0.03(70)
        + 1.06(SR-PA-2) = 11.05 METs."""
        result = fitness_age.estimate_cardiorespiratory_fitness(
            age=45,
            sex_at_birth="male",
            bmi=25.0,
            resting_hr=70,
            activity_level="moderately_active",
        )
        assert result is not None
        assert result.mets == pytest.approx(11.05)
        assert result.vo2max_ml_kg_min == pytest.approx(11.05 * 3.5)
        assert result.citation == fitness_age.CITATION
        assert result.inputs["bmi"] == 25.0

    def test_papers_own_worked_example_does_not_reconcile_to_9_02(self):
        """Documents a real discrepancy rather than hiding it: the paper's
        worksheet (Figure 1) states its 45-year-old-male example (193 lb /
        68 in -> BMI 29.6, resting HR 72, PA level 2) comes out to 9.02
        METs. Recomputing from the same Table 5 coefficients used here
        gives ~9.51 METs — this implementation is not tuned to force a
        match to the paper's inconsistent printed figure."""
        bmi = fitness_age.bmi_from_height_weight(height_cm=172, weight_kg=87.7)
        result = fitness_age.estimate_cardiorespiratory_fitness(
            age=45,
            sex_at_birth="male",
            bmi=bmi,
            resting_hr=72,
            activity_level="lightly_active",  # SR-PA-1 -> paper's "Level 2"
        )
        assert result is not None
        assert result.mets == pytest.approx(9.4605, abs=1e-3)
        assert result.mets != pytest.approx(9.02, abs=0.1)

    def test_female_uses_zero_sex_term(self):
        male = fitness_age.estimate_cardiorespiratory_fitness(
            age=30, sex_at_birth="male", bmi=24.0, resting_hr=60, activity_level="sedentary"
        )
        female = fitness_age.estimate_cardiorespiratory_fitness(
            age=30, sex_at_birth="female", bmi=24.0, resting_hr=60, activity_level="sedentary"
        )
        assert male.mets - female.mets == pytest.approx(2.77)

    @pytest.mark.parametrize(
        "override",
        [
            {"age": None},
            {"bmi": None},
            {"resting_hr": None},
            {"activity_level": None},
            {"sex_at_birth": "intersex"},
            {"sex_at_birth": None},
        ],
    )
    def test_missing_or_unsupported_input_is_none_not_a_guess(self, override):
        kwargs = {
            "age": 30,
            "sex_at_birth": "male",
            "bmi": 24.0,
            "resting_hr": 60,
            "activity_level": SED,
        }
        kwargs.update(override)
        assert fitness_age.estimate_cardiorespiratory_fitness(**kwargs) is None


@given(
    age=st.integers(min_value=18, max_value=90),
    sex_at_birth=st.sampled_from(["male", "female"]),
    bmi=st.floats(min_value=12.0, max_value=60.0, allow_nan=False, allow_infinity=False),
    resting_hr=st.integers(min_value=30, max_value=120),
    activity_level=st.sampled_from(
        ["sedentary", "lightly_active", "moderately_active", "very_active", "extra_active"]
    ),
)
def test_result_is_always_finite_and_never_fabricated_for_valid_inputs(
    age, sex_at_birth, bmi, resting_hr, activity_level
):
    result = fitness_age.estimate_cardiorespiratory_fitness(
        age=age,
        sex_at_birth=sex_at_birth,
        bmi=bmi,
        resting_hr=resting_hr,
        activity_level=activity_level,
    )
    assert result is not None
    assert math.isfinite(result.mets)
    assert result.vo2max_ml_kg_min == pytest.approx(result.mets * 3.5)
    assert result.inputs["age"] == age
    assert result.inputs["sex_at_birth"] == sex_at_birth
    assert result.inputs["resting_hr"] == resting_hr
    assert result.inputs["activity_level"] == activity_level


class TestVo2maxToFitnessAge:
    def test_hand_calculated_interpolation(self):
        """38.675 mL/kg/min (male) sits between the 44.5 (37.8) and 34.5
        (42.4) decade midpoints in Table 3: age = 34.5 +
        ((42.4-38.675)/(42.4-37.8))*(44.5-34.5) = 42.60 (hand-verified)."""
        result = fitness_age.vo2max_to_fitness_age(38.675, "male")
        assert result == pytest.approx(42.5978, abs=1e-3)

    def test_exact_decade_midpoint_value_returns_that_midpoint(self):
        assert fitness_age.vo2max_to_fitness_age(37.8, "male") == pytest.approx(44.5)
        assert fitness_age.vo2max_to_fitness_age(26.7, "female") == pytest.approx(44.5)

    def test_fitter_than_youngest_decade_clamps_not_extrapolates(self):
        assert fitness_age.vo2max_to_fitness_age(70.0, "male") == 24.5
        assert fitness_age.vo2max_to_fitness_age(60.0, "female") == 24.5

    def test_less_fit_than_oldest_decade_clamps_not_extrapolates(self):
        assert fitness_age.vo2max_to_fitness_age(10.0, "male") == 74.5
        assert fitness_age.vo2max_to_fitness_age(10.0, "female") == 74.5

    def test_unsupported_sex_is_none(self):
        assert fitness_age.vo2max_to_fitness_age(40.0, "intersex") is None


class TestEstimateFitnessAge:
    def test_composes_both_published_sources(self):
        result = fitness_age.estimate_fitness_age(
            age=45,
            sex_at_birth="male",
            bmi=25.0,
            resting_hr=70,
            activity_level="moderately_active",
        )
        assert result is not None
        assert result.cardiorespiratory_fitness.mets == pytest.approx(11.05)
        assert result.fitness_age == pytest.approx(42.5978, abs=1e-3)
        assert result.norms_citation == fitness_age.NORMS_CITATION

    def test_missing_input_is_none_not_a_guess(self):
        result = fitness_age.estimate_fitness_age(
            age=None,
            sex_at_birth="male",
            bmi=25.0,
            resting_hr=70,
            activity_level="moderately_active",
        )
        assert result is None
