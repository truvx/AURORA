package dev.aurora.player.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.aurora.player.data.discovery.Profile
import dev.aurora.player.ui.components.GlassLevel
import dev.aurora.player.ui.components.GlassSurface
import dev.aurora.player.ui.theme.Aurora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The listener's avatar: their photo, their initials, or a person.
 *
 * Never an empty circle. Initials are the honest middle step between "no photo chosen" and
 * a stock silhouette, and they at least say whose device this is.
 */
@Composable
fun ProfileAvatar(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val colors = Aurora.colors

    // Keyed on the version as well as the path: the file keeps its name when replaced, so
    // the path alone would not tell this to reload.
    val photo by produceState<ImageBitmap?>(null, profile.photoPath, profile.photoVersion) {
        val path = profile.photoPath
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    GlassSurface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = profile.name?.let { "Profile: $it" } ?: "Profile and settings"
            },
        level = GlassLevel.Elevated,
        shape = CircleShape
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = photo
            val initials = profile.name?.initials()
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                initials != null -> Text(
                    text = initials,
                    style = Aurora.typography.label,
                    color = colors.textPrimary
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** First letters of the first two words, which is what reads at 40dp. */
internal fun String.initials(): String? =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { null }
