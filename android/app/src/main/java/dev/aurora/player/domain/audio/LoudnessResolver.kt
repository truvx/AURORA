package dev.aurora.player.domain.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object LoudnessResolver {

    fun resolveGain(
        preference: LoudnessPreference,
        trackData: TrackLoudnessData?,
        capability: LoudnessCapability,
        isAlbumContext: Boolean = false
    ): AppliedNormalization {
        if (preference == LoudnessPreference.Off || capability != LoudnessCapability.Supported) {
            return AppliedNormalization(
                preference = preference,
                targetLufs = null,
                capability = capability,
                actualGainDb = 0f,
                capped = false,
                truePeakLimited = false
            )
        }

        val targetLufs = when (preference) {
            LoudnessPreference.Quiet -> -18f
            LoudnessPreference.Normal -> -14f
            LoudnessPreference.Loud -> -11f
            else -> 0f // Unreachable
        }

        val attenuationCap = when (preference) {
            LoudnessPreference.Loud -> -12f
            else -> -18f
        }

        val gainCap = 6f
        val peakTarget = -1.0f // dBTP

        // Determine base LUFS and peak
        val baseLufs = if (isAlbumContext && trackData?.albumLufs != null) {
            trackData.albumLufs
        } else {
            trackData?.lufsIntegrated
        }

        val basePeakLinear = if (isAlbumContext && trackData?.albumPeak != null) {
            trackData.albumPeak
        } else {
            trackData?.truePeak
        }

        if (baseLufs == null) {
            return AppliedNormalization(
                preference = preference,
                targetLufs = targetLufs,
                capability = capability,
                actualGainDb = 0f,
                capped = false,
                truePeakLimited = false
            )
        }

        var desiredGain = targetLufs - baseLufs
        var isCapped = false

        if (desiredGain > gainCap) {
            desiredGain = gainCap
            isCapped = true
        } else if (desiredGain < attenuationCap) {
            desiredGain = attenuationCap
            isCapped = true
        }

        var truePeakLimited = false
        if (basePeakLinear != null && basePeakLinear > 0f) {
            val peakDb = 20f * log10(basePeakLinear)
            val expectedPeak = peakDb + desiredGain
            if (expectedPeak > peakTarget) {
                desiredGain = peakTarget - peakDb
                truePeakLimited = true
            }
        }

        return AppliedNormalization(
            preference = preference,
            targetLufs = targetLufs,
            capability = capability,
            actualGainDb = desiredGain,
            capped = isCapped,
            truePeakLimited = truePeakLimited
        )
    }
    
    fun toLinearGain(gainDb: Float): Float {
        return 10f.pow(gainDb / 20f)
    }
}
