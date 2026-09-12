package dev.aurora.player.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the Phase 10 acceptance surface: favorites, playlist ordering under duplicates and
 * reorder, history retention, resume positions, queue snapshot restore, and privacy deletion.
 */
@RunWith(AndroidJUnit4::class)
class LibraryOrganizationDaoTest {

    private lateinit var db: AuroraDatabase
    private lateinit var dao: LibraryOrganizationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuroraDatabase::class.java).build()
        dao = db.libraryOrganizationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    private suspend fun seedMedia(vararg ids: String) {
        val localDao = db.localLibraryDao()
        ids.forEach { id ->
            localDao.insertMediaItem(
                MediaItemEntity(
                    id = id,
                    provider = "LOCAL",
                    kind = "TRACK",
                    title = "Track $id",
                    provenance = "LOCAL",
                    isAvailable = true,
                    albumId = null
                )
            )
        }
    }

    private suspend fun createPlaylist(id: String = "p1"): String {
        dao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = "Test Playlist",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                sourceKind = "USER"
            )
        )
        return id
    }

    private suspend fun positions(playlistId: String): List<String> =
        dao.getPlaylistEntries(playlistId).map { it.mediaId }

    // --- favorites ----------------------------------------------------------------------

    @Test
    fun favoriteRoundTrips() = runBlocking {
        seedMedia("a")
        dao.setFavorite("a", true, now = 500L)

        val favorites = dao.getFavorites()
        assertEquals(1, favorites.size)
        assertEquals("a", favorites[0].mediaId)
        assertTrue(favorites[0].isFavorite)
    }

    @Test
    fun unfavoritingKeepsTheEntryButClearsTheFlag() = runBlocking {
        seedMedia("a")
        dao.setFavorite("a", true, now = 500L)
        dao.setFavorite("a", false, now = 900L)

        assertTrue(dao.getFavorites().isEmpty())
        val entry = dao.getLibraryEntry("a")
        assertNotNull(entry)
        assertFalse(entry!!.isFavorite)
    }

    @Test
    fun refavoritingPreservesOriginalAddedAt() = runBlocking {
        seedMedia("a")
        dao.setFavorite("a", true, now = 500L)
        dao.setFavorite("a", false, now = 900L)
        dao.setFavorite("a", true, now = 1_500L)

        assertEquals(500L, dao.getLibraryEntry("a")!!.addedAt)
    }

    // --- playlist ordering --------------------------------------------------------------

    @Test
    fun entriesAppendInOrder() = runBlocking {
        seedMedia("a", "b", "c")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)
        dao.addToPlaylist(id, "e2", "b", 2L)
        dao.addToPlaylist(id, "e3", "c", 3L)

        assertEquals(listOf("a", "b", "c"), positions(id))
        assertEquals(listOf(0, 1, 2), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun theSameTrackMayAppearTwice() = runBlocking {
        seedMedia("a")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)
        dao.addToPlaylist(id, "e2", "a", 2L)

        assertEquals(listOf("a", "a"), positions(id))
        assertEquals(listOf(0, 1), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun removingAnEntryClosesTheGap() = runBlocking {
        seedMedia("a", "b", "c")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)
        dao.addToPlaylist(id, "e2", "b", 2L)
        dao.addToPlaylist(id, "e3", "c", 3L)

        dao.removeFromPlaylist(id, "e2", 4L)

        assertEquals(listOf("a", "c"), positions(id))
        assertEquals(listOf(0, 1), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun movingAnEntryDownReordersDeterministically() = runBlocking {
        seedMedia("a", "b", "c", "d")
        val id = createPlaylist()
        listOf("a", "b", "c", "d").forEachIndexed { i, m ->
            dao.addToPlaylist(id, "e$i", m, i.toLong())
        }

        dao.moveEntry(id, fromPosition = 0, toPosition = 2, now = 10L)

        assertEquals(listOf("b", "c", "a", "d"), positions(id))
        assertEquals(listOf(0, 1, 2, 3), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun movingAnEntryUpReordersDeterministically() = runBlocking {
        seedMedia("a", "b", "c", "d")
        val id = createPlaylist()
        listOf("a", "b", "c", "d").forEachIndexed { i, m ->
            dao.addToPlaylist(id, "e$i", m, i.toLong())
        }

        dao.moveEntry(id, fromPosition = 3, toPosition = 1, now = 10L)

        assertEquals(listOf("a", "d", "b", "c"), positions(id))
        assertEquals(listOf(0, 1, 2, 3), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun movingToTheSamePositionIsANoOp() = runBlocking {
        seedMedia("a", "b")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e0", "a", 1L)
        dao.addToPlaylist(id, "e1", "b", 2L)

        dao.moveEntry(id, fromPosition = 1, toPosition = 1, now = 10L)

        assertEquals(listOf("a", "b"), positions(id))
    }

    @Test
    fun anOutOfRangeMoveLeavesOrderingIntact() = runBlocking {
        seedMedia("a", "b")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e0", "a", 1L)
        dao.addToPlaylist(id, "e1", "b", 2L)

        dao.moveEntry(id, fromPosition = 0, toPosition = 7, now = 10L)

        assertEquals(listOf("a", "b"), positions(id))
        assertEquals(listOf(0, 1), dao.getPlaylistEntries(id).map { it.position })
    }

    @Test
    fun deletingAPlaylistRemovesItsEntries() = runBlocking {
        seedMedia("a")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)

        dao.deletePlaylist(id)

        assertTrue(dao.getPlaylistEntries(id).isEmpty())
        assertNull(dao.getPlaylist(id))
    }

    @Test
    fun anUnavailableTrackStaysInItsPlaylist() = runBlocking {
        seedMedia("a")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)

        // Losing the local file must not silently edit the user's playlist.
        db.localLibraryDao().markMediaItemUnavailable("a")

        assertEquals(listOf("a"), positions(id))
    }

    // --- history ------------------------------------------------------------------------

    @Test
    fun historyRecordsAndPrunesByThreshold() = runBlocking {
        listOf(100L, 200L, 300L, 400L).forEach { ts ->
            dao.insertListeningEvent(
                ListeningEventEntity(
                    mediaId = "a",
                    provider = "LOCAL",
                    timestamp = ts,
                    sessionId = "s1",
                    kind = "PLAY",
                    progressMs = 0L
                )
            )
        }
        assertEquals(4, dao.countEvents())

        dao.pruneEventsBefore(cutoff = 300L)

        val remaining = dao.getAllEvents()
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.timestamp >= 300L })
    }

    @Test
    fun historyExportReturnsEverythingInOrder() = runBlocking {
        listOf(300L, 100L, 200L).forEach { ts ->
            dao.insertListeningEvent(
                ListeningEventEntity(
                    mediaId = "a",
                    provider = "LOCAL",
                    timestamp = ts,
                    sessionId = "s1",
                    kind = "PLAY",
                    progressMs = 0L
                )
            )
        }

        assertEquals(listOf(100L, 200L, 300L), dao.getAllEvents().map { it.timestamp })
    }

    // --- resume -------------------------------------------------------------------------

    @Test
    fun resumePositionUpsertsPerProviderAndMedia() = runBlocking {
        dao.upsertResumePosition(ResumePositionEntity("LOCAL", "a", 5_000L, 200_000L, 1L))
        dao.upsertResumePosition(ResumePositionEntity("LOCAL", "a", 9_000L, 200_000L, 2L))
        dao.upsertResumePosition(ResumePositionEntity("YOUTUBE", "a", 1_000L, null, 3L))

        assertEquals(9_000L, dao.getResumePosition("LOCAL", "a")!!.positionMs)
        assertEquals(1_000L, dao.getResumePosition("YOUTUBE", "a")!!.positionMs)
    }

    // --- queue snapshots ----------------------------------------------------------------

    @Test
    fun queueSnapshotRestoresOrder() = runBlocking {
        dao.replaceQueueSnapshot(
            QueueSnapshotEntity("s1", 10L, currentIndex = 1, positionMs = 4_000L, repeatMode = "OFF", shuffleMode = "OFF"),
            listOf(
                QueueSnapshotItemEntity("s1", 0, "a", "LOCAL"),
                QueueSnapshotItemEntity("s1", 1, "b", "LOCAL"),
                QueueSnapshotItemEntity("s1", 2, "c", "LOCAL")
            )
        )

        val snapshot = dao.getLatestQueueSnapshot()!!
        assertEquals(1, snapshot.currentIndex)
        assertEquals(listOf("a", "b", "c"), dao.getQueueSnapshotItems("s1").map { it.mediaId })
    }

    @Test
    fun onlyTheLatestQueueSnapshotIsRetained() = runBlocking {
        dao.replaceQueueSnapshot(
            QueueSnapshotEntity("s1", 10L, 0, 0L, "OFF", "OFF"),
            listOf(QueueSnapshotItemEntity("s1", 0, "a", "LOCAL"))
        )
        dao.replaceQueueSnapshot(
            QueueSnapshotEntity("s2", 20L, 0, 0L, "OFF", "OFF"),
            listOf(QueueSnapshotItemEntity("s2", 0, "b", "LOCAL"))
        )

        assertEquals("s2", dao.getLatestQueueSnapshot()!!.snapshotId)
        assertTrue(dao.getQueueSnapshotItems("s1").isEmpty())
    }

    // --- privacy ------------------------------------------------------------------------

    @Test
    fun deletingPrivateDataKeepsFavoritesAndPlaylists() = runBlocking {
        seedMedia("a")
        val id = createPlaylist()
        dao.addToPlaylist(id, "e1", "a", 1L)
        dao.setFavorite("a", true, 1L)
        dao.insertListeningEvent(
            ListeningEventEntity(mediaId = "a", provider = "LOCAL", timestamp = 1L, sessionId = "s", kind = "PLAY", progressMs = 0L)
        )
        dao.upsertResumePosition(ResumePositionEntity("LOCAL", "a", 1_000L, null, 1L))
        dao.replaceQueueSnapshot(
            QueueSnapshotEntity("s1", 1L, 0, 0L, "OFF", "OFF"),
            listOf(QueueSnapshotItemEntity("s1", 0, "a", "LOCAL"))
        )

        dao.deleteAllPrivateData()

        assertEquals(0, dao.countEvents())
        assertNull(dao.getResumePosition("LOCAL", "a"))
        assertNull(dao.getLatestQueueSnapshot())
        // The user's own curation survives.
        assertEquals(1, dao.getFavorites().size)
        assertEquals(listOf("a"), positions(id))
    }
}
