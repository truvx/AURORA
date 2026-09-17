package dev.aurora.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.app.PlayerCoordinator
import dev.aurora.player.app.TrackResolver
import dev.aurora.player.domain.audio.TrackLoudnessData
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlayerCommand
import dev.aurora.player.ui.components.LocalPlayerCoordinator
import dev.aurora.player.ui.components.MiniPlayer
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.haptics.NoOpHapticEngine
import dev.aurora.player.ui.theme.AuroraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The mini-player is the only route to the full player, and it was not rendered at all:
 * MiniPlayer, NowPlayingScreen and QueueSheet were written and then left unwired, so playing
 * a track changed nothing on screen.
 *
 * These cover the two things that has to keep doing - be reachable, and open the full player
 * by gesture as well as by tap - because the failure mode is silent. Nothing throws when a
 * composable is simply never called.
 */
@RunWith(AndroidJUnit4::class)
class MiniPlayerGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private val track = MediaItem(
        id = "local_1",
        provider = ProviderKind.LOCAL,
        title = "Test Track",
        artist = "Test Artist",
        album = null,
        artworkUri = null
    )

    private class SilentAdapter : PlayerAdapter {
        override val events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
        override fun load(
            track: MediaItem,
            uri: String,
            playWhenReady: Boolean,
            crossfadeDurationMs: Long
        ) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(volume: Float) {}
        override fun setAudioGain(linearGain: Float) {}
        override fun release() {}
    }

    private class InstantResolver : TrackResolver {
        override suspend fun resolveUri(trackId: String) = "file://$trackId"
        override suspend fun resolveLoudness(trackId: String): TrackLoudnessData? = null
    }

    /** Renders the mini-player with [track] loaded, and reports when it asks to expand. */
    private fun setMiniPlayer(onExpand: () -> Unit) {
        val scope = CoroutineScope(Dispatchers.Main)
        val coordinator = PlayerCoordinator(SilentAdapter(), InstantResolver(), scope)
        coordinator.dispatch(PlayerCommand.Load(track))

        rule.setContent {
            CompositionLocalProvider(
                LocalHapticEngine provides NoOpHapticEngine(),
                LocalPlayerCoordinator provides coordinator
            ) {
                AuroraTheme { Column { MiniPlayer(onExpand = onExpand) } }
            }
        }
    }

    @Test
    fun theMiniPlayerAppearsOnceATrackIsLoaded() {
        setMiniPlayer {}

        rule.onNodeWithText("Test Track").assertIsDisplayed()
    }

    @Test
    fun theTransportControlIsReachableAndLabelled() {
        // The play control has to be usable from here without opening the full player.
        setMiniPlayer {}

        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun draggingUpOpensTheFullPlayer() {
        var expanded = false
        setMiniPlayer { expanded = true }

        // Swiped past the node's own bounds on purpose. The mini-player is only 64dp tall,
        // so a swipe confined to it barely moves - this has to be a gesture with real
        // travel and speed behind it, which is what the release decision reads.
        rule.onNodeWithText("Test Track").performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 500f, durationMillis = 120)
        }
        rule.waitForIdle()

        assertTrue("dragging the mini-player up did not open the full player", expanded)
    }

    @Test
    fun draggingDownDoesNotOpenTheFullPlayer() {
        // There is nothing below the mini-player, so a downward drag must resist and settle
        // back rather than being read as an intent to open anything.
        var expanded = false
        setMiniPlayer { expanded = true }

        rule.onNodeWithText("Test Track").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 500f, durationMillis = 120)
        }
        rule.waitForIdle()

        assertFalse("dragging down opened the full player", expanded)
    }

    @Test
    fun tappingOpensTheFullPlayer() {
        // The gesture must never be the only way in: a tap, and therefore the accessibility
        // action behind it, has to reach the same place.
        var expanded = false
        setMiniPlayer { expanded = true }

        rule.onNodeWithText("Test Track").performTouchInput { click() }
        rule.waitForIdle()

        assertTrue("tapping the mini-player did not open the full player", expanded)
    }
}
