package dev.aurora.player.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import dev.aurora.player.data.db.AlbumEntity
import dev.aurora.player.data.db.ArtistEntity
import dev.aurora.player.data.db.ArtworkEntity
import dev.aurora.player.data.db.LocalFileEntity
import dev.aurora.player.data.db.MediaItemEntity
import dev.aurora.player.data.db.TrackLoudnessEntity
import dev.aurora.player.data.db.TrackTechnicalMetadataEntity
import dev.aurora.player.domain.audio.ReplayGainReference
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class LocalMusicScanner(private val context: Context) {

    open suspend fun scan(): LocalScanResult = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ScannedTrack>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )
            
            if (cursor == null) {
                return@withContext LocalScanResult.PermissionDenied("Cursor returned null. Permission may be denied or unavailable.")
            }
            
            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeTypeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateModifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                
                while (c.moveToNext()) {
                    try {
                        val row = ScannedRow(
                            id = c.getLong(idCol),
                            title = c.getString(titleCol),
                            artist = c.getString(artistCol),
                            artistId = c.getLong(artistIdCol),
                            album = c.getString(albumCol),
                            albumId = c.getLong(albumIdCol),
                            durationMs = c.getLong(durationCol),
                            size = c.getLong(sizeCol),
                            mimeType = c.getString(mimeTypeCol),
                            dateModified = c.getLong(dateModifiedCol),
                        )

                        val uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            row.id
                        )
                        val artworkUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            row.albumId
                        ).toString()

                        val mediaId = ScanMapping.mediaId(row.id)
                        tracks.add(
                            ScanMapping.scannedTrack(
                                row = row,
                                uri = uri.toString(),
                                artworkUri = artworkUri,
                                metadata = extractTechnicalMetadata(
                                    context,
                                    uri,
                                    mediaId,
                                    row.mimeType ?: "audio/*",
                                    row.durationMs
                                ),
                                loudness = extractLoudness(context, uri, mediaId),
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One unreadable row must not end the scan; the rest of the library
                        // is still worth having.
                    }
                }
            }

            return@withContext LocalScanResult.Success(tracks)
            
        } catch (e: CancellationException) {
            // Propagate cancellation
            throw e
        } catch (e: Exception) {
            return@withContext ScanMapping.failureFor(e)
        }
    }
    
    /**
     * Reads ReplayGain tags already present in the file. Returns null when the file carries
     * no usable tags, so loudness stays genuinely unknown rather than being invented.
     */
    private fun extractLoudness(context: Context, uri: Uri, mediaId: String): TrackLoudnessEntity? {
        val tags = try {
            context.contentResolver.openInputStream(uri)?.use { ReplayGainReader.read(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null

        return TrackLoudnessEntity(
            mediaId = mediaId,
            lufsIntegrated = tags.trackGainDb?.let(ReplayGainReference::lufsFromGain),
            truePeak = tags.trackPeak,
            albumLufs = tags.albumGainDb?.let(ReplayGainReference::lufsFromGain),
            albumPeak = tags.albumPeak,
            analysisVersion = ReplayGainReference.ANALYSIS_VERSION
        )
    }

    private fun extractTechnicalMetadata(
        context: Context, 
        uri: Uri, 
        mediaId: String, 
        fallbackMimeType: String,
        fallbackDuration: Long
    ): TrackTechnicalMetadataEntity {
        var codec: String? = null
        var container: String? = fallbackMimeType
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var duration: Long = fallbackDuration
        var isLossless = ScanMapping.isLossless(fallbackMimeType)
        
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let {
                bitrate = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                duration = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                container = it
                if (ScanMapping.isLossless(it)) isLossless = true
            }
        } catch (e: Exception) {
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
        
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            if (extractor.trackCount > 0) {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        codec = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            val encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            bitDepth = when (encoding) {
                                2 -> 16
                                3 -> 8
                                4 -> 32
                                21 -> 24
                                22 -> 32
                                else -> null
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            extractor.release()
        }
        
        return TrackTechnicalMetadataEntity(
            mediaId = mediaId,
            codec = codec ?: fallbackMimeType,
            container = container,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            durationMs = duration.takeIf { it > 0 },
            isLossless = isLossless,
            extractionVersion = 1,
            sourceVersion = 1
        )
    }
}

data class ScannedTrack(
    val item: MediaItemEntity,
    val file: LocalFileEntity,
    val metadata: TrackTechnicalMetadataEntity,
    val album: AlbumEntity?,
    val artists: List<ArtistEntity>,
    val artwork: ArtworkEntity?,
    /** Null when the file carries no ReplayGain tags - loudness is then unknown. */
    val loudness: TrackLoudnessEntity? = null
)
