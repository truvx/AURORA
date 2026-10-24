package dev.aurora.player.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aurora.player.data.security.gatewayTokenStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because the point is the real Android Keystore: a JVM fake would pass
 * whether or not the token is actually encrypted.
 */
@RunWith(AndroidJUnit4::class)
class GatewayTokenStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun store() = gatewayTokenStore(context)

    @Before
    @After
    fun reset() = store().clear()

    @Test
    fun roundTripsTheToken() {
        store().set("  s3cret-token  ")

        // Trimmed: a pasted token commonly carries whitespace the gateway would reject.
        assertEquals("s3cret-token", store().get())
    }

    @Test
    fun survivesANewInstance() {
        store().set("persisted")

        val reopened = store()
        assertTrue(reopened.isConfigured.value)
        assertEquals("persisted", reopened.get())
    }

    @Test
    fun neverStoresThePlaintext() {
        store().set("plaintext-marker")

        val raw = context.getSharedPreferences("aurora_secure", Context.MODE_PRIVATE).all.values
        assertTrue(raw.isNotEmpty())
        assertFalse(raw.any { it.toString().contains("plaintext-marker") })
    }

    @Test
    fun clearRemovesItAndReportsDisconnected() {
        val store = store()
        store.set("gone")

        store.clear()

        assertNull(store.get())
        assertFalse(store.isConfigured.value)
    }

    @Test
    fun discardsAValueThatCannotBeDecrypted() {
        // What a backup restored onto another device looks like: ciphertext present, but the
        // Keystore key that made it did not travel. It must read as "not connected".
        context.getSharedPreferences("aurora_secure", Context.MODE_PRIVATE).edit()
            .putString("gateway_token", "AAAAAAAAAAAAAAAAAAAAAA==")
            .putString("gateway_token_iv", "AAAAAAAAAAAAAAAA")
            .commit()

        val store = store()
        assertNull(store.get())
        assertFalse(store.isConfigured.value)
    }
}
