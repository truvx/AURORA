package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val name: String,
    val artworkRef: String?
)
