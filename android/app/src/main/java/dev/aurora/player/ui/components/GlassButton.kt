package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.theme.Aurora

/**
 * AURORA glass-styled button.
 *
 * Primary/secondary/quiet variants. Minimum touch target 48dp.
 * Visible focus/pressed/disabled states. No domain logic.
 */
enum class GlassButtonStyle { Primary, Secondary, Quiet }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = Aurora.colors

    val containerColor = when (style) {
        GlassButtonStyle.Primary -> colors.controlPrimary
        GlassButtonStyle.Secondary -> colors.surfaceGlassSecondary
        GlassButtonStyle.Quiet -> colors.backgroundPrimary.copy(alpha = 0f)
    }
    val contentColor = when (style) {
        GlassButtonStyle.Primary -> colors.controlOnPrimary
        GlassButtonStyle.Secondary -> colors.textPrimary
        GlassButtonStyle.Quiet -> colors.accentPrimary
    }

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.48f),
            disabledContentColor = contentColor.copy(alpha = 0.48f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = Aurora.typography.label,
        )
    }
}
