package dev.aurora.player.domain.player

import dev.aurora.player.domain.models.ProviderKind

/**
 * When the official YouTube player may follow the listener out of the app, and what the
 * buttons on it do.
 *
 * Picture-in-picture is the only way a YouTube track keeps playing when AURORA is not the
 * foreground app, and it is allowed for one reason: the player is still *there*. It
 * shrinks into a floating window with YouTube's own controls, branding and advertising
 * intact. Nothing is extracted, nothing is re-wrapped, and the moment the window is
 * dismissed the audio stops. That is the difference between this and a background player,
 * which `AGENTS.md` forbids outright.
 *
 * Local files do not use it. They have a media session and play with the screen off, which
 * is a thing AURORA is allowed to do because it owns the file.
 *
 * Plain Kotlin so the rules can be tested without an Activity; the Activity decides *when*
 * to ask, this decides *whether* the answer is yes.
 */
object PictureInPicturePolicy {

    /**
     * Whether leaving the app right now should shrink the player rather than stop it.
     *
     * Only for a YouTube track that is actually making sound. A paused video that followed
     * the listener to their home screen would be a window asking to be dismissed, and an
     * idle one would be a window showing nothing.
     */
    fun shouldEnter(state: PlayerState): Boolean {
        val track = state.currentTrack ?: return false
        if (track.provider != ProviderKind.YOUTUBE) return false
        return state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering
    }

    /**
     * Whether leaving the foreground should stop a YouTube track.
     *
     * True whenever it is not going into picture-in-picture. A YouTube track playing with
     * no visible player is the state the capability matrix exists to prevent, and it does
     * not become acceptable just because the listener pressed Home.
     */
    fun shouldPauseOnLeaving(state: PlayerState, enteringPictureInPicture: Boolean): Boolean {
        if (enteringPictureInPicture) return false
        val track = state.currentTrack ?: return false
        return track.provider == ProviderKind.YOUTUBE
    }

    /** The three controls the floating window carries, and what each one dispatches. */
    enum class Action(val id: String) {
        Previous("dev.aurora.player.pip.PREVIOUS"),
        PlayPause("dev.aurora.player.pip.PLAY_PAUSE"),
        Next("dev.aurora.player.pip.NEXT"),
        ;

        companion object {
            fun from(id: String?): Action? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * The command an action means, given what the player is doing.
     *
     * Play/pause is the only one that depends on state, and it reads the same state the
     * window's own icon is drawn from, so the button cannot say one thing and do another.
     */
    fun commandFor(action: Action, state: PlayerState): PlayerCommand = when (action) {
        Action.Previous -> PlayerCommand.SkipPrevious
        Action.Next -> PlayerCommand.SkipNext
        Action.PlayPause ->
            if (state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Buffering) {
                PlayerCommand.Pause
            } else {
                PlayerCommand.Play
            }
    }
}
