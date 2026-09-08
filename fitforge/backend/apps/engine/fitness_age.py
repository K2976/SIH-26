"""Non-exercise cardiorespiratory fitness (VO2max) estimate — §11.

Pure function, same contract as calculator.py: plain data in, plain data
out. No ORM, no I/O, no datetime.now(). Never touches the LLM (CLAUDE.md
rule 1) and is never tuned — every coefficient in constants.py is copied
verbatim from the published source, not fit or adjusted against local data.

Model: Jurca et al. 2005 (see CITATION below), Table 5's NASA-cohort
regression — the model the paper's own clinical worksheet (Figure 1) is
built from.

Known limitation, disclosed rather than hidden: the paper's own worked
example (a 45-year-old man, BMI ~29.6 from 193 lb/68 in, resting HR 72,
PA level 2) is printed as 9.02 METs, but recomputing it from Table 5's own
printed coefficients gives ~9.51 METs under every reading of the inputs
tried (metric vs. imperial BMI, PA level 1 vs. 2, rounded vs. unrounded
BMI). This looks like a rounding artifact in the original publication, not
an error here — see test_fitness_age.py, which asserts against the value
this implementation actually produces, not the paper's inconsistent
worked figure.

The raw fitness number (METs / VO2max in mL.kg^-1.min^-1) is turned into a
"fitness age" by comparing it against a second published source:
Kaminsky et al. 2015's FRIEND-registry age/sex-normed VO2max percentiles
(see NORMS_CITATION) — chosen specifically because it's a US population,
matching Jurca's own largest cohort (ACLS), unlike the Norwegian HUNT-study
norms (Loe et al. 2013) considered and rejected for this pairing. The
conversion is a piecewise-linear interpolation against the published
50th-percentile-by-decade table — clamped to the table's 20-79 range,
never extrapolated (CLAUDE.md rule 2).
"""

from __future__ import annotations

from dataclasses import dataclass

from apps.engine.constants import (
    ACTIVITY_LEVEL_TO_SR_PA,
    FRIEND_50TH_PERCENTILE_VO2MAX_BY_AGE_MIDPOINT,
    JURCA_NASA_AGE_COEF,
    JURCA_NASA_BMI_COEF,
    JURCA_NASA_INTERCEPT,
    JURCA_NASA_MALE_COEF,
    JURCA_NASA_RESTING_HR_COEF,
    JURCA_NASA_SR_PA_COEFS,
    MET_TO_ML_KG_MIN,
)

CITATION = (
    "Jurca R, Jackson AS, LaMonte MJ, Morrow JR Jr, Blair SN, Wareham NJ, "
    "Haskell WL, van Mechelen W, Church TS, Jakicic JM, Laukkanen R. "
    "Assessing Cardiorespiratory Fitness Without Performing Exercise "
    "Testing. Am J Prev Med. 2005;29(3):185-193."
)

NORMS_CITATION = (
    "Kaminsky LA, Arena R, Myers J. Reference Standards for "
    "Cardiorespiratory Fitness Measured With Cardiopulmonary Exercise "
    "Testing: Data From the Fitness Registry and the Importance of "
    "Exercise National Database (FRIEND). Mayo Clin Proc. "
    "2015;90(11):1515-1523."
)


@dataclass(frozen=True)
class CardiorespiratoryFitnessResult:
    mets: float
    vo2max_ml_kg_min: float
    citation: str
    # Exactly what was fed into the formula, for the UI to show alongside
    # the number (§11: "show the inputs that produced the number").
    inputs: dict[str, float | int | str]


def bmi_from_height_weight(height_cm: float | None, weight_kg: float | None) -> float | None:
    if height_cm is None or weight_kg is None or height_cm <= 0:
        return None
    height_m = height_cm / 100.0
    return weight_kg / (height_m * height_m)


