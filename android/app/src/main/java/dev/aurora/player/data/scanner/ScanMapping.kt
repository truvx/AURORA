package dev.aurora.player.data.scanner

import dev.aurora.player.data.db.AlbumEntity
import dev.aurora.player.data.db.ArtistEntity
import dev.aurora.player.data.db.ArtworkEntity
import dev.aurora.player.data.db.LocalFileEntity
import dev.aurora.player.data.db.MediaItemEntity
import dev.aurora.player.data.db.TrackLoudnessEntity
import dev.aurora.player.data.db.TrackTechnicalMetadataEntity
import dev.aurora.player.domain.models.ProviderKind

/**
 * One MediaStore row, read out of the cursor before anything has been decided about it.
 *
 * Separate from the scan so the decisions below can be tested. Everything that needs a
 * device - the cursor, the URIs, the metadata retriever - stays in LocalMusicScanner;
 * what an id is called and what happens to a missing tag is here.
 */
internal data class ScannedRow(
    val id: Long,
    val title: String?,
    val artist: String?,
    val artistId: Long,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    val mimeType: String?,
    val dateModified: Long,
)

internal object ScanMapping {

    /**
     * Ids other layers depend on.
     *
     * The resolver routes on the "local_" prefix, so these spellings are a contract, not a
     * formatting choice: change one and tracks stop resolving to a playable URI.
     */
    fun mediaId(rowId: Long): String = "local_$rowId"

    fun albumId(albumRowId: Long): String = "local_album_$albumRowId"

    fun artistId(artistRowId: Long): String = "local_artist_$artistRowId"

    /**
     * Whether the container is lossless.
     *
     * Only from what the file says. Claiming lossless for something that is not is the
     * kind of invented quality docs/AUDIO_QUALITY_POLICY.md rules out, so anything not
     * recognised is false rather than a guess.
     */
    fun isLossless(mimeType: String?): Boolean {
        val mime = mimeType?.lowercase() ?: return false
        return mime.contains("flac") || mime.contains("wav") || mime.contains("alac")
    }

    /**
     * A title, artist or album the file did not carry.
     *
     * MediaStore does not only use null for this: it stores the literal string "<unknown>",
     * which AURORA was showing to people verbatim under a track. It means the same thing as
     * a missing tag and is treated the same way.
     */
    private val MISSING = setOf("<unknown>", "unknown")

    private fun given(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in MISSING }

    fun titleOr(value: String?): String = given(value) ?: "Unknown Title"

    fun artistOr(value: String?): String = given(value) ?: "Unknown Artist"

    fun albumOr(value: String?): String = given(value) ?: "Unknown Album"

    fun scannedTrack(
        row: ScannedRow,
        uri: String,
        artworkUri: String,
        metadata: TrackTechnicalMetadataEntity,
        loudness: TrackLoudnessEntity?,
    ): ScannedTrack {
        val provider = ProviderKind.LOCAL.name
        val mediaId = mediaId(row.id)
        val albumId = albumId(row.albumId)
        val artistName = artistOr(row.artist)

        return ScannedTrack(
            item = MediaItemEntity(
                id = mediaId,
                provider = provider,
                kind = "TRACK",
                title = titleOr(row.title),
                provenance = "LOCAL",
                isAvailable = true,
                albumId = albumId,
            ),
            file = LocalFileEntity(
                uri = uri,
                mediaId = mediaId,
                permissionStatus = "GRANTED",
                size = row.size,
                dateModified = row.dateModified,
                importState = "SCANNED",
            ),
            metadata = metadata,
            album = AlbumEntity(
                id = albumId,
                provider = provider,
                title = albumOr(row.album),
                albumArtist = artistName,
                year = null,
                artworkRef = artworkUri,
            ),
            artists = listOf(
                ArtistEntity(
                    id = artistId(row.artistId),
                    provider = provider,
                    name = artistName,
                    artworkRef = null,
                )
            ),
            artwork = ArtworkEntity(
                sourceRef = artworkUri,
                width = null,
                height = null,
                paletteVersion = 1,
                cacheMetadata = null,
            ),
            loudness = loudness,
        )
    }

    /**
     * What a thrown error means to the person.
     *
     * A permission problem is not a failure of the app and should not be reported as one:
     * it is recoverable by granting access, and the library screen says so.
     */
    fun failureFor(error: Throwable): LocalScanResult = when (error) {
        is SecurityException -> LocalScanResult.PermissionDenied("SecurityException: ${error.message}")
        else -> LocalScanResult.Failed("Exception during scan: ${error.message}", error)
    }
}
