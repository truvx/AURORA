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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.outlined.AccountCircle
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
import dev.aurora.player.ui.components.SegmentedControl
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
    googleAuth: dev.aurora.player.data.auth.GoogleAuthRepository? = null,
    audioQuality: dev.aurora.player.domain.audio.RequestedQuality? = null,
    onSelectQuality: ((dev.aurora.player.domain.audio.RequestedQuality) -> Unit)? = null,
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
            Spacer(modifier = Modifier.height(spacing.space5))
        }

        // ── Haptic Feedback Toggle ────────────────────────────────────────

        SectionLabel("Interaction")

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.Primary
        ) {
            var hapticEnabled by remember { mutableStateOf(haptics.isEnabled) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    /*
                     * One control, one state.
                     *
                     * The row and the switch each held their own idea of whether haptics
                     * were on: tapping the row turned the engine on or off without moving
                     * the switch next to it. toggleable makes the row the control, carries
                     * the state a screen reader announces, and leaves the switch to draw it.
                     */
                    .toggleable(
                        value = hapticEnabled,
                        role = Role.Switch,
                        onValueChange = { checked ->
                            hapticEnabled = checked
                            haptics.setEnabled(checked)
                            if (checked) haptics.fire(HapticEvent.Toggle)
                        }
                    )
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Vibration,
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.padding(end = spacing.space3)
                )
                Text(
                    text = "Haptic Feedback",
                    style = typography.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hapticEnabled,
                    // Same as the radio buttons: the row already carries the toggle, and
                    // an independently focusable switch here was a second, silent stop.
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.accentPrimary,
                        checkedTrackColor = colors.accentPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.textSecondary.copy(alpha = 0.2f),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.space5))

        // ── Audio ─────────────────────────────────────────────────────────
        
        if (playerCoordinator != null) {
            val state by playerCoordinator.state.collectAsState()
            val currentPref = state.loudnessPreference
            
            SectionLabel("Audio")
            
            // Both as segmented controls: the same sliding lozenge as Home's quality pill,
            // and a radio group to a screen reader, so "3 of 6" is still announced.
            if (audioQuality != null && onSelectQuality != null) {
                ChoiceCard(icon = Icons.Outlined.GraphicEq, title = "Preferred Audio Quality") {
                    SegmentedControl(
                        options = dev.aurora.player.domain.audio.RequestedQuality.entries,
                        selected = audioQuality,
                        label = { it.label },
                        onSelect = onSelectQuality,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(spacing.space3))
            }

            ChoiceCard(icon = Icons.Outlined.GraphicEq, title = "Volume Normalization") {
                SegmentedControl(
                    options = LoudnessPreference.values().toList(),
                    selected = currentPref,
                    label = { it.name },
                    onSelect = { playerCoordinator.dispatch(PlayerCommand.SetLoudnessPreference(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (gatewayTokenStore != null) {
            Spacer(modifier = Modifier.height(spacing.space5))
            SecretSection(
                sectionTitle = "AI",
                icon = Icons.Outlined.AutoAwesome,
                name = "AI gateway",
                setText = "Connected",
                unsetText = "Not connected",
                placeholder = "Gateway token",
                clearLabel = "Disconnect",
                saveLabel = "Connect",
                savingLabel = "Connecting…",
                store = gatewayTokenStore,
                haptics = haptics
            )
        }

        if (googleAuth != null) {
            Spacer(modifier = Modifier.height(spacing.space5))
            // Shown whether or not a client ID was built in. Hiding it left someone looking
            // for a sign-in option with no option and no reason, which reads as the feature
            // not existing rather than as one step of setup being outstanding.
            GoogleAccountSection(auth = googleAuth, haptics = haptics)
        }

        if (youTubeKeyStore != null) {
            Spacer(modifier = Modifier.height(spacing.space5))
            SecretSection(
                sectionTitle = "YouTube",
                icon = Icons.Outlined.PlayCircle,
                name = "Data API key",
                setText = "Saved",
                /*
                 * What "not set" means depends on whether this build carries a key.
                 *
                 * Saying "Not set" while YouTube plainly works would be the app lying
                 * about where its results come from, and the whole point of this row is
                 * to tell the truth about which credential is in use.
                 */
                unsetText = if (dev.aurora.player.BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                    "Using this build's key"
                } else {
                    "Not set"
                },
                placeholder = "API key",
                clearLabel = "Remove",
                saveLabel = "Save",
                savingLabel = "Saving…",
                store = youTubeKeyStore,
                haptics = haptics
            )
        }

        if (privacyViewModel != null) {
            Spacer(modifier = Modifier.height(spacing.space5))
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

    SectionLabel("Privacy")

    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Listening history",
                style = typography.body,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.space3))

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
                            haptics.fire(HapticEvent.Refused)
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
 * Signing in to Google, in place of pasting a key.
 *
 * Signing in does not hand AURORA a quota - a Google account is an identity, and the
 * requests are still spent against the Cloud project the client ID came from. What it
 * replaces is the typing.
 */
@Composable
private fun GoogleAccountSection(
    auth: dev.aurora.player.data.auth.GoogleAuthRepository,
    haptics: dev.aurora.player.ui.haptics.HapticEngine,
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors
    val state by auth.state.collectAsState()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            auth.complete(result.data)
            working = false
        }
    }

    SectionLabel("Google")

    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = colors.accentPrimary,
                    modifier = Modifier.padding(end = spacing.space3)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Account", style = typography.body, color = colors.textPrimary)
                    // Status in words, never colour alone.
                    Text(
                        text = when (val current = state) {
                            is dev.aurora.player.domain.auth.GoogleSignInState.SignedIn ->
                                current.email ?: "Signed in"
                            is dev.aurora.player.domain.auth.GoogleSignInState.Failed ->
                                current.reason.sentence()
                            dev.aurora.player.domain.auth.GoogleSignInState.NotConfigured ->
                                "Not available in this build"
                            else -> "Not signed in"
                        },
                        style = typography.label,
                        color = colors.textSecondary
                    )
                }
            }

            // No client ID was built in: the status line says so, and there is nothing to
            // press. The client ID is set in local.properties at build time (docs/SECURITY.md).
            if (state is dev.aurora.player.domain.auth.GoogleSignInState.NotConfigured) {
                return@Column
            }

            Spacer(modifier = Modifier.height(spacing.space3))

            if (state is dev.aurora.player.domain.auth.GoogleSignInState.SignedIn) {
                GlassButton(
                    text = if (working) "Signing out…" else "Sign out",
                    onClick = {
                        haptics.fire(dev.aurora.player.ui.haptics.HapticEvent.Tap)
                        working = true
                        scope.launch {
                            auth.signOut()
                            working = false
                        }
                    },
                    style = GlassButtonStyle.Secondary,
                    enabled = !working
                )
            } else {
                GlassButton(
                    text = if (working) "Opening Google…" else "Sign in with Google",
                    onClick = {
                        haptics.fire(dev.aurora.player.ui.haptics.HapticEvent.Tap)
                        val intent = auth.authorizationIntent()
                        if (intent == null) {
                            working = false
                        } else {
                            working = true
                            launcher.launch(intent)
                        }
                    },
                    enabled = !working
                )
            }
        }
    }
}

