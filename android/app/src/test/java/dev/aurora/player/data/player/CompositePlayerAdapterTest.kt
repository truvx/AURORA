package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
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

        override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            loadCount++
        }
        override fun play() {}
        override fun pause() { pauseCount++ }
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
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
    fun `idle youtube ticks do not reach the coordinator while local audio plays`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val composite = CompositePlayerAdapter(local, youtube)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(track(ProviderKind.LOCAL), "file:///music.flac", true, 0L)

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
            val composite = CompositePlayerAdapter(local, youtube)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(track(ProviderKind.YOUTUBE), "https://youtube.com/watch?v=abc", true, 0L)

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
            val composite = CompositePlayerAdapter(local, youtube)

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
            val composite = CompositePlayerAdapter(local, youtube)

            composite.load(track(ProviderKind.LOCAL), "file:///a.flac", true, 0L)
            composite.load(track(ProviderKind.YOUTUBE), "https://youtube.com/watch?v=abc", true, 0L)

            assertEquals("the local player must stop when switching to YouTube", 1, local.pauseCount)
            assertEquals(1, youtube.loadCount)
        }

    @Test
    fun `errors from the active adapter still reach the coordinator`() =
        runTest(UnconfinedTestDispatcher()) {
            val local = FakeAdapter()
            val youtube = FakeAdapter()
            val composite = CompositePlayerAdapter(local, youtube)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            composite.load(track(ProviderKind.LOCAL), "file:///a.flac", true, 0L)
            local.emitted.emit(EngineEvent.Error(IllegalStateException("decoder failed")))

            collector.cancel()
            assertTrue(
                "filtering must not swallow failures",
                received.any { it is EngineEvent.Error }
            )
        }
}
