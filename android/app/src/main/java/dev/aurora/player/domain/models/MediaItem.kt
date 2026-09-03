package dev.aurora.player.domain.models

data class MediaItem(
    val id: String, // Stable source ID
    val provider: ProviderKind,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val technicalMetadata: TrackTechnicalMetadata? = null,
    val isAvailable: Boolean = true
)

data class TrackTechnicalMetadata(
    val codec: String?,
    val container: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val bitDepth: Int?,
    val durationMs: Long?,
    val isLossless: Boolean?
)
