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
class GatewayAuthInterceptor(
    private val idToken: () -> String?,
    private val sharedToken: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credential = credentialFor(request.url().isHttps)
        val authorized = if (credential.isNullOrBlank()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $credential").build()
        }
        return chain.proceed(authorized)
    }

    /**
     * A Google ID token never leaves the device over plaintext.
     *
     * It names a real person to anyone holding it, and it is minted for an audience that
     * will accept it - so putting one on an unencrypted wire is handing out an identity,
     * not just leaking a request. Over cleartext, only the shared token the developer
     * deliberately pointed at a local server is sent; over TLS, the signed-in identity is
     * preferred as usual.
     */
    private fun credentialFor(isHttps: Boolean): String? =
        if (isHttps) idToken() ?: sharedToken() else sharedToken()
}
