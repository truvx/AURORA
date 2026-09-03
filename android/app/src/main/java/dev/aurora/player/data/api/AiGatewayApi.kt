package dev.aurora.player.data.api

import dev.aurora.player.domain.ai.AiRequest
import dev.aurora.player.domain.ai.AiResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AiGatewayApi {
    @POST("api/ai/intent")
    suspend fun resolveIntent(@Body request: AiRequest): AiResponse
}
