package dev.aurora.player.domain.audio

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The shape of a recording: how loud it is, slice by slice, from 0 to 1.
 *
 * Measured from the actual samples of a file AURORA can read, and never drawn for one it
 * cannot. A YouTube track has no waveform here and must not be given one - the bytes are
 * the provider's, AURORA never sees them, and a pattern invented to fill the bar would be
 * a picture of a sound nobody measured.
 */
data class Waveform(val peaks: List<Float>) {

    val isEmpty: Boolean get() = peaks.isEmpty()

    /**
     * The same shape at a different number of bars.
     *
     * Downsampling takes the loudest sample in each bucket rather than the average.
     * Averaging is what turns a drum track into a flat line: the character of a waveform is
     * in its peaks, and the mean of a peak and the silence around it is neither.
     */
    fun resampleTo(count: Int): List<Float> {
        if (count <= 0 || peaks.isEmpty()) return emptyList()
        if (count == peaks.size) return peaks

        return List(count) { index ->
            val start = index.toLong() * peaks.size / count
            val end = ((index + 1).toLong() * peaks.size / count).coerceAtLeast(start + 1)
            var loudest = 0f
            for (i in start until end.coerceAtMost(peaks.size.toLong())) {
                loudest = max(loudest, peaks[i.toInt()])
            }
            loudest
        }
    }

    companion object {
        /**
         * Scales a set of raw amplitudes so the loudest is 1.
         *
         * Without this a quiet recording draws as a flat line and a loud one fills the bar,
         * which says more about mastering than about the song. The bars are a picture of
         * this track's own dynamics, not a comparison between tracks - the loudness system
         * is what compares tracks, and it measures LUFS rather than looking at pictures.
         */
        fun normalized(amplitudes: List<Float>): Waveform {
            val loudest = amplitudes.maxOrNull() ?: 0f
            if (loudest <= 0f) return Waveform(emptyList())
            return Waveform(amplitudes.map { (it / loudest).coerceIn(0f, 1f) })
        }
    }
}

/**
 * How a waveform is written down.
 *
 * One byte per peak as two hex characters. A float per peak would be four times the size
 * for precision no bar 3dp wide can show, and 1/255 of the height is already below a
 * pixel. Plain text so the column is readable in a database dump and survives any Room
 * type converter change.
 */
object WaveformCodec {

    fun encode(waveform: Waveform): String {
        val out = StringBuilder(waveform.peaks.size * 2)
        for (peak in waveform.peaks) {
            val quantised = (peak.coerceIn(0f, 1f) * 255f).roundToInt()
            out.append(HEX[quantised shr 4]).append(HEX[quantised and 0x0F])
        }
        return out.toString()
    }

    /** Null for anything that is not a whole number of well-formed bytes. */
    fun decode(encoded: String?): Waveform? {
        if (encoded.isNullOrEmpty() || encoded.length % 2 != 0) return null
        val peaks = ArrayList<Float>(encoded.length / 2)
        for (index in encoded.indices step 2) {
            val high = HEX.indexOf(encoded[index].lowercaseChar())
            val low = HEX.indexOf(encoded[index + 1].lowercaseChar())
            if (high < 0 || low < 0) return null
            peaks += ((high shl 4) or low) / 255f
        }
        return Waveform(peaks)
    }

    private const val HEX = "0123456789abcdef"
}
