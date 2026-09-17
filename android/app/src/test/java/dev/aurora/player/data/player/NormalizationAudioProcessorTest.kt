package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The gain stage every track passes through.
 *
 * A gain of exactly 1 used to take a "passthrough" branch that copied the input buffer onto
 * itself, which throws. Since a track only has a gain other than 1 when it carries
 * ReplayGain metadata, that meant *every untagged track failed to play* - which is most
 * music. It went unnoticed because the one local file used for verification was tagged.
 *
 * So the case these care most about is the boring one: gain 1, and the buffers aliasing.
 */
class NormalizationAudioProcessorTest {

    private fun processorFor(
        encoding: Int = C.ENCODING_PCM_16BIT,
        gain: Float
    ): NormalizationAudioProcessor {
        val processor = NormalizationAudioProcessor()
        processor.setLinearGain(gain)
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, encoding))
        processor.flush()
        return processor
    }

    /** Media3's buffers are native-ordered; matching that is what makes reads line up. */
    private fun shorts(vararg values: Short): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putShort(it) }
            flip()
        }

    private fun floats(vararg values: Float): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putFloat(it) }
            flip()
        }

    private fun readShorts(buffer: ByteBuffer): List<Short> =
        buildList { while (buffer.remaining() >= 2) add(buffer.short) }

    private fun readFloats(buffer: ByteBuffer): List<Float> =
        buildList { while (buffer.remaining() >= 4) add(buffer.float) }

    // --- the regression -----------------------------------------------------------------

    @Test
    fun `unity gain passes samples through unchanged`() {
        val processor = processorFor(gain = 1.0f)

        processor.queueInput(shorts(0, 1000, -1000, 32767))

        assertEquals(
            listOf<Short>(0, 1000, -1000, 32767),
            readShorts(processor.output)
        )
    }

    @Test
    fun `a buffer that is already the processor's own output does not fail`() {
        // Exactly the shape that crashed: Media3 can hand a processor back a buffer it
        // previously produced. `put` refuses to copy a buffer onto itself, and the renderer
        // surfaces it only as "Unexpected runtime error".
        val processor = processorFor(gain = 1.0f)

        processor.queueInput(shorts(500, -500))
        val ownOutput = processor.output

        // Feeding that same instance back in must produce audio, not an exception.
        processor.queueInput(ownOutput)

        assertEquals(listOf<Short>(500, -500), readShorts(processor.output))
    }

    @Test
    fun `every track is playable regardless of whether it carries gain metadata`() {
        // The two states a real library contains. Neither may throw.
        for (gain in listOf(1.0f, 0.7f)) {
            val processor = processorFor(gain = gain)

            processor.queueInput(shorts(1000, 2000, 3000))

            assertTrue("gain $gain produced no audio", processor.output.remaining() > 0)
        }
    }

    // --- the actual gain ----------------------------------------------------------------

    @Test
    fun `attenuation scales samples down`() {
        val processor = processorFor(gain = 0.5f)

        processor.queueInput(shorts(1000, -2000))

        assertEquals(listOf<Short>(500, -1000), readShorts(processor.output))
    }

    @Test
    fun `positive gain scales samples up`() {
        val processor = processorFor(gain = 2.0f)

        processor.queueInput(shorts(1000, -1000))

        assertEquals(listOf<Short>(2000, -2000), readShorts(processor.output))
    }

    @Test
    fun `boosting past full scale clamps instead of wrapping`() {
        // Integer overflow here would turn a loud peak into a sample of the opposite sign,
        // which is heard as a click rather than as distortion.
        val processor = processorFor(gain = 4.0f)

        processor.queueInput(shorts(20_000, -20_000))

        assertEquals(listOf(Short.MAX_VALUE, Short.MIN_VALUE), readShorts(processor.output))
    }

    @Test
    fun `float samples are scaled and clamped to full scale`() {
        val processor = processorFor(encoding = C.ENCODING_PCM_FLOAT, gain = 2.0f)

        processor.queueInput(floats(0.1f, 0.8f, -0.8f))

        val output = readFloats(processor.output)
        assertEquals(0.2f, output[0], 0.0001f)
        assertEquals(1.0f, output[1], 0.0001f)
        assertEquals(-1.0f, output[2], 0.0001f)
    }

    @Test
    fun `the output is the same length as the input`() {
        val processor = processorFor(gain = 0.9f)

        processor.queueInput(shorts(1, 2, 3, 4, 5, 6))

        assertEquals(12, processor.output.remaining())
    }

    @Test
    fun `an empty buffer produces no output and does not throw`() {
        val processor = processorFor(gain = 1.0f)

        processor.queueInput(shorts())

        assertEquals(0, processor.output.remaining())
    }

    // --- configuration ------------------------------------------------------------------

    @Test
    fun `unsupported encodings are refused at configure time`() {
        // The guard that makes the unreachable branch in queueInput unreachable.
        val processor = NormalizationAudioProcessor()

        val output = processor.configure(
            AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_24BIT)
        )

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, output)
    }

    @Test
    fun `supported encodings pass the format through unchanged`() {
        val processor = NormalizationAudioProcessor()
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)

        assertEquals(format, processor.configure(format))
    }
}
