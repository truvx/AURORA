package dev.aurora.player.domain.audio

enum class LoudnessPreference {
    Off, Quiet, Normal, Loud
}

enum class LoudnessCapability {
    Supported, ProviderManaged, Unavailable
}

data class TrackLoudnessData(
    val lufsIntegrated: Float?,
    val truePeak: Float?,
    val albumLufs: Float?,
    val albumPeak: Float?
)

data class AppliedNormalization(
    val preference: LoudnessPreference,
    val targetLufs: Float?,
    val capability: LoudnessCapability,
    val actualGainDb: Float,
    val capped: Boolean,
    val truePeakLimited: Boolean
)
