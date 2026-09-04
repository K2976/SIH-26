package com.fitforge.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FitForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initCrashReporting()
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
