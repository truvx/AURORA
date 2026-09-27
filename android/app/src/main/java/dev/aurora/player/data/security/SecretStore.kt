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
 * One secret the person supplies: the AI gateway's access token, or the YouTube Data API
 * key.
 *
 * Entered in Settings rather than compiled into the app. A value baked into BuildConfig
 * ships inside the APK, where anyone holding the file can read it - which is exactly what
 * docs/SECURITY.md rules out.
 */
interface SecretStore {
    val isConfigured: StateFlow<Boolean>
    fun get(): String?
    fun set(value: String)
    fun clear()
}

/**
 * Encrypts the secret with an AES-GCM key held in the Android Keystore.
 *
 * The key never leaves the Keystore; only ciphertext and IV sit in SharedPreferences. That
 * matters because the app allows backup: preferences can be restored onto another device,
 * but the Keystore key does not travel with them, so a restored secret simply fails to
 * decrypt and is discarded - the person re-enters it rather than the secret leaking.
 *
 * Each secret gets its own Keystore alias, so clearing one cannot strand another.
 */
class KeystoreSecretStore(
    context: Context,
    private val keyAlias: String,
    private val prefKey: String,
) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ivKey = prefKey + "_iv"
    // Presence of ciphertext, not a decrypt: this is read when Settings first composes, and
    // a Keystore round-trip does not belong on the main thread. A token that turns out to be
    // undecryptable is cleared by the first real get(), which flips this back to false.
    private val _isConfigured = MutableStateFlow(prefs.contains(prefKey))
    override val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    override fun get(): String? {
        val stored = prefs.getString(prefKey, null) ?: return null
        val iv = prefs.getString(ivKey, null) ?: return null
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

    override fun set(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Secret is empty" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(prefKey, encode(encrypted))
            .putString(ivKey, encode(cipher.iv))
            .apply()
        _isConfigured.value = true
    }

    override fun clear() {
        prefs.edit().remove(prefKey).remove(ivKey).apply()
        _isConfigured.value = false
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
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
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "aurora_secure"
    }
}

/** The AI gateway's access token. */
fun gatewayTokenStore(context: Context): SecretStore =
    KeystoreSecretStore(context, "aurora_gateway_token", "gateway_token")

/**
 * The YouTube Data API key.
 *
 * Per-install rather than per-build: the key used to be compiled into BuildConfig from
 * local.properties, so every copy of the APK carried a working key that anyone could pull
 * out of the file with `strings`. Discovery is simply unavailable until one is entered.
 */
/**
 * The Google session: access token, refresh token and ID token, as one serialized blob.
 *
 * The refresh token in it is the valuable part - it is long-lived and it is what lets
 * anyone holding it mint access tokens - so it gets the same Keystore treatment as the
 * gateway token rather than the ordinary preferences AppAuth would happily write to.
 */
fun googleAuthStore(context: Context): SecretStore =
    KeystoreSecretStore(context, "aurora_google_auth", "google_auth_state")

fun youTubeKeyStore(context: Context): SecretStore =
    KeystoreSecretStore(context, "aurora_youtube_key", "youtube_api_key")
