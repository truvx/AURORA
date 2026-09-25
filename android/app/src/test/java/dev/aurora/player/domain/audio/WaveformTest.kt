package dev.aurora.player.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of a recording, and how it survives being written down and redrawn.
 *
 * The rule under all of it: a waveform is a measurement. Everything here is about not
 * losing what was measured, and about having nothing at all when nothing was measured.
 */
class WaveformTest {

    @Test
    fun `downsampling keeps the peak rather than averaging it away`() {
        // A snare in the middle of silence. The mean of these is 0.25, which is neither
        // the hit nor the quiet around it, and would draw every drum track as a flat line.
        val waveform = Waveform(listOf(0f, 1f, 0f, 0f))

        assertEquals(listOf(1f, 0f), waveform.resampleTo(2))
    }

    @Test
    fun `resampling to its own size changes nothing`() {
        val peaks = listOf(0.1f, 0.9f, 0.4f)

        assertEquals(peaks, Waveform(peaks).resampleTo(3))
    }

    @Test
    fun `asking for more bars than there are samples still fills every bar`() {
        val drawn = Waveform(listOf(0.2f, 0.8f)).resampleTo(6)

        assertEquals(6, drawn.size)
        assertTrue(drawn.all { it > 0f })
    }

    @Test
    fun `an empty shape resamples to nothing rather than to zeroes`() {
        // Zeroes would draw as a real measurement of silence. There was no measurement.
        assertEquals(emptyList<Float>(), Waveform(emptyList()).resampleTo(40))
        assertEquals(emptyList<Float>(), Waveform(listOf(0.5f)).resampleTo(0))
    }

    @Test
    fun `normalizing scales the loudest moment to full height`() {
        // A quietly mastered file should draw with the same shape as a loud one: these
        // bars describe this track's own dynamics, not how it compares to another track.
        val waveform = Waveform.normalized(listOf(0.05f, 0.1f, 0.025f))

        assertEquals(listOf(0.5f, 1f, 0.25f), waveform.peaks)
    }

    @Test
    fun `silence has no shape at all`() {
        // Dividing by the loudest sample of nothing is how a silent file becomes NaN bars.
        assertTrue(Waveform.normalized(listOf(0f, 0f, 0f)).isEmpty)
        assertTrue(Waveform.normalized(emptyList()).isEmpty)
    }

    @Test
    fun `a shape survives being written down and read back`() {
        val original = Waveform.normalized(listOf(0.13f, 0.87f, 0.5f, 1f, 0f))

        val restored = WaveformCodec.decode(WaveformCodec.encode(original))!!

        assertEquals(original.peaks.size, restored.peaks.size)
        original.peaks.forEachIndexed { index, peak ->
            // One byte per peak: within half a step of 1/255, which is under a pixel.
            assertEquals(peak, restored.peaks[index], 1f / 255f)
        }
    }

    @Test
    fun `a column written by something else is treated as absent`() {
        // Better a plain scrubber than bars drawn from whatever the string happened to be.
        assertNull(WaveformCodec.decode(null))
        assertNull(WaveformCodec.decode(""))
        assertNull(WaveformCodec.decode("abc"))
        assertNull(WaveformCodec.decode("zz"))
    }

    @Test
    fun `the ends of the range are written exactly`() {
        val restored = WaveformCodec.decode(WaveformCodec.encode(Waveform(listOf(0f, 1f))))!!

        assertEquals(0f, restored.peaks[0], 0f)
        assertEquals(1f, restored.peaks[1], 0f)
    }
}
