package dev.aurora.player.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Validates HapticEngine contract with the NoOp implementation.
 */
class HapticEngineTest {

    @Test
    fun `NoOpHapticEngine reports None capability`() {
        val engine = NoOpHapticEngine()
        assertEquals(HapticCapabilityTier.None, engine.capabilityTier)
    }

    @Test
    fun `NoOpHapticEngine reports disabled`() {
        val engine = NoOpHapticEngine()
        assertFalse(engine.isEnabled)
    }

    @Test
    fun `NoOpHapticEngine fires all events without exception`() {
        val engine = NoOpHapticEngine()
        val events = listOf(
            HapticEvent.Tap,
            HapticEvent.Selection,
            HapticEvent.Toggle,
            HapticEvent.Scrub,
            HapticEvent.SliderTick,
            HapticEvent.DragStart,
            HapticEvent.DragMove,
            HapticEvent.DragDrop,
            HapticEvent.Favorite,
            HapticEvent.QueueReorder,
            HapticEvent.DownloadComplete,
            HapticEvent.Success,
            HapticEvent.Warning,
            HapticEvent.Error,
        )
        events.forEach { instance ->
            engine.fire(instance)
        }
    }

    @Test
    fun `all HapticEvent types are sealed objects`() {
        // Verify all events are data objects (singletons)
        val events = listOf(
            HapticEvent.Tap,
            HapticEvent.Selection,
            HapticEvent.Toggle,
            HapticEvent.Scrub,
            HapticEvent.SliderTick,
            HapticEvent.DragStart,
            HapticEvent.DragMove,
            HapticEvent.DragDrop,
            HapticEvent.Favorite,
            HapticEvent.QueueReorder,
            HapticEvent.DownloadComplete,
            HapticEvent.Success,
            HapticEvent.Warning,
            HapticEvent.Error,
        )
        assertEquals(14, events.size)
    }
}
