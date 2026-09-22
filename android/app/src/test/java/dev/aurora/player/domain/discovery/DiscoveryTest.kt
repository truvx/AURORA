package dev.aurora.player.domain.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAffinityTest {

    private val now = 1_000_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private fun signal(artist: String, kind: SignalKind, daysAgo: Int = 0) =
        ArtistSignal(artist, kind, now - daysAgo * day)

    @Test
    fun `completions outrank plays`() {
        val top = ArtistAffinity.topArtists(
            listOf(
                signal("Keshi", SignalKind.COMPLETE),
                signal("Joji", SignalKind.PLAY),
                signal("Joji", SignalKind.PLAY),
            ),
            now
        )
        assertEquals(listOf("Keshi", "Joji"), top)
    }

    @Test
    fun `recent listening outweighs the same amount of old listening`() {
        val top = ArtistAffinity.topArtists(
            listOf(signal("Old", SignalKind.COMPLETE, daysAgo = 120), signal("New", SignalKind.COMPLETE)),
            now
        )
        assertEquals("New", top.first())
    }

    @Test
    fun `an artist the listener keeps skipping drops out`() {
        val top = ArtistAffinity.topArtists(
            listOf(
                signal("Skipped", SignalKind.PLAY),
                signal("Skipped", SignalKind.SKIP),
                signal("Skipped", SignalKind.SKIP),
                signal("Liked", SignalKind.PLAY),
            ),
            now
        )
        assertEquals(listOf("Liked"), top)
    }

    @Test
    fun `the same artist from a tag and a YouTube channel groups as one`() {
        val top = ArtistAffinity.topArtists(
            listOf(
                signal("Keshi", SignalKind.PLAY),
                signal("keshi - Topic", SignalKind.PLAY),
                signal("KeshiVEVO", SignalKind.PLAY),
                // One play, so only the grouping - three plays as one artist - can put Keshi
                // first. (A completion would score 3.0 and tie.)
                signal("Joji", SignalKind.PLAY),
            ),
            now
        )
        assertEquals(2, top.size)
        assertEquals("Keshi", top.first())
    }

    @Test
    fun `placeholder artist tags are never ranked`() {
        val top = ArtistAffinity.topArtists(
            listOf(signal("Unknown Artist", SignalKind.FAVORITE), signal("  ", SignalKind.FAVORITE)),
            now
        )
        assertTrue(top.isEmpty())
    }

    @Test
    fun `respects the limit`() {
        val signals = (1..10).map { signal("Artist $it", SignalKind.PLAY) }
        assertEquals(3, ArtistAffinity.topArtists(signals, now, limit = 3).size)
    }
}

class ChannelMatcherTest {

    private fun c(id: String, title: String) = ChannelMatcher.Candidate(id, title)

    @Test
    fun `prefers the official Topic channel`() {
        val picked = ChannelMatcher.pick(
            "Keshi",
            listOf(c("fan", "Keshi"), c("topic", "keshi - Topic"))
        )
        assertEquals("topic", picked)
    }

    @Test
    fun `falls back to an exact name match`() {
        assertEquals("main", ChannelMatcher.pick("Joji", listOf(c("x", "Joji Fans"), c("main", "joji"))))
    }

    @Test
    fun `refuses a near match rather than guessing`() {
        // A wrong channel would fill Latest Releases with a stranger's music.
        assertNull(ChannelMatcher.pick("Joji", listOf(c("x", "Joji Official Lyrics"), c("y", "JojiCovers"))))
    }
}

class MixCatalogTest {

    @Test
    fun `mixes are seeded with the listener's top artists`() {
        val mixes = MixCatalog.mixesFor(listOf("Keshi", "Joji", "Laufey"))
        assertTrue(mixes.all { it.query.contains("Keshi") && it.query.contains("Joji") })
        assertTrue(mixes.none { it.query.contains("Laufey") })
        assertTrue(mixes.all { it.subtitle == "Made for you" })
    }

    @Test
    fun `without history a mix does not claim to be personal`() {
        val mixes = MixCatalog.mixesFor(emptyList())
        assertTrue(mixes.none { it.subtitle == "Made for you" })
        assertEquals(MixMood.entries.size, mixes.size)
    }

    @Test
    fun `mix ids are stable`() {
        assertEquals(MixCatalog.mixesFor(emptyList()).map { it.id }, MixCatalog.mixesFor(listOf("A")).map { it.id })
    }
}
