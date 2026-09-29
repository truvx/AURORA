package dev.aurora.player.domain.library

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Albums and artists from tags.
 *
 * These rules mirror the Web client's lib/library/grouping.ts. The two clients disagreeing
 * about what an album is would be a worse bug than either being wrong alone, so the cases
 * here are the same cases.
 */
class TrackGroupingTest {

    private fun track(id: String, title: String = id, artist: String? = null, album: String? = null) =
        MediaItem(
            id = id,
            provider = ProviderKind.LOCAL,
            title = title,
            artist = artist,
            album = album,
            artworkUri = null,
        )

    @Test
    fun `tracks collect under their album`() {
        val groups = TrackGrouping.byAlbum(
            listOf(
                track("1", album = "Requiem", artist = "keshi"),
                track("2", album = "Requiem", artist = "keshi"),
                track("3", album = "Gabriel", artist = "keshi"),
            )
        )

        assertEquals(listOf("Gabriel", "Requiem"), groups.map { it.name })
        assertEquals(listOf("1", "2"), groups.first { it.name == "Requiem" }.trackIds)
    }

    @Test
    fun `an album artist is only claimed when every track agrees`() {
        // A compilation stays unattributed rather than credited to whoever is first.
        val compilation = TrackGrouping.byAlbum(
            listOf(
                track("1", album = "Now That's What I Call Music", artist = "A"),
                track("2", album = "Now That's What I Call Music", artist = "B"),
            )
        ).single()
        assertNull(compilation.artist)

        val album = TrackGrouping.byAlbum(
            listOf(track("1", album = "Requiem", artist = "keshi"), track("2", album = "Requiem", artist = "keshi"))
        ).single()
        assertEquals("keshi", album.artist)
    }

    @Test
    fun `missing tags collect under an explicit unknown rather than disappearing`() {
        val albums = TrackGrouping.byAlbum(listOf(track("1"), track("2", album = "   ")))

        assertEquals(listOf(TrackGrouping.UNKNOWN_ALBUM), albums.map { it.name })
        assertEquals(listOf("1", "2"), albums.single().trackIds)
    }

    @Test
    fun `unknown sorts last, because it is a catch-all and not a name`() {
        // Sorting it by its letter would bury real albums under whatever lacks tags.
        val albums = TrackGrouping.byAlbum(
            listOf(track("1", album = "Zebra"), track("2"), track("3", album = "Apple"))
        )

        assertEquals(listOf("Apple", "Zebra", TrackGrouping.UNKNOWN_ALBUM), albums.map { it.name })
    }

    @Test
    fun `artists group the same way`() {
        val artists = TrackGrouping.byArtist(
            listOf(track("1", artist = "keshi"), track("2", artist = "keshi"), track("3"))
        )

        assertEquals(listOf("keshi", TrackGrouping.UNKNOWN_ARTIST), artists.map { it.name })
        assertEquals(listOf("1", "2"), artists.first().trackIds)
    }

    @Test
    fun `nothing in gives nothing out`() {
        assertEquals(emptyList<TrackGroup>(), TrackGrouping.byAlbum(emptyList()))
        assertEquals(emptyList<TrackGroup>(), TrackGrouping.byArtist(emptyList()))
    }
}
