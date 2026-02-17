package com.mainlert.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    playlistId: String = "PLhFO614gb9CpY3ABVEPe_knvJmSXQfpVB",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }
    }

    DisposableEffect(playlistId) {
        // YouTube IFrame API with playlist, autoplay, mute, and loop
            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { margin: 0; padding: 0; width: 100%; background: transparent; overflow: hidden; }
                    #player { width: 100%; position: absolute; top: 0; left: 0; }
                    iframe { width: 100%; border: none; display: block; position: absolute; top: 0; left: 0; }
                </style>
            </head>
            <body>
                <div id="player"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            width: '100%',
                            playerVars: {
                                'listType': 'playlist',
                                'list': '$playlistId',
                                'autoplay': 1,
                                'mute': 1,
                                'loop': 1,
                                'controls': 0,
                                'rel': 0,
                                'showinfo': 0,
                                'modestbranding': 1,
                                'playsinline': 1,
                                'iv_load_policy': 3,
                                'origin': window.location.origin
                            },
                            events: {
                                'onReady': function(event) {
                                    event.target.playVideo();
                                },
                                'onStateChange': function(event) {
                                    if (event.data == YT.PlayerState.ENDED) {
                                        event.target.playVideo();
                                    }
                                }
                            }
                        });
                    }
                    function mutePlayer() {
                        if (player && player.mute) {
                            player.mute();
                        }
                    }
                    function unmutePlayer() {
                        if (player && player.unMute) {
                            player.unMute();
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://youtube.com", html, "text/html", "utf-8", null)

        onDispose {
            webView.destroy()
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        // Mute/Unmute button at top left
        IconButton(
            onClick = {
                if (isMuted) {
                    webView.evaluateJavascript("unmutePlayer()", null)
                    isMuted = false
                } else {
                    webView.evaluateJavascript("mutePlayer()", null)
                    isMuted = true
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
