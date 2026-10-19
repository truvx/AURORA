package dev.aurora.player.data.player.youtube

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Auth state carried across InnerTube playback requests (PoToken, visitor data, cookies).
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/PlaybackAuthState.kt`.
 */
@Serializable
data class PlaybackAuthState(
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val poToken: String? = null,
    val poTokenGvs: String? = null,
    val poTokenPlayer: String? = null,
    val webClientPoTokenEnabled: Boolean = false,
) {
    val hasLoginCookie: Boolean
        get() = hasYouTubeLoginCookie(cookie)

    val hasPlaybackLoginContext: Boolean
        get() = hasLoginCookie && !dataSyncId.isNullOrBlank()

    val sessionId: String?
        get() = if (hasPlaybackLoginContext) dataSyncId else visitorData

    companion object {
        val EMPTY = PlaybackAuthState()

        internal fun needsServiceIntegrity(client: YouTubeClient): Boolean {
            val name = client.clientName.uppercase(Locale.US)
            return name == "WEB" ||
                name == "WEB_REMIX" ||
                name == "WEB_CREATOR" ||
                name == "MWEB" ||
                name == "WEB_EMBEDDED_PLAYER" ||
                name == "TVHTML5" ||
                name == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" ||
                name == "TVHTML5_SIMPLY"
        }
    }
}

private fun hasYouTubeLoginCookie(cookie: String?): Boolean {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return YOUTUBE_LOGIN_COOKIE_NAMES.firstNotNullOfOrNull { cookieName ->
        cookieMap[cookieName]?.takeIf(String::isNotBlank)
    } != null
}

private fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                if (key.isEmpty()) null else key to part.substring(splitIndex + 1).trim()
            }
        }
        .toMap()

private val YOUTUBE_LOGIN_COOKIE_NAMES = listOf(
    "SAPISID",
    "__Secure-3PAPISID",
    "__Secure-1PAPISID",
    "APISID"
)
