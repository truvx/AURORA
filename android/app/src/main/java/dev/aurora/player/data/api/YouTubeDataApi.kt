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
    val videoId: String? = null,
    val channelId: String? = null
)

@Serializable
data class YouTubeSnippet(
    // Escaped on the wire; see HtmlText.kt.
    @Serializable(with = HtmlTextSerializer::class) val title: String,
    @Serializable(with = HtmlTextSerializer::class) val channelTitle: String = "",
    val thumbnails: YouTubeThumbnails,
    /** ISO-8601. Present on search results, videos and playlist items. */
    val publishedAt: String? = null,
    /** Playlist items only: the channel that published the video, not the playlist owner. */
    @Serializable(with = HtmlTextSerializer::class) val videoOwnerChannelTitle: String? = null,
    /** Playlist items only. */
    val resourceId: YouTubeResourceId? = null
)

@Serializable
data class YouTubeResourceId(
    val videoId: String? = null
)

@Serializable
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val default: YouTubeThumbnail? = null
) {
    /** Largest available; high is 480x360, enough for a card without fetching maxres. */
    fun best(): String? = high?.url ?: medium?.url ?: default?.url
}

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

@Serializable
data class YouTubeChannelsResponse(
    val items: List<YouTubeChannel> = emptyList()
)

@Serializable
data class YouTubeChannel(
    val id: String,
    val contentDetails: YouTubeChannelContentDetails? = null
)

@Serializable
data class YouTubeChannelContentDetails(
    val relatedPlaylists: YouTubeRelatedPlaylists? = null
)

@Serializable
data class YouTubeRelatedPlaylists(
    /** Every channel's uploads, newest first. Reading it costs 1 quota unit. */
    val uploads: String? = null
)

@Serializable
data class YouTubePlaylistItemsResponse(
    val items: List<YouTubePlaylistItem> = emptyList()
)

@Serializable
data class YouTubePlaylistItem(
    val snippet: YouTubeSnippet,
    val contentDetails: YouTubePlaylistItemDetails? = null
)

@Serializable
data class YouTubePlaylistItemDetails(
    val videoId: String? = null,
    /** When the video itself went public - not when it was added to the playlist. */
    val videoPublishedAt: String? = null
)

/**
 * YouTube Data API v3. Quota per call, from the published cost table: search 100, every
 * list call (videos, channels, playlistItems) 1. That spread is why discovery features
 * resolve an artist's channel once with a search and then read it with list calls.
 */
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

    /** A chart, e.g. `mostPopular` in video category 10 (Music) for one region. */
    @GET("videos")
    suspend fun getChart(
        @Query("part") part: String = "snippet,contentDetails,status",
        @Query("chart") chart: String = "mostPopular",
        @Query("videoCategoryId") categoryId: String = "10",
        @Query("regionCode") regionCode: String,
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YouTubeVideosResponse

    @GET("search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "channel",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse

    @GET("channels")
    suspend fun getChannels(
        @Query("part") part: String = "contentDetails",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): YouTubeChannelsResponse

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") apiKey: String
    ): YouTubePlaylistItemsResponse
}
