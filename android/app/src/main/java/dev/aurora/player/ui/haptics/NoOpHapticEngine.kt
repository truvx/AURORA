package dev.aurora.player.ui.haptics

/**
 * No-op haptic engine for devices without vibration support,
 * testing, or when haptics are disabled by user preference.
 */
class NoOpHapticEngine : HapticEngine {
    override val capabilityTier: HapticCapabilityTier = HapticCapabilityTier.None
    override val isEnabled: Boolean = false
    override fun fire(event: HapticEvent) { /* intentional no-op */ }
}
