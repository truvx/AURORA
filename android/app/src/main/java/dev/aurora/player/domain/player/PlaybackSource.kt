package dev.aurora.player.domain.player

import dev.aurora.player.domain.audio.RequestedQuality

data class PlaybackSource(
    val uri: String,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val durationMs: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val expirationTimeMs: Long? = null,
    val quality: RequestedQuality = RequestedQuality.Auto
)
