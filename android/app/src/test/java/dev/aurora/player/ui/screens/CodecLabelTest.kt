package dev.aurora.player.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecLabelTest {

    @Test
    fun namesTheCodecsTheScannerStoresAsMimeTypes() {
        assertEquals("MP3", codecLabel("audio/mpeg", "audio/mpeg"))
        assertEquals("AAC", codecLabel("audio/mp4a-latm", "audio/mp4"))
        assertEquals("FLAC", codecLabel("audio/flac", "audio/flac"))
        assertEquals("OPUS", codecLabel("audio/opus", "audio/ogg"))
        assertEquals("VORBIS", codecLabel("audio/vorbis", "audio/ogg"))
        assertEquals("ALAC", codecLabel("audio/alac", "audio/mp4"))
    }

    @Test
    fun rawPcmIsNamedByItsContainer() {
        assertEquals("WAV", codecLabel("audio/raw", "audio/x-wav"))
        assertEquals("PCM", codecLabel("audio/raw", null))
    }

    @Test
    fun unknownStaysUnknownOrIsShownAsWritten() {
        assertNull(codecLabel(null, null))
        assertNull(codecLabel("  ", null))
        assertEquals("AC3", codecLabel("audio/ac3", null))
    }
}
