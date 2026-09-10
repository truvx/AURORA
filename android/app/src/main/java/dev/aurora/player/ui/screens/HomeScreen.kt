package dev.aurora.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
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
 * AURORA Home screen.
 * 
 * Presents a curated vertical narrative. Currently shows a premium empty
 * state as the user has no active history or recommendations yet.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            // .background(colors.backgroundPrimary) // Removed to let AmbientArtworkLayer show through
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = spacing.space4, 
                vertical = spacing.space6 + 48.dp // Padding for Top/Bottom bars
            ),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Listen Now",
            style = typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space6)
        )
        
        // Premium Empty State (Floating over ambient environment)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.LibraryMusic,
                contentDescription = null,
                tint = colors.accentPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = spacing.space4)
            )
            Text(
                text = "Your listening space is waiting",
                style = typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.space3))
            Text(
                text = "Discover music through AI or allow library access to start building your AURORA experience.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = spacing.space4)
            )
        }
        
        Spacer(modifier = Modifier.height(spacing.space6))
        
        // Placeholder for future sections, also floating
        Text(
            text = "Recently Played",
            style = typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = spacing.space3)
        )
        
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            level = GlassLevel.Secondary
        ) {
            Text(
                text = "Nothing played yet.",
                style = typography.body,
                color = colors.textTertiary,
            )
        }
    }
}
