package dev.aurora.player.data.providers

import dev.aurora.player.data.api.YouTubeDataApi
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderCapabilities
import dev.aurora.player.domain.models.ProviderCapability
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.auth.YouTubeCredential
import dev.aurora.player.domain.providers.MusicProvider

class YouTubeMusicProvider(
    private val api: YouTubeDataApi,
    /*
     * A lookup rather than a value.
     *
     * The authority to call YouTube arrives, changes, or is withdrawn long after this
     * object is built - a key typed into Settings, an account signed in or signed out -
     * and it is suspending because a signed-in session may need its token refreshed before
     * it can answer.
     */
    private val credentials: suspend () -> YouTubeCredential
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
        val credential = credentials()
        if (credential is YouTubeCredential.None) {
            return Result.failure(Exception("Sign in with Google, or add a YouTube Data API key, in Settings."))
        }
        
        return try {
            val response = api.search(
                query = query,
                apiKey = credential.keyParameter,
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
        val credential = credentials()
        if (credential is YouTubeCredential.None) {
            return Result.failure(Exception("Sign in with Google, or add a YouTube Data API key, in Settings."))
        }
        
        return try {
            val response = api.getVideos(
                id = videoId,
                apiKey = credential.keyParameter
            )
            val item = response.items.firstOrNull() 
                ?: return Result.failure(Exception("Video not found"))
                
            if (item.status?.embeddable != true) {
                return Result.failure(Exception("Video is not embeddable"))
            }
            
            val bestThumbnail = item.snippet.thumbnails.high?.url ?: item.snippet.thumbnails.default?.url
            Result.success(
                MediaItem(
                    // Prefixed to match search(). Without it, an item resolved by metadata
                    // carried a bare id, and every call site routing on "youtube:" treated
                    // it as local - so the track resolved to no URI and would not play.
                    id = "youtube:${item.id}",
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
