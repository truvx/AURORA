package dev.aurora.player.domain.discovery

import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.library.ListeningHistoryRecord
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an artist's page shows, and why that ordering is honest.
 *
 * "Top songs" is this listener's play count and nothing else - AURORA has no chart data -
 * so the ranking has to be defensible: skips must not count, and tracks nobody has played
 * need a stable place rather than scan order.
 */
class ArtistPageTest {

    private fun track(id: String, title: String, artist: String?) = MediaItem(
        id = id,
        provider = ProviderKind.LOCAL,
        title = title,
        artist = artist,
        album = null,
        artworkUri = null,
    )

    private fun play(id: String, kind: ListeningEventKind = ListeningEventKind.PLAY) =
        ListeningHistoryRecord(
            mediaId = id,
            provider = "LOCAL",
            timestamp = 0,
            kind = kind,
            progressMs = 0,
        )

    private val library = listOf(
        track("1", "Blue Bird", "Ikimonogakari"),
        track("2", "Hotaru no Hikari", "ikimonogakari"),
        track("3", "Sakura", " Ikimonogakari "),
        track("4", "Unrelated", "Someone Else"),
        track("5", "Untagged", null),
    )

    @Test
    fun `an artist's tracks are found however the tagger wrote the name`() {
        val theirs = ArtistPage.tracksBy("Ikimonogakari", library)

        // Case and stray spaces vary between taggers and between providers; three files
        // by the same artist must not become three artists.
        assertEquals(listOf("1", "2", "3"), theirs.map { it.id })
    }

    @Test
    fun `an untagged track belongs to no one`() {
        assertEquals(emptyList<MediaItem>(), ArtistPage.tracksBy("", library))
        assertEquals(emptyList<MediaItem>(), ArtistPage.tracksBy("   ", library))
    }

    @Test
    fun `most played comes first`() {
        val plays = ArtistPage.playCounts(
            listOf(play("2"), play("2"), play("2"), play("1"), play("3", ListeningEventKind.COMPLETE))
        )

        val top = ArtistPage.topSongs(ArtistPage.tracksBy("Ikimonogakari", library), plays)

        assertEquals(listOf("2", "1", "3"), top.map { it.id })
    }

    @Test
    fun `skips do not count as listening`() {
        // A track started and abandoned eleven times is not anyone's favourite, and
        // counting it as one is how "top songs" becomes "songs you keep skipping".
        val plays = ArtistPage.playCounts(
            List(11) { play("3", ListeningEventKind.SKIP) } + play("1")
        )

        assertEquals(mapOf("1" to 1), plays)
        assertEquals("1", ArtistPage.topSongs(ArtistPage.tracksBy("Ikimonogakari", library), plays).first().id)
    }

    @Test
    fun `unplayed tracks are alphabetical rather than in scan order`() {
        val top = ArtistPage.topSongs(ArtistPage.tracksBy("Ikimonogakari", library), emptyMap())

        assertEquals(listOf("Blue Bird", "Hotaru no Hikari", "Sakura"), top.map { it.title })
    }

    @Test
    fun `the list is bounded`() {
        val many = (1..50).map { track("id$it", "Track $it", "A") }
        assertEquals(20, ArtistPage.topSongs(many, emptyMap()).size)
        assertEquals(5, ArtistPage.topSongs(many, emptyMap(), limit = 5).size)
    }
}
