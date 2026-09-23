package dev.aurora.player.ui.screens

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.models.TrackTechnicalMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two small facts under the scrubber.
 *
 * The reference prints "studio album · 2018" and "19 songs · 52m" from a catalogue AURORA
 * does not have. These come from the file itself, and where there is nothing to say they
 * say nothing - an empty row rather than a plausible-looking guess.
 */
class TrackFactsTest {

    private fun local(metadata: TrackTechnicalMetadata? = null, album: String? = null) = MediaItem(
        id = "local_1",
        provider = ProviderKind.LOCAL,
        title = "Blue Bird",
        artist = "Ikimonogakari",
        album = album,
        artworkUri = null,
        technicalMetadata = metadata,
    )

    private fun metadata(
        container: String? = "audio/flac",
        bitrate: Int? = 1_024_000,
        sampleRate: Int? = 44_100,
        lossless: Boolean = true,
    ) = TrackTechnicalMetadata(
        codec = "audio/flac",
        container = container,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = 2,
        bitDepth = 16,
        durationMs = 215_000,
        isLossless = lossless,
    )

    @Test
    fun `a local file says what it is`() {
        val (left, right) = trackFacts(local(metadata(), album = "Hanabi"))

        assertEquals("FLAC · 1024 kbps", left)
        assertEquals("Hanabi · Lossless", right)
    }

    @Test
    fun `a lossy file is not called lossless`() {
        val (left, right) = trackFacts(
            local(metadata(container = "audio/mpeg", bitrate = 320_000, lossless = false), album = "Hanabi")
        )

        assertEquals("MPEG · 320 kbps", left)
        assertEquals("Hanabi", right)
    }

    @Test
    fun `missing metadata leaves the row empty rather than inventing one`() {
        val (left, right) = trackFacts(local())

        assertNull(left)
        assertNull(right)
    }

    @Test
    fun `an unknown bitrate falls back to what is known`() {
        val (left, _) = trackFacts(local(metadata(bitrate = null)))

        // Sample rate takes the second slot rather than a blank or a guessed bitrate.
        assertEquals("FLAC · 44.1 kHz", left)
    }

    @Test
    fun `YouTube says only that it is YouTube`() {
        val youtube = MediaItem(
            id = "youtube:abc",
            provider = ProviderKind.YOUTUBE,
            title = "A video",
            artist = "A channel",
            album = null,
            artworkUri = null,
            // Even if something had filled this in, a bitrate here would be invented:
            // the capability matrix makes YouTube quality provider-determined and unknown.
            technicalMetadata = metadata(),
        )

        val (left, right) = trackFacts(youtube)

        assertEquals("YouTube", left)
        assertNull(right)
    }

    @Test
    fun `nothing playing says nothing`() {
        assertEquals(null to null, trackFacts(null))
    }
}
