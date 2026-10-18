package dev.aurora.player.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Device capability snapshot, computed once at composition root.
 *
 * A low-end device is one Android itself treats as RAM-constrained
 * (`isLowRamDevice`) or with a small memory class. On those devices the glass
 * backdrop effects (blur, vibrancy, refraction) are skipped so the UI stays at
 * 60 fps instead of throttling the GPU. The glass colour itself is kept - the
 * theme is unchanged, only the per-frame backdrop sampling is dropped.
 */
@Immutable
data class DeviceCapabilities(
    val isLowEnd: Boolean,
    val memoryClassMb: Int,
) {
    companion object {
        fun from(context: Context): DeviceCapabilities {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClassMb = am.memoryClass
            val isLowEnd = am.isLowRamDevice || memoryClassMb <= 256
            return DeviceCapabilities(isLowEnd = isLowEnd, memoryClassMb = memoryClassMb)
        }
    }
}

val LocalDeviceCapabilities = staticCompositionLocalOf<DeviceCapabilities> {
    DeviceCapabilities(isLowEnd = false, memoryClassMb = 512)
}
