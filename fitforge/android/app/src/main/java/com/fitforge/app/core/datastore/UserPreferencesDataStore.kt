package com.fitforge.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The session, unit preference, reference sync cursor, and cached bodyweight. No other state belongs here. */
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val UNIT_PREFERENCE = stringPreferencesKey("unit_preference")
        val REFERENCE_VERSION = intPreferencesKey("reference_version")
        val BODYWEIGHT_KG = doublePreferencesKey("bodyweight_kg")
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[Keys.ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[Keys.REFRESH_TOKEN] }
    val unitPreference: Flow<String?> = dataStore.data.map { it[Keys.UNIT_PREFERENCE] }
    val referenceVersion: Flow<Int> = dataStore.data.map { it[Keys.REFERENCE_VERSION] ?: 0 }
    /** Last-known bodyweight from the profile — cached locally so live burn estimates work offline. */
    val bodyweightKg: Flow<Double?> = dataStore.data.map { it[Keys.BODYWEIGHT_KG] }

    suspend fun currentAccessToken(): String? = dataStore.data.first()[Keys.ACCESS_TOKEN]

    suspend fun currentRefreshToken(): String? = dataStore.data.first()[Keys.REFRESH_TOKEN]

    suspend fun saveSession(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
    }

    suspend fun setUnitPreference(value: String) {
        dataStore.edit { prefs -> prefs[Keys.UNIT_PREFERENCE] = value }
    }

    suspend fun setReferenceVersion(value: Int) {
        dataStore.edit { prefs -> prefs[Keys.REFERENCE_VERSION] = value }
    }

    suspend fun setBodyweightKg(value: Double) {
        dataStore.edit { prefs -> prefs[Keys.BODYWEIGHT_KG] = value }
    }
}
