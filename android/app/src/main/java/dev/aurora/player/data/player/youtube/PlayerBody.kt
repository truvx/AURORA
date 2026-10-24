package dev.aurora.player.data.player.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `youtubei/v1/player`.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/models/body/PlayerBody.kt`.
 */
@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String
    )
}
