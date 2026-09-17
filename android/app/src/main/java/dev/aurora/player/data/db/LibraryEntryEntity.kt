package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user's relationship to a media item: whether it is a favorite and when it entered the
 * library. Separate from [MediaItemEntity] so provider metadata and user intent have
 * independent lifecycles - losing a local file must not lose the fact the user liked it.
 */
@Entity(
    tableName = "library_entries",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["addedAt"])
    ]
)
data class LibraryEntryEntity(
    @PrimaryKey val mediaId: String,
    val isFavorite: Boolean,
    val addedAt: Long,
    /** Local availability/import state, mirroring the file's own state when one exists. */
    val localState: String?
)
