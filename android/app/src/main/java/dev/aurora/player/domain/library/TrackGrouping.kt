package dev.aurora.player.domain.library

import dev.aurora.player.domain.models.MediaItem

/**
 * A set of tracks that share an album or an artist.
 *
 * [artist] is filled only when every track in the group agrees, so it is never a guess: a
 * compilation stays unattributed rather than being credited to whoever happens to be first.
 */
data class TrackGroup(
    val key: String,
    val name: String,
    val trackIds: List<String>,
    val artist: String? = null,
)

/**
 * Albums and artists, derived from tags when asked rather than stored.
 *
 * Deriving is cheap, and a second copy in the database would be one more thing to keep in
 * step with a rescan. These rules mirror the Web client's `lib/library/grouping.ts` line
 * for line - the two clients disagreeing about what an album is would be a worse bug than
 * either being wrong on its own.
 *
 * Tracks with missing tags collect under an explicit Unknown group rather than being
 * hidden or attributed to a guess.
 */
object TrackGrouping {

    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"

    fun byAlbum(tracks: List<MediaItem>): List<TrackGroup> =
        tracks
            .groupBy { it.album?.trim().orEmpty().ifEmpty { UNKNOWN_ALBUM } }
            .map { (name, members) ->
                TrackGroup(
                    key = name,
                    name = name,
                    trackIds = members.map { it.id },
                    artist = singleArtist(members),
                )
            }
            .sortedWith(byName)

    fun byArtist(tracks: List<MediaItem>): List<TrackGroup> =
        tracks
            .groupBy { it.artist?.trim().orEmpty().ifEmpty { UNKNOWN_ARTIST } }
            .map { (name, members) ->
                TrackGroup(key = name, name = name, trackIds = members.map { it.id })
            }
            .sortedWith(byName)

    private fun singleArtist(tracks: List<MediaItem>): String? {
        val names = tracks.mapNotNull { it.artist?.trim()?.ifEmpty { null } }.toSet()
        return names.singleOrNull()
    }

    /**
     * Alphabetical, with the Unknown groups last.
     *
     * They are a catch-all rather than a name, and sorting them by their letter would bury
     * real albums under whatever happens to lack tags.
     */
    private val byName = Comparator<TrackGroup> { a, b ->
        val aUnknown = a.name == UNKNOWN_ALBUM || a.name == UNKNOWN_ARTIST
        val bUnknown = b.name == UNKNOWN_ALBUM || b.name == UNKNOWN_ARTIST
        when {
            aUnknown != bUnknown -> if (aUnknown) 1 else -1
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}
