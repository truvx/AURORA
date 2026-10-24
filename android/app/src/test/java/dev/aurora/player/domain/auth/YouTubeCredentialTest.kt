package dev.aurora.player.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which authority a YouTube request carries.
 *
 * The rule that matters: never both. Google rejects a request bearing an API key and an
 * access token at once, so a signed-in session has to suppress the key rather than sit
 * alongside it.
 */
class YouTubeCredentialTest {

    @Test
    fun `a signed-in session is preferred over a pasted key`() {
        val credential = YouTubeCredential.of(accessToken = "ya29.token", apiKey = "AIza-key")

        assertEquals(YouTubeCredential.SignedIn("ya29.token"), credential)
    }

    @Test
    fun `a signed-in session sends no key at all`() {
        // Both together is not belt and braces; it is a request Google refuses.
        assertEquals("", YouTubeCredential.of("ya29.token", "AIza-key").keyParameter)
    }

    @Test
    fun `the key is the fallback when nobody is signed in`() {
        assertEquals(YouTubeCredential.Key("AIza-key"), YouTubeCredential.of(null, "AIza-key"))
        assertEquals("AIza-key", YouTubeCredential.of(null, "AIza-key").keyParameter)
    }

    @Test
    fun `an expired session that cannot refresh falls back rather than going dark`() {
        // accessToken() returns null when a refresh fails. Someone who also has a key
        // typed in should keep working, not lose discovery until they notice.
        assertEquals(YouTubeCredential.Key("AIza-key"), YouTubeCredential.of(null, "AIza-key"))
    }

    @Test
    fun `neither is a stated absence rather than an empty request`() {
        assertEquals(YouTubeCredential.None, YouTubeCredential.of(null, null))
        assertEquals(YouTubeCredential.None, YouTubeCredential.of("", ""))
        assertEquals(YouTubeCredential.None, YouTubeCredential.of("   ", "  "))
        assertEquals("", YouTubeCredential.None.keyParameter)
    }
}
