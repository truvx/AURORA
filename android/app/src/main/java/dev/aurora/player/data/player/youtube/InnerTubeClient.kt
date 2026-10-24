package dev.aurora.player.data.player.youtube

import dev.aurora.player.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * InnerTube HTTP client: POSTs to `youtubei/v1/player` to resolve stream URLs.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/InnerTube.kt`.
 *
 * Uses OkHttp directly (already a transitive dependency via Retrofit in AURORA)
 * instead of Ktor, so no new networking library is introduced.
 *
 * The YouTube API key (bundled in `BuildConfig.YOUTUBE_API_KEY`) is sent as the
 * `key=` query parameter on every `youtubei/v1/player` request; without it YouTube
 * returns no `streamingData`, so stream URLs cannot be resolved.
 */
class InnerTubeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val apiKey: String = BuildConfig.YOUTUBE_API_KEY,
) {
    private companion object {
        const val TAG = "InnerTubeClient"
    }

    var localeGl: String = java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() } ?: "US"
        private set

    var localeHl: String = java.util.Locale.getDefault().language.takeIf { it.isNotEmpty() } ?: "en"
        private set

    @Volatile
    var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    /**
     * Session id synthesized when there's no visitor/login context, so PoTokens can
     * still be minted for WEB/WEB_REMIX clients. Generated once per client instance
     * so the BotGuard engine isn't recreated on every call.
     */
    @Volatile
    private var syntheticSessionId: String? = null

    /** Visitor data scraped from YouTube's watch page, cached after first fetch. */
    @Volatile
    private var cachedVisitorData: String? = null

    /**
     * Resolves a YouTube video's playable streams via `youtubei/v1/player`.
     *
     * Tries clients in rotation: WEB_REMIX first (main client), then fallback clients
     * when the stream is unavailable. Returns the parsed [PlayerResponse] or throws
     * the last error.
     *
     * For WEB and WEB_REMIX clients (which require service integrity), a PoToken is
     * minted via BotGuard and sent as `serviceIntegrityDimensions.poToken` so YouTube
     * issues actual stream URLs instead of empty formats.
     */
    suspend fun player(
        videoId: String,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ): PlayerResponse = withContext(Dispatchers.IO) {
        // Ensure we have visitor data before making player requests — YouTube rejects
        // PoTokens without valid visitor context for WEB/WEB_REMIX clients.
        val visitorData = authState.visitorData
            ?: cachedVisitorData
            ?: run {
                val fetched = fetchVisitorData(videoId)
                cachedVisitorData = fetched
                authState = authState.copy(visitorData = fetched)
                fetched
            }

        val clients = listOf(
            YouTubeClient.WEB_REMIX,
            YouTubeClient.WEB,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID,
            YouTubeClient.ANDROID_MUSIC,
        )
        var lastError: Throwable? = null
        for (client in clients) {
            try {
                // Mint a PoToken for clients that require service integrity (WEB, WEB_REMIX).
                // Without it YouTube returns empty formats and stream URLs cannot be resolved.
                val clientPoToken = if (PlaybackAuthState.needsServiceIntegrity(client)) {
                    val sessionId = authState.sessionId
                        ?: run {
                            syntheticSessionId ?: run {
                                syntheticSessionId = java.util.UUID.randomUUID().toString()
                                syntheticSessionId!!
                            }
                        }
                    BotGuardTokenGenerator.mintToken(videoId, sessionId)?.playerToken?.let { token ->
                        authState = authState.copy(poToken = token)
                        token
                    }
                } else poToken

                val response = executePlayerRequest(
                    client = client,
                    videoId = videoId,
                    signatureTimestamp = signatureTimestamp,
                    poToken = clientPoToken,
                    visitorData = visitorData,
                )
                // YouTube returns HTTP 200 with empty streamingData when it requires a
                // PoToken, signature timestamp, or a different client identity. Don't treat
                // that as success — fall through to the next client instead.
                if (response.streamingData == null) {
                    Log.d(TAG, "$client returned no streamingData for $videoId; trying next client")
                    continue
                }
                return@withContext response
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }
        throw lastError ?: IOException("No YouTube client returned streamingData")
    }

    /**
     * Scrapes visitorData from YouTube's watch page HTML. YouTube embeds it in the
     * `ytcfg` config object; the key is `VISITOR_DATA`.
     */
    private suspend fun fetchVisitorData(videoId: String): String? {
        val url = "https://www.youtube.com/watch?v=$videoId"
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val html = response.body()?.string() ?: ""
            response.close()
            // Look for "VISITOR_DATA":"..." in ytcfg or "INNERTUBE_CONTEXT" block
            val visitorPattern = Pattern.compile("\"VISITOR_DATA\":\"([^\"]+)\"")
            visitorPattern.matcher(html).run {
                if (find()) group(1) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch visitorData", e)
            null
        }
    }

    private suspend fun executePlayerRequest(
        client: YouTubeClient,
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
        visitorData: String?,
    ): PlayerResponse {
        val body = PlayerBody(
            context = client.toContext(
                gl = localeGl,
                hl = localeHl,
                visitorData = visitorData,
            ),
            videoId = videoId,
            playlistId = null,
            playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                PlayerBody.PlaybackContext(
                    PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp)
                )
            } else null,
            serviceIntegrityDimensions = poToken?.let {
                PlayerBody.ServiceIntegrityDimensions(poToken = it)
            },
        )

        val request = Request.Builder()
            .url(HttpUrl.parse(YouTubeClient.API_URL_YOUTUBE_MUSIC + "player")!!
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build()
                .toString())
            .post(bodyToRequestBody(body))
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("X-Origin", client.requestOrigin())
            .header("Referer", client.requestReferer())
            .header("User-Agent", client.userAgent)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Unexpected code ${response.code()} for ${request.url().toString()}")
        }
        val responseBody = response.body()?.string() ?: ""
        response.close()
        // Log playability status so we can see why YouTube may withhold streamingData
        try {
            val peek = json.decodeFromString(PlayerResponse.serializer(), responseBody)
            Log.d(TAG, "InnerTube player response for $videoId: playability=${peek.playabilityStatus.status} reason=${peek.playabilityStatus.reason} streamingData=${if (peek.streamingData != null) "present(${peek.streamingData.adaptiveFormats.size} formats)" else "ABSENT"}")
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube player response for $videoId: (unparsed, length=${responseBody.length})")
        }
        return json.decodeFromString(PlayerResponse.serializer(), responseBody)
    }

    private fun bodyToRequestBody(body: PlayerBody): okhttp3.RequestBody {
        val jsonString = json.encodeToString(PlayerBody.serializer(), body)
        return RequestBody.create(MediaType.parse("application/json"), jsonString)
    }

    /**
     * Builds the InnerTube `context.client` object from a [YouTubeClient].
     */
    private fun YouTubeClient.toContext(gl: String, hl: String, visitorData: String?): Context {
        return Context(
            client = Context.Client(
                clientName = clientName,
                clientVersion = clientVersion,
                osName = null,
                osVersion = null,
                deviceMake = null,
                deviceModel = null,
                androidSdkVersion = null,
                gl = gl,
                hl = hl,
                visitorData = visitorData,
            ),
        )
    }
}
