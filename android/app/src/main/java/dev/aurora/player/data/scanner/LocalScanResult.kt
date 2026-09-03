package dev.aurora.player.data.scanner

sealed class LocalScanResult {
    data class Success(val tracks: List<ScannedTrack>) : LocalScanResult()
    data class Failed(val reason: String, val exception: Throwable? = null) : LocalScanResult()
    data class Cancelled(val reason: String) : LocalScanResult()
    data class PermissionDenied(val reason: String) : LocalScanResult()
}