/** Each failure gets its own sentence, because each has a different thing to do about it. */
private fun dev.aurora.player.domain.auth.SignInFailure.sentence(): String = when (this) {
    dev.aurora.player.domain.auth.SignInFailure.Cancelled -> "Not signed in"
    dev.aurora.player.domain.auth.SignInFailure.Unreachable -> "Couldn't reach Google. Try again."
    dev.aurora.player.domain.auth.SignInFailure.Refused ->
        "Google refused the sign-in. Check the app is registered in your Cloud project."
    dev.aurora.player.domain.auth.SignInFailure.Unreadable -> "Google's answer couldn't be read."
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

    SectionLabel(sectionTitle)

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
                    Text(text = name, style = typography.body, color = colors.textPrimary)
                    // Status in words, not only colour.
                    Text(
                        text = if (connected) setText else unsetText,
                        style = typography.label,
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
                                    haptics.fire(HapticEvent.Commit)
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
                if (ok) haptics.fire(HapticEvent.Commit)
            }
        }
    }

    SectionLabel("You")

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
                Text(
                    text = profile.name ?: "Add your name",
                    style = typography.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
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

/** A group's name over its cards: small capitals, like the shelves on Home. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = Aurora.typography.section,
        color = Aurora.colors.textSecondary,
        modifier = Modifier.padding(start = Aurora.spacing.space1, bottom = Aurora.spacing.space2)
    )
}

/** One setting whose value is picked from a row of choices underneath its name. */
@Composable
private fun ChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    choices: @Composable () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), level = GlassLevel.Primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(bottom = Aurora.spacing.space3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Aurora.colors.accentPrimary,
                    modifier = Modifier.padding(end = Aurora.spacing.space3)
                )
                Text(text = title, style = Aurora.typography.body, color = Aurora.colors.textPrimary)
            }
            choices()
        }
    }
}
