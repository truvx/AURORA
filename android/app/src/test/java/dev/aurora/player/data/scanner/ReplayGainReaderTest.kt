package dev.aurora.player.data.scanner

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies ReplayGain tags are read from real tag layouts, and that anything unreadable
 * yields null (unknown) rather than a fabricated value.
 */
class ReplayGainReaderTest {

    private val tolerance = 0.0001f

    // --- FLAC ---------------------------------------------------------------------------

    @Test
    fun `reads all four values from a FLAC vorbis comment`() {
        val file = flac(
            "REPLAYGAIN_TRACK_GAIN=-6.48 dB",
            "REPLAYGAIN_TRACK_PEAK=0.987654",
            "REPLAYGAIN_ALBUM_GAIN=-7.25 dB",
            "REPLAYGAIN_ALBUM_PEAK=1.012345"
        )

        val tags = ReplayGainReader.read(file.inputStream())

        assertNotNull(tags)
        assertEquals(-6.48f, tags!!.trackGainDb!!, tolerance)
        assertEquals(0.987654f, tags.trackPeak!!, tolerance)
        assertEquals(-7.25f, tags.albumGainDb!!, tolerance)
        assertEquals(1.012345f, tags.albumPeak!!, tolerance)
    }

    @Test
    fun `skips non-comment metadata blocks`() {
        // The comment block sits behind a STREAMINFO block, as in a real FLAC file.
        val tags = ReplayGainReader.read(flac("REPLAYGAIN_TRACK_GAIN=+2.10 dB").inputStream())

        assertEquals(2.10f, tags!!.trackGainDb!!, tolerance)
    }

    @Test
    fun `leaves absent tags null`() {
        val tags = ReplayGainReader.read(flac("REPLAYGAIN_TRACK_GAIN=-3.00 dB").inputStream())

        assertEquals(-3.0f, tags!!.trackGainDb!!, tolerance)
        assertNull(tags.trackPeak)
        assertNull(tags.albumGainDb)
        assertNull(tags.albumPeak)
    }

    @Test
    fun `tag keys are matched case-insensitively`() {
        val tags = ReplayGainReader.read(flac("replaygain_track_gain=-4.50 dB").inputStream())

        assertEquals(-4.5f, tags!!.trackGainDb!!, tolerance)
    }

    @Test
    fun `a bare number without a dB suffix is accepted`() {
        val tags = ReplayGainReader.read(flac("REPLAYGAIN_TRACK_GAIN=-5.25").inputStream())

        assertEquals(-5.25f, tags!!.trackGainDb!!, tolerance)
    }

    @Test
    fun `unrelated comments are ignored`() {
        val tags = ReplayGainReader.read(
            flac("TITLE=Some Song", "ARTIST=Someone", "REPLAYGAIN_TRACK_GAIN=-1.00 dB").inputStream()
        )

        assertEquals(-1.0f, tags!!.trackGainDb!!, tolerance)
    }

    @Test
    fun `a FLAC without replaygain tags returns null`() {
        assertNull(ReplayGainReader.read(flac("TITLE=Some Song").inputStream()))
    }

    @Test
    fun `a non-positive peak is rejected`() {
        val tags = ReplayGainReader.read(
            flac("REPLAYGAIN_TRACK_GAIN=-1.00 dB", "REPLAYGAIN_TRACK_PEAK=0.0").inputStream()
        )

        assertEquals(-1.0f, tags!!.trackGainDb!!, tolerance)
        assertNull(tags.trackPeak)
    }

    // --- ID3v2 --------------------------------------------------------------------------

    @Test
    fun `reads TXXX frames from ID3v2 4`() {
        val file = id3(
            majorVersion = 4,
            "REPLAYGAIN_TRACK_GAIN" to "-8.15 dB",
            "REPLAYGAIN_TRACK_PEAK" to "0.755000"
        )

        val tags = ReplayGainReader.read(file.inputStream())

        assertEquals(-8.15f, tags!!.trackGainDb!!, tolerance)
        assertEquals(0.755f, tags.trackPeak!!, tolerance)
    }

