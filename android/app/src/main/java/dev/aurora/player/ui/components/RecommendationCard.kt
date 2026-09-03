package dev.aurora.player.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aurora.player.domain.recommendations.RecommendationCandidate
import dev.aurora.player.ui.theme.Aurora

@Composable
fun RecommendationCard(
    candidate: RecommendationCandidate,
    onPlayClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    GlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(spacing.space3)) {
            Text(
                text = candidate.item.title,
                style = typography.body,
                color = colors.textPrimary,
                maxLines = 1
            )
            if (candidate.item.artist != null) {
                Spacer(modifier = Modifier.height(spacing.space1))
                Text(
                    text = candidate.item.artist,
                    style = typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
            if (candidate.reason != null) {
                Spacer(modifier = Modifier.height(spacing.space2))
                Text(
                    text = "Why: ${candidate.reason}",
                    style = typography.caption,
                    color = colors.accentPrimary
                )
            }
            Spacer(modifier = Modifier.height(spacing.space3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                GlassButton(text = "Queue", onClick = onQueueClick)
                Spacer(modifier = Modifier.width(spacing.space2))
                GlassButton(text = "Play", onClick = onPlayClick)
            }
        }
    }
}
