@file:Suppress("SwallowedException")

package com.fitforge.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitforge.app.data.repository.ReferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Daily check of the reference manifest, pulling exercise deltas since the last known version. */
@HiltWorker
class ReferenceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val referenceRepository: ReferenceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        referenceRepository.syncDelta()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "reference-delta-sync"

        fun enqueuePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<ReferenceSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
