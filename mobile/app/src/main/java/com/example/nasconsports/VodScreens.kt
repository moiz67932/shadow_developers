package com.example.nasconsports

import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject

fun MainActivity.defaultArenaEntity(): String =
    (data.optJSONArray("fixtures") ?: JSONArray()).objects().firstOrNull()?.optString("match_id")
        ?: (data.optJSONArray("players") ?: JSONArray()).objects().firstOrNull()?.optString("player_id")
        ?: (data.optJSONArray("clubs") ?: JSONArray()).objects().firstOrNull()?.optString("club_id")
        ?: "match-m1"

fun MainActivity.vodArchive(athlete: String = "", initialQuery: String = "", initialFilter: String = "All VODs") {
    start("VOD Archive")
    routeArgs.put("athlete", athlete).put("query", initialQuery).put("filter", initialFilter)
    val view = layoutInflater.inflate(R.layout.screen_vod_archive, content, true)
    val search = view.findViewById<EditText>(R.id.vod_search)
    val filters = view.findViewById<ChipGroup>(R.id.vod_filters)
    val featured = view.findViewById<LinearLayout>(R.id.vod_featured)
    val grid = view.findViewById<LinearLayout>(R.id.vod_grid)
    val count = view.findViewById<TextView>(R.id.vod_count)
    val empty = view.findViewById<TextView>(R.id.vod_empty)
    var entries = emptyList<JSONObject>()
    var selected = initialFilter
    val bookmarkKey = "vod_bookmarks:$owner"
    fun saved() = prefs.getStringSet(bookmarkKey, emptySet()).orEmpty().toSet()
    fun render() {
        featured.removeAllViews(); grid.removeAllViews()
        val query = search.text.toString().trim()
        routeArgs.put("query", query).put("filter", selected)
        val visible = entries.filter { vod ->
            val isComposite = !vod.isNull("session_id") && vod.optString("session_id").isNotBlank()
            (query.isBlank() || listOf("vod_id", "player_id", "session_id", "created_at").any { vod.optString(it).contains(query, true) }) &&
                when (selected) { "Athletes" -> !isComposite; "Co-streams" -> isComposite; "Saved" -> vod.optString("vod_id") in saved(); else -> true }
        }
        count.text = "VOD ARCHIVE & HIGHLIGHTS · ${visible.size} RECORDINGS"
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        empty.text = if (entries.isEmpty()) "No recordings yet. Published recordings appear here after a broadcast has been archived." else "No recordings match this search or filter."
        fun tile(vod: JSONObject, parent: LinearLayout, hero: Boolean): View {
            val card = layoutInflater.inflate(R.layout.item_vod_card, parent, false)
            val id = vod.getString("vod_id")
            val composite = !vod.isNull("session_id") && vod.optString("session_id").isNotBlank()
            val title = if (composite) "Co-stream · ${vod.optString("session_id")}" else "Athlete · ${vod.optString("player_id", "Recording")}" 
            card.findViewById<TextView>(R.id.vod_title).text = title
            card.findViewById<TextView>(R.id.vod_meta).text = vod.optString("created_at").replace('T', ' ').substringBefore('.')
            card.findViewById<TextView>(R.id.vod_kind).text = if (composite) "CO-STREAM REPLAY" else "ATHLETE REPLAY"
            card.findViewById<ImageView>(R.id.vod_thumbnail).setImageResource(if (composite || hero) R.drawable.vod_arena_cover else R.drawable.vod_replay_cover)
            card.findViewById<View>(R.id.vod_play).apply {
                contentDescription = "Play $title"
                if (hero) layoutParams.height = dp(190)
                setOnClickListener { vodPlayer(vod) }
            }
            card.findViewById<MaterialButton>(R.id.vod_save).apply {
                text = if (id in saved()) "Saved ✓" else "Save replay"
                contentDescription = "$text: $title"
                setOnClickListener {
                    val next = saved().toMutableSet(); if (!next.add(id)) next.remove(id)
                    prefs.edit().putStringSet(bookmarkKey, next).apply(); render()
                }
            }
            return card
        }
        visible.firstOrNull()?.let { featured.addView(tile(it, featured, true)) }
        visible.drop(1).chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2))
            pair.forEachIndexed { index, vod -> row.addView(tile(vod, row, false), LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(6) else leftMargin = dp(6) }) }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
    }
    for (name in listOf("All VODs", "Athletes", "Co-streams", "Saved")) {
        val chip = Chip(this).apply {
            text = name; isCheckable = true; isChecked = name == selected; minHeight = dp(48)
            setOnClickListener {
                selected = name
                for (i in 0 until filters.childCount) (filters.getChildAt(i) as Chip).isChecked = (filters.getChildAt(i) as Chip).text.toString() == name
                render()
            }
        }
        filters.addView(chip)
    }
    search.setText(initialQuery)
    search.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
        override fun afterTextChanged(s: Editable?) {}
    })
    view.findViewById<MaterialButton>(R.id.vod_refresh).setOnClickListener { vodArchive(athlete, search.text.toString(), selected) }
    // The existing context API is the available VOD catalogue contract; no invented list endpoint.
    val entity = athlete.takeIf { validEntity(it) } ?: defaultArenaEntity()
    fetch("/web/api/context/${Uri.encode(entity)}") { result ->
        entries = (result.optJSONArray("vods") ?: JSONArray()).objects()
        render()
        text(if (forcedOffline() || !online) "Saved catalogue · video playback requires a connection." else "Latest available recordings · cover artwork is illustrative.", 11f)
    }
}