    @Test
    fun `reads TXXX frames from ID3v2 3`() {
        // v2.3 frame sizes are plain big-endian rather than synchsafe.
        val file = id3(majorVersion = 3, "REPLAYGAIN_ALBUM_GAIN" to "-2.40 dB")

        val tags = ReplayGainReader.read(file.inputStream())

        assertEquals(-2.4f, tags!!.albumGainDb!!, tolerance)
    }

    @Test
    fun `ignores non-replaygain TXXX frames`() {
        val file = id3(
            majorVersion = 4,
            "MusicBrainz Album Id" to "abc-123",
            "REPLAYGAIN_TRACK_GAIN" to "-9.00 dB"
        )

        val tags = ReplayGainReader.read(file.inputStream())

        assertEquals(-9.0f, tags!!.trackGainDb!!, tolerance)
    }

    @Test
    fun `ID3v2 2 is unsupported and returns null`() {
        assertNull(ReplayGainReader.read(id3(majorVersion = 2).inputStream()))
    }

    // --- unsupported and malformed input ------------------------------------------------

    @Test
    fun `an unrecognised container returns null`() {
        assertNull(ReplayGainReader.read("RIFFsomething".toByteArray().inputStream()))
    }

    @Test
    fun `an empty stream returns null`() {
        assertNull(ReplayGainReader.read(ByteArray(0).inputStream()))
    }

    @Test
    fun `a truncated FLAC returns null instead of throwing`() {
        val full = flac("REPLAYGAIN_TRACK_GAIN=-6.48 dB")

        assertNull(ReplayGainReader.read(full.copyOf(full.size / 2).inputStream()))
    }

    @Test
    fun `a declared comment count larger than the block does not overrun`() {
        val payload = ByteArrayOutputStream().apply {
            writeU32Le(0)      // empty vendor string
            writeU32Le(50)     // claims 50 comments that are not present
        }.toByteArray()

        val file = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray())
            write(0x84)        // last block, type 4
            writeU24Be(payload.size)
            write(payload)
        }.toByteArray()

        assertNull(ReplayGainReader.read(file.inputStream()))
    }

    // --- synthetic file builders --------------------------------------------------------

    private fun flac(vararg comments: String): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            val vendor = "AURORA test".toByteArray()
            writeU32Le(vendor.size)
            write(vendor)
            writeU32Le(comments.size)
            comments.forEach {
                val bytes = it.toByteArray()
                writeU32Le(bytes.size)
                write(bytes)
            }
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("fLaC".toByteArray())
            write(0x00)                 // STREAMINFO, not last
            writeU24Be(34)
            write(ByteArray(34))
            write(0x84)                 // VORBIS_COMMENT, last
            writeU24Be(payload.size)
            write(payload)
        }.toByteArray()
    }

    private fun id3(majorVersion: Int, vararg frames: Pair<String, String>): ByteArray {
        val body = ByteArrayOutputStream().apply {
            frames.forEach { (description, value) ->
                val payload = ByteArrayOutputStream().apply {
                    write(0x00) // ISO-8859-1
                    write(description.toByteArray(Charsets.ISO_8859_1))
                    write(0x00)
                    write(value.toByteArray(Charsets.ISO_8859_1))
                }.toByteArray()

                write("TXXX".toByteArray())
                if (majorVersion == 4) writeSynchsafe(payload.size) else writeU32Be(payload.size)
                write(0x00)
                write(0x00)
                write(payload)
            }
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray())
            write(majorVersion)
            write(0x00)
            write(0x00)
            writeSynchsafe(body.size)
            write(body)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeU32Le(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU32Be(value: Int) {
        write((value shr 24) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeU24Be(value: Int) {
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeSynchsafe(value: Int) {
        write((value shr 21) and 0x7F)
        write((value shr 14) and 0x7F)
        write((value shr 7) and 0x7F)
        write(value and 0x7F)
    }
}
