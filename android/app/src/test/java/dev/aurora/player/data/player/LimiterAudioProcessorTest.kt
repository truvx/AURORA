package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.aurora.player.domain.audio.LimiterConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The limiter is the last guard against clipping after normalization gain is applied, so
 * its ceiling must hold for every sample, in both polarities, at both supported encodings.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class LimiterAudioProcessorTest {

    private fun pcm16Processor(ceilingDb: Float = -1.0f, enabled: Boolean = true) =
        LimiterAudioProcessor().apply {
            configure(LimiterConfig(enabled = enabled, ceilingDb = ceilingDb))
            configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            flush()
        }

    private fun buffer16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun drain16(processor: LimiterAudioProcessor): List<Short> {
        val out = processor.output
        val result = mutableListOf<Short>()
        while (out.remaining() >= 2) result.add(out.short)
        return result
    }

    private fun ceilingFor(ceilingDb: Float): Int =
        (Short.MAX_VALUE * Math.pow(10.0, ceilingDb / 20.0).toFloat()).toInt()

    @Test
    fun `samples above the ceiling are clamped down`() {
        val processor = pcm16Processor()
        processor.queueInput(buffer16(Short.MAX_VALUE, 1000))

        val output = drain16(processor)
        val ceiling = ceilingFor(-1.0f)
        assertEquals(ceiling.toShort(), output[0])
        assertEquals(1000.toShort(), output[1])
    }

    @Test
    fun `negative samples are clamped symmetrically`() {
        val processor = pcm16Processor()
        processor.queueInput(buffer16(Short.MIN_VALUE, (-1000).toShort()))

        val output = drain16(processor)
        assertEquals((-ceilingFor(-1.0f)).toShort(), output[0])
        assertEquals((-1000).toShort(), output[1])
    }

    @Test
    fun `no sample ever exceeds the ceiling in either direction`() {
        val processor = pcm16Processor()
        val extremes = shortArrayOf(
            Short.MAX_VALUE, Short.MIN_VALUE, 32000, -32000, 0, 1, -1, 16384, -16384
        )
        processor.queueInput(buffer16(*extremes))

        val ceiling = ceilingFor(-1.0f)
        drain16(processor).forEach { sample ->
            assertTrue("sample $sample exceeded ceiling $ceiling", sample <= ceiling)
            assertTrue("sample $sample below -$ceiling", sample >= -ceiling)
        }
    }

    @Test
    fun `a lower ceiling clamps harder`() {
        val loose = pcm16Processor(ceilingDb = -1.0f)
        val tight = pcm16Processor(ceilingDb = -6.0f)

        loose.queueInput(buffer16(Short.MAX_VALUE))
        tight.queueInput(buffer16(Short.MAX_VALUE))

        val looseOut = drain16(loose).first()
        val tightOut = drain16(tight).first()
        assertTrue("a -6 dB ceiling must clamp below a -1 dB ceiling", tightOut < looseOut)
    }

    @Test
    fun `signal below the ceiling passes through untouched`() {
        val processor = pcm16Processor()
        val quiet = shortArrayOf(100, -100, 5000, -5000)
        processor.queueInput(buffer16(*quiet))

        assertEquals(quiet.toList(), drain16(processor))
    }

    @Test
    fun `a disabled limiter reports itself inactive`() {
        val processor = pcm16Processor(enabled = false)
        assertFalse("a disabled limiter must not sit in the audio path", processor.isActive)
    }

    @Test
    fun `unsupported encodings are rejected rather than passed through silently`() {
        val processor = LimiterAudioProcessor().apply {
            configure(LimiterConfig(enabled = true))
        }
        val result = processor.configure(
            AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_8BIT)
        )
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
    }
}
