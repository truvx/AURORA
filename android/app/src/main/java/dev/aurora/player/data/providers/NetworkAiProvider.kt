package dev.aurora.player.data.providers

import dev.aurora.player.data.api.AiGatewayApi
import dev.aurora.player.domain.ai.AiFailureReason
import dev.aurora.player.domain.ai.AiGatewayException
import dev.aurora.player.domain.ai.AiProvider
import dev.aurora.player.domain.ai.AiRequest
import dev.aurora.player.domain.ai.AiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

class NetworkAiProvider(
    private val api: AiGatewayApi
) : AiProvider {
    override suspend fun resolveIntent(request: AiRequest): Result<AiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(api.resolveIntent(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Result.failure(AiGatewayException(reasonFor(e.code(), e.response()?.errorBody()?.string()), e))
            } catch (e: IOException) {
                Result.failure(AiGatewayException(AiFailureReason.Unreachable, e))
            } catch (e: Exception) {
                Result.failure(AiGatewayException(AiFailureReason.Unexpected, e))
            }
        }
    }

    internal companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The gateway's `error.code` is authoritative; the HTTP status is the fallback for a
         * body that is missing or not the gateway's (a proxy page, say).
         */
        fun reasonFor(status: Int, body: String?): AiFailureReason {
            val code = body?.let {
                runCatching {
                    json.parseToJsonElement(it).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
                }.getOrNull()
            }
            if (code != null) return AiFailureReason.fromGatewayCode(code)
            return when (status) {
                401 -> AiFailureReason.NotConnected
                429 -> AiFailureReason.RateLimited
                503 -> AiFailureReason.ProviderBusy
                else -> AiFailureReason.Unexpected
            }
        }
    }
}
