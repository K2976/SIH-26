package com.fitforge.app.domain.repository

import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import java.time.Instant

/**
 * Domain repository defining interactions with health data providers.
 */
interface HealthRepository {
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
     * Reads available health records from Health Connect within the given time window.
     * Only queries permissions that have actually been granted.
     */
    suspend fun readHealthSummary(startTime: Instant, endTime: Instant): Result<HealthDataSummary>
}
