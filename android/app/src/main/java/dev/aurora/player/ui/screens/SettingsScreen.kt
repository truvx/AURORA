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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.aurora.player.data.security.SecretStore
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
    gatewayTokenStore: SecretStore? = null,
    youTubeKeyStore: SecretStore? = null,
    profileStore: dev.aurora.player.data.discovery.ProfileStore? = null,
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
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.space4)
            // Top clears the status bar; bottom is the room the floating chrome needs, so
            // the last row scrolls clear of the glass instead of sitting under it forever.
            .padding(
                top = spacing.space6,
                bottom = dev.aurora.player.ui.navigation.LocalChromeInsets.current
                    .calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Settings",
            style = typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space6)
        )

        if (profileStore != null) {
            ProfileSection(profileStore, haptics)
            Spacer(modifier = Modifier.height(spacing.space6))
        }

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

        if (gatewayTokenStore != null) {
            Spacer(modifier = Modifier.height(spacing.space6))
            SecretSection(
                sectionTitle = "AI",
                icon = Icons.Outlined.AutoAwesome,
                name = "AI gateway",
                setText = "Connected",
                unsetText = "Not connected",
                explanation = "Paste the access token set as AURORA_GATEWAY_TOKEN on your gateway. " +
                    "It's encrypted on this device.",
                placeholder = "Gateway token",
                clearLabel = "Disconnect",
                saveLabel = "Connect",
                savingLabel = "Connecting…",
                store = gatewayTokenStore,
                haptics = haptics
            )
        }

        if (youTubeKeyStore != null) {
            Spacer(modifier = Modifier.height(spacing.space6))
            SecretSection(
                sectionTitle = "YouTube",
                icon = Icons.Outlined.PlayCircle,
                name = "Data API key",
                setText = "Saved",
                unsetText = "Not set",
                explanation = "Search, the Home feed and new releases need a YouTube Data API v3 key " +
                    "from your own Google Cloud project. It's encrypted on this device and never " +
                    "built into the app.",
                placeholder = "API key",
                clearLabel = "Remove",
                saveLabel = "Save",
                savingLabel = "Saving…",
                store = youTubeKeyStore,
                haptics = haptics
            )
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

/**
 * One secret the person supplies: the AI gateway token, or the YouTube Data API key.
 *
 * Neither is compiled into the app, so the matching feature stays off until a value is
 * entered here. It is stored encrypted with a Keystore key and never shown back: once
 * saved, the only actions are replacing it or removing it.
 */
@Composable
private fun SecretSection(
    sectionTitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    setText: String,
    unsetText: String,
    explanation: String,
    placeholder: String,
    clearLabel: String,
    saveLabel: String,
    savingLabel: String,
    store: SecretStore,
    haptics: dev.aurora.player.ui.haptics.HapticEngine
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val connected by store.isConfigured.collectAsState()
    var draft by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(
        text = sectionTitle,
        style = typography.headline,
        color = colors.textPrimary,
        modifier = Modifier.padding(bottom = spacing.space3)
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.padding(end = spacing.space3)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = name, style = typography.title, color = colors.textPrimary)
                    // Status in words, not only colour.
                    Text(
                        text = if (connected) setText else unsetText,
                        style = typography.body,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.space3))

            if (connected) {
                GlassButton(
                    text = clearLabel,
                    style = GlassButtonStyle.Secondary,
                    onClick = {
                        haptics.fire(HapticEvent.Tap)
                        store.clear()
                    }
                )
            } else {
                Text(
                    text = explanation,
                    style = typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.space3)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            failed = false
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(placeholder, color = colors.textTertiary) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = colors.surfaceGlassSecondary,
                            unfocusedContainerColor = colors.surfaceGlassSecondary,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentPrimary
                        ),
                        shape = Aurora.shapes.card
                    )
                    Spacer(modifier = Modifier.width(spacing.space3))
                    GlassButton(
                        text = if (saving) savingLabel else saveLabel,
                        enabled = draft.isNotBlank() && !saving,
                        onClick = {
                            val token = draft
                            saving = true
                            scope.launch {
                                // Keystore key generation is slow enough to drop frames, so
                                // it runs off the main thread.
                                val stored = withContext(Dispatchers.IO) {
                                    try {
                                        store.set(token)
                                        true
                                    } catch (_: java.security.GeneralSecurityException) {
                                        // The Keystore refused to create or use the key. Rare,
                                        // and not something retrying would change.
                                        false
                                    }
                                }
                                saving = false
                                failed = !stored
                                if (stored) {
                                    haptics.fire(HapticEvent.Success)
                                    draft = ""
                                }
                            }
                        }
                    )
                }
                if (failed) {
                    Text(
                        text = "This device couldn't store it securely.",
                        style = typography.label,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = spacing.space2)
                    )
                }
            }
        }
    }
}

/**
 * The listener's own name and picture, kept on this device.
 *
 * Not an account: AURORA has no sign-in, and this exists so the avatar in the header is
 * theirs rather than a stock silhouette. The photo is copied into app storage and
 * downscaled, because a Photo Picker grant does not outlive the process.
 */
@Composable
private fun ProfileSection(
    store: dev.aurora.player.data.discovery.ProfileStore,
    haptics: dev.aurora.player.ui.haptics.HapticEngine
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val profile by store.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var draftName by remember(profile.name) { mutableStateOf(profile.name.orEmpty()) }
    var photoFailed by remember { mutableStateOf(false) }

    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                // Decoding and writing a file is not main-thread work.
                val ok = withContext(Dispatchers.IO) { store.setPhoto(uri) }
                photoFailed = !ok
                if (ok) haptics.fire(HapticEvent.Success)
            }
        }
    }

    Text(
        text = "You",
        style = typography.headline,
        color = colors.textPrimary,
        modifier = Modifier.padding(bottom = spacing.space3)
    )

    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile = profile, onClick = {
                    pickPhoto.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly
                        )
                    )
                }, size = 56.dp)
                Spacer(modifier = Modifier.width(spacing.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name ?: "Add your name",
                        style = typography.title,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Shown on your avatar. Stored only on this device.",
                        style = typography.body,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.space3))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = {
                        draftName = it
                        store.setName(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Your name", color = colors.textTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = colors.surfaceGlassSecondary,
                        unfocusedContainerColor = colors.surfaceGlassSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentPrimary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
                )
            }

            Spacer(modifier = Modifier.height(spacing.space3))

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                GlassButton(
                    text = if (profile.photoPath == null) "Choose photo" else "Change photo",
                    style = GlassButtonStyle.Secondary,
                    onClick = {
                        haptics.fire(HapticEvent.Tap)
                        pickPhoto.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                if (profile.photoPath != null) {
                    GlassButton(
                        text = "Remove",
                        style = GlassButtonStyle.Secondary,
                        onClick = {
                            haptics.fire(HapticEvent.Tap)
                            store.clearPhoto()
                        }
                    )
                }
            }

            if (photoFailed) {
                Text(
                    text = "That image could not be read.",
                    style = typography.label,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = spacing.space2)
                )
            }
        }
    }
}
