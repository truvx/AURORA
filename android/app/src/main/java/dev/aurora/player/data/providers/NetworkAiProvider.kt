package dev.aurora.player.data.providers

import dev.aurora.player.data.api.AiGatewayApi
import dev.aurora.player.domain.ai.AiProvider
import dev.aurora.player.domain.ai.AiRequest
import dev.aurora.player.domain.ai.AiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NetworkAiProvider(
    private val api: AiGatewayApi
) : AiProvider {
    override suspend fun resolveIntent(request: AiRequest): Result<AiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.resolveIntent(request)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
