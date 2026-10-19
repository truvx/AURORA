package dev.aurora.player.domain.player.providers

import android.util.Log
import dev.aurora.player.data.player.youtube.InnerTubeClient
import dev.aurora.player.data.player.youtube.YouTubeStreamExtractor
import dev.aurora.player.domain.audio.RequestedQuality
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlaybackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves YouTube tracks to playable audio stream URLs via InnerTube (`youtubei/v1/player`).
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/pages/NewPipe.kt` (`getStreamUrl`) and
 * `innertube/utils/YTPlayerUtils.kt`.
 *
 * Resolves audio-only `adaptiveFormats` by itag through `YouTubeStreamExtractor`, then
 * returns the extracted stream URL as the [PlaybackSource.uri]. The URI is expiration-aware:
 * stream URLs from YouTube are short-lived, so consumers must not treat a resolved source
 * as permanently valid.
 */
class YouTubePlaybackSourceProvider(
    private val streamExtractor: YouTubeStreamExtractor,
) : PlaybackSourceProvider {
    override val id: String = "youtube"

    private companion object {
        const val TAG = "YouTubePlaybackSourceProvider"
    }

    override suspend fun resolve(track: MediaItem, quality: RequestedQuality): PlaybackSource? {
        val videoId = track.id.removePrefix("youtube:")

        return withContext(Dispatchers.IO) {
            val streamUrl = runCatching {
                streamExtractor.extractAudioStreamUrl(videoId)
            }.getOrNull()

            if (streamUrl != null) {
                Log.d(TAG, "Resolved stream URL for $videoId")
                PlaybackSource(
                    uri = streamUrl,
                    mimeType = "audio/mp4",
                    bitrate = null,
                    durationMs = null,
                    headers = emptyMap(),
                    // Stream URLs are short-lived; treat each resolution as single-use.
                    expirationTimeMs = System.currentTimeMillis() + 60 * 60 * 1000,
                    quality = quality,
                )
            } else {
                Log.e(TAG, "Failed to resolve stream URL for $videoId")
                null
            }
        }
    }
}
