package dev.aurora.player.data.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Following an artist is a local watch list, and the Home feed reads it, so it has to
 * survive a restart and never grow without bound.
 */
class FollowedArtistsStoreTest {

    private class InMemoryStore : KeyValueStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    @Test
    fun `a followed artist is remembered across a restart`() {
        val storage = InMemoryStore()
        FollowedArtistsStore(storage).setFollowed("Ikimonogakari", true)

        // A new instance is what the next launch builds.
        val reopened = FollowedArtistsStore(storage)
        assertTrue(reopened.isFollowed("Ikimonogakari"))
        assertEquals(listOf("Ikimonogakari"), reopened.followed.value)
    }

    @Test
    fun `following is matched without case, but shown as it was written`() {
        val store = FollowedArtistsStore(InMemoryStore())
        store.setFollowed("Ikimonogakari", true)

        assertTrue(store.isFollowed("ikimonogakari"))
        assertTrue(store.isFollowed("  IKIMONOGAKARI  "))
        // Stored the way the person saw it, not lowercased.
        assertEquals(listOf("Ikimonogakari"), store.followed.value)
    }

    @Test
    fun `following twice does not list them twice`() {
        val store = FollowedArtistsStore(InMemoryStore())
        store.setFollowed("Keshi", true)
        store.setFollowed("keshi", true)

        assertEquals(listOf("Keshi"), store.followed.value)
    }

    @Test
    fun `unfollowing removes them whatever the spelling`() {
        val store = FollowedArtistsStore(InMemoryStore())
        store.setFollowed("Keshi", true)
        store.setFollowed("KESHI", false)

        assertFalse(store.isFollowed("Keshi"))
        assertEquals(emptyList<String>(), store.followed.value)
    }

    @Test
    fun `the newest follow comes first`() {
        val store = FollowedArtistsStore(InMemoryStore())
        store.setFollowed("First", true)
        store.setFollowed("Second", true)

        // The feed takes a bounded number of artists, and the one just followed is the one
        // there is a reason to check.
        assertEquals(listOf("Second", "First"), store.followed.value)
    }

    @Test
    fun `the list is bounded, because each artist costs a lookup`() {
        val store = FollowedArtistsStore(InMemoryStore())
        repeat(60) { store.setFollowed("Artist $it", true) }

        assertEquals(50, store.followed.value.size)
        assertEquals("Artist 59", store.followed.value.first())
    }

    @Test
    fun `a blank name is not an artist`() {
        val store = FollowedArtistsStore(InMemoryStore())
        store.setFollowed("   ", true)

        assertEquals(emptyList<String>(), store.followed.value)
    }
}
