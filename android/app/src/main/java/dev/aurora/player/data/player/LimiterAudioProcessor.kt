package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import dev.aurora.player.domain.audio.CapabilityState
import dev.aurora.player.domain.audio.LimiterConfig
import java.nio.ByteBuffer

class LimiterAudioProcessor : BaseAudioProcessor(), dev.aurora.player.domain.audio.AudioProcessor<LimiterConfig> {
    
    private var isEnabled = true
    private var ceilingLinear = 1.0f
    
    override val capabilityState = CapabilityState.SUPPORTED

    override fun configure(config: LimiterConfig) {
        isEnabled = config.enabled
        // Convert dB to linear scalar. ceilingDb = -1.0 means ~0.89 linear
        ceilingLinear = Math.pow(10.0, config.ceilingDb / 20.0).toFloat()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean {
        return super.isActive() && isEnabled
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        val outputBuffer = replaceOutputBuffer(size)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val ceiling = (Short.MAX_VALUE * ceilingLinear).toInt()
                val minCeil = -ceiling
                for (i in position until limit step 2) {
                    var sample = inputBuffer.getShort(i).toInt()
                    if (sample > ceiling) sample = ceiling
                    if (sample < minCeil) sample = minCeil
                    outputBuffer.putShort(sample.toShort())
                }
                inputBuffer.position(limit)
            }
            C.ENCODING_PCM_FLOAT -> {
                val ceiling = ceilingLinear
                val minCeil = -ceiling
                for (i in position until limit step 4) {
                    var sample = inputBuffer.getFloat(i)
                    if (sample > ceiling) sample = ceiling
                    if (sample < minCeil) sample = minCeil
                    outputBuffer.putFloat(sample)
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
