package dev.aurora.player.domain.lyrics

/**
 * The words of a song, and when each one is sung.
 *
 * Provider-neutral: this is what a lyrics surface renders, whatever answered the question.
 * AURORA never derives these from a video's captions or its description - a caption track
 * is a transcript of a video, not the lyric of a recording, and reading one as the other is
 * how a player ends up printing "[Music]" and an uploader's sign-off as verse.
 */
sealed interface Lyrics {

    /** Timed lines, from an LRC file. The only kind that can follow playback. */
    data class Synced(val lines: List<LyricLine>) : Lyrics

    /** The words with no timing. Shown as a block, and cannot be followed. */
    data class Plain(val text: String) : Lyrics

    /** The source says this recording has no words. Different from having found nothing. */
    data object Instrumental : Lyrics
}

/** One line, and the moment it starts. */
data class LyricLine(val startMs: Long, val text: String)

/**
 * Reads an LRC file into timed lines.
 *
 * Only the timestamp form is understood - `[mm:ss.xx]`, optionally several on one line for
 * a repeated refrain. The metadata tags an LRC may carry (`[ar:]`, `[ti:]`, `[by:]`) are
 * deliberately dropped: they describe the file, and printing "by: someone" in the middle of
 * a chorus is not a lyric.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = TIMESTAMP.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                val minutes = stamp.groupValues[1].toLongOrNull() ?: continue
                val seconds = stamp.groupValues[2].toLongOrNull() ?: continue
                val fraction = stamp.groupValues[3]
                // "12" is centiseconds, "123" is milliseconds. Reading both as the same
                // number would put a line a second out of place on half the files.
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                lines += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, text)
            }
        }
        return lines.sortedBy { it.startMs }
    }

    /**
     * Which line is being sung at [positionMs], or -1 before the first one starts.
     *
     * Binary search rather than a scan: this is asked on every position tick, and a song
     * with a thousand timestamps would otherwise cost a thousand comparisons a frame.
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines.first().startMs) return -1
        var low = 0
        var high = lines.lastIndex
        var found = 0
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}
