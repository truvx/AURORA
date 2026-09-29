package dev.aurora.player.domain.headphones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parser stands between a headset's own message and a number shown as fact, so what it
 * refuses matters more than what it accepts. Every malformed case here has to come back
 * null rather than with whatever could be salvaged.
 */
class AppleAccessoryEventTest {

    @Test
    fun `reads the battery level from a single pair`() {
        // AT+IPHONEACCEV=1,1,5 - one pair, key 1 (battery), level 5 of 0..9.
        assertEquals(60, AppleAccessoryEvent.batteryPercent(listOf("1", "1", "5")))
    }

    @Test
    fun `the ten steps span ten to a hundred`() {
        // Level is an index, not a percentage: a flat headset reports 0 and a full one 9.
        assertEquals(10, AppleAccessoryEvent.batteryPercent(listOf("1", "1", "0")))
        assertEquals(100, AppleAccessoryEvent.batteryPercent(listOf("1", "1", "9")))
    }

    @Test
    fun `finds the battery pair wherever it sits`() {
        // Key 2 is dock state, which comes first on some headsets.
        assertEquals(80, AppleAccessoryEvent.batteryPercent(listOf("2", "2", "0", "1", "7")))
    }

    @Test
    fun `a message with no battery pair reports nothing`() {
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("1", "2", "1")))
    }

    @Test
    fun `a truncated message is refused rather than half-read`() {
        // Claims two pairs, carries one. The pair that did arrive might be either of them.
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("2", "1", "5")))
    }

    @Test
    fun `a level outside the ten steps is not stretched to fit`() {
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("1", "1", "10")))
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("1", "1", "-1")))
    }

    @Test
    fun `anything that is not a number is refused`() {
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("1", "1", "full")))
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("one", "1", "5")))
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("1", "battery", "5")))
    }

    @Test
    fun `an empty or zero-pair message reports nothing`() {
        assertNull(AppleAccessoryEvent.batteryPercent(emptyList()))
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("0")))
        assertNull(AppleAccessoryEvent.batteryPercent(listOf("-1", "1", "5")))
    }

    @Test
    fun `surrounding space does not stop it being read`() {
        // The arguments arrive as whatever the headset put on the wire.
        assertEquals(60, AppleAccessoryEvent.batteryPercent(listOf(" 1", " 1 ", "5 ")))
    }

    @Test
    fun `a percentage it produces is always one a Connected state will accept`() {
        // The two agree by construction; this is the check that they keep agreeing.
        for (level in 0..9) {
            val percent = AppleAccessoryEvent.batteryPercent(listOf("1", "1", level.toString()))
            HeadphoneState.Connected(percent)
        }
    }
}
