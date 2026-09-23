package dev.aurora.player.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every failure the gateway can report has to mean something on this device.
 *
 * The codes are declared once, in the web client's GatewayErrorCode union, and this app
 * maps them by hand. Nothing links the two, so the gateway can grow a code and Android
 * will quietly call it "something went wrong" - which is what had already happened to four
 * of them, including invalid_provider_response, the one the tool allowlist raises when the
 * model asks for a tool it was never offered.
 */
class GatewayErrorContractTest {

    /** The codes as the gateway declares them, read from its own source. */
    private fun declaredCodes(): List<String> {
        // Unit tests run with the module directory as the working directory.
        val source = File("../../web/lib/ai/errors.ts")
        assertTrue("cannot find ${source.absolutePath}", source.exists())

        val union = source.readText()
            .substringAfter("export type GatewayErrorCode =")
            .substringBefore(";")

        return Regex("\"([a-z_]+)\"").findAll(union).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `the gateway declares codes this test can actually read`() {
        // A guard on the guard: a parse that silently found nothing would pass forever.
        val codes = declaredCodes()
        assertTrue("parsed $codes", codes.size >= 8)
        assertTrue(codes.contains("invalid_provider_response"))
    }

    @Test
    fun `every gateway code says something more useful than unexpected`() {
        val unmapped = declaredCodes().filter {
            AiFailureReason.fromGatewayCode(it) == AiFailureReason.Unexpected
        }

        assertEquals("these codes reach the person as a generic error", emptyList<String>(), unmapped)
    }

    @Test
    fun `a rejected tool call is its own failure, not a crash`() {
        // What the allowlist produces: the model asked for a tool it was never offered, the
        // gateway refused to pass it on, and nothing was executed here.
        assertEquals(
            AiFailureReason.RefusedByGateway,
            AiFailureReason.fromGatewayCode("invalid_provider_response")
        )
    }

    @Test
    fun `an unknown code is still not guessed at`() {
        assertEquals(AiFailureReason.Unexpected, AiFailureReason.fromGatewayCode("something_new"))
        assertEquals(AiFailureReason.Unexpected, AiFailureReason.fromGatewayCode(null))
    }
}
