package dev.aurora.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import dev.aurora.player.ui.AuroraAppRoot

import androidx.compose.runtime.CompositionLocalProvider
import dev.aurora.player.ui.haptics.AndroidHapticEngine
import dev.aurora.player.ui.haptics.LocalHapticEngine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Single-activity host for the AURORA Compose UI.
 * All navigation is handled within the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AURORA always draws its dark theme (AuroraTheme defaults darkTheme = true), so the
        // system bars are told they sit on a dark app. The no-argument form follows the
        // phone's own light/dark setting instead, which on a light-mode phone drew dark
        // status-bar icons on the navy canvas - clock, battery and signal all invisible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val hapticEngine = remember { AndroidHapticEngine(this) }

            NotificationPermissionOnFirstPlay()

            CompositionLocalProvider(
                LocalHapticEngine provides hapticEngine
            ) {
                AuroraAppRoot()
            }
        }
    }
}

/**
 * Asks for the notification permission the first time something plays.
 *
 * Not at launch. At launch the request has no context and the honest answer to "allow
 * notifications?" is "for what?" - and a permission denied in the abstract is denied for
 * good. Once a track is playing the notification is the thing that will carry its
 * controls onto the lock screen, so the question has an answer.
 *
 * Denying it costs the controls, not the playback: the foreground service still runs.
 */
@Composable
private fun NotificationPermissionOnFirstPlay() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val coordinator = (context.applicationContext as AuroraApp).container.playerCoordinator
    val state by coordinator.state.collectAsState()

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Either answer is fine; nothing here depends on the result. */ }

    var asked by remember { mutableStateOf(false) }
    val playing = state.currentTrack != null

    LaunchedEffect(playing) {
        if (!playing || asked) return@LaunchedEffect
        asked = true
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
