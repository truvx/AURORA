package dev.aurora.player.domain.headphones

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state is shown as fact next to a headphone glyph, so the only readings it may hold
 * are ones a headset could actually have reported.
 */
class HeadphoneStateTest {

    @Test
    fun `an unknown level is a state, not a zero`() {
        val connected = HeadphoneState.Connected(batteryPercent = null)
        assertFalse("nothing is known, so nothing is low", connected.isLow)
    }

    @Test
    fun `low is at or under the threshold`() {
        assertTrue(HeadphoneState.Connected(HeadphoneState.LOW_BATTERY_PERCENT).isLow)
        assertTrue(HeadphoneState.Connected(0).isLow)
        assertFalse(HeadphoneState.Connected(HeadphoneState.LOW_BATTERY_PERCENT + 1).isLow)
        assertFalse(HeadphoneState.Connected(100).isLow)
    }

    @Test
    fun `a percentage outside nought to a hundred is refused at construction`() {
        // Better to fail where the bad number was produced than to render "127%".
        assertThrows(IllegalArgumentException::class.java) { HeadphoneState.Connected(101) }
        assertThrows(IllegalArgumentException::class.java) { HeadphoneState.Connected(-1) }
    }
}
