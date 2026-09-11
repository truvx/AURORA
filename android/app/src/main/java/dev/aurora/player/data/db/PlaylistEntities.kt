package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-created or imported playlist. `sourceKind` keeps user-authored lists distinct from
 * provider-backed ones so an import can never silently overwrite the user's own list.
 */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["updatedAt"])]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceKind: String
)

/**
 * One ordered slot in a playlist.
 *
 * `entryId` is stable and independent of position, so reordering never invalidates a
 * reference to a row, and the same media item may appear more than once. The media foreign
 * key deliberately uses NO ACTION: an unavailable or removed local file must not silently
 * delete entries from a user's playlist.
 */
@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["mediaId"])
    ]
)
data class PlaylistEntryEntity(
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val mediaId: String,
    val position: Int,
    val addedAt: Long
)
