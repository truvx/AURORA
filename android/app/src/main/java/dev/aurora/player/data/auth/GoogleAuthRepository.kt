package dev.aurora.player.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.aurora.player.data.security.SecretStore
import dev.aurora.player.domain.auth.GoogleSignInState
import dev.aurora.player.domain.auth.SignInFailure
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Signing in to Google, so nothing has to be pasted.
 *
 * What this buys is precise, and it is worth being precise about because the obvious
 * reading is wrong. It does **not** give AURORA access to YouTube or to Gemini by virtue
 * of the account having them: quota belongs to the Google Cloud project the client ID came
 * from, and a consumer account brings none of its own. What it replaces is the *paste* -
 * an access token authorizes the same Data API calls an API key did, and an ID token lets
 * the gateway recognise who is asking without a shared secret.
 *
 * The protocol is AppAuth's, not this file's: authorization code flow with PKCE and state,
 * an exact redirect derived from the client ID, refresh handled on use, and revocation on
 * the way out. Everything persisted - including the refresh token, which is the valuable
 * part - goes through the Keystore-backed [SecretStore], never into ordinary preferences.
 */
class GoogleAuthRepository(
    private val context: Context,
    private val store: SecretStore,
    private val clientId: String,
    private val redirectUri: String,
) {

    private val service by lazy { AuthorizationService(context) }

    private val _state = MutableStateFlow<GoogleSignInState>(
        if (clientId.isBlank()) GoogleSignInState.NotConfigured else GoogleSignInState.SignedOut
    )
    val state: StateFlow<GoogleSignInState> = _state.asStateFlow()

    val isConfigured: Boolean get() = clientId.isNotBlank()

    private var authState: AuthState = AuthState()

    /** Reads back a previous session, if there is one. Safe to call more than once. */
    fun restore() {
        if (!isConfigured) return
        val saved = store.get()?.takeIf { it.isNotBlank() } ?: return
        authState = runCatching { AuthState.jsonDeserialize(saved) }.getOrElse {
            // Written by an older version, or corrupted. Treated as signed out rather than
            // crashing the app on a stored string.
            store.clear()
            AuthState()
        }
        _state.value = if (authState.isAuthorized) {
            GoogleSignInState.SignedIn(emailFromIdToken(authState.idToken))
        } else {
            GoogleSignInState.SignedOut
        }
    }

    /** The intent to launch for the consent screen, or null when no client ID was built in. */
    fun authorizationIntent(): Intent? {
        if (!isConfigured) return null
        val request = AuthorizationRequest.Builder(
            GOOGLE_CONFIG,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(redirectUri),
        )
            .setScopes(SCOPES)
            /*
             * Consent, every time, with offline access.
             *
             * Google only issues a refresh token on the first grant unless asked again. An
             * app that skips this works until the day someone reinstalls it, and then
             * silently has no way to refresh - which surfaces as being signed out an hour
             * later for no visible reason.
             */
            .setPrompt(AuthorizationRequest.Prompt.CONSENT)
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    /**
     * Completes the flow with whatever came back from the consent screen.
     *
     * A cancelled sign-in is reported as cancelled rather than as a failure: someone
     * changing their mind is not an error, and showing them a red message for it is how an
     * app teaches people to distrust its errors.
     */
    suspend fun complete(data: Intent?): GoogleSignInState {
        if (data == null) return finish(GoogleSignInState.Failed(SignInFailure.Cancelled))
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)

        if (response == null) return finish(GoogleSignInState.Failed(error.toFailure()))

        authState.update(response, error)
        val exchanged = suspendCancellableCoroutine { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokens, exception ->
                authState.update(tokens, exception)
                continuation.resumeOnce(exception)
            }
        }
        if (exchanged != null) return finish(GoogleSignInState.Failed(exchanged.toFailure()))

        persist()
        return finish(GoogleSignInState.SignedIn(emailFromIdToken(authState.idToken)))
    }

    /**
     * A usable access token, refreshed if the one held has expired.
     *
     * Null rather than an exception: every caller's fallback is the same - use the API key
     * if there is one, and say the feature is unavailable if there is not - and a throw
     * would turn "not signed in" into a crash on a background thread.
     */
    suspend fun accessToken(): String? = freshTokens()?.first

    /** The ID token, for a server that needs to know who is asking rather than act for them. */
    suspend fun idToken(): String? = freshTokens()?.second

    private suspend fun freshTokens(): Pair<String?, String?>? {
        if (!isConfigured || !authState.isAuthorized) return null
        return suspendCancellableCoroutine { continuation ->
            authState.performActionWithFreshTokens(service) { accessToken, idToken, exception ->
                if (exception != null) {
                    // The refresh token was revoked from the Google account page, or the
                    // grant expired. Being signed out is the truth, so it is recorded.
                    if (exception.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR) {
                        clearLocally()
                    }
                    continuation.resumeOnce(null as Pair<String?, String?>?)
                } else {
                    persist()
                    continuation.resumeOnce(accessToken to idToken)
                }
            }
        }
    }

    /**
     * Signs out, and tells Google so.
     *
     * Dropping the tokens locally would leave a live grant on the account page that the
     * person believes they revoked. The local state is cleared either way - a revocation
     * that cannot reach the network must not leave someone signed in to an app they just
     * signed out of.
     */
    suspend fun signOut() {
        val token = authState.refreshToken ?: authState.accessToken
        clearLocally()
        if (token.isNullOrBlank()) return
        runCatching { revoke(token) }
    }

    private fun clearLocally() {
        authState = AuthState()
        store.clear()
        _state.value = if (isConfigured) GoogleSignInState.SignedOut else GoogleSignInState.NotConfigured
    }

    private fun persist() {
        runCatching { store.set(authState.jsonSerializeString()) }
    }

    private fun finish(next: GoogleSignInState): GoogleSignInState {
        _state.value = next
        return next
    }

    /**
     * The email out of the ID token, read rather than requested separately.
     *
     * The payload is only decoded for display. Nothing is trusted from it: this is the
     * account AURORA was handed, and the server that cares about identity verifies the
     * token's signature, issuer, audience and expiry itself.
     */
    private fun emailFromIdToken(idToken: String?): String? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        return runCatching {
            val json = String(
                android.util.Base64.decode(
                    payload,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                )
            )
            JSONObject(json).optString("email").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun revoke(token: String) {
        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(REVOKE_TIMEOUT_SECONDS))
            .build()
        val body = okhttp3.FormBody.Builder().add("token", token).build()
        val request = okhttp3.Request.Builder().url(REVOKE_ENDPOINT).post(body).build()
        client.newCall(request).execute().close()
    }

    private fun AuthorizationException?.toFailure(): SignInFailure = when {
        this == null -> SignInFailure.Unreadable
        this == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW -> SignInFailure.Cancelled
        type == AuthorizationException.TYPE_GENERAL_ERROR -> SignInFailure.Unreachable
        else -> SignInFailure.Refused
    }

    private companion object {
        /*
         * Google's endpoints, written down rather than discovered.
         *
         * Discovery is one more network round trip standing between a tap and a consent
         * screen, on endpoints that have not moved in years and that Google documents
         * directly. If they ever do move, this fails loudly at sign-in rather than quietly
         * at refresh.
         */
        val GOOGLE_CONFIG = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )

        const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        const val REVOKE_TIMEOUT_SECONDS = 10L

        /*
         * The least that will do the job.
         *
         * `youtube.readonly` is read access to the Data API, which is all AURORA does with
         * it - nothing here uploads, rates, subscribes or deletes, and asking for the
         * scopes that would is how a music player ends up able to empty someone's
         * playlists. `openid` and `email` are what make an ID token worth having to the
         * gateway, and `email` is also the only way Settings can say which account is
         * signed in.
         */
        val SCOPES = listOf(
            "openid",
            "email",
            "https://www.googleapis.com/auth/youtube.readonly",
        )
    }
}

/** AppAuth's callbacks can fire more than once on some paths; resuming twice would crash. */
private fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
    if (isActive) resume(value)
}
