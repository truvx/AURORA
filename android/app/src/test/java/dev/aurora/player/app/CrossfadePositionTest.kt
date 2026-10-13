package dev.aurora.player.app

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossfadePositionTest {

    class FakePlayerAdapter(val name: String) : PlayerAdapter {
        override val events = MutableSharedFlow<EngineEvent>()
        override fun load(source: dev.aurora.player.domain.player.PlaybackSource, track: MediaItem, playWhenReady: Boolean, crossfadeDurationMs: Long) {}

        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}

        suspend fun emitPosition(elapsed: Long) {
            events.emit(EngineEvent.PositionChanged(elapsed, 100_000L, null))
        }
        
        suspend fun emitEvent(event: EngineEvent) {
            events.emit(event)
        }
    }

    @Test
    fun `stale position from outgoing player must not overwrite incoming player`() = runBlocking {
        val primary = FakePlayerAdapter("primary")
        val secondary = FakePlayerAdapter("secondary")

        // Simulating the logic in CrossfadeMedia3Adapter's current merge
        val mergedEvents = merge(primary.events, secondary.events)

        val results = mutableListOf<Long>()
        val collector = launch {
            mergedEvents.collect { event ->
                if (event is EngineEvent.PositionChanged) {
                    results.add(event.elapsed)
                }
            }
        }

        // 1. Outgoing player at 30,000 ms
        primary.emitPosition(30_000L)

        // Give it a tiny bit of time to propagate
        delay(50)

        // 2. Incoming player becomes authoritative (simulated by events arriving)
        // 3. Incoming player emits 500 ms
        secondary.emitPosition(500L)

        delay(50)

        // 4. Outgoing player subsequently emits 30,500 ms (delayed)
        primary.emitPosition(30_500L)

        delay(100)
        collector.cancel()

        // The current results will be [30000, 500, 30500]
        // The final canonical position in the UI would be 30,500ms, which is WRONG.
        println("Received positions: $results")
        assertEquals("The final position should be the incoming player's 500ms, not the stale 30,500ms", 500L, results.last())
    }

}
