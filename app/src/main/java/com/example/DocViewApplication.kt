package com.example

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import com.example.service.BackgroundLifecyclePolicy
import com.example.service.LiteBackgroundService
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks foreground/background transitions and active document work:
 * - When switching to another app without closing: trims heavy memory caches, pauses speculative
 *   background prewarming, and runs [LiteBackgroundService] very low until work completes or timeout.
 * - When closed from the mobile Recent menu: stops [LiteBackgroundService], cancels all pending
 *   work, finishes activities, and terminates the process cleanly so no background service remains.
 */
class DocViewApplication : Application(), Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val liveActivities = ArrayList<WeakReference<Activity>>()

    @Volatile
    private var startedActivityCount = 0

    @Volatile
    private var backgroundEnteredAtMs = 0L

    @Volatile
    private var isTaskWatcherBound = false

    private val taskWatcherConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) {
            isTaskWatcherBound = false
        }
    }

    private val backgroundCloseRunnable = Runnable {
        if (startedActivityCount > 0) return@Runnable
        val elapsed = System.currentTimeMillis() - backgroundEnteredAtMs
        if (!BackgroundLifecyclePolicy.shouldAutoCloseBackgroundApp(startedActivityCount, elapsed)) {
            return@Runnable
        }
        performBackgroundIdleStop()
    }

    private val processExitRunnable = Runnable {
        if (startedActivityCount <= 0) {
            Process.killProcess(Process.myPid())
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LiteBackgroundService.onTaskRemovedCallback = {
            performTaskRemovedCleanup()
        }
        registerActivityLifecycleCallbacks(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN || startedActivityCount <= 0) {
            lowBackgroundListeners.forEach { listener ->
                runCatching { listener() }
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        synchronized(liveActivities) {
            liveActivities.removeAll { it.get() == null || it.get() === activity }
            liveActivities.add(WeakReference(activity))
        }
        bindTaskWatcher()
    }

    override fun onActivityStarted(activity: Activity) {
        val wasInBackground = startedActivityCount == 0
        startedActivityCount++
        bindTaskWatcher()
        if (wasInBackground) {
            cancelScheduledClose()
            backgroundEnteredAtMs = 0L
            // App is in foreground now; stop background service immediately.
            LiteBackgroundService.stop(this)
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) {
        // Start LiteBackgroundService while still foreground-eligible if active work must finish
        // when switching directly to another app.
        if (!activity.isChangingConfigurations && startedActivityCount <= 1 && activeWorkCount.get() > 0) {
            LiteBackgroundService.startIfNeeded(
                context = this,
                activeWorkCount = activeWorkCount.get(),
                canStart = true
            )
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (BackgroundLifecyclePolicy.shouldEnterLowBackgroundMode(startedActivityCount, activity.isChangingConfigurations)) {
            backgroundEnteredAtMs = System.currentTimeMillis()
            lowBackgroundListeners.forEach { listener ->
                runCatching { listener() }
            }
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
        val remainingCount = synchronized(liveActivities) {
            liveActivities.removeAll { it.get() == null || it.get() === activity }
            liveActivities.size
        }
        if (!activity.isChangingConfigurations && remainingCount <= 0) {
            // Last activity closed cleanly: stop background service & pending work completely.
            cancelScheduledClose()
            activeWorkCount.set(0)
            unbindTaskWatcher()
            LiteBackgroundService.stop(this)
            closeListeners.forEach { listener ->
                runCatching { listener() }
            }
        }
    }

    private fun bindTaskWatcher() {
        if (isTaskWatcherBound) return
        val intent = Intent(this, LiteBackgroundService::class.java)
        isTaskWatcherBound = runCatching {
            bindService(intent, taskWatcherConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
    }

    private fun unbindTaskWatcher() {
        if (!isTaskWatcherBound) return
        isTaskWatcherBound = false
        runCatching { unbindService(taskWatcherConnection) }
    }

    private fun performTaskRemovedCleanup() {
        cancelScheduledClose()
        activeWorkCount.set(0)
        unbindTaskWatcher()
        LiteBackgroundService.stop(this)
        lowBackgroundListeners.forEach { listener ->
            runCatching { listener() }
        }
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
        mainHandler.postDelayed(processExitRunnable, 120L)
    }

    private fun scheduleBackgroundClose() {
        cancelScheduledClose()
        mainHandler.postDelayed(backgroundCloseRunnable, BackgroundLifecyclePolicy.BACKGROUND_TIMEOUT_MS)
    }

    private fun cancelScheduledClose() {
        mainHandler.removeCallbacks(backgroundCloseRunnable)
        mainHandler.removeCallbacks(processExitRunnable)
    }

    private fun performBackgroundIdleStop() {
        // When user switched apps without closing from Recents, stop background service & active jobs
        // after timeout so zero background services remain running while keeping the app task intact.
        activeWorkCount.set(0)
        unbindTaskWatcher()
        LiteBackgroundService.stop(this)
        lowBackgroundListeners.forEach { listener ->
            runCatching { listener() }
        }
        closeListeners.forEach { listener ->
            runCatching { listener() }
        }
    }

    companion object {
        @Volatile
        private var instance: DocViewApplication? = null
        private val activeWorkCount = AtomicInteger(0)
        private val closeListeners = CopyOnWriteArraySet<() -> Unit>()
        private val lowBackgroundListeners = CopyOnWriteArraySet<() -> Unit>()

        fun beginBackgroundWork(context: Context) {
            activeWorkCount.incrementAndGet()
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

        fun registerLowBackgroundListener(listener: () -> Unit) {
            lowBackgroundListeners.add(listener)
        }

        fun unregisterLowBackgroundListener(listener: () -> Unit) {
            lowBackgroundListeners.remove(listener)
        }
    }
}

