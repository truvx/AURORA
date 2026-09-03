package dev.aurora.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.aurora.player.ui.AuroraAppRoot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.PlayerViewModel
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
            val playerViewModel: PlayerViewModel = viewModel()
            val view = LocalView.current
            val hapticEngine = remember { AndroidHapticEngine(this, view) }
            
            CompositionLocalProvider(
                LocalPlayerCoordinator provides playerViewModel.coordinator,
                LocalHapticEngine provides hapticEngine
            ) {
                AuroraAppRoot()
            }
        }
    }
}
