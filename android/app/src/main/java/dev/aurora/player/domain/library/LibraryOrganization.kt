package dev.aurora.player.domain.library

import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.flow.Flow

/** A user-created or imported playlist, with its current size. */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceKind: PlaylistSource,
    val trackCount: Int
)

enum class PlaylistSource { USER, IMPORTED }

/** One ordered slot in a playlist. `entryId` is stable across reordering. */
data class PlaylistTrack(
    val entryId: String,
    val position: Int,
    val item: MediaItem?
)

enum class ListeningEventKind { PLAY, SKIP, COMPLETE }

data class ResumePoint(
    val provider: String,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long?
)

data class QueueSnapshotTrack(val mediaId: String, val provider: String)

data class QueueSnapshot(
    val tracks: List<QueueSnapshotTrack>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: String,
    val shuffleMode: String
)

/**
 * Durable library organization, independent of any provider.
 *
 * Everything here must work offline: favorites, playlists, history, resume, and queue
 * restoration are all local state. History is private by default and fully deletable
 * without affecting playback or the user's own curation.
 */
interface LibraryOrganizationRepository {

    fun observeFavorites(): Flow<List<MediaItem>>
    fun observeIsFavorite(mediaId: String): Flow<Boolean>
    suspend fun setFavorite(mediaId: String, isFavorite: Boolean)

    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrack>>
    suspend fun createPlaylist(name: String): String
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addToPlaylist(playlistId: String, mediaId: String)
    suspend fun removeFromPlaylist(playlistId: String, entryId: String)
    suspend fun movePlaylistEntry(playlistId: String, fromPosition: Int, toPosition: Int)

    suspend fun recordEvent(
        mediaId: String,
        provider: String,
        kind: ListeningEventKind,
        progressMs: Long,
        sessionId: String
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItem>>

    suspend fun saveResumePoint(point: ResumePoint)
    suspend fun getResumePoint(provider: String, mediaId: String): ResumePoint?

    suspend fun saveQueueSnapshot(snapshot: QueueSnapshot)
    suspend fun loadQueueSnapshot(): QueueSnapshot?

    /** Retention: drops history older than [maxAgeMs]. */
    suspend fun pruneHistory(maxAgeMs: Long, now: Long)

    /** Privacy export: the user's own listening record, oldest first. */
    suspend fun exportHistory(): List<ListeningHistoryRecord>

    /** Privacy deletion: erases behavioural data, keeping favorites and playlists. */
    suspend fun deleteAllPrivateData()
}

data class ListeningHistoryRecord(
    val mediaId: String,
    val provider: String,
    val timestamp: Long,
    val kind: ListeningEventKind,
    val progressMs: Long
)
