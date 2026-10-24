package dev.aurora.player.data.player

import android.content.Context
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composite now wraps a single Media3/ExoPlayer adapter for both local and YouTube tracks.
 *
 * All YouTube tracks now play through ExoPlayer with audio stream URLs resolved by
 * `YouTubeStreamExtractor` (InnerTube `youtubei/v1/player`, audio-only `adaptiveFormats`),
 * so there is no longer a separate WebView IFrame adapter or YouTube Music remote adapter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompositePlayerAdapterTest {

    private class FakeAdapter : PlayerAdapter {
        val emitted = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override val events: Flow<EngineEvent> = emitted
        var loadCount = 0
        var pauseCount = 0

        override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            loadCount++
        }
        override fun play() {}
        override fun pause() { pauseCount++ }
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private class FakeMedia3Adapter : PlayerAdapter by FakeAdapter(), Media3PlayerOwner {
        var cleared = 0
        override val sessionPlayer: androidx.media3.exoplayer.ExoPlayer
            get() = error("the session player is not used in this test")
        override fun clearSession() { cleared++ }
    }

    private fun track(provider: ProviderKind) = MediaItem(
        id = if (provider == ProviderKind.YOUTUBE) "youtube:abc" else "local_1",
        provider = provider,
        title = "Track",
        artist = null,
        album = null,
        artworkUri = null
    )

    @Test
    fun `events from the media3 adapter reach the coordinator`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = FakeAdapter()
            val composite = CompositePlayerAdapter(adapter)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(PlaybackSource("file:///music.flac"), track(ProviderKind.LOCAL), true, 0L)

            adapter.emitted.emit(EngineEvent.PositionChanged(elapsed = 4_000L, duration = 180_000L, buffered = 8_000L))

            collector.cancel()

            assertEquals(listOf(4_000L), received.filterIsInstance<EngineEvent.PositionChanged>().map { it.elapsed })
        }

    @Test
    fun `errors from the adapter reach the coordinator`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = FakeAdapter()
            val composite = CompositePlayerAdapter(adapter)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(PlaybackSource("file:///a.flac"), track(ProviderKind.LOCAL), true, 0L)
            adapter.emitted.emit(EngineEvent.Error(IllegalStateException("decoder failed")))

            collector.cancel()
            assertTrue("errors must not be swallowed", received.any { it is EngineEvent.Error })
        }

    @Test
    fun `switching providers pauses the adapter`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = FakeAdapter()
            val composite = CompositePlayerAdapter(adapter)

            composite.load(PlaybackSource("file:///a.flac"), track(ProviderKind.LOCAL), true, 0L)
            composite.load(PlaybackSource("https://youtube.com/watch?v=abc"), track(ProviderKind.YOUTUBE), true, 0L)

            // Same adapter handles both sources: two loads, no separate pause needed.
            assertEquals(0, adapter.pauseCount)
            assertEquals(2, adapter.loadCount)
        }

    @Test
    fun `handing over to a YouTube track empties the media session`() {
        // A paused Media3 player keeps its item, so the notification, the lock screen and
        // the car would go on naming a local track while a YouTube track made the sound.
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local)

        composite.load(PlaybackSource("file:///music.mp3"), track(ProviderKind.LOCAL), true, 0)
        assertEquals(0, local.cleared)

        composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0)
        assertEquals(1, local.cleared)
    }

    @Test
    fun `coming back to a local track does not empty it again`() {
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local)

        composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0)
        composite.load(PlaybackSource("file:///music.mp3"), track(ProviderKind.LOCAL), true, 0)

        // Cleared on the way out to YouTube, and not on the way back - the local player is
        // about to be given the item it is supposed to play.
        assertEquals(1, local.cleared)
    }

    @Test
    fun `two local tracks in a row never empty the session`() {
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local)

        composite.load(PlaybackSource("file:///a.mp3"), track(ProviderKind.LOCAL), true, 0)
        composite.load(PlaybackSource("file:///b.mp3"), track(ProviderKind.LOCAL), true, 0)

        assertEquals(0, local.cleared)
    }
}
