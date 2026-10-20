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
        // Dump format fields for debugging
        for (f in audioFormats.take(5)) {
            Log.d(TAG, "Format itag=${f.itag} mime=${f.mimeType} url=${f.url != null} sigCipher=${f.signatureCipher != null} cipher=${f.cipher != null} bitrate=${f.bitrate} avgBitrate=${f.averageBitrate} width=${f.width} height=${f.height}")
        }

        // Rotate through clients for cipher deobfuscation — different clients return
        // different cipher types, and some require a PoToken or signature timestamp.
        val clients = listOf(
            YouTubeClient.WEB_REMIX,
            YouTubeClient.WEB,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID,
            YouTubeClient.ANDROID_MUSIC,
        )
        var lastError: Throwable? = null
        for (client in clients) {
            for (format in audioFormats) {
                Log.d(TAG, "Trying format itag=${format.itag} mime=${format.mimeType} url=${format.url != null} cipher=${format.signatureCipher != null || format.cipher != null} client=${client.clientName}")
                try {
                    val url = resolveStreamUrl(
                        format = format,
                        videoId = videoId,
                        client = client,
                        authState = innerTube.authState,
                    )
                    if (url?.isNotBlank() == true) {
                        Log.d(TAG, "Resolved stream URL for itag=${format.itag} client=${client.clientName}")
                        return@withContext url
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed format itag=${format.itag} client=${client.clientName}: ${e::class.simpleName} ${e.message}")
                    lastError = e
                    continue
                }
            }
        }

        throw lastError ?: IllegalStateException("No playable audio stream found for $videoId")
    }

    /**
     * Resolves a single format's stream URL, handling both direct URLs and signature ciphers.
     *
     * Returns null when the format has neither a direct URL nor a signature cipher —
     * such formats cannot be played and should be skipped.
     */
    private fun resolveStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient,
        authState: PlaybackAuthState,
    ): String? {
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
                removeThrottlingParam(directUrl)
            }

            return appendPoToken(url = resolvedDirectUrl, client = client, authState = authState)
        }

        // No direct URL: must be a signature cipher.
        val cipherString = format.signatureCipher ?: format.cipher ?: return null

        val params = parseQueryString(cipherString)
        val obfuscatedSignature = params["s"]
            ?: run { Log.d(TAG, "No 's' param in cipher for itag=${format.itag}"); return null }
        val signatureParam = params["sp"]
            ?: run { Log.d(TAG, "No 'sp' param in cipher for itag=${format.itag}"); return null }
        val cipherUrl = params["url"]
            ?: run { Log.d(TAG, "No 'url' param in cipher for itag=${format.itag}"); return null }

        Log.d(TAG, "Deobfuscating cipher signature for itag=${format.itag} client=${client.clientName}")
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
        }.getOrElse { removeThrottlingParam(cipherUrl) }

        // Reconstruct URL with the deobfuscated signature as the sp parameter.
        val urlObj = okhttp3.HttpUrl.parse(resolvedUrl) ?: return null
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

    /** Removes the `n` (throttling) query parameter from a stream URL. */
    private fun removeThrottlingParam(url: String): String {
        return HttpUrl.parse(url)?.let { u ->
            u.newBuilder().apply { removeAllQueryParameters("n") }.build().toString()
        } ?: url
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
