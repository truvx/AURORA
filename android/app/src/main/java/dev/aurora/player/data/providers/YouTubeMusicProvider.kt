package dev.aurora.player.data.providers

import dev.aurora.player.BuildConfig
import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderCapability
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.providers.MusicProvider

class YouTubeMusicProvider(
    private val api: YouTubeDataApi
) : MusicProvider {
    
    override val id: ProviderKind = ProviderKind.YOUTUBE
    
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        setOf(
            ProviderCapability.SEARCH,
            ProviderCapability.METADATA,
            ProviderCapability.ARTWORK,
            ProviderCapability.PLAYBACK,
            ProviderCapability.QUEUE
        )
    )

    override suspend fun search(query: String): Result<List<MediaItem>> {
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(Exception("YouTube API key is not configured."))
        }
        
        return try {
            val response = api.search(
                query = query,
                apiKey = apiKey,
                videoEmbeddable = "true"
            )
            val items = response.items.mapNotNull { result ->
                val videoId = result.id.videoId ?: return@mapNotNull null
                val bestThumbnail = result.snippet.thumbnails.high?.url ?: result.snippet.thumbnails.default?.url
                MediaItem(
                    id = "youtube:$videoId",
                    provider = ProviderKind.YOUTUBE,
                    title = result.snippet.title,
                    artist = result.snippet.channelTitle,
                    album = null,
                    artworkUri = bestThumbnail
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveMetadata(trackId: String): Result<MediaItem> {
        val videoId = trackId.removePrefix("youtube:")
        val apiKey = BuildConfig.YOUTUBE_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(Exception("YouTube API key is not configured."))
        }
        
        return try {
            val response = api.getVideos(
                id = videoId,
                apiKey = apiKey
            )
            val item = response.items.firstOrNull() 
                ?: return Result.failure(Exception("Video not found"))
                
            if (item.status?.embeddable != true) {
                return Result.failure(Exception("Video is not embeddable"))
            }
            
            val bestThumbnail = item.snippet.thumbnails.high?.url ?: item.snippet.thumbnails.default?.url
            Result.success(
                MediaItem(
                    id = item.id,
                    provider = ProviderKind.YOUTUBE,
                    title = item.snippet.title,
                    artist = item.snippet.channelTitle,
                    album = null,
                    artworkUri = bestThumbnail
                    // Note: ISO 8601 duration parsing for contentDetails.duration 
                    // could be added here to populate technicalMetadata if needed.
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
