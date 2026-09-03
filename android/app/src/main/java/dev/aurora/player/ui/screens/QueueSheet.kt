package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.components.ArtworkSurface
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

@Composable
fun QueueSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinator = LocalPlayerCoordinator.current
    val haptics = LocalHapticEngine.current
    val state by coordinator.state.collectAsState()
    
    val queue = state.queue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Aurora.colors.backgroundPrimary)
            .padding(Aurora.spacing.space4)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next in Queue", 
                style = Aurora.typography.title,
                color = Aurora.colors.textPrimary
            )
            GlassIconButton(
                onClick = {
                    haptics.fire(HapticEvent.Tap)
                    onClose()
                },
                icon = Icons.Rounded.Close,
                contentDesc = "Close Queue",
            )
        }

        Spacer(modifier = Modifier.height(Aurora.spacing.space4))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue.items, key = { _, item -> item.id }) { index, item ->
                QueueItemRow(
                    item = item,
                    isCurrent = index == queue.currentIndex,
                    onMoveUp = if (index > 0) {
                        {
                            haptics.fire(HapticEvent.QueueReorder)
                            coordinator.dispatch(PlayerCommand.MoveInQueue(index, index - 1))
                        }
                    } else null,
                    onMoveDown = if (index < queue.items.size - 1) {
                        {
                            haptics.fire(HapticEvent.QueueReorder)
                            coordinator.dispatch(PlayerCommand.MoveInQueue(index, index + 1))
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun QueueItemRow(
    item: MediaItem,
    isCurrent: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticEngine.current
    
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Aurora.spacing.space1),
        level = if (isCurrent) GlassLevel.Elevated else GlassLevel.Primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Aurora.spacing.space3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkSurface(
                altText = "${item.title} artwork",
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(Aurora.spacing.space4))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = Aurora.typography.body,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) Aurora.colors.accentPrimary else Aurora.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist ?: "Unknown Artist",
                    style = Aurora.typography.label,
                    color = Aurora.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column {
                GlassIconButton(
                    onClick = { onMoveUp?.invoke() },
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDesc = "Move Up",
                    modifier = Modifier.size(28.dp),
                    enabled = onMoveUp != null
                )
                GlassIconButton(
                    onClick = { onMoveDown?.invoke() },
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDesc = "Move Down",
                    modifier = Modifier.size(28.dp),
                    enabled = onMoveDown != null
                )
            }
        }
    }
}
