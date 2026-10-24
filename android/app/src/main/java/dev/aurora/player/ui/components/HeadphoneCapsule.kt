package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadphonesBattery
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.headphones.HeadphoneState
import dev.aurora.player.ui.theme.Aurora

/**
 * Headphones, and what they say about their battery.
 *
 * Three states, three marks - the plain glyph when the level is unknown, a glyph with a
 * cell in it when it is known, an alert when it is nearly out - so the warning is carried
 * by the shape and the figure as well as by the colour. A listener who cannot tell the
 * amber from the grey still reads "15%" and a different icon.
 *
 * Shows nothing at all when no headphones are connected: an empty capsule sitting in the
 * bar would be a control that never does anything.
 *
 * Silent to a screen reader on purpose. The bar around it is one merged node whose spoken
 * description is written in [MiniPlayer], and this is said there, in that sentence, rather
 * than as a second announcement for the same row.
 */
@Composable
fun HeadphoneCapsule(
    state: HeadphoneState,
    modifier: Modifier = Modifier,
) {
    val connected = state as? HeadphoneState.Connected ?: return
    val colors = Aurora.colors
    val spacing = Aurora.spacing

    val percent = connected.batteryPercent
    val low = connected.isLow
    val tint = if (low) colors.statusWarning else colors.textSecondary

    GlassSurface(
        modifier = modifier.clearAndSetSemantics {},
        level = GlassLevel.Secondary,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = spacing.space2,
                vertical = spacing.space1 + 2.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    low -> Icons.Rounded.BatteryAlert
                    percent != null -> Icons.Rounded.HeadphonesBattery
                    else -> Icons.Rounded.Headphones
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            if (percent != null) {
                Spacer(modifier = Modifier.width(spacing.space1))
                Text(
                    text = "$percent%",
                    style = Aurora.typography.caption,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * How the capsule reads out loud, folded into the row that contains it.
 *
 * Null when there is nothing to say, so the caller appends nothing rather than a clause
 * about headphones that are not there.
 */
fun headphoneAnnouncement(state: HeadphoneState): String? {
    val connected = state as? HeadphoneState.Connected ?: return null
    val percent = connected.batteryPercent ?: return "Headphones connected"
    return if (connected.isLow) {
        "Headphones connected, battery low, $percent percent"
    } else {
        "Headphones connected, battery $percent percent"
    }
}