def estimate_cardiorespiratory_fitness(
    age: int | None,
    sex_at_birth: str | None,
    bmi: float | None,
    resting_hr: int | None,
    activity_level: str | None,
) -> CardiorespiratoryFitnessResult | None:
    """None whenever a required input is missing or unsupported by the
    model — never a placeholder or an averaged guess (CLAUDE.md rule 2).
    The paper's regression has only a binary sex term, so sex_at_birth
    values outside male/female (declined, intersex, unknown) return None
    rather than silently picking a branch.
    """
    if age is None or bmi is None or resting_hr is None:
        return None
    if sex_at_birth not in ("male", "female"):
        return None
    sr_pa_level = ACTIVITY_LEVEL_TO_SR_PA.get(activity_level)
    if sr_pa_level is None:
        return None

    male = 1.0 if sex_at_birth == "male" else 0.0
    mets = (
        JURCA_NASA_INTERCEPT
        + JURCA_NASA_MALE_COEF * male
        + JURCA_NASA_AGE_COEF * age
        + JURCA_NASA_BMI_COEF * bmi
        + JURCA_NASA_RESTING_HR_COEF * resting_hr
        + JURCA_NASA_SR_PA_COEFS[sr_pa_level]
    )
    return CardiorespiratoryFitnessResult(
        mets=mets,
        vo2max_ml_kg_min=mets * MET_TO_ML_KG_MIN,
        citation=CITATION,
        inputs={
            "age": age,
            "sex_at_birth": sex_at_birth,
            "bmi": round(bmi, 1),
            "resting_hr": resting_hr,
            "activity_level": activity_level,
        },
    )


@dataclass(frozen=True)
class FitnessAgeResult:
    fitness_age: float
    cardiorespiratory_fitness: CardiorespiratoryFitnessResult
    norms_citation: str


def vo2max_to_fitness_age(vo2max_ml_kg_min: float, sex_at_birth: str) -> float | None:
    """Piecewise-linear interpolation against Kaminsky et al. 2015's
    50th-percentile-VO2max-by-decade table: finds the age at which the
    average person of this sex has this VO2max.

    VO2max at or above the table's youngest-decade mean, or at or below
    its oldest-decade mean, is clamped to that decade's midpoint rather
    than extrapolated past what the published data actually covers.
    """
    table = FRIEND_50TH_PERCENTILE_VO2MAX_BY_AGE_MIDPOINT.get(sex_at_birth)
    if table is None:
        return None

    ages = sorted(table)  # ascending age
    vo2s = [table[a] for a in ages]  # descending VO2max as age increases

    if vo2max_ml_kg_min >= vo2s[0]:
        return ages[0]
    if vo2max_ml_kg_min <= vo2s[-1]:
        return ages[-1]

    for i in range(len(ages) - 1):
        v_hi, v_lo = vo2s[i], vo2s[i + 1]
        if v_lo <= vo2max_ml_kg_min <= v_hi:
            a_lo, a_hi = ages[i], ages[i + 1]
            frac = (v_hi - vo2max_ml_kg_min) / (v_hi - v_lo)
            return a_lo + frac * (a_hi - a_lo)
    return None  # unreachable: the clamps above cover the remaining range


def estimate_fitness_age(
    age: int | None,
    sex_at_birth: str | None,
    bmi: float | None,
    resting_hr: int | None,
    activity_level: str | None,
) -> FitnessAgeResult | None:
    """Composes estimate_cardiorespiratory_fitness (Jurca 2005) with
    vo2max_to_fitness_age (Kaminsky et al. 2015 norms). None whenever
    either published source can't produce a value for these inputs.
    """
    fitness = estimate_cardiorespiratory_fitness(
        age=age,
        sex_at_birth=sex_at_birth,
        bmi=bmi,
        resting_hr=resting_hr,
        activity_level=activity_level,
    )
    if fitness is None:
        return None
    fitness_age_value = vo2max_to_fitness_age(fitness.vo2max_ml_kg_min, sex_at_birth)
    if fitness_age_value is None:
        return None
    return FitnessAgeResult(
        fitness_age=fitness_age_value,
        cardiorespiratory_fitness=fitness,
        norms_citation=NORMS_CITATION,
    )
