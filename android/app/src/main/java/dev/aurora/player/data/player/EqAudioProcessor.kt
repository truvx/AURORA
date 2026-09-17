package dev.aurora.player.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.aurora.player.domain.audio.CapabilityState
import dev.aurora.player.domain.audio.EqConfig
import java.nio.ByteBuffer

// Media3's audio-processing and renderer APIs are annotated @UnstableApi. This class is
// built directly on them, so the opt-in is deliberate and scoped to this file rather than
// enabled module-wide, which would silently cover future code too.
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class EqAudioProcessor : BaseAudioProcessor(), dev.aurora.player.domain.audio.AudioProcessor<EqConfig> {
    
    private var isEnabled = false
    override val capabilityState = CapabilityState.SUPPORTED

    override fun configure(config: EqConfig) {
        isEnabled = config.enabled
    }

    private companion object {
        /**
         * Flip to true when real filters land.
         *
         * Until then this processor copies its input to its output and changes nothing, so
         * being "active" buys a full buffer copy per callback and no audible difference.
         * Worse, that copy is `outputBuffer.put(inputBuffer)`, which throws when Media3
         * hands a processor a buffer that is already its own output - the same fault that
         * stopped every untagged track from playing in NormalizationAudioProcessor.
         */
        const val FILTERING_IMPLEMENTED = false
    }

    /** [isEnabled] is still honoured, so the user's setting is ready when filters exist. */
    override fun isActive(): Boolean =
        super.isActive() && FILTERING_IMPLEMENTED && isEnabled

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
