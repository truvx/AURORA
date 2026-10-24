package dev.aurora.player.ui.haptics

/**
 * What the phone is allowed to say with a buzz.
 *
 * Seven, down from nineteen. The nineteen were not nineteen sensations - several compiled
 * to the same rhythm under different names, and others differed by a tenth of a scale
 * factor and a few milliseconds, which is a difference on paper and nothing at all through
 * a phone's chassis. A vocabulary whose words cannot be told apart is not a vocabulary.
 *
 * Each of these means something the others do not, and feels like something the others do
 * not. `AndroidHapticEngineTest` holds the second half of that to account.
 *
 * None of them is ever the only signal: everything here is also said on screen and to a
 * screen reader. See docs/AURORA_HAPTIC_SPEC.md.
 */
sealed interface HapticEvent {

    /** A control did the thing it says it does. Once per activation. */
    data object Tap : HapticEvent

    /** Which one of a set is current has changed - a tab, a segment, a picker. */
    data object Selection : HapticEvent

    /** A two-state thing committed: played or paused, favourited, switched on. */
    data object Toggle : HapticEvent

    /**
     * A boundary crossed under a gesture that is still going - a scrub snap, a slider
     * step, an insertion point passed while dragging.
     *
     * Rate-limited by the engine. This is the one that would ruin everything if it fired
     * per frame, which is why it is also the quietest.
     */
    data object Tick : HapticEvent

    /** Something came off the list and is now following the finger. */
    data object Lift : HapticEvent

    /** It landed, or it worked: a reorder accepted, a playlist made, a secret saved. */
    data object Commit : HapticEvent

    /** It did not happen - refused on purpose, or it failed. Always said on screen too. */
    data object Refused : HapticEvent
}
