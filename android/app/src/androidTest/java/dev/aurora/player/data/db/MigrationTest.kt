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
}
