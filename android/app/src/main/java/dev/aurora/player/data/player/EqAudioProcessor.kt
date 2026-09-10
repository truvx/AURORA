package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import dev.aurora.player.domain.audio.CapabilityState
import dev.aurora.player.domain.audio.EqConfig
import java.nio.ByteBuffer

class EqAudioProcessor : BaseAudioProcessor(), dev.aurora.player.domain.audio.AudioProcessor<EqConfig> {
    
    private var isEnabled = false
    override val capabilityState = CapabilityState.SUPPORTED

    override fun configure(config: EqConfig) {
        isEnabled = config.enabled
    }

    override fun isActive(): Boolean {
        return super.isActive() && isEnabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Passthrough for now, true EQ requires FFT or BiQuad filters
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        val outputBuffer = replaceOutputBuffer(size)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}
