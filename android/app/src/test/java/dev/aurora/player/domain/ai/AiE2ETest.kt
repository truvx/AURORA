package dev.aurora.player.domain.ai

import dev.aurora.player.app.AiToolExecutor
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.app.TrackResolver
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.data.providers.NetworkAiProvider
import dev.aurora.player.data.providers.YouTubeMusicProvider
import dev.aurora.player.domain.audio.TrackLoudnessData
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.recommendations.RecommendationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType

class DummyPlayerAdapter : PlayerAdapter {
    override val events = MutableSharedFlow<EngineEvent>()
    var lastLoadedUri: String? = null
    var lastLoadedTrack: MediaItem? = null

    override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        lastLoadedUri = uri
        lastLoadedTrack = track
    }
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun setVolume(volume: Float) {}
    override fun setAudioGain(linearGain: Float) {}
    override fun release() {}
}

class DummyTrackResolver : TrackResolver {
    override suspend fun resolveUri(trackId: String): String? = "dummy://uri"
    override suspend fun resolveLoudness(trackId: String): TrackLoudnessData? = null
}

class AiE2ETest {

    private suspend fun retryIntent(provider: AiProvider, request: AiRequest, retries: Int = 3): Result<AiResponse> {
        for (i in 1..retries) {
            val res = provider.resolveIntent(request)
            if (res.isSuccess) return res
            if (i == retries) return res
            println("Intent failed, retrying ($i/$retries)...")
            kotlinx.coroutines.delay(2000L)
        }
        return Result.failure(Exception("Failed after retries"))
    }

    @Test
    fun testLiveAiAndYouTubePipeline() = runBlocking {
        // This is an integration test against a real AI gateway, not a unit test. It needs
        // `npm run dev` in web/ plus live provider credentials, so it is skipped by default
        // and the rest of the suite stays runnable offline.
        // Run it with: ./gradlew :app:testDebugUnitTest -PauroraLiveAi=true
        org.junit.Assume.assumeTrue(
            "Skipping live AI gateway test; enable with -PauroraLiveAi=true",
            System.getProperty("aurora.liveAi") == "true"
        )

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val contentType = MediaType.get("application/json")
        
        // 1. Setup real Gemini AI Provider hitting localhost:3000
        val okHttpClient = okhttp3.OkHttpClient.Builder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        val aiRetrofit = Retrofit.Builder().client(okHttpClient)
            .baseUrl("http://localhost:3000/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        val aiApi = aiRetrofit.create(dev.aurora.player.data.api.AiGatewayApi::class.java)
        val aiProvider = NetworkAiProvider(aiApi)

        // 2. Setup real YouTube Music Provider
        val ytRetrofit = Retrofit.Builder().client(okHttpClient)
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        val ytApi = ytRetrofit.create(YouTubeDataApi::class.java)
        val ytProvider = YouTubeMusicProvider(ytApi)

        // 3. Setup Recommendation Engine
        val engine = RecommendationEngine(listOf(ytProvider))

        // 4. Setup Dummy Coordinator for assertions
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = PlayerCoordinator(DummyPlayerAdapter(), DummyTrackResolver(), scope)
        val executor = AiToolExecutor(coordinator, engine, listOf(ytProvider))

        // 5. Test Live Gemini Intent Parsing (Search & Playback Flow)
        println("Testing Gemini Search Intent...")
        val searchResult = retryIntent(aiProvider, AiRequest(query = "Find some energetic music for a workout"))
        assertTrue("AI Intent resolution failed: ${searchResult.exceptionOrNull()?.message}", searchResult.isSuccess)
        
        val searchToolCalls = searchResult.getOrNull()?.toolCalls
        assertNotNull("Tool calls should not be null", searchToolCalls)
        
        val searchCall = searchToolCalls!!.find { it.name == "searchMusic" || it.name == "recommendMusic" }
        println("SEARCH CALL: $searchCall")
        assertNotNull("Should find a searchMusic or recommendMusic tool call", searchCall)

        // 6. Test Live YouTube Search Execution
        println("Testing YouTube Data API Search Execution...")
        println("YouTube API key present: ${dev.aurora.player.BuildConfig.YOUTUBE_API_KEY.isNotEmpty()}")
        val candidates = executor.executeTool(searchCall!!)
        assertTrue("Should find candidates from YouTube", candidates.isNotEmpty())
        
        val firstTrack = candidates[0].item
        println("Found candidate: ${firstTrack.title} by ${firstTrack.artist} (${firstTrack.id})")

        // 7. Test AI Queue Operation (Add to queue)
        println("Testing Gemini Queue Intent...")
        val queueResult = retryIntent(aiProvider, AiRequest(query = "Add ${firstTrack.title} to the queue"))
        assertTrue("AI Intent resolution failed: ${queueResult.exceptionOrNull()?.message}", queueResult.isSuccess)
        
        val queueToolCalls = queueResult.getOrNull()?.toolCalls
        // Execute the tool call (usually addToQueue or playTrack)
        if (queueToolCalls != null && queueToolCalls.isNotEmpty()) {
            val qCall = queueToolCalls[0]
            if (qCall.name == "addToQueue" || qCall.name == "playTrack") {
                // To safely test we just verify it doesn't crash 
                executor.executeTool(qCall)
            }
        }
        
        println("Pipeline execution successful.")
    }

    @Test
    fun testInvalidTrackIdCannotReachCoordinator() = runBlocking {
        val ytProvider = YouTubeMusicProvider(Retrofit.Builder().baseUrl("https://www.googleapis.com/youtube/v3/").addConverterFactory(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.asConverterFactory(MediaType.get("application/json"))).build().create(YouTubeDataApi::class.java))
        val engine = RecommendationEngine(listOf(ytProvider))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var loadedTrack: MediaItem? = null
        val testAdapter = object : PlayerAdapter {
            override val events = MutableSharedFlow<EngineEvent>()
            override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
                loadedTrack = track
                events.tryEmit(EngineEvent.Prepared)
            }
            override fun play() {}
            override fun pause() {}
            override fun seekTo(positionMs: Long) {}
            override fun setVolume(volume: Float) {}
            override fun setAudioGain(linearGain: Float) {}
            override fun release() {}
        }
        val coordinator = PlayerCoordinator(testAdapter, DummyTrackResolver(), scope)
        val executor = AiToolExecutor(coordinator, engine, listOf(ytProvider))
        
        val fakeCall = AiToolCall("playTrack", kotlinx.serialization.json.JsonObject(mapOf("trackId" to kotlinx.serialization.json.JsonPrimitive("youtube:invalid_id_xyz999"))))
        executor.executeTool(fakeCall)
        
        // It should NOT reach coordinator if it's invalid/unembeddable
        assertTrue("Invalid track reached coordinator", loadedTrack == null)
    }

    @Test
    fun testNetworkFailureDeterministicFallback() = runBlocking {
        // Dummy provider that always fails
        val failingProvider = object : dev.aurora.player.domain.providers.MusicProvider {
            override val id = ProviderKind.YOUTUBE
            override val capabilities = dev.aurora.player.domain.models.ProviderCapabilities(emptySet())
            override suspend fun search(query: String) = Result.failure<List<MediaItem>>(Exception("Network Error"))
            override suspend fun resolveMetadata(trackId: String) = Result.failure<MediaItem>(Exception("Network Error"))
        }
        val engine = RecommendationEngine(listOf(failingProvider))
        val candidates = engine.getRecommendations("workout")
        
        // The deterministic fallback should just handle the exception and return 0 candidates, not crash
        assertTrue("Expected 0 candidates on network failure", candidates.isEmpty())
    }
}
