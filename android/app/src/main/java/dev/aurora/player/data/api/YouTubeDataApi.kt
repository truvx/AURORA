package dev.aurora.player.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchResult>
)

@Serializable
data class YouTubeSearchResult(
    val id: YouTubeVideoId,
    val snippet: YouTubeSnippet
)

@Serializable
data class YouTubeVideoId(
    val videoId: String? = null
)

@Serializable
data class YouTubeSnippet(
    val title: String,
    val channelTitle: String,
    val thumbnails: YouTubeThumbnails
)

@Serializable
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val default: YouTubeThumbnail? = null
)

@Serializable
data class YouTubeThumbnail(
    val url: String
)

@Serializable
data class YouTubeVideosResponse(
    val items: List<YouTubeVideoItem>
)

@Serializable
data class YouTubeVideoItem(
    val id: String,
    val snippet: YouTubeSnippet,
    val contentDetails: YouTubeContentDetails? = null,
    val status: YouTubeStatus? = null
)

@Serializable
data class YouTubeStatus(
    val embeddable: Boolean
)

@Serializable
data class YouTubeContentDetails(
    val duration: String
)

interface YouTubeDataApi {
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("videoEmbeddable") videoEmbeddable: String? = null,
        @Query("maxResults") maxResults: Int = 20
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideos(
        @Query("part") part: String = "snippet,contentDetails,status",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): YouTubeVideosResponse
}
