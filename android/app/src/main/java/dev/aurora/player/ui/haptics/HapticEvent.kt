package dev.aurora.player.ui.haptics

/**
 * Semantic haptic events from docs/AURORA_HAPTIC_SPEC.md.
 *
 * UI, player, download, and navigation code send these events
 * to HapticEngine instead of calling raw vibration APIs.
 */
sealed interface HapticEvent {
    /** Meaningful button activation */
    data object Tap : HapticEvent
    /** Picker/tab/meaningful choice committed */
    data object Selection : HapticEvent
    /** On/off setting committed */
    data object Toggle : HapticEvent
    /** Sparse meaningful scrub snap — rate-limited */
    data object Scrub : HapticEvent
    /** Discrete slider boundary/tick — rate-limited */
    data object SliderTick : HapticEvent
    /** Queue/item drag begins */
    data object DragStart : HapticEvent
    /** Only meaningful snap/boundary during drag — sparse */
    data object DragMove : HapticEvent
    /** Valid reorder committed */
    data object DragDrop : HapticEvent
    /** Favorite state committed */
    data object Favorite : HapticEvent
    /** Queue reorder committed */
    data object QueueReorder : HapticEvent
    /** Legitimate local import/download completed */
    data object DownloadComplete : HapticEvent
    /** Meaningful successful operation */
    data object Success : HapticEvent
    /** Actionable warning (not passive states) */
    data object Warning : HapticEvent
    /** Actionable error */
    data object Error : HapticEvent
}
