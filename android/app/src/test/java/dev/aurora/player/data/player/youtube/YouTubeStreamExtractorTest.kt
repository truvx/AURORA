package dev.aurora.player.data.player.youtube

import dev.aurora.player.domain.models.ProviderKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class YouTubeStreamExtractorTest {

    private fun responseWithStreamingData(url: String = "https://stream.example.com/audio"): PlayerResponse =
        PlayerResponse(
            responseContext = PlayerResponse.ResponseContext(),
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(
                formats = emptyList(),
                adaptiveFormats = listOf(
                    PlayerResponse.StreamingData.Format(
                        itag = 251,
                        url = url,
                        mimeType = "audio/webm",
                        bitrate = 160000,
                        width = null,
                        height = null,
                        contentLength = null,
                        quality = "tiny",
                        fps = null,
                        qualityLabel = null,
                        averageBitrate = 160000,
                        audioQuality = "AUDIO_QUALITY_MEDIUM",
                        approxDurationMs = null,
                        audioSampleRate = 48000,
                        audioChannels = 2,
                        loudnessDb = null,
                        lastModified = null,
                        signatureCipher = null,
                        cipher = null,
                    )
                ),
                expiresInSeconds = null,
            ),
            videoDetails = null,
            playbackTracking = null,
        )

    private fun responseWithoutStreamingData(): PlayerResponse =
        PlayerResponse(
            responseContext = PlayerResponse.ResponseContext(),
            playabilityStatus = PlayerResponse.PlayabilityStatus(
                status = "UNPLAYABLE",
                reason = "bot_detection"
            ),
            playerConfig = null,
            streamingData = null,
            videoDetails = null,
            playbackTracking = null,
        )

    @Test
    fun `extractAudioStreamUrl returns URL when streamingData present`() = runTest {
        val innerTube = mockk<InnerTubeClient>()
        coEvery { innerTube.player(any(), any(), any()) } returns responseWithStreamingData()
        every { innerTube.authState } returns PlaybackAuthState.EMPTY

        val extractor = YouTubeStreamExtractor(innerTube)
        val url = extractor.extractAudioStreamUrl("dQw4w9WgXcQ")

        assertNotNull(url)
        coVerify(exactly = 1) { innerTube.player(any(), any(), any()) }
    }

    @Test
    fun `extractAudioStreamUrl returns null when streamingData absent`() = runTest {
        val innerTube = mockk<InnerTubeClient>()
        coEvery { innerTube.player(any(), any(), any()) } returns responseWithoutStreamingData()
        every { innerTube.authState } returns PlaybackAuthState.EMPTY

        val extractor = YouTubeStreamExtractor(innerTube)
        val url = extractor.extractAudioStreamUrl("dQw4w9WgXcQ")

        assertNull(url)
        coVerify(exactly = 1) { innerTube.player(any(), any(), any()) }
    }

    @Test
    fun `extractAudioStreamUrl appends pot= when authState has a PoToken`() = runTest {
        val poToken = "test-pot-token-12345"
        val innerTube = mockk<InnerTubeClient>()
        coEvery { innerTube.player(any(), any(), any()) } returns responseWithStreamingData()
        every { innerTube.authState } returns PlaybackAuthState.EMPTY.copy(poToken = poToken)

        val extractor = YouTubeStreamExtractor(innerTube)
        val url = extractor.extractAudioStreamUrl("dQw4w9WgXcQ")

        assertNotNull(url)
        assertTrue(url!!.contains("pot=$poToken"))
        coVerify(exactly = 1) { innerTube.player(any(), any(), any()) }
    }

    @Test
    fun `extractAudioStreamUrl does not append pot= when authState has no PoToken`() = runTest {
        val innerTube = mockk<InnerTubeClient>()
        coEvery { innerTube.player(any(), any(), any()) } returns responseWithStreamingData()
        every { innerTube.authState } returns PlaybackAuthState.EMPTY

        val extractor = YouTubeStreamExtractor(innerTube)
        val url = extractor.extractAudioStreamUrl("dQw4w9WgXcQ")

        assertNotNull(url)
        assertFalse(url!!.contains("pot="))
        coVerify(exactly = 1) { innerTube.player(any(), any(), any()) }
    }
}
