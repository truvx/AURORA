package dev.aurora.player.data.discovery

import android.content.SharedPreferences
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The storage discovery caches sit on. An interface so JVM tests need no Android. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
}

class InMemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/**
 * Time-limited cache for YouTube responses.
 *
 * Every discovery surface spends YouTube quota, and the default allowance is 10,000 units a
 * day - one search is 100 of them. Without caching, simply opening Home a few dozen times
 * would exhaust it, and every YouTube feature would then fail for the rest of the day.
 */
class DiscoveryCache(
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Serializable
    private data class Entry(val savedAt: Long, val payload: String)

    private val json = Json { ignoreUnknownKeys = true }

    fun <T> read(key: String, maxAgeMs: Long, serializer: KSerializer<T>): T? {
        val raw = store.get(key) ?: return null
        return runCatching {
            val entry = json.decodeFromString(Entry.serializer(), raw)
            if (now() - entry.savedAt > maxAgeMs) null
            else json.decodeFromString(serializer, entry.payload)
        }.getOrElse {
            // Written by an older version of this class; treat as absent rather than crash.
            store.remove(key)
            null
        }
    }

    fun <T> write(key: String, value: T, serializer: KSerializer<T>) {
        store.put(key, json.encodeToString(Entry.serializer(), Entry(now(), json.encodeToString(serializer, value))))
    }
}

/** A MediaItem as cached. Technical metadata is deliberately not cached: it is local-only. */
@Serializable
data class CachedMedia(
    val id: String,
    val provider: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val isAvailable: Boolean = true
) {
    fun toMediaItem() = MediaItem(
        id = id,
        provider = ProviderKind.valueOf(provider),
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri,
        isAvailable = isAvailable
    )

    companion object {
        fun of(item: MediaItem) = CachedMedia(
            item.id, item.provider.name, item.title, item.artist, item.album, item.artworkUri, item.isAvailable
        )
    }
}
