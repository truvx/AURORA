package dev.aurora.player.ui.accessibility

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Platform accessibility preferences that change how the UI renders.
 *
 * Read once near the root and provided down the tree, so individual components never touch
 * Settings directly and every surface reacts to the same values.
 */
@Immutable
data class AccessibilityPreferences(
    /** Travel, parallax, and bounce collapse to immediate state changes. */
    val reducedMotion: Boolean = false,
    /** Glass is replaced with opaque surfaces. */
    val reducedTransparency: Boolean = false,
    /** A screen reader or similar service is driving the UI. */
    val screenReaderEnabled: Boolean = false,
    /** System font scale; 1.0 is default. */
    val fontScale: Float = 1.0f
)

val LocalAccessibilityPreferences = compositionLocalOf { AccessibilityPreferences() }

/**
 * Resolves the current platform preferences and keeps them up to date while composed.
 *
 * Android exposes no direct "reduce transparency" toggle the way iOS does. The closest
 * honest signals are the user disabling animations and turning on high-contrast text, both
 * of which indicate a preference for plainer, higher-contrast surfaces, so either one drops
 * the glass effect rather than guessing.
 */
@Composable
fun rememberAccessibilityPreferences(): AccessibilityPreferences {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current

    var screenReaderEnabled by remember { mutableStateOf(isScreenReaderEnabled(context)) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            screenReaderEnabled = enabled
        }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }

    val reducedMotion = animationsDisabled(context)

    return AccessibilityPreferences(
        reducedMotion = reducedMotion,
        reducedTransparency = reducedMotion || highContrastTextEnabled(context),
        screenReaderEnabled = screenReaderEnabled,
        fontScale = configuration.fontScale
    )
}

/** True when the user has turned animations off in system or developer settings. */
private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1.0f
    ) == 0f

private fun isScreenReaderEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager ?: return false
    return manager.isEnabled && manager.isTouchExplorationEnabled
}

/**
 * High-contrast text is a hidden setting with no public accessor, so it is read by key and
 * treated as absent when unavailable rather than assumed.
 */
private fun highContrastTextEnabled(context: Context): Boolean =
    runCatching {
        Settings.Secure.getInt(
            context.contentResolver,
            "high_text_contrast_enabled",
            0
        ) == 1
    }.getOrDefault(false)
