package dev.aurora.player.data.player.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * InnerTube HTTP client: POSTs to `youtubei/v1/player` to resolve stream URLs.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/InnerTube.kt`.
 *
 * Uses OkHttp directly (already a transitive dependency via Retrofit in AURORA)
 * instead of Ktor, so no new networking library is introduced.
 */
class InnerTubeClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
) {

    var localeGl: String = java.util.Locale.getDefault().country.takeIf { it.isNotEmpty() } ?: "US"
        private set

    var localeHl: String = java.util.Locale.getDefault().language.takeIf { it.isNotEmpty() } ?: "en"
        private set

    @Volatile
    var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    /**
     * Resolves a YouTube video's playable streams via `youtubei/v1/player`.
     *
     * Tries clients in rotation: WEB_REMIX first (main client), then fallback clients
     * when the stream is unavailable. Returns the parsed [PlayerResponse] or throws
     * the last error.
     */
    suspend fun player(
        videoId: String,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ): PlayerResponse = withContext(Dispatchers.IO) {
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
                return@withContext executePlayerRequest(
                    client = client,
                    videoId = videoId,
                    signatureTimestamp = signatureTimestamp,
                    poToken = poToken,
                )
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }
        throw lastError ?: IOException("No YouTube client available")
    }

    private suspend fun executePlayerRequest(
        client: YouTubeClient,
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
    ): PlayerResponse {
        val body = PlayerBody(
            context = client.toContext(
                gl = localeGl,
                hl = localeHl,
                visitorData = authState.visitorData,
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
            .url("${YouTubeClient.API_URL_YOUTUBE_MUSIC}player")
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
