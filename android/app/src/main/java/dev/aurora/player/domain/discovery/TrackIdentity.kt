package dev.aurora.player.domain.discovery

/**
 * What a YouTube video is actually a recording of.
 *
 * A video title is not a song. "keshi - Soft Spot (Official Music Video)" is one string
 * doing three jobs, and until it is taken apart AURORA cannot look the record up, cannot
 * group it with anything, and shows the production credits under the player as though they
 * were the name of the song.
 */
data class TrackIdentity(
    val artist: String,
    val title: String,
    /**
     * Whether the split came from real structure rather than a guess.
     *
     * An unconfident identity is still worth showing, but it is not worth spending a cover
     * lookup on, and it is what the AI is asked about.
     */
    val confident: Boolean,
) {
    /** How to ask a music database about this recording. */
    val searchQuery: String get() = "$artist $title"
}

object TrackIdentifier {

    /**
     * Bracketed asides that describe the video rather than the song.
     *
     * A deliberately closed list, not "strip every bracket": "Everything In Its Right Place
     * (Live)" and "Redbone (Childish Gambino Cover)" are part of what the recording is, and
     * throwing away every parenthesis loses them.
     */
    private val NOISE = Regex(
        """^\s*(?:""" +
            """official\s+)?(?:music\s+|hd\s+|4k\s+|full\s+)?""" +
            """(?:video|audio|visuali[sz]er|lyric\s+video|lyrics?|m/?v|""" +
            """official|hd|4k|1080p|720p|explicit|color\s+coded\s+lyrics?|""" +
            """with\s+lyrics|letra|sub\s+espa[nñ]ol)""" +
            """\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** YouTube generates these for an artist's catalogue; the channel is the artist. */
    private const val TOPIC_SUFFIX = " - Topic"

    private val SEPARATORS = listOf(" - ", " – ", " — ", " ~ ")

    /**
     * Splits a video into an artist and a song.
     *
     * Returns null only when there is nothing usable at all. Everything else comes back
     * with [TrackIdentity.confident] saying how much to trust it.
     */
    fun identify(videoTitle: String, channelTitle: String?): TrackIdentity? {
        val cleaned = stripNoise(videoTitle).trim()
        if (cleaned.isEmpty()) return null

        val topicArtist = channelTitle
            ?.takeIf { it.endsWith(TOPIC_SUFFIX, ignoreCase = true) }
            ?.dropLast(TOPIC_SUFFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val separator = SEPARATORS.firstOrNull { cleaned.contains(it) }
        if (separator != null) {
            val artist = cleaned.substringBefore(separator).trim()
            val title = cleaned.substringAfter(separator).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) {
                // "keshi - Soft Spot" on the "keshi - Topic" channel: the two agree, which
                // is as sure as this gets without asking anyone.
                return TrackIdentity(artist = artist, title = title, confident = true)
            }
        }

        // No separator, but YouTube says whose catalogue it is.
        if (topicArtist != null) {
            return TrackIdentity(artist = topicArtist, title = cleaned, confident = true)
        }

        // A channel name is not an artist - "KeshiVEVO" and "Morris C." are labels and
        // uploaders - so this is offered without confidence rather than asserted.
        val channel = channelTitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return TrackIdentity(artist = channel, title = cleaned, confident = false)
    }

    /**
     * Removes the parts of a title that describe the upload.
     *
     * Bracketed asides matching the noise list, and anything after a pipe, which on YouTube
     * is where uploaders put their own name.
     */
    fun stripNoise(title: String): String {
        var result = title.substringBefore('|').trim().ifEmpty { title.trim() }

        // Repeatedly, because a title can carry several: "(Official Video) [4K]".
        var previous: String
        do {
            previous = result
            result = Regex("""\s*[\(\[]([^\)\]]*)[\)\]]\s*""").replace(result) { match ->
                if (NOISE.containsMatchIn(match.groupValues[1])) " " else match.value
            }.trim()
        } while (result != previous)

        return result.trim().trim('-', '–', '—', '~').trim()
    }
}
