package dev.aurora.player.app

import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.library.ListeningHistoryRecord
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.library.QueueSnapshot
import dev.aurora.player.domain.library.ResumePoint
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackPosition
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerState
import dev.aurora.player.domain.player.QueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History is private, local, and never required for playback, so the rules that matter are
 * what gets recorded, how often it is written, and that a skip is never logged as a listen.
 */
class PlaybackHistoryRecorderTest {

    private class FakeRepo : LibraryOrganizationRepository {
        val events = mutableListOf<Triple<String, ListeningEventKind, Long>>()
        val resumePoints = mutableListOf<ResumePoint>()
        val snapshots = mutableListOf<QueueSnapshot>()

        override suspend fun recordEvent(
            mediaId: String,
            provider: String,
            kind: ListeningEventKind,
            progressMs: Long,
            sessionId: String
        ) {
            events += Triple(mediaId, kind, progressMs)
        }

        override suspend fun saveResumePoint(point: ResumePoint) { resumePoints += point }
        override suspend fun saveQueueSnapshot(snapshot: QueueSnapshot) { snapshots += snapshot }

        override fun observeFavorites(): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeIsFavorite(mediaId: String): Flow<Boolean> = flowOf(false)
        override suspend fun setFavorite(mediaId: String, isFavorite: Boolean) {}
        override fun observePlaylists(): Flow<List<Playlist>> = flowOf(emptyList())
        override fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrack>> = flowOf(emptyList())
        override suspend fun createPlaylist(name: String): String = "p"
        override suspend fun renamePlaylist(playlistId: String, name: String) {}
        override suspend fun deletePlaylist(playlistId: String) {}
        override suspend fun addToPlaylist(playlistId: String, mediaId: String) {}
        override suspend fun removeFromPlaylist(playlistId: String, entryId: String) {}
        override suspend fun movePlaylistEntry(playlistId: String, fromPosition: Int, toPosition: Int) {}
        override fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItem>> = flowOf(emptyList())
        override suspend fun getResumePoint(provider: String, mediaId: String): ResumePoint? = null
        override suspend fun loadQueueSnapshot(): QueueSnapshot? = null
        override suspend fun pruneHistory(maxAgeMs: Long, now: Long) {}
        override suspend fun exportHistory(): List<ListeningHistoryRecord> = emptyList()
        override suspend fun deleteAllPrivateData() {}
    }

    private fun track(id: String) = MediaItem(
        id = id,
        provider = ProviderKind.LOCAL,
        title = "Track $id",
        artist = null,
        album = null,
        artworkUri = null
    )

    private fun state(
        id: String?,
        status: PlaybackStatus = PlaybackStatus.Playing,
        elapsed: Long = 0L,
        duration: Long? = 200_000L,
        queue: List<String> = emptyList(),
        index: Int = -1
    ) = PlayerState(
        status = status,
        currentTrack = id?.let { track(it) },
        position = PlaybackPosition(elapsed, duration, null),
        queue = QueueState(items = queue.map { track(it) }, currentIndex = index)
    )

    private fun recorder(repo: FakeRepo, clock: () -> Long = { 0L }) =
        PlaybackHistoryRecorder(repository = repo, sessionId = "s1", now = clock)

    @Test
    fun `starting a track records a play`() = runBlocking {
        val repo = FakeRepo()
        recorder(repo).onState(state("a"))

        assertEquals(listOf(Triple("a", ListeningEventKind.PLAY, 0L)), repo.events)
    }

    @Test
    fun `leaving a track early records a skip`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", elapsed = 0L))
        r.onState(state("a", elapsed = 30_000L))   // 15% of 200s
        r.onState(state("b"))

