package dev.aurora.player.app

import dev.aurora.player.domain.models.MediaItem

interface TrackResolver {
    suspend fun resolveUri(trackId: String): String?
    suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData?
}
