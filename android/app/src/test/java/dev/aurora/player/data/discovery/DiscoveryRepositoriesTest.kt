package dev.aurora.player.data.discovery

import dev.aurora.player.data.api.YouTubeChannel
import dev.aurora.player.data.api.YouTubeChannelContentDetails
import dev.aurora.player.data.api.YouTubeChannelsResponse
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.data.api.YouTubePlaylistItem
import dev.aurora.player.data.api.YouTubePlaylistItemDetails
import dev.aurora.player.data.api.YouTubePlaylistItemsResponse
import dev.aurora.player.data.api.YouTubeRelatedPlaylists
import dev.aurora.player.data.api.YouTubeSearchResponse
import dev.aurora.player.data.api.YouTubeSearchResult
import dev.aurora.player.data.api.YouTubeSnippet
import dev.aurora.player.data.api.YouTubeStatus
import dev.aurora.player.data.api.YouTubeThumbnail
import dev.aurora.player.data.api.YouTubeThumbnails
import dev.aurora.player.data.api.YouTubeVideoId
import dev.aurora.player.data.api.YouTubeVideoItem
import dev.aurora.player.data.api.YouTubeVideosResponse
import dev.aurora.player.domain.discovery.Release
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider
import dev.aurora.player.domain.models.ProviderCapabilities
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * A fake YouTube API that counts calls by endpoint, because quota is the constraint these
 * repositories are designed around: a search costs 100 units, a list call 1.
 */
private class FakeYouTube(
    val channels: Map<String, List<Pair<String, String>>> = emptyMap(),
    val uploads: Map<String, List<YouTubePlaylistItem>> = emptyMap(),
    val chart: List<YouTubeVideoItem> = emptyList(),
    val videos: Map<String, YouTubeVideoItem> = emptyMap(),
    val failAll: Boolean = false
) : YouTubeDataApi {
    val calls = mutableMapOf<String, Int>()
    private fun count(name: String) {
        calls[name] = (calls[name] ?: 0) + 1
        if (failAll) throw IOException("offline")
    }

    override suspend fun search(part: String, type: String, query: String, apiKey: String, videoEmbeddable: String?, videoCategoryId: String, maxResults: Int) =
        YouTubeSearchResponse(emptyList()).also { count("search") }

    override suspend fun getVideos(part: String, id: String, apiKey: String): YouTubeVideosResponse {
        count("videos")
        return YouTubeVideosResponse(id.split(",").mapNotNull { videos[it] })
    }

    override suspend fun getChart(part: String, chart: String, categoryId: String, regionCode: String, maxResults: Int, apiKey: String): YouTubeVideosResponse {
        count("chart")
        return YouTubeVideosResponse(this.chart)
    }

    override suspend fun searchChannels(part: String, type: String, query: String, maxResults: Int, apiKey: String): YouTubeSearchResponse {
        count("searchChannels")
        val artist = query.removeSuffix(" - Topic")
        return YouTubeSearchResponse(
            channels[artist].orEmpty().map { (id, title) ->
                YouTubeSearchResult(YouTubeVideoId(channelId = id), YouTubeSnippet(title = title, thumbnails = thumbs()))
            }
        )
    }

    override suspend fun getChannels(part: String, id: String, apiKey: String): YouTubeChannelsResponse {
        count("channels")
        return YouTubeChannelsResponse(listOf(YouTubeChannel(id, YouTubeChannelContentDetails(YouTubeRelatedPlaylists("UU-$id")))))
    }

    override suspend fun getPlaylistItems(part: String, playlistId: String, maxResults: Int, apiKey: String): YouTubePlaylistItemsResponse {
        count("playlistItems")
        return YouTubePlaylistItemsResponse(uploads[playlistId].orEmpty())
    }
}

private fun thumbs() = YouTubeThumbnails(high = YouTubeThumbnail("https://i.ytimg.com/x.jpg"))

private val NOW = Instant.parse("2026-09-18T12:00:00Z").toEpochMilli()
private fun daysAgo(days: Long) = Instant.ofEpochMilli(NOW - days * 24 * 60 * 60 * 1000).toString()

private fun upload(videoId: String, title: String, published: String) = YouTubePlaylistItem(
    snippet = YouTubeSnippet(title = title, thumbnails = thumbs(), publishedAt = published),
    contentDetails = YouTubePlaylistItemDetails(videoId, published)
)

class LatestReleasesRepositoryTest {

    private fun repo(api: FakeYouTube, store: KeyValueStore = InMemoryStore(), key: String = "k") =
        LatestReleasesRepository(api, { val k = key; if (k.isBlank()) dev.aurora.player.domain.auth.YouTubeCredential.None else dev.aurora.player.domain.auth.YouTubeCredential.Key(k) }, DiscoveryCache(store) { NOW }) { NOW }

