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
            webChromeClient = android.webkit.WebChromeClient()
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    return true
                }
            }
            addJavascriptInterface(object : Any() {
                @android.webkit.JavascriptInterface
                fun onReady() {
                    onPrepared()
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
                    onError(Exception("YouTube Player Error: $error"))
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
                            'controls': 0,
                            'disablekb': 1,
                            'fs': 0,
                            'rel': 0,
                            'modestbranding': 1,
                            'origin': 'https://aurora.dev'
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
            
            loadDataWithBaseURL("https://aurora.dev", html, "text/html", "UTF-8", null)
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
        _webView?.evaluateJavascript("loadVideo('$uri');", null)
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
