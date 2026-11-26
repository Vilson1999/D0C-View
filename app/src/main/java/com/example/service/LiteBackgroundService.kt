package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Policy constants and pure-Kotlin decision helpers for the 1-minute background auto-close
 * and on-demand lite background service lifecycle.
 */
object BackgroundLifecyclePolicy {
    const val BACKGROUND_TIMEOUT_MS: Long = 60_000L

    fun shouldStartLiteService(
        activeWorkCount: Int,
        isServiceRunning: Boolean,
        canStartService: Boolean
    ): Boolean = activeWorkCount > 0 && !isServiceRunning && canStartService

    fun shouldStopLiteService(
        activeWorkCount: Int,
        isServiceRunning: Boolean
    ): Boolean = activeWorkCount <= 0 && isServiceRunning

    fun shouldAutoCloseBackgroundApp(
        startedActivityCount: Int,
        elapsedInBackgroundMs: Long
    ): Boolean = startedActivityCount <= 0 && elapsedInBackgroundMs >= BACKGROUND_TIMEOUT_MS

    fun shouldStopOnForegroundClose(
        remainingActivityCount: Int,
        isChangingConfigurations: Boolean,
        isServiceRunning: Boolean
    ): Boolean = remainingActivityCount <= 0 && !isChangingConfigurations && isServiceRunning
}

/**
 * Ultra-lightweight, non-sticky background Service started only while active document work
 * (scanning, PDF conversion, cache maintenance) needs to finish briefly in background.
 * Stops immediately when work completes, when returning to foreground, when the foreground
 * task/activity closes, or at the 1-minute background cap.
 */
class LiteBackgroundService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hardStopRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        mainHandler.removeCallbacks(hardStopRunnable)
        mainHandler.postDelayed(hardStopRunnable, BackgroundLifecyclePolicy.BACKGROUND_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mainHandler.removeCallbacks(hardStopRunnable)
        isRunning = false
        onTaskRemovedCallback?.invoke()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(hardStopRunnable)
        isRunning = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var onTaskRemovedCallback: (() -> Unit)? = null

        fun startIfNeeded(context: Context, activeWorkCount: Int, canStart: Boolean) {
            if (!BackgroundLifecyclePolicy.shouldStartLiteService(activeWorkCount, isRunning, canStart)) {
                return
            }
            val appCtx = context.applicationContext ?: context
            runCatching {
                appCtx.startService(Intent(appCtx, LiteBackgroundService::class.java))
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext ?: context
            mainStop(appCtx)
        }

        private fun mainStop(appCtx: Context) {
            isRunning = false
            runCatching {
                appCtx.stopService(Intent(appCtx, LiteBackgroundService::class.java))
            }
        }
    }
}
