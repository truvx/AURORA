package dev.aurora.player.data.discovery

import dev.aurora.player.data.api.MusicBrainzApi
import dev.aurora.player.data.api.MusicBrainzArtistCredit
import dev.aurora.player.data.api.MusicBrainzRecording
import dev.aurora.player.data.api.MusicBrainzRecordingResponse
import dev.aurora.player.data.api.MusicBrainzRelease
import dev.aurora.player.data.api.MusicBrainzReleaseGroup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Finding the sleeve of the record behind a video.
 *
 * The rules that matter here are about when *not* to look: a guess is not worth a request
 * to a service that allows one a second, and a wrong sleeve is a worse answer than the
 * thumbnail the video already has.
 */
class CoverArtRepositoryTest {

    private class FakeMusicBrainz(
        private val response: MusicBrainzRecordingResponse = MusicBrainzRecordingResponse(),
        private val failure: Throwable? = null,
    ) : MusicBrainzApi {
        var calls = 0
        var lastQuery: String? = null

        override suspend fun searchRecording(query: String, format: String, limit: Int):
            MusicBrainzRecordingResponse {
            calls++
            lastQuery = query
            failure?.let { throw it }
            return response
        }
    }

    private fun recording(
        score: Int = 100,
        releases: List<MusicBrainzRelease> = listOf(
            MusicBrainzRelease("release-1", "GABRIEL", MusicBrainzReleaseGroup("rg-1", "Album"))
        ),
    ) = MusicBrainzRecordingResponse(
        listOf(
            MusicBrainzRecording(
                id = "rec-1",
                title = "Soft Spot",
                score = score,
                releases = releases,
                artistCredit = listOf(MusicBrainzArtistCredit("keshi")),
            )
        )
    )

    private fun repository(
        api: MusicBrainzApi,
        store: KeyValueStore = InMemoryStore(),
        coverExists: suspend (String) -> Boolean = { true },
    ) = CoverArtRepository(api, DiscoveryCache(store) { NOW }, coverExists) { NOW }

    @Test
    fun `finds the sleeve of the album a song came from`() = runTest {
        val api = FakeMusicBrainz(recording())

        val cover = repository(api).coverFor("keshi - Soft Spot (Official Music Video)", "KeshiVEVO")!!

        assertEquals("https://coverartarchive.org/release/release-1/front-500", cover.coverUrl)
        assertEquals("GABRIEL", cover.album)
        assertEquals("keshi", cover.identity.artist)
        assertEquals("Soft Spot", cover.identity.title)
    }

