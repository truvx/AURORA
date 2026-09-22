package dev.aurora.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.data.discovery.DiscoveryUnavailable
import dev.aurora.player.data.discovery.TrendingRepository
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.providers.MusicProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What the YouTube half of the results is doing. The library half is always instant. */
sealed class RemoteSearchState {
    object Idle : RemoteSearchState()
    object Searching : RemoteSearchState()
    data class Results(val items: List<MediaItem>) : RemoteSearchState()
    object NotConfigured : RemoteSearchState()
    object Unavailable : RemoteSearchState()
}

/**
 * Search across the local library and YouTube.
 *
 * The two halves behave differently on purpose. Local search runs on every keystroke - it
 * is a database query on the device. A YouTube search costs 100 of the day's 10,000 quota
 * units, so it waits for the listener to actually ask.
 */
class SearchViewModel(
    private val localProvider: MusicProvider,
    private val youtubeProvider: MusicProvider,
    private val trending: TrendingRepository,
    private val region: String = Locale.getDefault().country.ifBlank { "US" }
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _localResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val localResults: StateFlow<List<MediaItem>> = _localResults.asStateFlow()

    private val _remote = MutableStateFlow<RemoteSearchState>(RemoteSearchState.Idle)
    val remote: StateFlow<RemoteSearchState> = _remote.asStateFlow()

    private val _trending = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Shown when nothing has been typed: somewhere to start rather than an empty screen. */
    val trendingNow: StateFlow<List<MediaItem>> = _trending.asStateFlow()

    val regionName: String = Locale("", region).displayCountry.ifBlank { region }

    private var localJob: Job? = null

    init {
        viewModelScope.launch {
            trending.trending(region).onSuccess { _trending.value = it }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) {
            _localResults.value = emptyList()
            _remote.value = RemoteSearchState.Idle
            return
        }
        localJob?.cancel()
        localJob = viewModelScope.launch {
            _localResults.value = localProvider.search(value).getOrDefault(emptyList())
        }
    }

    /** Called when the listener submits: this is the one that spends quota. */
    fun submit() {
        val value = _query.value.trim()
        if (value.isEmpty()) return
        _remote.value = RemoteSearchState.Searching
        viewModelScope.launch {
            youtubeProvider.search(value)
                .onSuccess { _remote.value = RemoteSearchState.Results(it) }
                .onFailure { error ->
                    _remote.value = when {
                        (error as? DiscoveryUnavailable)?.reason ==
                            DiscoveryUnavailable.Reason.NO_API_KEY -> RemoteSearchState.NotConfigured
                        error.message?.contains("API key", ignoreCase = true) == true ->
                            RemoteSearchState.NotConfigured
                        else -> RemoteSearchState.Unavailable
                    }
                }
        }
    }
}
