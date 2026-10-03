package dev.aurora.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import dev.aurora.player.ui.theme.Aurora

/**
 * The lozenge that marks which of a row of choices is current, and slides between them.
 *
 * It is one object moving, not several blinking: the eye follows it, so the control reads
 * as having a position rather than a set of independent lights. An interrupted tap
 * re-targets from wherever the lozenge has reached instead of jumping.
 *
 * The material is a real lens. It samples [backdrop] - the page behind the whole bar, not
 * the bar's own glass - and bends it, so what shows through the lozenge is displaced
 * against what shows through the track on either side. That displacement is the effect;
 * a lighter fill is a label for it. The specular highlight is drawn rather than gathered,
 * which is what lets an edge catch light on a black canvas where there is none to gather.
 *
 * Sampling the page rather than the bar is also what keeps it from being glass on glass,
 * which stops reading as either.
 */
@Composable
fun BoxScope.SelectionCapsule(
    /** Which slot is selected, counted in slots from the start. */
    index: Int,
    /** How many slots share the row. */
    count: Int,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    inset: Dp = 3.dp,
) {
    if (count <= 0) return
    val motion = Aurora.motion
    val colors = Aurora.colors

    val slot by animateFloatAsState(
        targetValue = index.coerceIn(0, count - 1).toFloat(),
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = motion.springStandardDamping,
            stiffness = motion.springStandardStiffness,
        ),
        label = "selection",
    )

    // Its own captured layer when the caller has none, so the lens always has something to
    // bend. With nothing behind it the effect is invisible and the fill carries the state.
    val source = backdrop ?: rememberLayerBackdrop()
    val shape = RoundedCornerShape(percent = 50)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val refractionHeight = with(density) { REFRACTION_HEIGHT.toPx() }
    val refractionAmount = with(density) { REFRACTION_AMOUNT.toPx() }
    val blurRadius = with(density) { BLUR.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(1f / count)
            .graphicsLayer { translationX = slot * size.width }
            .padding(inset)
            .clearAndSetSemantics {}
            .drawBackdrop(
                backdrop = source,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blurRadius)
                    // The refraction. Height is how deep the bevel reads, amount is how far
                    // the background is pushed aside at the edge - an index of refraction
                    // expressed in pixels, which is the control this library gives.
                    lens(refractionHeight, refractionAmount)
                },
                highlight = { Highlight(alpha = 1f, style = HighlightStyle.Default()) },
            ) { drawRect(colors.surfaceGlassElevated) }
    )
}

/** How deep the bevel reads. Larger looks thicker; past the radius it looks like a tube. */
private val REFRACTION_HEIGHT = 14.dp

/** How far the edge pushes the background aside - the visible strength of the refraction. */
private val REFRACTION_AMOUNT = 28.dp

/** Softens what shows through, so the lozenge reads as a material rather than a window. */
private val BLUR = 2.dp
