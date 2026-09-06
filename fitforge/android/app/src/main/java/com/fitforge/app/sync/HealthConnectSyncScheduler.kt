package com.fitforge.app.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HealthSyncScheduler"

/**
 * Unique name for the periodic Health Connect background synchronization work.
 */
const val HEALTH_CONNECT_SYNC_WORK_NAME = "fitforge_health_connect_sync"

/**
 * Manages scheduling and cancellation of the periodic Health Connect background sync worker.
 */
@Singleton
class HealthConnectSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    /**
     * Schedules a unique periodic worker to sync Health Connect data approximately once per hour.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] to prevent duplicate workers if one is already scheduled.
     */
    fun schedulePeriodicSync() {
        Log.d(TAG, "Enqueuing unique periodic Health Connect sync worker (~1 hour interval)")
        val syncRequest = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            HEALTH_CONNECT_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest,
        )
    }

    /**
     * Cancels any currently scheduled Health Connect background synchronization work.
     */
    fun cancelPeriodicSync() {
        Log.d(TAG, "Cancelling unique Health Connect sync worker ($HEALTH_CONNECT_SYNC_WORK_NAME)")
        workManager.cancelUniqueWork(HEALTH_CONNECT_SYNC_WORK_NAME)
    }

    /**
     * Observes whether the periodic sync worker is currently enqueued or running.
     */
    fun isSyncScheduled(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(HEALTH_CONNECT_SYNC_WORK_NAME).map { workInfos ->
            workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }
}
