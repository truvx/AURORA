package dev.aurora.player.domain.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taking a song back out of a video title.
 *
 * The cases below are real results from a search for "keshi", which is where this started:
 * the app was showing "keshi - Soft Spot (Official Music Video)" as the name of the song
 * and the channel as the artist.
 */
class TrackIdentifierTest {

    @Test
    fun `splits the usual artist and title`() {
        val identity = TrackIdentifier.identify("keshi - Soft Spot (Official Music Video)", "KeshiVEVO")!!

        assertEquals("keshi", identity.artist)
        assertEquals("Soft Spot", identity.title)
        assertTrue(identity.confident)
    }

    @Test
    fun `strips the ways an upload describes itself`() {
        val cases = mapOf(
            "keshi - LIMBO (Lyric Video)" to "LIMBO",
            "keshi - UNDERSTAND (Visualizer)" to "UNDERSTAND",
            "Artist - Song (Official Video)" to "Song",
            "Artist - Song [Official Audio]" to "Song",
            "Artist - Song (Official Music Video) [4K]" to "Song",
            "Artist - Song (M/V)" to "Song",
            "Artist - Song (Lyrics)" to "Song",
            "Artist - Song (Explicit)" to "Song",
        )
        for ((raw, expected) in cases) {
            assertEquals(raw, expected, TrackIdentifier.identify(raw, null)!!.title)
        }
    }

    @Test
    fun `keeps the brackets that are part of the record`() {
        // Not every parenthesis is noise. These say what the recording is.
        assertEquals(
            "Everything In Its Right Place (Live)",
            TrackIdentifier.identify("Radiohead - Everything In Its Right Place (Live)", null)!!.title
        )
        assertEquals(
            "Redbone (Childish Gambino Cover)",
            TrackIdentifier.identify("Someone - Redbone (Childish Gambino Cover)", null)!!.title
        )
        assertEquals(
            "Song (feat. Another Artist)",
            TrackIdentifier.identify("Artist - Song (feat. Another Artist)", null)!!.title
        )
    }

    @Test
    fun `reads an auto-generated artist channel`() {
        // "X - Topic" is YouTube's own channel for an artist's catalogue, so the channel
        // is the artist even when the title carries nothing but the song.
        val identity = TrackIdentifier.identify("i swear i'll never leave again", "keshi - Topic")!!

        assertEquals("keshi", identity.artist)
        assertEquals("i swear i'll never leave again", identity.title)
        assertTrue(identity.confident)
    }

    @Test
    fun `drops what an uploader appended after a pipe`() {
        val identity = TrackIdentifier.identify(
            "Artist - Song | Morris C.",
            "Morris C."
        )!!

        assertEquals("Song", identity.title)
    }

    @Test
    fun `a channel name is offered but not trusted as an artist`() {
        // "KeshiVEVO" is a label's channel and "Morris C." is someone who makes mixes.
        // Neither is the artist, so this is a guess and says so.
        val identity = TrackIdentifier.identify("1-hour mix with seamless transitions", "Morris C.")!!

        assertEquals("Morris C.", identity.artist)
        assertFalse(identity.confident)
    }

    @Test
    fun `handles the other dashes uploaders use`() {
        for (dash in listOf("–", "—", "~")) {
            val identity = TrackIdentifier.identify("Artist $dash Song", null)!!
            assertEquals("Artist", identity.artist)
            assertEquals("Song", identity.title)
        }
    }

    @Test
    fun `nothing usable is nothing, not an empty artist`() {
        assertNull(TrackIdentifier.identify("", "Some Channel"))
        assertNull(TrackIdentifier.identify("(Official Video)", null))
        assertNull(TrackIdentifier.identify("Song With No Channel", null))
    }

    @Test
    fun `the lookup query is the artist and the song, not the video`() {
        val identity = TrackIdentifier.identify("keshi - Soft Spot (Official Music Video)", "KeshiVEVO")!!

        assertEquals("keshi Soft Spot", identity.searchQuery)
    }
}
