package dev.aurora.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import dev.aurora.player.domain.audio.TrackLoudnessData

@Dao
interface LocalLibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalFile(file: LocalFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackMetadata(metadata: TrackTechnicalMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaItemArtistCrossRef(crossRef: MediaItemArtistCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtwork(artwork: ArtworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackLoudness(loudness: TrackLoudnessEntity)

    @Transaction
    suspend fun upsertLocalTrack(
        item: MediaItemEntity,
        file: LocalFileEntity,
        metadata: TrackTechnicalMetadataEntity?,
        album: AlbumEntity?,
        artists: List<ArtistEntity>,
        artwork: ArtworkEntity?,
        loudness: TrackLoudnessEntity?
    ) {
        if (artwork != null) insertArtwork(artwork)
        if (album != null) insertAlbum(album)
        artists.forEach { insertArtist(it) }
        
        insertMediaItem(item)
        
        artists.forEach { artist ->
            insertMediaItemArtistCrossRef(MediaItemArtistCrossRef(item.id, artist.id))
        }
        
        insertLocalFile(file)
        if (metadata != null) {
            insertTrackMetadata(metadata)
        }
        if (loudness != null) {
            insertTrackLoudness(loudness)
        }
    }

    @Transaction
    @Query("SELECT * FROM media_items WHERE provider = 'LOCAL'")
    fun observeLocalItems(): Flow<List<MediaItemWithDetails>>
    
    @Transaction
    @Query("SELECT * FROM media_items WHERE provider = 'LOCAL'")
    suspend fun getLocalItems(): List<MediaItemWithDetails>

    @Query("SELECT * FROM local_files WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getLocalFileForMedia(mediaId: String): LocalFileEntity?

    @Query("SELECT * FROM track_metadata WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getMetadataForMedia(mediaId: String): TrackTechnicalMetadataEntity?

    @Query("SELECT lufsIntegrated, truePeak, albumLufs, albumPeak FROM track_loudness WHERE mediaId = :mediaId")
    suspend fun getTrackLoudness(mediaId: String): TrackLoudnessData?

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbum(albumId: String): AlbumEntity?

    @Query("SELECT artists.* FROM artists INNER JOIN media_item_artist_cross_ref ON artists.id = media_item_artist_cross_ref.artistId WHERE media_item_artist_cross_ref.mediaId = :mediaId")
    suspend fun getArtistsForMedia(mediaId: String): List<ArtistEntity>

    @Query("SELECT uri FROM local_files")
    suspend fun getAllLocalFileUris(): List<String>

    @Query("SELECT mediaId FROM local_files WHERE uri = :uri LIMIT 1")
    suspend fun getMediaIdForLocalFile(uri: String): String?

    @Query("DELETE FROM local_files WHERE uri = :uri")
    suspend fun deleteLocalFile(uri: String)

    @Query("UPDATE media_items SET isAvailable = 0 WHERE id = :mediaId")
    suspend fun markMediaItemUnavailable(mediaId: String)
    
    @Transaction
    suspend fun deleteStaleLocalFiles(staleUris: List<String>) {
        staleUris.forEach { uri ->
            val mediaId = getMediaIdForLocalFile(uri)
            if (mediaId != null) {
                deleteLocalFile(uri)
                markMediaItemUnavailable(mediaId)
            }
        }
    }
}
