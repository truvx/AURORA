package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Applies the gain that LoudnessResolver decides on. Its own clamping is a safety net
 * behind the limiter, so overflow must saturate rather than wrap: a wrapped sample turns a
 * loud peak into a full-scale sample of the opposite sign, which is audible as a click.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NormalizationAudioProcessorTest {

    private fun processor(
        gain: Float,
        encoding: Int = C.ENCODING_PCM_16BIT
    ) = NormalizationAudioProcessor().apply {
        setLinearGain(gain)
        configure(AudioProcessor.AudioFormat(44_100, 2, encoding))
        flush()
    }

    private fun buffer16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun bufferFloat(vararg samples: Float): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putFloat(it) }
            flip()
        }

    private fun drain16(p: NormalizationAudioProcessor): List<Short> {
        val out = p.output
        val result = mutableListOf<Short>()
        while (out.remaining() >= 2) result.add(out.short)
        return result
    }

    private fun drainFloat(p: NormalizationAudioProcessor): List<Float> {
        val out = p.output
        val result = mutableListOf<Float>()
        while (out.remaining() >= 4) result.add(out.float)
        return result
    }

    @Test
    fun `unity gain passes audio through unchanged`() {
        val p = processor(1.0f)
        val input = shortArrayOf(100, -100, 12345, -12345)
        p.queueInput(buffer16(*input))

        assertEquals(input.toList(), drain16(p))
    }

    @Test
    fun `positive gain scales samples up`() {
        val p = processor(2.0f)
        p.queueInput(buffer16(1000, -1000))

        assertEquals(listOf<Short>(2000, -2000), drain16(p))
    }

    @Test
    fun `attenuation scales samples down`() {
        val p = processor(0.5f)
        p.queueInput(buffer16(1000, -1000))

        assertEquals(listOf<Short>(500, -500), drain16(p))
    }

    @Test
    fun `overflow saturates instead of wrapping`() {
        // 20000 * 2 = 40000, beyond Short.MAX_VALUE. Wrapping would flip the sign.
        val p = processor(2.0f)
        p.queueInput(buffer16(20000, -20000))

        val output = drain16(p)
        assertEquals(Short.MAX_VALUE, output[0])
        assertEquals(Short.MIN_VALUE, output[1])
        assertTrue("positive sample must not wrap negative", output[0] > 0)
        assertTrue("negative sample must not wrap positive", output[1] < 0)
    }

    @Test
    fun `float audio is clamped to full scale`() {
        val p = processor(4.0f, encoding = C.ENCODING_PCM_FLOAT)
        p.queueInput(bufferFloat(0.5f, -0.5f, 0.1f))

        val output = drainFloat(p)
        assertEquals(1.0f, output[0], 0.0001f)
        assertEquals(-1.0f, output[1], 0.0001f)
        assertEquals(0.4f, output[2], 0.0001f)
    }

    @Test
    fun `unsupported encodings are rejected`() {
        val p = NormalizationAudioProcessor()
        val result = p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_8BIT))

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
    }
}
