"""Unit tests for apps/engine/calculator.py against hand-built fixtures.

No @pytest.mark.django_db anywhere in this file: the calculator takes plain
dataclasses in and plain values out, never touching the ORM, so fixtures are
just Python objects — that's the point of keeping it pure.
"""

from datetime import date, timedelta

import pytest
from hypothesis import given
from hypothesis import strategies as st

from apps.engine import calculator as calc

AS_OF = date(2026, 3, 4)  # a Wednesday


class TestAdherence7d:
    def test_no_sessions_in_window_is_null(self):
        assert calc.adherence_7d([], AS_OF) == calc.Derived(None)

    def test_ratio_of_completed_to_resolved(self):
        sessions = [
            calc.PlannedSessionRecord(date(2026, 3, 1), "completed", None),
            calc.PlannedSessionRecord(date(2026, 3, 2), "skipped", None),
            calc.PlannedSessionRecord(date(2026, 3, 3), "completed", None),
        ]
        result = calc.adherence_7d(sessions, AS_OF)
        assert result.value == 2 / 3
        assert result.partial is False

    def test_overdue_pending_session_is_excluded_and_marks_partial(self):
        sessions = [
            calc.PlannedSessionRecord(date(2026, 3, 1), "completed", None),
            calc.PlannedSessionRecord(date(2026, 3, 2), "pending", None),  # unresolved gap
        ]
        result = calc.adherence_7d(sessions, AS_OF)
        assert result.value == 1.0  # denominator excludes the pending row, not counted as a miss
        assert result.partial is True

    def test_only_pending_sessions_is_null_not_zero(self):
        sessions = [calc.PlannedSessionRecord(date(2026, 3, 1), "pending", None)]
        assert calc.adherence_7d(sessions, AS_OF) == calc.Derived(None)

    def test_outside_window_is_ignored(self):
        sessions = [calc.PlannedSessionRecord(date(2026, 2, 1), "completed", None)]
        assert calc.adherence_7d(sessions, AS_OF) == calc.Derived(None)


class TestWeekVolume:
    def test_no_planned_sessions_this_week_is_null(self):
        assert calc.week_volume_planned([], AS_OF) == calc.Derived(None)

    def test_planned_sums_known_shapes_and_flags_malformed(self):
        sessions = [
            calc.PlannedSessionRecord(date(2026, 3, 2), "pending", 500.0),
            calc.PlannedSessionRecord(date(2026, 3, 4), "pending", None),  # malformed/missing shape
        ]
        result = calc.week_volume_planned(sessions, AS_OF)
        assert result.value == 500.0
        assert result.partial is True

    def test_no_sets_logged_this_week_is_null_not_zero(self):
        assert calc.week_volume_completed([], AS_OF) == calc.Derived(None)

    def test_completed_sums_weight_times_reps_excluding_warmups(self):
        sets = [
            calc.SetRecord(date(2026, 3, 3), 100.0, 5, False, False, ("back",)),
            calc.SetRecord(date(2026, 3, 3), 40.0, 10, True, False, ("back",)),  # warmup, excluded
        ]
        result = calc.week_volume_completed(sets, AS_OF)
        assert result.value == 500.0
        assert result.partial is False

    def test_bodyweight_exercise_missing_weight_is_zero_not_a_gap(self):
        sets = [calc.SetRecord(date(2026, 3, 3), None, 10, False, True, ("core",))]
        result = calc.week_volume_completed(sets, AS_OF)
        assert result.value == 0.0
        assert result.partial is False

    def test_non_bodyweight_missing_weight_is_a_gap(self):
        sets = [
            calc.SetRecord(date(2026, 3, 3), 100.0, 5, False, False, ("legs",)),
            calc.SetRecord(date(2026, 3, 3), None, 8, False, False, ("legs",)),
        ]
        result = calc.week_volume_completed(sets, AS_OF)
        assert result.value == 500.0
        assert result.partial is True


