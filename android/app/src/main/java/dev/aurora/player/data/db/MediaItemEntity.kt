package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["albumId"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val kind: String,
    val title: String,
    val provenance: String?,
    val isAvailable: Boolean,
    val albumId: String?
)
