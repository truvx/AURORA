package dev.aurora.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.discovery.DiscoveryUnavailable
import dev.aurora.player.data.discovery.LatestReleasesRepository
import dev.aurora.player.data.discovery.MediaResolver
import dev.aurora.player.data.discovery.WhatsNewStore
import dev.aurora.player.domain.discovery.ArtistAffinity
import dev.aurora.player.domain.discovery.ArtistSignal
import dev.aurora.player.domain.discovery.Mix
import dev.aurora.player.domain.discovery.MixCatalog
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

/** What the Latest Releases row is doing, so the UI can say which rather than sit blank. */
sealed class ReleasesState {
    object Loading : ReleasesState()
    data class Ready(val releases: List<Release>, val unseen: Set<String>) : ReleasesState()
    /** No artists ranked yet: nothing has been played, so there is nothing to follow. */
    object NoHistory : ReleasesState()
    object NotConfigured : ReleasesState()
    object Unavailable : ReleasesState()
}

/**
 * The Home feed: recent listening, the artists behind it, and what those artists released.
 *
 * Everything here is derived on the device from the listener's own history. Nothing is
 * fetched until there is a reason to: with no plays recorded there are no artists to follow
 * and no releases to ask YouTube about.
 */
class HomeViewModel(
    private val organization: LibraryOrganizationRepository,
    private val resolver: MediaResolver,
    private val releasesRepository: LatestReleasesRepository,
    private val whatsNew: WhatsNewStore,
    private val now: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _releases = MutableStateFlow<ReleasesState>(ReleasesState.Loading)
    val releases: StateFlow<ReleasesState> = _releases.asStateFlow()

    private val _recentlyPlayed = MutableStateFlow<List<MediaItem>>(emptyList())
    val recentlyPlayed: StateFlow<List<MediaItem>> = _recentlyPlayed.asStateFlow()

    private val _topArtists = MutableStateFlow<List<String>>(emptyList())

    val mixes: StateFlow<List<Mix>> = _topArtists
        .map { MixCatalog.mixesFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MixCatalog.mixesFor(emptyList()))

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

                    val artists = ArtistAffinity.topArtists(signals, now())
                    _topArtists.value = artists
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
                    releases = releases,
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

    fun markReleasesSeen() = whatsNew.markSeen(now())

    fun setFavorite(mediaId: String, isFavorite: Boolean) {
        viewModelScope.launch { organization.setFavorite(mediaId, isFavorite) }
    }

    private companion object {
        /** Enough history for a fair ranking without reading the whole table. */
        const val HISTORY_DEPTH = 200
        const val RECENT_LIMIT = 8
    }
}
