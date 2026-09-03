package dev.aurora.player.domain.player

sealed class PlayerEffect {
    data class ShowError(val message: String) : PlayerEffect()
    object HapticFeedback : PlayerEffect()
    data class NavigationHint(val route: String) : PlayerEffect()
}
