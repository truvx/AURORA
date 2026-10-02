package dev.aurora.player.ui

import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the UI needs to know about the floating window.
 *
 * [active] is true while AURORA is in picture-in-picture, and the tree draws only the
 * YouTube player: at that size a navigation bar, a track list and a mini-player are not
 * small versions of themselves, they are noise around a video.
 *
 * [reportPlayerBounds] is how the player tells the framework where it is, so the window
 * expands out of the player rather than appearing from nowhere.
 */
data class PictureInPictureSurface(
    val active: Boolean = false,
    val reportPlayerBounds: (Rect?) -> Unit = {},
)

val LocalPictureInPicture = staticCompositionLocalOf { PictureInPictureSurface() }
