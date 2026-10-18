package com.example.nasconsports

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun MainActivity.nativeLiveArena(id: String) {
    if (!validEntity(id)) { notice("Invalid entity ID"); return }
    start("Live Arena")
    routeArgs.put("entity", id)
    prefs.edit().putString("creator_entity", id).apply()
    val view = layoutInflater.inflate(R.layout.screen_live_arena, content, true)
    val signal = view.findViewById<TextView>(R.id.live_signal)
    val waiting = view.findViewById<TextView>(R.id.live_waiting)
    val video = view.findViewById<VideoView>(R.id.live_video)
    val chatStatus = view.findViewById<TextView>(R.id.live_chat_status)
    val messages = view.findViewById<LinearLayout>(R.id.live_messages)
    val input = view.findViewById<EditText>(R.id.live_message)
    val send = view.findViewById<MaterialButton>(R.id.live_send)
    val tab = view.findViewById<TabLayout>(R.id.live_tabs)
    val chatPanel = view.findViewById<View>(R.id.live_chat_panel)
    val stats = view.findViewById<LinearLayout>(R.id.live_stats_panel)
    tab.addTab(tab.newTab().setText("Live fan chat")); tab.addTab(tab.newTab().setText("Match stats"))
    tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(t: TabLayout.Tab) { chatPanel.visibility = if (t.position == 0) View.VISIBLE else View.GONE; stats.visibility = if (t.position == 1) View.VISIBLE else View.GONE }
        override fun onTabUnselected(t: TabLayout.Tab) {}
        override fun onTabReselected(t: TabLayout.Tab) {}
    })
    arenaVideo = video
    fetch("/web/api/context/${Uri.encode(id)}") { context ->
        signal.text = "ARENA · ${context.optJSONObject("match")?.optString("status")?.replace('_', ' ')?.uppercase() ?: "STREAM ROOM"}"
        val match = context.optJSONObject("match")
        if (match != null) {
            view.findViewById<LinearLayout>(R.id.live_score_container).addView(liveScoreboard(match))
            text("${match.optString("sport_id").uppercase()} · ${match.optString("status").replace('_', ' ')}", 15f, true, stats)
            text("Score version ${match.optInt("version")}", 12f, false, stats)
            button("Open Match Center", stats) { matchScreen(id) }
        } else text("${context.optJSONObject("session")?.optInt("participant_count") ?: 1} broadcaster(s) in this room.", 13f, false, stats)
        val media = context.optString("media_path").takeIf { it.isNotBlank() && it != "null" }
        if (media != null && !forcedOffline()) {
            val server = Uri.parse(api.base)
            val host = server.host.orEmpty().let { if (it.contains(':')) "[$it]" else it }
            video.visibility = View.VISIBLE
            video.setMediaController(MediaController(this))
            video.setOnPreparedListener { waiting.visibility = View.GONE; signal.text = "● LIVE PLAYBACK"; video.start() }
            video.setOnErrorListener { _, _, _ -> video.visibility = View.GONE; waiting.text = "Live video is unavailable. Refresh after the broadcaster connects."; waiting.visibility = View.VISIBLE; signal.text = "WAITING FOR SIGNAL"; true }
            video.setVideoURI(Uri.parse("${server.scheme}://$host:8888/$media/index.m3u8"))
        } else { waiting.text = if (forcedOffline()) "Offline · connect to watch live video" else "Waiting for the broadcaster"; signal.text = "WAITING FOR SIGNAL" }
        (context.optJSONArray("products") ?: JSONArray()).objects().firstOrNull()?.let { product ->
            val merch = layoutInflater.inflate(R.layout.item_live_merch, view.findViewById(R.id.live_merch), true)
            merch.findViewById<TextView>(R.id.live_merch_name).text = product.optString("name")
            merch.findViewById<TextView>(R.id.live_merch_price).text = money(product.optJSONArray("variants")?.optJSONObject(0)?.optLong("price_minor") ?: 0)
            merch.findViewById<MaterialButton>(R.id.live_merch_bag).setOnClickListener { productScreen(product) }
        }
        val actions = mutableListOf(
            ArenaAction("Refresh arena", "Reload video and room", R.drawable.ic_play_circle) { arenaScreen(id) },
            ArenaAction("VOD archive", "Replays and recordings", R.drawable.ic_play_circle) { vodArchive(if (context.optJSONObject("player") != null) id else "") },
            ArenaAction("Matchday store", "Official merchandise", R.drawable.ic_bag) { storeScreen() },
            ArenaAction("Live alerts", "Player with score notifications", R.drawable.ic_bell) { startActivity(Intent(this, Day2LiveActivity::class.java).setData(Uri.parse("nascon://entity/$id"))) }
        )
        val athlete = context.optJSONObject("player")?.optString("player_id")
        if (athlete != null) actions.add(ArenaAction("Athlete profile", "Follow and career records", R.drawable.ic_person) { athleteScreen(athlete) })
        actionGrid(actions, view.findViewById(R.id.live_actions))
        val room = context.optString("chat_room_id", "chat-$id")
        fun renderChat(result: JSONObject) {
            val entries = (result.optJSONArray("messages") ?: JSONArray()).objects().takeLast(30)
            messages.removeAllViews()
            chatStatus.text = if (entries.isEmpty()) "Be the first to cheer for your team." else "Latest ${entries.size} messages"
            for (entry in entries) {
                val row = layoutInflater.inflate(R.layout.item_chat_message, messages, false)
                val sender = entry.optString("sender", "Fan")
                row.findViewById<TextView>(R.id.chat_avatar).text = sender.take(2).uppercase()
                row.findViewById<TextView>(R.id.chat_sender).text = sender
                row.findViewById<TextView>(R.id.chat_text).text = entry.optString("text")
                messages.addView(row)
            }
        }
        val path = "/api/v1/chat/${Uri.encode(room)}"
        val stamp = generation
        val handler = Handler(Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                if (isDestroyed || stamp != generation) return
                if (forcedOffline() || !hasWindowFocus() || busy) { handler.postDelayed(this, 2000); return }
                work({ api.call(path).also { local.put("$owner:$path", it) } }, { renderChat(it); handler.postDelayed(this, 2000) }, {
                    chatStatus.text = "Chat connection unavailable. Retrying…"; handler.postDelayed(this, 4000)
                })
            }
        }
        local.get("$owner:$path")?.let { renderChat(it) }
        if (forcedOffline()) chatStatus.text = "Saved chat · connect to send and receive new messages."
        handler.post(poll)
        send.setOnClickListener {
            if (busy) return@setOnClickListener
            val message = input.text.toString().trim()
            if (message.isEmpty()) { input.error = "Enter a message"; return@setOnClickListener }
            creatorWrite("/api/v1/chat", JSONObject().put("chat_room_id", room).put("message_id", "android-" + UUID.randomUUID()).put("text", message)) { result ->
                input.setText("")
                chatStatus.text = if ((result.optJSONArray("kicked_users")?.length() ?: 0) > 0) "Message blocked by moderation." else "Message processed by server moderation."
            }
        }
        input.setOnEditorActionListener { _, action, _ -> if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { send.performClick(); true } else false }
    }
}

fun MainActivity.liveScoreboard(match: JSONObject): View {
    val card = layoutInflater.inflate(R.layout.item_fixture_card, content, false)
    card.findViewById<TextView>(R.id.fixture_competition).text = match.optString("sport_id").uppercase()
    card.findViewById<TextView>(R.id.fixture_status).text = match.optString("status").replace('_', ' ').uppercase()
    card.findViewById<TextView>(R.id.fixture_home).text = match.optJSONObject("home")?.optString("team_id")?.replace('-', ' ')
    card.findViewById<TextView>(R.id.fixture_away).text = match.optJSONObject("away")?.optString("team_id")?.replace('-', ' ')
    card.findViewById<TextView>(R.id.fixture_score).text = "${match.optJSONObject("home")?.optInt("score") ?: 0} : ${match.optJSONObject("away")?.optInt("score") ?: 0}"
    card.findViewById<ImageView>(R.id.fixture_home_badge).setImageResource(R.drawable.team_crest_blue)
    card.findViewById<ImageView>(R.id.fixture_away_badge).setImageResource(R.drawable.team_crest_gold)
    card.findViewById<TextView>(R.id.fixture_meta).text = "SERVER SCORE · VERSION ${match.optInt("version")}"
    return card
}
