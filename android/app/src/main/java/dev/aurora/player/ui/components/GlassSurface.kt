package dev.aurora.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aurora.player.ui.theme.Aurora
import dev.aurora.player.ui.theme.DeviceCapabilities
import dev.aurora.player.ui.theme.LocalDeviceCapabilities

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

/**
 * Base AURORA glass material surface.
 *
 * The surface samples what is behind it, saturates, blurs and finally *refracts* it, then
 * lays its own tint over the top. The refraction is what makes it read as a piece of glass
 * rather than a blurred panel: content bends at the edges, and a bright specular highlight
 * traces the rim where light catches the material.
 *
 * Material weight carries hierarchy, so blur, refraction and highlight all follow the
 * level. A small chip and a floating bar are not the same thickness of glass.
 *
 * Falls back to opaque when the user has asked for reduced transparency, or when a caller
 * asks for it explicitly. Glass is never the only thing providing contrast: legibility over
 * the composited surface is asserted in GlassContrastTest, and artwork bright enough to
 * threaten it is handled by the scrim in AmbientArtworkLayer.
 *
 * @param level Glass depth level controlling tint, blur, refraction and edge treatment
 * @param useOpaqueFallback Force opaque rendering for a surface that cannot afford glass
 * @param backdrop What this glass samples. Defaults to the ambient artwork layer. Floating
 *   chrome passes the combined backdrop so page content refracts through it too - but any
 *   glass *inside* that content must keep the default, or it would sample the layer it is
 *   drawn into and recurse until the render thread overflows its stack.
 */
enum class GlassLevel { Primary, Secondary, Elevated }

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Primary,
    useOpaqueFallback: Boolean = false,
    /*
     * How hard the surface is being pressed, 0 to 1.
     *
     * Real glass answers a finger: the material bulges under it and the edge brightens.
     * Both of those need something behind the surface to bend and something bright to
     * catch, so the effect is invisible over a black background - which is every screen
     * of the emulator with an empty library, and why this is not verified by a
     * screenshot. Judge it on a device with artwork.
     *
     * At rest it multiplies by one, so a surface that is never pressed draws exactly as
     * it did before.
     */
    pressProgress: () -> Float = { 0f },
    shape: androidx.compose.ui.graphics.Shape = Aurora.shapes.card,
    backdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Aurora.colors

    // Glass is an enhancement, never the only contrast mechanism: a user asking for reduced
    // transparency gets opaque surfaces regardless of what the caller requested.
    val reducedTransparency =
        dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences.current.reducedTransparency

    val backgroundColor: Color = if (useOpaqueFallback || reducedTransparency) {
        colors.surfaceOpaqueFallback
    } else {
        when (level) {
            GlassLevel.Primary -> colors.surfaceGlassPrimary
            GlassLevel.Secondary -> colors.surfaceGlassSecondary
            GlassLevel.Elevated -> colors.surfaceGlassElevated
        }
    }

    val source = backdrop ?: dev.aurora.player.ui.theme.LocalAuroraBackdrop.current
    val depth = Aurora.depth
    val density = androidx.compose.ui.platform.LocalDensity.current
    val capabilities = LocalDeviceCapabilities.current

    // On low-end devices skip every per-frame backdrop effect (blur, vibrancy,
    // refraction lens, highlight, shadow) so the GPU stays under its thermal
    // limit. The glass colour itself is kept - the theme is unchanged, only the
    // backdrop sampling that costs frames is dropped.
    val drawBackdrop = source != null && !useOpaqueFallback && !reducedTransparency && !capabilities.isLowEnd

    val blurRadius = with(density) {
        when (level) {
            GlassLevel.Primary -> depth.blurSurface
            GlassLevel.Secondary -> depth.blurSubtle
            GlassLevel.Elevated -> depth.blurFloating
        }.toPx()
    }

    /*
     * Refraction height and amount, in pixels.
     *
     * Secondary glass gets none: it is a quiet grouped material sitting in scrolling
     * content, and a per-frame refraction pass over a constantly invalidating backdrop is
     * exactly the case the library warns costs frames.
     */
    val refractionHeight = with(density) {
        when (level) {
            GlassLevel.Primary -> 12.dp
            GlassLevel.Secondary -> 0.dp
            GlassLevel.Elevated -> 16.dp
        }.toPx()
    }
    val refractionAmount = with(density) {
        when (level) {
            GlassLevel.Primary -> 24.dp
            GlassLevel.Secondary -> 0.dp
            GlassLevel.Elevated -> 32.dp
        }.toPx()
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (drawBackdrop) {
                    Modifier.drawBackdrop(
                        backdrop = source,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(blurRadius)
                            if (refractionAmount > 0f) {
                                // Pressing deepens the refraction, so what shows through
                                // the surface swells under the finger and settles back.
                                val press = pressProgress()
                                lens(refractionHeight, refractionAmount * (1f + PRESS_REFRACTION * press))
                            }
                        },
                        highlight = {
                            val press = pressProgress()
                            when (level) {
                                /*
                                 * Floating chrome catches the most light, and has to catch
                                 * it from somewhere.
                                 *
                                 * The ambient style takes its brightness from the backdrop,
                                 * which worked while the canvas was a near-black with a
                                 * coloured wash over it. On a pitch-black canvas there is
                                 * no light to gather: measured on device, the pill's top
                                 * edge came to 21 against an interior of 26 - darker than
                                 * the surface it was supposed to be lighting - while the
                                 * bottom edge reached 85. A lens with a rim on one side is
                                 * a rectangle. The specular style draws its own, so the
                                 * edge holds whatever is or is not behind it.
                                 */
                                GlassLevel.Elevated -> Highlight(
                                    alpha = 1f + PRESS_HIGHLIGHT * press,
                                    style = HighlightStyle.Default()
                                )
                                GlassLevel.Primary -> Highlight(
                                    alpha = 1f + PRESS_HIGHLIGHT * press,
                                    style = HighlightStyle.Default()
                                )
                                // A plain hairline; anything stronger turns a list of
                                // cards into a list of outlines.
                                GlassLevel.Secondary -> Highlight.Plain
                            }
                        },
                        shadow = {
                            // Only floating surfaces cast one - that is what says they float.
                            if (level == GlassLevel.Elevated) {
                                Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.35f))
                            } else {
                                null
                            }
                        },
                        onDrawSurface = {
                            drawRect(color = backgroundColor)
                        }
                    )
                } else {
                    Modifier.background(backgroundColor)
                }
            ),
        content = content,
    )
}

/** How much a press deepens the refraction and lifts the edge. */
private const val PRESS_REFRACTION = 0.45f
private const val PRESS_HIGHLIGHT = 0.6f
