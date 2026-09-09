package com.fitforge.app.pose

/**
 * A rule-based proxy label for a completed rep, derived from the exact same
 * fields [CompletedRep] already carries — not a new measurement. This is
 * what tags training data for the future rep-quality classifier (see
 * `fitforge/ml/`); it is never shown to the user and never replaces
 * [CompletedRep.depthReached]/[CompletedRep.lockoutReached]/
 * [CompletedRep.symmetryDeviationDeg] as the source of truth.
 */
enum class RepQuality { CLEAN, SHALLOW, ASYMMETRIC, PARTIAL_LOCKOUT }

/**
 * Priority order when a rep has more than one defect: depth first (a shallow
 * rep is shallow regardless of what else is true of it), then symmetry, then
 * lockout, else clean. [symmetryToleranceDeg] mirrors the same threshold the
 * live UI already uses to color-code symmetry feedback
 * ([ExercisePoseConfig.symmetryToleranceDeg]).
 */
fun repQuality(rep: CompletedRep, symmetryToleranceDeg: Double): RepQuality = when {
    !rep.depthReached -> RepQuality.SHALLOW
    rep.symmetryDeviationDeg != null && rep.symmetryDeviationDeg > symmetryToleranceDeg -> RepQuality.ASYMMETRIC
    !rep.lockoutReached -> RepQuality.PARTIAL_LOCKOUT
    else -> RepQuality.CLEAN
}
