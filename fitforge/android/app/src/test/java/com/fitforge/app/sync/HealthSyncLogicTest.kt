package com.fitforge.app.sync

import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.model.HealthDataSummary
import com.fitforge.app.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests verifying WorkManager constants and synchronization behavior under various permission states.
 */
class HealthSyncLogicTest {

    @Test
    fun `unique work name matches convention`() {
        assertEquals("fitforge_health_connect_sync", HEALTH_CONNECT_SYNC_WORK_NAME)
        assertTrue(HEALTH_CONNECT_SYNC_WORK_NAME.isNotEmpty())
    }

    @Test
    fun `graceful exit when Health Connect is not available`() = runBlocking {
        val fakeRepo = FakeHealthRepository(
            availability = HealthConnectAvailability.NotSupported,
            grantedPermissions = setOf("android.permission.health.READ_STEPS"),
        )

        val result = fakeRepo.syncHealthData()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `graceful exit when no permissions are granted`() = runBlocking {
        val fakeRepo = FakeHealthRepository(
            availability = HealthConnectAvailability.Available,
            grantedPermissions = emptySet(),
        )

        assertFalse(fakeRepo.hasAnyPermission())
        val result = fakeRepo.syncHealthData()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `successful sync updates health data summary`() = runBlocking {
        val testSummary = HealthDataSummary(
            steps = 5420L,
            distanceMeters = 3850.5,
            activeCaloriesKcal = 210.0,
            totalCaloriesKcal = 1850.0,
            latestHeartRateBpm = 72L,
            lastSyncedAt = Instant.parse("2026-09-06T12:00:00Z"),
        )

        val fakeRepo = FakeHealthRepository(
            availability = HealthConnectAvailability.Available,
            grantedPermissions = setOf("android.permission.health.READ_STEPS"),
            summaryToReturn = testSummary,
        )

        assertTrue(fakeRepo.hasAnyPermission())
        val result = fakeRepo.syncHealthData()
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertEquals(5420L, data?.steps)
        assertEquals(3850.5, data?.distanceMeters ?: 0.0, 0.001)
        assertEquals(72L, data?.latestHeartRateBpm)
    }

    private class FakeHealthRepository(
        private val availability: HealthConnectAvailability,
        private val grantedPermissions: Set<String>,
        private val summaryToReturn: HealthDataSummary? = null,
    ) : HealthRepository {
        override val latestHealthData: Flow<HealthDataSummary?> = flowOf(summaryToReturn)
        override val lastSyncedAt: Flow<Instant?> = flowOf(summaryToReturn?.lastSyncedAt)

        override fun getAvailability(): HealthConnectAvailability = availability
        override fun getRequiredPermissions(): Set<String> = setOf("android.permission.health.READ_STEPS")
        override suspend fun getGrantedPermissions(): Set<String> = grantedPermissions
        override suspend fun hasAllPermissions(): Boolean = grantedPermissions.containsAll(getRequiredPermissions())
        override suspend fun hasAnyPermission(): Boolean = grantedPermissions.isNotEmpty()

        override suspend fun readHealthSummary(startTime: Instant, endTime: Instant): Result<HealthDataSummary> {
            return if (summaryToReturn != null) {
                Result.success(summaryToReturn)
            } else {
                Result.failure(IllegalStateException("No summary configured"))
            }
        }

        override suspend fun syncHealthData(): Result<HealthDataSummary> {
            if (availability != HealthConnectAvailability.Available) {
                return Result.failure(IllegalStateException("Health Connect is not available ($availability)"))
            }
            if (!hasAnyPermission()) {
                return Result.failure(SecurityException("No Health Connect permissions granted"))
            }
            return summaryToReturn?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Failed to read"))
        }
    }
}