class TestVolumeByMuscleGroup:
    def test_no_sets_is_null(self):
        assert calc.volume_by_muscle_group([], AS_OF) == calc.Derived(None)

    def test_full_set_credited_to_each_primary_muscle(self):
        sets = [calc.SetRecord(date(2026, 3, 3), 50.0, 10, False, False, ("chest", "triceps"))]
        result = calc.volume_by_muscle_group(sets, AS_OF)
        assert result.value == {"chest": 500.0, "triceps": 500.0}
        assert result.partial is False

    def test_untouched_muscles_are_absent_not_zero(self):
        sets = [calc.SetRecord(date(2026, 3, 3), 50.0, 10, False, False, ("back",))]
        result = calc.volume_by_muscle_group(sets, AS_OF)
        assert "legs" not in result.value


class TestDaysSinceRest:
    def test_no_diary_data_ever_is_null(self):
        assert calc.days_since_rest(frozenset(), AS_OF, None) == calc.Derived(None)

    def test_never_trained_but_has_other_diary_data_is_zero(self):
        result = calc.days_since_rest(frozenset(), AS_OF, date(2026, 2, 1))
        assert result == calc.Derived(0)

    def test_today_is_a_rest_day(self):
        training_days = frozenset({date(2026, 3, 3)})
        result = calc.days_since_rest(training_days, AS_OF, date(2026, 2, 1))
        assert result == calc.Derived(0)

    def test_consecutive_streak_ending_today(self):
        training_days = frozenset({date(2026, 3, 2), date(2026, 3, 3), AS_OF})
        result = calc.days_since_rest(training_days, AS_OF, date(2026, 1, 1))
        assert result.value == 3
        assert result.partial is False

    def test_streak_running_off_known_history_is_partial(self):
        earliest = date(2026, 3, 2)
        training_days = frozenset({date(2026, 3, 2), date(2026, 3, 3), AS_OF})
        result = calc.days_since_rest(training_days, AS_OF, earliest)
        assert result.value == 3
        assert result.partial is True  # the streak may predate our earliest record


class TestConsumedKcalToday:
    def test_no_meal_logs_is_null_not_zero(self):
        assert calc.consumed_kcal_today([]) == calc.Derived(None)

    def test_sums_items(self):
        assert calc.consumed_kcal_today([200.0, 350.5]) == calc.Derived(550.5)


class TestBurnedKcalToday:
    def test_no_sessions_is_null_not_zero(self):
        assert calc.burned_kcal_today([]) == calc.Derived(None)

    def test_computes_from_met_bodyweight_duration(self):
        session = calc.SessionBurnInput(mean_met=6.0, duration_hours=1.0, bodyweight_kg=80.0)
        assert calc.burned_kcal_today([session]) == calc.Derived(480.0)

    def test_unfinished_session_excluded_and_marks_partial(self):
        known = calc.SessionBurnInput(6.0, 1.0, 80.0)
        unfinished = calc.SessionBurnInput(6.0, None, 80.0)  # no duration yet
        result = calc.burned_kcal_today([known, unfinished])
        assert result.value == 480.0
        assert result.partial is True

    def test_all_sessions_uncomputable_is_null(self):
        unfinished = calc.SessionBurnInput(6.0, None, 80.0)
        assert calc.burned_kcal_today([unfinished]) == calc.Derived(None)


class TestWeekBalanceKcal:
    def test_no_days_known_is_null(self):
        assert calc.week_balance_kcal([(None, None)] * 7) == calc.Derived(None)

    def test_sums_known_days_and_flags_gaps(self):
        pairs = [(2000.0, 2200.0), (None, 2200.0), (1800.0, 2200.0)]
        result = calc.week_balance_kcal(pairs)
        assert result.value == (2000 - 2200) + (1800 - 2200)
        assert result.partial is True

    def test_full_week_known_is_not_partial(self):
        pairs = [(2000.0, 2200.0)] * 7
        result = calc.week_balance_kcal(pairs)
        assert result.partial is False


