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
import androidx.compose.runtime.remember

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
            
            CompositionLocalProvider(
                LocalHapticEngine provides hapticEngine
            ) {
                AuroraAppRoot()
            }
        }
    }
}