    @Test
    fun `asks about the song, not about the video`() = runTest {
        val api = FakeMusicBrainz(recording())

        repository(api).coverFor("keshi - Soft Spot (Official Music Video)", "KeshiVEVO")

        assertEquals("""artist:"keshi" AND recording:"Soft Spot"""", api.lastQuery)
    }

    @Test
    fun `prefers the album over a single or a compilation`() = runTest {
        val api = FakeMusicBrainz(
            recording(
                releases = listOf(
                    MusicBrainzRelease("comp", "Now That's What I Call Music", MusicBrainzReleaseGroup("rg-c", "Compilation")),
                    MusicBrainzRelease("single", "Soft Spot", MusicBrainzReleaseGroup("rg-s", "Single")),
                    MusicBrainzRelease("album", "GABRIEL", MusicBrainzReleaseGroup("rg-a", "Album")),
                )
            )
        )

        // The sleeve a listener associates with a song is the record it was written for.
        val cover = repository(api).coverFor("keshi - Soft Spot", "keshi - Topic")!!

        assertTrue(cover.coverUrl!!.contains("album"))
        assertEquals("GABRIEL", cover.album)
    }

    @Test
    fun `looks past a match whose sleeve was never uploaded`() = runTest {
        // Real case: searching for keshi's "Soft Spot" returns three recordings all
        // scoring 100. The first carries a single with no cover in the archive; the album
        // two recordings later has one.
        val api = FakeMusicBrainz(
            MusicBrainzRecordingResponse(
                listOf(
                    MusicBrainzRecording(
                        id = "rec-1", title = "Soft Spot", score = 100,
                        releases = listOf(
                            MusicBrainzRelease("single-no-art", "Soft Spot (alternate versions)", MusicBrainzReleaseGroup("rg-s", "Single"))
                        ),
                    ),
                    MusicBrainzRecording(
                        id = "rec-2", title = "Soft Spot", score = 100,
                        releases = listOf(
                            MusicBrainzRelease("album-with-art", "Requiem", MusicBrainzReleaseGroup("rg-a", "Album"))
                        ),
                    ),
                )
            )
        )
        val withArt = setOf("https://coverartarchive.org/release/album-with-art/front-500")

        val cover = repository(api, coverExists = { it in withArt })
            .coverFor("keshi - Soft Spot", "keshi - Topic")!!

        assertEquals("https://coverartarchive.org/release/album-with-art/front-500", cover.coverUrl)
        assertEquals("Requiem", cover.album)
    }

    @Test
    fun `no sleeve anywhere is no sleeve, not a broken link`() = runTest {
        val api = FakeMusicBrainz(recording())

        // Handing the image loader a URL that 404s leaves the player with no artwork at
        // all, which is worse than the thumbnail it would have replaced.
        val cover = repository(api, coverExists = { false })
            .coverFor("keshi - Soft Spot", "keshi - Topic")!!

        assertNull(cover.coverUrl)
    }

    @Test
    fun `a weak match is no match`() = runTest {
        val api = FakeMusicBrainz(recording(score = 40))

        val cover = repository(api).coverFor("keshi - Soft Spot", "keshi - Topic")!!

        // Below the threshold MusicBrainz is guessing, and the wrong sleeve is worse than
        // the thumbnail the video already has.
        assertNull(cover.coverUrl)
    }

    @Test
    fun `a guess is not worth a request`() = runTest {
        val api = FakeMusicBrainz(recording())

        // A channel name is not an artist, so this identity is unconfident.
        val cover = repository(api).coverFor("1-hour mix with seamless transitions", "Morris C.")!!

        assertEquals(0, api.calls)
        assertNull(cover.coverUrl)
    }

    @Test
    fun `a video that is not a song is not looked up at all`() = runTest {
        val api = FakeMusicBrainz(recording())

        assertNull(repository(api).coverFor("", null))
        assertEquals(0, api.calls)
    }

    @Test
    fun `an answer is remembered, including the answer that there is none`() = runTest {
        val store = InMemoryStore()
        val api = FakeMusicBrainz(MusicBrainzRecordingResponse(emptyList()))

        repository(api, store).coverFor("keshi - Soft Spot", "keshi - Topic")
        repository(api, store).coverFor("keshi - Soft Spot", "keshi - Topic")

        // An artist who is not in MusicBrainz would otherwise be looked up on every play,
        // at a service that asks for one request a second.
        assertEquals(1, api.calls)
    }

    @Test
    fun `being offline is not remembered as having no cover`() = runTest {
        val store = InMemoryStore()
        val offline = FakeMusicBrainz(failure = IOException("offline"))

        val cover = repository(offline, store).coverFor("keshi - Soft Spot", "keshi - Topic")!!
        assertNull(cover.coverUrl)

        // The next attempt, online, must still be allowed to find one: a failed request
        // says nothing about whether a cover exists.
        val online = FakeMusicBrainz(recording())
        val second = repository(online, store).coverFor("keshi - Soft Spot", "keshi - Topic")!!
        assertEquals("https://coverartarchive.org/release/release-1/front-500", second.coverUrl)
    }

    @Test
    fun `an artist whose name is Lucene syntax does not break the query`() = runTest {
        val api = FakeMusicBrainz(recording())

        repository(api).coverFor("AC/DC - Thunderstruck", "AC/DC - Topic")

        assertEquals("""artist:"AC\/DC" AND recording:"Thunderstruck"""", api.lastQuery)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
