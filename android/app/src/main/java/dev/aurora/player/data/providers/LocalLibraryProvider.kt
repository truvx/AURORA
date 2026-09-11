package dev.aurora.player.data.providers

import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.scanner.LocalMusicScanner
import dev.aurora.player.data.scanner.LocalScanResult
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderCapability
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.models.TrackTechnicalMetadata
import dev.aurora.player.domain.providers.MusicProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalLibraryProvider(
    private val dao: LocalLibraryDao,
    private val scanner: LocalMusicScanner
) : MusicProvider {

    override val id: ProviderKind = ProviderKind.LOCAL

    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        setOf(
            ProviderCapability.SEARCH,
            ProviderCapability.METADATA,
            ProviderCapability.ARTWORK,
            ProviderCapability.PLAYBACK,
            ProviderCapability.QUEUE,
            ProviderCapability.OFFLINE_PLAYBACK
        )
    )

    suspend fun syncLibrary() {
        // 1. Fetch existing URIs to detect deletions
        val existingUris = dao.getAllLocalFileUris().toSet()

        // 2. Scan for current files
        val scanResult = scanner.scan()
        
        if (scanResult !is LocalScanResult.Success) {
            // A partial/failed scan must NEVER reconcile missing files.
            return
        }

        val tracks = scanResult.tracks
        val scannedUris = tracks.map { it.file.uri }.toSet()

        // 3. Upsert scanned tracks (this handles additions and updates)
        for (track in tracks) {
            dao.upsertLocalTrack(
                item = track.item,
                file = track.file,
                metadata = track.metadata,
                album = track.album,
                artists = track.artists,
                artwork = track.artwork,
                // Measured loudness only. Null means the file carried no ReplayGain tags,
                // and normalization must treat it as unknown rather than assume a level.
                loudness = track.loudness
            )
        }

        // 4. Handle missing/deleted files atomically via transaction
        val missingUris = (existingUris - scannedUris).toList()
        if (missingUris.isNotEmpty()) {
            dao.deleteStaleLocalFiles(missingUris)
        }
    }

    fun observeLibrary(): Flow<List<MediaItem>> {
        return dao.observeLocalItems().map { relations ->
            relations.map { relation ->
                val artistName = relation.artists.firstOrNull()?.name
                MediaItem(
                    id = relation.mediaItem.id,
                    provider = ProviderKind.LOCAL,
                    title = relation.mediaItem.title,
                    artist = artistName,
                    album = relation.album?.title,
                    artworkUri = relation.album?.artworkRef,
                    isAvailable = relation.mediaItem.isAvailable
                )
            }
        }
    }

    override suspend fun search(query: String): Result<List<MediaItem>> {
        val items = dao.getLocalItems().filter { relation ->
            relation.mediaItem.title.contains(query, ignoreCase = true) ||
            relation.artists.any { it.name.contains(query, ignoreCase = true) }
        }
        
        return Result.success(items.map { relation ->
            val artistName = relation.artists.firstOrNull()?.name
            MediaItem(
                id = relation.mediaItem.id,
                provider = ProviderKind.LOCAL,
                title = relation.mediaItem.title,
                artist = artistName,
                album = relation.album?.title,
                artworkUri = relation.album?.artworkRef,
                isAvailable = relation.mediaItem.isAvailable
            )
        })
    }

    override suspend fun resolveMetadata(trackId: String): Result<MediaItem> {
        val metadataEntity = dao.getMetadataForMedia(trackId)
        val fileEntity = dao.getLocalFileForMedia(trackId)
        
        val relation = dao.getLocalItems().find { it.mediaItem.id == trackId }
            ?: return Result.failure(Exception("Track not found in local library"))
        
        val techMeta = metadataEntity?.let {
            TrackTechnicalMetadata(
                codec = it.codec,
                container = it.container,
                bitrate = it.bitrate,
                sampleRate = it.sampleRate,
                channels = it.channels,
                bitDepth = it.bitDepth,
                durationMs = it.durationMs,
                isLossless = it.isLossless
            )
        }

        val artistName = relation.artists.firstOrNull()?.name

        return Result.success(
            MediaItem(
                id = relation.mediaItem.id,
                provider = ProviderKind.LOCAL,
                title = relation.mediaItem.title,
                artist = artistName,
                album = relation.album?.title,
                artworkUri = relation.album?.artworkRef,
                technicalMetadata = techMeta,
                isAvailable = fileEntity != null
            )
        )
    }
}
