package dev.aurora.player.ui.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.discovery.FollowedArtistsStore
import dev.aurora.player.data.providers.LocalLibraryProvider
import dev.aurora.player.domain.discovery.ArtistPage
import dev.aurora.player.domain.library.LibraryOrganizationRepository
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
 * One artist, as seen from this device.
 *
 * Everything here is local: their tracks in the library, ranked by how often this listener
 * actually played them. AURORA has no artist catalogue and no chart data, so "top songs"
 * means the only thing it can honestly mean.
 */
class ArtistViewModel(
    val artist: String,
    library: LocalLibraryProvider,
    organization: LibraryOrganizationRepository,
    private val followedArtists: FollowedArtistsStore,
) : ViewModel() {

    val topSongs: StateFlow<List<MediaItem>> = combine(
        library.observeLibrary(),
        organization.observeRecentHistory(HISTORY_DEPTH)
    ) { items, history ->
        ArtistPage.topSongs(ArtistPage.tracksBy(artist, items), ArtistPage.playCounts(history))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isFollowed: StateFlow<Boolean> = followedArtists.followed
        .map { followed -> followed.any { it.equals(artist.trim(), ignoreCase = true) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), followedArtists.isFollowed(artist))

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    init {
        viewModelScope.launch {
            organization.observeFavoriteIds().collect { _favoriteIds.value = it.toSet() }
        }
    }

    fun setFollowed(followed: Boolean) {
        followedArtists.setFollowed(artist, followed)
    }

    private companion object {
        /** Deep enough to rank a library; the same depth the Home feed reads. */
        const val HISTORY_DEPTH = 200
    }
}
