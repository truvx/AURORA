package dev.aurora.player.data.player

import dev.aurora.player.app.EngineEvent
import dev.aurora.player.app.PlayerAdapter
import dev.aurora.player.domain.models.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YouTubePlayerAdapter : PlayerAdapter {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events

    /*
     * The IFrame player takes a moment to exist.
     *
     * The page has to fetch YouTube's API and construct a player before any of the JS
     * functions below do anything, and until then `loadVideo()` calls a method on an
     * undefined object and silently does nothing. Every track chosen in the first seconds
     * after a cold start was dropped that way: the transport said Pause, the position
     * stayed empty and no audio was ever produced.
     */
    private var isPlayerReady = false
    private var pendingVideoId: String? = null
    private var pendingPlay = false

    private var _webView: android.webkit.WebView? = null
    val webView: android.webkit.WebView?
        get() = _webView

    data class YouTubeState(
        val videoId: String? = null,
        val shouldPlay: Boolean = false,
        val seekToMs: Long? = null,
        val volume: Float = 1.0f
    )

    private val _state = MutableStateFlow(YouTubeState())
    val state: StateFlow<YouTubeState> = _state.asStateFlow()

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    fun initializeWebView(context: android.content.Context) {
        if (_webView != null) return
        _webView = android.webkit.WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true // Required for YouTube player
            webChromeClient = object : android.webkit.WebChromeClient() {
                // Without this the player fails in silence: a JavaScript error inside the
                // page never reaches logcat, which is why a broken player looked exactly
                // like a working one.
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.d(
                        "AuroraYouTube",
                        "${message.messageLevel()}: ${message.message()} (line ${message.lineNumber()})"
                    )
                    return true
                }
            }
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    // The player navigates within YouTube's own domains; anything else is
                    // a link out of the app and is not followed here. Returning true for
                    // everything also blocked the player's own frames.
                    val host = request?.url?.host?.lowercase().orEmpty()
                    val allowed = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                    return !allowed
                }
            }
            addJavascriptInterface(object : Any() {
                @android.webkit.JavascriptInterface
                fun onReady() {
                    isPlayerReady = true
                    onPrepared()
                    // Whatever was chosen while the player was still loading.
                    pendingVideoId?.let { queued ->
                        pendingVideoId = null
                        // Back to the main thread: this arrives on the JS bridge thread,
                        // and WebView methods may only be called where it was created.
                        _webView?.post { sendLoad(queued, pendingPlay) }
                    }
                }

                @android.webkit.JavascriptInterface
                fun onStateChange(state: Int) {
                    when (state) {
                        1 -> onPlaying()
                        2 -> onPaused()
                        3 -> onBuffering(true)
                        0 -> onTrackCompleted()
                    }
                    if (state != 3) {
                        onBuffering(false)
                    }
                }

                @android.webkit.JavascriptInterface
                fun onError(error: Int) {
                    /*
                     * The player's own codes, in words.
                     *
                     * 101 and 150 are a video whose owner does not allow it off YouTube,
                     * and 152 behaves the same way. AURORA does not work around that - the
                     * capability matrix rules it out - so the only honest thing left is to
                     * say so. Reported as a number, this looked like a dead player.
                     */
                    val reason = when (error) {
                        2 -> "YouTube rejected the video id."
                        5 -> "YouTube's player could not play this video here."
                        100 -> "That video is gone or private."
                        101, 150, 152 ->
                            "The owner of this video does not allow it to play outside YouTube."
                        else -> "YouTube's player stopped with error $error."
                    }
                    onError(Exception(reason))
                }

                @android.webkit.JavascriptInterface
                fun onTimeUpdate(time: Float, duration: Float) {
                    onPositionChanged((time * 1000).toLong(), (duration * 1000).toLong())
                }
            }, "AuroraBridge")

            val html = """
                <!DOCTYPE html>
                <html>
                  <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                      body { margin: 0; background-color: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; height: 100vh; }
                      iframe { width: 100%; height: 100%; border: none; }
                    </style>
                  </head>
                  <body>
                    <div id="player"></div>
                    <script>
                      var tag = document.createElement('script');
                      tag.src = "https://www.youtube.com/iframe_api";
                      var firstScriptTag = document.getElementsByTagName('script')[0];
                      firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                      var player;
                      function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                          height: '100%',
                          width: '100%',
                          playerVars: {
                            'playsinline': 1,
                            // No control bar: AURORA's transport drives the player through
                            // the IFrame API. Every value here is a documented player
                            // parameter. The title, channel avatar, YouTube logo and ads are
                            // the player's to show and are left alone.
                            'controls': 0,
                            'fs': 0,
                            'iv_load_policy': 3,
                            'rel': 0,
                            'origin': 'https://aurora.local'
                          },
                          events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange,
                            'onError': onPlayerError
                          }
                        });
                      }

                      function onPlayerReady(event) {
                        AuroraBridge.onReady();
                        setInterval(function() {
                          // Only report while actually playing. This timer runs from the
                          // moment the player is ready, and an unguarded tick reports 0/0
                          // whenever no video is loaded.
                          if (player && player.getCurrentTime && player.getPlayerState) {
                            var state = player.getPlayerState();
                            var PLAYING = 1, BUFFERING = 3;
                            if (state === PLAYING || state === BUFFERING) {
                              AuroraBridge.onTimeUpdate(player.getCurrentTime(), player.getDuration());
                            }
                          }
                        }, 500);
                      }

                      function onPlayerStateChange(event) {
                        AuroraBridge.onStateChange(event.data);
                      }

                      function onPlayerError(event) {
                        AuroraBridge.onError(event.data);
                      }

                      function loadVideo(videoId) {
                        if (player && player.loadVideoById) {
                          player.loadVideoById(videoId);
                        }
                      }

                      function playVideo() {
                        if (player && player.playVideo) {
                          player.playVideo();
                        }
                      }

                      function pauseVideo() {
                        if (player && player.pauseVideo) {
                          player.pauseVideo();
                        }
                      }

                      function seekTo(seconds) {
                        if (player && player.seekTo) {
                          player.seekTo(seconds, true);
                        }
                      }

                      function setVolume(volume) {
                        if (player && player.setVolume) {
                          player.setVolume(volume * 100);
                        }
                      }
                    </script>
                  </body>
                </html>
            """.trimIndent()
            
            /*
             * A reserved origin, not a registrable domain.
             *
             * The page used to claim "https://aurora.dev", a domain this project does not
             * own and anybody could buy. ".local" is reserved by RFC 6762 and can never be
             * registered, so it names this page without pretending to be anyone.
             *
             * Not "https://www.youtube.com" either, which is the conventional choice and
             * was tried: the IFrame player answers every load with error 152 when a page
             * that is not YouTube claims to be YouTube. Measured, not guessed.
             */
            loadDataWithBaseURL("https://aurora.local", html, "text/html", "UTF-8", null)
        }
    }

    override fun load(track: MediaItem, uri: String, playWhenReady: Boolean, crossfadeDurationMs: Long) {
        // IFrame adapter doesn't support crossfade natively right now
        // It should just load and play based on playWhenReady
        _state.value = _state.value.copy(
            videoId = uri,
            shouldPlay = playWhenReady,
            seekToMs = 0L
        )
        if (isPlayerReady) {
            sendLoad(uri, playWhenReady)
        } else {
            pendingVideoId = uri
            pendingPlay = playWhenReady
        }
    }

    /** Only ever called once the player exists. */
    private fun sendLoad(videoId: String, playWhenReady: Boolean) {
        // A video id is [A-Za-z0-9_-]{11}, but it arrives from a provider response, and
        // docs/SECURITY.md treats those as untrusted until checked. Anything else would be
        // going into a JavaScript string literal.
        if (!VIDEO_ID.matches(videoId)) {
            onError(IllegalArgumentException("Not a YouTube video id: $videoId"))
            return
        }
        _webView?.evaluateJavascript("loadVideo('$videoId');", null)
        if (playWhenReady) {
            _webView?.evaluateJavascript("playVideo();", null)
        }
    }

    override fun play() {
        _state.value = _state.value.copy(shouldPlay = true)
        _webView?.evaluateJavascript("playVideo();", null)
    }

    override fun pause() {
        _state.value = _state.value.copy(shouldPlay = false)
        _webView?.evaluateJavascript("pauseVideo();", null)
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(seekToMs = positionMs)
        _webView?.evaluateJavascript("seekTo(${positionMs / 1000f});", null)
    }

    override fun setVolume(volume: Float) {
        _state.value = _state.value.copy(volume = volume)
        _webView?.evaluateJavascript("setVolume($volume);", null)
    }

    override fun setAudioGain(linearGain: Float) {
        // No-op: YouTube handles its own loudness normalization.
    }

    override fun release() {
        _state.value = YouTubeState()
        isPlayerReady = false
        pendingVideoId = null
        _webView?.let {
            it.loadUrl("about:blank")
            it.clearHistory()
            it.removeAllViews()
            it.destroy()
        }
        _webView = null
    }
    
    fun clearSeek() {
        _state.value = _state.value.copy(seekToMs = null)
    }

    fun onPrepared() {
        _events.tryEmit(EngineEvent.Prepared)
    }

    fun onPlaying() {
        _events.tryEmit(EngineEvent.Started)
    }

    fun onPaused() {
        _events.tryEmit(EngineEvent.Paused)
    }
    
    fun onBuffering(isBuffering: Boolean) {
        _events.tryEmit(EngineEvent.BufferingChanged(isBuffering))
    }

    fun onPositionChanged(elapsed: Long, duration: Long?) {
        _events.tryEmit(EngineEvent.PositionChanged(elapsed, duration, null))
    }

    fun onTrackCompleted() {
        _events.tryEmit(EngineEvent.TrackCompleted)
    }

    fun onError(error: Throwable) {
        _events.tryEmit(EngineEvent.Error(error))
    }
}

private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

private val ALLOWED_HOSTS = listOf("youtube.com", "youtube-nocookie.com", "ytimg.com", "google.com", "gstatic.com")
