package dev.aurora.player.domain.recommendations

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderCapability
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking must be deterministic and must keep working when a provider fails: recommendations
 * are the offline-capable path, so a dead network should degrade them, not empty them
 * (docs/RECOMMENDATION_ENGINE.md).
 */
class RecommendationEngineTest {

    private class FakeProvider(
        override val id: ProviderKind,
        private val results: List<MediaItem> = emptyList(),
        private val failure: Throwable? = null
    ) : MusicProvider {
        var searchCount = 0

        override val capabilities = ProviderCapabilities(
            setOf(ProviderCapability.SEARCH, ProviderCapability.METADATA)
        )

        override suspend fun search(query: String): Result<List<MediaItem>> {
            searchCount++
            return failure?.let { Result.failure(it) } ?: Result.success(results)
        }

        override suspend fun resolveMetadata(trackId: String): Result<MediaItem> =
            results.firstOrNull { it.id == trackId }?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException(trackId))
    }

    private fun item(id: String, title: String, provider: ProviderKind = ProviderKind.LOCAL) =
        MediaItem(
            id = id,
            provider = provider,
            title = title,
            artist = null,
            album = null,
            artworkUri = null
        )

    @Test
    fun `returns candidates from every provider`() = runBlocking {
        val local = FakeProvider(ProviderKind.LOCAL, listOf(item("l1", "Local Song")))
        val youtube = FakeProvider(ProviderKind.YOUTUBE, listOf(item("y1", "Video", ProviderKind.YOUTUBE)))

        val results = RecommendationEngine(listOf(local, youtube)).getRecommendations("Song")

        assertEquals(setOf("l1", "y1"), results.map { it.item.id }.toSet())
    }

    @Test
    fun `a title matching the query ranks above one that does not`() = runBlocking {
        val provider = FakeProvider(
            ProviderKind.LOCAL,
            listOf(item("miss", "Unrelated"), item("hit", "Ambient Dream"))
        )

        val results = RecommendationEngine(listOf(provider)).getRecommendations("Ambient")

        assertEquals("hit", results.first().item.id)
    }

    @Test
    fun `query matching ignores case`() = runBlocking {
        val provider = FakeProvider(
            ProviderKind.LOCAL,
            listOf(item("miss", "Unrelated"), item("hit", "AMBIENT DREAM"))
        )

        val results = RecommendationEngine(listOf(provider)).getRecommendations("ambient")

        assertEquals("hit", results.first().item.id)
    }

    @Test
    fun `equal scores keep a stable order across repeated runs`() = runBlocking {
        val provider = FakeProvider(
            ProviderKind.LOCAL,
            listOf(item("a", "One"), item("b", "Two"), item("c", "Three"))
        )
        val engine = RecommendationEngine(listOf(provider))

        // Nothing matches, so every candidate scores identically; ordering must not wobble
        // between runs or the same request would return different results each time.
        val first = engine.getRecommendations("zzz").map { it.item.id }
        val second = engine.getRecommendations("zzz").map { it.item.id }

        assertEquals(first, second)
        assertEquals(listOf("a", "b", "c"), first)
    }

    @Test
    fun `one failing provider does not empty the results`() = runBlocking {
        val broken = FakeProvider(ProviderKind.YOUTUBE, failure = java.io.IOException("offline"))
        val working = FakeProvider(ProviderKind.LOCAL, listOf(item("l1", "Local Song")))

        val results = RecommendationEngine(listOf(broken, working)).getRecommendations("Song")

        // Deterministic local recommendations must survive a dead network.
        assertEquals(listOf("l1"), results.map { it.item.id })
    }

    @Test
    fun `every provider failing yields no candidates rather than an error`() = runBlocking {
        val broken = FakeProvider(ProviderKind.LOCAL, failure = java.io.IOException("offline"))

        val results = RecommendationEngine(listOf(broken)).getRecommendations("anything")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `respects the requested limit`() = runBlocking {
        val many = (1..50).map { item("id$it", "Track $it") }
        val provider = FakeProvider(ProviderKind.LOCAL, many)

        val results = RecommendationEngine(listOf(provider)).getRecommendations("Track", limit = 5)

        assertEquals(5, results.size)
    }

    @Test
    fun `asking for more than exists returns what exists`() = runBlocking {
        val provider = FakeProvider(ProviderKind.LOCAL, listOf(item("a", "Only One")))

        val results = RecommendationEngine(listOf(provider)).getRecommendations("Only", limit = 20)

        assertEquals(1, results.size)
    }

    @Test
    fun `no providers yields no candidates`() = runBlocking {
        val results = RecommendationEngine(emptyList()).getRecommendations("anything")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `every candidate carries a reason`() = runBlocking {
        val provider = FakeProvider(ProviderKind.LOCAL, listOf(item("a", "Song")))

        val results = RecommendationEngine(listOf(provider)).getRecommendations("Song")

        // The spec requires every displayed result to explain its provenance. `reason` is
        // nullable on the model, so absent counts as a failure here, not as "no reason yet".
        assertTrue(results.all { !it.reason.isNullOrBlank() })
    }

    @Test
    fun `each provider is searched exactly once per request`() = runBlocking {
        val provider = FakeProvider(ProviderKind.LOCAL, listOf(item("a", "Song")))

        RecommendationEngine(listOf(provider)).getRecommendations("Song")

        // A duplicate search would double YouTube quota consumption for one request.
        assertEquals(1, provider.searchCount)
    }
}
