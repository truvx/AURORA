package dev.aurora.player.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * YouTube returns titles escaped for a web page; AURORA draws them into a Compose Text.
 *
 * The strings below are real ones seen on screen during a search for "keshi", printed
 * with their escapes showing.
 */
class HtmlTextTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun snippet(title: String): YouTubeSnippet =
        json.decodeFromString(
            YouTubeSnippet.serializer(),
            """{"title":"$title","channelTitle":"$title","thumbnails":{}}"""
        )

    @Test
    fun `an escaped apostrophe is an apostrophe`() {
        assertEquals("i swear i'll never leave again", HtmlEntities.decode("i swear i&#39;ll never leave again"))
        assertEquals("Tinubu's UNGA Absence", HtmlEntities.decode("Tinubu&#39;s UNGA Absence"))
    }

    @Test
    fun `the named entities YouTube actually sends`() {
        assertEquals("Rock & Roll", HtmlEntities.decode("Rock &amp; Roll"))
        assertEquals("\"Am I Wrong\"", HtmlEntities.decode("&quot;Am I Wrong&quot;"))
        assertEquals("<3", HtmlEntities.decode("&lt;3"))
        assertEquals("a > b", HtmlEntities.decode("a &gt; b"))
        assertEquals("it's", HtmlEntities.decode("it&apos;s"))
    }

    @Test
    fun `hexadecimal and decimal forms both work`() {
        assertEquals("it's", HtmlEntities.decode("it&#x27;s"))
        assertEquals("it's", HtmlEntities.decode("it&#39;s"))
        // Beyond the basic plane: an emoji in a title is one code point, not two chars.
        assertEquals("🎵", HtmlEntities.decode("&#127925;"))
    }

    @Test
    fun `decoding happens once, so a channel can still write an escaped ampersand`() {
        // "&amp;amp;" is how a title that wants to show "&amp;" arrives. One pass leaves
        // the text the channel meant; a second would eat it.
        assertEquals("&amp;", HtmlEntities.decode("&amp;amp;"))
    }

    @Test
    fun `an ampersand that is not an entity is left alone`() {
        assertEquals("Simon & Garfunkel", HtmlEntities.decode("Simon & Garfunkel"))
        assertEquals("R&B", HtmlEntities.decode("R&B"))
        assertEquals("A&", HtmlEntities.decode("A&"))
        // A semicolon far along the line is punctuation, not the end of an entity.
        assertEquals(
            "Me & you; nobody else",
            HtmlEntities.decode("Me & you; nobody else")
        )
        assertEquals("&unknown;", HtmlEntities.decode("&unknown;"))
        assertEquals("&#;", HtmlEntities.decode("&#;"))
    }

    @Test
    fun `text without an ampersand is returned unchanged`() {
        val plain = "keshi - Soft Spot (Official Music Video)"
        assertEquals(plain, HtmlEntities.decode(plain))
        assertEquals("", HtmlEntities.decode(""))
    }

    @Test
    fun `a snippet off the wire arrives decoded`() {
        val decoded = snippet("i swear i&#39;ll never leave again")

        assertEquals("i swear i'll never leave again", decoded.title)
        assertEquals("i swear i'll never leave again", decoded.channelTitle)
    }

    @Test
    fun `a snippet built in Kotlin keeps exactly what was written`() {
        // Escaping is a property of the wire format, not of the value, so a fixture that
        // says "&amp;" means an ampersand entity and must not be quietly rewritten.
        val built = YouTubeSnippet(title = "Rock &amp; Roll", thumbnails = YouTubeThumbnails())

        assertEquals("Rock &amp; Roll", built.title)
    }
}
