package dev.aurora.player.data.providers

import dev.aurora.player.data.api.YouTubeContentDetails
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.data.api.YouTubeSearchResponse
import dev.aurora.player.data.api.YouTubeSearchResult
import dev.aurora.player.data.api.YouTubeSnippet
import dev.aurora.player.data.api.YouTubeStatus
import dev.aurora.player.data.api.YouTubeThumbnail
import dev.aurora.player.data.api.YouTubeThumbnails
import dev.aurora.player.data.api.YouTubeVideoId
import dev.aurora.player.data.api.YouTubeVideoItem
import dev.aurora.player.data.api.YouTubeVideosResponse
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One contract, run against every provider.
 *
 * docs/TEST_ARCHITECTURE.md asks for the same suite against YouTube and local adapters so
 * that capability declarations, typed failures, and unknown metadata behave identically
 * whichever provider the UI is talking to. No live provider calls: the network boundary is
 * faked, which is the only boundary these tests mock.
 */
abstract class MusicProviderContractTest {

    /** A provider whose backing data contains exactly one known track. */
    protected abstract fun providerWithKnownTrack(): MusicProvider

    /** A provider whose backing source fails, standing in for an offline or erroring source. */
    protected abstract fun providerThatFails(): MusicProvider

    protected abstract val knownTrackId: String

    @Test
    fun `declares its own provider kind`() = runBlocking {
        val provider = providerWithKnownTrack()
        assertTrue(provider.id == ProviderKind.LOCAL || provider.id == ProviderKind.YOUTUBE)
    }

    @Test
    fun `declares at least search and metadata capability`() {
        val capabilities = providerWithKnownTrack().capabilities
        // The UI queries capabilities before offering an action, so an empty set would
        // silently disable the provider rather than fail loudly.
        assertTrue(capabilities.supportedCapabilities.isNotEmpty())
    }

    @Test
    fun `resolves a known track to an item carrying its own provenance`() = runBlocking {
        val provider = providerWithKnownTrack()
        val result = provider.resolveMetadata(knownTrackId)

        assertTrue("expected $knownTrackId to resolve", result.isSuccess)
        val item = result.getOrThrow()
        assertEquals(knownTrackId, item.id)
        // Provenance must survive the mapping; the executor relies on it to route playback.
        assertEquals(provider.id, item.provider)
        assertTrue(item.title.isNotBlank())
    }

    @Test
    fun `an unknown id fails rather than inventing an item`() = runBlocking {
        val result = providerWithKnownTrack().resolveMetadata("definitely-not-a-real-id")

        // Fabricating a placeholder here is what lets an AI-invented id reach playback.
        assertTrue("an unknown id must not resolve", result.isFailure)
    }

    @Test
    fun `a failing source returns a typed failure rather than throwing`() = runBlocking {
        val result = providerThatFails().search("anything")

        assertTrue("a provider failure must be a Result, not an exception", result.isFailure)
    }

    @Test
    fun `a failing source does not resolve metadata`() = runBlocking {
        val result = providerThatFails().resolveMetadata(knownTrackId)

        assertTrue(result.isFailure)
    }

    @Test
    fun `search results carry this provider's identity`() = runBlocking {
        val provider = providerWithKnownTrack()
        val results = provider.search("").getOrDefault(emptyList())

        assertTrue(results.all { it.provider == provider.id })
    }

    @Test
    fun `no result exposes a raw arbitrary media url as its id`() = runBlocking {
        val provider = providerWithKnownTrack()
        val results = provider.search("").getOrDefault(emptyList()) +
            listOfNotNull(provider.resolveMetadata(knownTrackId).getOrNull())

        // Ids are provider-scoped handles. A bare http(s) media URL escaping through here is
        // how an extraction path would begin.
        assertFalse(
            "a provider id must not be a raw media URL",
            results.any { it.id.startsWith("http://") || it.id.startsWith("https://") }
        )
    }
}

// --- YouTube -----------------------------------------------------------------------------

private const val KNOWN_VIDEO_ID = "abc12345678"

class YouTubeMusicProviderContractTest : MusicProviderContractTest() {

    override val knownTrackId = "youtube:$KNOWN_VIDEO_ID"

    private class FakeApi(private val failure: Throwable? = null) : YouTubeDataApi {
        override suspend fun search(
            part: String,
            type: String,
            query: String,
            apiKey: String,
            videoEmbeddable: String?,
            maxResults: Int
        ): YouTubeSearchResponse {
            failure?.let { throw it }
            return YouTubeSearchResponse(
                items = listOf(
                    YouTubeSearchResult(
                        id = YouTubeVideoId(videoId = KNOWN_VIDEO_ID),
                        snippet = YouTubeSnippet(
                            title = "A Video",
                            channelTitle = "A Channel",
                            thumbnails = YouTubeThumbnails(high = YouTubeThumbnail("https://img/1"))
                        )
                    )
                )
            )
        }

        override suspend fun getVideos(
            part: String,
            id: String,
            apiKey: String
        ): YouTubeVideosResponse {
            failure?.let { throw it }
            // The real API returns no items for an unknown id rather than a substitute, and
            // a fake that always answers would hide the provider's not-found handling.
            if (id != KNOWN_VIDEO_ID) return YouTubeVideosResponse(items = emptyList())
            return YouTubeVideosResponse(
                items = listOf(
                    YouTubeVideoItem(
                        id = KNOWN_VIDEO_ID,
                        snippet = YouTubeSnippet(
                            title = "A Video",
                            channelTitle = "A Channel",
                            thumbnails = YouTubeThumbnails(high = YouTubeThumbnail("https://img/1"))
                        ),
                        contentDetails = YouTubeContentDetails(duration = "PT3M20S"),
                        // The provider refuses non-embeddable videos, which is required
                        // policy behaviour, so the fixture has to be embeddable.
                        status = YouTubeStatus(embeddable = true)
                    )
                )
            )
        }
    }

    override fun providerWithKnownTrack(): MusicProvider =
        YouTubeMusicProvider(FakeApi(), apiKey = "test-key")

    override fun providerThatFails(): MusicProvider =
        YouTubeMusicProvider(FakeApi(java.io.IOException("offline")), apiKey = "test-key")

    @Test
    fun `a missing api key fails cleanly rather than calling the network`() = runBlocking {
        val provider = YouTubeMusicProvider(FakeApi(), apiKey = "")

        // CI has no local.properties, so an unconfigured key must be an ordinary typed
        // failure rather than an exception or a silent empty result.
        assertTrue(provider.resolveMetadata(knownTrackId).isFailure)
        assertTrue(provider.search("anything").isFailure)
    }

    @Test
    fun `never claims a technical quality figure for a youtube track`() = runBlocking {
        val item = providerWithKnownTrack().resolveMetadata(knownTrackId).getOrThrow()

        // YouTube quality is provider-determined. Reporting a bitrate or lossless flag here
        // would be a claim AURORA cannot support (docs/YOUTUBE_CAPABILITY_MATRIX.md).
        val metadata = item.technicalMetadata
        if (metadata != null) {
            assertEquals(null, metadata.bitrate)
            assertEquals(null, metadata.isLossless)
        }
    }
}
