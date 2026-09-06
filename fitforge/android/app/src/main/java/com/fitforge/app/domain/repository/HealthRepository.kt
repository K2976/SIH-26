package com.fitforge.app.domain.repository

import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain repository defining interactions with health data providers.
 */
interface HealthRepository {
    /**
     * Flow emitting the latest cached health data summary stored locally.
     */
    val latestHealthData: Flow<HealthDataSummary?>

    /**
     * Flow emitting the timestamp of the last successful health data synchronization.
     */
    val lastSyncedAt: Flow<Instant?>

    /**
     * Checks whether Health Connect is available, needs installation, or is unsupported.
     */
    fun getAvailability(): HealthConnectAvailability

    /**
     * Returns the set of health permissions FitForge requires for complete data ingestion.
     */
    fun getRequiredPermissions(): Set<String>

    /**
     * Returns the set of health permissions currently granted by the user.
     */
    suspend fun getGrantedPermissions(): Set<String>

    /**
     * Checks whether all required health permissions have been granted.
     */
    suspend fun hasAllPermissions(): Boolean

    /**
     * Checks whether at least one required health permission has been granted.
     */
    suspend fun hasAnyPermission(): Boolean

    /**
     * Reads available health records from Health Connect within the given time window.
     * Only queries permissions that have actually been granted.
     */
    suspend fun readHealthSummary(startTime: Instant, endTime: Instant): Result<HealthDataSummary>

    /**
     * Synchronizes health data for the current day from Health Connect, updates local persistence,
     * and returns the resulting summary. Shared between manual "Sync Now" and background sync.
     */
    suspend fun syncHealthData(): Result<HealthDataSummary>
}

