package dev.aurora.player.data.player.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses the raw response from YouTube's `api/jnn/v1/Create` endpoint into a JSON object
 * that can be embedded directly into a JavaScript snippet for `runBotGuard()`.
 *
 * The response is a JSON array.  The first element may be:
 * - a nested JSON array (unscrambled challenge), or
 * - a base64-encoded string (scrambled challenge) that must be descrambled first.
 *
 * The resulting challenge array contains (by index):
 *   [0]  messageId
 *   [1]  interpreterJavascript array (or null)
 *   [2]  interpreterTrustedResourceUrl array (or null)
 *   [3]  interpreterHash
 *   [4]  program (base64)
 *   [5]  globalName
 *   [6]  (unknown)
 *   [7]  clientExperimentsStateBlob
 */
fun parseCreateChallenge(rawResponse: String): String {
    val element = json.parseToJsonElement(rawResponse)

    // YouTube's Create endpoint returns either a bare JSON array or an object with a
    // "scrambledChallenge" field holding a base64-encoded challenge array. Handle both.
    val outer: JsonArray
    val alreadyUnscrambled: Boolean
    when (element) {
        is JsonArray -> {
            outer = element
            alreadyUnscrambled = false
        }
        is JsonObject -> {
            val scrambled = element["scrambledChallenge"]?.jsonPrimitive?.content
            if (scrambled != null) {
                // Scrambled: base64-decode then add 97 to each byte — gives the challenge array directly.
                outer = json.parseToJsonElement(descramble(scrambled)).jsonArray
                alreadyUnscrambled = true
            } else {
                val arr = element.entries
                    .mapNotNull { (_, v) -> v as? JsonArray }
                    .firstOrNull()
                    ?: throw PoTokenException("Create response is an object with no array field: ${rawResponse.take(200)}")
                outer = arr
                alreadyUnscrambled = false
            }
        }
        else -> throw PoTokenException("Create response is neither array nor object: ${rawResponse.take(200)}")
    }

    val challenge = if (alreadyUnscrambled) {
        outer
    } else if (outer.size > 1 && outer[1].jsonPrimitive.isString) {
        // Scrambled: base64-decode then add 97 to each byte.
        val decoded = descramble(outer[1].jsonPrimitive.content)
        json.parseToJsonElement(decoded).jsonArray
    } else {
        outer[0].jsonArray
    }

    val program = challenge[4].jsonPrimitive.content
    val globalName = challenge[5].jsonPrimitive.content

    val interpreterJs = challenge[1]
        .takeIf { it !is JsonNull }
        ?.jsonArray
        ?.firstOrNull { it.jsonPrimitive.isString }

    val interpreterUrl = challenge[2]
        .takeIf { it !is JsonNull }
        ?.jsonArray
        ?.firstOrNull { it.jsonPrimitive.isString }

    return json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "program" to JsonPrimitive(program),
                "globalName" to JsonPrimitive(globalName),
                "interpreterJavascript" to JsonObject(
                    mapOf(
                        "privateDoNotAccessOrElseSafeScriptWrappedValue" to (interpreterJs ?: JsonNull),
                        "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to (interpreterUrl ?: JsonNull),
                    )
                ),
            )
        )
    )
}

/**
 * Parses the raw response from YouTube's `api/jnn/v1/GenerateIT` endpoint.
 *
 * Returns a pair of:
 * - A JavaScript `Uint8Array(...)` string representation of the integrity token
 * - The token's lifetime in seconds
 */
fun parseIntegrityToken(rawResponse: String): Pair<String, Long> {
    val obj = json.parseToJsonElement(rawResponse) as JsonObject
    val tokenU8 = base64ToJsUint8Array(obj["integrityToken"]?.jsonPrimitive?.content
        ?: throw PoTokenException("GenerateIT response missing integrityToken: ${rawResponse.take(200)}"))
    val lifetimeSeconds = obj["estimatedTtlSecs"]?.jsonPrimitive?.long
        ?: throw PoTokenException("GenerateIT response missing estimatedTtlSecs: ${rawResponse.take(200)}")
    return tokenU8 to lifetimeSeconds
}

/**
 * Converts a plain-string identifier to a JavaScript `Uint8Array(...)` literal.
 */
fun stringToJsUint8Array(identifier: String): String {
    val bytes = identifier.toByteArray(Charsets.UTF_8)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

/**
 * Converts a comma-separated byte list (output of `Uint8Array.toString()` in JS)
 * to the YouTube-specific base64 encoding used for PoTokens.
 */
fun commaSeparatedBytesToBase64(commaBytes: String): String {
    val bytes = commaBytes.split(",")
        .map { it.trim().toInt().toByte() }
        .toByteArray()
    return java.util.Base64.getEncoder()
        .encodeToString(bytes)
        .replace('+', '-')
        .replace('/', '_')
        .replace('.', '=')
        .trimEnd('=')
}

// --- internal helpers ---

private fun descramble(base64Payload: String): String {
    return base64ToByteArray(base64Payload)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()
}

private fun base64ToJsUint8Array(base64: String): String {
    val bytes = base64ToByteArray(base64)
    return "new Uint8Array([${bytes.joinToString(",") { (it.toInt() and 0xFF).toString() }}])"
}

private fun base64ToByteArray(base64: String): ByteArray {
    val normalised = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')
    return java.util.Base64.getDecoder().decode(normalised)
        .takeIf { it.isNotEmpty() }
        ?: throw PoTokenException("Cannot decode base64: ${base64.take(40)}…")
}
