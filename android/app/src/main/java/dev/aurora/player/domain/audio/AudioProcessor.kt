package dev.aurora.player.domain.audio

interface AudioProcessorConfig {
    val enabled: Boolean
}

interface AudioProcessor<T: AudioProcessorConfig> {
    fun configure(config: T)
    val capabilityState: CapabilityState
}

data class LimiterConfig(
    override val enabled: Boolean,
    val ceilingDb: Float = -1.0f
) : AudioProcessorConfig

data class EqConfig(
    override val enabled: Boolean,
    // Future: preset or band config
) : AudioProcessorConfig
