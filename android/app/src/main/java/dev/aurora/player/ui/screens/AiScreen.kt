package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.RecommendationCard
import androidx.compose.material.icons.rounded.ArrowUpward
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.app.PlayerCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    viewModel: AiViewModel,
    playerCoordinator: PlayerCoordinator,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    val uiState by viewModel.uiState.collectAsState()
    var textState by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
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
            text = "AURORA AI",
            style = typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space6)
        )
        
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.Primary
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. late night driving music...", color = colors.textTertiary) },
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
                    Spacer(modifier = Modifier.width(spacing.space3))
                    // Solid, round and small: the send button in the references is the one
                    // filled control on an otherwise translucent bar.
                    dev.aurora.player.ui.components.PrimaryCircleButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = "Ask AURORA",
                        size = 48.dp,
                        onClick = {
                            if (textState.text.isNotBlank()) {
                                viewModel.submitQuery(textState.text)
                                textState = TextFieldValue("")
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.space6))

        when (val state = uiState) {
            is AiUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentPrimary)
                }
            }
            is AiUiState.Error -> {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    level = GlassLevel.Secondary
                ) {
                    Column {
                        // The message is the whole explanation; prefixing "Error:" and colouring
                        // it red added alarm without adding information.
                        Text(text = state.message, color = colors.textPrimary, style = typography.body)
                        if (state.opensSettings) {
                            Spacer(modifier = Modifier.height(spacing.space3))
                            GlassButton(
                                text = "Open Settings",
                                style = dev.aurora.player.ui.components.GlassButtonStyle.Secondary,
                                onClick = onOpenSettings
                            )
                        }
                    }
                }
            }
            is AiUiState.Success -> {
                if (state.message.isNotBlank()) {
                    Text(text = state.message, color = colors.textPrimary, style = typography.body)
                    Spacer(modifier = Modifier.height(spacing.space3))
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.space3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.candidates) { candidate ->
                        RecommendationCard(
                            candidate = candidate,
                            onPlayClick = {
                                playerCoordinator.dispatch(PlayerCommand.Load(candidate.item))
                            },
                            onQueueClick = {
                                playerCoordinator.dispatch(PlayerCommand.AddToQueue(candidate.item))
                            }
                        )
                    }
                }
            }
            is AiUiState.Idle -> {
                // Premium Empty State for AI (Floating)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.space8),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.accentPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = spacing.space4)
                    )
                    Text(
                        text = "Music intelligence",
                        style = typography.title,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(spacing.space3))
                    Text(
                        text = "Ask AURORA to curate a playlist, find a specific vibe, or discover new music based on your taste.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.space4)
                    )
                }
            }
        }
    }
}
