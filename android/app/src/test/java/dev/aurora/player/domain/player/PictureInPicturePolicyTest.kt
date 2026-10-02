package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.MediaItem
import dev.aurora.player.domain.models.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep a YouTube track from ever playing unseen.
 *
 * Picture-in-picture is allowed because the player goes with it. The two halves of that
 * bargain are here: it may only shrink while something is actually playing, and anything
 * that is not shrinking must stop.
 */
class PictureInPicturePolicyTest {

    private fun state(
        provider: ProviderKind? = ProviderKind.YOUTUBE,
        status: PlaybackStatus = PlaybackStatus.Playing,
    ) = PlayerState(
        status = status,
        currentTrack = provider?.let {
            MediaItem(id = "1", provider = it, title = "A Video", artist = null, album = null, artworkUri = null)
        },
    )

    @Test
    fun `a playing YouTube track shrinks rather than stops`() {
        assertTrue(PictureInPicturePolicy.shouldEnter(state()))
        // Buffering counts: it is on its way to making sound, and stopping it to open a
        // window a second later would be a stutter the listener caused by pressing Home.
        assertTrue(PictureInPicturePolicy.shouldEnter(state(status = PlaybackStatus.Buffering)))
    }

    @Test
    fun `nothing else opens a window`() {
        // A local file has a media session and plays with the screen off; a floating video
        // window of a song with no video would be a box of album art in the way.
        assertFalse(PictureInPicturePolicy.shouldEnter(state(provider = ProviderKind.LOCAL)))
        // Paused would be a window asking to be dismissed; idle would show nothing at all.
        assertFalse(PictureInPicturePolicy.shouldEnter(state(status = PlaybackStatus.Paused)))
        assertFalse(PictureInPicturePolicy.shouldEnter(state(status = PlaybackStatus.Idle)))
        assertFalse(PictureInPicturePolicy.shouldEnter(state(provider = null)))
    }

    @Test
    fun `a YouTube track that is not shrinking is stopped`() {
        // The state the capability matrix exists to prevent: playing, with no player on
        // screen. Pressing Home is not consent to it.
        assertTrue(
            PictureInPicturePolicy.shouldPauseOnLeaving(state(), enteringPictureInPicture = false)
        )
        assertTrue(
            PictureInPicturePolicy.shouldPauseOnLeaving(
                state(status = PlaybackStatus.Paused), enteringPictureInPicture = false
            )
        )
    }

    @Test
    fun `a track on its way into the window is left alone`() {
        assertFalse(
            PictureInPicturePolicy.shouldEnter(state()).let {
                PictureInPicturePolicy.shouldPauseOnLeaving(state(), enteringPictureInPicture = true)
            }
        )
    }

    @Test
    fun `local playback is never stopped by leaving`() {
        // It is allowed to play in the background, and this policy must not take that away.
        assertFalse(
            PictureInPicturePolicy.shouldPauseOnLeaving(
                state(provider = ProviderKind.LOCAL), enteringPictureInPicture = false
            )
        )
    }

    @Test
    fun `the window's buttons map to the one coordinator's commands`() {
        val playing = state(status = PlaybackStatus.Playing)
        val paused = state(status = PlaybackStatus.Paused)

        assertEquals(
            PlayerCommand.SkipPrevious,
            PictureInPicturePolicy.commandFor(PictureInPicturePolicy.Action.Previous, playing)
        )
        assertEquals(
            PlayerCommand.SkipNext,
            PictureInPicturePolicy.commandFor(PictureInPicturePolicy.Action.Next, playing)
        )
        // The one that depends on state reads the same state the icon is drawn from, so the
        // button cannot say one thing and do another.
        assertEquals(
            PlayerCommand.Pause,
            PictureInPicturePolicy.commandFor(PictureInPicturePolicy.Action.PlayPause, playing)
        )
        assertEquals(
            PlayerCommand.Play,
            PictureInPicturePolicy.commandFor(PictureInPicturePolicy.Action.PlayPause, paused)
        )
    }

    @Test
    fun `an action id that is not ours resolves to nothing`() {
        // The receiver is addressed by a broadcast; anything may send one.
        assertEquals(null, PictureInPicturePolicy.Action.from("com.someone.else.PLAY"))
        assertEquals(null, PictureInPicturePolicy.Action.from(null))
        assertEquals(
            PictureInPicturePolicy.Action.Next,
            PictureInPicturePolicy.Action.from(PictureInPicturePolicy.Action.Next.id)
        )
    }
}
