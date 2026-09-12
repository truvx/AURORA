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
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassButtonStyle
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
    privacyViewModel: PrivacyViewModel? = null,
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

        if (privacyViewModel != null) {
            Spacer(modifier = Modifier.height(spacing.space6))
            PrivacySection(privacyViewModel, haptics)
        }
    }
}

/**
 * Export and deletion controls for listening history. Deleting behavioural data leaves
 * favorites and playlists intact - those are the user's curation, not their history.
 */
@Composable
private fun PrivacySection(
    viewModel: PrivacyViewModel,
    haptics: dev.aurora.player.ui.haptics.HapticEngine
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val action by viewModel.action.collectAsState()
    var confirmingDelete by remember { mutableStateOf(false) }

    Text(
        text = "Privacy",
        style = typography.headline,
        color = colors.textPrimary,
        modifier = Modifier.padding(bottom = spacing.space3)
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Listening history",
                style = typography.title,
                color = colors.textPrimary
            )
            Text(
                text = "Stored only on this device. Exporting or deleting it does not " +
                    "affect your favorites or playlists.",
                style = typography.body,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = spacing.space4)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                GlassButton(
                    text = "Export",
                    enabled = action !is PrivacyAction.Working,
                    style = GlassButtonStyle.Secondary,
                    onClick = {
                        haptics.fire(HapticEvent.Tap)
                        viewModel.exportHistory()
                    }
                )
                GlassButton(
                    text = if (confirmingDelete) "Confirm delete" else "Delete",
                    enabled = action !is PrivacyAction.Working,
                    style = GlassButtonStyle.Secondary,
                    onClick = {
                        if (confirmingDelete) {
                            haptics.fire(HapticEvent.Warning)
                            viewModel.deleteAllPrivateData()
                            confirmingDelete = false
                        } else {
                            // Deleting history is irreversible, so it takes two presses.
                            haptics.fire(HapticEvent.Tap)
                            confirmingDelete = true
                        }
                    }
                )
            }

            val status: String? = when (val current = action) {
                is PrivacyAction.Exported ->
                    "Exported ${current.count} events to ${current.path}"
                is PrivacyAction.Deleted ->
                    "Listening history, resume positions, and saved queue deleted."
                is PrivacyAction.Failed -> current.reason
                else -> null
            }

            if (status != null) {
                Text(
                    text = status,
                    style = typography.label,
                    color = if (action is PrivacyAction.Failed) {
                        colors.textPrimary
                    } else {
                        colors.textSecondary
                    },
                    modifier = Modifier.padding(top = spacing.space3)
                )
            }
        }
    }
}

