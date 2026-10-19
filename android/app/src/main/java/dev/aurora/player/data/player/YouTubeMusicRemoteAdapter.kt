package dev.aurora.player.data.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * YouTube Music's package name. Top-level because [const val] cannot live directly in a
 * class body in Kotlin.
 */
const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

/**
 * A [PlayerAdapter] that delegates YouTube playback to the official YouTube Music app
 * via Android's public [MediaController]/[MediaSession] APIs.
 *
 * This is the only AGENTS.md-compliant path to background YouTube playback. AURORA never
 * touches YouTube media bytes, never extracts stream URLs, never scrapes cookies/tokens,
 * and never runs a hidden player. YouTube Music owns playback as a foreground service;
 * AURORA sends transport commands through MediaController and receives state projections
 * back through its listener callbacks.
 *
 * If YouTube Music is not installed or its MediaSession cannot be reached, the adapter
 * falls back to launching YouTube Music via an intent, which still gives background
 * playback but without transport control or state sync from AURORA.
 *
 * Reuse before rewriting: the intent-launch fallback follows the same approach as
 * NomaTune's `YouTubeMusicLauncher.kt` (GPL-3.0, github.com/shahdullah/nomatune),
 * adapted to AURORA's `PlayerAdapter` contract.
 */
open class YouTubeMusicRemoteAdapter(
    private val context: Context,
    scope: CoroutineScope
) : PlayerAdapter {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events

    // Not part of [PlayerAdapter]; exposed on this concrete type so [CompositePlayerAdapter]
    // can route YouTube tracks here when YouTube Music is reachable.
    open val isAvailable: Boolean
        get() = _isAvailable.value
    private val _isAvailable = MutableStateFlow(false)

    private val _controller = MutableStateFlow<MediaController?>(null)
    /** The connected MediaController, or null while connecting / unavailable. */
    val controller: MediaController?
        get() = _controller.value

    private var connectionFuture: ListenableFuture<MediaController>? = null

    init {
        connect()
    }

    /**
     * Discovers YouTube Music's exported MediaSessionService and connects a
     * [MediaController] to it. If discovery fails, [isAvailable] stays false and
     * [load] falls back to an intent launch.
     */
    private fun connect() {
        if (!isYouTubeMusicInstalled()) {
            _isAvailable.value = false
            return
        }

        val component = findMediaSessionService() ?: return
        val token = SessionToken(context, component)
        val pending = MediaController.Builder(context, token).buildAsync()
        connectionFuture = pending

        pending.addListener(
            {
                runCatching { pending.get() }.onSuccess { controller ->
                    _controller.value = controller
                    _isAvailable.value = true
                    setupListener(controller)
                }.onFailure {
                    _isAvailable.value = false
                    _controller.value = null
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    private fun isYouTubeMusicInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(YOUTUBE_MUSIC_PACKAGE, 0) }.isSuccess

    /**
     * Discovers YouTube Music's exported [MediaSessionService] by querying the package
     * manager for services handling `androidx.media3.session.MediaSessionService`
     * within YouTube Music's package.
     */
    private fun findMediaSessionService(): ComponentName? {
        val intent = Intent("androidx.media3.session.MediaSessionService")
        intent.setPackage(YOUTUBE_MUSIC_PACKAGE)
        return context.packageManager
            .queryIntentServices(intent, 0)
            .firstOrNull()
            ?.serviceInfo
            ?.let { ComponentName(it.packageName, it.name) }
    }

    private fun setupListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(@Player.State state: Int) {
                when (state) {
                    Player.STATE_READY ->
                        _events.tryEmit(EngineEvent.Prepared)
                    Player.STATE_ENDED ->
                        _events.tryEmit(EngineEvent.TrackCompleted)
                    Player.STATE_BUFFERING ->
                        _events.tryEmit(EngineEvent.BufferingChanged(true))
                    Player.STATE_IDLE ->
                        _events.tryEmit(EngineEvent.BufferingChanged(false))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _events.tryEmit(EngineEvent.Started)
                } else {
                    _events.tryEmit(EngineEvent.Paused)
                }
            }

            @OptIn(UnstableApi::class)
            override fun onPositionDiscontinuity(@Player.DiscontinuityReason reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    _events.tryEmit(EngineEvent.SeekCompleted)
                }
            }
        })
    }

    override fun load(
        source: PlaybackSource,
        track: MediaItem,
        playWhenReady: Boolean,
        crossfadeDurationMs: Long
    ) {
        val controller = _controller.value
        if (controller == null) {
            // Not connected to YouTube Music's session. Launch the app via intent so the
            // track still plays in the background, but without transport control from AURORA.
            launchYouTubeMusicIntent(track)
            _events.tryEmit(EngineEvent.Prepared)
            if (playWhenReady) _events.tryEmit(EngineEvent.Started)
            return
        }

        val videoId = track.id.removePrefix("youtube:")
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(Uri.parse("https://music.youtube.com/watch?v=$videoId"))
            .setMediaId(videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        controller.setMediaItem(mediaItem)
        controller.playWhenReady = playWhenReady
        controller.prepare()

        _events.tryEmit(EngineEvent.Prepared)
        if (playWhenReady) _events.tryEmit(EngineEvent.Started)
    }

    /**
     * Launches YouTube Music with the given track's URL. Used when the MediaController
     * is not connected, so the listener still gets background playback through YouTube
     * Music's own foreground service.
     */
    private fun launchYouTubeMusicIntent(track: MediaItem) {
        val videoId = track.id.removePrefix("youtube:")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://music.youtube.com/watch?v=$videoId")
            setPackage(YOUTUBE_MUSIC_PACKAGE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { context.startActivity(intent) }
    }

    override fun play() {
        _controller.value?.play()
    }

    override fun pause() {
        _controller.value?.pause()
    }

    override fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    override fun setVolume(volume: Float) {
        // YouTube Music controls its own audio stream; AURORA has no volume authority.
    }

    override fun setAudioGain(linearGain: Float) {
        // No-op: YouTube Music manages its own normalization; AURORA does not touch its bytes.
    }

    override fun release() {
        connectionFuture?.cancel(false)
        _controller.value?.release()
        _controller.value = null
        _isAvailable.value = false
        connectionFuture = null
    }
}
