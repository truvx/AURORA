package dev.aurora.player.data.scanner

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Raw ReplayGain values as stored in a file's tags.
 *
 * Gains are in dB relative to the ReplayGain reference level; peaks are linear sample
 * values where 1.0 is full scale. A missing tag stays null - it is never defaulted.
 */
data class ReplayGainTags(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null
) {
    val hasAnyValue: Boolean
        get() = trackGainDb != null || trackPeak != null || albumGainDb != null || albumPeak != null
}

/**
 * Reads ReplayGain tags that are already present in a file.
 *
 * This reader never estimates, interpolates, or invents loudness. A file without usable
 * tags returns null so the rest of the pipeline can treat its loudness as genuinely
 * unknown, per the truthful-metadata rule in docs/LOUDNESS_POLICY_FINAL.md.
 *
 * Supported containers:
 *  - FLAC (Vorbis comment block)
 *  - ID3v2.3 / ID3v2.4 (TXXX frames, as used by MP3 and friends)
 *
 * Ogg/Opus and MP4/M4A carry ReplayGain in formats this reader does not parse yet; they
 * return null (unknown) rather than a guess.
 */
object ReplayGainReader {

    private const val MAX_BLOCK_BYTES = 1 shl 20
    private const val MAX_COMMENT_COUNT = 4096

    private const val KEY_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    private const val KEY_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    private const val KEY_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    private const val KEY_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"

    private val NUMBER = Regex("[-+]?[0-9]*\\.?[0-9]+")

    private val LATIN1: Charset = Charset.forName("ISO-8859-1")
    private val UTF16: Charset = Charset.forName("UTF-16")
    private val UTF16BE: Charset = Charset.forName("UTF-16BE")

    /**
     * Parses tags from [source]. Returns null when the container is unsupported, the tags
     * are absent, or the data is malformed. Reads only the header region of the file.
     */
    fun read(source: InputStream): ReplayGainTags? {
        val input = BufferedInputStream(source, DEFAULT_BUFFER_SIZE)
        val magic = input.readExactly(4) ?: return null

        return when {
            magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() &&
                magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte() -> readFlac(input)

            magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() &&
                magic[2] == '3'.code.toByte() -> readId3(input, majorVersion = magic[3].toInt() and 0xFF)

            else -> null
        }?.takeIf { it.hasAnyValue }
    }

    // --- FLAC -------------------------------------------------------------------------

