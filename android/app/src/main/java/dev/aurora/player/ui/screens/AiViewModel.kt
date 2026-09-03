package dev.aurora.player.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aurora.player.app.AiToolExecutor
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
    data class Error(val message: String) : AiUiState()
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
                _uiState.value = AiUiState.Error(e.message ?: "Failed to process AI request")
            }
        }
    }
}
