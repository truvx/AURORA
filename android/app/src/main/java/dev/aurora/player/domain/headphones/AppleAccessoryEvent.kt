package dev.aurora.player.domain.headphones

/**
 * The battery reading a headset will give up without a private API.
 *
 * Android has no public accessor for an accessory's battery: nothing on `BluetoothDevice`
 * or `BluetoothHeadset` returns one, and the fields that do exist are `@SystemApi` or
 * hidden. What is public is the vendor-specific AT event - a headset's own message,
 * delivered verbatim through a documented broadcast - and the message nearly every headset
 * sends is the one Apple's accessory protocol defines, because they all want to show a
 * level on an iPhone.
 *
 * ```
 * AT+IPHONEACCEV=<pairs>,<key1>,<value1>,<key2>,<value2>,…
 * ```
 *
 * Key 1 is the battery level, given as 0 to 9 - ten steps, not a percentage - so the level
 * is `(value + 1) * 10`, and a fresh headset reports 9 rather than 10.
 *
 * Parsing is all-or-nothing. A message that does not fit the shape returns null rather
 * than whatever could be salvaged from it: a battery figure is only worth showing if it
 * came from the headset intact, and half of a malformed message is not a reading.
 */
object AppleAccessoryEvent {

    /**
     * The AT command this carries.
     *
     * Spelled out rather than taken from `BluetoothHeadset`, whose constant for it is not
     * public API. This is the command's own name as the headset sent it, read out of a
     * public extra - not a hidden field reached by another route.
     */
    const val COMMAND = "+IPHONEACCEV"

    private const val KEY_BATTERY_LEVEL = 1
    private const val MAX_LEVEL = 9

    /**
     * The battery level as a percentage, or null if this message does not carry one.
     *
     * [args] is the event's argument list, in order, each already rendered as text.
     */
    fun batteryPercent(args: List<String>): Int? {
        val pairs = args.firstOrNull()?.trim()?.toIntOrNull() ?: return null
        if (pairs <= 0) return null
        // The count is the headset's own claim about the message; a message shorter than it
        // promises has been truncated somewhere, and the pairs that did arrive cannot be
        // trusted to be the ones they appear to be.
        if (args.size < 1 + pairs * 2) return null

        for (index in 0 until pairs) {
            val key = args[1 + index * 2].trim().toIntOrNull() ?: return null
            val value = args[2 + index * 2].trim().toIntOrNull() ?: return null
            if (key != KEY_BATTERY_LEVEL) continue
            if (value !in 0..MAX_LEVEL) return null
            return (value + 1) * 10
        }
        return null
    }
}
