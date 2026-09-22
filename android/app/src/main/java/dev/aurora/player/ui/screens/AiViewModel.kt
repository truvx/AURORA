package dev.aurora.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.app.AiToolExecutor
import dev.aurora.player.domain.ai.AiFailureReason
import dev.aurora.player.domain.ai.AiGatewayException
import dev.aurora.player.domain.ai.AiProvider
import dev.aurora.player.domain.ai.AiRequest
import dev.aurora.player.domain.recommendations.RecommendationCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AiUiState {
    object Idle : AiUiState()
    object Loading : AiUiState()
    data class Success(val candidates: List<RecommendationCandidate>, val message: String) : AiUiState()

    /** [opensSettings] is true when the fix is on this device: connecting the gateway. */
    data class Error(val message: String, val opensSettings: Boolean = false) : AiUiState()
}

/** What to tell the person, per failure. Each says what happened and what to do next. */
internal fun errorFor(reason: AiFailureReason): AiUiState.Error = when (reason) {
    AiFailureReason.NotConnected -> AiUiState.Error(
        "AURORA AI isn't connected. Add your gateway token in Settings.",
        opensSettings = true
    )
    AiFailureReason.GatewayNotConfigured ->
        AiUiState.Error("The AI gateway isn't set up yet: it has no access token configured.")
    AiFailureReason.AiNotConfigured ->
        AiUiState.Error("The AI gateway has no provider key set up.")
    AiFailureReason.RateLimited ->
        AiUiState.Error("That's a lot of requests in a short time. Try again in a minute.")
    AiFailureReason.ProviderBusy ->
        AiUiState.Error("The AI service is busy right now. Try again shortly.")
    AiFailureReason.Unreachable ->
        AiUiState.Error("Couldn't reach the AI gateway. Check your connection.")
    AiFailureReason.Unexpected ->
        AiUiState.Error("Something went wrong with that request. Try again.")
}

class AiViewModel(
    private val aiProvider: AiProvider,
    private val aiToolExecutor: AiToolExecutor
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    fun submitQuery(query: String) {
        if (query.isBlank()) return
        
        _uiState.value = AiUiState.Loading
        viewModelScope.launch {
            val result = aiProvider.resolveIntent(AiRequest(query = query))
            
            result.onSuccess { response ->
                val allCandidates = mutableListOf<RecommendationCandidate>()
                // Execute each tool call
                for (toolCall in response.toolCalls) {
                    val candidates = aiToolExecutor.executeTool(toolCall)
                    allCandidates.addAll(candidates)
                }
                
                _uiState.value = AiUiState.Success(
                    candidates = allCandidates.distinctBy { it.item.id },
                    message = response.text
                )
            }.onFailure { e ->
                // Never the raw exception text: that was "HTTP 500" for every failure.
                _uiState.value = errorFor((e as? AiGatewayException)?.reason ?: AiFailureReason.Unexpected)
            }
        }
    }
}
