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
# Cardiorespiratory fitness (non-exercise VO2max estimate) — §11
# ---------------------------------------------------------------------------
# Jurca R, Jackson AS, LaMonte MJ, et al. Assessing Cardiorespiratory
# Fitness Without Performing Exercise Testing. Am J Prev Med.
# 2005;29(3):185-193. Table 5, NASA-cohort model — the lowest-SEE of the
# paper's three cohort models and the one its own clinical worksheet
# (Figure 1) is built from. Coefficients copied verbatim; never tuned.
# Output is METs (1 MET = 3.5 mL O2 . kg body mass^-1 . min^-1).
JURCA_NASA_INTERCEPT = 18.07
JURCA_NASA_MALE_COEF = 2.77  # sex term: male=1, female=0
JURCA_NASA_AGE_COEF = -0.10  # per year
JURCA_NASA_BMI_COEF = -0.17  # per kg/m^2
JURCA_NASA_RESTING_HR_COEF = -0.03  # per beat/min
# Self-reported physical activity (SR-PA), dummy-coded against SR-PA-0
# (inactive) as the reference category (contributes 0). See the paper's
# Table 1 for what each level describes.
JURCA_NASA_SR_PA_COEFS = {
    0: 0.0,
    1: 0.32,
    2: 1.06,
    3: 1.76,
    4: 3.03,
}
MET_TO_ML_KG_MIN = 3.5

# UserProfile.activity_level is the 5-level TDEE multiplier scale used by
# maintenance_kcal above (ACTIVITY_MULTIPLIERS) — a different instrument
# from Jurca's SR-PA scale, but both are 5 ordered levels. This ordinal
# mapping is an adaptation disclosed to the user, not part of the
# published model.
ACTIVITY_LEVEL_TO_SR_PA = {
    "sedentary": 0,
    "lightly_active": 1,
    "moderately_active": 2,
    "very_active": 3,
    "extra_active": 4,
}

# Kaminsky LA, Arena R, Myers J. Reference Standards for Cardiorespiratory
# Fitness Measured With Cardiopulmonary Exercise Testing: Data From the
# Fitness Registry and the Importance of Exercise National Database
# (FRIEND). Mayo Clin Proc. 2015;90(11):1515-1523, Table 3. 50th-percentile
# VO2max (mL O2 . kg^-1 . min^-1) by sex and age-decade, US population —
# chosen over the (Norwegian) Loe et al. 2013 norms because it's the
# population Jurca's ACLS cohort (its largest) is drawn from. Keyed by each
# decade's midpoint for piecewise-linear interpolation in fitness_age.py.
FRIEND_50TH_PERCENTILE_VO2MAX_BY_AGE_MIDPOINT = {
    "male": {24.5: 48.0, 34.5: 42.4, 44.5: 37.8, 54.5: 32.6, 64.5: 28.2, 74.5: 24.4},
    "female": {24.5: 37.6, 34.5: 30.2, 44.5: 26.7, 54.5: 23.4, 64.5: 20.0, 74.5: 18.3},
}

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

# ---------------------------------------------------------------------------
# Cycle module (§10) — phase derivation
# ---------------------------------------------------------------------------
# Phase is derived only from a user's own period-start history, gated by
# confidence. Below these thresholds it is None — never a default middle
# value (CLAUDE.md rule 2).

# Need at least this many logged period starts (i.e. this many completed
# cycles) before a typical cycle length is inferred at all.
MIN_PERIOD_STARTS_FOR_TYPICAL_LENGTH = 3

# PLACEHOLDER — cycle-length confidence reaches 1.0 at this many observed
# intervals, mirroring MIN_WEIGHINGS_FOR_FULL_TREND_CONFIDENCE's role for
# weight trend. Pending calibration.
FULL_CONFIDENCE_CYCLES = 4

# Below this confidence, phase is not surfaced even though a typical length
# exists — a rough guess from 2-3 cycles is not enough to label a phase.
MIN_CONFIDENCE_FOR_PHASE = 0.5

# Fallback period length when CycleProfile.typical_period_days is unset.
# PLACEHOLDER — a commonly cited average; replaced by the user's own history
# via CycleProfile as soon as it exists.
DEFAULT_PERIOD_DAYS = 5

# The luteal phase (ovulation to next period) is the most consistent part of
# the cycle across individuals and cycle lengths, unlike the follicular
# phase which varies. A widely cited approximate constant, not measured.
LUTEAL_PHASE_DAYS = 14

# Width of the ovulatory window itself, centered on the estimated ovulation
# day (typical_length - LUTEAL_PHASE_DAYS).
OVULATORY_WINDOW_DAYS = 2

# If cycle_day runs this many days past typical_length_days without a new
# period start being logged, the cycle has diverged too far from her history
# to label a phase confidently — this is exactly the situation the energy
# availability check (below) is watching for.
CYCLE_OVERDUE_UNCERTAIN_BUFFER_DAYS = 3

# ---------------------------------------------------------------------------
# Cycle module — pattern learning (CyclePattern)
# ---------------------------------------------------------------------------
# PLACEHOLDER — full confidence in a per-cycle-day pattern (energy, symptom
# frequency, adherence, volume) at this many contributing cycles.
CYCLE_PATTERN_FULL_CONFIDENCE_CYCLES = 3

# ---------------------------------------------------------------------------
# Cycle module — energy availability check
# ---------------------------------------------------------------------------
# The most important safety feature in the product (§10). All three
# conditions must hold: natural tracking, a significantly overdue period,
# and a sustained deficit alongside high training load. It does not
# diagnose; it stops the engine from automatically escalating the deficit
# further and surfaces a flag for the UI to turn into a "talk to a doctor"
# suggestion.

# PLACEHOLDER — how many days past the predicted period date counts as
# "significantly overdue". Pending clinical review.
PERIOD_OVERDUE_DAYS_THRESHOLD = 10

# PLACEHOLDER — consecutive days of logged intake meaningfully below
# maintenance before a deficit counts as "sustained".
DEFICIT_STREAK_DAYS_THRESHOLD = 14

# A day only counts toward the deficit streak if logged intake falls this
# far below maintenance — small day-to-day noise shouldn't count.
DEFICIT_MARGIN_KCAL = 200.0

# PLACEHOLDER — days since rest at or above this counts as "high training
# load" for this check. Set below READINESS_DAYS_SINCE_REST_CRITICAL so this
# safety check can fire before readiness alone would already reflect it.
HIGH_TRAINING_LOAD_DAYS_SINCE_REST = 5

# The flag value UserState.energy_availability_flag carries when it fires.
# A key, not a diagnosis: it names no condition and carries no score. The UI
# is responsible for turning this into a "consider talking to a doctor"
# suggestion.
ENERGY_AVAILABILITY_FLAG = "energy_availability_review"
