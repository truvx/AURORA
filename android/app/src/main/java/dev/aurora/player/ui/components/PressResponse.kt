package dev.aurora.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * How far into a press a control is, from 0 to 1.
 *
 * One spring, in one place. The same critically damped, high-stiffness spring was written
 * out five times across three files - identical every time, which is lucky rather than
 * designed, and the kind of thing that drifts the moment somebody tunes one of them.
 *
 * Critically damped on purpose: a press is not a throw. Nothing here carries momentum, so
 * there is nothing for an overshoot to express, and a button that bounces back past its
 * resting size reads as a toy.
 */
@Composable
fun rememberPressProgress(interactionSource: InteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "press",
    )
}
