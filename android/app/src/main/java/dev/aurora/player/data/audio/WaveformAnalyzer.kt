package dev.aurora.player.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dev.aurora.player.domain.audio.Waveform
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

/**
 * Measures the shape of a local file by decoding it.
 *
 * There is no shortcut. A container's tags do not carry amplitude, and the only way to know
 * how loud a recording is at two minutes in is to decode it and look. So the file is run
 * through the platform decoder once, its samples bucketed by time, and the answer written
 * down - the work happens once per track, ever, and never on the main thread.
 *
 * This is for local files only, and deliberately takes a [Uri] the user already granted
 * rather than anything a provider hands over. AURORA does not have YouTube's audio and
 * must not pretend to measure it.
 */
class WaveformAnalyzer(
    private val context: Context,
    /** Bars are 3dp wide; a phone shows around sixty. Enough to downsample from. */
    private val bucketCount: Int = DEFAULT_BUCKETS,
) {

    /** Null when the file cannot be read, has no audio, or holds no sound at all. */
    fun analyze(uri: Uri): Waveform? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = audioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION)
                ?.takeIf { it > 0 }
                ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            decode(extractor, codec, durationUs)
                ?.let { Waveform.normalized(it.toList()) }
                ?.takeUnless { it.isEmpty }
        } catch (error: Exception) {
            // A malformed file, an unsupported codec, a revoked permission. Artwork of the
            // sound is decoration: failing to measure it must never look like a failure to
            // play, so this reports "no shape" and the scrubber draws its plain bars.
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    /**
     * Decodes the file into peaks, or gives up.
     *
     * Null on the deadline rather than a short waveform: the buckets past the point it
     * stopped are still zero, and drawing those is the picture of a track that falls
     * silent halfway. Null is already the "no waveform" answer the scrubber knows how to
     * draw - plain bars, meaning unknown - and unknown is what this is.
     */
    private fun decode(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long,
    ): FloatArray? {
        val peaks = FloatArray(bucketCount)
        val info = MediaCodec.BufferInfo()
        var sawInputEnd = false
        var sawOutputEnd = false

        /*
         * A wall clock on the whole decode.
         *
         * The loop's only exits were the codec reporting end-of-stream or reporting
         * nothing more after the input ended. A codec that does neither - a truncated
         * file, a stream whose declared length outlives its data - left this spinning on
         * a worker with a hardware decoder open, and because the repository serialises
         * decoding behind a mutex, one such file took every later track's waveform with
         * it for the rest of the session.
         */
        val deadline = android.os.SystemClock.elapsedRealtime() + DECODE_BUDGET_MS

        while (!sawOutputEnd) {
            if (android.os.SystemClock.elapsedRealtime() > deadline) return null
            if (!sawInputEnd) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)
                    val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEnd = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outputIndex >= 0) {
                val output = codec.getOutputBuffer(outputIndex)
                if (output != null && info.size > 0) {
                    accumulate(output, info, durationUs, peaks)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEnd = true
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && sawInputEnd) {
                // The decoder has everything and has stopped producing: nothing more is coming.
                break
            }
        }
        return peaks
    }

    /**
     * The loudest sample in this buffer, into the bucket its timestamp falls in.
     *
     * The peak rather than the mean, because the mean of a snare and the silence around it
     * is neither, and every drum track would draw as one flat line.
     */
    private fun accumulate(
        output: ByteBuffer,
        info: MediaCodec.BufferInfo,
        durationUs: Long,
        peaks: FloatArray,
    ) {
        val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * bucketCount)
            .toInt()
            .coerceIn(0, peaks.lastIndex)

        val samples = output.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var loudest = 0f
        // Stepping: a buffer holds thousands of samples and the peak of every fourth is the
        // same picture for a quarter of the work, on a path that runs for a whole file.
        var index = 0
        val limit = min(samples.limit(), info.size / 2)
        while (index < limit) {
            val value = abs(samples.get(index).toInt()) / Short.MAX_VALUE.toFloat()
            if (value > loudest) loudest = value
            index += SAMPLE_STEP
        }
        if (loudest > peaks[bucket]) peaks[bucket] = loudest
    }

    private companion object {
        const val DEFAULT_BUCKETS = 400
        const val TIMEOUT_US = 10_000L

        /**
         * How long the whole decode may take before it is abandoned.
         *
         * Generous: a long track on a slow phone decodes well inside it. It is a bound on
         * a loop that otherwise has none, not a performance target.
         */
        const val DECODE_BUDGET_MS = 15_000L
        const val SAMPLE_STEP = 4
    }
}
