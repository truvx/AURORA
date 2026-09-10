package dev.aurora.player.domain.audio

import dev.aurora.player.domain.models.ProviderKind

enum class CapabilityState {
    SUPPORTED, AVAILABLE, ACTIVE, UNAVAILABLE, UNSUPPORTED, NOT_APPLICABLE
}

data class ProviderAudioCapabilities(
    val provider: ProviderKind,
    val normalization: CapabilityState,
    val crossfade: CapabilityState,
    val eq: CapabilityState,
    val limiter: CapabilityState
)
