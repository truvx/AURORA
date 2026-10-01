package dev.aurora.player.ui.haptics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vocabulary, and the rule that keeps it one.
 *
 * The test this replaces built a list of fourteen names by hand and asserted the list had
 * fourteen entries in it, which is a test that the author can count. What matters is that
 * no two events feel the same - that was the state these were cut down from, and nothing
 * but a check stops it growing back one well-meaning distinction at a time.
 */
class HapticEngineTest {

    private val everyEvent = listOf(
        HapticEvent.Tap,
        HapticEvent.Selection,
        HapticEvent.Toggle,
        HapticEvent.Tick,
        HapticEvent.Lift,
        HapticEvent.Commit,
        HapticEvent.Refused,
    )

    @Test
    fun `no two events are played as the same rhythm`() {
        val byRhythm = everyEvent.groupBy { event ->
            rhythm(event).map { Triple(it.kind, it.scale, it.gapMs) }
        }
        val collisions = byRhythm.filterValues { it.size > 1 }.values
        assertTrue(
            "these events are indistinguishable through a phone, so they are one event: $collisions",
            collisions.isEmpty()
        )
    }

    @Test
    fun `the repeating one is the quietest`() {
        // Tick is the only event that arrives under a moving finger. If it ever outweighs
        // a deliberate press, scrubbing turns into a massage.
        val tick = rhythm(HapticEvent.Tick).single()
        val tap = rhythm(HapticEvent.Tap).single()
        assertTrue(tick.kind.amplitude * tick.scale < tap.kind.amplitude * tap.scale)
    }

    @Test
    fun `a refusal is two beats, because one reads as done`() {
        assertEquals(2, rhythm(HapticEvent.Refused).size)
    }

    @Test
    fun `every event has a rhythm`() {
        // `when` over a sealed interface is exhaustive, so this fails to compile rather
        // than fails here - but a rhythm of no beats would vibrate nothing and pass.
        everyEvent.forEach { event ->
            assertTrue("$event has no beats", rhythm(event).isNotEmpty())
        }
    }

    @Test
    fun `the no-op engine reports no capability and fires everything without complaint`() {
        val engine = NoOpHapticEngine()
        assertEquals(HapticCapabilityTier.None, engine.capabilityTier)
        assertFalse(engine.isEnabled)
        everyEvent.forEach(engine::fire)
    }
}
