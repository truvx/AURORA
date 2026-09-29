package dev.aurora.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.discovery.CoverArtRepository
import dev.aurora.player.data.discovery.DiscoveryUnavailable
import dev.aurora.player.data.discovery.LatestReleasesRepository
import dev.aurora.player.data.discovery.MediaResolver
import dev.aurora.player.data.discovery.WhatsNewStore
import dev.aurora.player.domain.discovery.ArtistAffinity
import dev.aurora.player.domain.discovery.ArtistSignal
import dev.aurora.player.domain.discovery.Release
import dev.aurora.player.domain.discovery.SignalKind
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.ListeningEventKind
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A release as the feed shows it: the record, not the upload.
 *
 * A channel publishes an album one video at a time, so the raw list is ten uploads that
 * share one sleeve and one name. [album] is what collapses them back into the record they
 * came from, and the cover is the sleeve from the Cover Art Archive rather than a still
 * from the video.
 */
data class ResolvedRelease(
    val release: Release,
    val coverUrl: String?,
    val title: String,
    val artist: String,
)

/** An artist the listener actually plays, for the row that used to hold mood mixes. */
data class TopArtist(val name: String, val artworkUri: String?)

/** What the Latest Releases row is doing, so the UI can say which rather than sit blank. */
sealed class ReleasesState {
    object Loading : ReleasesState()
    data class Ready(val releases: List<ResolvedRelease>, val unseen: Set<String>) : ReleasesState()
    /** No artists ranked yet: nothing has been played, so there is nothing to follow. */
    object NoHistory : ReleasesState()
    object NotConfigured : ReleasesState()
    object Unavailable : ReleasesState()
}

/**
 * The Home feed: recent listening, the artists behind it, and what those artists released.
 *
 * Everything here is derived on the device from the listener's own history and from the
 * artists they followed by hand. Nothing is fetched until there is a reason to: with no
 * plays recorded and nobody followed there is nothing to ask YouTube about.
 */
