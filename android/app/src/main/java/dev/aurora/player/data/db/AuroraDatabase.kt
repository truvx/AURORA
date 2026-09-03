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
        TrackLoudnessEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun localLibraryDao(): LocalLibraryDao

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
    }
}
