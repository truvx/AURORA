package dev.aurora.player.ui.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.data.player.Media3PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.app.TrackResolver

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val coordinator = (application as dev.aurora.player.AuroraApp).container.playerCoordinator
}
