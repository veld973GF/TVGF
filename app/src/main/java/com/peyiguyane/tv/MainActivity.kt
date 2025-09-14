package com.peyiguyane.tv

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.DefaultLoadControl
import com.google.android.exoplayer2.util.Util
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    data class Stream(
        val name: String,
        val url: String,
        val headers: Map<String, String> = emptyMap()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.playerView)
        showPickerAndStart()
    }

    private fun showPickerAndStart() {
        val streams = loadStreams()
        val names = streams.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choisissez un flux")
            .setItems(names) { _, which ->
                startPlayer(streams[which])
            }
            .setCancelable(false)
            .show()
    }

    private fun loadStreams(): List<Stream> {
        val br = BufferedReader(InputStreamReader(assets.open("streams.json")))
        val text = br.readText()
        br.close()
        val arr = JSONArray(text)
        val list = mutableListOf<Stream>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.getString("name")
            val url = o.getString("url")
            val headers = mutableMapOf<String, String>()
            if (o.has("headers")) {
                val h = o.getJSONObject("headers")
                val it = h.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    headers[k] = h.getString(k)
                }
            }
            list.add(Stream(name, url, headers))
        }
        return list
    }

    private fun startPlayer(stream: Stream) {
        releasePlayer()

        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,   // minBufferMs (~5s)
                10000,  // maxBufferMs
                2500,   // bufferForPlaybackMs
                5000    // bufferForPlaybackAfterRebufferMs
            ).build()

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        val mediaItem = MediaItem.fromUri(Uri.parse(stream.url))

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(stream.headers["User-Agent"] ?: Util.getUserAgent(this, "PeyiTV"))
            .setAllowCrossProtocolRedirects(true)

        // Appliquer les en-têtes custom (Referer/Origin), sans toucher au User-Agent
        if (stream.headers.isNotEmpty()) {
            val headersMap = HashMap<String, String>()
            for ((k, v) in stream.headers) {
                if (!k.equals("User-Agent", true)) {
                    headersMap[k] = v
                }
            }
            httpFactory.setDefaultRequestProperties(headersMap)
        }

        val mediaSource: MediaSource = if (stream.url.contains(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        }

        playerView.player = player
        player!!.setMediaSource(mediaSource)
        player!!.prepare()
        player!!.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
