package dev.aurora.player.ui.artwork

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Picks the colour the artwork atmosphere takes on behind the glass.
 *
 * Glass only reads as glass when there is something behind it to refract. The backdrop is
 * that something: a slow wash of the current artwork's dominant colour, which is what makes
 * the translucent surfaces above it look like material rather than like flat tinted panels.
 *
 * docs/AURORA_MOTION_SPEC.md asks for the ambient palette to "interpolate slowly and clamp
 * chroma". Clamping is not a detail - an unclamped dominant colour from a saturated cover
 * produces a neon full-screen wash that fights every control on top of it, and the contrast
 * work in ContrastScrim then has to spend a heavy scrim undoing it.
 *
 * Pure, so the behaviour is unit-testable against synthetic pixels rather than judged by eye
 * against real album art.
 */
object ArtworkPalette {

    /** Saturation ceiling for a full-screen wash. Above this it stops being a backdrop. */
    const val MAX_CHROMA = 0.52f

    /** Lightness band. Too dark is invisible; too light leaves nothing for text to sit on. */
    const val MIN_LIGHTNESS = 0.16f
    const val MAX_LIGHTNESS = 0.58f

    /** Pixels at least this opaque count; the rest carry no reliable colour. */
    private const val MIN_ALPHA = 128

    /**
     * Near-black and near-white are excluded before counting.
     *
     * They are the most common pixels in a large share of covers - borders, backgrounds,
     * blown-out highlights - and they describe nothing about the record. Counting them means
     * most artwork resolves to the same two backdrops.
     */
    private const val MIN_USEFUL_LIGHTNESS = 0.07f
    private const val MAX_USEFUL_LIGHTNESS = 0.93f

    private class Bucket {
        var count = 0
        var red = 0L
        var green = 0L
        var blue = 0L
        var saturation = 0.0
    }

    /**
     * The dominant colour of [pixels], already clamped for use as an atmosphere.
     *
     * @param pixels ARGB values, as returned by `Bitmap.getPixels`
     * @return null when nothing usable was found - a fully transparent image, an empty one,
     *         or one made entirely of black and white. The caller keeps its neutral backdrop
     *         rather than being handed a meaningless colour.
     */
    fun dominantColor(pixels: IntArray): Color? {
        if (pixels.isEmpty()) return null

        val buckets = HashMap<Int, Bucket>()

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < MIN_ALPHA) continue

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val highest = max(r, max(g, b))
            val lowest = min(r, min(g, b))
            val lightness = (highest + lowest) / 510f
            if (lightness < MIN_USEFUL_LIGHTNESS || lightness > MAX_USEFUL_LIGHTNESS) continue

            val saturation = if (highest == 0) 0.0 else (highest - lowest).toDouble() / highest

            // Quantised to 4 bits per channel: fine enough to keep distinct colours apart,
            // coarse enough that shading across one region still lands in one bucket.
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count += 1
            bucket.red += r
            bucket.green += g
            bucket.blue += b
            bucket.saturation += saturation
        }

        if (buckets.isEmpty()) return null

        // Weighted by how colourful the bucket is as well as how large. A cover that is
        // mostly grey with one strong accent should take its identity from the accent, but
        // sheer dominance still counts - this tips ties, it does not override them.
        val winner = buckets.values.maxByOrNull { bucket ->
            val averageSaturation = bucket.saturation / bucket.count
            bucket.count * (0.5 + averageSaturation)
        } ?: return null

        return clampForAtmosphere(
            Color(
                red = winner.red.toFloat() / winner.count / 255f,
                green = winner.green.toFloat() / winner.count / 255f,
                blue = winner.blue.toFloat() / winner.count / 255f
            )
        )
    }

    /**
     * Brings a colour into the range a full-screen backdrop can actually occupy.
     *
     * Hue is preserved - that is the part carrying the artwork's identity - while saturation
     * and lightness are pulled into a band that leaves room for content above it.
     */
    fun clampForAtmosphere(color: Color): Color {
        val (hue, saturation, lightness) = toHsl(color)
        return fromHsl(
            hue = hue,
            saturation = saturation.coerceAtMost(MAX_CHROMA),
            lightness = lightness.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS)
        )
    }

    /** Hue in degrees, saturation and lightness in 0..1. */
    fun toHsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val highest = max(r, max(g, b))
        val lowest = min(r, min(g, b))
        val delta = highest - lowest
        val lightness = (highest + lowest) / 2f

        if (delta < 1e-6f) return Triple(0f, 0f, lightness)

        val saturation = delta / (1f - abs(2f * lightness - 1f))
        val hue = when (highest) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return Triple(if (hue < 0f) hue + 360f else hue, saturation.coerceIn(0f, 1f), lightness)
    }

    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Color {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val hue60 = ((hue % 360f) + 360f) % 360f / 60f
        val second = chroma * (1f - abs((hue60 % 2f) - 1f))
        val (r, g, b) = when (hue60.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val offset = lightness - chroma / 2f
        return Color(
            red = (r + offset).coerceIn(0f, 1f),
            green = (g + offset).coerceIn(0f, 1f),
            blue = (b + offset).coerceIn(0f, 1f)
        )
    }
}
