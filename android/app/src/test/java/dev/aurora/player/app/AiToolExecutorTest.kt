package dev.aurora.player.app

import dev.aurora.player.domain.ai.AiToolCall
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderCapability
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider
import dev.aurora.player.domain.recommendations.RecommendationEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The executor is the boundary where model output becomes playback commands, so it must
 * honour the declared tool allowlist and refuse anything it cannot resolve.
 */
class AiToolExecutorTest {

    private val localTrack = MediaItem(
        id = "local_1",
        provider = ProviderKind.LOCAL,
        title = "Known Track",
        artist = "Real Artist",
        album = null,
        artworkUri = null
    )

    private class FakeProvider(
        override val id: ProviderKind,
        private val known: Map<String, MediaItem>
    ) : MusicProvider {
        override val capabilities = ProviderCapabilities(
            setOf(ProviderCapability.SEARCH, ProviderCapability.METADATA, ProviderCapability.PLAYBACK)
        )

        override suspend fun search(query: String): Result<List<MediaItem>> {
            val terms = query.split(" ").filter { it.isNotBlank() }
            return Result.success(
                known.values.filter { item ->
                    terms.any { t ->
                        item.title.contains(t, ignoreCase = true) ||
                            item.artist?.contains(t, ignoreCase = true) == true
                    }
                }
            )
        }

        override suspend fun resolveMetadata(trackId: String): Result<MediaItem> =
            known[trackId]?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException("unknown id: $trackId"))
    }

    private fun executor(): AiToolExecutor {
        val provider = FakeProvider(ProviderKind.LOCAL, mapOf(localTrack.id to localTrack))
        val providers = listOf<MusicProvider>(provider)
        return AiToolExecutor(
            playerCoordinator = PlayerCoordinator(
                DummyPlayerAdapterForExecutor(),
                DummyTrackResolverForExecutor(),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
            ),
            recommendationEngine = RecommendationEngine(providers),
            providers = providers
        )
    }

    private fun call(name: String, args: Map<String, String> = emptyMap()) =
        AiToolCall(name, JsonObject(args.mapValues { JsonPrimitive(it.value) }))

    @Test
    fun `searchMusic returns candidates`() = runBlocking {
        val result = executor().executeTool(call("searchMusic", mapOf("query" to "Known")))
        assertTrue("searchMusic should produce candidates", result.isNotEmpty())
    }

    @Test
    fun `findSimilarMusic is handled because the model is told it exists`() = runBlocking {
        // tools.ts declares findSimilarMusic to the model. If the executor ignores it, every
        // "play something similar" request silently returns nothing.
        val result = executor().executeTool(
            call("findSimilarMusic", mapOf("referenceTrackId" to localTrack.id))
        )
        assertTrue("findSimilarMusic must be handled, not silently dropped", result.isNotEmpty())
    }

    @Test
    fun `findSimilarMusic with an invented reference returns nothing`() = runBlocking {
        val result = executor().executeTool(
            call("findSimilarMusic", mapOf("referenceTrackId" to "not_a_real_id"))
        )
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `an undeclared tool name is rejected`() = runBlocking {
        val result = executor().executeTool(call("deleteEverything", mapOf("query" to "Known")))
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `playTrack with an unresolvable id dispatches nothing`() = runBlocking {
        // Anti-fabrication: a model-invented id must never reach the coordinator.
        val result = executor().executeTool(call("playTrack", mapOf("trackId" to "made_up_id")))
        assertEquals(emptyList<Any>(), result)
    }
}

private class DummyPlayerAdapterForExecutor : PlayerAdapter {
    override val events = kotlinx.coroutines.flow.MutableSharedFlow<EngineEvent>()
    override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun setVolume(volume: Float) {}
    override fun setAudioGain(linearGain: Float) {}
    override fun release() {}
}

private class DummyTrackResolverForExecutor : TrackResolver {
    override suspend fun resolveUri(trackId: String): String? = "file://$trackId"
    override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = null
}
