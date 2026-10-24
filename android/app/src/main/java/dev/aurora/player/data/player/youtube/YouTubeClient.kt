package dev.aurora.player.data.player.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * YouTube InnerTube client definitions.
 *
 * Ported from NomaTune (GPL-3.0, github.com/Shahdullah/NomaTune),
 * `innertube/models/YouTubeClient.kt`.
 *
 * These define the client identity sent to `youtubei/v1/player`. WEB_REMIX is the
 * main client (it returns audio-only `adaptiveFormats`); the fallback clients are
 * tried when a stream fails.
 */
@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val useWebPoTokens: Boolean = false,
    val isEmbedded: Boolean = false,
) {
    fun requestOrigin(): String {
        return when (clientName.uppercase(Locale.US)) {
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY" -> ORIGIN_YOUTUBE
            else -> ORIGIN_YOUTUBE_MUSIC
        }
    }

    fun requestReferer(): String {
        return when (clientName.uppercase(Locale.US)) {
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY" -> REFERER_YOUTUBE_TV
            else -> REFERER_YOUTUBE_MUSIC
        }
    }

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        const val USER_AGENT_WEB_REMIX = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

        const val ORIGIN_YOUTUBE = "https://www.youtube.com"
        const val REFERER_YOUTUBE_TV = "$ORIGIN_YOUTUBE/tv"

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260114.00.00",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = USER_AGENT_WEB_REMIX,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "19.29.1",
            clientId = "5",
            userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
            osVersion = "17.5.1.21F90",
        )

        val ANDROID = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.10.38",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.10.38 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            loginSupported = true,
            useSignatureTimestamp = true
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            clientId = "21",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro",
            androidSdkVersion = "35",
            buildId = "AP4A.250205.002",
            cronetVersion = "132.0.6834.79",
            packageName = "com.google.android.apps.youtube.music",
            friendlyName = "Android Music",
            loginSupported = true,
            useSignatureTimestamp = true,
        )
    }
}
