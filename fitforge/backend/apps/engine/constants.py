"""Named constants for the engine's calculator. Every number here that isn't a
textbook/compendium value is a judgment call — see docs/DECISIONS.md for why.
"""

from decimal import Decimal

# --- Windows ---
ADHERENCE_WINDOW_DAYS = 7
FOOD_GAP_WINDOW_DAYS = 7
WEIGHT_GAP_WINDOW_DAYS = 14
WEIGHT_TREND_WINDOW_DAYS = 28
GOAL_RECENTLY_CHANGED_WINDOW_DAYS = 14

# --- Weight trend confidence ---
# A 2-point series fits a line perfectly (r^2 == 1) but says nothing about a
# real trend. Confidence is r^2 scaled down until enough points exist.
MIN_WEIGHINS_FOR_FULL_TREND_CONFIDENCE = 6

# --- Maintenance kcal (Mifflin-St Jeor) ---
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

# --- Data sufficiency ---
ENERGY_SUFFICIENT_MAX_MISSING_FOOD_DAYS = 2
INSUFFICIENT_MAX_MISSING_FOOD_DAYS_7D = 3
INSUFFICIENT_MAX_MISSING_WEIGHT_DAYS_14D = 10
