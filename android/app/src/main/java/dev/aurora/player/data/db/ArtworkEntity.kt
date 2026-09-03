package dev.aurora.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artworks")
data class ArtworkEntity(
    @PrimaryKey val sourceRef: String,
    val width: Int?,
    val height: Int?,
    val paletteVersion: Int?,
    val cacheMetadata: String?
)
