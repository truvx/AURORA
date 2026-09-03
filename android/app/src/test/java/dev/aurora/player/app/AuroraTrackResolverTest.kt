package dev.aurora.player.app

import dev.aurora.player.data.db.LocalFileEntity
import dev.aurora.player.data.db.LocalLibraryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuroraTrackResolverTest {

    private val fakeDao = object : LocalLibraryDao {
        override suspend fun getLocalFileForMedia(mediaItemId: String): LocalFileEntity? {
            return if (mediaItemId == "local_123") {
                LocalFileEntity(
                    uri = "content://media/external/audio/media/123",
                    mediaId = "local_123",
                    permissionStatus = "GRANTED",
                    size = 1000L,
                    dateModified = 123456789L,
                    importState = "SCANNED"
                )
            } else {
                null
            }
        }
        
        // Stubs for the rest of the interface...
        override suspend fun upsertLocalTrack(item: dev.aurora.player.data.db.MediaItemEntity, file: dev.aurora.player.data.db.LocalFileEntity, metadata: dev.aurora.player.data.db.TrackTechnicalMetadataEntity?, album: dev.aurora.player.data.db.AlbumEntity?, artists: List<dev.aurora.player.data.db.ArtistEntity>, artwork: dev.aurora.player.data.db.ArtworkEntity?) {}
        override suspend fun getAllLocalFileUris(): List<String> = emptyList()
        override suspend fun deleteStaleLocalFiles(uris: List<String>) {}
        override fun observeLocalItems(): kotlinx.coroutines.flow.Flow<List<dev.aurora.player.data.db.MediaItemWithDetails>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getLocalItems(): List<dev.aurora.player.data.db.MediaItemWithDetails> = emptyList()
        override suspend fun getMetadataForMedia(mediaItemId: String): dev.aurora.player.data.db.TrackTechnicalMetadataEntity? = null
        override suspend fun insertMediaItem(item: dev.aurora.player.data.db.MediaItemEntity) {}
        override suspend fun insertLocalFile(file: dev.aurora.player.data.db.LocalFileEntity) {}
        override suspend fun insertTrackMetadata(metadata: dev.aurora.player.data.db.TrackTechnicalMetadataEntity) {}
        override suspend fun insertAlbum(album: dev.aurora.player.data.db.AlbumEntity) {}
        override suspend fun insertArtist(artist: dev.aurora.player.data.db.ArtistEntity) {}
        override suspend fun insertMediaItemArtistCrossRef(crossRef: dev.aurora.player.data.db.MediaItemArtistCrossRef) {}
        override suspend fun insertArtwork(artwork: dev.aurora.player.data.db.ArtworkEntity) {}
        override suspend fun getAlbum(albumId: String): dev.aurora.player.data.db.AlbumEntity? = null
        override suspend fun getArtistsForMedia(mediaId: String): List<dev.aurora.player.data.db.ArtistEntity> = emptyList()
        override suspend fun getMediaIdForLocalFile(uri: String): String? = null
        override suspend fun deleteLocalFile(uri: String) {}
        override suspend fun markMediaItemUnavailable(mediaId: String) {}
    }

    private val resolver = AuroraTrackResolver(fakeDao)

    @Test
    fun testResolveValidIdReturnsUri() = runTest {
        val uri = resolver.resolveUri("local_123")
        assertEquals("content://media/external/audio/media/123", uri)
    }

    @Test
    fun testResolveInvalidIdReturnsNull() = runTest {
        val uri = resolver.resolveUri("not_found")
        assertNull(uri)
    }
}
