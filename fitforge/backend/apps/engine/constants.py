"""Named constants for the engine's calculator and adaptive layer.

Every number here that isn't a textbook/compendium value is a judgment call —
see docs/DECISIONS.md for why.

Constants marked PLACEHOLDER are best-effort values pending a proper
literature review or user-testing calibration.  They are safe defaults
but should be refined.
"""

from decimal import Decimal

# ---------------------------------------------------------------------------
# Windows
# ---------------------------------------------------------------------------
ADHERENCE_WINDOW_DAYS = 7
FOOD_GAP_WINDOW_DAYS = 7
WEIGHT_GAP_WINDOW_DAYS = 14
WEIGHT_TREND_WINDOW_DAYS = 28
GOAL_RECENTLY_CHANGED_WINDOW_DAYS = 14

# ---------------------------------------------------------------------------
# Weight trend confidence
# ---------------------------------------------------------------------------
# A 2-point series fits a line perfectly (r^2 == 1) but says nothing about a
# real trend. Confidence is r^2 scaled down until enough points exist.
MIN_WEIGHINS_FOR_FULL_TREND_CONFIDENCE = 6

# ---------------------------------------------------------------------------
# Maintenance kcal (Mifflin-St Jeor)
# ---------------------------------------------------------------------------
# Standard published TDEE activity multipliers (Mifflin-St Jeor companion
# table), not something we invented.
ACTIVITY_MULTIPLIERS = {
    "sedentary": Decimal("1.2"),
    "lightly_active": Decimal("1.375"),
    "moderately_active": Decimal("1.55"),
    "very_active": Decimal("1.725"),
    "extra_active": Decimal("1.9"),
}
# This slice only ever produces the equation-derived maintenance figure
# (§7's "blended" measured path is a later slice), so its confidence is a
# fixed constant rather than a computed one.
EQUATION_MAINTENANCE_CONFIDENCE = 0.7

# Standard approximation: ~7700 kcal per kg of body mass.
KCAL_PER_KG = Decimal("7700")

# ---------------------------------------------------------------------------
# Data sufficiency
# ---------------------------------------------------------------------------
ENERGY_SUFFICIENT_MAX_MISSING_FOOD_DAYS = 2
INSUFFICIENT_MAX_MISSING_FOOD_DAYS_7D = 3
INSUFFICIENT_MAX_MISSING_WEIGHT_DAYS_14D = 10

# ---------------------------------------------------------------------------
# Adaptive engine — blended maintenance sufficiency gate
# ---------------------------------------------------------------------------
# Minimum data in the rolling 14-day window before the measured-maintenance
# path is activated. Below this gate, equation-only maintenance is used and
# UserState.insufficient_data_for_adaptation is True.

# Minimum weight measurements in the 14-day window. Fewer than this and the
# weight-trend regression is too noisy to invert into a reliable TDEE.
BLENDED_MAINTENANCE_MIN_WEIGHT_POINTS = 5

# Minimum food-logged days in the 14-day window. Fewer than this and
# average daily intake is unreliable, so solving maintenance backwards
# from intake vs. weight trend would be garbage-in-garbage-out.
BLENDED_MAINTENANCE_MIN_FOOD_DAYS = 10

# ---------------------------------------------------------------------------
# Adaptive engine — floor values
# ---------------------------------------------------------------------------
# ADA (American Dietetic Association) and NICE clinical guidance recommend
# that adults not consume fewer than 1200 kcal/day (female) or 1500 kcal/day
# (male) without medical supervision. These are hard floors.
MIN_DAILY_KCAL_FEMALE = 1200.0
MIN_DAILY_KCAL_MALE = 1500.0

# Fallback when sex_at_birth is unknown, intersex, or declined — use the
# more conservative (lower) bound so we never over-restrict.
MIN_DAILY_KCAL_DEFAULT = 1200.0

# PLACEHOLDER — pending sport-nutrition literature review.
# Current evidence suggests 1.6–2.2 g/kg for resistance-trained individuals.
# 1.2 g/kg is a conservative floor that protects lean mass during a deficit
# without being so high that it's unreachable on a low-calorie day.
MIN_DAILY_PROTEIN_G_PER_KG = 1.2

# Maximum single-day training volume as a multiple of the trailing 7-day
# average. The 10% cap is a common progressive-overload heuristic — a
# single day that's >10% above the recent average is a spike, not progress.
MAX_VOLUME_INCREASE_FACTOR = 1.10

# PLACEHOLDER — maximum single-day kcal swing from adaptive rebalancing.
# Prevents aggressive day-to-day compensation that feels punitive. 300 kcal
# is roughly the caloric cost of a moderate snack, keeping adjustments
# perceptually small.
MAX_DAILY_KCAL_ADJUSTMENT = 300.0

# ---------------------------------------------------------------------------
# Damping
# ---------------------------------------------------------------------------
# Only this fraction of any week-balance drift carries forward to adjust
# tomorrow's target. Full compensation (1.0) creates an overeat–restrict
# oscillation loop, which is both physiologically counterproductive and
# psychologically harmful.
#
# 0.5 means: if you over-ate by 400 kcal across the week so far, only
# 200 kcal of that shortfall is spread across remaining days, not 400.
DAMPING_FACTOR = 0.5

# Hard invariant: damping must be strictly less than this value.
# A configuration where DAMPING_FACTOR >= 1.0 is NEVER safe. This is
# validated at import time in floors.py.
DAMPING_MAX_EXCLUSIVE = 1.0

# ---------------------------------------------------------------------------
# Readiness ceiling weights — PLACEHOLDER
# ---------------------------------------------------------------------------
# These weights sum to 1.0 and control how much each sub-signal contributes
# to the daily readiness multiplier. Pending calibration against real user
# data and sports-science literature on session-RPE monitoring.

# Weight of recent-load factor (today's volume vs trailing average).
READINESS_LOAD_WEIGHT = 0.35

# Weight of days-since-rest factor (consecutive training days).
READINESS_REST_WEIGHT = 0.30

# Weight of sleep quality (1–5 self-report). When absent, this portion
# is redistributed equally across the other present signals.
READINESS_SLEEP_WEIGHT = 0.20

# Weight of self-reported symptoms and energy (1–5 + symptom count).
# When absent, redistributed like sleep.
READINESS_SYMPTOM_WEIGHT = 0.15

# After this many consecutive training days without rest, the rest
# sub-score drops to 0. PLACEHOLDER — 6 is a conservative estimate;
# many programs schedule deloads after 4–6 consecutive hard days.
READINESS_DAYS_SINCE_REST_CRITICAL = 6

# ---------------------------------------------------------------------------
# Volume recovery safety
# ---------------------------------------------------------------------------
# If remaining weekly volume can't be spread across at least this many
# remaining days without exceeding the volume-increase cap, it is DROPPED
# entirely rather than crammed into fewer days. Cramming raises injury risk.
MAX_SAFE_VOLUME_RECOVERY_DAYS = 3

# Maximum number of symptoms before the symptom sub-score bottoms out.
# PLACEHOLDER — represents a rough ceiling where more symptoms don't
# meaningfully decrease readiness further (it's already very low).
READINESS_MAX_SYMPTOM_COUNT = 5
