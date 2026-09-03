package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA glass icon button.
 *
 * Compact control with 48dp minimum touch target (per accessibility spec),
 * 24dp icon glyph, accessible content description, and themed colors.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = Aurora.colors
    val tint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.48f)
        selected -> colors.accentPrimary
        else -> colors.controlPrimary
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = contentDesc },
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = colors.textSecondary.copy(alpha = 0.48f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Handled by parent semantics
            modifier = Modifier.size(24.dp),
        )
    }
}
