package dev.aurora.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

/**
 * A few mutually exclusive choices, side by side on one piece of glass.
 *
 * A radio group rather than a row of buttons, which is what it is: `selectableGroup` and
 * `Role.RadioButton` mean a screen reader announces "2 of 3" and moves between them as a
 * set, instead of reading three unrelated controls that happen to be adjacent.
 *
 * The selected segment is a filled pill that slides. Sliding rather than cross-fading is
 * the point: the fill is one object moving between positions, so the eye follows it and
 * learns the control has a position, and an interrupted tap re-targets from wherever the
 * fill has got to rather than jumping.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    if (options.isEmpty()) return
    val colors = Aurora.colors
    val haptics = LocalHapticEngine.current
    val index = options.indexOf(selected).coerceAtLeast(0)

    GlassSurface(
        modifier = modifier.height(SEGMENT_HEIGHT),
        level = GlassLevel.Elevated,
        shape = RoundedCornerShape(percent = 50),
        backdrop = backdrop,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // One lozenge, refracting the page behind the bar. See SelectionCapsule.
            SelectionCapsule(
                index = index,
                count = options.size,
                backdrop = backdrop,
                inset = SEGMENT_INSET,
            )

            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    if (!isSelected) haptics.fire(HapticEvent.Selection)
                                    onSelect(option)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label(option),
                            style = Aurora.typography.label,
                            // Weight as well as colour, so the selection survives a screen
                            // read as greyscale.
                            fontWeight = if (isSelected) {
                                androidx.compose.ui.text.font.FontWeight.Bold
                            } else {
                                androidx.compose.ui.text.font.FontWeight.Normal
                            },
                            /*
                             * Accent on the selected word.
                             *
                             * The fill alone was carrying the selection, and on a dark
                             * track a slightly lighter capsule is a quiet signal. Colour
                             * and weight together mean the choice is legible at a glance
                             * and still legible without colour, which is the rule.
                             */
                            color = if (isSelected) colors.accentPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 44.dp
private val SEGMENT_INSET = 3.dp

/** Fully rounded ends, so the fill reads as a capsule at whatever width a segment is. */
private val CapsuleShape = RoundedCornerShape(percent = 50)

/** One device pixel at most densities: an edge, not an outline. */
private val HAIRLINE = 1.dp
