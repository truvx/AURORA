package dev.aurora.player.data.player.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import kotlin.math.abs

/**
 * Extracts audio-only stream URLs from a `youtubei/v1/player` response.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/pages/NewPipe.kt` (`getStreamUrl`) and
 * `innertube/utils/YTPlayerUtils.kt` (client rotation, cipher handling).
 *
 * The extractor:
 * 1. Takes a [PlayerResponse] from InnerTube.
 * 2. Selects audio-only `adaptiveFormats` (where `width == null`).
 * 3. For each format, resolves the stream URL: if `url` is present, uses it directly;
 *    if `signatureCipher`/`cipher` is present, deobfuscates via NewPipe's
 *    `YoutubeJavaScriptPlayerManager.deobfuscateSignature`.
 * 4. Appends PoToken (`pot=`) where required.
 * 5. Returns the list of resolved audio stream URLs, preferred bitrate first.
 */
class YouTubeStreamExtractor(
    private val innerTube: InnerTubeClient,
) {
    private companion object {
        const val TAG = "YouTubeStreamExtractor"
    }

    /**
     * Resolves a playable audio stream URL for a YouTube video.
     *
     * @param videoId the YouTube video ID
     * @param signatureTimestamp the `signatureTimestamp` from `sw.js_data`, if known
     * @param poToken the PoToken (BotGuard), if known
     * @return a resolved audio stream URL, or null if no audio stream could be resolved
     */
    suspend fun extractAudioStreamUrl(
        videoId: String,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val response = innerTube.player(
            videoId = videoId,
            signatureTimestamp = signatureTimestamp,
            poToken = poToken,
        )

        val streamingData = response.streamingData
            ?: return@withContext null

        val audioFormats = streamingData.adaptiveFormats
            .filter { it.isAudio }
            .sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { it.averageBitrate ?: 0 }
                    .thenByDescending { it.bitrate }
            )
        if (audioFormats.isEmpty()) {
            Log.d(TAG, "No audio-only adaptiveFormats for $videoId (total=${streamingData.adaptiveFormats.size}, formats=${streamingData.formats?.size ?: 0})")
            return@withContext null
        }

        var lastError: Throwable? = null
        for (format in audioFormats) {
            try {
                val url = resolveStreamUrl(
                    format = format,
                    videoId = videoId,
                    client = YouTubeClient.WEB_REMIX,
                    authState = innerTube.authState,
                )
                if (url.isNotBlank()) {
                    return@withContext url
                }
            } catch (e: Exception) {
                lastError = e
                continue
            }
        }

        throw lastError ?: IllegalStateException("No playable audio stream found for $videoId")
    }

    /**
     * Resolves a single format's stream URL, handling both direct URLs and signature ciphers.
     */
    private fun resolveStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
        authState: PlaybackAuthState,
    ): String {
        val directUrl = format.url
        if (directUrl != null) {
            val resolvedDirectUrl = if (HttpUrl.parse(directUrl)?.queryParameter("n")?.isNotBlank() == true) {
                runCatching {
                    retryWithBackoff(
                        maxAttempts = 3,
                        initialDelayMs = 250L,
                        maxDelayMs = 2_000L
                    ) {
                        YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, directUrl)
                    }
                }.getOrElse { directUrl }
            } else {
                directUrl
            }

            return appendPoToken(url = resolvedDirectUrl, client = client, authState = authState)
        }

        // No direct URL: must be a signature cipher.
        val cipherString = format.signatureCipher ?: format.cipher
            ?: throw IllegalStateException("Could not find format url for itag ${format.itag}")

        val params = parseQueryString(cipherString)
        val obfuscatedSignature = params["s"]
            ?: throw IllegalStateException("Could not parse cipher signature")
        val signatureParam = params["sp"]
            ?: throw IllegalStateException("Could not parse cipher signature parameter")
        val cipherUrl = params["url"]
            ?: throw IllegalStateException("Could not parse cipher url")

        val deobfuscatedSig =
            YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)

        val resolvedUrl = runCatching {
            retryWithBackoff(
                maxAttempts = 3,
                initialDelayMs = 250L,
                maxDelayMs = 2_000L
            ) {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, cipherUrl)
            }
        }.getOrElse { cipherUrl }

        // Reconstruct URL with the deobfuscated signature as the sp parameter.
        val urlObj = okhttp3.HttpUrl.parse(resolvedUrl) ?: throw IllegalStateException("Invalid stream URL")
        val builder = urlObj.newBuilder()
        builder.setQueryParameter(signatureParam, deobfuscatedSig)
        val finalUrl = builder.build().toString()

        return appendPoToken(url = finalUrl, client = client, authState = authState)
    }

    /**
     * Appends the PoToken (`pot=`) query parameter to a stream URL when required.
     */
    private fun appendPoToken(
        url: String,
        client: YouTubeClient,
        authState: PlaybackAuthState,
    ): String {
        if (url.contains("pot=")) return url
        val token = authState.poToken
            ?: return url
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$token"
    }

    private fun parseQueryString(cipherString: String): Map<String, String> {
        return cipherString.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx == -1) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()
    }

    private inline fun <T> retryWithBackoff(
        maxAttempts: Int,
        initialDelayMs: Long,
        maxDelayMs: Long,
        block: () -> T
    ): T {
        var attempt = 0
        var delayMs = initialDelayMs
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (e: Throwable) {
                val isRetryable = e is java.io.IOException ||
                    e.cause is java.io.IOException
                if (!isRetryable || attempt == maxAttempts - 1) throw e
                lastError = e
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
                attempt++
            }
        }
        throw lastError ?: IllegalStateException("Retry attempts exhausted")
    }
}
