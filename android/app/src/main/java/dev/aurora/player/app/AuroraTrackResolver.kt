package dev.aurora.player.app

import dev.aurora.player.data.db.LocalLibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuroraTrackResolver(
    private val localDao: LocalLibraryDao
) : TrackResolver {
    override suspend fun resolveSource(track: dev.aurora.player.domain.models.MediaItem, preferredQuality: dev.aurora.player.domain.audio.RequestedQuality): dev.aurora.player.domain.player.PlaybackSource? = withContext(Dispatchers.IO) {
        if (track.id.startsWith("youtube:")) {
            return@withContext dev.aurora.player.domain.player.PlaybackSource(
                uri = track.id.removePrefix("youtube:"),
                mimeType = "application/vnd.youtube.music",
                quality = dev.aurora.player.domain.audio.RequestedQuality.Auto
            )
        }
        // Find the actual playable source URI for the given track ID.
        val fileEntity = localDao.getLocalFileForMedia(track.id)
        fileEntity?.uri?.let { uri ->
            dev.aurora.player.domain.player.PlaybackSource(
                uri = uri,
                quality = dev.aurora.player.domain.audio.RequestedQuality.Lossless
            )
        }
    }

    override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = withContext(Dispatchers.IO) {
        if (trackId.startsWith("youtube:")) {
            return@withContext null
        }
        localDao.getTrackLoudness(trackId)
    }
}
