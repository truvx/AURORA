package dev.aurora.player.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MusicBrainz, for what a recording actually is.
 *
 * Open data under CC0, so the answers can be cached and shown without a licence question,
 * and no key to ship. It is a metadata lookup and not a `MusicProvider`: nothing is played
 * from here, nothing is browsed, and AGENTS.md's two providers are unchanged.
 *
 * The service asks for a real User-Agent identifying the application and a contact, and
 * rate limits to roughly one request a second. Both are handled in CoverArtRepository.
 */
interface MusicBrainzApi {

    /**
     * Recordings matching an artist and a title.
     *
     * Lucene syntax, with the two fields quoted so a title containing "and" is not read as
     * a boolean. `inc=releases` brings back the releases the recording appears on, which is
     * what carries the cover.
     */
    @GET("ws/2/recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 5,
    ): MusicBrainzRecordingResponse
}

@Serializable
data class MusicBrainzRecordingResponse(
    val recordings: List<MusicBrainzRecording> = emptyList(),
)

@Serializable
data class MusicBrainzRecording(
    val id: String,
    val title: String = "",
    /** 0-100, how well MusicBrainz thinks this matches the query. */
    val score: Int = 0,
    val releases: List<MusicBrainzRelease> = emptyList(),
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
)

@Serializable
data class MusicBrainzRelease(
    val id: String,
    val title: String = "",
    /**
     * Album, single, EP - and the type matters: a song's cover should come from its album
     * rather than from a compilation it was later included on.
     */
    @SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroup? = null,
)

@Serializable
data class MusicBrainzReleaseGroup(
    val id: String,
    @SerialName("primary-type") val primaryType: String? = null,
)

@Serializable
data class MusicBrainzArtistCredit(val name: String = "")