    private val keshi = FakeYouTube(
        channels = mapOf("Keshi" to listOf("fan" to "Keshi Fans", "UCkeshi" to "keshi - Topic")),
        uploads = mapOf(
            "UU-UCkeshi" to listOf(
                upload("new1", "Soft Spot", daysAgo(3)),
                upload("old1", "Old Song", daysAgo(400)),
                upload("priv", "Private video", daysAgo(1)),
            )
        )
    )

    @Test
    fun `returns the artist's recent uploads, newest first, and drops old and private ones`() = runTest {
        val releases = repo(keshi).latest(listOf("Keshi")).getOrThrow()

        assertEquals(listOf("youtube:new1"), releases.map { it.item.id })
        assertEquals("Keshi", releases.single().artist)
    }

    @Test
    fun `finds the channel once, then reuses it`() = runTest {
        // The channel search is 100 quota units; repeating it every load would drain the
        // daily quota in a few dozen opens of Home.
        val store = InMemoryStore()
        repo(keshi, store).latest(listOf("Keshi"))
        store.values.keys.filter { it.startsWith("releases:") }.forEach(store::remove) // expire the list

        repo(keshi, store).latest(listOf("Keshi"))

        assertEquals(1, keshi.calls["searchChannels"])
        assertEquals(2, keshi.calls["playlistItems"])
    }

    @Test
    fun `serves the finished list from cache without any calls`() = runTest {
        val store = InMemoryStore()
        repo(keshi, store).latest(listOf("Keshi"))
        val before = keshi.calls.values.sum()

        repo(keshi, store).latest(listOf("Keshi"))

        assertEquals(before, keshi.calls.values.sum())
    }

    @Test
    fun `an artist with no exact channel contributes nothing, and is not searched again`() = runTest {
        val api = FakeYouTube(channels = mapOf("Aoi Mori" to listOf("x" to "Aoi Mori Covers")))
        val store = InMemoryStore()

        assertTrue(repo(api, store).latest(listOf("Aoi Mori")).getOrThrow().isEmpty())
        store.values.keys.filter { it.startsWith("releases:") }.forEach(store::remove)
        repo(api, store).latest(listOf("Aoi Mori"))

        assertEquals(1, api.calls["searchChannels"])
        assertEquals(null, api.calls["channels"])
    }

    @Test
    fun `reports a missing API key as its own reason`() = runTest {
        val error = repo(keshi, key = "").latest(listOf("Keshi")).exceptionOrNull() as DiscoveryUnavailable
        assertEquals(DiscoveryUnavailable.Reason.NO_API_KEY, error.reason)
    }

    @Test
    fun `reports offline when no artist could be reached`() = runTest {
        val error = repo(FakeYouTube(failAll = true)).latest(listOf("Keshi")).exceptionOrNull() as DiscoveryUnavailable
        assertEquals(DiscoveryUnavailable.Reason.OFFLINE_OR_QUOTA, error.reason)
    }

    @Test
    fun `no artists means no releases, and no calls`() = runTest {
        assertTrue(repo(keshi).latest(emptyList()).getOrThrow().isEmpty())
        assertTrue(keshi.calls.isEmpty())
    }
}

class TrendingRepositoryTest {

    private fun video(id: String, embeddable: Boolean) = YouTubeVideoItem(
        id = id,
        snippet = YouTubeSnippet(title = "Song $id", channelTitle = "Artist - Topic", thumbnails = thumbs()),
        status = YouTubeStatus(embeddable)
    )

    @Test
    fun `keeps only videos that can play in AURORA`() = runTest {
        val api = FakeYouTube(chart = listOf(video("a", true), video("b", false)))

        val items = TrendingRepository(api, { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW }).trending("IN").getOrThrow()

        assertEquals(listOf("youtube:a"), items.map { it.id })
        // Channel decoration stripped for display.
        assertEquals("Artist", items.single().artist)
    }

    @Test
    fun `picks up a key entered after it was built`() = runTest {
        // The key now lives in the Keystore and is typed into Settings, so it routinely
        // arrives after the repository exists. Reading it once at construction would have
        // left discovery dead until the app was restarted.
        var key = ""
        val api = FakeYouTube(chart = listOf(video("a", true)))
        val repo = TrendingRepository(api, { val k = key; if (k.isBlank()) dev.aurora.player.domain.auth.YouTubeCredential.None else dev.aurora.player.domain.auth.YouTubeCredential.Key(k) }, DiscoveryCache(InMemoryStore()) { NOW })

        val before = repo.trending("IN")
        assertEquals(
            DiscoveryUnavailable.Reason.NO_API_KEY,
            (before.exceptionOrNull() as DiscoveryUnavailable).reason
        )

        key = "k"
        assertEquals(listOf("youtube:a"), repo.trending("IN").getOrThrow().map { it.id })

        // And removing it turns discovery off again rather than serving a stale cache.
        key = ""
        assertTrue(repo.trending("US").isFailure)
    }

