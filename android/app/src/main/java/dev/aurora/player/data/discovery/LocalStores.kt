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

/**
 * Artists the listener has explicitly followed.
 *
 * Not a social feature - AURORA has no accounts and nothing is sent anywhere. Following an
 * artist means one concrete thing: they are watched for new releases on the Home feed,
 * whether or not enough has been played for them to be inferred. Inference alone cannot
 * cover an artist you have just discovered and have not listened to yet.
 *
 * Names are matched case-insensitively but stored as they were written, so the feed can
 * show them the way the person saw them.
 */
class FollowedArtistsStore(private val store: KeyValueStore) {

    private val _followed = MutableStateFlow(read())
    val followed: StateFlow<List<String>> = _followed.asStateFlow()

    fun isFollowed(artist: String): Boolean =
        _followed.value.any { it.equals(artist.trim(), ignoreCase = true) }

    fun setFollowed(artist: String, followed: Boolean) {
        val name = artist.trim()
        if (name.isEmpty()) return

        val current = _followed.value
        // Following someone already followed changes nothing. Otherwise a second tap from
        // a differently-tagged track would rewrite their name in the list and move them,
        // for no reason the person could see.
        if (followed && current.any { it.equals(name, ignoreCase = true) }) return

        val without = current.filterNot { it.equals(name, ignoreCase = true) }
        val next = when {
            // Newest first: the Home feed takes a bounded number of artists, and the one
            // just followed is the one there is a reason to check.
            followed -> (listOf(name) + without).take(LIMIT)
            else -> without
        }
        if (next == current) return

        store.put(KEY, next.joinToString(SEPARATOR))
        _followed.value = next
    }

    private fun read(): List<String> =
        store.get(KEY)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(LIMIT)
            .orEmpty()

    private companion object {
        const val KEY = "followed_artists"
        /** A newline cannot appear in a MediaStore artist tag the way a comma can. */
        const val SEPARATOR = "\n"
        const val LIMIT = 50
    }
}
