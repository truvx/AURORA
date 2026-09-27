package dev.aurora.player.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Turns a signed-in session into an authorized request.
 *
 * Sitting here rather than in six method signatures is deliberate: the token has to be
 * *fresh*, and freshness is a property of the moment the request leaves, not of the moment
 * a caller decided to make one. A token resolved earlier and threaded down through three
 * layers is a token that can expire on the way.
 *
 * When a token is present the `key` parameter is removed. Sending both is not belt and
 * braces - Google rejects a request that carries an API key and a bearer token at once.
 */
class YouTubeAuthInterceptor(
    /**
     * Blocking on purpose.
     *
     * Interceptors already run on OkHttp's own I/O threads, never the main one, and the
     * work behind this is a token refresh that either returns immediately from memory or
     * makes the one network call the request was about to depend on anyway.
     */
    private val accessToken: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = accessToken()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(chain.request())

        val request = chain.request()
        val url = request.url().newBuilder().removeAllQueryParameters("key").build()
        return chain.proceed(
            request.newBuilder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
