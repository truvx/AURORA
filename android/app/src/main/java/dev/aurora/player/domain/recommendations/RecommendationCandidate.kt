package dev.aurora.player.domain.recommendations

import dev.aurora.player.domain.models.MediaItem

/**
 * A scored candidate for recommendation.
 */
data class RecommendationCandidate(
    val item: MediaItem,
    val score: Float,
    val reason: String? = null
)
