package dev.aurora.player.domain.providers

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderKind

interface MusicProvider {
    val id: ProviderKind
    val capabilities: ProviderCapabilities

    suspend fun search(query: String): Result<List<MediaItem>>
    suspend fun resolveMetadata(trackId: String): Result<MediaItem>
}
