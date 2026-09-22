package dev.aurora.player.data.discovery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.aurora.player.domain.discovery.Release
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * "What's new": releases the listener has not looked at yet.
 *
 * Kept on the device with no background work and no notification permission. The bell's
 * dot means "there are releases newer than the last time you opened this", which is all a
 * local, pull-based feature can honestly promise.
 */
class WhatsNewStore(private val store: KeyValueStore) {
    private val _lastSeenAt = MutableStateFlow(store.get(KEY)?.toLongOrNull() ?: 0L)
    val lastSeenAt: StateFlow<Long> = _lastSeenAt.asStateFlow()

    fun unseen(releases: List<Release>): List<Release> =
        releases.filter { it.publishedAtMs > _lastSeenAt.value }

    fun markSeen(nowMs: Long) {
        store.put(KEY, nowMs.toString())
        _lastSeenAt.value = nowMs
    }

    private companion object {
        const val KEY = "whats_new_last_seen"
    }
}

/** [photoVersion] changes whenever the photo does, so an image loader keyed on it reloads. */
data class Profile(val name: String?, val photoPath: String?, val photoVersion: Long = 0)

/**
 * The listener's local profile: a name and a photo, both optional and both on the device.
 *
 * There are no accounts in AURORA - AGENTS.md leaves authentication out of scope - so this
 * is not an identity, only what the avatar shows.
 */
class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("aurora_profile", Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(
        Profile(prefs.getString(KEY_NAME, null), prefs.getString(KEY_PHOTO, null)?.takeIf { File(it).exists() })
    )
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    fun setName(name: String) {
        val trimmed = name.trim().take(40).ifEmpty { null }
        prefs.edit().putString(KEY_NAME, trimmed).apply()
        _profile.value = _profile.value.copy(name = trimmed)
    }

    /**
     * Copies a picked image into app storage, downscaled.
     *
     * Copied because a Photo Picker grant does not outlive the process, and downscaled
     * because the avatar is 40dp - a 12-megapixel original would be decoded on every launch
     * for nothing. Call off the main thread.
     */
    fun setPhoto(source: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val edge = maxOf(bounds.outWidth, bounds.outHeight)
        if (edge <= 0) return false

        var sample = 1
        while (edge / (sample * 2) >= PHOTO_EDGE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false

        val file = File(context.filesDir, "profile_photo.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        prefs.edit().putString(KEY_PHOTO, file.absolutePath).apply()
        // Same path every time, so the version is what tells observers to reload the image.
        _profile.value = _profile.value.copy(photoPath = file.absolutePath, photoVersion = System.currentTimeMillis())
        return true
    }

    fun clearPhoto() {
        File(context.filesDir, "profile_photo.jpg").delete()
        prefs.edit().remove(KEY_PHOTO).apply()
        _profile.value = _profile.value.copy(photoPath = null)
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_PHOTO = "photo"
        const val PHOTO_EDGE = 256
    }
}
