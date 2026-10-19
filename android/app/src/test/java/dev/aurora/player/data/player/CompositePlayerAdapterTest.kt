package dev.aurora.player.data.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * The composite must forward only the active adapter's events.
 *
 * The YouTube IFrame keeps a 500ms JavaScript timer running from the moment its player is
 * ready, reporting position 0 and duration 0 whenever no video is loaded. Merging both
 * adapters unconditionally let those zeros overwrite the position of a locally playing
 * track twice a second, so playback looked frozen at 0:00 while the audio actually played.
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

    /**
     * A [YouTubeMusicRemoteAdapter] that reports itself as available, so the composite
     * routes YouTube tracks to it instead of the WebView IFrame player.
     *
     * The real adapter's constructor connects to YouTube Music's MediaSession, which needs a
     * real Context and package. We mock Context/PackageManager so the constructor does not
     * touch the system, then override `isAvailable` and `load` to record calls.
     */
    private inner class FakeRemoteAdapter : YouTubeMusicRemoteAdapter(mockContext(), emptyScope()) {
        val emitted = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override val events: Flow<EngineEvent> = emitted
        override val isAvailable: Boolean = true
        var loadCount = 0
        var pauseCount = 0

        override fun load(source: PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            loadCount++
            emitted.tryEmit(EngineEvent.Prepared)
            if (playWhenReady) emitted.tryEmit(EngineEvent.Started)
        }
        override fun play() { emitted.tryEmit(EngineEvent.Started) }
        override fun pause() { pauseCount++; emitted.tryEmit(EngineEvent.Paused) }
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private inner class FakeRemoteUnavailableAdapter : YouTubeMusicRemoteAdapter(mockContext(), emptyScope()) {
        val emitted = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override val events: Flow<EngineEvent> = emitted
        override val isAvailable: Boolean = false
        var loadCount = 0

        override fun load(source: PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            loadCount++
        }
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private fun emptyScope() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job())

    private fun mockContext(): Context = mockk {
        every { packageManager } returns mockk {
            every { getPackageInfo("com.google.android.apps.youtube.music", 0) } throws PackageManager.NameNotFoundException()
        }
    }

    private fun track(provider: ProviderKind) = MediaItem(
        id = if (provider == ProviderKind.YOUTUBE) "youtube:abc" else "local_1",
        provider = provider,
        title = "Track",
        artist = null,
        album = null,
        artworkUri = null
    )

    private fun composite(
        local: PlayerAdapter,
        youtube: PlayerAdapter,
        remote: YouTubeMusicRemoteAdapter
    ) = CompositePlayerAdapter(local, youtube, remote)

    @Test
    fun `idle youtube ticks do not reach the coordinator while local audio plays`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(PlaybackSource("file:///music.flac"), track(ProviderKind.LOCAL), true, 0L)

            // Real local progress, interleaved with the YouTube IFrame's idle 500ms timer.
            local.emitted.emit(EngineEvent.PositionChanged(elapsed = 4_000L, duration = 180_000L, buffered = 8_000L))
            youtube.emitted.emit(EngineEvent.PositionChanged(elapsed = 0L, duration = 0L, buffered = null))
            local.emitted.emit(EngineEvent.PositionChanged(elapsed = 4_200L, duration = 180_000L, buffered = 8_000L))
            youtube.emitted.emit(EngineEvent.PositionChanged(elapsed = 0L, duration = 0L, buffered = null))

            collector.cancel()

            val positions = received.filterIsInstance<EngineEvent.PositionChanged>()
            assertTrue(
                "expected only local positions, got $positions",
                positions.none { it.elapsed == 0L && it.duration == 0L }
            )
            assertEquals(listOf(4_000L, 4_200L), positions.map { it.elapsed })
        }

    @Test
    fun `local events do not leak while youtube is the active adapter`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(PlaybackSource("https://youtube.com/watch?v=abc"), track(ProviderKind.YOUTUBE), true, 0L)

            youtube.emitted.emit(EngineEvent.PositionChanged(elapsed = 1_000L, duration = 60_000L, buffered = null))
            local.emitted.emit(EngineEvent.PositionChanged(elapsed = 99_000L, duration = 180_000L, buffered = null))

            collector.cancel()

            assertEquals(
                listOf(1_000L),
                received.filterIsInstance<EngineEvent.PositionChanged>().map { it.elapsed }
            )
        }

    @Test
    fun `no events are forwarded before anything is loaded`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            // The IFrame starts ticking as soon as its WebView is ready, which is before
            // the user has chosen anything to play.
            youtube.emitted.emit(EngineEvent.PositionChanged(elapsed = 0L, duration = 0L, buffered = null))

            collector.cancel()
            assertTrue("no adapter is active yet, so nothing should be forwarded", received.isEmpty())
        }

    @Test
    fun `switching providers pauses the adapter being left`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            composite.load(PlaybackSource("file:///a.flac"), track(ProviderKind.LOCAL), true, 0L)
            composite.load(PlaybackSource("https://youtube.com/watch?v=abc"), track(ProviderKind.YOUTUBE), true, 0L)

            assertEquals("the local player must stop when switching to YouTube", 1, local.pauseCount)
            assertEquals(1, youtube.loadCount)
        }

    @Test
    fun `prefers remote adapter when YouTube Music is available`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteAdapter()
            val composite = composite(local, youtube, remote)

            composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0L)

            // Remote adapter is preferred over WebView when available.
            assertEquals(1, remote.loadCount)
            assertEquals(0, youtube.loadCount)
        }

    @Test
    fun `falls back to webview adapter when remote is unavailable`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0L)

            assertEquals(1, youtube.loadCount)
            assertEquals(0, remote.loadCount)
        }

    @Test
    fun `errors from the active adapter still reach the coordinator`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val remote = FakeRemoteUnavailableAdapter()
            val composite = composite(local, youtube, remote)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(PlaybackSource("file:///a.flac"), track(ProviderKind.LOCAL), true, 0L)
            local.emitted.emit(EngineEvent.Error(IllegalStateException("decoder failed")))

            collector.cancel()
            assertTrue(
                "filtering must not swallow failures",
                received.any { it is EngineEvent.Error }
            )
        }

    @Test
    fun `handing over to youtube empties the media session`() {
        // A paused Media3 player keeps its item, so the notification, the lock screen and
        // the car would go on naming a local track while a YouTube video made the sound -
        // and a media session behind YouTube playback is what the capability matrix
        // forbids in the first place.
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local, FakeAdapter(), FakeRemoteUnavailableAdapter())

        composite.load(PlaybackSource("file:///music.mp3"), track(ProviderKind.LOCAL), true, 0)
        assertEquals(0, local.cleared)

        composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0)
        assertEquals(1, local.cleared)
    }

    @Test
    fun `coming back to a local track does not empty it again`() {
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local, FakeAdapter(), FakeRemoteUnavailableAdapter())

        composite.load(PlaybackSource("abc123"), track(ProviderKind.YOUTUBE), true, 0)
        composite.load(PlaybackSource("file:///music.mp3"), track(ProviderKind.LOCAL), true, 0)

        // Cleared on the way out to YouTube, and not on the way back - the local player is
        // about to be given the item it is supposed to play.
        assertEquals(1, local.cleared)
    }

    @Test
    fun `two local tracks in a row never empty the session`() {
        val local = FakeMedia3Adapter()
        val composite = CompositePlayerAdapter(local, FakeAdapter(), FakeRemoteUnavailableAdapter())

        composite.load(PlaybackSource("file:///a.mp3"), track(ProviderKind.LOCAL), true, 0)
        composite.load(PlaybackSource("file:///b.mp3"), track(ProviderKind.LOCAL), true, 0)

        assertEquals(0, local.cleared)
    }

}
