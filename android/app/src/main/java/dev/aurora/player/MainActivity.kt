package dev.aurora.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.aurora.player.ui.AuroraAppRoot

import androidx.compose.runtime.CompositionLocalProvider
import dev.aurora.player.ui.haptics.AndroidHapticEngine
import dev.aurora.player.ui.haptics.LocalHapticEngine
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember

/**
 * Single-activity host for the AURORA Compose UI.
 * All navigation is handled within the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val view = LocalView.current
            val hapticEngine = remember { AndroidHapticEngine(this, view) }
            
            CompositionLocalProvider(
                LocalHapticEngine provides hapticEngine
            ) {
                AuroraAppRoot()
            }
        }
    }
}
