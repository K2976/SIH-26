package com.fitforge.app.domain.model

import java.time.Instant

/**
 * Summary of all health and fitness data retrieved from Health Connect.
 * Nullable fields represent data types that are either not permitted,
 * not provided by the user's wearable/device, or have no recorded data for the queried period.
 */
data class HealthDataSummary(
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCaloriesKcal: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val sleepDurationMinutes: Long? = null,
    val sleepStagesSummary: String? = null,
    val latestExerciseSessionTitle: String? = null,
    val latestExerciseDurationMinutes: Long? = null,
    val weightKg: Double? = null,
    val heightMeters: Double? = null,
    val bodyFatPercentage: Double? = null,
    val oxygenSaturationPercentage: Double? = null,
    val respiratoryRateBpm: Double? = null,
    val lastSyncedAt: Instant = Instant.now(),
)
