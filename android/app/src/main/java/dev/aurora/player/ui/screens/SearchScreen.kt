package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.theme.Aurora

/** 
 * Search screen — foundation shell. 
 * Shows a premium empty state before a search is performed.
 */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = spacing.space4, 
                vertical = spacing.space6 + 48.dp // Padding for Top/Bottom bars
            ),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Search",
            style = typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space6)
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = spacing.space4)
            )
            Text(
                text = "Find your music",
                style = typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.space3))
            Text(
                text = "Search across your local library and YouTube for tracks, artists, and playlists.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }
    }
}
