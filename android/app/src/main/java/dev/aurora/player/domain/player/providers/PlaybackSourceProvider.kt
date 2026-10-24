package dev.aurora.player.domain.player.providers

import dev.aurora.player.domain.audio.RequestedQuality
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlaybackSource

interface PlaybackSourceProvider {
    val id: String
    suspend fun resolve(track: MediaItem, quality: RequestedQuality): PlaybackSource?
}
