package dev.aurora.player.ui.haptics

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tiers of haptic capability to allow for graceful degradation.
 */
enum class HapticCapabilityTier {
    None,
    Basic,
    Rich
}

/**
 * Centralized boundary for all haptic feedback in the application.
 */
interface HapticEngine {
    val capabilityTier: HapticCapabilityTier
    val isEnabled: Boolean
    fun fire(event: HapticEvent)
}

val LocalHapticEngine = staticCompositionLocalOf<HapticEngine> { NoOpHapticEngine() }
