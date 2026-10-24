package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.aurora.player.domain.audio.EqConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * EqAudioProcessor is currently a passthrough: `queueInput` copies its input unchanged, and
 * EqConfig carries no bands. These tests pin that contract rather than implying a working
 * equalizer - if real filtering lands, the passthrough tests should fail and be rewritten
 * alongside it.
 *
 * Until then no EQ control is exposed in Settings, so the stub makes no promise to the user.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class EqAudioProcessorTest {

    private fun processor(enabled: Boolean = true) = EqAudioProcessor().apply {
        configure(EqConfig(enabled = enabled))
        configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun buffer16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun drain16(p: EqAudioProcessor): List<Short> {
        val out = p.output
        val result = mutableListOf<Short>()
        while (out.remaining() >= 2) result.add(out.short)
        return result
    }

    @Test
    fun `audio passes through bit-exact while the equalizer is a stub`() {
        val p = processor()
        val input = shortArrayOf(0, 1, -1, 12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE)
        p.queueInput(buffer16(*input))

        assertEquals(input.toList(), drain16(p))
    }

    @Test
    fun `a disabled equalizer reports itself inactive`() {
        assertFalse(
            "a disabled processor must not sit in the audio path",
            processor(enabled = false).isActive
        )
    }

    @Test
    fun `unsupported encodings are rejected`() {
        val p = EqAudioProcessor().apply { configure(EqConfig(enabled = true)) }
        val result = p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_8BIT))

        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
    }
}
