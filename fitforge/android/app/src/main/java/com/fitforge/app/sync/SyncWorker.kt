package com.fitforge.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.fitforge.app.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Pushes pending diary rows to the backend. Enqueued after every local write
 * and once at app start; WorkManager's own durable queue (not this worker)
 * is what makes pending work survive a force quit and resume once
 * connectivity returns.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        if (syncRepository.syncPending()) Result.success() else Result.retry()
    } catch (e: IOException) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sync-pending-diary-rows"

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            // APPEND_OR_REPLACE, not KEEP: if a sync is already RUNNING when this
            // fires, rows added after it started need a run of their own — KEEP
            // would drop this request instead of chaining one after the current run.
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
