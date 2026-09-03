package dev.aurora.player.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class MediaItemWithDetails(
    @Embedded val mediaItem: MediaItemEntity,
    
    @Relation(
        parentColumn = "albumId",
        entityColumn = "id"
    )
    val album: AlbumEntity?,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MediaItemArtistCrossRef::class,
            parentColumn = "mediaId",
            entityColumn = "artistId"
        )
    )
    val artists: List<ArtistEntity>
)
