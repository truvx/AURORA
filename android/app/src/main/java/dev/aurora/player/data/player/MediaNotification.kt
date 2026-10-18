package dev.aurora.player.data.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.aurora.player.MainActivity
import dev.aurora.player.R

/**
 * Builds the media playback notification and the notification channel it lives in.
 *
 * Android 8.0 (API 26) made notification channels mandatory: every notification must belong
 * to one, and the channel must be created before any notification is posted. Below API 26
 * nothing changes - this class simply does not register a channel there.
 *
 * The channel carries the media-playback foreground service type the manifest declares, so
 * the system knows what the notification keeps alive. It is created once at service start
 * rather than on every track change, because recreating it would dismiss the existing
 * notification and flash the shade.
 */
object MediaNotification {

    /** Channel id - stable so Android groups notifications under it. */
    private const val CHANNEL_ID = "dev.aurora.player.media_playback"

    /** Human-visible channel name for the listener to find in settings. */
    private const val CHANNEL_NAME = "Media playback"

    /** Notification id - stable so each new build replaces the last rather than stacking. */
    const val NOTIFICATION_ID = 1

    /**
     * Creates the media playback channel if this Android version requires one.
     * Safe to call repeatedly; creating an existing channel is a no-op.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls, lock screen and media session"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the ongoing media notification, with a tap returning to AURORA rather than
     * launching a second copy.
     */
    fun build(context: Context): Notification {
        val app = context.applicationContext
        val intent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AURORA")
            .setContentText("Media playback")
            .setContentIntent(intent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }
}
