package dev.aurora.player.domain.player

sealed class PlaybackError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
    class Authentication(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
    class SourceUnavailable(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
    class Unsupported(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
    class ProviderUnavailable(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
    class Unknown(message: String, cause: Throwable? = null) : PlaybackError(message, cause)
}
