package dev.aurora.player.data.providers

import dev.aurora.player.data.db.*
import dev.aurora.player.data.scanner.LocalMusicScanner
import dev.aurora.player.data.scanner.LocalScanResult
import dev.aurora.player.data.scanner.ScannedTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import android.content.Context
import io.mockk.mockk

class LocalLibraryProviderTest {

    private class FakeDao : LocalLibraryDao {
        val existingUris = mutableListOf<String>()
        val upsertedTracks = mutableListOf<MediaItemEntity>()
        var deletedStaleUris = listOf<String>()
        var mediaIdForUri: String? = null

        override suspend fun insertMediaItem(item: MediaItemEntity) {}
        override suspend fun insertLocalFile(file: LocalFileEntity) {}
        override suspend fun insertTrackMetadata(metadata: TrackTechnicalMetadataEntity) {}
        override suspend fun insertAlbum(album: AlbumEntity) {}
        override suspend fun insertArtist(artist: ArtistEntity) {}
        override suspend fun insertMediaItemArtistCrossRef(crossRef: MediaItemArtistCrossRef) {}
        override suspend fun insertArtwork(artwork: ArtworkEntity) {}

        override suspend fun upsertLocalTrack(
            item: MediaItemEntity,
            file: LocalFileEntity,
            metadata: TrackTechnicalMetadataEntity?,
            album: AlbumEntity?,
            artists: List<ArtistEntity>,
            artwork: ArtworkEntity?
        ) {
            upsertedTracks.add(item)
        }

        override fun observeLocalItems(): Flow<List<MediaItemWithDetails>> = flowOf(emptyList())
        override suspend fun getLocalItems(): List<MediaItemWithDetails> = emptyList()
        override suspend fun getLocalFileForMedia(mediaId: String): LocalFileEntity? = null
        override suspend fun getMetadataForMedia(mediaId: String): TrackTechnicalMetadataEntity? = null
        override suspend fun getAlbum(albumId: String): AlbumEntity? = null
        override suspend fun getArtistsForMedia(mediaId: String): List<ArtistEntity> = emptyList()

        override suspend fun getAllLocalFileUris(): List<String> = existingUris
        override suspend fun getMediaIdForLocalFile(uri: String): String? = mediaIdForUri
        override suspend fun deleteLocalFile(uri: String) {}
        override suspend fun markMediaItemUnavailable(mediaId: String) {}

        override suspend fun deleteStaleLocalFiles(staleUris: List<String>) {
            deletedStaleUris = staleUris
        }
    }

    private class FakeScanner(var result: LocalScanResult) : LocalMusicScanner(mockk<Context>()) {
        override suspend fun scan(): LocalScanResult = result
    }

    @Test
    fun `syncLibrary inserts new item when scanned successfully`() = runBlocking {
        val dao = FakeDao()
        val mediaItem = MediaItemEntity("local_1", "LOCAL", "TRACK", "Test Title", "LOCAL", true, "album_1")
        val fileEntity = LocalFileEntity("uri_1", "local_1", "GRANTED", 1000, 1000, "SCANNED")
        val techMeta = TrackTechnicalMetadataEntity(0, "local_1", "audio/mp3", "audio/mp3", 128000, 44100, 2, 16, 200000L, false, 1, 1)
        val albumEntity = AlbumEntity("album_1", "LOCAL", "Album", "Artist", null, null)
        val artistEntity = ArtistEntity("artist_1", "LOCAL", "Artist", null)
        
        val scannedTrack = ScannedTrack(mediaItem, fileEntity, techMeta, albumEntity, listOf(artistEntity), null)
        val scanner = FakeScanner(LocalScanResult.Success(listOf(scannedTrack)))
        
        val provider = LocalLibraryProvider(dao, scanner)
        provider.syncLibrary()
        
        assertEquals(1, dao.upsertedTracks.size)
        assertEquals("Test Title", dao.upsertedTracks[0].title)
    }

    @Test
    fun `syncLibrary removes stale items on complete successful empty scan`() = runBlocking {
        val dao = FakeDao()
        dao.existingUris.add("uri_stale")
        dao.mediaIdForUri = "local_2"
        
        val scanner = FakeScanner(LocalScanResult.Success(emptyList()))
        val provider = LocalLibraryProvider(dao, scanner)
        
        provider.syncLibrary()
        
        assertEquals(1, dao.deletedStaleUris.size)
        assertEquals("uri_stale", dao.deletedStaleUris[0])
    }
    
    @Test
    fun `syncLibrary does NOT remove stale items when scan fails`() = runBlocking {
        val dao = FakeDao()
        dao.existingUris.add("uri_stale")
        dao.mediaIdForUri = "local_2"
        
        val scanner = FakeScanner(LocalScanResult.Failed("Cursor null"))
        val provider = LocalLibraryProvider(dao, scanner)
        
        provider.syncLibrary()
        
        assertEquals(0, dao.deletedStaleUris.size)
    }
    
    @Test
    fun `syncLibrary does NOT remove stale items when permission denied`() = runBlocking {
        val dao = FakeDao()
        dao.existingUris.add("uri_stale")
        dao.mediaIdForUri = "local_2"
        
        val scanner = FakeScanner(LocalScanResult.PermissionDenied("Denied"))
        val provider = LocalLibraryProvider(dao, scanner)
        
        provider.syncLibrary()
        
        assertEquals(0, dao.deletedStaleUris.size)
    }
}
