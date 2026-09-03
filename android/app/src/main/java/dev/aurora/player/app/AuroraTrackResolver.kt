package dev.aurora.player.app

import dev.aurora.player.data.db.LocalLibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuroraTrackResolver(
    private val localDao: LocalLibraryDao
) : TrackResolver {
    override suspend fun resolveUri(trackId: String): String? = withContext(Dispatchers.IO) {
        if (trackId.startsWith("youtube:")) {
            return@withContext trackId.removePrefix("youtube:")
        }
        // Find the actual playable source URI for the given track ID.
        val fileEntity = localDao.getLocalFileForMedia(trackId)
        fileEntity?.uri
    }
}
