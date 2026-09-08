"""Coach context builder — the only place UserState data is allowed to
reach an LLM prompt.

Cycle data never leaves the server (§10, §12, CLAUDE.md "Security"). This is
enforced with an ALLOWLIST, not a blocklist: only fields named here are ever
copied out of a UserState dict. A field that doesn't appear below — "cycle"
today, or any field added to UserState tomorrow — is excluded by
construction, not by remembering to strip it. See
apps/coach/tests/test_prompt.py for the test this exists to satisfy.

No coach chat / tool-calling loop exists yet (§12 is a later slice); this is
the one piece the cycle module's privacy requirement needs today.
"""

from __future__ import annotations

from typing import Any

# Top-level UserState keys the coach may ever see, and — for keys whose
# value is itself a dict — which of *its* keys may pass through. `None`
# means "copy the value as-is" (only for plain scalars). "cycle" is
# deliberately absent: there is no entry for it, at any level.
_ALLOWED_FIELDS: dict[str, tuple[str, ...] | None] = {
    "as_of": None,
    "profile": ("age", "sex_at_birth", "height_cm", "unit_preference", "timezone"),
    "goal": ("energy_direction", "training_focus", "target_rate_kg_per_week", "auto_adjust"),
    "energy": (
        "maintenance_kcal",
        "target_kcal_today",
        "consumed_kcal_today",
        "week_balance_kcal",
        "data_sufficiency",
    ),
    "training": (
        "week_volume_planned",
        "week_volume_completed",
        "adherence_7d",
        "readiness_ceiling_today",
        "days_since_rest",
    ),
    "plan_delta": ("target_kcal", "volume_today_kg", "readiness_ceiling", "reason", "clamped_by"),
    "body": ("latest_weight_kg", "weight_trend_kg_per_week", "fitness_age"),
    "flags": (
        "missing_food_days_7d",
        "missing_weight_days_14d",
        "goal_recently_changed",
        "insufficient_data_for_adaptation",
    ),
}


def build_coach_context(state: dict[str, Any]) -> dict[str, Any]:
    """Filter a UserState dict down to what the coach's prompt may include.

    Any key not listed in _ALLOWED_FIELDS is dropped, whatever it's called
    and however UserState grows in the future.
    """
    context: dict[str, Any] = {}
    for key, allowed_subfields in _ALLOWED_FIELDS.items():
        if key not in state:
            continue
        value = state[key]
        if allowed_subfields is None:
            context[key] = value
        elif isinstance(value, dict):
            context[key] = {k: v for k, v in value.items() if k in allowed_subfields}
        # A permitted key whose actual value isn't a dict (e.g. plan_delta is
        # None) is dropped rather than guessed at — no partial dict to filter.
    return context
