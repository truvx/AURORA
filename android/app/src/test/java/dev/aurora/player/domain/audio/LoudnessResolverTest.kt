package dev.aurora.player.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the loudness policy against docs/LOUDNESS_POLICY_FINAL.md:
 * targets -18/-14/-11 LUFS-I, a +6 dB gain cap, attenuation caps of -18 dB
 * (-12 dB for Loud), and true-peak protection at -1.0 dBTP.
 */
class LoudnessResolverTest {

    private val tolerance = 0.001f

    private fun track(
        lufs: Float? = null,
        peak: Float? = null,
        albumLufs: Float? = null,
        albumPeak: Float? = null
    ) = TrackLoudnessData(
        lufsIntegrated = lufs,
        truePeak = peak,
        albumLufs = albumLufs,
        albumPeak = albumPeak
    )

    private fun resolve(
        preference: LoudnessPreference,
        data: TrackLoudnessData?,
        capability: LoudnessCapability = LoudnessCapability.Supported,
        isAlbumContext: Boolean = false
    ) = LoudnessResolver.resolveGain(preference, data, capability, isAlbumContext)

    // --- disabled and unsupported paths -------------------------------------------------

    @Test
    fun `Off applies no gain and reports no target`() {
        val result = resolve(LoudnessPreference.Off, track(lufs = -24f))

        assertEquals(0f, result.actualGainDb, tolerance)
        assertNull(result.targetLufs)
        assertFalse(result.capped)
        assertFalse(result.truePeakLimited)
    }

    @Test
    fun `provider-managed capability applies no gain`() {
        val result = resolve(
            LoudnessPreference.Normal,
            track(lufs = -24f),
            capability = LoudnessCapability.ProviderManaged
        )

        assertEquals(0f, result.actualGainDb, tolerance)
        assertEquals(LoudnessCapability.ProviderManaged, result.capability)
    }

    @Test
    fun `unavailable capability applies no gain`() {
        val result = resolve(
            LoudnessPreference.Normal,
            track(lufs = -24f),
            capability = LoudnessCapability.Unavailable
        )

        assertEquals(0f, result.actualGainDb, tolerance)
    }

    // --- unknown measurement ------------------------------------------------------------

