package dev.aurora.player.data.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Implemented by adapters backed by a Media3 player, so the media session can attach to the
 * real ExoPlayer without downcasting to a concrete adapter.
 *
 * The previous code cast `container.playerAdapter` straight to [Media3PlayerAdapter]. That
 * adapter is a [CompositePlayerAdapter], so the cast threw on every service bind and every
 * external transport control - lock screen, notification, Bluetooth, Android Auto - was
 * dead without any in-app symptom.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
interface Media3PlayerOwner {
    /** The player the media session should control. */
    val sessionPlayer: ExoPlayer
}