    @Test
    fun `caches per region`() = runTest {
        val api = FakeYouTube(chart = listOf(video("a", true)))
        val repo = TrendingRepository(api, { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW })

        repo.trending("IN"); repo.trending("IN"); repo.trending("US")

        assertEquals(2, api.calls["chart"])
    }
}

class MediaResolverTest {

    private val localTrack = MediaItem("local:1", ProviderKind.LOCAL, "Local Song", "Me", null, null)
    private val local = object : MusicProvider {
        override val id = ProviderKind.LOCAL
        override val capabilities = ProviderCapabilities(emptySet())
        override suspend fun search(query: String) = Result.success(emptyList<MediaItem>())
        override suspend fun resolveMetadata(trackId: String) =
            if (trackId == localTrack.id) Result.success(localTrack) else Result.failure(Exception("missing"))
    }

    private fun yt(id: String, embeddable: Boolean = true) = YouTubeVideoItem(
        id = id, snippet = YouTubeSnippet(title = "YT $id", channelTitle = "Band - Topic", thumbnails = thumbs()),
        status = YouTubeStatus(embeddable)
    )

    @Test
    fun `resolves both providers, keeping the caller's order`() = runTest {
        val api = FakeYouTube(videos = mapOf("a" to yt("a")))
        val resolver = MediaResolver(local, api, { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW })

        val result = resolver.resolve(listOf("youtube:a", "local:1"))

        assertEquals(listOf("youtube:a", "local:1"), result.keys.toList())
        assertEquals("Band", result.getValue("youtube:a").artist)
    }

    @Test
    fun `looks YouTube ids up in one batch and remembers them`() = runTest {
        val api = FakeYouTube(videos = mapOf("a" to yt("a"), "b" to yt("b")))
        val resolver = MediaResolver(local, api, { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW })

        resolver.resolve(listOf("youtube:a", "youtube:b"))
        resolver.resolve(listOf("youtube:a", "youtube:b"))

        assertEquals(1, api.calls["videos"])
    }

    @Test
    fun `marks a no-longer-embeddable video unavailable rather than hiding it`() = runTest {
        val api = FakeYouTube(videos = mapOf("gone" to yt("gone", embeddable = false)))
        val resolver = MediaResolver(local, api, { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW })

        assertEquals(false, resolver.resolve(listOf("youtube:gone")).getValue("youtube:gone").isAvailable)
    }

    @Test
    fun `an offline lookup leaves the rest of the list intact`() = runTest {
        val resolver = MediaResolver(local, FakeYouTube(failAll = true), { dev.aurora.player.domain.auth.YouTubeCredential.Key("k") }, DiscoveryCache(InMemoryStore()) { NOW })

        assertEquals(listOf("local:1"), resolver.resolve(listOf("youtube:a", "local:1")).keys.toList())
    }
}

class DiscoveryCacheTest {

    @Test
    fun `expires entries after their age limit`() {
        var now = 0L
        val cache = DiscoveryCache(InMemoryStore()) { now }
        cache.write("k", "value", String.serializer())

        now = 1_000
        assertEquals("value", cache.read("k", 1_000, String.serializer()))
        now = 1_001
        assertEquals(null, cache.read("k", 1_000, String.serializer()))
    }

    @Test
    fun `treats an unreadable entry as absent`() {
        val store = InMemoryStore().apply { put("k", "not json") }
        assertEquals(null, DiscoveryCache(store) { 0 }.read("k", 1_000, String.serializer()))
    }
}

class WhatsNewStoreTest {

    private fun release(id: String, at: Long) =
        Release(MediaItem(id, ProviderKind.YOUTUBE, id, "A", null, null), "A", at)

    @Test
    fun `only releases newer than the last visit are unseen, and it persists`() {
        val store = InMemoryStore()
        val whatsNew = WhatsNewStore(store)
        val releases = listOf(release("old", 100), release("new", 300))

        whatsNew.markSeen(200)

        assertEquals(listOf("new"), whatsNew.unseen(releases).map { it.item.id })
        assertEquals(listOf("new"), WhatsNewStore(store).unseen(releases).map { it.item.id })
    }
}