class TestWeightTrend:
    def test_fewer_than_two_points_is_null(self):
        assert calc.weight_trend([]) == calc.WeightTrend(None, None)
        assert calc.weight_trend([(0, 80.0)]) == calc.WeightTrend(None, None)

    def test_zero_x_variance_does_not_crash(self):
        # Two weigh-ins that somehow bucketed to the same day offset.
        assert calc.weight_trend([(0, 80.0), (0, 81.0)]) == calc.WeightTrend(None, None)

    def test_flat_weight_is_zero_slope_not_a_crash(self):
        points = [(i, 80.0) for i in range(6)]
        result = calc.weight_trend(points)
        assert result.kg_per_week == 0.0
        assert result.confidence == 1.0

    def test_more_points_raise_confidence_for_the_same_fit(self):
        few = calc.weight_trend([(0, 80.0), (1, 79.9), (2, 79.8)])
        many = calc.weight_trend([(i, 80.0 - 0.1 * i) for i in range(8)])
        assert many.confidence > few.confidence

    def test_regression_is_not_last_minus_first(self):
        """A steady decline is real; use is_last_minus_first as a stand-in for
        what a naive (last - first) trend would report, and confirm the
        regression's answer differs and is closer to the true slope."""
        points = [(i, 80.0 - 0.1 * i) for i in range(14)]  # true slope: -0.1 kg/day
        true_weekly_slope = -0.7

        result = calc.weight_trend(points)
        assert result.kg_per_week == pytest.approx(true_weekly_slope)

    def test_synthetic_water_weight_spike_does_not_move_the_trend(self):
        """CLAUDE.md §6: 'A 2 kg overnight water swing must not move it.'
        14 days of a steady -0.1kg/day decline, with one +2kg spike near the
        end — exactly where a naive last-minus-first read would be thrown
        off badly. The regression, outnumbered 13-to-1, should barely move.
        """
        clean = [(i, 80.0 - 0.1 * i) for i in range(14)]
        clean_trend = calc.weight_trend(clean)

        spiked = list(clean)
        spike_day, (x, y) = 12, clean[12]
        spiked[spike_day] = (x, y + 2.0)
        spiked_trend = calc.weight_trend(spiked)

        naive_last_minus_first_shift = 2.0 / 13 * 7  # what a naive read would move by
        actual_shift = abs(spiked_trend.kg_per_week - clean_trend.kg_per_week)

        assert actual_shift < naive_last_minus_first_shift / 3


class TestMaintenanceKcal:
    def test_missing_any_required_input_is_null(self):
        result = calc.maintenance_kcal(None, "male", 180.0, 80.0, "sedentary")
        assert result == calc.MaintenanceResult(None, None, None)

    def test_intersex_has_no_third_branch(self):
        result = calc.maintenance_kcal(30, "intersex", 180.0, 80.0, "sedentary")
        assert result == calc.MaintenanceResult(None, None, None)

    def test_computes_mifflin_st_jeor_for_male(self):
        result = calc.maintenance_kcal(30, "male", 180.0, 80.0, "sedentary")
        bmr = 10 * 80.0 + 6.25 * 180.0 - 5 * 30 + 5
        assert result.kcal == pytest.approx(bmr * 1.2)
        assert result.source == "equation"
        assert result.confidence == calc.EQUATION_MAINTENANCE_CONFIDENCE

    def test_computes_mifflin_st_jeor_for_female(self):
        result = calc.maintenance_kcal(30, "female", 165.0, 60.0, "sedentary")
        bmr = 10 * 60.0 + 6.25 * 165.0 - 5 * 30 - 161
        assert result.kcal == pytest.approx(bmr * 1.2)


class TestTargetKcalToday:
    def test_no_maintenance_is_null(self):
        assert calc.target_kcal_today(None, "cut", 0.5) is None

    def test_maintain_returns_maintenance_unchanged(self):
        assert calc.target_kcal_today(2500.0, "maintain", None) == 2500.0

    def test_cut_subtracts_deficit(self):
        result = calc.target_kcal_today(2500.0, "cut", 0.5)
        assert result == 2500.0 - (0.5 * 7700 / 7)

    def test_gain_adds_surplus(self):
        result = calc.target_kcal_today(2500.0, "gain", 0.25)
        assert result == 2500.0 + (0.25 * 7700 / 7)


