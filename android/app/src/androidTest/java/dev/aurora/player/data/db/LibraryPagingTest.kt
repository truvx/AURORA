package dev.aurora.player.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * docs/PERFORMANCE_BUDGET.md requires paged library and search queries, with no full-library
 * load and a warm local query target of 100 ms.
 *
 * Run against a real SQLite database rather than a fake, because paging and LIKE matching are
 * SQL behaviour: a mocked DAO would assert nothing about either.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPagingTest {

    private lateinit var db: AuroraDatabase
    private lateinit var dao: LocalLibraryDao

    private companion object {
        const val LIBRARY_SIZE = 2_000
        /** The budget's warm target, with headroom for emulator variance. */
        const val QUERY_BUDGET_MS = 100L
    }

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuroraDatabase::class.java).build()
        dao = db.localLibraryDao()

        runBlocking {
            for (i in 0 until LIBRARY_SIZE) {
                dao.insertMediaItem(
                    MediaItemEntity(
                        id = "local_$i",
                        provider = "LOCAL",
                        kind = "TRACK",
                        // Zero-padded so lexical ordering matches numeric ordering and the
                        // paging assertions below are meaningful.
                        title = "Track ${i.toString().padStart(5, '0')}",
                        provenance = "LOCAL",
                        isAvailable = true,
                        albumId = null
                    )
                )
            }
        }
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun countsWithoutLoadingRows() = runBlocking {
        assertEquals(LIBRARY_SIZE, dao.countLocalItems())
    }

    @Test
    fun returnsOnlyTheRequestedPage() = runBlocking {
        val page = dao.getLocalItemsPage(limit = 50, offset = 0)

        assertEquals(50, page.size)
        assertEquals("Track 00000", page.first().mediaItem.title)
    }

    @Test
    fun pagesDoNotOverlapOrSkipRows() = runBlocking {
        val first = dao.getLocalItemsPage(limit = 50, offset = 0).map { it.mediaItem.id }
        val second = dao.getLocalItemsPage(limit = 50, offset = 50).map { it.mediaItem.id }

        // A query without a stable ORDER BY can repeat or drop rows between pages, which
        // shows up as duplicated or missing tracks while scrolling.
        assertTrue("pages overlapped", first.intersect(second.toSet()).isEmpty())
        assertEquals(100, (first + second).distinct().size)
    }

    @Test
    fun theLastPageIsShortRatherThanEmpty() = runBlocking {
        val page = dao.getLocalItemsPage(limit = 50, offset = LIBRARY_SIZE - 10)

        assertEquals(10, page.size)
    }

    @Test
    fun searchIsBoundedByItsLimit() = runBlocking {
        // "Track" matches every row; without a limit this is the full-library load the
        // budget forbids.
        val results = dao.searchLocalItems("Track", limit = 25)

        assertEquals(25, results.size)
    }

    @Test
    fun searchMatchesOnASubstring() = runBlocking {
        val results = dao.searchLocalItems("01234", limit = 10)

        assertEquals(1, results.size)
        assertEquals("Track 01234", results.first().mediaItem.title)
    }

    @Test
    fun searchReturnsNothingForAnUnmatchedTerm() = runBlocking {
        assertTrue(dao.searchLocalItems("no-such-track", limit = 10).isEmpty())
    }

    @Test
    fun aPagedQueryStaysWithinTheWarmBudget() = runBlocking {
        // Warm the cache first; the budget is a warm target, not a cold one.
        dao.getLocalItemsPage(limit = 50, offset = 0)

        val start = System.nanoTime()
        dao.getLocalItemsPage(limit = 50, offset = 500)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "paged query took ${elapsedMs}ms against a ${QUERY_BUDGET_MS}ms budget " +
                "over $LIBRARY_SIZE rows",
            elapsedMs < QUERY_BUDGET_MS
        )
    }

    @Test
    fun aBoundedSearchStaysWithinTheWarmBudget() = runBlocking {
        dao.searchLocalItems("Track", limit = 25)

        val start = System.nanoTime()
        dao.searchLocalItems("Track 01", limit = 25)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "search took ${elapsedMs}ms against a ${QUERY_BUDGET_MS}ms budget " +
                "over $LIBRARY_SIZE rows",
            elapsedMs < QUERY_BUDGET_MS
        )
    }
}
