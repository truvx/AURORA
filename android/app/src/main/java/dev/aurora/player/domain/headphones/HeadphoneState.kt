package dev.aurora.player.domain.headphones

/**
 * Whether headphones are the active output, and what they say about their battery.
 *
 * Deliberately three-valued rather than two: "connected, level unknown" is its own state
 * and the commonest one. Most headsets never report a level at all, and the only public
 * route to one is a vendor message the headset may simply not send - so a UI that models
 * this as a number with a default would be inventing a reading. Unknown is shown as the
 * icon alone.
 */
sealed interface HeadphoneState {

    data object Disconnected : HeadphoneState

    /** [batteryPercent] is null whenever nothing has reported a level this session. */
    data class Connected(val batteryPercent: Int?) : HeadphoneState {
        init {
            require(batteryPercent == null || batteryPercent in 0..100) {
                "battery percent out of range: $batteryPercent"
            }
        }

        /** Below this, the capsule warns - with a word as well as a colour. */
        val isLow: Boolean get() = batteryPercent != null && batteryPercent <= LOW_BATTERY_PERCENT
    }

    companion object {
        const val LOW_BATTERY_PERCENT = 20
    }
}
