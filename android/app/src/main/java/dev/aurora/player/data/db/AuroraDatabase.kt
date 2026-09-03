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
        ArtworkEntity::class
    ],
    version = 2,
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
    }
}