    private fun readFlac(input: InputStream): ReplayGainTags? {
        while (true) {
            val header = input.readExactly(4) ?: return null
            val isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)

            if (length < 0 || length > MAX_BLOCK_BYTES) return null

            if (type == 4) {
                val block = input.readExactly(length) ?: return null
                return parseVorbisComments(block)
            }

            if (!input.skipExactly(length.toLong())) return null
            if (isLast) return null
        }
    }

    private fun parseVorbisComments(block: ByteArray): ReplayGainTags? {
        var offset = 0

        fun u32le(): Int? {
            if (offset + 4 > block.size) return null
            val value = (block[offset].toInt() and 0xFF) or
                ((block[offset + 1].toInt() and 0xFF) shl 8) or
                ((block[offset + 2].toInt() and 0xFF) shl 16) or
                ((block[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return value
        }

        val vendorLength = u32le() ?: return null
        if (vendorLength < 0 || offset + vendorLength > block.size) return null
        offset += vendorLength

        val count = u32le() ?: return null
        if (count < 0 || count > MAX_COMMENT_COUNT) return null

        var tags = ReplayGainTags()
        repeat(count) {
            val length = u32le() ?: return tags
            if (length < 0 || offset + length > block.size) return tags
            val comment = String(block, offset, length, Charsets.UTF_8)
            offset += length

            val separator = comment.indexOf('=')
            if (separator > 0) {
                tags = tags.withTag(
                    key = comment.substring(0, separator),
                    value = comment.substring(separator + 1)
                )
            }
        }
        return tags
    }

    // --- ID3v2 ------------------------------------------------------------------------

    private fun readId3(input: InputStream, majorVersion: Int): ReplayGainTags? {
        // Only 2.3 and 2.4 are parsed; 2.2 uses a different frame layout.
        if (majorVersion != 3 && majorVersion != 4) return null

        val rest = input.readExactly(6) ?: return null
        val flags = rest[1].toInt() and 0xFF

        // Unsynchronisation rewrites the byte stream; refuse rather than misread numbers.
        if ((flags and 0x80) != 0) return null

        val tagSize = synchsafe(rest, 2) ?: return null
        if (tagSize <= 0 || tagSize > MAX_BLOCK_BYTES) return null

        val body = input.readExactly(tagSize) ?: return null
        var offset = 0

        // Skip the extended header when present.
        if ((flags and 0x40) != 0) {
            if (offset + 4 > body.size) return null
            val extendedSize = if (majorVersion == 4) {
                synchsafe(body, offset) ?: return null
            } else {
                u32be(body, offset) + 4
            }
            if (extendedSize < 0 || offset + extendedSize > body.size) return null
            offset += extendedSize
        }

        var tags = ReplayGainTags()
        while (offset + 10 <= body.size) {
            if (body[offset].toInt() == 0) break // padding

            val frameId = String(body, offset, 4, LATIN1)
            val frameSize = if (majorVersion == 4) {
                synchsafe(body, offset + 4) ?: break
            } else {
                u32be(body, offset + 4)
            }
            offset += 10

            if (frameSize < 0 || offset + frameSize > body.size) break

            if (frameId == "TXXX") {
                val (description, value) = parseTxxx(body, offset, frameSize) ?: (null to null)
                if (description != null && value != null) {
                    tags = tags.withTag(description, value)
                }
            }
            offset += frameSize
        }
        return tags
    }

    private fun parseTxxx(body: ByteArray, start: Int, size: Int): Pair<String?, String?>? {
        if (size < 2) return null
        val encoding = body[start].toInt() and 0xFF
        val contentStart = start + 1
        val contentEnd = start + size

        return when (encoding) {
            0, 3 -> {
                val charset = if (encoding == 0) LATIN1 else Charsets.UTF_8
                var terminator = -1
                for (i in contentStart until contentEnd) {
                    if (body[i].toInt() == 0) {
                        terminator = i
                        break
                    }
                }
                if (terminator < 0) return null
                val description = String(body, contentStart, terminator - contentStart, charset)
                val value = String(body, terminator + 1, contentEnd - terminator - 1, charset)
                description to value
            }

            1, 2 -> {
                val charset = if (encoding == 1) UTF16 else UTF16BE
                var terminator = -1
                var i = contentStart
                while (i + 1 < contentEnd) {
                    if (body[i].toInt() == 0 && body[i + 1].toInt() == 0) {
                        terminator = i
                        break
                    }
                    i += 2
                }
                if (terminator < 0) return null
                val description = String(body, contentStart, terminator - contentStart, charset)
                val value = String(body, terminator + 2, contentEnd - terminator - 2, charset)
                description to value
            }

            else -> null
        }
    }

    // --- shared -----------------------------------------------------------------------

    private fun ReplayGainTags.withTag(key: String, value: String): ReplayGainTags =
        when (key.trim().uppercase()) {
            KEY_TRACK_GAIN -> copy(trackGainDb = parseGain(value) ?: trackGainDb)
            KEY_TRACK_PEAK -> copy(trackPeak = parsePeak(value) ?: trackPeak)
            KEY_ALBUM_GAIN -> copy(albumGainDb = parseGain(value) ?: albumGainDb)
            KEY_ALBUM_PEAK -> copy(albumPeak = parsePeak(value) ?: albumPeak)
            else -> this
        }

    /** Accepts the conventional "-6.48 dB" form as well as a bare number. */
    private fun parseGain(raw: String): Float? {
        val value = NUMBER.find(raw.trim())?.value?.toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }

    /** Peaks are linear; a non-positive or non-finite peak carries no information. */
    private fun parsePeak(raw: String): Float? {
        val value = NUMBER.find(raw.trim())?.value?.toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() && it > 0f }
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        var result = 0
        for (i in 0 until 4) {
            val byte = bytes[offset + i].toInt() and 0xFF
            if ((byte and 0x80) != 0) return null // not a valid synchsafe integer
            result = (result shl 7) or byte
        }
        return result
    }

    private fun u32be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun InputStream.readExactly(count: Int): ByteArray? {
        if (count < 0 || count > MAX_BLOCK_BYTES) return null
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(buffer, read, count - read)
            if (n < 0) return null
            read += n
        }
        return buffer
    }

    private fun InputStream.skipExactly(count: Long): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) return false
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
        return true
    }
}
