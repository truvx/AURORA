package dev.aurora.player.domain.auth

/**
 * Whether AURORA can speak to Google on the listener's behalf.
 *
 * Deliberately not "is the user logged in". A Google account is an identity, not an
 * entitlement: signing in grants AURORA permission to call APIs *as* that person, and the
 * quota those calls spend still belongs to the Cloud project the client ID came from.
 * Nothing here should be read as "YouTube is now free" or "Gemini is now paid for".
 */
sealed interface GoogleSignInState {

    /** No OAuth client ID was built in, so signing in is not offered at all. */
    data object NotConfigured : GoogleSignInState

    data object SignedOut : GoogleSignInState

    /** [email] is shown so it is obvious *which* account is being spent. Null if not granted. */
    data class SignedIn(val email: String?) : GoogleSignInState

    /** The last attempt failed. Kept as state so Settings can say what went wrong. */
    data class Failed(val reason: SignInFailure) : GoogleSignInState
}

/**
 * Why signing in did not work, in terms a person can act on.
 *
 * Typed rather than a message, because the responses differ: a cancelled sign-in needs
 * nothing, a network failure needs retrying, and a refused grant needs a different account
 * or a different project. A single "sign-in failed" string hides all three.
 */
enum class SignInFailure {
    /** The person backed out of the consent screen. Not an error, and not reported as one. */
    Cancelled,
    /** Offline, or Google did not answer in time. */
    Unreachable,
    /** Google refused: wrong client, redirect mismatch, or the API is not enabled. */
    Refused,
    /** A token came back that AURORA could not make sense of. */
    Unreadable,
}
