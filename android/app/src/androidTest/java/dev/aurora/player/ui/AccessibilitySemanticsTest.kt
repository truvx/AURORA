package dev.aurora.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.aurora.player.domain.library.Playlist
import dev.aurora.player.domain.library.PlaylistSource
import dev.aurora.player.domain.library.PlaylistTrack
import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import dev.aurora.player.domain.player.PlaybackPosition
import dev.aurora.player.ui.accessibility.AccessibilityPreferences
import dev.aurora.player.ui.accessibility.LocalAccessibilityPreferences
import dev.aurora.player.ui.components.PlayerScrubber
import dev.aurora.player.ui.haptics.LocalHapticEngine
import dev.aurora.player.ui.haptics.NoOpHapticEngine
import dev.aurora.player.ui.library.PlaylistsSection
import dev.aurora.player.ui.theme.AuroraTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility is a release gate, not a polish pass (docs/ACCESSIBILITY_SPEC.md). These
 * assert the outcomes the spec calls non-negotiable: every control is labelled, targets meet
 * the minimum size, values are meaningful rather than raw numbers, and no essential action
 * depends on colour or drag.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsTest {

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

    private fun setAuroraContent(
        preferences: AccessibilityPreferences = AccessibilityPreferences(),
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        rule.setContent {
            CompositionLocalProvider(
                LocalHapticEngine provides NoOpHapticEngine(),
                LocalAccessibilityPreferences provides preferences
            ) {
                AuroraTheme { Column { content() } }
            }
        }
    }

    // --- scrubber -----------------------------------------------------------------------

    @Test
    fun scrubberAnnouncesATimeNotARawNumber() {
        setAuroraContent {
            PlayerScrubber(
                position = PlaybackPosition(elapsed = 65_000L, duration = 185_000L, buffered = null),
                onSeek = {}
            )
        }

        // "1 minute 5 seconds of 3 minutes 5 seconds", never "0.35".
        rule.onNodeWithContentDescription(
            "Playback position, 1 minute 5 seconds of 3 minutes 5 seconds"
        ).assertExists()
    }

    @Test
    fun scrubberSaysSoWhenDurationIsUnknown() {
        setAuroraContent {
            PlayerScrubber(
                position = PlaybackPosition(elapsed = 0L, duration = null, buffered = null),
                onSeek = {}
            )
        }

        rule.onNodeWithContentDescription("Playback position. Duration unknown.").assertExists()
    }

    // --- playlist reordering ------------------------------------------------------------

    private val playlist = Playlist(
        id = "p1",
        name = "Test Playlist",
        createdAt = 0L,
        updatedAt = 0L,
        sourceKind = PlaylistSource.USER,
        trackCount = 2
    )

    private fun playlistContent() {
        setAuroraContent {
            PlaylistsSection(
                playlists = listOf(playlist),
                openPlaylist = playlist,
                openPlaylistTracks = listOf(
                    PlaylistTrack("e1", 0, track),
                    PlaylistTrack("e2", 1, track.copy(id = "local_2", title = "Second"))
                ),
                onOpenPlaylist = {},
                onCreatePlaylist = {},
                onDeletePlaylist = {},
                onRemoveEntry = { _, _ -> },
                onMoveEntry = { _, _, _ -> }
            )
        }
    }

    @Test
    fun reorderingHasNamedControlsRatherThanDragOnly() {
        // The spec requires a named Move up / Move down alternative to dragging.
        playlistContent()

        rule.onAllNodesWithContentDescriptionSubstring("Move up").assertAny()
        rule.onAllNodesWithContentDescriptionSubstring("Move down").assertAny()
    }

    @Test
    fun reorderControlsMeetTheMinimumTargetSize() {
        playlistContent()

        // 48dp minimum per the spec, checked on the real rendered node. Every row has its
        // own control, so assert the size of each rather than assuming a single match.
        val controls = rule.onAllNodesWithContentDescriptionSubstring("Move down")
        val count = controls.fetchSemanticsNodes().size
        assertTrue("expected reorder controls to exist", count > 0)
        repeat(count) { index ->
            controls[index]
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun everyInteractiveControlIsLabelled() {
        playlistContent()

        val tree = rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 100)
        // An icon-only control with no description is unusable with a screen reader.
        assertFalse(
            "found a clickable node with no content description:\n$tree",
            tree.contains("Role = 'Button'") && tree.contains("ContentDescription = '[]'")
        )
    }

    // --- reduced transparency -----------------------------------------------------------

    @Test
    fun reducedTransparencyStillRendersContent() {
        // Dropping glass must not drop the content or its semantics with it.
        setAuroraContent(AccessibilityPreferences(reducedTransparency = true)) {
            PlayerScrubber(
                position = PlaybackPosition(elapsed = 1_000L, duration = 2_000L, buffered = null),
                onSeek = {}
            )
        }

        rule.onNodeWithContentDescription(
            "Playback position, 1 second of 2 seconds"
        ).assertExists()
    }

    @Test
    fun reducedMotionPreferenceIsObservable() {
        var observed = false
        setAuroraContent(AccessibilityPreferences(reducedMotion = true)) {
            observed = LocalAccessibilityPreferences.current.reducedMotion
        }
        rule.waitForIdle()
        assertTrue("components must be able to read the reduced-motion preference", observed)
    }
}

/** Matches a content description containing [text]; Compose only offers exact match. */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule
    .onAllNodesWithContentDescriptionSubstring(text: String) =
    onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("ContentDescription contains '$text'") { node ->
            val descriptions: List<String> =
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            descriptions.any { description -> description.contains(text, ignoreCase = true) }
        },
        useUnmergedTree = true
    )

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertAny() {
    if (fetchSemanticsNodes().isEmpty()) {
        throw AssertionError("expected at least one matching node, found none")
    }
}
