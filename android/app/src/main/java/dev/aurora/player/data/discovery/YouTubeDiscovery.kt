package dev.aurora.player.data.discovery

import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.domain.discovery.ArtistAffinity
import dev.aurora.player.domain.discovery.ChannelMatcher
import dev.aurora.player.domain.discovery.Release
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant

/** Why a discovery surface has nothing to show, so the UI can say which. */
class DiscoveryUnavailable(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NO_API_KEY, OFFLINE_OR_QUOTA }
}

/**
 * Recent releases from the artists a listener plays, read from their YouTube channels.
 *
 * Cost is the design constraint. Finding a channel is a search (100 quota units), so each
 * artist's channel is found once and remembered; reading its uploads afterwards is two list
 * calls (2 units). The finished list is cached for six hours on top of that.
 */
class LatestReleasesRepository(
    private val api: YouTubeDataApi,
    /*
     * A lookup rather than a value: the key is entered in Settings and kept in the
     * Keystore, so it can arrive, change, or be cleared long after this object was built.
     */
    private val apiKeyProvider: () -> String,
    private val cache: DiscoveryCache,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Serializable
    private data class CachedRelease(val media: CachedMedia, val artist: String, val publishedAtMs: Long)

    @Serializable
    private data class ChannelRecord(val channelId: String?, val uploadsPlaylistId: String?)

    suspend fun latest(artists: List<String>): Result<List<Release>> {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) return Result.failure(DiscoveryUnavailable(DiscoveryUnavailable.Reason.NO_API_KEY))
        if (artists.isEmpty()) return Result.success(emptyList())

        val listKey = "releases:" + artists.joinToString("|") { it.lowercase() }
        cache.read(listKey, LIST_TTL_MS, ListSerializer(CachedRelease.serializer()))?.let { cached ->
            return Result.success(cached.map { Release(it.media.toMediaItem(), it.artist, it.publishedAtMs) })
        }

        val releases = mutableListOf<Release>()
        var anyReached = false
        for (artist in artists) {
            try {
                val playlist = uploadsPlaylistFor(artist, apiKey)
                anyReached = true
                if (playlist == null) continue
                val items = api.getPlaylistItems(playlistId = playlist, maxResults = PER_ARTIST, apiKey = apiKey).items
                for (entry in items) {
                    val videoId = entry.contentDetails?.videoId ?: entry.snippet.resourceId?.videoId ?: continue
                    val published = parseInstant(entry.contentDetails?.videoPublishedAt ?: entry.snippet.publishedAt)
                        ?: continue
                    if (now() - published > WINDOW_MS) continue
                    // Private and deleted uploads still appear in the playlist with a stock title.
                    if (entry.snippet.title == "Private video" || entry.snippet.title == "Deleted video") continue
                    releases += Release(
                        item = MediaItem(
                            id = "youtube:$videoId",
                            provider = ProviderKind.YOUTUBE,
                            title = entry.snippet.title,
                            artist = artist,
                            album = null,
                            artworkUri = entry.snippet.thumbnails.best()
                        ),
                        artist = artist,
                        publishedAtMs = published
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // One artist failing (renamed channel, transient error) must not hide the rest.
            }
        }

        if (!anyReached) return Result.failure(DiscoveryUnavailable(DiscoveryUnavailable.Reason.OFFLINE_OR_QUOTA))

        val sorted = releases.distinctBy { it.item.id }.sortedByDescending { it.publishedAtMs }
        cache.write(
            listKey,
            sorted.map { CachedRelease(CachedMedia.of(it.item), it.artist, it.publishedAtMs) },
            ListSerializer(CachedRelease.serializer())
        )
        return Result.success(sorted)
    }

    /** The artist's uploads playlist, found once and remembered - including "no channel". */
    private suspend fun uploadsPlaylistFor(artist: String, apiKey: String): String? {
        val key = "channel:" + artist.lowercase()
        cache.read(key, CHANNEL_TTL_MS, ChannelRecord.serializer())?.let { return it.uploadsPlaylistId }

        val candidates = api.searchChannels(query = "$artist - Topic", apiKey = apiKey).items.mapNotNull { result ->
            val id = result.id.channelId ?: return@mapNotNull null
            ChannelMatcher.Candidate(id, result.snippet.title)
        }
        val channelId = ChannelMatcher.pick(artist, candidates)
        val uploads = channelId?.let {
            api.getChannels(id = it, apiKey = apiKey).items.firstOrNull()?.contentDetails?.relatedPlaylists?.uploads
        }
        // A miss is remembered too: re-searching every load would spend 100 units each time
        // to learn the same thing.
        cache.write(key, ChannelRecord(channelId, uploads), ChannelRecord.serializer())
        return uploads
    }

    private companion object {
        const val PER_ARTIST = 5
        const val LIST_TTL_MS = 6L * 60 * 60 * 1000
        const val CHANNEL_TTL_MS = 30L * 24 * 60 * 60 * 1000
        /** "Latest" means the last six months; older uploads are catalogue, not releases. */
        const val WINDOW_MS = 180L * 24 * 60 * 60 * 1000
    }
}

/** YouTube's most-popular Music chart for the listener's region. One quota unit per refresh. */
class TrendingRepository(
    private val api: YouTubeDataApi,
    /*
     * A lookup rather than a value: the key is entered in Settings and kept in the
     * Keystore, so it can arrive, change, or be cleared long after this object was built.
     */
    private val apiKeyProvider: () -> String,
    private val cache: DiscoveryCache
) {
    suspend fun trending(regionCode: String): Result<List<MediaItem>> {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) return Result.failure(DiscoveryUnavailable(DiscoveryUnavailable.Reason.NO_API_KEY))
        val key = "trending:$regionCode"
        cache.read(key, TTL_MS, ListSerializer(CachedMedia.serializer()))?.let { cached ->
            return Result.success(cached.map { it.toMediaItem() })
        }
        return try {
            val items = api.getChart(regionCode = regionCode, apiKey = apiKey).items
                // Trending is for playing, and only embeddable videos can play in AURORA.
                .filter { it.status?.embeddable != false }
                .map { video ->
                    MediaItem(
                        id = "youtube:${video.id}",
                        provider = ProviderKind.YOUTUBE,
                        title = video.snippet.title,
                        artist = ArtistAffinity.displayName(video.snippet.channelTitle),
                        album = null,
                        artworkUri = video.snippet.thumbnails.best()
                    )
                }
            cache.write(key, items.map(CachedMedia::of), ListSerializer(CachedMedia.serializer()))
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(DiscoveryUnavailable(DiscoveryUnavailable.Reason.OFFLINE_OR_QUOTA))
        }
    }

    private companion object {
        const val TTL_MS = 6L * 60 * 60 * 1000
    }
}

internal fun parseInstant(value: String?): Long? =
    value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
