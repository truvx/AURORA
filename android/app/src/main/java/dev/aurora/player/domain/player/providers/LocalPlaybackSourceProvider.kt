package dev.aurora.player.domain.player.providers

import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.domain.audio.RequestedQuality
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlaybackSource

class LocalPlaybackSourceProvider(
    private val localDao: LocalLibraryDao
) : PlaybackSourceProvider {
    override val id: String = "local"

    override suspend fun resolve(track: MediaItem, quality: RequestedQuality): PlaybackSource? {
        // Resolve the real content URI from the library rather than using the track id
        // as the URI. The id is stable provider identity; the URI is what Media3 opens.
        // No entity means this track isn't in the library and cannot be played.
        val fileEntity = localDao.getLocalFileForMedia(track.id) ?: return null
        return PlaybackSource(
            uri = fileEntity.uri,
            mimeType = null, // Media3 derives this from the file extension
            quality = quality
        )
    }
}
