package dev.aurora.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Durable library organization: favorites, playlists, listening history, resume positions,
 * and queue snapshots.
 *
 * Ordering operations are transactional so an interrupted reorder can never leave a playlist
 * with duplicate or gapped positions.
 */
@Dao
interface LibraryOrganizationDao {

    // --- favorites ----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryEntry(entry: LibraryEntryEntity)

    @Query("SELECT * FROM library_entries WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getLibraryEntry(mediaId: String): LibraryEntryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM library_entries WHERE mediaId = :mediaId AND isFavorite = 1)")
    fun observeIsFavorite(mediaId: String): Flow<Boolean>

    @Query("SELECT * FROM library_entries WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries WHERE isFavorite = 1 ORDER BY addedAt DESC")
    suspend fun getFavorites(): List<LibraryEntryEntity>

    /** Preserves the original `addedAt` so un-favoriting and re-favoriting keeps library order. */
    @Transaction
    suspend fun setFavorite(mediaId: String, isFavorite: Boolean, now: Long) {
        val existing = getLibraryEntry(mediaId)
        upsertLibraryEntry(
            LibraryEntryEntity(
                mediaId = mediaId,
                isFavorite = isFavorite,
                addedAt = existing?.addedAt ?: now,
                localState = existing?.localState
            )
        )
    }

    // --- playlists ----------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistEntry(entry: PlaylistEntryEntity)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observePlaylistEntries(playlistId: String): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistEntries(playlistId: String): List<PlaylistEntryEntity>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId AND position = :position LIMIT 1")
    suspend fun getEntryAt(playlistId: String, position: Int): PlaylistEntryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: String): Int

    @Query("UPDATE playlist_entries SET position = :position WHERE entryId = :entryId")
    suspend fun setEntryPosition(entryId: String, position: Int)

    @Query("DELETE FROM playlist_entries WHERE entryId = :entryId")
    suspend fun deleteEntryById(entryId: String)

    @Query(
        "UPDATE playlist_entries SET position = position - 1 " +
            "WHERE playlistId = :playlistId AND position BETWEEN :start AND :end"
    )
    suspend fun shiftPositionsDown(playlistId: String, start: Int, end: Int)

    @Query(
        "UPDATE playlist_entries SET position = position + 1 " +
            "WHERE playlistId = :playlistId AND position BETWEEN :start AND :end"
    )
    suspend fun shiftPositionsUp(playlistId: String, start: Int, end: Int)

    /** Appends to the end of the playlist. The same media item may be added more than once. */
    @Transaction
    suspend fun addToPlaylist(playlistId: String, entryId: String, mediaId: String, now: Long) {
        insertPlaylistEntry(
            PlaylistEntryEntity(
                entryId = entryId,
                playlistId = playlistId,
                mediaId = mediaId,
                position = nextPosition(playlistId),
                addedAt = now
            )
        )
        touchPlaylist(playlistId, now)
    }

    /** Removes an entry and closes the gap so positions stay contiguous. */
    @Transaction
    suspend fun removeFromPlaylist(playlistId: String, entryId: String, now: Long) {
        val entries = getPlaylistEntries(playlistId)
        val target = entries.firstOrNull { it.entryId == entryId } ?: return
        deleteEntryById(entryId)
        shiftPositionsDown(playlistId, target.position + 1, entries.size - 1)
        touchPlaylist(playlistId, now)
    }

    /**
     * Moves an entry between positions, shifting the entries it passes over. Out-of-range or
     * no-op moves leave the playlist untouched rather than corrupting ordering.
     */
    @Transaction
    suspend fun moveEntry(playlistId: String, fromPosition: Int, toPosition: Int, now: Long) {
        if (fromPosition == toPosition) return
        val entries = getPlaylistEntries(playlistId)
        val lastIndex = entries.size - 1
        if (fromPosition !in 0..lastIndex || toPosition !in 0..lastIndex) return

        val moving = entries.firstOrNull { it.position == fromPosition } ?: return

        if (fromPosition < toPosition) {
            shiftPositionsDown(playlistId, fromPosition + 1, toPosition)
        } else {
            shiftPositionsUp(playlistId, toPosition, fromPosition - 1)
        }
        setEntryPosition(moving.entryId, toPosition)
        touchPlaylist(playlistId, now)
    }

    // --- listening history --------------------------------------------------------------

    @Insert
    suspend fun insertListeningEvent(event: ListeningEventEntity)

    @Query("SELECT * FROM listening_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM listening_events ORDER BY timestamp ASC")
    suspend fun getAllEvents(): List<ListeningEventEntity>

    @Query("SELECT COUNT(*) FROM listening_events")
    suspend fun countEvents(): Int

    @Query("DELETE FROM listening_events WHERE timestamp < :cutoff")
    suspend fun pruneEventsBefore(cutoff: Long)

    @Query("DELETE FROM listening_events")
    suspend fun deleteAllEvents()

    // --- resume positions ---------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResumePosition(position: ResumePositionEntity)

    @Query("SELECT * FROM resume_positions WHERE provider = :provider AND mediaId = :mediaId LIMIT 1")
    suspend fun getResumePosition(provider: String, mediaId: String): ResumePositionEntity?

    @Query("DELETE FROM resume_positions WHERE provider = :provider AND mediaId = :mediaId")
    suspend fun clearResumePosition(provider: String, mediaId: String)

    @Query("DELETE FROM resume_positions")
    suspend fun deleteAllResumePositions()

    // --- queue snapshots ----------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSnapshot(snapshot: QueueSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSnapshotItems(items: List<QueueSnapshotItemEntity>)

    @Query("DELETE FROM queue_snapshots")
    suspend fun deleteAllQueueSnapshots()

    @Query("SELECT * FROM queue_snapshots ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestQueueSnapshot(): QueueSnapshotEntity?

    @Query("SELECT * FROM queue_snapshot_items WHERE snapshotId = :snapshotId ORDER BY position ASC")
    suspend fun getQueueSnapshotItems(snapshotId: String): List<QueueSnapshotItemEntity>

    /** Only the most recent snapshot is retained; restoring an older queue is not offered. */
    @Transaction
    suspend fun replaceQueueSnapshot(
        snapshot: QueueSnapshotEntity,
        items: List<QueueSnapshotItemEntity>
    ) {
        deleteAllQueueSnapshots()
        insertQueueSnapshot(snapshot)
        if (items.isNotEmpty()) insertQueueSnapshotItems(items)
    }

    // --- privacy ------------------------------------------------------------------------

    /** Erases everything derived from listening behaviour. Favorites and playlists survive. */
    @Transaction
    suspend fun deleteAllPrivateData() {
        deleteAllEvents()
        deleteAllResumePositions()
        deleteAllQueueSnapshots()
    }
}
