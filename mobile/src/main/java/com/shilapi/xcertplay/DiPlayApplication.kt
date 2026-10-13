package com.shilapi.xcertplay

import android.content.Context
import android.util.Log
import androidx.multidex.MultiDexApplication

class DiPlayApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(this, thread, throwable)
            } catch (ignored: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val TAG = "DiPlayApplication"

        fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
            Log.e(TAG, "FATAL EXCEPTION", throwable)
            // Redacted, bounded and readable by the exported report even after a restart.
            CrashDiagnostics.recordInAppDirectories(context, thread, throwable)
        }
    }
}
