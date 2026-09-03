package dev.aurora.player.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates navigation destination contract.
 */
class AuroraDestinationTest {

    @Test
    fun `primary destinations contains exactly 5 entries`() {
        assertEquals(5, AuroraDestination.primaryDestinations.size)
    }

    @Test
    fun `all destinations have unique routes`() {
        val routes = AuroraDestination.primaryDestinations.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `all destinations have non-empty labels`() {
        AuroraDestination.primaryDestinations.forEach { destination ->
            assertTrue(
                "Destination ${destination.route} has empty label",
                destination.label.isNotEmpty(),
            )
        }
    }

    @Test
    fun `destination order matches spec (Home, Search, Library, AI, Settings)`() {
        val routes = AuroraDestination.primaryDestinations.map { it.route }
        assertEquals(listOf("home", "search", "library", "ai", "settings"), routes)
    }
}
