package dev.aurora.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.RecommendationCard
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.app.PlayerCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    viewModel: AiViewModel,
    playerCoordinator: PlayerCoordinator,
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
            .padding(horizontal = spacing.space4, vertical = spacing.space6),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "AI",
            style = typography.headline,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(spacing.space2))
        Text(
            text = "Music discovery powered by intelligence",
            style = typography.body,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(spacing.space4))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("E.g., find some late night driving music") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentPrimary,
                    unfocusedBorderColor = colors.dividerSubtle
                )
            )
            Spacer(modifier = Modifier.width(spacing.space2))
            GlassButton(
                text = "Send",
                onClick = { 
                    viewModel.submitQuery(textState.text) 
                    textState = TextFieldValue("")
                }
            )
        }

        Spacer(modifier = Modifier.height(spacing.space4))

        when (val state = uiState) {
            is AiUiState.Loading -> {
                CircularProgressIndicator(color = colors.accentPrimary)
            }
            is AiUiState.Error -> {
                Text(text = "Error: ${state.message}", color = colors.statusError, style = typography.body)
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
                // Initial state
            }
        }
    }
}
