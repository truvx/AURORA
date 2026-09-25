package dev.aurora.player.data.audio

import android.net.Uri
import dev.aurora.player.data.db.LocalLibraryDao
import dev.aurora.player.data.db.TrackWaveformEntity
import dev.aurora.player.domain.audio.Waveform
import dev.aurora.player.domain.audio.WaveformCodec
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The shape of a track, measured once and remembered.
 *
 * Only local files have one. A YouTube track returns null and must keep returning null:
 * AURORA never receives that audio, so there is nothing to measure, and the scrubber's
 * plain bars are the honest drawing of "unknown" rather than a gap to be filled.
 *
 * Decoding is serialised. Two tracks analysed at once would have two hardware decoders
 * open while one of them is also playing, which on a mid-range phone is how playback
 * starts to stutter - and the answer is wanted in a second or two, not in a hurry.
 */
class WaveformRepository(
    private val dao: LocalLibraryDao,
    private val analyzer: WaveformAnalyzer,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    private val decoding = Mutex()

    suspend fun waveformFor(track: MediaItem): Waveform? {
        if (track.provider != ProviderKind.LOCAL) return null

        withContext(io) { dao.getTrackWaveform(track.id) }
            ?.takeIf { it.analysisVersion == ANALYSIS_VERSION }
            ?.let { return WaveformCodec.decode(it.peaks) }

        val uri = withContext(io) { dao.getLocalFileForMedia(track.id)?.uri }
            ?.let(Uri::parse)
            ?: return null
        val measured = decoding.withLock {
            withContext(io) { analyzer.analyze(uri) }
        } ?: return null

        withContext(io) {
            dao.insertTrackWaveform(
                TrackWaveformEntity(
                    mediaId = track.id,
                    peaks = WaveformCodec.encode(measured),
                    analysisVersion = ANALYSIS_VERSION,
                )
            )
        }
        return measured
    }

    private companion object {
        /** Bump when the measurement changes, so old shapes are re-measured rather than kept. */
        const val ANALYSIS_VERSION = 1
    }
}