class TestSufficiencyFlags:
    def test_missing_food_days_counts_gaps_in_window(self):
        logged = frozenset({date(2026, 3, 2), date(2026, 3, 4)})
        assert calc.missing_food_days_7d(logged, AS_OF) == 5

    def test_zero_missing_is_a_real_measured_zero(self):
        logged = frozenset(AS_OF - timedelta(days=i) for i in range(7))
        assert calc.missing_food_days_7d(logged, AS_OF) == 0

    def test_goal_never_set_is_not_recently_changed(self):
        assert calc.goal_recently_changed(None, AS_OF) is False

    def test_goal_started_within_window_is_recent(self):
        assert calc.goal_recently_changed(date(2026, 3, 1), AS_OF) is True

    def test_goal_started_long_ago_is_not_recent(self):
        assert calc.goal_recently_changed(date(2026, 1, 1), AS_OF) is False

    def test_insufficient_when_food_gaps_exceed_threshold(self):
        assert calc.insufficient_data_for_adaptation(4, 0) is True
        assert calc.insufficient_data_for_adaptation(3, 0) is False

    def test_energy_sufficiency_requires_maintenance_and_low_gaps(self):
        assert calc.energy_data_sufficiency(1, True) == "sufficient"
        assert calc.energy_data_sufficiency(1, False) == "insufficient"
        assert calc.energy_data_sufficiency(5, True) == "insufficient"


class TestAgeOn:
    def test_none_dob_is_null(self):
        assert calc.age_on(AS_OF, None) is None

    def test_birthday_already_passed_this_year(self):
        assert calc.age_on(date(2026, 3, 4), date(1996, 1, 1)) == 30

    def test_birthday_not_yet_reached_this_year(self):
        assert calc.age_on(date(2026, 3, 4), date(1996, 12, 1)) == 29


class TestUnloggedUserGetsNullsNotZeros:
    """The task's explicit done-when: a user who logged nothing gets nulls."""

    def test_every_derived_value_is_none_for_an_empty_diary(self):
        assert calc.adherence_7d([], AS_OF).value is None
        assert calc.week_volume_planned([], AS_OF).value is None
        assert calc.week_volume_completed([], AS_OF).value is None
        assert calc.volume_by_muscle_group([], AS_OF).value is None
        assert calc.days_since_rest(frozenset(), AS_OF, None).value is None
        assert calc.consumed_kcal_today([]).value is None
        assert calc.burned_kcal_today([]).value is None
        assert calc.week_balance_kcal([(None, None)] * 7).value is None
        assert calc.weight_trend([]).kg_per_week is None
        assert calc.weight_trend([]).confidence is None
        # None of these are 0 — a real 0 would be indistinguishable from a
        # measurement, which is exactly what "missing is not zero" forbids.


# --- Property-based tests (CLAUDE.md: "Engine — property-based tests") ---


@given(
    points=st.lists(
        st.tuples(
            st.integers(min_value=0, max_value=60),
            st.floats(min_value=30.0, max_value=250.0, allow_nan=False, allow_infinity=False),
        ),
        min_size=0,
        max_size=30,
    )
)
def test_weight_trend_never_crashes_and_confidence_stays_bounded(points):
    result = calc.weight_trend(points)
    if result.confidence is not None:
        assert 0.0 <= result.confidence <= 1.0


@given(
    consumed=st.lists(
        st.one_of(st.none(), st.floats(min_value=0, max_value=6000, allow_nan=False)),
        min_size=7,
        max_size=7,
    ),
    maintenance=st.one_of(st.none(), st.floats(min_value=800, max_value=6000, allow_nan=False)),
)
def test_week_balance_kcal_never_fabricates_a_zero(consumed, maintenance):
    pairs = [(c, maintenance) for c in consumed]
    result = calc.week_balance_kcal(pairs)
    all_missing = maintenance is None or all(c is None for c in consumed)
    if all_missing:
        assert result.value is None
