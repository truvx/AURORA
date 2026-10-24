package dev.aurora.player.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Text the YouTube Data API returns HTML-escaped.
 *
 * `snippet.title` and `snippet.channelTitle` arrive escaped for insertion into a web page:
 * an apostrophe is `&#39;`, an ampersand is `&amp;`. AURORA draws them into a Compose Text,
 * which is not HTML, so they were shown literally - "i swear i&#39;ll never leave again"
 * under a track.
 *
 * Decoding belongs here, at the boundary where the wire format stops being the wire
 * format, rather than at each of the seven places a title is read.
 */
internal object HtmlEntities {

    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )

    /**
     * Decodes named and numeric entities, once.
     *
     * Once, not repeatedly: a title legitimately containing the text "&amp;" - which is
     * how a channel writes an ampersand it wants shown - would be eaten by a second pass.
     * Anything that is not a recognised entity is left exactly as it was, because a lone
     * "&" or a "<3" in a title is a character, not markup.
     */
    fun decode(value: String): String {
        if ('&' !in value) return value

        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '&') {
                out.append(char)
                index++
                continue
            }

            val end = value.indexOf(';', index + 1)
            // An entity is short; a distant semicolon is punctuation further along the line.
            if (end == -1 || end - index > MAX_ENTITY_LENGTH) {
                out.append(char)
                index++
                continue
            }

            val body = value.substring(index + 1, end)
            val replacement = when {
                body.startsWith("#x", ignoreCase = true) ->
                    body.drop(2).toIntOrNull(16)?.let(::codePoint)
                body.startsWith("#") ->
                    body.drop(1).toIntOrNull()?.let(::codePoint)
                else -> NAMED[body]
            }

            if (replacement == null) {
                out.append(char)
                index++
            } else {
                out.append(replacement)
                index = end + 1
            }
        }
        return out.toString()
    }

    /** Null for anything outside Unicode, so a malformed entity stays as written. */
    private fun codePoint(value: Int): String? =
        if (value in 1..0x10FFFF) String(Character.toChars(value)) else null

    private const val MAX_ENTITY_LENGTH = 10
}

/**
 * Applied to the fields that arrive escaped.
 *
 * On the field rather than on the whole class so it runs only when JSON is parsed: a test
 * that builds a snippet in Kotlin keeps exactly the string it wrote, because escaping is a
 * property of the wire format and not of the value.
 */
internal object HtmlTextSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("HtmlText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = HtmlEntities.decode(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
