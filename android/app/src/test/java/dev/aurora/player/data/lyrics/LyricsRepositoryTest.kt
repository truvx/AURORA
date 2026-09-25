package dev.aurora.player.data.lyrics

import dev.aurora.player.data.api.LrcLibApi
import dev.aurora.player.data.api.LrcLibRecord
import dev.aurora.player.data.discovery.DiscoveryCache
import dev.aurora.player.data.discovery.InMemoryStore
import dev.aurora.player.domain.discovery.TrackIdentifier
import dev.aurora.player.domain.discovery.TrackIdentity
import dev.aurora.player.domain.lyrics.Lyrics
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Finding the words of what is playing.
 *
 * The rules that matter are about refusing: a lyric is read along with, so the lyric of a
 * different edit is not a near miss - it drifts further out of time with every chorus.
 */
class LyricsRepositoryTest {

    private class FakeLrcLib(
        private val records: List<LrcLibRecord> = emptyList(),
        private val failure: Throwable? = null,
    ) : LrcLibApi {
        var calls = 0
        var lastArtist: String? = null
        var lastTrack: String? = null

        override suspend fun search(artist: String, track: String): List<LrcLibRecord> {
            calls++
            lastArtist = artist
            lastTrack = track
            failure?.let { throw it }
            return records
        }
    }

    private fun record(
        durationSeconds: Double?,
        synced: String? = null,
        plain: String? = "the words",
        instrumental: Boolean = false,
        name: String = "Soft Spot",
    ) = LrcLibRecord(
        trackName = name,
        artistName = "keshi",
        duration = durationSeconds,
        instrumental = instrumental,
        plainLyrics = plain,
        syncedLyrics = synced,
    )

    private fun repository(api: LrcLibApi, store: InMemoryStore = InMemoryStore()) =
        LyricsRepository(api, DiscoveryCache(store) { NOW })

    /** What the cover lookup would have established, read the same way it reads it. */
    private fun identity(videoTitle: String, channel: String?): TrackIdentity? =
        TrackIdentifier.identify(videoTitle, channel)

    @Test
    fun `picks the edit whose length matches what is playing`() = runTest {
        val api = FakeLrcLib(
            listOf(
                record(durationSeconds = 240.0, plain = "the long version"),
                record(durationSeconds = 189.0, plain = "the album version"),
            )
        )

        val result = repository(api).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        val found = result as LyricsRepository.Result.Found
        assertEquals("the album version", (found.lyrics as Lyrics.Plain).text)
    }

    @Test
    fun `refuses every candidate when none is the right length`() = runTest {
        // A live take and a remix, and neither is the recording on screen.
        val api = FakeLrcLib(listOf(record(durationSeconds = 300.0), record(durationSeconds = 120.0)))

        val result = repository(api).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        assertEquals(LyricsRepository.Result.None, result)
    }

    @Test
    fun `prefers a timed file over a plain one at the same length`() = runTest {
        val api = FakeLrcLib(
            listOf(
                record(durationSeconds = 189.0, plain = "plain only"),
                record(durationSeconds = 189.0, synced = "[00:01.00]timed", plain = "also plain"),
            )
        )

        val result = repository(api).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        val found = result as LyricsRepository.Result.Found
        assertTrue(found.lyrics is Lyrics.Synced)
    }

    @Test
    fun `an instrumental is a finding, not an absence`() = runTest {
        val api = FakeLrcLib(listOf(record(durationSeconds = 189.0, plain = null, instrumental = true)))

        val result = repository(api).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        assertEquals(Lyrics.Instrumental, (result as LyricsRepository.Result.Found).lyrics)
    }

    @Test
    fun `an identity nobody is sure of is not worth a request`() = runTest {
        // A channel name is not an artist, so this identity is unconfident.
        val api = FakeLrcLib(listOf(record(189.0)))

        val result = repository(api).lyricsFor(identity("1-hour mix with seamless transitions", "Morris C."), 3_600_000)

        assertEquals(LyricsRepository.Result.Unidentified, result)
        assertEquals(0, api.calls)
    }

    @Test
    fun `nothing identified is never asked about`() = runTest {
        val api = FakeLrcLib(listOf(record(189.0)))

        assertEquals(LyricsRepository.Result.Unidentified, repository(api).lyricsFor(null, 189_000))
        assertEquals(0, api.calls)
    }

    @Test
    fun `the upload's own wording is not what the database is asked`() = runTest {
        val api = FakeLrcLib(listOf(record(189.0)))

        repository(api).lyricsFor(identity("keshi - Soft Spot (Official Music Video)", "KeshiVEVO"), 189_000)

        assertEquals("keshi", api.lastArtist)
        assertEquals("Soft Spot", api.lastTrack)
    }

    @Test
    fun `a service that cannot be reached is not remembered as an absence`() = runTest {
        val store = InMemoryStore()
        val offline = FakeLrcLib(failure = IOException("offline"))

        assertEquals(
            LyricsRepository.Result.Unavailable,
            repository(offline, store).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)
        )

        // Nothing written, so the next attempt actually asks again.
        val online = FakeLrcLib(listOf(record(189.0)))
        val second = repository(online, store).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        assertEquals(1, online.calls)
        assertTrue(second is LyricsRepository.Result.Found)
    }

    @Test
    fun `an answer is remembered, including the answer that there is none`() = runTest {
        val store = InMemoryStore()
        val api = FakeLrcLib(emptyList())

        repository(api, store).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)
        val again = repository(api, store).lyricsFor(identity("keshi - Soft Spot", "KeshiVEVO"), 189_000)

        assertEquals(1, api.calls)
        assertEquals(LyricsRepository.Result.None, again)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
