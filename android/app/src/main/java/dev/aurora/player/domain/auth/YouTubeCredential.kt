package dev.aurora.player.domain.auth

/**
 * How a YouTube Data API request proves it is allowed to be made.
 *
 * Both forms spend the same quota, belonging to the same Cloud project. The difference is
 * only where the authority comes from - a key somebody typed, or a token Google issued to
 * an account that signed in - which is why this is one type with two shapes rather than
 * two unrelated paths through the callers.
 */
sealed interface YouTubeCredential {

    /** An OAuth access token. Carried in the header; no key is sent. */
    data class SignedIn(val accessToken: String) : YouTubeCredential

    /** A pasted Data API key. */
    data class Key(val value: String) : YouTubeCredential

    /** Neither. The feature is unavailable and says so rather than failing at the network. */
    data object None : YouTubeCredential

    /**
     * What to put in the `key` query parameter.
     *
     * Empty when a token will carry the request: the interceptor strips the parameter
     * entirely, and sending both a key and a bearer token is an argument Google settles by
     * rejecting the request.
     */
    val keyParameter: String get() = if (this is Key) value else ""

    companion object {
        /**
         * Which of the two to use, given whatever is available.
         *
         * A signed-in session wins. It is the newer thing, it is revocable from the Google
         * account page, and somebody who signed in has said which they want. The key stays
         * as the fallback for anyone who has not signed in - and for a session that has
         * expired and cannot refresh, where falling back beats going dark.
         */
        fun of(accessToken: String?, apiKey: String?): YouTubeCredential = when {
            !accessToken.isNullOrBlank() -> SignedIn(accessToken)
            !apiKey.isNullOrBlank() -> Key(apiKey)
            else -> None
        }
    }
}
