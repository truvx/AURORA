package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val title: String,
    val albumArtist: String?,
    val year: Int?,
    val artworkRef: String?
)
