package com.example.model

import android.content.ContentResolver
import android.content.pm.ActivityInfo
import android.provider.Settings

/**
 * Pure orientation policy helpers for respecting system auto-rotate
 * while allowing explicit portrait/landscape toggling from the reader topbar.
 */
object ScreenOrientationPolicy {

    fun isSystemAutoRotateEnabled(contentResolver: ContentResolver): Boolean {
        return runCatching {
            Settings.System.getInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            ) == 1
        }.getOrDefault(false)
    }

    fun nextManualOrientation(isCurrentlyLandscape: Boolean): Int {
        return if (isCurrentlyLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    fun resolveSensorFollowOrientation(): Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
