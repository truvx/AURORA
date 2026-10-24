package dev.aurora.player.data.headphones

import android.Manifest
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.aurora.player.domain.headphones.AppleAccessoryEvent
import dev.aurora.player.domain.headphones.HeadphoneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Whether headphones are the output, and what they say about their battery.
 *
 * Two independent sources, because the two questions are not the same question and do not
 * cost the same.
 *
 * *Connected* comes from [AudioManager]'s output devices. It needs no permission, it is
 * true of a wired pair as well as a wireless one, and it answers what the listener can
 * actually see - are these headphones playing this. Asking the Bluetooth profile instead
 * would need a runtime permission to learn something the audio system already knows.
 *
 * *Battery* comes from the headset's own vendor-specific AT message, which is the only
 * public route to a level at all (see [AppleAccessoryEvent]). Receiving it does need
 * `BLUETOOTH_CONNECT`, so it is read only when that has already been granted and is never
 * asked for here: a battery figure is a nicety, and a permission dialog raised by opening
 * the app to play music would be asking for something in exchange for nothing yet. Without
 * it, and for any headset that simply never sends the message, the state stays
 * [HeadphoneState.Connected] with a null level and the capsule shows the icon alone.
 *
 * Nothing here reflects, and nothing reads a hidden or `@SystemApi` member.
 */
class HeadphoneMonitor(
    private val context: Context,
    scope: CoroutineScope,
) {

    val state: StateFlow<HeadphoneState> = callbackFlow {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        /*
         * Battery survives the headset going quiet but not it going away.
         *
         * A headset sends its level on connecting and again when it changes, so between
         * those the last reading is the current one. On disconnect it is dropped: the next
         * pair of headphones is not the last pair's battery.
         */
        var batteryPercent: Int? = null

        fun headphonesConnected(): Boolean =
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.any { it.type in HEADPHONE_TYPES } == true

        fun publish() {
            val connected = headphonesConnected()
            if (!connected) batteryPercent = null
            trySend(
                if (connected) HeadphoneState.Connected(batteryPercent) else HeadphoneState.Disconnected
            )
        }

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = publish()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = publish()
        }
        audioManager?.registerAudioDeviceCallback(deviceCallback, null)

        val batteryReceiver = if (canReadVendorEvents()) {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) return
                    val command =
                        intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                    if (command != AppleAccessoryEvent.COMMAND) return
                    val type = intent.getIntExtra(
                        BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE,
                        -1
                    )
                    // Only the headset setting a value; a read or a test carries no level.
                    if (type != BluetoothHeadset.AT_CMD_TYPE_SET) return

                    // The compatibility getter, which picks the type-checked overload on
                    // Android 13 and the older one below it.
                    val args = androidx.core.content.IntentCompat.getSerializableExtra(
                        intent,
                        BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS,
                        Array<Any>::class.java
                    ) ?: return
                    val percent = AppleAccessoryEvent.batteryPercent(args.map { it.toString() })
                        ?: return
                    batteryPercent = percent
                    publish()
                }
            }.also { receiver ->
                val filter = IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
                // The broadcast is addressed by the company that defined the command, so a
                // receiver that does not name that company is never sent it.
                filter.addCategory(
                    BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY +
                        "." + android.bluetooth.BluetoothAssignedNumbers.APPLE
                )
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    // Only the system sends this; nothing else may reach the receiver.
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
        } else {
            null
        }

        publish()

        awaitClose {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
            batteryReceiver?.let { context.unregisterReceiver(it) }
        }
    }
        .distinctUntilChanged()
        /*
         * Kept briefly after the last collector leaves, so moving between screens does not
         * tear down the receiver and lose a battery reading that the headset will not
         * repeat until the level next changes.
         */
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), HeadphoneState.Disconnected)

    private fun canReadVendorEvents(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        /**
         * Output devices that are headphones.
         *
         * A speaker is not, and neither is a car or a television - the capsule is about
         * something on the listener's head, which is the only thing a headphone glyph and
         * a battery level make sense next to.
         *
         * The BLE type is only defined from Android 12; on anything older the constant is
         * inlined and simply never matches a device the system reports.
         */
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
