package dev.aurora.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * The primary action on a playlist or album: a solid pill, not glass.
 *
 * Glass belongs to chrome that floats over content. The one action a screen exists for
 * should be the most solid thing on it - which in this palette also makes it the highest
 * contrast pair available (near-black on white, about 18:1).
 */
@Composable
fun PrimaryPlayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "play press"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f - 0.04f * press
                scaleY = 1f - 0.04f * press
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.controlPrimary)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.fire(HapticEvent.Tap)
                onClick()
            }
            .defaultMinSize(minWidth = 140.dp, minHeight = 52.dp)
            .padding(horizontal = Aurora.spacing.space6),
        horizontalArrangement = Arrangement.spacedBy(Aurora.spacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = colors.controlOnPrimary,
            modifier = Modifier.size(22.dp)
        )
        Text(text = text, style = Aurora.typography.title, color = colors.controlOnPrimary)
    }
}

/**
 * The same solid action as a circle, for the player's play/pause.
 *
 * In the references the transport's centre button is the one filled, opaque control on an
 * otherwise translucent screen - it is where the eye goes and where the thumb lands.
 */
@Composable
fun PrimaryCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "primary circle press"
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f - 0.05f * press
                scaleY = 1f - 0.05f * press
            }
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(colors.controlPrimary)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.fire(HapticEvent.Tap)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        /*
         * The icon changes, the button does not.
         *
         * Play and pause swap on this control constantly, and at this size a hard cut is
         * the most visible frame on the screen. Crossfading the whole button instead makes
         * the white circle flicker, so only what actually changed is animated.
         */
        androidx.compose.animation.AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.scaleIn(
                        androidx.compose.animation.core.tween(120),
                        initialScale = 0.7f
                    ))
                    .togetherWith(
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
                            androidx.compose.animation.scaleOut(
                                androidx.compose.animation.core.tween(120),
                                targetScale = 0.7f
                            )
                    )
            },
            label = "primary icon"
        ) { current ->
            Icon(
                imageVector = current,
                contentDescription = contentDescription,
                tint = colors.controlOnPrimary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}
