package dev.aurora.player.ui.components

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/** The device's music volume as 0..1, and a way to set it. */
class MusicVolume(val fraction: Float, val set: (Float) -> Unit)

/**
 * The system music volume, kept current while the hardware keys change it.
 *
 * The same volume the keys move, not a gain inside AURORA: a slider that disagreed with the
 * keys would be two volumes, and YouTube's WebView only answers to this one.
 */
@Composable
fun rememberMusicVolume(): MusicVolume {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    DisposableEffect(audio) {
        // Volume changes are written to system settings, which is the one change feed for
        // them that is a public API.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    return MusicVolume(level.toFloat() / max) { fraction ->
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        level = target
    }
}

/** Where the sound is going: a headset's own name, or null for the phone's speaker. */
@Composable
fun rememberOutputDeviceName(): String? {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var name by remember { mutableStateOf(externalOutputName(audio)) }

    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                name = externalOutputName(audio)
            }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                name = externalOutputName(audio)
            }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return name
}

private fun externalOutputName(audio: AudioManager): String? {
    val external = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type in external }
        ?.productName?.toString()?.takeIf { it.isNotBlank() }
}

/**
 * Opens the system's own output picker, where it has one (Android 14+).
 *
 * @return false where there is no picker, so the caller can leave the chip inert.
 */
fun showSystemOutputSwitcher(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()
