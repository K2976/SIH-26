package com.fitforge.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
            prefs.saveActivityMetrics(summary)
            prefs.saveVitals(summary)
            prefs.saveBodyMetrics(summary)
        }
    }

    private fun MutablePreferences.saveActivityMetrics(summary: HealthDataSummary) {
        setOrRemove(Keys.STEPS, summary.steps)
        setOrRemove(Keys.DISTANCE_METERS, summary.distanceMeters)
        setOrRemove(Keys.ACTIVE_CALORIES_KCAL, summary.activeCaloriesKcal)
        setOrRemove(Keys.TOTAL_CALORIES_KCAL, summary.totalCaloriesKcal)
        setOrRemove(Keys.LATEST_EXERCISE_SESSION_TITLE, summary.latestExerciseSessionTitle)
        setOrRemove(Keys.LATEST_EXERCISE_DURATION_MINUTES, summary.latestExerciseDurationMinutes)
    }

    private fun MutablePreferences.saveVitals(summary: HealthDataSummary) {
        setOrRemove(Keys.LATEST_HEART_RATE_BPM, summary.latestHeartRateBpm)
        setOrRemove(Keys.RESTING_HEART_RATE_BPM, summary.restingHeartRateBpm)
        setOrRemove(Keys.SLEEP_DURATION_MINUTES, summary.sleepDurationMinutes)
        setOrRemove(Keys.SLEEP_STAGES_SUMMARY, summary.sleepStagesSummary)
        setOrRemove(Keys.OXYGEN_SATURATION_PERCENTAGE, summary.oxygenSaturationPercentage)
        setOrRemove(Keys.RESPIRATORY_RATE_BPM, summary.respiratoryRateBpm)
    }

    private fun MutablePreferences.saveBodyMetrics(summary: HealthDataSummary) {
        setOrRemove(Keys.WEIGHT_KG, summary.weightKg)
        setOrRemove(Keys.HEIGHT_METERS, summary.heightMeters)
        setOrRemove(Keys.BODY_FAT_PERCENTAGE, summary.bodyFatPercentage)
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) {
            this[key] = value
        } else {
            this.remove(key)
        }
    }

    override suspend fun clearHealthData() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
