package dev.aurora.player.ui.screens

import dev.aurora.player.domain.ai.AiFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the AI screen tells the person when a request fails. */
class AiErrorMessageTest {

    @Test
    fun `only the not-connected failure offers Settings, since only it is fixed there`() {
        AiFailureReason.entries.forEach { reason ->
            assertEquals(
                "opensSettings for $reason",
                reason == AiFailureReason.NotConnected,
                errorFor(reason).opensSettings
            )
        }
    }

    @Test
    fun `every failure has its own message`() {
        // Distinct messages are the point: they used to all read "HTTP 500".
        val messages = AiFailureReason.entries.map { errorFor(it).message }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `no message leaks transport detail`() {
        AiFailureReason.entries.forEach { reason ->
            val message = errorFor(reason).message
            assertFalse("$reason: $message", message.contains("HTTP"))
            assertTrue("$reason should be a sentence", message.endsWith("."))
        }
    }
}
