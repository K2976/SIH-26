package com.fitforge.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.fitforge.app.data.repository.ReferenceRepository
import com.fitforge.app.sync.ReferenceSyncWorker
import com.fitforge.app.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FitForgeApplication : Application() {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var referenceRepository: ReferenceRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initCrashReporting()

        // Manual init, not Configuration.Provider: the manifest's default
        // androidx.startup initializer is disabled (see AndroidManifest.xml)
        // because it runs before Hilt injects workerFactory above.
        WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(workerFactory).build())

        // Seed offline before anything else — search/logging must work in
        // airplane mode on a fresh install, before any network call.
        applicationScope.launch { referenceRepository.seedFromAssetsIfEmpty() }

        val workManager = WorkManager.getInstance(this)
        ReferenceSyncWorker.enqueuePeriodic(workManager)
        SyncWorker.enqueue(workManager)
    }

    // ponytail: logs uncaught crashes to Logcat before the process dies.
    // Swap for Firebase Crashlytics once there's a Firebase project to
    // report to (needs google-services.json, which no environment here
    // can provision).
    private fun initCrashReporting() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FitForgeCrash", "Uncaught exception on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
