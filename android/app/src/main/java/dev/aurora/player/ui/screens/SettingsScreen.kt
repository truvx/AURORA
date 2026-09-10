package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.domain.audio.LoudnessPreference
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/** 
 * AURORA Settings screen.
 * 
 * Provides access to audio quality, normalization, haptic toggle,
 * and other core preferences.
 */
@Composable
fun SettingsScreen(
    playerCoordinator: PlayerCoordinator? = null,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current

    Column(
        modifier = modifier
            .fillMaxSize()
            // .background(colors.backgroundPrimary) // Removed to let AmbientArtworkLayer show through
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = spacing.space4, 
                vertical = spacing.space6 + 48.dp // Padding for Top/Bottom bars
            ),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Settings",
            style = typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space6)
        )

        // ── Haptic Feedback Toggle ────────────────────────────────────────

        Text(
            text = "Interaction",
            style = typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space3)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.Primary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !haptics.isEnabled
                        haptics.setEnabled(newState)
                        if (newState) haptics.fire(HapticEvent.Toggle)
                    }
                    .padding(vertical = spacing.space2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.padding(end = spacing.space3)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptic Feedback",
                        style = typography.title,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Feel controls respond to your touch.",
                        style = typography.body,
                        color = colors.textSecondary,
                    )
                }
                var hapticEnabled by remember { mutableStateOf(haptics.isEnabled) }
                Switch(
                    checked = hapticEnabled,
                    onCheckedChange = { checked ->
                        hapticEnabled = checked
                        haptics.setEnabled(checked)
                        if (checked) haptics.fire(HapticEvent.Toggle)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.accentPrimary,
                        checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.textSecondary.copy(alpha = 0.2f),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.space6))

        // ── Audio ─────────────────────────────────────────────────────────
        
        if (playerCoordinator != null) {
            val state by playerCoordinator.state.collectAsState()
            val currentPref = state.loudnessPreference
            
            Text(
                text = "Audio",
                style = typography.headline,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = spacing.space3)
            )
            
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                level = GlassLevel.Primary
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space4),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = colors.accentPrimary,
                            modifier = Modifier.padding(end = spacing.space3)
                        )
                        Column {
                            Text(
                                text = "Volume Normalization",
                                style = typography.title,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Adjusts playback volume to a consistent level.",
                                style = typography.body,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.space1)
                    ) {
                        LoudnessPreference.values().forEach { pref ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptics.fire(HapticEvent.Selection)
                                        playerCoordinator.dispatch(PlayerCommand.SetLoudnessPreference(pref))
                                    }
                                    .padding(vertical = spacing.space2),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentPref == pref,
                                    onClick = {
                                        haptics.fire(HapticEvent.Selection)
                                        playerCoordinator.dispatch(PlayerCommand.SetLoudnessPreference(pref))
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colors.accentPrimary,
                                        unselectedColor = colors.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(spacing.space3))
                                Column {
                                    Text(
                                        text = pref.name,
                                        style = typography.title,
                                        color = if (currentPref == pref) colors.textPrimary else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

