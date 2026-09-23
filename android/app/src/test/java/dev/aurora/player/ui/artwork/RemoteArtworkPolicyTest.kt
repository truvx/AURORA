package dev.aurora.player.ui.artwork

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which remote artwork URLs the loader will fetch. They come from API responses. */
class RemoteArtworkPolicyTest {

    @Test
    fun `fetches YouTube's image hosts over https`() {
        assertTrue(ArtworkLoader.isAllowedRemote("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertTrue(ArtworkLoader.isAllowedRemote("https://yt3.ggpht.com/a/photo.jpg"))
        assertTrue(ArtworkLoader.isAllowedRemote("https://lh3.googleusercontent.com/x"))
        // Album covers. AURORA builds these URLs from a MusicBrainz release id rather than
        // reading them out of a response, and the archive redirects through its own
        // storage nodes before the image arrives.
        assertTrue(ArtworkLoader.isAllowedRemote("https://coverartarchive.org/release/abc/front-500"))
        assertTrue(ArtworkLoader.isAllowedRemote("https://archive.org/download/mbid-abc/front.jpg"))
        assertTrue(ArtworkLoader.isAllowedRemote("https://dn710208.ca.archive.org/0/items/mbid-abc/front.jpg"))
    }

    @Test
    fun `refuses plain http`() {
        assertFalse(ArtworkLoader.isAllowedRemote("http://i.ytimg.com/vi/abc/hqdefault.jpg"))
    }

    @Test
    fun `refuses other hosts, including lookalikes`() {
        assertFalse(ArtworkLoader.isAllowedRemote("https://example.com/cover.jpg"))
        assertFalse(ArtworkLoader.isAllowedRemote("https://ytimg.com.evil.example/x.jpg"))
        assertFalse(ArtworkLoader.isAllowedRemote("https://evilytimg.com/x.jpg"))
    }

    @Test
    fun `refuses things that are not URLs`() {
        assertFalse(ArtworkLoader.isAllowedRemote("https://"))
        assertFalse(ArtworkLoader.isAllowedRemote("not a url"))
    }
}
