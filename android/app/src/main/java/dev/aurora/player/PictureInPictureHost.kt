package dev.aurora.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.domain.player.PictureInPicturePolicy
import dev.aurora.player.domain.player.PlaybackStatus
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.domain.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The official YouTube player, following the listener out of the app in a window.
 *
 * Everything Android-shaped about picture-in-picture lives here so the Activity stays a
 * host and [PictureInPicturePolicy] stays testable. The division: the policy decides
 * whether a window is allowed and what its buttons mean, this talks to the framework.
 *
 * What it will not do is keep audio alive without a window. The window closing, or the
 * screen going off, stops the track - see [onStopped]. That is the whole reason this is
 * permitted where a background player is not: the player is never not there.
 */
class PictureInPictureHost(
    private val activity: ComponentActivity,
    private val coordinator: PlayerCoordinator,
) {

    private val _isInPictureInPicture = MutableStateFlow(false)

    /** Collected by the UI, which draws only the player while this is true. */
    val isInPictureInPicture: StateFlow<Boolean> = _isInPictureInPicture.asStateFlow()

    /** Where the player sits on screen, so the window animates out of it rather than in from nowhere. */
    private var sourceBounds: android.graphics.Rect? = null

    private val supported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = PictureInPicturePolicy.Action.from(intent?.action) ?: return
            coordinator.dispatch(PictureInPicturePolicy.commandFor(action, coordinator.state.value))
        }
    }

    fun onCreate() {
        if (!supported) return
        val filter = IntentFilter().apply {
            PictureInPicturePolicy.Action.entries.forEach { addAction(it.id) }
        }
        // Not exported: these are this app's own buttons, and nothing else may press them.
        ContextCompat.registerReceiver(
            activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun onDestroy() {
        if (!supported) return
        runCatching { activity.unregisterReceiver(receiver) }
    }

    /** Called as the player's on-screen rectangle changes, so the window knows where to come from. */
    fun setSourceBounds(bounds: android.graphics.Rect?) {
        sourceBounds = bounds
    }

    /**
     * Keeps the framework's copy of the parameters current.
     *
     * On API 31 and up this is also what arms auto-enter: the system puts the app into the
     * window itself when the listener leaves, with no frame of the home screen showing
     * first. Below that the Activity asks on `onUserLeaveHint`, which is later and visibly
     * so, but it is the only hook there is.
     */
    fun onStateChanged(state: PlayerState) {
        if (!supported) return
        val allowed = PictureInPicturePolicy.shouldEnter(state)
        runCatching { activity.setPictureInPictureParams(params(state, autoEnter = allowed)) }
    }

    /** API 30 and below: the last call before the app goes away. */
    fun onUserLeaving() {
        if (!supported || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val state = coordinator.state.value
        if (!PictureInPicturePolicy.shouldEnter(state)) return
        runCatching { activity.enterPictureInPictureMode(params(state, autoEnter = false)) }
    }

    fun onPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        _isInPictureInPicture.value = inPictureInPicture
    }

    /**
     * The app is no longer visible at all.
     *
     * Reached when the window is dismissed and when the screen turns off, and in both
     * cases a YouTube track must stop: audio with no player is the one thing the
     * capability matrix rules out. A local file is left alone - it has a media session and
     * is allowed to keep going.
     */
    fun onStopped() {
        val state = coordinator.state.value
        if (PictureInPicturePolicy.shouldPauseOnLeaving(state, _isInPictureInPicture.value)) {
            coordinator.dispatch(PlayerCommand.Pause)
        }
    }

    private fun params(state: PlayerState, autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            // The shape of a YouTube video. Anything else letterboxes the player inside its
            // own window.
            .setAspectRatio(Rational(16, 9))
            .setActions(actions(state))
        sourceBounds?.let { builder.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun actions(state: PlayerState): List<RemoteAction> {
        val playing = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering
        return listOf(
            remoteAction(
                PictureInPicturePolicy.Action.Previous,
                android.R.drawable.ic_media_previous,
                "Previous"
            ),
            remoteAction(
                PictureInPicturePolicy.Action.PlayPause,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play"
            ),
            remoteAction(
                PictureInPicturePolicy.Action.Next,
                android.R.drawable.ic_media_next,
                "Next"
            ),
        )
    }

    private fun remoteAction(
        action: PictureInPicturePolicy.Action,
        iconRes: Int,
        label: String,
    ): RemoteAction = RemoteAction(
        Icon.createWithResource(activity, iconRes),
        label,
        label,
        PendingIntent.getBroadcast(
            activity,
            action.ordinal,
            Intent(action.id).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    )
}
