package dev.aurora.player.app

import dev.aurora.player.data.db.LocalLibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuroraTrackResolver(
    private val localDao: LocalLibraryDao
) : TrackResolver {
    override suspend fun resolveUri(trackId: String): String? = withContext(Dispatchers.IO) {
        // Find the actual playable source URI for the given track ID.
        // For Phase 5, we only support local files.
        val fileEntity = localDao.getLocalFileForMedia(trackId)
        fileEntity?.uri
    }
}
