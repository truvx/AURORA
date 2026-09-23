package dev.aurora.player.data.scanner

import dev.aurora.player.data.db.TrackTechnicalMetadataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the local scanner decides about a MediaStore row.
 *
 * The scan itself needs a device, but none of these decisions do, and they are the ones
 * that break things quietly: an id that no longer matches what the resolver routes on, a
 * missing tag turning into a blank row, or a file being called lossless because its name
 * happened to contain "wav".
 */
class ScanMappingTest {

    private val row = ScannedRow(
        id = 42,
        title = "Blue Bird",
        artist = "Ikimonogakari",
        artistId = 7,
        album = "Hanabi",
        albumId = 3,
        durationMs = 215_000,
        size = 8_200_000,
        mimeType = "audio/mpeg",
        dateModified = 1_700_000_000,
    )

    private val metadata = TrackTechnicalMetadataEntity(
        mediaId = "local_42",
        codec = "audio/mpeg",
        container = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100,
        channels = 2,
        bitDepth = null,
        durationMs = 215_000,
        isLossless = false,
        extractionVersion = 1,
        sourceVersion = 1,
    )

    private fun scanned(from: ScannedRow = row) =
        ScanMapping.scannedTrack(from, "content://audio/42", "content://albumart/3", metadata, null)

    @Test
    fun `ids keep the prefixes the rest of the app routes on`() {
        val track = scanned()

        // The resolver decides between local playback and YouTube on this prefix. If these
        // spellings drift, a track resolves to no URI and simply will not play.
        assertEquals("local_42", track.item.id)
        assertEquals("local_42", track.file.mediaId)
        assertEquals("local_album_3", track.item.albumId)
        assertEquals("local_album_3", track.album?.id)
        assertEquals("local_artist_7", track.artists.single().id)
    }

    @Test
    fun `a row keeps its own file and size`() {
        val track = scanned()

        assertEquals("content://audio/42", track.file.uri)
        assertEquals(8_200_000, track.file.size)
        assertEquals(1_700_000_000, track.file.dateModified)
        assertEquals("content://albumart/3", track.album?.artworkRef)
    }

    @Test
    fun `missing tags are named, not left blank`() {
        val track = scanned(row.copy(title = null, artist = null, album = null))

        assertEquals("Unknown Title", track.item.title)
        assertEquals("Unknown Artist", track.artists.single().name)
        assertEquals("Unknown Album", track.album?.title)
    }

    @Test
    fun `a tag that is only whitespace counts as missing`() {
        // MediaStore hands back "   " for some files, and a row titled with three spaces
        // is unfindable and looks broken.
        val track = scanned(row.copy(title = "   ", artist = "\t", album = ""))

        assertEquals("Unknown Title", track.item.title)
        assertEquals("Unknown Artist", track.artists.single().name)
        assertEquals("Unknown Album", track.album?.title)
    }

    @Test
    fun `the album artist follows the track artist, including when it is unknown`() {
        assertEquals("Ikimonogakari", scanned().album?.albumArtist)
        assertEquals("Unknown Artist", scanned(row.copy(artist = null)).album?.albumArtist)
    }

    @Test
    fun `lossless is only claimed for containers that are lossless`() {
        assertTrue(ScanMapping.isLossless("audio/flac"))
        assertTrue(ScanMapping.isLossless("audio/x-wav"))
        assertTrue(ScanMapping.isLossless("audio/alac"))
        // Case is not guaranteed by MediaStore or by a metadata retriever.
        assertTrue(ScanMapping.isLossless("AUDIO/FLAC"))

        assertFalse(ScanMapping.isLossless("audio/mpeg"))
        assertFalse(ScanMapping.isLossless("audio/mp4"))
        assertFalse(ScanMapping.isLossless("audio/ogg"))
        // Unknown must stay unknown rather than becoming a claim.
        assertFalse(ScanMapping.isLossless(null))
        assertFalse(ScanMapping.isLossless(""))
    }

    @Test
    fun `loudness stays absent when the file carried no tags`() {
        // Not zero, not a default: a file with no ReplayGain has unknown loudness, and
        // the normalisation policy depends on being able to tell those apart.
        assertNull(scanned().loudness)
    }

    @Test
    fun `a permission problem is not reported as a failure`() {
        val result = ScanMapping.failureFor(SecurityException("READ_MEDIA_AUDIO denied"))

        // It is recoverable by granting access, and the library screen offers that. Calling
        // it a failure sends the person looking for a broken app instead.
        assertTrue(result is LocalScanResult.PermissionDenied)
        assertTrue((result as LocalScanResult.PermissionDenied).reason.contains("denied"))
    }

    @Test
    fun `anything else is a failure that keeps its cause`() {
        val cause = IllegalStateException("cursor closed")
        val result = ScanMapping.failureFor(cause)

        assertTrue(result is LocalScanResult.Failed)
        assertEquals(cause, (result as LocalScanResult.Failed).exception)
        assertTrue(result.reason.contains("cursor closed"))
    }
}
