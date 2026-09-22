package dev.aurora.player.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the gateway token to every AI gateway request.
 *
 * Read per request rather than captured once, so a token saved or cleared in Settings takes
 * effect on the next request without rebuilding the HTTP client. With no token saved the
 * request goes out bare and the gateway answers 401, which the app reports as "not
 * connected" - the same outcome as a wrong token, and the right thing to show either way.
 */
class GatewayAuthInterceptor(private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val current = token()
        val request = if (current.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $current").build()
        }
        return chain.proceed(request)
    }
}
