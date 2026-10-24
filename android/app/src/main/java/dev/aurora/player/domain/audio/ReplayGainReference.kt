package dev.aurora.player.domain.audio

/**
 * Converts ReplayGain tag values into the LUFS-I scale used by [LoudnessResolver].
 *
 * ReplayGain does not store loudness directly; it stores the gain needed to bring a track
 * to a reference level. ReplayGain 2.0 - what modern taggers (loudgain, rsgain, foobar2000)
 * write - defines that reference as `-18 LUFS-I`, so:
 *
 * ```text
 * trackLoudness = REFERENCE_LUFS - trackGainDb
 * ```
 *
 * Older ReplayGain 1.0 tags used an SPL-based reference and are not distinguishable from
 * 2.0 tags by inspection. Treating both as -18 LUFS is the documented, conventional
 * behaviour; it is an assumption about the tag, never a measurement of the audio.
 */
object ReplayGainReference {

    const val REFERENCE_LUFS: Float = -18f

    /**
     * Bumped whenever the meaning of a stored loudness row changes, so stale rows written
     * under an older interpretation can be identified and purged by a migration.
     *
     * 1 = pre-release placeholder values (never measured; purged in schema v4)
     * 2 = derived from ReplayGain tags against [REFERENCE_LUFS]
     */
    const val ANALYSIS_VERSION: Int = 2

    fun lufsFromGain(gainDb: Float): Float = REFERENCE_LUFS - gainDb
}
