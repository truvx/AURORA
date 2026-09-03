package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.MediaItem

enum class RepeatMode {
    Off, One, All
}

enum class ShuffleMode {
    Off, On
}

data class QueueState(
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
    val shuffledOrder: List<Int> = emptyList()
)
