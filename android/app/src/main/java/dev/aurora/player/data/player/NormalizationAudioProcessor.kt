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

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        if (linearGain == 1.0f) {
            // Passthrough if no gain is applied
            replaceOutputBuffer(size).put(inputBuffer).flip()
            return
        }

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
            else -> {
                outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
    }
}
