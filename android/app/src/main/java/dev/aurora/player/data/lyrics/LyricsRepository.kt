package dev.aurora.player.data.lyrics

import dev.aurora.player.data.api.LrcLibApi
import dev.aurora.player.data.api.LrcLibRecord
import dev.aurora.player.data.discovery.DiscoveryCache
import dev.aurora.player.domain.discovery.TrackIdentity
import dev.aurora.player.domain.lyrics.LrcParser
import dev.aurora.player.domain.lyrics.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * The words of what is playing, or an honest account of why there are none.
 *
 * The rule here is the same one the cover lookup follows: a wrong answer is worse than no
 * answer. A lyric is the one thing on the screen a listener reads along with, and the
 * lyric of a different song is not a near miss - so a candidate is only accepted when its
 * duration agrees with the track being played, and an identity AURORA is not confident
 * about is not looked up at all.
 */
class LyricsRepository(
    private val api: LrcLibApi,
    private val cache: DiscoveryCache,
) {

    /** What the lookup found, including the answer that there is nothing. */
    sealed interface Result {
        data class Found(val lyrics: Lyrics, val source: String) : Result
        /** Looked, and this recording has no lyrics in the database. */
        data object None : Result
        /** Could not look: offline, or the service did not answer. Not cached. */
        data object Unavailable : Result
        /** The upload could not be read as a song at all, so there was nothing to ask. */
        data object Unidentified : Result
    }

    @Serializable
    private data class Cached(
        val plain: String? = null,
        val synced: String? = null,
        val instrumental: Boolean = false,
        /** True when the lookup ran and found nothing, which is worth remembering. */
        val empty: Boolean = false,
    )

    /**
     * [identity] is the record as it has already been established - by the cover lookup for
     * a YouTube upload, by its own tags for a local file. It is taken rather than derived
     * because deriving it again here would re-read a title that has already been read, with
     * less to go on than the caller had, and come back with a guess.
     */
    suspend fun lyricsFor(identity: TrackIdentity?, durationMs: Long?): Result {
        if (identity == null || !identity.confident) return Result.Unidentified

        val key = "lyrics:" + identity.searchQuery.lowercase()
        cache.read(key, CACHE_TTL_MS, Cached.serializer())?.let { return it.toResult() }

        val candidates = runCatching { api.search(identity.artist, identity.title) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                // Says nothing about whether lyrics exist, so it is not written down.
                return Result.Unavailable
            }

        val picked = pick(candidates, identity, durationMs)
        val entry = when {
            picked == null -> Cached(empty = true)
            picked.instrumental -> Cached(instrumental = true)
            else -> Cached(plain = picked.plainLyrics, synced = picked.syncedLyrics)
        }
        cache.write(key, entry, Cached.serializer())
        return entry.toResult()
    }

    private fun Cached.toResult(): Result {
        val synced = synced?.takeIf { it.isNotBlank() }?.let { LrcParser.parse(it) }
        return when {
            instrumental -> Result.Found(Lyrics.Instrumental, SOURCE)
            !synced.isNullOrEmpty() -> Result.Found(Lyrics.Synced(synced), SOURCE)
            !plain.isNullOrBlank() -> Result.Found(Lyrics.Plain(plain.trim()), SOURCE)
            else -> Result.None
        }
    }

    /**
     * The candidate that is actually this recording.
     *
     * Duration is the check that matters. The database holds a radio edit, an extended mix
     * and a live take under one name, and their lyrics do not line up - a synced file from
     * the wrong edit drifts further out of time with every chorus. Where AURORA knows the
     * duration, a candidate more than [DURATION_TOLERANCE_MS] away is refused outright.
     *
     * Where it does not, the name has to match exactly instead, and synced beats plain.
     */
    private fun pick(
        candidates: List<LrcLibRecord>,
        identity: TrackIdentity,
        durationMs: Long?,
    ): LrcLibRecord? {
        val usable = candidates.filter {
            it.instrumental || !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank()
        }
        if (usable.isEmpty()) return null

        if (durationMs != null && durationMs > 0) {
            return usable
                .mapNotNull { record ->
                    val seconds = record.duration ?: return@mapNotNull null
                    val delta = abs((seconds * 1000).toLong() - durationMs)
                    if (delta > DURATION_TOLERANCE_MS) null else record to delta
                }
                // Closest first, and among equally close ones prefer the timed file.
                .sortedWith(compareBy({ it.second }, { if (it.first.syncedLyrics.isNullOrBlank()) 1 else 0 }))
                .firstOrNull()
                ?.first
        }

        return usable
            .filter { it.trackName.equals(identity.title, ignoreCase = true) }
            .minByOrNull { if (it.syncedLyrics.isNullOrBlank()) 1 else 0 }
    }

    private companion object {
        /** Lyrics do not change. A month is about how long a correction takes to matter. */
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000

        /**
         * Three seconds. Uploads carry a few seconds of silence or a label sting either
         * side of the recording, so an exact match would reject the right answer; a
         * different edit is off by far more than this.
         */
        const val DURATION_TOLERANCE_MS = 3_000L

        const val SOURCE = "LRCLIB"
    }
}
