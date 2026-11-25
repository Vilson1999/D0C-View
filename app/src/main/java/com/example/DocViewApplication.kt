package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.example.service.BackgroundLifecyclePolicy
import com.example.service.LiteBackgroundService
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks foreground/background transitions and active document work.
 * - Starts [LiteBackgroundService] only when work is actively needed.
 * - Closes the app cleanly if it remains in the background for 1 minute (60 seconds).
 */
class DocViewApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveActivities = ArrayList<WeakReference<Activity>>()

    @Volatile
    private var startedActivityCount = 0

    @Volatile
    private var backgroundEnteredAtMs = 0L

    private val backgroundCloseRunnable = Runnable {
        if (startedActivityCount > 0) return@Runnable
        val elapsed = System.currentTimeMillis() - backgroundEnteredAtMs
        if (!BackgroundLifecyclePolicy.shouldAutoCloseBackgroundApp(startedActivityCount, elapsed)) {
            return@Runnable
        }
        performBackgroundAutoClose()
    }

    private val processExitRunnable = Runnable {
        if (startedActivityCount <= 0) {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        synchronized(liveActivities) {
            liveActivities.removeAll { it.get() == null || it.get() === activity }
            liveActivities.add(WeakReference(activity))
        }
    }

    override fun onActivityStarted(activity: Activity) {
        val wasInBackground = startedActivityCount == 0
        startedActivityCount++
        if (wasInBackground) {
            cancelScheduledClose()
            backgroundEnteredAtMs = 0L
            if (activeWorkCount.get() > 0) {
                LiteBackgroundService.startIfNeeded(
                    context = this,
                    activeWorkCount = activeWorkCount.get(),
                    canStart = true
                )
            }
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
            backgroundEnteredAtMs = System.currentTimeMillis()
            if (activeWorkCount.get() > 0) {
                LiteBackgroundService.startIfNeeded(
                    context = this,
                    activeWorkCount = activeWorkCount.get(),
                    canStart = true
                )
            }
            scheduleBackgroundClose()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        synchronized(liveActivities) {
            liveActivities.removeAll { it.get() == null || it.get() === activity }
        }
    }

    private fun scheduleBackgroundClose() {
        cancelScheduledClose()
        mainHandler.postDelayed(backgroundCloseRunnable, BackgroundLifecyclePolicy.BACKGROUND_TIMEOUT_MS)
    }

    private fun cancelScheduledClose() {
        mainHandler.removeCallbacks(backgroundCloseRunnable)
        mainHandler.removeCallbacks(processExitRunnable)
    }

    private fun performBackgroundAutoClose() {
        LiteBackgroundService.stop(this)
        closeListeners.forEach { listener ->
            runCatching { listener() }
        }
        val snapshot = synchronized(liveActivities) {
            liveActivities.mapNotNull { it.get() }
        }
        snapshot.forEach { activity ->
            if (!activity.isFinishing && !activity.isDestroyed) {
                runCatching { activity.finishAndRemoveTask() }
            }
        }
        mainHandler.postDelayed(processExitRunnable, 250L)
    }

    companion object {
        @Volatile
        private var instance: DocViewApplication? = null
        private val activeWorkCount = AtomicInteger(0)
        private val closeListeners = CopyOnWriteArraySet<() -> Unit>()

        fun beginBackgroundWork(context: Context) {
            val count = activeWorkCount.incrementAndGet()
            val app = instance
            val canStart = app == null || app.startedActivityCount > 0
            LiteBackgroundService.startIfNeeded(context, count, canStart)
        }

        fun endBackgroundWork(context: Context) {
            val count = activeWorkCount.updateAndGet { prev -> (prev - 1).coerceAtLeast(0) }
            if (BackgroundLifecyclePolicy.shouldStopLiteService(count, LiteBackgroundService.isRunning)) {
                LiteBackgroundService.stop(context)
            }
        }

        fun registerBackgroundCloseListener(listener: () -> Unit) {
            closeListeners.add(listener)
        }

        fun unregisterBackgroundCloseListener(listener: () -> Unit) {
            closeListeners.remove(listener)
        }
    }
}
