package dev.aurora.player.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The destination lists must actually contain destinations.
 *
 * A list built in the companion object of a sealed class, referring to that class's own
 * objects, can be initialised before those objects exist - leaving nulls that only surface
 * as a crash when something reads a field off one.
 */
class DestinationListTest {

    @Test
    fun `pill destinations are all present`() {
        assertEquals(4, AuroraDestination.pillDestinations.size)
        assertTrue(AuroraDestination.pillDestinations.all { it.route.isNotBlank() })
    }

    @Test
    fun `primary destinations are all present`() {
        assertTrue(AuroraDestination.primaryDestinations.all { it.route.isNotBlank() })
    }

    @Test
    fun `search is reachable but not in the pill`() {
        // It lives in its own circular button beside the pill.
        assertTrue(AuroraDestination.Search !in AuroraDestination.pillDestinations)
        assertTrue(AuroraDestination.Search in AuroraDestination.primaryDestinations)
    }
}
