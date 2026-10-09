package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem

interface TrackResolver {
    suspend fun resolveSource(track: MediaItem, preferredQuality: dev.aurora.player.domain.audio.RequestedQuality): dev.aurora.player.domain.player.PlaybackSource?
    suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData?
}
