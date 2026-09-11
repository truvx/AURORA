package dev.aurora.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.providers.LocalLibraryProvider
import dev.aurora.player.domain.library.LibraryOrganizationRepository
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
