package dev.aurora.player.data.discovery

import dev.aurora.player.data.api.MusicBrainzApi
import dev.aurora.player.domain.discovery.TrackIdentifier
import dev.aurora.player.domain.discovery.TrackIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * The cover of the record a YouTube video is a recording of.
 *
 * A YouTube thumbnail is a 16:9 still from a video - a face, a lyric card, a frame of a
 * visualiser - and stretching one into a square on the player is why every track looked
 * like a screenshot. This finds the actual sleeve.
 *
 * Every answer is cached, including the ones that found nothing: an artist whose catalogue
 * is not in MusicBrainz would otherwise be looked up again on every play, at a service
 * that asks for one request a second.
 */
class CoverArtRepository(
    private val musicBrainz: MusicBrainzApi,
    private val cache: DiscoveryCache,
    /**
     * Whether the archive actually holds a front cover for a release.
     *
     * Checked rather than assumed: a release existing in MusicBrainz says nothing about
     * whether anyone has uploaded its sleeve, and handing the image loader a URL that 404s
     * leaves the player with no artwork at all - worse than the thumbnail it replaced.
     */
    private val coverExists: suspend (String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Serializable
    private data class CachedCover(
        val coverUrl: String? = null,
        val album: String? = null,
        val artist: String? = null,
        val title: String? = null,
    )

    /** What was found, or nothing. [coverUrl] absent means there is no sleeve to show. */
    data class Cover(
        val coverUrl: String?,
        val album: String?,
        val identity: TrackIdentity,
    )

    private val rateLimit = Mutex()
    private var lastRequestAt = 0L

    /**
     * Identifies the video, then finds the cover of the record it came from.
     *
     * Returns null when the video cannot be read as a song at all. A song that is read but
     * has no cover comes back with a null [Cover.coverUrl] and its identity intact, because
     * knowing the artist and the title is worth something on its own.
     */
    suspend fun coverFor(videoTitle: String, channelTitle: String?): Cover? {
        val identity = TrackIdentifier.identify(videoTitle, channelTitle) ?: return null

        val key = "cover:" + identity.searchQuery.lowercase()
        cache.read(key, CACHE_TTL_MS, CachedCover.serializer())?.let { cached ->
            return Cover(
                coverUrl = cached.coverUrl,
                album = cached.album,
                identity = identity,
            )
        }

        // A guess is not worth a lookup: searching MusicBrainz for a channel name and a
        // mix title finds either nothing or the wrong record, and the wrong sleeve is a
        // worse answer than the thumbnail.
        if (!identity.confident) return Cover(null, null, identity)

        val found = runCatching { lookup(identity) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                // Offline, rate limited, or the service is down. Not cached: this says
                // nothing about whether a cover exists, and caching it would hide the
                // answer until the entry expired.
                return Cover(null, null, identity)
            }

        cache.write(
            key,
            CachedCover(found?.first, found?.second, identity.artist, identity.title),
            CachedCover.serializer(),
        )
        return Cover(found?.first, found?.second, identity)
    }

    /** Returns the cover URL and the album it belongs to, or null when there is no match. */
    private suspend fun lookup(identity: TrackIdentity): Pair<String, String?>? {
        val query = """artist:"${escape(identity.artist)}" AND recording:"${escape(identity.title)}""""

        /*
         * Releases from every strong match, not from the first one.
         *
         * A popular song comes back as several recordings all scoring 100, and the first
         * is as likely to be a b-side compilation as the album. Searching MusicBrainz for
         * keshi's "Soft Spot" returns three, and the first carries only a single called
         * "Soft Spot (alternate versions)" - which has no cover uploaded at all, while the
         * album two recordings later does.
         */
        val candidates = throttled { musicBrainz.searchRecording(query) }
            .recordings
            .filter { it.score >= MIN_SCORE }
            .flatMap { it.releases }
            .distinctBy { it.id }
            .sortedBy { release ->
                // The sleeve a listener associates with a song is the record it was
                // written for, not a compilation it was later collected on.
                when (release.releaseGroup?.primaryType?.lowercase()) {
                    "album" -> 0
                    "ep" -> 1
                    "single" -> 2
                    else -> 3
                }
            }
            .take(MAX_CANDIDATES)

        for (release in candidates) {
            val url = "https://coverartarchive.org/release/${release.id}/front-500"
            if (coverExists(url)) {
                return url to release.title.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** MusicBrainz asks for about one request a second, so requests queue behind each other. */
    private suspend fun <T> throttled(block: suspend () -> T): T = rateLimit.withLock {
        val since = now() - lastRequestAt
        if (since < MIN_INTERVAL_MS) kotlinx.coroutines.delay(MIN_INTERVAL_MS - since)
        try {
            block()
        } finally {
            lastRequestAt = now()
        }
    }

    /** Lucene reserves these, and an artist called "AC/DC" would otherwise be a syntax error. */
    private fun escape(value: String): String =
        value.replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), """\\$1""")

    private companion object {
        /** Covers do not change; a month means a reinstall is not needed to pick up a fix. */
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MIN_INTERVAL_MS = 1_100L
        /** Below this MusicBrainz is guessing, and a wrong sleeve is worse than none. */
        const val MIN_SCORE = 80

        /**
         * How many releases to try before giving up.
         *
         * Each one is a request, and a song with no cover anywhere would otherwise walk
         * every pressing it has ever had.
         */
        const val MAX_CANDIDATES = 4
    }
}