fun MainActivity.vodPlayer(vod: JSONObject, position: Int = 0) {
    val id = vod.optString("vod_id")
    if (!validEntity(id)) { vodArchive(); return }
    start("VOD Player")
    routeArgs.put("vod", vod).put("position", position)
    prefs.edit().putString("last_vod", vod.toString()).apply()
    val view = layoutInflater.inflate(R.layout.screen_vod_player, content, true)
    val video = view.findViewById<VideoView>(R.id.replay_video)
    val loading = view.findViewById<View>(R.id.replay_loading)
    val status = view.findViewById<TextView>(R.id.replay_status)
    val retry = view.findViewById<MaterialButton>(R.id.replay_retry)
    view.findViewById<TextView>(R.id.replay_title).text = "Replay · $id"
    arenaVideo = video
    video.setMediaController(MediaController(this))
    fun play() {
        if (forcedOffline()) { loading.visibility = View.GONE; status.text = "Connect and turn off Work offline to play this recording."; return }
        loading.visibility = View.VISIBLE
        status.text = "Preparing recording…"
        video.setVideoURI(Uri.parse(api.base + "/api/v1/vods/${Uri.encode(id)}/play"), mapOf("Authorization" to "Bearer ${api.token}"))
    }
    video.setOnPreparedListener {
        loading.visibility = View.GONE; status.text = "Recording ready · use the player controls to pause or seek."
        if (position > 0) video.seekTo(position)
        video.start()
    }
    video.setOnCompletionListener { status.text = "Replay finished. Tap Retry playback to watch again." }
    video.setOnErrorListener { _, _, _ -> loading.visibility = View.GONE; status.text = "Recording unavailable. Check your connection or retry after storage is ready."; true }
    retry.setOnClickListener { play() }
    view.findViewById<MaterialButton>(R.id.replay_share).setOnClickListener {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, api.base + "/api/v1/vods/${Uri.encode(id)}/play"), "Share replay"))
    }
    view.findViewById<MaterialButton>(R.id.replay_archive).setOnClickListener { vodArchive() }
    view.findViewById<MaterialButton>(R.id.replay_athlete).apply {
        val athlete = vod.optString("player_id")
        visibility = if (validEntity(athlete)) View.VISIBLE else View.GONE
        setOnClickListener { athleteScreen(athlete) }
    }
    play()
}
