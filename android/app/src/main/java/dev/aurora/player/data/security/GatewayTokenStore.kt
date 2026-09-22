package dev.aurora.player.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The access token for the AURORA AI gateway.
 *
 * Entered by the person in Settings rather than compiled into the app. A value baked into
 * BuildConfig ships inside the APK, where anyone holding the file can read it - which is
 * how the YouTube key is handled today, and exactly what docs/SECURITY.md rules out for
 * tokens.
 */
interface GatewayTokenStore {
    val isConfigured: StateFlow<Boolean>
    fun get(): String?
    fun set(token: String)
    fun clear()
}

/**
 * Encrypts the token with an AES-GCM key held in the Android Keystore.
 *
 * The key never leaves the Keystore; only ciphertext and IV sit in SharedPreferences. That
 * matters because the app allows backup: preferences can be restored onto another device,
 * but the Keystore key does not travel with them, so a restored token simply fails to
 * decrypt and is discarded - the person reconnects rather than the token leaking.
 */
class KeystoreGatewayTokenStore(context: Context) : GatewayTokenStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    // Presence of ciphertext, not a decrypt: this is read when Settings first composes, and
    // a Keystore round-trip does not belong on the main thread. A token that turns out to be
    // undecryptable is cleared by the first real get(), which flips this back to false.
    private val _isConfigured = MutableStateFlow(prefs.contains(KEY_TOKEN))
    override val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    override fun get(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
            String(cipher.doFinal(decode(stored)), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            // Undecryptable: restored from another device, or the key was invalidated. The
            // stored value is useless either way, so it is removed rather than retried.
            clear()
            null
        } catch (_: IllegalArgumentException) {
            clear()
            null
        }
    }

    override fun set(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "Token is empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_TOKEN, encode(encrypted))
            .putString(KEY_IV, encode(cipher.iv))
            .apply()
        _isConfigured.value = true
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_IV).apply()
        _isConfigured.value = false
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aurora_gateway_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "aurora_secure"
        const val KEY_TOKEN = "gateway_token"
        const val KEY_IV = "gateway_token_iv"
    }
}
