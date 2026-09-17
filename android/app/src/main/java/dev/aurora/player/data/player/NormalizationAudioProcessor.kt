package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

// Media3's audio-processing and renderer APIs are annotated @UnstableApi. This class is
// built directly on them, so the opt-in is deliberate and scoped to this file rather than
// enabled module-wide, which would silently cover future code too.
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NormalizationAudioProcessor : BaseAudioProcessor() {

    private var linearGain: Float = 1.0f

    fun setLinearGain(gain: Float) {
        if (this.linearGain != gain) {
            this.linearGain = gain
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    /**
     * Applies the gain sample by sample.
     *
     * There is deliberately no fast path for a gain of exactly 1. The previous one did
     * `replaceOutputBuffer(size).put(inputBuffer)`, and Media3 can hand a processor a buffer
     * that is already its own output buffer - `put` then throws "The source buffer is this
     * buffer" and the renderer reports an unexplained "Unexpected runtime error".
     *
     * That path ran for every track *without* ReplayGain metadata, which is most music, so
     * nothing but the one tagged test file could be played. It survived because that tagged
     * file was what verification used.
     *
     * The loop below is safe whether or not the buffers alias: it reads at an absolute index
     * that is never behind the sequential write position, so a value is always read before
     * anything can overwrite it. A multiply per sample is a rounding error next to the codec
     * that produced them; if a real fast path is ever wanted it belongs in `isActive`, not
     * here, so that Media3 skips this processor entirely.
     */
    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        val outputBuffer = replaceOutputBuffer(size)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                for (i in position until limit step 2) {
                    val sample = inputBuffer.getShort(i).toFloat()
                    var processed = sample * linearGain
                    // Hard limiter to avoid clipping 16-bit boundaries
                    if (processed > Short.MAX_VALUE) processed = Short.MAX_VALUE.toFloat()
                    if (processed < Short.MIN_VALUE) processed = Short.MIN_VALUE.toFloat()
                    outputBuffer.putShort(processed.toInt().toShort())
                }
                inputBuffer.position(limit)
            }
            C.ENCODING_PCM_FLOAT -> {
                for (i in position until limit step 4) {
                    val sample = inputBuffer.getFloat(i)
                    var processed = sample * linearGain
                    // Float PCM bounds are typically -1.0 to 1.0
                    if (processed > 1.0f) processed = 1.0f
                    if (processed < -1.0f) processed = -1.0f
                    outputBuffer.putFloat(processed)
                }
                inputBuffer.position(limit)
            }
            else -> throw IllegalStateException(
                // Unreachable: onConfigure returns NOT_SET for anything else, so Media3
                // never configures this processor with an encoding it cannot handle. Stated
                // rather than silently copied, because a silent copy here would pass
                // untouched audio through a processor whose whole job is to change it.
                "Unsupported PCM encoding ${inputAudioFormat.encoding} reached the processor"
            )
        }

        outputBuffer.flip()
    }
}
