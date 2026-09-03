package dev.aurora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aurora.player.ui.theme.Aurora

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment

import androidx.compose.runtime.collectAsState
import dev.aurora.player.domain.audio.LoudnessPreference
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.app.PlayerCoordinator

/** Settings screen — foundation shell. Content added in Phase 4+. */
@Composable
fun SettingsScreen(
    playerCoordinator: PlayerCoordinator? = null,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.space4, vertical = spacing.space6),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Settings",
            style = typography.headline,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(spacing.space2))
        Text(
            text = "Audio quality, normalization, and preferences",
            style = typography.body,
            color = colors.textSecondary,
        )
        
        if (playerCoordinator != null) {
            val state by playerCoordinator.state.collectAsState()
            
            Spacer(modifier = Modifier.height(spacing.space6))
            Text(
                text = "Volume Normalization",
                style = typography.title,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(spacing.space2))
            
            // Wait, we need to know the current preference. Since it's in Coordinator, we can expose it via PlayerState or observe it.
            val currentPref = state.loudnessPreference
            
            Column {
                LoudnessPreference.values().forEach { pref ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerCoordinator.dispatch(PlayerCommand.SetLoudnessPreference(pref))
                            }
                            .padding(vertical = spacing.space2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentPref == pref,
                            onClick = { playerCoordinator.dispatch(PlayerCommand.SetLoudnessPreference(pref)) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colors.accentPrimary,
                                unselectedColor = colors.textSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(spacing.space2))
                        Text(
                            text = pref.name,
                            style = typography.body,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