class HomeViewModel(
    private val organization: LibraryOrganizationRepository,
    private val resolver: MediaResolver,
    private val releasesRepository: LatestReleasesRepository,
    private val whatsNew: WhatsNewStore,
    private val followedArtists: dev.aurora.player.data.discovery.FollowedArtistsStore? = null,
    /** Absent in tests that do not care what the sleeves look like. */
    private val coverArt: CoverArtRepository? = null,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _releases = MutableStateFlow<ReleasesState>(ReleasesState.Loading)
    val releases: StateFlow<ReleasesState> = _releases.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<MediaItem>> = _recentlyPlayed.asStateFlow()

    private val _topArtists = MutableStateFlow<List<String>>(emptyList())

    /**
     * The people behind the listening, rather than six invented moods.
     *
     * A "Chill Mix" was a search dressed up as a record: nothing existed until it was
     * tapped, and its cover was a gradient because there was nothing to show. An artist is
     * something the listener already chose, has a real picture, and leads somewhere with
     * their actual songs in it.
     */
    private val _artists = MutableStateFlow<List<TopArtist>>(emptyList())
    val artists: StateFlow<List<TopArtist>> = _artists.asStateFlow()

    /**
     * The listener's own playlists, newest activity first, for the Home row.
     *
     * The whole list rather than a page of it: [dev.aurora.player.domain.library.Playlist]
     * already carries its size, so the header can say how many there are without a second
     * query, and a hand-made collection of playlists is not a table that needs paging.
     */
    val playlists: StateFlow<List<dev.aurora.player.domain.library.Playlist>> =
        organization.observePlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Favorite ids for every provider, so a favorited YouTube track shows as one. */
    val favoriteIds: StateFlow<Set<String>> = organization.observeFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            // History and favorites together: what was played, and what was chosen.
            combine(
                organization.observeRecentHistory(HISTORY_DEPTH),
                organization.observeFavoriteIds()
            ) { history, favorites -> history to favorites }
                .collect { (history, favorites) ->
                    val ids = (history.map { it.mediaId } + favorites).distinct()
                    val items = resolver.resolve(ids)

                    _recentlyPlayed.value = history
                        .asSequence()
                        .filter { it.kind != ListeningEventKind.SKIP }
                        .map { it.mediaId }
                        .distinct()
                        .mapNotNull { items[it] }
                        .take(RECENT_LIMIT)
                        .toList()

                    val signals = history.mapNotNull { record ->
                        val artist = items[record.mediaId]?.artist ?: return@mapNotNull null
                        ArtistSignal(
                            artist = artist,
                            kind = when (record.kind) {
                                ListeningEventKind.COMPLETE -> SignalKind.COMPLETE
                                ListeningEventKind.SKIP -> SignalKind.SKIP
                                ListeningEventKind.PLAY -> SignalKind.PLAY
                            },
                            timestampMs = record.timestamp
                        )
                    } + favorites.mapNotNull { id ->
                        items[id]?.artist?.let { ArtistSignal(it, SignalKind.FAVORITE, now()) }
                    }

                    /*
                     * Followed artists first, then the ones inferred from listening.
                     *
                     * An explicit follow is a stated choice and outranks a guess, and it
                     * is the only way to watch an artist you have just discovered and have
                     * not played yet. The list stays bounded because each artist costs a
                     * YouTube lookup.
                     */
                    val inferred = ArtistAffinity.topArtists(signals, now())
                    val followed = followedArtists?.followed?.value.orEmpty()
                    val artists = (followed + inferred)
                        .distinctBy { it.lowercase() }
                        .take(ARTIST_LIMIT)

                    _topArtists.value = artists

                    /*
                     * A face for each name, taken from something of theirs that was played.
                     * There is no artist-photo endpoint in what AURORA is allowed to call,
                     * and a track's own artwork is at least a picture of the right person's
                     * record rather than a generated initial.
                     */
                    val artworkByArtist = buildMap {
                        for (item in items.values) {
                            val name = ArtistAffinity.displayName(item.artist) ?: continue
                            val art = item.artworkUri ?: continue
                            putIfAbsent(name.lowercase(), art)
                        }
                    }
                    _artists.value = artists.map { name ->
                        TopArtist(name, artworkByArtist[name.lowercase()])
                    }

                    loadReleases(artists)
                }
        }
    }

    private suspend fun loadReleases(artists: List<String>) {
        if (artists.isEmpty()) {
            _releases.value = ReleasesState.NoHistory
            return
        }
        releasesRepository.latest(artists)
            .onSuccess { releases ->
                _releases.value = ReleasesState.Ready(
                    releases = resolve(releases),
                    unseen = whatsNew.unseen(releases).map { it.item.id }.toSet()
                )
            }
            .onFailure { error ->
                _releases.value = when ((error as? DiscoveryUnavailable)?.reason) {
                    DiscoveryUnavailable.Reason.NO_API_KEY -> ReleasesState.NotConfigured
                    else -> ReleasesState.Unavailable
                }
            }
    }

    /**
     * Turns uploads into records: one card per release, wearing its own sleeve.
     *
     * The lookup is cached for a month and rate-limited by the repository, so this is one
     * slow pass on a cold cache and free afterwards. Anything the lookup cannot place keeps
     * the upload's own title and thumbnail and is never folded into another record, because
     * "unknown album" is not an album two tracks can share.
     */
    private suspend fun resolve(releases: List<Release>): List<ResolvedRelease> {
        val seen = mutableSetOf<String>()
        val cards = mutableListOf<ResolvedRelease>()
        for (release in releases) {
            val cover = runCatching {
                coverArt?.coverFor(
                    videoTitle = release.item.title,
                    channelTitle = release.item.artist,
                    knownArtist = release.artist,
                )
            }.getOrNull()
            val album = cover?.album?.takeIf { it.isNotBlank() }
            if (album != null && !seen.add("${release.artist.lowercase()}|${album.lowercase()}")) {
                continue
            }
            cards += ResolvedRelease(
                release = release,
                coverUrl = cover?.coverUrl ?: release.item.artworkUri,
                title = album ?: cover?.identity?.title ?: release.item.title,
                artist = cover?.identity?.artist ?: release.artist,
            )
        }
        return cards
    }

    fun markReleasesSeen() = whatsNew.markSeen(now())

    fun setFavorite(mediaId: String, isFavorite: Boolean) {
        viewModelScope.launch { organization.setFavorite(mediaId, isFavorite) }
    }

    private companion object {
        /** Each artist is a separate YouTube lookup, so the feed asks about a few. */
        const val ARTIST_LIMIT = 8

        /** Enough history for a fair ranking without reading the whole table. */
        const val HISTORY_DEPTH = 200
        const val RECENT_LIMIT = 8
    }
}
