package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process

/**
 * Policy constants and pure-Kotlin decision helpers for very-low background service mode
 * when switching apps and full shutdown when closed from the mobile Recent menu.
 */
object BackgroundLifecyclePolicy {
    const val BACKGROUND_TIMEOUT_MS: Long = 60_000L
    const val LOW_BACKGROUND_THREAD_PRIORITY: Int = Process.THREAD_PRIORITY_LOWEST

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

    fun shouldEnterLowBackgroundMode(
        startedActivityCount: Int,
        isChangingConfigurations: Boolean
    ): Boolean = startedActivityCount <= 0 && !isChangingConfigurations

    fun shouldFullyStopOnTaskRemoved(isTaskRemoved: Boolean): Boolean = isTaskRemoved
}

/**
 * Ultra-lightweight, non-sticky Service:
 * - Bound lightly to live tasks so [onTaskRemoved] fires reliably when swiped from Recent menu.
 * - Started on-demand when switching away to another app with active work, running at lowest priority.
 * - Stops immediately and shuts down completely when closed from the mobile Recent menu.
 */
class LiteBackgroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): LiteBackgroundService = this@LiteBackgroundService
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hardStopRunnable = Runnable {
        isRunning = false
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        if (!isRunning) {
            stopSelf()
        }
        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        mainHandler.removeCallbacks(hardStopRunnable)
        mainHandler.postDelayed(hardStopRunnable, BackgroundLifecyclePolicy.BACKGROUND_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mainHandler.removeCallbacksAndMessages(null)
        isRunning = false
        onTaskRemovedCallback?.invoke()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
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

