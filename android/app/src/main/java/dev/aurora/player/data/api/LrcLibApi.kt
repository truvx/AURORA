package dev.aurora.player.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * LRCLIB, for the words of a song.
 *
 * A community lyrics database with a public API and no key to ship. Like MusicBrainz and
 * the Cover Art Archive it answers one question and is not a `MusicProvider`: nothing is
 * played from here, nothing is browsed, and nothing is recommended, so AGENTS.md's two
 * providers are unchanged.
 *
 * It is used instead of the obvious shortcut - reading a YouTube video's caption track -
 * because a caption track is a transcript of a video and AURORA is forbidden from treating
 * one as a lyric. This is a source that publishes lyrics as lyrics.
 */
interface LrcLibApi {

    /**
     * Candidate records for an artist and a title.
     *
     * Search rather than the exact-match endpoint: that one needs the album and the
     * duration to agree to the second, which is exactly what AURORA does not reliably know
     * for a YouTube upload. Picking among candidates by duration is done in the repository,
     * where the track's own duration is available.
     */
    @GET("api/search")
    suspend fun search(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String,
    ): List<LrcLibRecord>
}

@Serializable
data class LrcLibRecord(
    val id: Long = 0,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String? = null,
    /** Seconds, as a float. Null when the contributor did not record one. */
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)
