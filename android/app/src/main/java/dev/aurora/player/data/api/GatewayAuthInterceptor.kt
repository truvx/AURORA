package dev.aurora.player.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches whatever proves who is asking to every AI gateway request.
 *
 * That is a Google ID token where someone has signed in, and the pasted gateway token
 * otherwise. The gateway tells the two apart by shape - a JWT has three parts, a shared
 * secret does not - so nothing further has to be agreed between them.
 *
 * Read per request rather than captured once, so signing in, signing out, or saving a token
 * in Settings takes effect on the next request without rebuilding the HTTP client. With
 * neither, the request goes out bare and the gateway answers 401, which the app reports as
 * "not connected" - the same outcome as a wrong token, and the right thing to show either
 * way.
 */
class GatewayAuthInterceptor(private val credential: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val current = credential()
        val request = if (current.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $current").build()
        }
        return chain.proceed(request)
    }
}
