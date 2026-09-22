package dev.aurora.player.ui.artwork

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads album artwork and the atmosphere colour taken from it.
 *
 * Decoded small on purpose. The artwork is shown at 48dp in the mini-player and roughly a
 * screen width in the full player, and the palette only needs enough pixels to be
 * representative - decoding a full-size cover to sample its colour would cost megabytes per
 * track for no visible gain.
 *
 * Results are cached by URI: the same album's artwork is asked for by every row in a list,
 * the mini-player and the full player, and re-decoding it each time would put an image
 * decode on the frame path.
 */
class ArtworkLoader(
    private val resolver: ContentResolver,
    private val decodeSize: Int = DEFAULT_DECODE_SIZE,
) {

    internal companion object {
        /** Big enough to display at mini-player size and to sample honestly. */
        const val DEFAULT_DECODE_SIZE = 256

        /** Bounded so a long listening session cannot grow this without limit. */
        const val CACHE_LIMIT = 48

        /** A YouTube thumbnail is ~30 KB; anything past this is not a thumbnail. */
        const val MAX_REMOTE_BYTES = 2 * 1024 * 1024

        /**
         * Remote artwork is fetched only from YouTube's image hosts. The URLs arrive in API
         * responses, which docs/SECURITY.md treats as untrusted until validated - an open
         * fetcher would load whatever URL a response contained.
         */
        private val REMOTE_HOSTS = listOf("ytimg.com", "ggpht.com", "googleusercontent.com")

        fun isAllowedRemote(url: String): Boolean {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            if (uri.scheme != "https") return false
            val host = uri.host?.lowercase() ?: return false
            return REMOTE_HOSTS.any { host == it || host.endsWith(".$it") }
        }
    }

    private val http by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Whether a palette was computed is itself worth caching: artwork that yields no colour
     * would otherwise be re-decoded on every recomposition that asks.
     */
    private data class Entry(val bitmap: Bitmap?, val palette: Color?)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean =
            size > CACHE_LIMIT
    }

    suspend fun bitmapFor(artworkUri: String?): Bitmap? = entryFor(artworkUri)?.bitmap

    /** The clamped atmosphere colour for this artwork, or null when there is none to take. */
    suspend fun paletteFor(artworkUri: String?): Color? = entryFor(artworkUri)?.palette

    private suspend fun entryFor(artworkUri: String?): Entry? {
        if (artworkUri.isNullOrBlank()) return null

        synchronized(cache) { cache[artworkUri] }?.let { return it }

        val entry = withContext(Dispatchers.IO) {
            val bitmap = decode(artworkUri)
            Entry(bitmap, bitmap?.let { paletteOf(it) })
        }

        synchronized(cache) { cache[artworkUri] = entry }
        return entry
    }

    private fun decode(artworkUri: String): Bitmap? = try {
        if (artworkUri.startsWith("http")) decodeRemote(artworkUri) else decodeLocal(artworkUri)
    } catch (_: Exception) {
        // Missing, unreadable, revoked, offline, or not an image. Artwork is decoration: the
        // caller falls back to the placeholder and nothing about playback is affected.
        null
    }

    /** YouTube thumbnails. Before this, every YouTube track showed the placeholder. */
    private fun decodeRemote(url: String): Bitmap? {
        if (!isAllowedRemote(url)) return null
        val request = okhttp3.Request.Builder().url(url).build()
        val bytes = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            if (body.contentLength() > MAX_REMOTE_BYTES) return null
            // Read by hand, capped: InputStream.readNBytes is API 33 and minSdk is 26, and
            // Content-Length can be absent, so the cap has to be enforced while reading.
            body.byteStream().use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() <= MAX_REMOTE_BYTES) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }
        if (bytes.size > MAX_REMOTE_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (largestEdge <= 0) return null
        var sampleSize = 1
        while (largestEdge / (sampleSize * 2) >= decodeSize) sampleSize *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun decodeLocal(artworkUri: String): Bitmap? = run {
        val uri = Uri.parse(artworkUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The supported route to MediaStore album art. Reading the old
            // "content://media/external/audio/albumart" path directly has not been
            // permitted since Android 10 and throws rather than returning null.
            resolver.loadThumbnail(uri, Size(decodeSize, decodeSize), null)
        } else {
            // minSdk is 26, so two releases predate loadThumbnail entirely and have to open
            // the stream themselves.
            decodeStream(uri)
        }
    }

    /**
     * Pre-Q path: decode bounds first, then decode again subsampled.
     *
     * Decoding straight to a Bitmap would pull a full-resolution cover into memory - several
     * megabytes each, for something shown at 48dp in a list.
     */
    private fun decodeStream(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val largestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (largestEdge <= 0) return null

        var sampleSize = 1
        while (largestEdge / (sampleSize * 2) >= decodeSize) sampleSize *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun paletteOf(bitmap: Bitmap): Color? {
        val sampled = if (bitmap.width > decodeSize || bitmap.height > decodeSize) {
            bitmap.scale(decodeSize)
        } else {
            bitmap
        }
        return try {
            val pixels = IntArray(sampled.width * sampled.height)
            sampled.getPixels(pixels, 0, sampled.width, 0, 0, sampled.width, sampled.height)
            ArtworkPalette.dominantColor(pixels)
        } catch (_: Exception) {
            null
        } finally {
            if (sampled !== bitmap) sampled.recycle()
        }
    }

    private fun Bitmap.scale(maxEdge: Int): Bitmap {
        val ratio = maxEdge.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            /* filter = */ true
        )
    }
}
