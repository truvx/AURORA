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
