package dev.aurora.player.domain.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A request to the AI Provider/Gateway.
 */
@Serializable
data class AiRequest(
    val query: String,
    val context: Map<String, String> = emptyMap(),
    val providerPreference: String = "gemini"
)

/**
 * A single structured tool call proposed by the AI.
 */
@Serializable
data class AiToolCall(
    val name: String,
    val args: JsonObject
)

/**
 * The response envelope from the AI Provider.
 */
@Serializable
data class AiResponse(
    val text: String,
    val toolCalls: List<AiToolCall> = emptyList()
)

/**
 * Provider-neutral interface for requesting intent resolution.
 */
interface AiProvider {
    suspend fun resolveIntent(request: AiRequest): Result<AiResponse>
}
