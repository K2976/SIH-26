package com.fitforge.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitforge.app.domain.model.HealthDataSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.healthPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fitforge_health_prefs",
)

/**
 * Local data source contract for persisting and observing Health Connect data and sync metadata.
 */
interface HealthLocalDataSource {
    val latestHealthData: Flow<HealthDataSummary?>
    val lastSyncedAt: Flow<Instant?>

    suspend fun saveHealthSummary(summary: HealthDataSummary)
    suspend fun clearHealthData()
}

@Singleton
class HealthDataStoreLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HealthLocalDataSource {

    private val dataStore: DataStore<Preferences> = context.healthPreferencesDataStore

    private object Keys {
        val LAST_SYNCED_AT_EPOCH_MS = longPreferencesKey("last_synced_at_epoch_ms")
        val STEPS = longPreferencesKey("health_steps")
        val DISTANCE_METERS = doublePreferencesKey("health_distance_meters")
        val ACTIVE_CALORIES_KCAL = doublePreferencesKey("health_active_calories")
        val TOTAL_CALORIES_KCAL = doublePreferencesKey("health_total_calories")
        val LATEST_HEART_RATE_BPM = longPreferencesKey("health_latest_heart_rate")
        val RESTING_HEART_RATE_BPM = longPreferencesKey("health_resting_heart_rate")
        val SLEEP_DURATION_MINUTES = longPreferencesKey("health_sleep_duration_minutes")
        val SLEEP_STAGES_SUMMARY = stringPreferencesKey("health_sleep_summary")
        val LATEST_EXERCISE_SESSION_TITLE = stringPreferencesKey("health_exercise_title")
        val LATEST_EXERCISE_DURATION_MINUTES = longPreferencesKey("health_exercise_duration_minutes")
        val WEIGHT_KG = doublePreferencesKey("health_weight_kg")
        val HEIGHT_METERS = doublePreferencesKey("health_height_meters")
        val BODY_FAT_PERCENTAGE = doublePreferencesKey("health_body_fat_percentage")
        val OXYGEN_SATURATION_PERCENTAGE = doublePreferencesKey("health_oxygen_saturation")
        val RESPIRATORY_RATE_BPM = doublePreferencesKey("health_respiratory_rate")
    }

    override val lastSyncedAt: Flow<Instant?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNCED_AT_EPOCH_MS]?.let { Instant.ofEpochMilli(it) }
    }

    override val latestHealthData: Flow<HealthDataSummary?> = dataStore.data.map { prefs ->
        val lastSyncedMs = prefs[Keys.LAST_SYNCED_AT_EPOCH_MS] ?: return@map null
        HealthDataSummary(
            steps = prefs[Keys.STEPS],
            distanceMeters = prefs[Keys.DISTANCE_METERS],
            activeCaloriesKcal = prefs[Keys.ACTIVE_CALORIES_KCAL],
            totalCaloriesKcal = prefs[Keys.TOTAL_CALORIES_KCAL],
            latestHeartRateBpm = prefs[Keys.LATEST_HEART_RATE_BPM],
            restingHeartRateBpm = prefs[Keys.RESTING_HEART_RATE_BPM],
            sleepDurationMinutes = prefs[Keys.SLEEP_DURATION_MINUTES],
            sleepStagesSummary = prefs[Keys.SLEEP_STAGES_SUMMARY],
            latestExerciseSessionTitle = prefs[Keys.LATEST_EXERCISE_SESSION_TITLE],
            latestExerciseDurationMinutes = prefs[Keys.LATEST_EXERCISE_DURATION_MINUTES],
            weightKg = prefs[Keys.WEIGHT_KG],
            heightMeters = prefs[Keys.HEIGHT_METERS],
            bodyFatPercentage = prefs[Keys.BODY_FAT_PERCENTAGE],
            oxygenSaturationPercentage = prefs[Keys.OXYGEN_SATURATION_PERCENTAGE],
            respiratoryRateBpm = prefs[Keys.RESPIRATORY_RATE_BPM],
            lastSyncedAt = Instant.ofEpochMilli(lastSyncedMs),
        )
    }

    override suspend fun saveHealthSummary(summary: HealthDataSummary) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNCED_AT_EPOCH_MS] = summary.lastSyncedAt.toEpochMilli()

            summary.steps?.let { prefs[Keys.STEPS] = it } ?: prefs.remove(Keys.STEPS)
            summary.distanceMeters?.let { prefs[Keys.DISTANCE_METERS] = it } ?: prefs.remove(Keys.DISTANCE_METERS)
            summary.activeCaloriesKcal?.let { prefs[Keys.ACTIVE_CALORIES_KCAL] = it } ?: prefs.remove(Keys.ACTIVE_CALORIES_KCAL)
            summary.totalCaloriesKcal?.let { prefs[Keys.TOTAL_CALORIES_KCAL] = it } ?: prefs.remove(Keys.TOTAL_CALORIES_KCAL)
            summary.latestHeartRateBpm?.let { prefs[Keys.LATEST_HEART_RATE_BPM] = it } ?: prefs.remove(Keys.LATEST_HEART_RATE_BPM)
            summary.restingHeartRateBpm?.let { prefs[Keys.RESTING_HEART_RATE_BPM] = it } ?: prefs.remove(Keys.RESTING_HEART_RATE_BPM)
            summary.sleepDurationMinutes?.let { prefs[Keys.SLEEP_DURATION_MINUTES] = it } ?: prefs.remove(Keys.SLEEP_DURATION_MINUTES)
            summary.sleepStagesSummary?.let { prefs[Keys.SLEEP_STAGES_SUMMARY] = it } ?: prefs.remove(Keys.SLEEP_STAGES_SUMMARY)
            summary.latestExerciseSessionTitle?.let { prefs[Keys.LATEST_EXERCISE_SESSION_TITLE] = it } ?: prefs.remove(Keys.LATEST_EXERCISE_SESSION_TITLE)
            summary.latestExerciseDurationMinutes?.let { prefs[Keys.LATEST_EXERCISE_DURATION_MINUTES] = it } ?: prefs.remove(Keys.LATEST_EXERCISE_DURATION_MINUTES)
            summary.weightKg?.let { prefs[Keys.WEIGHT_KG] = it } ?: prefs.remove(Keys.WEIGHT_KG)
            summary.heightMeters?.let { prefs[Keys.HEIGHT_METERS] = it } ?: prefs.remove(Keys.HEIGHT_METERS)
            summary.bodyFatPercentage?.let { prefs[Keys.BODY_FAT_PERCENTAGE] = it } ?: prefs.remove(Keys.BODY_FAT_PERCENTAGE)
            summary.oxygenSaturationPercentage?.let { prefs[Keys.OXYGEN_SATURATION_PERCENTAGE] = it } ?: prefs.remove(Keys.OXYGEN_SATURATION_PERCENTAGE)
            summary.respiratoryRateBpm?.let { prefs[Keys.RESPIRATORY_RATE_BPM] = it } ?: prefs.remove(Keys.RESPIRATORY_RATE_BPM)
        }
    }

    override suspend fun clearHealthData() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
