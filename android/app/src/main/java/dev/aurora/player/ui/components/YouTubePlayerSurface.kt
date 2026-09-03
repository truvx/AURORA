package dev.aurora.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YouTubePlayerSurface(
    modifier: Modifier = Modifier
) {
    val adapter = LocalYouTubePlayerAdapter.current
    val webView = adapter.webView
    
    if (webView != null) {
        AndroidView(
            factory = { 
                val parent = webView.parent as? android.view.ViewGroup
                parent?.removeView(webView)
                webView
            },
            modifier = modifier
        )
    }
}
