package com.fitforge.app.domain.workout

/** One logged set, reduced to what the live-metrics arithmetic needs. */
data class SetMetricInput(val weightKg: Double?, val reps: Int, val isWarmup: Boolean, val exerciseMetValue: Double = 0.0)

/** Total kg moved so far this session — sum of weight × reps, warmups excluded. Pure add/multiply, no thresholds. */
fun sessionVolumeKg(sets: List<SetMetricInput>): Double =
    sets.filterNot { it.isWarmup }.sumOf { (it.weightKg ?: 0.0) * it.reps }

/**
 * Live, local, display-only calorie estimate — never persisted or synced.
 * The server recomputes the authoritative figure from stored MET values on
 * sync (CLAUDE.md hard rule: every calorie is computed in Python).
 *
 * Formula: kcal = MET × bodyweight_kg × duration_hours (the standard
 * compendium-of-physical-activities formula that `met_value` exists for).
 * A session mixes exercises, so this uses the mean MET across logged sets.
 *
 * Bodyweight unknown → burn is uncomputable, not zero: returns null so the
 * UI can show an empty state instead of a fabricated number.
 */
fun estimatedSessionKcal(sets: List<SetMetricInput>, bodyweightKg: Double?, elapsedMinutes: Double): Double? {
    if (bodyweightKg == null || sets.isEmpty()) return null
    val meanMet = sets.sumOf { it.exerciseMetValue } / sets.size
    return meanMet * bodyweightKg * (elapsedMinutes / 60.0)
}
