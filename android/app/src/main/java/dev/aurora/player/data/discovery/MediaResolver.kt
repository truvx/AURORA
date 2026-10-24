package dev.aurora.player.data.discovery

import dev.aurora.player.domain.auth.YouTubeCredential
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.domain.discovery.ArtistAffinity
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider
import kotlinx.coroutines.CancellationException

/**
 * Turns stored media ids back into displayable tracks, for every provider.
 *
 * History and favorites store only an id. Local ids resolve against the library database;
 * YouTube ids have no local row, so they are looked up with `videos.list` - batched fifty
 * to a call, one quota unit per call - and remembered, so a track resolved once is never
 * looked up again for a month.
 */
class MediaResolver(
    private val local: MusicProvider,
    private val api: YouTubeDataApi,
    /*
     * A lookup rather than a value: the key is entered in Settings and kept in the
     * Keystore, so it can arrive, change, or be cleared long after this object was built.
     */
    private val credentials: suspend () -> YouTubeCredential,
    private val cache: DiscoveryCache
) {
    suspend fun resolve(ids: List<String>): Map<String, MediaItem> {
        val resolved = LinkedHashMap<String, MediaItem>()
        val missingYouTube = mutableListOf<String>()

        for (id in ids.distinct()) {
            if (id.startsWith(YOUTUBE_PREFIX)) {
                val cached = cache.read(key(id), CACHE_MS, CachedMedia.serializer())
                if (cached != null) resolved[id] = cached.toMediaItem() else missingYouTube += id
            } else {
                local.resolveMetadata(id).getOrNull()?.let { resolved[id] = it }
            }
        }

        val credential = credentials()
        val apiKey = credential.keyParameter
        if (missingYouTube.isNotEmpty() && credential !is YouTubeCredential.None) {
            for (batch in missingYouTube.chunked(50)) {
                val response = try {
                    api.getVideos(
                        part = "snippet,status",
                        id = batch.joinToString(",") { it.removePrefix(YOUTUBE_PREFIX) },
                        apiKey = apiKey
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Offline or quota: these stay unresolved this time and are retried on
                    // the next load. The rest of the list still shows.
                    continue
                }
                for (video in response.items) {
                    val item = MediaItem(
                        id = YOUTUBE_PREFIX + video.id,
                        provider = ProviderKind.YOUTUBE,
                        title = video.snippet.title,
                        artist = ArtistAffinity.displayName(video.snippet.channelTitle),
                        album = null,
                        artworkUri = video.snippet.thumbnails.best(),
                        // Removed or no longer embeddable videos stay visible in history but
                        // are marked, so they are not presented as playable.
                        isAvailable = video.status?.embeddable != false
                    )
                    resolved[item.id] = item
                    cache.write(key(item.id), CachedMedia.of(item), CachedMedia.serializer())
                }
            }
        }

        // Preserve the caller's order - it is usually recency.
        return ids.distinct().mapNotNull { id -> resolved[id]?.let { id to it } }.toMap(LinkedHashMap())
    }

    private fun key(id: String) = "media:$id"

    private companion object {
        const val YOUTUBE_PREFIX = "youtube:"
        const val CACHE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
