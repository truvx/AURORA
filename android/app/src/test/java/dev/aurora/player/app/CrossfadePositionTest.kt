package dev.aurora.player.app

import dev.aurora.player.data.player.CompositePlayerAdapter
import dev.aurora.player.data.player.Media3PlayerOwner
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that CrossfadeMedia3Adapter filters stale position events from the outgoing
 * player during a crossfade, so the playhead never jumps back to the previous track's end.
 *
 * The composite wraps a single Media3 adapter for both local and YouTube tracks. During
 * a provider switch (local <-> YouTube), the session is cleared so the lock screen and
 * notification never name one source while the other is playing.
 */
class CrossfadePositionTest {

    private open class FakeAdapter : PlayerAdapter {
        val emitted = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override val events: Flow<EngineEvent> = emitted
        var loadCount = 0
        var pauseCount = 0
        var clearedCount = 0

        override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            loadCount++
        }
        override fun play() {}
        override fun pause() { pauseCount++ }
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}

        fun emitPosition(elapsed: Long) {
            emitted.tryEmit(EngineEvent.PositionChanged(elapsed, 100_000L, null))
        }
    }

    private class FakeMedia3Adapter : FakeAdapter(), Media3PlayerOwner {
        override val sessionPlayer: androidx.media3.exoplayer.ExoPlayer
            get() = error("not used in this test")
        override fun clearSession() { clearedCount++ }
    }

    @Test
    fun `stale position from outgoing player must not overwrite incoming player`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = FakeMedia3Adapter()
            val composite = CompositePlayerAdapter(adapter)

            val received = mutableListOf<EngineEvent>()
            val collector = launch { composite.events.toList(received) }

            // Load a local track at 30,000 ms
            composite.load(
                PlaybackSource("file:///a.mp3"),
                MediaItem("local_1", ProviderKind.LOCAL, "Track", null, null, null),
                true,
                0L,
            )
            adapter.emitPosition(30_000L)

            // Switch to a YouTube track; the session should be cleared
            composite.load(
                PlaybackSource("abc123"),
                MediaItem("youtube:1", ProviderKind.YOUTUBE, "Track", null, null, null),
                true,
                0L,
            )

            assertEquals(1, adapter.clearedCount)

            // The incoming player starts at 500 ms
            adapter.emitPosition(500L)

            collector.cancel()

            val positions = received.filterIsInstance<EngineEvent.PositionChanged>()
            assertEquals(listOf(30_000L, 500L), positions.map { it.elapsed })
            assertEquals(1, adapter.loadCount - 1) // two loads total
        }
}
