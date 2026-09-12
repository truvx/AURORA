package dev.aurora.player.app

import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.QueueSnapshot
import dev.aurora.player.domain.library.QueueSnapshotTrack
import dev.aurora.player.domain.library.ResumePoint
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerState
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Records what was played, where playback got to, and what was queued.
 *
 * Kept out of [PlayerCoordinator] deliberately: the coordinator owns canonical playback
 * state and must stay correct whether or not anything is being persisted. This observes
 * that state instead, so a failure here can never break playback, and history can be
 * disabled or deleted without touching the player.
 *
 * All of this is private, local data. See docs/DATABASE_MODEL.md.
 */
class PlaybackHistoryRecorder(
    private val repository: LibraryOrganizationRepository,
    private val sessionId: String = UUID.randomUUID().toString(),
    private val now: () -> Long = System::currentTimeMillis,
    /** Fraction of a track that counts as listened rather than skipped. */
    private val completionThreshold: Float = 0.9f,
    /** Resume positions are written at most this often while a track plays. */
    private val resumeDebounceMs: Long = 5_000L,
    /** Positions below this are not worth resuming from. */
    private val minimumResumeMs: Long = 10_000L
) {

    private var lastTrackId: String? = null
    private var lastProgressMs: Long = 0L
    private var lastDurationMs: Long? = null
    private var lastResumeWriteAt: Long = 0L
    private var completedCurrentTrack = false
    private var lastQueueSignature: String? = null
    private var lastProvider: String = "LOCAL"

    /** Collects until the flow ends or the surrounding scope is cancelled. */
    suspend fun observe(states: Flow<PlayerState>) {
        states.collect { onState(it) }
    }

    suspend fun onState(state: PlayerState) {
        val track = state.currentTrack

        if (track?.id != lastTrackId) {
            finishPreviousTrack()
            if (track != null) {
                repository.recordEvent(
                    mediaId = track.id,
                    provider = track.provider.name,
                    kind = ListeningEventKind.PLAY,
                    progressMs = 0L,
                    sessionId = sessionId
                )
            }
            lastTrackId = track?.id
            lastProvider = track?.provider?.name ?: lastProvider
            lastProgressMs = 0L
            lastDurationMs = null
            lastResumeWriteAt = 0L
            completedCurrentTrack = false
        }

        if (track != null) {
            lastProgressMs = state.position.elapsed
            lastDurationMs = state.position.duration

            if (state.status == PlaybackStatus.Completed && !completedCurrentTrack) {
                completedCurrentTrack = true
                repository.recordEvent(
                    mediaId = track.id,
                    provider = track.provider.name,
                    kind = ListeningEventKind.COMPLETE,
                    progressMs = state.position.elapsed,
                    sessionId = sessionId
                )
                // A finished track should start from the beginning next time.
                repository.saveResumePoint(
                    ResumePoint(track.provider.name, track.id, 0L, state.position.duration)
                )
            } else if (state.status == PlaybackStatus.Playing) {
                maybeSaveResume(track, state.position.elapsed, state.position.duration)
            }
        }

        maybeSaveQueueSnapshot(state)
    }

    /**
     * Writes a resume point at most once per debounce window. Position updates arrive on
     * every tick; persisting each one would write to disk several times a second.
     */
    private suspend fun maybeSaveResume(track: MediaItem, elapsed: Long, duration: Long?) {
        if (elapsed < minimumResumeMs) return
        val timestamp = now()
        if (timestamp - lastResumeWriteAt < resumeDebounceMs) return
        lastResumeWriteAt = timestamp
        repository.saveResumePoint(
            ResumePoint(track.provider.name, track.id, elapsed, duration)
        )
    }

    /**
     * A track left before [completionThreshold] was skipped, not listened to. Unknown
     * duration counts as a skip rather than inventing a completion.
     */
    private suspend fun finishPreviousTrack() {
        val previousId = lastTrackId ?: return
        if (completedCurrentTrack) return

        val duration = lastDurationMs
        val listenedEnough =
            duration != null && duration > 0 && lastProgressMs >= duration * completionThreshold

        repository.recordEvent(
            mediaId = previousId,
            provider = lastProvider,
            kind = if (listenedEnough) ListeningEventKind.COMPLETE else ListeningEventKind.SKIP,
            progressMs = lastProgressMs,
            sessionId = sessionId
        )
    }

    /** Snapshots only when the queue's identity or position actually changes. */
    private suspend fun maybeSaveQueueSnapshot(state: PlayerState) {
        val items = state.queue.items
        if (items.isEmpty()) return

        val signature = buildString {
            items.forEach { append(it.id).append(',') }
            append('@').append(state.queue.currentIndex)
        }
        if (signature == lastQueueSignature) return
        lastQueueSignature = signature

        repository.saveQueueSnapshot(
            QueueSnapshot(
                tracks = items.map { QueueSnapshotTrack(it.id, it.provider.name) },
                currentIndex = state.queue.currentIndex,
                positionMs = state.position.elapsed,
                repeatMode = state.queue.repeatMode.name,
                shuffleMode = state.queue.shuffleMode.name
            )
        )
    }
}