    @Test
    fun `unknown loudness applies no gain but still reports the target`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = null))

        assertEquals(0f, result.actualGainDb, tolerance)
        assertEquals(-14f, result.targetLufs!!, tolerance)
        assertFalse(result.capped)
        assertFalse(result.truePeakLimited)
    }

    @Test
    fun `absent track data applies no gain`() {
        val result = resolve(LoudnessPreference.Loud, null)

        assertEquals(0f, result.actualGainDb, tolerance)
        assertEquals(-11f, result.targetLufs!!, tolerance)
    }

    // --- targets ------------------------------------------------------------------------

    @Test
    fun `targets match the specification`() {
        assertEquals(-18f, resolve(LoudnessPreference.Quiet, track(lufs = -18f)).targetLufs!!, tolerance)
        assertEquals(-14f, resolve(LoudnessPreference.Normal, track(lufs = -14f)).targetLufs!!, tolerance)
        assertEquals(-11f, resolve(LoudnessPreference.Loud, track(lufs = -11f)).targetLufs!!, tolerance)
    }

    @Test
    fun `a track already at target needs no gain`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -14f))

        assertEquals(0f, result.actualGainDb, tolerance)
        assertFalse(result.capped)
    }

    @Test
    fun `a quiet track is amplified toward the target`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -18f))

        assertEquals(4f, result.actualGainDb, tolerance)
        assertFalse(result.capped)
    }

    @Test
    fun `a loud track is attenuated toward the target`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -8f))

        assertEquals(-6f, result.actualGainDb, tolerance)
        assertFalse(result.capped)
    }

    // --- gain cap -----------------------------------------------------------------------

    @Test
    fun `gain of exactly plus 6 dB is allowed uncapped`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -20f))

        assertEquals(6f, result.actualGainDb, tolerance)
        assertFalse(result.capped)
    }

    @Test
    fun `gain beyond plus 6 dB is capped`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -30f))

        assertEquals(6f, result.actualGainDb, tolerance)
        assertTrue(result.capped)
    }

    // --- attenuation caps ---------------------------------------------------------------

    @Test
    fun `attenuation is capped at minus 18 dB for Normal`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = 10f))

        assertEquals(-18f, result.actualGainDb, tolerance)
        assertTrue(result.capped)
    }

    @Test
    fun `attenuation is capped at minus 18 dB for Quiet`() {
        val result = resolve(LoudnessPreference.Quiet, track(lufs = 6f))

        assertEquals(-18f, result.actualGainDb, tolerance)
        assertTrue(result.capped)
    }

    @Test
    fun `attenuation is capped at minus 12 dB for Loud`() {
        val result = resolve(LoudnessPreference.Loud, track(lufs = 6f))

        assertEquals(-12f, result.actualGainDb, tolerance)
        assertTrue(result.capped)
    }

    @Test
    fun `attenuation of exactly the cap is not flagged as capped`() {
        val result = resolve(LoudnessPreference.Loud, track(lufs = 1f))

        assertEquals(-12f, result.actualGainDb, tolerance)
        assertFalse(result.capped)
    }

    // --- true-peak protection -----------------------------------------------------------

    @Test
    fun `gain is reduced so the peak stays at minus 1 dBTP`() {
        // Full-scale peak (0 dBTP) with a +4 dB desired gain would clip hard.
        val result = resolve(LoudnessPreference.Normal, track(lufs = -18f, peak = 1.0f))

        assertEquals(-1f, result.actualGainDb, tolerance)
        assertTrue(result.truePeakLimited)
    }

    @Test
    fun `headroom below minus 1 dBTP leaves gain untouched`() {
        // -20 dBTP peak leaves plenty of headroom for a +4 dB gain.
        val result = resolve(LoudnessPreference.Normal, track(lufs = -18f, peak = 0.1f))

        assertEquals(4f, result.actualGainDb, tolerance)
        assertFalse(result.truePeakLimited)
    }

    @Test
    fun `peak protection applies after the gain cap`() {
        // Desired +16 dB caps to +6 dB, which still pushes a -6 dBTP peak over the limit.
        val result = resolve(LoudnessPreference.Normal, track(lufs = -30f, peak = 0.5f))

        assertTrue(result.capped)
        assertTrue(result.truePeakLimited)
        assertEquals(-1f, 20f * kotlin.math.log10(0.5f) + result.actualGainDb, 0.01f)
    }

    @Test
    fun `a non-positive peak is ignored rather than treated as silence`() {
        val result = resolve(LoudnessPreference.Normal, track(lufs = -18f, peak = 0f))

        assertEquals(4f, result.actualGainDb, tolerance)
        assertFalse(result.truePeakLimited)
    }

    // --- album context ------------------------------------------------------------------

    @Test
    fun `album context prefers album loudness`() {
        val data = track(lufs = -20f, albumLufs = -16f)
        val result = resolve(LoudnessPreference.Normal, data, isAlbumContext = true)

        assertEquals(2f, result.actualGainDb, tolerance)
    }

    @Test
    fun `album context falls back to track loudness when album data is missing`() {
        val data = track(lufs = -20f, albumLufs = null)
        val result = resolve(LoudnessPreference.Normal, data, isAlbumContext = true)

        assertEquals(6f, result.actualGainDb, tolerance)
    }

    @Test
    fun `track context ignores album loudness`() {
        val data = track(lufs = -20f, albumLufs = -16f)
        val result = resolve(LoudnessPreference.Normal, data, isAlbumContext = false)

        assertEquals(6f, result.actualGainDb, tolerance)
    }

    @Test
    fun `album context uses album peak for protection`() {
        val data = track(lufs = -18f, peak = 0.1f, albumLufs = -18f, albumPeak = 1.0f)
        val result = resolve(LoudnessPreference.Normal, data, isAlbumContext = true)

        assertEquals(-1f, result.actualGainDb, tolerance)
        assertTrue(result.truePeakLimited)
    }

    // --- linear conversion --------------------------------------------------------------

    @Test
    fun `linear gain conversion matches the dB scale`() {
        assertEquals(1f, LoudnessResolver.toLinearGain(0f), tolerance)
        assertEquals(2f, LoudnessResolver.toLinearGain(6.0206f), 0.001f)
        assertEquals(0.5f, LoudnessResolver.toLinearGain(-6.0206f), 0.001f)
    }
}
