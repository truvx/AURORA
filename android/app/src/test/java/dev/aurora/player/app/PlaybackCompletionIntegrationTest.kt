package dev.aurora.player.app

import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.library.ListeningHistoryRecord
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.library.QueueSnapshot
import dev.aurora.player.domain.library.ResumePoint
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlayerCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real coordinator and the real recorder together.
 *
 * Both were individually correct while the feature did not work: the coordinator set
 * Completed and an engine settling event immediately overwrote it, and the recorder only
 * ever saw the overwritten value through a conflated StateFlow. Only a test spanning both
 * catches that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCompletionIntegrationTest {

    private class RecordingRepo : LibraryOrganizationRepository {
        val events = mutableListOf<Pair<String, ListeningEventKind>>()
        val snapshots = mutableListOf<QueueSnapshot>()

        override suspend fun recordEvent(
            mediaId: String, provider: String, kind: ListeningEventKind,
            progressMs: Long, sessionId: String
        ) { events += mediaId to kind }

        override suspend fun saveQueueSnapshot(snapshot: QueueSnapshot) { snapshots += snapshot }
        override suspend fun saveResumePoint(point: ResumePoint) {}
        override fun observeFavorites(): Flow<List<MediaItem>> = flowOf(emptyList())
        override fun observeIsFavorite(mediaId: String): Flow<Boolean> = flowOf(false)
        override suspend fun setFavorite(mediaId: String, isFavorite: Boolean) {}
        override fun observePlaylists(): Flow<List<Playlist>> = flowOf(emptyList())
        override fun observePlaylistTracks(playlistId: String): Flow<List<PlaylistTrack>> = flowOf(emptyList())
        override suspend fun createPlaylist(name: String): String = "p"
        override suspend fun renamePlaylist(playlistId: String, name: String) {}
        override suspend fun deletePlaylist(playlistId: String) {}
        override suspend fun addToPlaylist(playlistId: String, mediaId: String) {}
        override suspend fun removeFromPlaylist(playlistId: String, entryId: String) {}
        override suspend fun movePlaylistEntry(playlistId: String, fromPosition: Int, toPosition: Int) {}
        override fun observeRecentlyPlayed(limit: Int): Flow<List<MediaItem>> = flowOf(emptyList())
        override suspend fun getResumePoint(provider: String, mediaId: String): ResumePoint? = null
        override suspend fun loadQueueSnapshot(): QueueSnapshot? = null
        override suspend fun pruneHistory(maxAgeMs: Long, now: Long) {}
        override suspend fun exportHistory(): List<ListeningHistoryRecord> = emptyList()
        override suspend fun deleteAllPrivateData() {}
    }

    private class EndingAdapter : PlayerAdapter {
        override val events = MutableSharedFlow<EngineEvent>(replay = 1, extraBufferCapacity = 64)
        override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
            events.tryEmit(EngineEvent.Prepared)
        }
        override fun play() { events.tryEmit(EngineEvent.Started) }
        /** Media3 emits nothing here once the track has already ended. */
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}

        /** Reproduces Media3's STATE_ENDED emission order exactly. */
        suspend fun endTrack() {
            events.emit(EngineEvent.TrackCompleted)
            events.emit(EngineEvent.BufferingChanged(false))
        }
    }

    private val resolver = object : TrackResolver {
        override suspend fun resolveUri(trackId: String) = "file:///$trackId.flac"
        override suspend fun resolveLoudness(trackId: String): dev.aurora.player.domain.audio.TrackLoudnessData? = null
    }

    private val track = MediaItem(
        id = "local_1", provider = ProviderKind.LOCAL, title = "Track",
        artist = null, album = null, artworkUri = null
    )

    @Test
    fun `finishing a track records a completion and a queue snapshot`() =
        runTest(UnconfinedTestDispatcher()) {
            val adapter = EndingAdapter()
            val repo = RecordingRepo()
            val coordinator = PlayerCoordinator(adapter, resolver, backgroundScope)
            val recorder = PlaybackHistoryRecorder(repo, sessionId = "s1")

            val observing = launch { recorder.observe(coordinator.state) }

            coordinator.dispatch(PlayerCommand.Load(track))
            testScheduler.advanceUntilIdle()
            coordinator.dispatch(PlayerCommand.Play)
            testScheduler.advanceUntilIdle()

            adapter.endTrack()
            testScheduler.advanceUntilIdle()

            observing.cancel()

            assertTrue(
                "expected a COMPLETE, got ${repo.events}",
                repo.events.contains("local_1" to ListeningEventKind.COMPLETE)
            )
            assertTrue(
                "a played track must produce a restorable queue snapshot",
                repo.snapshots.isNotEmpty()
            )
        }
}
