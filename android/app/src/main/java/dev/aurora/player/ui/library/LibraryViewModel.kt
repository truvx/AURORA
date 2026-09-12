package dev.aurora.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.providers.LocalLibraryProvider
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for the local library screen.
 *
 * Owns the scan lifecycle so the screen only renders state and forwards intents. The item
 * list is observed from the database, so a completed scan updates the UI without the screen
 * having to re-request anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val provider: LocalLibraryProvider,
    private val organization: LibraryOrganizationRepository
) : ViewModel() {

    val items: StateFlow<List<MediaItem>> = provider.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = organization.observeFavorites()
        .map { favorites -> favorites.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setFavorite(mediaId: String, isFavorite: Boolean) {
        viewModelScope.launch { organization.setFavorite(mediaId, isFavorite) }
    }

    // --- playlists ----------------------------------------------------------------------

    val playlists: StateFlow<List<Playlist>> = organization.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openPlaylistId = MutableStateFlow<String?>(null)
    val openPlaylistId: StateFlow<String?> = _openPlaylistId.asStateFlow()

    /** Tracks of the currently open playlist; empty when none is open. */
    val openPlaylistTracks: StateFlow<List<PlaylistTrack>> = _openPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else organization.observePlaylistTracks(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openPlaylist(playlistId: String?) {
        _openPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { organization.createPlaylist(trimmed) }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (_openPlaylistId.value == playlistId) _openPlaylistId.value = null
            organization.deletePlaylist(playlistId)
        }
    }

    fun addToPlaylist(playlistId: String, mediaId: String) {
        viewModelScope.launch { organization.addToPlaylist(playlistId, mediaId) }
    }

    fun removeFromPlaylist(playlistId: String, entryId: String) {
        viewModelScope.launch { organization.removeFromPlaylist(playlistId, entryId) }
    }

    fun moveEntry(playlistId: String, from: Int, to: Int) {
        viewModelScope.launch { organization.movePlaylistEntry(playlistId, from, to) }
    }

    fun scan() {
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch {
            try {
                provider.syncLibrary()
            } finally {
                _isScanning.value = false
            }
        }
    }
}
