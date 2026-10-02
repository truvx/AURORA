package dev.aurora.player.ui.components

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Wherever the one official YouTube player is currently supposed to be.
 *
 * There is a single WebView - the player, with YouTube's own controls and branding - and
 * three places it can appear: docked beside the floating bar, filling the full player, and
 * alone in the picture-in-picture window. Only one of those is ever composed at a time, and
 * this moves the player to whichever it is.
 *
 * The move happens in `update`, not `factory`. A factory runs once per host; when the
 * picture-in-picture branch replaced the whole tree, its host never ran one, so the player
 * stayed parented to a view that had just been disposed - a black window, and playback
 * stopping with it. Re-parenting on every update means the question "who owns the player
 * now" is answered by whoever is on screen, every time it is asked.
 *
 * The container is what Compose owns; the player is only ever a guest inside it.
 */
@Composable
fun YouTubePlayerSurface(
    modifier: Modifier = Modifier
) {
    val adapter = LocalYouTubePlayerAdapter.current
    val webView = adapter.webView
    // Where this sits on screen, handed to the framework so the picture-in-picture window
    // expands out of the player rather than appearing from nowhere.
    val pictureInPicture = dev.aurora.player.ui.LocalPictureInPicture.current

    if (webView != null) {
        AndroidView(
            factory = { context -> FrameLayout(context) },
            update = { container ->
                if (webView.parent !== container) {
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    container.removeAllViews()
                    container.addView(
                        webView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    )
                }
            },
            modifier = modifier.onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                pictureInPicture.reportPlayerBounds(
                    android.graphics.Rect(
                        bounds.left.toInt(), bounds.top.toInt(),
                        bounds.right.toInt(), bounds.bottom.toInt()
                    )
                )
            }
        )
    }
}
