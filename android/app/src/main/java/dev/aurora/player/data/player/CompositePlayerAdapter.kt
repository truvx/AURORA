package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge

/**
 * Routes playback to the Media3/ExoPlayer engine for both local and YouTube tracks.
 *
 * YouTube tracks now play through ExoPlayer with audio stream URLs resolved by
 * `YouTubeStreamExtractor` (InnerTube `youtubei/v1/player`, audio-only `adaptiveFormats`),
 * so there is no longer a separate WebView IFrame adapter or YouTube Music remote adapter.
 *
 * Switching between a local track and a YouTube track clears the Media3 session so the
 * notification, lock screen and headset controls never name one source while the other
 * one is playing.
 */
class CompositePlayerAdapter(
    private val media3Adapter: PlayerAdapter,
) : PlayerAdapter, Media3PlayerOwner {

    override val sessionPlayer: androidx.media3.exoplayer.ExoPlayer
        get() = (media3Adapter as Media3PlayerOwner).sessionPlayer

    override fun clearSession() {
        (media3Adapter as? Media3PlayerOwner)?.clearSession()
    }

    override val events: Flow<EngineEvent> = media3Adapter.events

    private var lastProvider: ProviderKind? = null

    override fun load(source: PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        if (lastProvider != null && lastProvider != track.provider) {
            clearSession()
        }
        lastProvider = track.provider
        media3Adapter.load(source, track, playWhenReady, crossfadeDurationMs)
    }

    override fun play() {
        media3Adapter.play()
    }

    override fun pause() {
        media3Adapter.pause()
    }

    override fun seekTo(positionMs: Long) {
        media3Adapter.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        media3Adapter.setVolume(volume)
    }

    override fun setAudioGain(linearGain: Float) {
        media3Adapter.setAudioGain(linearGain)
    }

    override fun release() {
        media3Adapter.release()
    }
}
