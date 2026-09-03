package dev.aurora.player.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Reduced-motion utility.
 *
 * Checks the platform ANIMATOR_DURATION_SCALE accessibility setting.
 * When reduced motion is enabled, travel, parallax, bounce, shared-element
 * flight, and animated blur should be replaced with immediate or short
 * opacity/state changes per docs/AURORA_MOTION_SPEC.md.
 */
@Composable
@ReadOnlyComposable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val durationScale = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1.0f,
    )
    return durationScale == 0f
}
