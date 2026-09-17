package dev.aurora.player.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AuroraDatabase::class.java
    )

    @Test
    fun migrate1To2() {
        var db = helper.createDatabase(TEST_DB, 1)

        // Insert some data in version 1
        db.execSQL(
            "INSERT INTO media_items (id, provider, kind, title, provenance, isAvailable, albumId) " +
            "VALUES ('item1', 'LOCAL', 'TRACK', 'Old Title', 'LOCAL', 1, 'album1')"
        )
        db.execSQL(
            "INSERT INTO local_files (uri, mediaId, permissionStatus, size, dateModified, importState) " +
            "VALUES ('content://uri', 'item1', 'GRANTED', 1000, 12345, 'IMPORTED')"
        )
        db.execSQL(
            "INSERT INTO track_metadata (metadataId, mediaId, codec, container, bitrate, sampleRate, channels, bitDepth, durationMs, isLossless, extractionVersion) " +
            "VALUES (1, 'item1', 'mp3', 'mp3', 320, 44100, 2, 16, 200000, 0, 1)"
        )

        db.close()

        // Re-open the database with version 2 and provide MIGRATION_1_2
        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AuroraDatabase.MIGRATION_1_2)

        // Verify data was migrated correctly
        val cursor = db.query("SELECT * FROM track_metadata WHERE mediaId = 'item1'")
        assertTrue(cursor.moveToFirst())
        
        val sourceVersionIndex = cursor.getColumnIndex("sourceVersion")
        val codecIndex = cursor.getColumnIndex("codec")
        
        assertEquals("1", cursor.getString(sourceVersionIndex))
        assertEquals("mp3", cursor.getString(codecIndex))

        cursor.close()
    }

    @Test
    fun migrate3To4_purgesPlaceholderLoudnessButKeepsMeasuredRows() {
        var db = helper.createDatabase(TEST_DB, 3)

        db.execSQL(
            "INSERT INTO media_items (id, provider, kind, title, provenance, isAvailable, albumId) " +
            "VALUES ('placeholder', 'LOCAL', 'TRACK', 'Placeholder', 'LOCAL', 1, 'album1')"
        )
        db.execSQL(
            "INSERT INTO media_items (id, provider, kind, title, provenance, isAvailable, albumId) " +
            "VALUES ('measured', 'LOCAL', 'TRACK', 'Measured', 'LOCAL', 1, 'album1')"
        )
        // analysisVersion 1 was written with hardcoded values that were never measured.
        db.execSQL(
            "INSERT INTO track_loudness (mediaId, lufsIntegrated, truePeak, albumLufs, albumPeak, analysisVersion) " +
            "VALUES ('placeholder', -12.0, 0.9, NULL, NULL, 1)"
        )
        db.execSQL(
            "INSERT INTO track_loudness (mediaId, lufsIntegrated, truePeak, albumLufs, albumPeak, analysisVersion) " +
            "VALUES ('measured', -9.5, 0.85, NULL, NULL, 2)"
        )

        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AuroraDatabase.MIGRATION_3_4)

        val placeholder = db.query("SELECT * FROM track_loudness WHERE mediaId = 'placeholder'")
        assertEquals(0, placeholder.count)
        placeholder.close()

        val measured = db.query("SELECT lufsIntegrated FROM track_loudness WHERE mediaId = 'measured'")
        assertTrue(measured.moveToFirst())
        assertEquals(-9.5f, measured.getFloat(0), 0.001f)
        measured.close()
    }

    @Test
    fun migrate4To5_addsOrganizationTablesAndPreservesExistingData() {
        var db = helper.createDatabase(TEST_DB, 4)

        db.execSQL(
            "INSERT INTO media_items (id, provider, kind, title, provenance, isAvailable, albumId) " +
            "VALUES ('keep', 'LOCAL', 'TRACK', 'Existing Track', 'LOCAL', 1, NULL)"
        )
        db.execSQL(
            "INSERT INTO track_loudness (mediaId, lufsIntegrated, truePeak, albumLufs, albumPeak, analysisVersion) " +
            "VALUES ('keep', -9.5, 0.85, NULL, NULL, 2)"
        )

        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 5, true, AuroraDatabase.MIGRATION_4_5)

        // Phase 10 is purely additive: nothing from earlier schemas may be lost.
        val existing = db.query("SELECT title FROM media_items WHERE id = 'keep'")
        assertTrue(existing.moveToFirst())
        assertEquals("Existing Track", existing.getString(0))
        existing.close()

        val loudness = db.query("SELECT lufsIntegrated FROM track_loudness WHERE mediaId = 'keep'")
        assertTrue(loudness.moveToFirst())
        assertEquals(-9.5f, loudness.getFloat(0), 0.001f)
        loudness.close()

        // Every new table must be writable after the upgrade.
        db.execSQL("INSERT INTO library_entries (mediaId, isFavorite, addedAt, localState) VALUES ('keep', 1, 1, NULL)")
        db.execSQL("INSERT INTO playlists (id, name, createdAt, updatedAt, sourceKind) VALUES ('p1', 'Mix', 1, 1, 'USER')")
        db.execSQL("INSERT INTO playlist_entries (entryId, playlistId, mediaId, position, addedAt) VALUES ('e1', 'p1', 'keep', 0, 1)")
        db.execSQL("INSERT INTO listening_events (mediaId, provider, timestamp, sessionId, kind, progressMs) VALUES ('keep', 'LOCAL', 1, 's', 'PLAY', 0)")
        db.execSQL("INSERT INTO resume_positions (provider, mediaId, positionMs, durationMs, updatedAt) VALUES ('LOCAL', 'keep', 10, 100, 1)")
        db.execSQL("INSERT INTO queue_snapshots (snapshotId, createdAt, currentIndex, positionMs, repeatMode, shuffleMode) VALUES ('s1', 1, 0, 0, 'OFF', 'OFF')")
        db.execSQL("INSERT INTO queue_snapshot_items (snapshotId, position, mediaId, provider) VALUES ('s1', 0, 'keep', 'LOCAL')")

        val favorite = db.query("SELECT isFavorite FROM library_entries WHERE mediaId = 'keep'")
        assertTrue(favorite.moveToFirst())
        assertEquals(1, favorite.getInt(0))
        favorite.close()
    }
}