        assertTrue(repo.events.contains(Triple("a", ListeningEventKind.SKIP, 30_000L)))
    }

    @Test
    fun `leaving a track past the threshold records a completion`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", elapsed = 0L))
        r.onState(state("a", elapsed = 190_000L))  // 95% of 200s
        r.onState(state("b"))

        assertTrue(repo.events.contains(Triple("a", ListeningEventKind.COMPLETE, 190_000L)))
    }

    @Test
    fun `unknown duration counts as a skip rather than an invented completion`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", elapsed = 0L, duration = null))
        r.onState(state("a", elapsed = 500_000L, duration = null))
        r.onState(state("b", duration = null))

        assertTrue(repo.events.contains(Triple("a", ListeningEventKind.SKIP, 500_000L)))
    }

    @Test
    fun `reaching the end records exactly one completion`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", elapsed = 0L))
        r.onState(state("a", status = PlaybackStatus.Completed, elapsed = 200_000L))
        r.onState(state("a", status = PlaybackStatus.Completed, elapsed = 200_000L))

        assertEquals(1, repo.events.count { it.second == ListeningEventKind.COMPLETE })
    }

    @Test
    fun `a completed track resumes from the beginning`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a"))
        r.onState(state("a", status = PlaybackStatus.Completed, elapsed = 200_000L))

        assertEquals(0L, repo.resumePoints.last().positionMs)
    }

    // --- resume debounce ----------------------------------------------------------------

    @Test
    fun `resume positions are not written on every position tick`() = runBlocking {
        val repo = FakeRepo()
        var clock = 1_000L
        val r = recorder(repo) { clock }
        r.onState(state("a"))

        // Ten ticks inside a single debounce window.
        repeat(10) {
            clock += 200L
            r.onState(state("a", elapsed = 20_000L + it * 200L))
        }

        assertTrue("expected at most one write per window, got ${repo.resumePoints.size}",
            repo.resumePoints.size <= 1)
    }

    @Test
    fun `resume position is written again after the debounce window`() = runBlocking {
        val repo = FakeRepo()
        var clock = 10_000L
        val r = recorder(repo) { clock }
        r.onState(state("a"))

        r.onState(state("a", elapsed = 20_000L))
        clock += 6_000L
        r.onState(state("a", elapsed = 26_000L))

        assertEquals(2, repo.resumePoints.size)
    }

    @Test
    fun `a position too early in a track is not worth resuming`() = runBlocking {
        val repo = FakeRepo()
        var clock = 100_000L
        val r = recorder(repo) { clock }
        r.onState(state("a"))
        r.onState(state("a", elapsed = 3_000L))

        assertTrue(repo.resumePoints.isEmpty())
    }

    // --- queue snapshots ----------------------------------------------------------------

    @Test
    fun `a queue is snapshotted once per distinct arrangement`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", queue = listOf("a", "b"), index = 0))
        r.onState(state("a", queue = listOf("a", "b"), index = 0, elapsed = 5_000L))

        assertEquals(1, repo.snapshots.size)
        assertEquals(listOf("a", "b"), repo.snapshots.first().tracks.map { it.mediaId })
    }

    @Test
    fun `changing the queue writes a new snapshot`() = runBlocking {
        val repo = FakeRepo()
        val r = recorder(repo)
        r.onState(state("a", queue = listOf("a", "b"), index = 0))
        r.onState(state("a", queue = listOf("a", "b", "c"), index = 0))

        assertEquals(2, repo.snapshots.size)
        assertEquals(3, repo.snapshots.last().tracks.size)
    }

    @Test
    fun `an empty queue is not snapshotted over a real one`() = runBlocking {
        val repo = FakeRepo()
        recorder(repo).onState(state(null, queue = emptyList()))

        assertTrue(repo.snapshots.isEmpty())
    }

    @Test
    fun `observing a flow records the whole sequence`() = runBlocking {
        val repo = FakeRepo()
        recorder(repo).observe(flowOf(state("a"), state("b")))

        assertEquals(
            listOf(ListeningEventKind.PLAY, ListeningEventKind.SKIP, ListeningEventKind.PLAY),
            repo.events.map { it.second }
        )
        assertNull(repo.events.firstOrNull { it.first == "c" })
    }
}
