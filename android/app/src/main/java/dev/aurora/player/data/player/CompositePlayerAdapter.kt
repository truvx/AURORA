package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge

class CompositePlayerAdapter(
    private val localAdapter: PlayerAdapter,
    private val youtubeAdapter: PlayerAdapter
) : PlayerAdapter, Media3PlayerOwner {

    /** Only local playback is backed by Media3; YouTube plays in a WebView. */
    override val sessionPlayer: androidx.media3.exoplayer.ExoPlayer
        get() = (localAdapter as Media3PlayerOwner).sessionPlayer

    override fun clearSession() {
        (localAdapter as? Media3PlayerOwner)?.clearSession()
    }

    // Volatile: load() runs on the caller's thread while the flows are collected elsewhere,
    // and the filters below read this on every emission.
    @Volatile
    private var activeAdapter: PlayerAdapter? = null

    /**
     * Only the active adapter's events are forwarded.
     *
     * Merging both unconditionally meant the YouTube IFrame's JavaScript timer - which runs
     * every 500ms from the moment its player is ready, reporting 0/0 whenever no video is
     * loaded - overwrote the position of a locally playing track twice a second. Playback
     * looked frozen at 0:00 while the audio was actually playing.
     */
    override val events: Flow<EngineEvent> = merge(
        localAdapter.events.filter { activeAdapter === localAdapter },
        youtubeAdapter.events.filter { activeAdapter === youtubeAdapter }
    )

    override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        val nextAdapter = if (track.provider == ProviderKind.YOUTUBE) {
            youtubeAdapter
        } else {
            localAdapter
        }

        if (activeAdapter != nextAdapter) {
            activeAdapter?.pause()
            /*
             * Handing over to YouTube empties the Media3 player rather than parking it.
             *
             * The media session is built on that player, so a merely paused one keeps its
             * last item - and the notification, the lock screen and the car would go on
             * naming a local track while a YouTube video was the thing making sound. That
             * is a lie in two directions at once: it credits the wrong recording, and it
             * puts a background media session behind YouTube playback, which
             * docs/YOUTUBE_CAPABILITY_MATRIX.md does not allow. Cleared, the session has
             * nothing to say and the service drops out of the foreground, which is the
             * truth: YouTube plays in the visible player or it does not play.
             */
            if (nextAdapter === youtubeAdapter) {
                clearSession()
            }
            activeAdapter = nextAdapter
        }
        
        activeAdapter?.load(source, track, playWhenReady, crossfadeDurationMs)
    }

    override fun play() {
        activeAdapter?.play()
    }

    override fun pause() {
        activeAdapter?.pause()
    }

    override fun seekTo(positionMs: Long) {
        activeAdapter?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        localAdapter.setVolume(volume)
        youtubeAdapter.setVolume(volume)
    }

    override fun setAudioGain(linearGain: Float) {
        localAdapter.setAudioGain(linearGain)
        youtubeAdapter.setAudioGain(linearGain)
    }

    override fun release() {
        localAdapter.release()
        youtubeAdapter.release()
    }
}
