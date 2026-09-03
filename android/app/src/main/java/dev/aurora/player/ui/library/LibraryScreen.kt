package dev.aurora.player.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.theme.Aurora

@Composable
fun LibraryScreen(
    items: List<MediaItem> = emptyList(),
    onScanRequested: () -> Unit = {}
) {
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onScanRequested()
        }
    }

    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
            .padding(spacing.space4)
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Local Library Access",
                    style = typography.headline,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = spacing.space2)
                )
                Text(
                    text = "Permission is required to scan for local music files.",
                    style = typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.space6)
                )
                GlassButton(
                    text = "Grant Permission",
                    onClick = {
                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(perm)
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = typography.headline,
                    color = colors.textPrimary
                )
                GlassButton(
                    text = "Rescan",
                    onClick = onScanRequested,
                    style = dev.aurora.player.ui.components.GlassButtonStyle.Secondary
                )
            }
            
            Spacer(modifier = Modifier.height(spacing.space4))
            
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Library Empty",
                        style = typography.headline,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = spacing.space2)
                    )
                    Text(
                        text = "No local music found or scan pending.",
                        style = typography.body,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.space2)
                ) {
                    items(items, key = { it.id }) { item ->
                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            level = GlassLevel.Primary
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.space3)
                            ) {
                                Text(
                                    text = item.title,
                                    style = typography.title,
                                    color = if (item.isAvailable) colors.textPrimary else colors.textSecondary
                                )
                                Text(
                                    text = item.artist ?: "Unknown Artist",
                                    style = typography.label,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
