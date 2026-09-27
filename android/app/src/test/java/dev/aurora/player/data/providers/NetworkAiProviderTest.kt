package dev.aurora.player.data.providers

import dev.aurora.player.data.api.AiGatewayApi
import dev.aurora.player.data.api.GatewayAuthInterceptor
import dev.aurora.player.domain.ai.AiFailureReason
import dev.aurora.player.domain.ai.AiGatewayException
import dev.aurora.player.domain.ai.AiRequest
import dev.aurora.player.domain.ai.AiResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * How gateway failures reach the app. Before the gateway reported error codes, every one of
 * these arrived as "HTTP 500" and the AI screen printed it verbatim.
 */
class NetworkAiProviderTest {

    private fun httpError(status: Int, body: String): retrofit2.HttpException =
        retrofit2.HttpException(
            retrofit2.Response.error<AiResponse>(
                status,
                ResponseBody.create(MediaType.get("application/json"), body)
            )
        )

    private fun providerThrowing(error: Throwable) = NetworkAiProvider(object : AiGatewayApi {
        override suspend fun resolveIntent(request: AiRequest): AiResponse = throw error
    })

    private suspend fun reasonFor(error: Throwable): AiFailureReason {
        val result = providerThrowing(error).resolveIntent(AiRequest(query = "hi"))
        return (result.exceptionOrNull() as AiGatewayException).reason
    }

    @Test
    fun `a rejected token means the device is not connected`() = runTest {
        assertEquals(
            AiFailureReason.NotConnected,
            reasonFor(httpError(401, """{"error":{"code":"unauthorized","message":"x"}}"""))
        )
    }

    @Test
    fun `the gateway's error code decides, not the status alone`() = runTest {
        // Both of these are 503; only the code tells "set up the server" from "wait".
        assertEquals(
            AiFailureReason.GatewayNotConfigured,
            reasonFor(httpError(503, """{"error":{"code":"gateway_not_configured","message":"x"}}"""))
        )
        assertEquals(
            AiFailureReason.ProviderBusy,
            reasonFor(httpError(503, """{"error":{"code":"provider_busy","message":"x"}}"""))
        )
    }

    @Test
    fun `rate limiting is reported as such`() = runTest {
        assertEquals(
            AiFailureReason.RateLimited,
            reasonFor(httpError(429, """{"error":{"code":"rate_limited","message":"x"}}"""))
        )
    }

    @Test
    fun `a body that is not the gateway's falls back to the status`() = runTest {
        // A proxy's HTML error page, for instance.
        assertEquals(AiFailureReason.NotConnected, reasonFor(httpError(401, "<html>denied</html>")))
        assertEquals(AiFailureReason.Unexpected, reasonFor(httpError(500, "")))
    }

    @Test
    fun `an unknown code is Unexpected rather than a guess`() = runTest {
        assertEquals(
            AiFailureReason.Unexpected,
            reasonFor(httpError(400, """{"error":{"code":"something_new","message":"x"}}"""))
        )
    }

    @Test
    fun `a network failure means the gateway could not be reached`() = runTest {
        assertEquals(AiFailureReason.Unreachable, reasonFor(IOException("connection refused")))
    }
}

class GatewayAuthInterceptorTest {

    private fun proceedWith(
        token: String?,
        url: String = "https://gateway.example.com/api/ai/intent",
        idToken: String? = null,
    ): Request {
        val sent = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url(url).build()
        every { chain.proceed(capture(sent)) } answers {
            Response.Builder().request(sent.captured).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        }
        GatewayAuthInterceptor(idToken = { idToken }, sharedToken = { token }).intercept(chain)
        return sent.captured
    }

    @Test
    fun `attaches the saved token as a bearer credential`() {
        assertEquals("Bearer abc123", proceedWith("abc123").header("Authorization"))
    }

    @Test
    fun `sends no header when no token is saved`() {
        // The gateway then answers 401, which the app reports as "not connected".
        assertNull(proceedWith(null).header("Authorization"))
        assertNull(proceedWith("   ").header("Authorization"))
    }

    @Test
    fun `an id token is never sent over cleartext`() {
        /*
         * The gateway was a hardcoded http://10.0.2.2:3000/ with the ID token attached
         * unconditionally, which put a token naming a real person on an unencrypted wire
         * on every AI call. Over http only the shared secret the developer deliberately
         * pointed at their own machine is sent.
         */
        val sent = proceedWith(token = null, url = "http://10.0.2.2:3000/api/ai/intent", idToken = "eyJ.id.token")

        assertNull(sent.header("Authorization"))
    }

    @Test
    fun `over cleartext the shared token is still sent`() {
        val sent = proceedWith(token = "dev-secret", url = "http://10.0.2.2:3000/api/ai/intent", idToken = "eyJ.id.token")

        assertEquals("Bearer dev-secret", sent.header("Authorization"))
    }

    @Test
    fun `over tls the signed-in identity is preferred over the pasted token`() {
        val sent = proceedWith(token = "dev-secret", idToken = "eyJ.id.token")

        assertEquals("Bearer eyJ.id.token", sent.header("Authorization"))
    }

    @Test
    fun `reads the token per request so Settings changes apply immediately`() {
        var token: String? = null
        val interceptor = GatewayAuthInterceptor(idToken = { null }, sharedToken = { token })
        val sent = mutableListOf<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url("https://gateway.example.com/").build()
        every { chain.proceed(any()) } answers {
            val request = firstArg<Request>()
            sent += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").build()
        }

        interceptor.intercept(chain)
        token = "fresh"
        interceptor.intercept(chain)

        assertNull(sent[0].header("Authorization"))
        assertTrue(sent[1].header("Authorization") == "Bearer fresh")
    }
}
