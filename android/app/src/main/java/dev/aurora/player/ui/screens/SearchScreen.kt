package dev.aurora.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.aurora.player.ui.theme.Aurora

/** Search screen — foundation shell. Content added in Phase 4+. */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.space4, vertical = spacing.space6),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Search",
            style = typography.headline,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(spacing.space2))
        Text(
            text = "Find music across your library and YouTube",
            style = typography.body,
            color = colors.textSecondary,
        )
    }
}
