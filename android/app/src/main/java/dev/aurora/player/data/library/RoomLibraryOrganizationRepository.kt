package dev.aurora.player.data.library

import dev.aurora.player.data.db.ListeningEventEntity
import dev.aurora.player.data.db.LibraryOrganizationDao
import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.db.PlaylistEntity
import dev.aurora.player.data.db.QueueSnapshotEntity
import dev.aurora.player.data.db.QueueSnapshotItemEntity
import dev.aurora.player.data.db.ResumePositionEntity
import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.library.ListeningHistoryRecord
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistSource
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.library.QueueSnapshot
import dev.aurora.player.domain.library.QueueSnapshotTrack
import dev.aurora.player.domain.library.ResumePoint
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-backed library organization.
 *
 * Item lookups join against the local library so a favorite or playlist entry whose media is
 * missing surfaces as a null item rather than disappearing - the user's curation is never
 * silently edited by availability changes.
 */
class RoomLibraryOrganizationRepository(
    private val organizationDao: LibraryOrganizationDao,
    private val localLibraryDao: LocalLibraryDao,
    private val now: () -> Long = System::currentTimeMillis
) : LibraryOrganizationRepository {

    private fun observeItemsById(): Flow<Map<String, MediaItem>> =
        localLibraryDao.observeLocalItems().map { relations ->
            relations.associate { relation ->
                relation.mediaItem.id to MediaItem(
                    id = relation.mediaItem.id,
                    provider = ProviderKind.LOCAL,
                    title = relation.mediaItem.title,
                    artist = relation.artists.firstOrNull()?.name,
                    album = relation.album?.title,
                    artworkUri = relation.album?.artworkRef,
                    isAvailable = relation.mediaItem.isAvailable
                )
            }
        }

    // --- favorites ----------------------------------------------------------------------

    override fun observeFavorites(): Flow<List<MediaItem>> =
        combine(organizationDao.observeFavorites(), observeItemsById()) { entries, items ->
            entries.mapNotNull { items[it.mediaId] }
        }

    override fun observeIsFavorite(mediaId: String): Flow<Boolean> =
        organizationDao.observeIsFavorite(mediaId)

    override suspend fun setFavorite(mediaId: String, isFavorite: Boolean) {
        organizationDao.setFavorite(mediaId, isFavorite, now())
    }

    // --- playlists ----------------------------------------------------------------------

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(
            organizationDao.observePlaylists(),
            localLibraryDao.observeLocalItems()
        ) { playlists, _ -> playlists }
            .map { playlists ->
                playlists.map { entity ->
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                        sourceKind = runCatching { PlaylistSource.valueOf(entity.sourceKind) }
                            .getOrDefault(PlaylistSource.USER),
                        trackCount = organizationDao.getPlaylistEntries(entity.id).size
                    )
                }
            }

    override fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrack>> =
        combine(
            organizationDao.observePlaylistEntries(playlistId),
            observeItemsById()
        ) { entries, items ->
            entries.map { entry ->
                PlaylistTrack(
                    entryId = entry.entryId,
                    position = entry.position,
                    item = items[entry.mediaId]
                )
            }
        }

    override suspend fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        val timestamp = now()
        organizationDao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = name,
                createdAt = timestamp,
                updatedAt = timestamp,
                sourceKind = PlaylistSource.USER.name
            )
        )
        return id
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        organizationDao.renamePlaylist(playlistId, name, now())
    }

    override suspend fun deletePlaylist(playlistId: String) {
        organizationDao.deletePlaylist(playlistId)
    }

    override suspend fun addToPlaylist(playlistId: String, mediaId: String) {
        organizationDao.addToPlaylist(playlistId, UUID.randomUUID().toString(), mediaId, now())
    }

    override suspend fun removeFromPlaylist(playlistId: String, entryId: String) {
        organizationDao.removeFromPlaylist(playlistId, entryId, now())
    }

    override suspend fun movePlaylistEntry(playlistId: String, fromPosition: Int, toPosition: Int) {
        organizationDao.moveEntry(playlistId, fromPosition, toPosition, now())
    }

    // --- history and resume -------------------------------------------------------------

    override suspend fun recordEvent(
        mediaId: String,
        provider: String,
        kind: ListeningEventKind,
        progressMs: Long,
        sessionId: String
    ) {
        organizationDao.insertListeningEvent(
            ListeningEventEntity(
                mediaId = mediaId,
                provider = provider,
                timestamp = now(),
                sessionId = sessionId,
                kind = kind.name,
                progressMs = progressMs
            )
        )
    }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItem>> =
        combine(
            organizationDao.observeRecentEvents(limit * 4),
            observeItemsById()
        ) { events, items ->
            events.asSequence()
                .filter { it.kind != ListeningEventKind.SKIP.name }
                .map { it.mediaId }
                .distinct()
                .mapNotNull { items[it] }
                .take(limit)
                .toList()
        }

    override suspend fun saveResumePoint(point: ResumePoint) {
        organizationDao.upsertResumePosition(
            ResumePositionEntity(
                provider = point.provider,
                mediaId = point.mediaId,
                positionMs = point.positionMs,
                durationMs = point.durationMs,
                updatedAt = now()
            )
        )
    }

    override suspend fun getResumePoint(provider: String, mediaId: String): ResumePoint? =
        organizationDao.getResumePosition(provider, mediaId)?.let {
            ResumePoint(it.provider, it.mediaId, it.positionMs, it.durationMs)
        }

    // --- queue snapshots ----------------------------------------------------------------

    override suspend fun saveQueueSnapshot(snapshot: QueueSnapshot) {
        val snapshotId = UUID.randomUUID().toString()
        organizationDao.replaceQueueSnapshot(
            QueueSnapshotEntity(
                snapshotId = snapshotId,
                createdAt = now(),
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                repeatMode = snapshot.repeatMode,
                shuffleMode = snapshot.shuffleMode
            ),
            snapshot.tracks.mapIndexed { index, track ->
                QueueSnapshotItemEntity(
                    snapshotId = snapshotId,
                    position = index,
                    mediaId = track.mediaId,
                    provider = track.provider
                )
            }
        )
    }

    override suspend fun loadQueueSnapshot(): QueueSnapshot? {
        val snapshot = organizationDao.getLatestQueueSnapshot() ?: return null
        val items = organizationDao.getQueueSnapshotItems(snapshot.snapshotId)
        return QueueSnapshot(
            tracks = items.map { QueueSnapshotTrack(it.mediaId, it.provider) },
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            repeatMode = snapshot.repeatMode,
            shuffleMode = snapshot.shuffleMode
        )
    }

    // --- privacy ------------------------------------------------------------------------

    override suspend fun pruneHistory(maxAgeMs: Long, now: Long) {
        organizationDao.pruneEventsBefore(now - maxAgeMs)
    }

    override suspend fun exportHistory(): List<ListeningHistoryRecord> =
        organizationDao.getAllEvents().map { event ->
            ListeningHistoryRecord(
                mediaId = event.mediaId,
                provider = event.provider,
                timestamp = event.timestamp,
                kind = runCatching { ListeningEventKind.valueOf(event.kind) }
                    .getOrDefault(ListeningEventKind.PLAY),
                progressMs = event.progressMs
            )
        }

    override suspend fun deleteAllPrivateData() {
        organizationDao.deleteAllPrivateData()
    }
}
