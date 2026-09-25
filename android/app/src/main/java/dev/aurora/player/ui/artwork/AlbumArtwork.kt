package dev.aurora.player.ui.artwork

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.aurora.player.data.discovery.CoverArtRepository
import dev.aurora.player.domain.discovery.TrackIdentifier
import dev.aurora.player.domain.discovery.TrackIdentity
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind

/** Supplied by the composition root; absent in previews and in tests that do not need it. */
val LocalCoverArtRepository = staticCompositionLocalOf<CoverArtRepository?> { null }

/**
 * What the record looks like, and what it is called.
 *
 * [artworkUri] is the album sleeve where one was found, and the track's own artwork
 * otherwise - for a local file that is the embedded cover, and for a YouTube video it is
 * the thumbnail, which is a still from the video rather than a cover. Falling back to it
 * is deliberate: a frame of the right video beats a blank square.
 */
data class ResolvedArtwork(
    val artworkUri: String?,
    val artist: String?,
    val title: String?,
    val album: String?,
    /**
     * What record this is, where AURORA is sure enough to ask a database about it.
     *
     * Carried here so nothing downstream has to re-read a title that has already been
     * read. Parsing "Soft Spot" a second time, with a channel name for an artist, throws
     * away the answer the cover lookup already paid for and comes back with a guess.
     */
    val identity: TrackIdentity? = null,
)

/**
 * What this track is, before any lookup has run.
 *
 * A local file's tags are the truth about it - somebody wrote them for their own library -
 * so they are taken as certain. A YouTube upload is read apart by the identifier, which
 * says itself how much to trust the result.
 */
private fun initialIdentity(track: MediaItem): TrackIdentity? =
    if (track.provider == ProviderKind.YOUTUBE) {
        TrackIdentifier.identify(track.title, track.artist)
    } else {
        track.artist
            ?.takeIf { it.isNotBlank() }
            ?.let { TrackIdentity(artist = it, title = track.title, confident = true) }
    }

/**
 * Resolves the album artwork for a track, looking it up once per track.
 *
 * Only for YouTube. A local file carries its own tags and its own embedded cover, and
 * those are the truth about it - going to a music database to second-guess someone's own
 * library would be both slower and wrong.
 */
@Composable
fun rememberAlbumArtwork(track: MediaItem?): State<ResolvedArtwork> {
    val repository = LocalCoverArtRepository.current
    val fallback = ResolvedArtwork(
        artworkUri = track?.artworkUri,
        artist = track?.artist,
        title = track?.title,
        album = track?.album,
        identity = track?.let { initialIdentity(it) },
    )
    val resolved = remember(track?.id) { mutableStateOf(fallback) }

    LaunchedEffect(track?.id, repository) {
        // Start from what the track already says, so the player is never blank while a
        // lookup is in flight.
        resolved.value = fallback
        if (repository == null || track == null) return@LaunchedEffect
        if (track.provider != ProviderKind.YOUTUBE) return@LaunchedEffect

        val cover = repository.coverFor(track.title, track.artist) ?: return@LaunchedEffect
        resolved.value = ResolvedArtwork(
            artworkUri = cover.coverUrl ?: track.artworkUri,
            artist = cover.identity.artist,
            title = cover.identity.title,
            album = cover.album ?: track.album,
            identity = cover.identity,
        )
    }

    return resolved
}
