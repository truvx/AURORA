package dev.aurora.player.domain.models

enum class ProviderCapability {
    SEARCH,
    METADATA,
    ARTWORK,
    PLAYBACK,
    QUEUE,
    RECOMMENDATIONS,
    LYRICS,
    DOWNLOAD,
    OFFLINE_PLAYBACK,
    QUALITY_SELECTION,
    BACKGROUND_PLAYBACK
}

data class ProviderCapabilities(
    val supportedCapabilities: Set<ProviderCapability>
) {
    fun supports(capability: ProviderCapability): Boolean = supportedCapabilities.contains(capability)
}

enum class ProviderKind {
    YOUTUBE,
    LOCAL
}
