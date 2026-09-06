package com.fitforge.app.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitforge.app.domain.model.HealthConnectAvailability
import com.fitforge.app.domain.repository.HealthRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val TAG = "HealthConnectSyncWorker"
private const val MAX_RETRIES = 3

/**
 * Hilt EntryPoint to allow [HealthConnectSyncWorker] to access injected singletons
 * without requiring custom WorkerFactory or disabling AndroidX Startup WorkManagerInitializer.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HealthSyncEntryPoint {
    fun healthRepository(): HealthRepository
    fun healthConnectSyncScheduler(): HealthConnectSyncScheduler
}

/**
 * Periodic WorkManager worker that synchronizes Health Connect data approximately every hour.
 * Reuses the existing [HealthRepository.syncHealthData] logic without duplicating queries.
 */
class HealthConnectSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            HealthSyncEntryPoint::class.java,
        )
    }

    private val healthRepository: HealthRepository
        get() = entryPoint.healthRepository()

    private val syncScheduler: HealthConnectSyncScheduler
        get() = entryPoint.healthConnectSyncScheduler()

    override suspend fun doWork(): Result {
        Log.i(TAG, "HealthConnectSyncWorker started (attempt #$runAttemptCount)")

        // 1. Check Health Connect availability on this device
        val availability = healthRepository.getAvailability()
        if (availability != HealthConnectAvailability.Available) {
            Log.w(TAG, "Health Connect is not available ($availability). Cancelling worker.")
            syncScheduler.cancelPeriodicSync()
            return Result.success()
        }

        // 2. Check if required permissions are still granted
        val hasAnyPermission = healthRepository.hasAnyPermission()
        if (!hasAnyPermission) {
            Log.w(TAG, "No Health Connect permissions are granted. Stopping gracefully without retrying.")
            syncScheduler.cancelPeriodicSync()
            return Result.success()
        }

        // 3. Execute synchronization using the unified repository logic
        val syncResult = healthRepository.syncHealthData()

        return syncResult.fold(
            onSuccess = { summary ->
                Log.i(TAG, "HealthConnectSyncWorker completed successfully at ${summary.lastSyncedAt}")
                Result.success()
            },
            onFailure = { error ->
                Log.e(TAG, "HealthConnectSyncWorker failed with error", error)
                when (error) {
                    is SecurityException -> {
                        // Permissions were revoked or security policy rejected access.
                        // Permanent failure: cancel periodic worker and exit cleanly without endless retries.
                        Log.w(TAG, "Permissions revoked or unauthorized. Cancelling periodic sync.")
                        syncScheduler.cancelPeriodicSync()
                        Result.success()
                    }
                    is IllegalStateException -> {
                        // Permanent or unrecoverable client state; do not retry
                        Log.w(TAG, "Illegal state encountered. Not retrying.")
                        Result.success()
                    }
                    else -> {
                        // Transient failures (e.g., IO, RemoteException, temporary service disconnect)
                        if (runAttemptCount < MAX_RETRIES) {
                            Log.w(TAG, "Scheduling retry for transient error (attempt #$runAttemptCount of $MAX_RETRIES)")
                            Result.retry()
                        } else {
                            Log.e(TAG, "HealthConnectSyncWorker exceeded maximum retry attempts ($MAX_RETRIES).")
                            Result.failure()
                        }
                    }
                }
            },
        )
    }
}
