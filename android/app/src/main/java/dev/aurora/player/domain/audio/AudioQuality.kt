package dev.aurora.player.domain.audio

/**
 * What the listener asked for, which is not what they will necessarily get.
 *
 * `RequestedQuality + ProviderCapabilities + TrackCapabilities = ActualQuality`, and this
 * is only the first term. docs/AUDIO_QUALITY_POLICY.md is explicit that a preference is
 * not a guarantee: the Now Playing disclosure shows what is actually playing, with its
 * provenance, and never echoes this back as though it were a fact about the audio.
 *
 * The six values are the policy's own - nothing here invents a quality tier, and in
 * particular nothing infers Lossless or Hi-Res from a preference having been set.
 */
enum class RequestedQuality {
    Auto,
    Low,
    Medium,
    High,
    Lossless,
    HiRes;

    /** How it is written on screen. "Hi-Res" is hyphenated in the policy, so it is here. */
    val label: String
        get() = when (this) {
            Auto -> "Auto"
            Low -> "Low"
            Medium -> "Medium"
            High -> "High"
            Lossless -> "Lossless"
            HiRes -> "Hi-Res"
        }

    companion object {
        /**
         * The three offered on Home, as a segmented control.
         *
         * A deliberate subset. Home is a place to nudge quality between listening
         * contexts, not to configure it - Auto, Low and Hi-Res stay in Settings, where
         * there is room to say what each one means. Showing all six here would make the
         * control the widest thing on the screen and still not explain itself.
         */
        val onHome: List<RequestedQuality> = listOf(Medium, High, Lossless)
    }
}
