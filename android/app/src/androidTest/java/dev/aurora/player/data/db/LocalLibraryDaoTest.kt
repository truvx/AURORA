package dev.aurora.player.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocalLibraryDaoTest {

    private lateinit var db: AuroraDatabase
    private lateinit var dao: LocalLibraryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AuroraDatabase::class.java
        ).build()
        dao = db.localLibraryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadMediaItem() = runBlocking {
        val mediaId = "local_1"
        
        val album = AlbumEntity(
            id = "album_1",
            provider = "LOCAL",
            title = "Test Album",
            albumArtist = "Test Artist",
            year = 2024,
            artworkRef = null
        )
        dao.insertAlbum(album)

        val artist = ArtistEntity(
            id = "artist_1",
            provider = "LOCAL",
            name = "Test Artist",
            artworkRef = null
        )
        dao.insertArtist(artist)

        val mediaItem = MediaItemEntity(
            id = mediaId,
            provider = "LOCAL",
            kind = "audio",
            title = "Test Song",
            provenance = "/storage/emulated/0/Music/test.mp3",
            isAvailable = true,
            albumId = album.id
        )
        dao.insertMediaItem(mediaItem)
        
        val localFile = LocalFileEntity(
            uri = "/storage/emulated/0/Music/test.mp3",
            mediaId = mediaId,
            permissionStatus = "GRANTED",
            size = 1000L,
            dateModified = 123456789L,
            importState = "IMPORTED"
        )
        dao.insertLocalFile(localFile)
        
        val metadata = TrackTechnicalMetadataEntity(
            mediaId = mediaId,
            codec = "audio/mpeg",
            container = "audio/mpeg",
            bitrate = 320000,
            sampleRate = 44100,
            channels = 2,
            bitDepth = 16,
            durationMs = 210000L,
            isLossless = false,
            extractionVersion = 1,
            sourceVersion = 1
        )
        dao.insertTrackMetadata(metadata)
        
        dao.insertMediaItemArtistCrossRef(MediaItemArtistCrossRef(mediaId, artist.id))
        
        val items = dao.getAllLocalFileUris()
        assertEquals(1, items.size)
        assertEquals("/storage/emulated/0/Music/test.mp3", items[0])
        
        val retrievedFile = dao.getLocalFileForMedia(mediaId)
        assertNotNull(retrievedFile)
        assertEquals(1000L, retrievedFile?.size)
        
        val retrievedMetadata = dao.getMetadataForMedia(mediaId)
        assertNotNull(retrievedMetadata)
        assertEquals(320000, retrievedMetadata?.bitrate)
    }
}
