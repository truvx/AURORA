package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_files",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"], unique = true)]
)
data class LocalFileEntity(
    @PrimaryKey val uri: String,
    val mediaId: String,
    val permissionStatus: String, // e.g. GRANTED, DENIED, etc.
    val size: Long,
    val dateModified: Long,
    val importState: String // e.g. SCANNED, IMPORTED, PENDING
)
