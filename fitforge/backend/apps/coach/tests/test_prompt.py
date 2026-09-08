"""Cycle data must never reach an LLM prompt (§10, §12, CLAUDE.md).

No @pytest.mark.django_db: build_coach_context is a pure dict transform.
"""

from apps.coach.prompt import build_coach_context

FULL_STATE = {
    "as_of": "2026-09-08",
    "profile": {
        "age": 29,
        "sex_at_birth": "female",
        "height_cm": 165.0,
        "unit_preference": "metric",
        "tracks_cycle": True,
        "timezone": "UTC",
    },
    "goal": {
        "energy_direction": "cut",
        "training_focus": "hypertrophy",
        "target_rate_kg_per_week": 0.25,
        "auto_adjust": True,
    },
    "energy": {
        "maintenance_kcal": 2100.0,
        "target_kcal_today": 1900.0,
        "consumed_kcal_today": 1500.0,
        "week_balance_kcal": -300.0,
        "data_sufficiency": "sufficient",
    },
    "training": {
        "week_volume_planned": 5000.0,
        "week_volume_completed": 4000.0,
        "adherence_7d": 0.8,
        "readiness_ceiling_today": 0.7,
        "days_since_rest": 3,
    },
    "plan_delta": {
        "target_kcal": 1900.0,
        "volume_today_kg": 800.0,
        "readiness_ceiling": 0.7,
        "reason": "some reason",
        "clamped_by": [],
    },
    "body": {
        "latest_weight_kg": 60.0,
        "weight_trend_kg_per_week": -0.2,
        "trend_confidence": 0.6,
        "fitness_age": None,
        "fitness_age_inputs": None,
    },
    "cycle": {
        "enabled": True,
        "tracking_mode": "natural",
        "phase": "luteal",
        "cycle_day": 22,
        "confidence": 0.9,
        "days_until_predicted_period": 3,
        "learned_pattern": {"sample_cycles": 4, "confidence": 1.0, "by_cycle_day": {}},
        "energy_availability_flag": "energy_availability_review",
    },
    "flags": {
        "missing_food_days_7d": 0,
        "missing_weight_days_14d": 2,
        "goal_recently_changed": False,
        "insufficient_data_for_adaptation": False,
    },
}


def _flatten_keys(value) -> set[str]:
    keys: set[str] = set()
    if isinstance(value, dict):
        for k, v in value.items():
            keys.add(k)
            keys |= _flatten_keys(v)
    elif isinstance(value, list):
        for item in value:
            keys |= _flatten_keys(item)
    return keys


def test_prompt_builder_excludes_cycle_data():
    context = build_coach_context(FULL_STATE)

    assert "cycle" not in context

    cycle_only_field_names = {
        "tracking_mode",
        "phase",
        "cycle_day",
        "days_until_predicted_period",
        "learned_pattern",
        "energy_availability_flag",
    }
    assert _flatten_keys(context).isdisjoint(cycle_only_field_names)


def test_prompt_builder_is_an_allowlist_not_a_blocklist():
    """A brand-new, unlisted field must be dropped too — not just "cycle".

    This is what makes the guarantee survive UserState growing new fields
    without anyone remembering to update a blocklist.
    """
    state = dict(FULL_STATE)
    state["some_future_field_nobody_reviewed"] = {"secret": "value"}
    state["profile"] = dict(state["profile"], some_new_profile_field="x")

    context = build_coach_context(state)

    assert "some_future_field_nobody_reviewed" not in context
    assert "some_new_profile_field" not in context["profile"]


def test_prompt_builder_keeps_permitted_fields():
    context = build_coach_context(FULL_STATE)

    assert context["energy"]["target_kcal_today"] == 1900.0
    assert context["training"]["readiness_ceiling_today"] == 0.7
    assert context["profile"]["age"] == 29
    # tracks_cycle is a boolean opt-in flag, not cycle data itself, but the
    # allowlist excludes it too — no reason for the coach to see it.
    assert "tracks_cycle" not in context["profile"]


def test_prompt_builder_handles_missing_sections():
    assert build_coach_context({}) == {}
    assert build_coach_context({"cycle": FULL_STATE["cycle"]}) == {}
