package dev.aurora.player.app

import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.domain.providers.MusicProvider

/**
 * Rebuilds the queue from the last snapshot on a cold start.
 *
 * Restoration is deliberately silent: items are put back in the queue but nothing starts
 * playing. A cold start that begins making noise on its own is a worse surprise than an
 * empty player.
 *
 * Tracks that no longer resolve are dropped rather than faked, so a deleted file leaves a
 * shorter queue instead of an entry that fails when tapped.
 */
class QueueRestorer(
    private val repository: LibraryOrganizationRepository,
    private val providers: List<MusicProvider>
) {

    /** Returns the number of tracks put back, or 0 when there was nothing to restore. */
    suspend fun restore(coordinator: PlayerCoordinator): Int {
        val snapshot = repository.loadQueueSnapshot() ?: return 0
        if (snapshot.tracks.isEmpty()) return 0

        val resolved = snapshot.tracks.mapNotNull { track ->
            val kind = runCatching { ProviderKind.valueOf(track.provider) }.getOrNull()
                ?: return@mapNotNull null
            providers.firstOrNull { it.id == kind }
                ?.resolveMetadata(track.mediaId)
                ?.getOrNull()
        }

        if (resolved.isEmpty()) return 0

        resolved.forEach { item: MediaItem ->
            coordinator.dispatch(PlayerCommand.AddToQueue(item))
        }
        return resolved.size
    }
}
