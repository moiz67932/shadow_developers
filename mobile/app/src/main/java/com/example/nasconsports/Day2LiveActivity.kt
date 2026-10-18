package com.example.nasconsports

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import android.widget.MediaController
import android.widget.Button
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Isolated Day 2 source integration. No dependency on Stitch layouts or login routing. */
class Day2LiveActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private var connection: HttpURLConnection? = null
    private var generation = 0
    private lateinit var root: LinearLayout
    private lateinit var score: TextView
    private lateinit var video: VideoView
    private val server: String get() = getSharedPreferences("settings", MODE_PRIVATE).getString("server", "http://127.0.0.1:8080")!!.trimEnd('/')

    override fun onCreate(state: Bundle?) { super.onCreate(state); render(intent) }
    override fun onNewIntent(value: Intent) { super.onNewIntent(value); setIntent(value); render(value) }
    private fun render(value: Intent) {
        connection?.disconnect(); generation++
        val current = generation
        val uri = value.data
        val entity = uri?.lastPathSegment ?: "match-m1"
        if (uri?.scheme != "nascon" || uri.host !in listOf("entity", "stream") || !entity.matches(Regex("[A-Za-z0-9_.:-]{1,64}"))) { finish(); return }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 16); setBackgroundColor(0xff080e1c.toInt()) }
        setContentView(root)
        val title = TextView(this).apply { text = "NASCON ARENA · $entity"; textSize = 21f; setTextColor(0xff7dbbff.toInt()); gravity = Gravity.CENTER }
        root.addView(title)
        score = TextView(this).apply { text = "Connecting to authoritative score…"; textSize = 24f; setTextColor(0xffffffff.toInt()); contentDescription = "live-score-banner" }
        root.addView(score)
        video = VideoView(this).apply { contentDescription = "guest-video-player"; setMediaController(MediaController(this@Day2LiveActivity)) }
        root.addView(video, LinearLayout.LayoutParams(-1, 640))
        root.addView(Button(this).apply { text = "Open full guest arena"; setOnClickListener { val route = if (entity.startsWith("cs-")) "/web/costream/$entity" else if(entity.startsWith("match-")) "/web/matches/$entity/live" else "/web/players/$entity"; startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(server + route))) } })
        root.addView(Button(this).apply { text = "Enable live notifications"; setOnClickListener { if(Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2026) } })
        io.execute {
            try {
                val context = JSONObject(URL("$server/web/api/context/$entity").readText())
                val media = context.optString("media_path", "")
                runOnUiThread {
                    if (current == generation && !isFinishing) {
                        context.optJSONObject("match")?.let { updateScore(it, false) }
                        context.optJSONObject("session")?.let { score.text = "${it.optString("layout")} · ${it.optInt("participant_count")} broadcasters" }
                        if (media.isNotBlank() && media != "null") { val host = Uri.parse(server).host ?: "127.0.0.1"; video.setVideoURI(Uri.parse("http://$host:8888/$media/index.m3u8")); video.setOnPreparedListener { video.start() } }
                    }
                }
                if (entity.startsWith("match-")) {
                    val stream = URL("$server/api/v1/matches/$entity/stream").openConnection() as HttpURLConnection
                    connection = stream; stream.connectTimeout = 3000; stream.readTimeout = 0
                    stream.inputStream.bufferedReader().use { input ->
                        while(current == generation && !isFinishing) {
                            val line = input.readLine() ?: break
                            if(line.startsWith("data: ")) { val data = JSONObject(line.removePrefix("data: ")); runOnUiThread { if(current == generation && !isFinishing) updateScore(data, true) } }
                        }
                    }
                }
            } catch (error: Exception) { runOnUiThread { if(current == generation && !isFinishing) score.text = "Live connection unavailable. Retry the deep link." } }
        }
    }
    private fun updateScore(data: JSONObject, notify: Boolean) {
        val value = "${data.getJSONObject("home").optInt("score")} - ${data.getJSONObject("away").optInt("score")}"; val changed = score.text.toString() != value; score.text = value
        if(notify && changed && Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("arena_scores", "Live match scores", NotificationManager.IMPORTANCE_LOW))
            if(Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.notify(2026, Notification.Builder(this, "arena_scores").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("NaSCon live score").setContentText(value).build())
            }
        }
    }
    override fun onDestroy() { generation++; connection?.disconnect(); video.stopPlayback(); io.shutdownNow(); super.onDestroy() }
}
