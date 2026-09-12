package dev.aurora.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaItemEntity::class,
        LocalFileEntity::class,
        TrackTechnicalMetadataEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        MediaItemArtistCrossRef::class,
        ArtworkEntity::class,
        TrackLoudnessEntity::class,
        LibraryEntryEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        ListeningEventEntity::class,
        ResumePositionEntity::class,
        QueueSnapshotEntity::class,
        QueueSnapshotItemEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun libraryOrganizationDao(): LibraryOrganizationDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_metadata ADD COLUMN sourceVersion INTEGER NOT NULL DEFAULT 1")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `track_loudness` (
                        `mediaId` TEXT NOT NULL, 
                        `lufsIntegrated` REAL, 
                        `truePeak` REAL, 
                        `albumLufs` REAL, 
                        `albumPeak` REAL, 
                        `analysisVersion` INTEGER NOT NULL, 
                        PRIMARY KEY(`mediaId`), 
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_loudness_mediaId` ON `track_loudness` (`mediaId`)")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // analysisVersion 1 rows were written with placeholder loudness values that were
                // never measured from the audio. Delete them so normalization reports unknown
                // until a real ReplayGain tag is read on the next scan.
                db.execSQL("DELETE FROM track_loudness WHERE analysisVersion = 1")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Phase 10: durable library organization. All additive - no existing row is
                // rewritten, so an interrupted upgrade cannot lose library or loudness data.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `library_entries` (
                        `mediaId` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `localState` TEXT,
                        PRIMARY KEY(`mediaId`),
                        FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_entries_isFavorite` ON `library_entries` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_entries_addedAt` ON `library_entries` (`addedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `sourceKind` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlists_updatedAt` ON `playlists` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_entries` (
                        `entryId` TEXT NOT NULL,
                        `playlistId` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entryId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_playlistId_position` ON `playlist_entries` (`playlistId`, `position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_entries_mediaId` ON `playlist_entries` (`mediaId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_events` (
                        `eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `progressMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_timestamp` ON `listening_events` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_mediaId` ON `listening_events` (`mediaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_events_sessionId` ON `listening_events` (`sessionId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `resume_positions` (
                        `provider` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`provider`, `mediaId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_resume_positions_updatedAt` ON `resume_positions` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_snapshots` (
                        `snapshotId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `currentIndex` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `repeatMode` TEXT NOT NULL,
                        `shuffleMode` TEXT NOT NULL,
                        PRIMARY KEY(`snapshotId`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_snapshot_items` (
                        `snapshotId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        PRIMARY KEY(`snapshotId`, `position`),
                        FOREIGN KEY(`snapshotId`) REFERENCES `queue_snapshots`(`snapshotId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_snapshot_items_snapshotId_position` ON `queue_snapshot_items` (`snapshotId`, `position`)")
            }
        }
    }
}
