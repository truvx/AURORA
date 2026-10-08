package dev.aurora.player.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pauses playback after a chosen number of minutes.
 *
 * Process-wide, beside the coordinator, so it keeps counting after Now Playing closes. It
 * pauses through the same command every other control sends, so it never touches a player.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val pause: () -> Unit,
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    private val _endsAt = MutableStateFlow<Long?>(null)

    /** When it fires, on [clock]'s timeline, or null when no timer is set. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    private var job: Job? = null

    fun start(minutes: Int) {
        cancel()
        val millis = minutes * 60_000L
        _endsAt.value = clock() + millis
        job = scope.launch {
            delay(millis)
            _endsAt.value = null
            pause()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _endsAt.value = null
    }

    /** Milliseconds left, for display. */
    fun remaining(): Long? = _endsAt.value?.let { (it - clock()).coerceAtLeast(0) }
}
