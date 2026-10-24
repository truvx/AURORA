package dev.aurora.player.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading an LRC file.
 *
 * The timestamps are the whole point: a lyric a second out of place is worse than no lyric,
 * because the listener trusts it and then reads the wrong line.
 */
class LrcParserTest {

    @Test
    fun `centiseconds and milliseconds are not read as the same number`() {
        // "[00:12.34]" is 12.34s. Reading "34" as milliseconds would put it at 12.034s.
        val lines = LrcParser.parse("[00:12.34]first\n[00:13.456]second")

        assertEquals(listOf(12_340L, 13_456L), lines.map { it.startMs })
    }

    @Test
    fun `a line with no fraction starts on the second`() {
        assertEquals(listOf(72_000L), LrcParser.parse("[01:12]a line").map { it.startMs })
    }

    @Test
    fun `a repeated refrain carries every one of its timestamps`() {
        val lines = LrcParser.parse("[00:10.00][01:20.00][02:30.00]chorus")

        assertEquals(3, lines.size)
        assertTrue(lines.all { it.text == "chorus" })
        assertEquals(listOf(10_000L, 80_000L, 150_000L), lines.map { it.startMs })
    }

    @Test
    fun `metadata tags are not lyrics`() {
        // "[ar: keshi]" describes the file. Printing it mid-chorus is not a lyric.
        val lines = LrcParser.parse("[ar: keshi]\n[ti: Soft Spot]\n[00:05.00]the first line")

        assertEquals(listOf("the first line"), lines.map { it.text })
    }

    @Test
    fun `lines come back in time order however the file was written`() {
        val lines = LrcParser.parse("[00:30.00]third\n[00:10.00]first\n[00:20.00]second")

        assertEquals(listOf("first", "second", "third"), lines.map { it.text })
    }

    @Test
    fun `nothing usable comes back empty rather than as a guess`() {
        assertEquals(emptyList<LyricLine>(), LrcParser.parse("just some words\n\n"))
    }

    @Test
    fun `the active line is the last one that has started`() {
        val lines = LrcParser.parse("[00:00.00]a\n[00:10.00]b\n[00:20.00]c")

        assertEquals(-1, LrcParser.activeIndex(LrcParser.parse("[00:05.00]a"), 1_000))
        assertEquals(0, LrcParser.activeIndex(lines, 0))
        assertEquals(0, LrcParser.activeIndex(lines, 9_999))
        assertEquals(1, LrcParser.activeIndex(lines, 10_000))
        assertEquals(2, LrcParser.activeIndex(lines, 900_000))
    }

    @Test
    fun `an empty file has no active line`() {
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 5_000))
    }
}
