package dev.aurora.player.app

import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.player.youtube.InnerTubeClient
import dev.aurora.player.data.player.youtube.YouTubeStreamExtractor
import dev.aurora.player.domain.player.providers.LocalPlaybackSourceProvider
import dev.aurora.player.domain.player.providers.PlaybackSourceProvider
import dev.aurora.player.domain.player.providers.YouTubePlaybackSourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuroraTrackResolver(
    private val localDao: LocalLibraryDao,
    private val innerTubeClient: InnerTubeClient,
) : TrackResolver {
    private val streamExtractor = YouTubeStreamExtractor(innerTubeClient)

    override val sourceProviders: List<PlaybackSourceProvider> = listOf(
        LocalPlaybackSourceProvider(localDao),
        YouTubePlaybackSourceProvider(streamExtractor),
    )

    override suspend fun resolveSource(track: dev.aurora.player.domain.models.MediaItem, preferredQuality: dev.aurora.player.domain.audio.RequestedQuality): dev.aurora.player.domain.player.PlaybackSource? = withContext(Dispatchers.IO) {
        val provider = sourceProviders.find { provider ->
            if (provider is YouTubePlaybackSourceProvider) {
                track.id.startsWith("youtube:")
            } else if (provider is LocalPlaybackSourceProvider) {
                !track.id.startsWith("youtube:")
            } else {
                false
            }
        }

        provider?.resolve(track, preferredQuality)
    }

    override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = withContext(Dispatchers.IO) {
        if (trackId.startsWith("youtube:")) {
            return@withContext null
        }
        localDao.getTrackLoudness(trackId)
    }
}
