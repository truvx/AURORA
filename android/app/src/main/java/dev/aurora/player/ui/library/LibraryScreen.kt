package dev.aurora.player.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.ui.components.GlassButton
import dev.aurora.player.ui.components.GlassCard
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassIconButton
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.haptics.HapticEvent
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.theme.Aurora

@Composable
fun LibraryScreen(
    items: List<MediaItem> = emptyList(),
    favoriteIds: Set<String> = emptySet(),
    onScanRequested: () -> Unit = {},
    onToggleFavorite: (mediaId: String, isFavorite: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticEngine.current
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // Read the real permission state; a permission granted in an earlier session must not
    // leave the user stuck behind the request gate.
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            onScanRequested()
        }
    }

    // Pick up files added since the last visit when access is already in place.
    LaunchedEffect(hasPermission) {
        if (hasPermission) onScanRequested()
    }

    val spacing = Aurora.spacing
    val typography = Aurora.typography
    val colors = Aurora.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = spacing.space4, 
                vertical = spacing.space6 + 48.dp // Padding for Top/Bottom bars
            )
    ) {
        if (!hasPermission) {
            Text(
                text = "Library",
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
                    imageVector = Icons.Outlined.LibraryMusic,
                    contentDescription = null,
                    tint = colors.accentPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = spacing.space4)
                )
                Text(
                    text = "Your library is waiting",
                    style = typography.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = spacing.space2)
                )
                Text(
                    text = "Allow AURORA to scan your device for local music files.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = spacing.space6)
                )
                GlassButton(
                    text = "Allow Access",
                    onClick = { permissionLauncher.launch(audioPermission) }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = typography.display,
                    color = colors.textPrimary
                )
                GlassButton(
                    text = "Rescan",
                    onClick = onScanRequested,
                    style = dev.aurora.player.ui.components.GlassButtonStyle.Secondary
                )
            }
            
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.space8),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.padding(bottom = spacing.space4)
                    )
                    Text(
                        text = "No music found",
                        style = typography.title,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(spacing.space2))
                    Text(
                        text = "Add audio files to your device or initiate a rescan.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
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
                            level = GlassLevel.Secondary
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.space3),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Placeholder for Artwork in Track Row
                                dev.aurora.player.ui.components.ArtworkSurface(
                                    altText = "Album art",
                                    modifier = Modifier.height(48.dp)
                                )
                                Column(
                                    modifier = Modifier.padding(start = spacing.space3).weight(1f)
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

                                val isFavorite = favoriteIds.contains(item.id)
                                GlassIconButton(
                                    icon = if (isFavorite) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Outlined.FavoriteBorder
                                    },
                                    contentDesc = if (isFavorite) {
                                        "Remove ${item.title} from favorites"
                                    } else {
                                        "Add ${item.title} to favorites"
                                    },
                                    onClick = {
                                        haptics.fire(HapticEvent.Favorite)
                                        onToggleFavorite(item.id, !isFavorite)
                                    },
                                    selected = isFavorite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
