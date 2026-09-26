package dev.aurora.player.data.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Holds a controller open so the media session service actually runs.
 *
 * The service was declared in the manifest, with the media-playback foreground type and the
 * permission to match, and nothing ever connected to it - so it never started. That is the
 * whole reason local playback died when the app went to the background and why there was no
 * notification and no lock-screen controls: a `MediaSessionService` is bound into existence
 * by a controller, and there was no controller.
 *
 * The connection is held for the life of the process rather than per screen. A controller
 * that came and went with an Activity would take the service down on every rotation.
 *
 * It controls nothing itself. AURORA's own UI talks to the PlayerCoordinator as it always
 * has; this exists so the session is alive for the notification, the lock screen, headset
 * buttons and Android Auto to talk to.
 */
class MediaSessionConnection(private val context: Context) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(
            context,
            ComponentName(context, AuroraMediaSessionService::class.java)
        )
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                // Failure here is not fatal: in-app playback is unaffected, and the
                // alternative - letting the exception escape onto the main thread - would
                // take the app down over a notification.
                controller = runCatching { pending.get() }.getOrNull()
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        controller?.release()
        controller = null
        future?.let(MediaController::releaseFuture)
        future = null
    }
}
