package dev.aurora.player.ui.accessibility

import android.content.Context
import android.provider.Settings

/**
 * Accessibility utility functions.
 *
 * Centralized checks for platform accessibility states that affect
 * rendering, motion, and haptic behavior. Use these to adapt UI
 * behavior without requiring runtime permission.
 */
object AccessibilityUtils {

    /** True when the system animator duration scale is 0 (reduced motion). */
    fun isReducedMotionEnabled(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f,
        )
        return scale == 0f
    }

    /** True when the system font scale is greater than 1.0 (enlarged text). */
    fun isLargeTextEnabled(context: Context): Boolean {
        val fontScale = context.resources.configuration.fontScale
        return fontScale > 1.0f
    }

    /** Returns the current system font scale (1.0 = default). */
    fun fontScale(context: Context): Float {
        return context.resources.configuration.fontScale
    }
}
