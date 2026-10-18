package com.example.nasconsports

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Creator operations require a live server; they never enter the Day 1 offline queue.
fun MainActivity.creatorWrite(path: String, body: JSONObject, method: String = "POST", done: (JSONObject) -> Unit) {
    if (forcedOffline()) { notice("Reconnect and turn off Work offline to use creator controls."); return }
    work({ api.call(path, body, method) }, done)
}
fun MainActivity.playerIdentity(): String = try {
    val claims = JSONObject(String(android.util.Base64.decode(api.token.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)))
    if (claims.isNull("player_id")) "" else claims.optString("player_id")
} catch (_: Exception) { "" }
fun MainActivity.copyValue(label: String, value: String) {
    (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
}
fun MainActivity.openCreatorUrl(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: android.content.ActivityNotFoundException) { notice("No application can open this link.") }
}
fun MainActivity.mediaUrl(value: String): String {
    val original = Uri.parse(value)
    return if (original.host in listOf("localhost", "127.0.0.1")) original.buildUpon().encodedAuthority(Uri.parse(api.base).host.let { host -> if (host?.contains(':') == true) "[$host]:${original.port}" else "$host:${original.port}" }).build().toString() else value
}
fun MainActivity.creatorHub() {
    start("Creator tools"); sectionTitle("Creator arena", "Broadcast, connect your squad and share the action")
    assetImage(R.drawable.match_arena, "NaSCon broadcast arena", 130)
    button("Broadcast Studio") { broadcastStudio() }
    button("Active Stream Director") { streamDirector() }
    button("Match Telemetry & QR") { matchTelemetry() }
    button("Stream Key Controls") { streamKeysScreen() }
    button("Co-stream Lobby") { costreamLobby() }
    button("QR Scanner / open link") { qrScannerScreen() }
    button("QR Share & Export") { qrExportScreen() }
    button("Athlete Profiles") { athleteDirectory() }
    val entity = field("Match, player or co-stream ID", matchId.ifBlank { "match-m1" })
    button("Open live player, chat & merchandise") { val id = entity.text.toString().trim(); if (validEntity(id)) arenaScreen(id) else entity.error = "Enter an entity ID" }
}
fun validEntity(value: String) = value.matches(Regex("[A-Za-z0-9_.:-]{1,64}"))
fun MainActivity.broadcastStudio() {
    start("Broadcast Studio"); sectionTitle("Solo broadcast studio", "Camera publishing through the self-hosted MediaMTX WebRTC publisher")
    assetImage(R.drawable.match_arena, "Broadcast stage", 140)
    val player = playerIdentity()
    if (player.isBlank()) { card("Athlete account required", "Sign in as a player to generate a personal publishing key."); return }
    card("Your athlete identity", player)
    button("Open stream key controls") { streamKeysScreen() }
    val saved = SessionStore(this, "creator_keys").read()?.takeIf { it.optString("owner") == owner }?.optJSONObject("key")
    if (saved?.optString("status") == "active") {
        val publisherUrl = field("Camera publisher URL (HTTPS or loopback)", mediaUrl(saved.getJSONObject("ingest_endpoints").getString("webrtc").removeSuffix("/whip") + "/publish"))
        button("Open camera publisher") {
            if (forcedOffline()) notice("Reconnect before broadcasting.")
            else startActivity(Intent(this, BroadcastActivity::class.java).putExtra("publisher", publisherUrl.text.toString().trim()))
        }
        button("Open Active Stream Director") { streamDirector() }
        button("Watch athlete stream") { arenaScreen(player) }
        button("Record current live stream") { creatorWrite("/api/v1/streams/$player/record", JSONObject()) { notice(if (it.optBoolean("started")) "Server accepted the media start request." else "Media is already running or no new process was started.") } }
    } else card("Generate a key first", "Stream Key Controls stores the server response securely on this device.")
    text("Camera publishing needs a secure media origin (HTTPS or loopback). USB: forward 8889 for WebRTC and 8888 for playback. Remote HTTP origins may deny camera access. This studio does not claim a broadcast is live until the publisher connects.", 13f)
}
fun MainActivity.streamKeysScreen() {
    start("Stream Key Controls"); sectionTitle("Stream keys", "Keys belong to the signed-in athlete")
    if (playerIdentity().isBlank()) { card("Athlete account required", "Use your player account for stream keys."); return }
    val label = field("Key label", "Android studio")
    val vault = SessionStore(this, "creator_keys")
    val saved = vault.read()?.takeIf { it.optString("owner") == owner }?.optJSONObject("key")
    card("Key status", saved?.optString("status") ?: "No key saved on this device")
    if (saved?.optString("status") == "active") {
        button("Copy private stream key") { copyValue("Private stream key", saved.getString("stream_key")) }
        val endpoints = saved.optJSONObject("ingest_endpoints")
        for (kind in listOf("rtmp", "webrtc")) endpoints?.optString(kind)?.takeIf { it.isNotBlank() }?.let { url ->
            button("Copy ${kind.uppercase()} ingest URL") { copyValue("Ingest URL", mediaUrl(url)) }
        }
    }
    for (action in listOf("generate", "rotate", "revoke")) button(action.replaceFirstChar { it.uppercase() } + " key") {
        confirm("${action.replaceFirstChar { it.uppercase() }} your stream key? Existing ingest may be disconnected.") {
            creatorWrite("/api/v1/streams/key", JSONObject().put("action", action).put("label", label.text.toString().trim().ifBlank { "Android studio" })) { result ->
                vault.save(JSONObject().put("owner", owner).put("key", result)); streamKeysScreen()
            }
        }
    }
    button("Return to Broadcast Studio") { broadcastStudio() }
}
fun MainActivity.costreamLobby() {
    start("Co-stream Lobby"); sectionTitle("Your squad. One stream.", "Up to four participants • one synchronized chat")
    val mine = playerIdentity()
    val sessionId = field("Session ID", prefs.getString("creator_session", "")!!.ifBlank { "cs-session-" + UUID.randomUUID().toString().take(8) })
    val participants = field("Participant IDs, comma separated", mine)
    val layouts = listOf("grid_4", "grid_2", "spotlight", "solo")
    val layout = picker("Layout", layouts)
    if (mine.isNotBlank()) button("Create co-stream session") {
        val sid = sessionId.text.toString().trim(); val ids = participants.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (!validEntity(sid) || !sid.startsWith("cs-")) { sessionId.error = "Use a cs- session ID"; return@button }
        if (ids.size !in 1..4 || ids.distinct().size != ids.size || mine !in ids || ids.any { !validEntity(it) }) { participants.error = "1–4 unique player IDs, including yourself"; return@button }
        // Session creation currently exists only on the documented Day 2 audit service.
        creatorWrite("/audit/v1/costream/session", JSONObject().put("session_id", sid).put("host_player_id", mine).put("participants", JSONArray(ids)).put("layout", layouts[layout.selectedItemPosition])) {
            prefs.edit().putString("creator_session", sid).apply(); hostRoom(sid)
        }
    }
    button("Open existing session") { val sid = sessionId.text.toString().trim(); if (validEntity(sid) && sid.startsWith("cs-")) { prefs.edit().putString("creator_session", sid).apply(); hostRoom(sid) } else sessionId.error = "Enter a cs- session ID" }
    val invite = field("Invitation token")
    button("Join with invitation") {
        val sid = sessionId.text.toString().trim()
        if (!validEntity(sid) || invite.text.isBlank()) { invite.error = "Enter session ID and signed invitation"; return@button }
        creatorWrite("/api/v1/costream/$sid/join", JSONObject().put("invite_token", invite.text.toString().trim())) { prefs.edit().putString("creator_session", sid).apply(); hostRoom(sid) }
    }
    text("Create a session with registered athlete IDs. Each athlete publishes their own stream; joining alone does not start a camera.", 13f)
}
fun MainActivity.hostRoom(id: String = prefs.getString("creator_session", "") ?: "") {
    if (!validEntity(id)) { costreamLobby(); return }
    start("Co-stream Host Room"); sectionTitle("Production suite", "Co-stream Host Room • $id")
    assetImage(R.drawable.match_arena, "Co-stream production arena", 125)
    fetch("/web/api/context/$id") { context ->
        val s = context.optJSONObject("session")
        if (s == null) { card("Session unavailable"); return@fetch }
        creatorStats(listOf("HOSTS" to s.optInt("participant_count").toString(), "LAYOUT" to s.optString("layout"), "STATUS" to s.optString("status")))
        card("Host management", "Current host: ${s.optString("active_host")}")
        button("Watch composite & open chat") { arenaScreen(id) }
        button("Refresh host and participants") { hostRoom(id) }
        val controls = role == "super_admin" || playerIdentity().let { it.isNotBlank() && it == s.optString("active_host") }
        if (!controls) { card("Audience / participant view", "Only the current host can moderate or start the composite."); return@fetch }
        button("Start composite") { creatorWrite("/api/v1/costream/$id/start", JSONObject().put("layout", s.optString("layout"))) { notice(if (it.optBoolean("started")) "Server accepted the media start request." else "Media is already running or no new process was started.") } }
        button("Stop composite") { confirm("Stop this composite stream?") { creatorWrite("/api/v1/costream/$id/stop", JSONObject()) { notice("Server confirmed: stopped=${it.optBoolean("stopped")}") } } }
        sectionTitle("Host management", "Live controls are confirmed by your server")
        val members = s.optJSONArray("active_participants") ?: JSONArray()
        for (i in 0 until members.length()) {
            val player = members.getString(i); val box = card(player, if (player == s.optString("active_host")) "Current host" else "Participant")
            button("Generate invitation", box) { creatorWrite("/api/v1/costream/$id/invite", JSONObject().put("player_id", player)) { copyValue("Invitation token", it.getString("invite_token")); notice("Invitation copied. Share privately with this athlete; expires in ${it.optInt("expires_in")} seconds.") } }
            for (action in listOf("mute", "unmute", "spotlight", "kick")) button(action.replaceFirstChar { it.uppercase() }, box) {
                val apply = { creatorWrite("/api/v1/costream/$id/control", JSONObject().put("action", action).put("player_id", player)) { hostRoom(id) } }
                if (action == "kick") confirm("Remove $player from this session?", apply) else apply()
            }
        }
    }
}
fun MainActivity.qrScannerScreen() {
    start("QR Scanner"); sectionTitle("Scan your arena", "Open a NaSCon QR or paste its destination")
    card("QR scan", "Uses an installed compatible barcode scanner. If unavailable, paste the QR link below.")
    button("Launch QR scanner") {
        try { startActivityForResult(Intent("com.google.zxing.client.android.SCAN").putExtra("SCAN_MODE", "QR_CODE_MODE"), 2031) }
        catch (_: android.content.ActivityNotFoundException) { notice("No compatible scanner is installed. Use your phone camera QR reader, then paste the NaSCon link below.") }
    }
    val link = field("NaSCon link or entity ID")
    button("Open scanned / pasted link") { openArenaLink(link.text.toString().trim()) }
}
fun MainActivity.openArenaLink(value: String) {
    if (validEntity(value)) { arenaScreen(value); return }
    val uri = Uri.parse(value)
    if (uri.scheme == "nascon" && uri.host in listOf("entity", "stream") && uri.pathSegments.size == 1) {
        val id = uri.lastPathSegment ?: ""; if (validEntity(id)) { arenaScreen(id); return }
    }
    if (uri.scheme in listOf("http", "https") && uri.host in listOf(Uri.parse(api.base).host, "localhost", "127.0.0.1")) {
        val path = uri.encodedPath ?: ""
        if (path.matches(Regex("/q/[A-Za-z0-9_-]+"))) { openCreatorUrl(api.base + path); return }
        val segments = uri.pathSegments
        if (segments.size >= 3 && segments[0] == "web" && segments[1] in listOf("matches", "players", "clubs", "costream") && validEntity(segments[2])) { arenaScreen(segments[2]); return }
    }
    notice("Use a NaSCon entity ID, nascon://entity/ID, or QR link for your configured server.")
}
fun MainActivity.qrExportScreen(initial: String = "") {
    start("QR Share & Export"); sectionTitle("One code. Every match state.", "Server-generated dynamic QR exports")
    val entity = field("Entity ID", initial.ifBlank { matchId.ifBlank { "match-m1" } })
    button("Load QR details") {
        val id = entity.text.toString().trim()
        if (!validEntity(id)) { entity.error = "Enter a valid entity ID"; return@button }
        if (forcedOffline()) { notice("QR exports require the server."); return@button }
        work({ api.call("/audit/v1/qr/$id?inspect=true") }, { meta ->
            card("${meta.optString("entity_type")} • ${meta.optString("entity_status")}", "${meta.optLong("scan_count")} scans\n${meta.optString("target_route")}")
            val short = api.base + Uri.parse(meta.getString("short_url")).encodedPath
            button("Copy short link") { copyValue("NaSCon QR link", short) }
            button("Share short link") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, short), "Share NaSCon")) }
            for (format in listOf("png", "svg", "pdf")) button("Open ${format.uppercase()} export") { openCreatorUrl(api.base + "/audit/v1/qr/$id/export.$format") }
        })
    }
}
fun MainActivity.athleteDirectory() {
    start("Athlete Profiles"); sectionTitle("Athlete profiles", "Career records, fixtures, following and replays")
    val players = data.optJSONArray("players") ?: JSONArray()
    for (p in players.objects()) card(p.optString("name", p.optString("player_id")), p.optString("club_id")) { athleteScreen(p.getString("player_id")) }
    val id = field("Athlete ID", playerIdentity())
    button("Open athlete") { val v = id.text.toString().trim(); if (validEntity(v)) athleteScreen(v) else id.error = "Enter an athlete ID" }
}
fun MainActivity.athleteScreen(id: String) {
    prefs.edit().putString("creator_athlete", id).apply()
    start("Athlete Profile"); sectionTitle("Athlete profile", id)
    assetImage(R.drawable.home_player, "NaSCon athlete profile cover", 140)
    fetch("/api/v1/players/$id/profile") { profile ->
        card(profile.optJSONObject("club")?.optString("name") ?: "Club affiliation unavailable", "${profile.optInt("followers")} followers")
        val career = profile.optJSONObject("career") ?: JSONObject()
        creatorStats(listOf("FOLLOWERS" to profile.optInt("followers").toString(), "CLUB MATCHES" to (career.optJSONArray("club_match_records")?.length() ?: 0).toString(), "VODS" to (profile.optJSONArray("vods")?.length() ?: 0).toString()))
        card("Career stats", "${career.optInt("attributed_matches")} attributed matches • ${career.optInt("goals")} goals • ${career.optInt("points")} points\n${career.optString("record_scope")}")
        for ((label, following) in listOf("Follow athlete" to true, "Unfollow athlete" to false)) button(label) {
            creatorWrite("/api/v1/players/$id/follow", JSONObject().put("following", following), "PUT") { result -> notice("${if (result.optBoolean("following")) "Following" else "Not following"} • ${result.optInt("followers")} followers"); athleteScreen(id) }
        }
        sectionTitle("Upcoming fixtures")
        val upcoming = profile.optJSONArray("upcoming") ?: JSONArray()
        if (upcoming.length() == 0) card("No upcoming fixtures")
        upcoming.objects().forEachIndexed { index, match -> fixtureCard(match, index) }
        button("Watch athlete live / chat") { arenaScreen(id) }
        sectionTitle("VOD recordings")
        val vods = profile.optJSONArray("vods") ?: JSONArray()
        if (vods.length() == 0) card("No recordings yet")
        for (vod in vods.objects()) button("Replay ${vod.optString("created_at")}") { openCreatorUrl(api.base + "/api/v1/vods/${Uri.encode(vod.getString("vod_id"))}/play") }
    }
}
fun MainActivity.arenaScreen(id: String) {
    if (!validEntity(id)) { notice("Invalid entity ID"); return }
    prefs.edit().putString("creator_entity", id).apply()
    start("Live Arena"); sectionTitle("Live arena", id)
    fetch("/web/api/context/$id") { context ->
        context.optJSONObject("match")?.let { match -> card("${match.getJSONObject("home").optInt("score")} : ${match.getJSONObject("away").optInt("score")}", match.optString("status")) }
        context.optJSONObject("club")?.let { card(it.optString("name"), it.optString("club_id")) }
        val media = context.optString("media_path").takeIf { it.isNotBlank() && it != "null" }
        if (media != null && !forcedOffline()) {
            val host = Uri.parse(api.base).host.orEmpty().let { if (it.contains(':')) "[$it]" else it }
            val video = VideoView(this).apply {
                contentDescription = "Live arena video"
                setMediaController(MediaController(this@arenaScreen))
                setVideoURI(Uri.parse("${Uri.parse(api.base).scheme}://$host:8888/$media/index.m3u8"))
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> Toast.makeText(this@arenaScreen, "Live playback unavailable. Retry when ingest is active.", Toast.LENGTH_LONG).show(); true }
            }
            arenaVideo = video
            content.addView(video, LinearLayout.LayoutParams(-1, dp(220)))
        } else card("Waiting for live video", "An athlete must publish an active stream before playback is available.")
        button("Open live player with score notifications") { startActivity(Intent(this, Day2LiveActivity::class.java).setData(Uri.parse("nascon://entity/$id"))) }
        button("Refresh arena") { arenaScreen(id) }
        sectionTitle("Merchandise", "Select a product to use the existing native cart and checkout")
        for (p in (context.optJSONArray("products") ?: JSONArray()).objects()) card(p.optString("name"), "View variants & availability") { productScreen(p) }
        sectionTitle("Live chat", "Server moderation applies to every message")
        val messages = text("Load the latest messages", 14f)
        val room = context.optString("chat_room_id", "chat-$id")
        fun loadChat() { fetch("/api/v1/chat/${Uri.encode(room)}") { r -> messages.text = (r.optJSONArray("messages") ?: JSONArray()).objects().joinToString("\n") { "${it.optString("sender")}: ${it.optString("text")}" }.ifBlank { "No messages yet" } } }
        button("Refresh chat") { loadChat() }
        val chatGeneration = generation
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                if (generation != chatGeneration || isDestroyed) return
                if (forcedOffline() || !hasWindowFocus() || busy) { handler.postDelayed(this, 2000); return }
                work({ api.call("/api/v1/chat/${Uri.encode(room)}") }, { result ->
                    messages.text = (result.optJSONArray("messages") ?: JSONArray()).objects().joinToString("\n") { "${it.optString("sender")}: ${it.optString("text")}" }.ifBlank { "No messages yet" }
                    handler.postDelayed(this, 2000)
                }, { handler.postDelayed(this, 4000) })
            }
        }
        handler.postDelayed(poll, 1000)
        val input = field("Message")
        button("Send message") {
            val value = input.text.toString().trim()
            if (value.isBlank() || value.length > 2000) { input.error = "Enter 1–2000 characters"; return@button }
            creatorWrite("/api/v1/chat", JSONObject().put("chat_room_id", room).put("message_id", "android-" + UUID.randomUUID()).put("text", value)) { result -> input.setText(""); notice(if ((result.optJSONArray("kicked_users")?.length() ?: 0) > 0) "Message blocked by moderation." else "Message processed by server moderation."); loadChat() }
        }
        if (context.optJSONObject("player") != null) button("View athlete profile") { athleteScreen(id) }
        for (vod in (context.optJSONArray("vods") ?: JSONArray()).objects()) button("Replay ${vod.optString("vod_id")}") { openCreatorUrl(api.base + "/api/v1/vods/${Uri.encode(vod.getString("vod_id"))}/play") }
    }
}

fun MainActivity.creatorStats(values: List<Pair<String, String>>) {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(12)) }
    content.addView(row)
    for ((label, value) in values) {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER; setPadding(dp(5), dp(12), dp(5), dp(10)); setBackgroundResource(R.drawable.bg_card) }
        text(value.uppercase(), 17f, true, cell).apply { gravity = android.view.Gravity.CENTER; setTextColor(android.graphics.Color.rgb(0, 210, 255)) }
        text(label, 9f, true, cell).gravity = android.view.Gravity.CENTER
        row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
    }
}
fun MainActivity.streamDirector() {
    start("Active Stream Director"); sectionTitle("Active stream director", "Ingest, match context and interactive room")
    assetImage(R.drawable.match_arena, "Live match production stage", 155)
    val player = playerIdentity()
    val saved = SessionStore(this, "creator_keys").read()?.takeIf { it.optString("owner") == owner }?.optJSONObject("key")
    creatorStats(listOf("ACCOUNT" to role.replace('_', ' '), "KEY" to (saved?.optString("status") ?: "UNSET")))
    card("Signal & telemetry", "The API does not report publisher bitrate or dropped frames. Connection feedback is available inside the camera publisher.")
    button("Open Broadcast Studio") { broadcastStudio() }
    sectionTitle("Ingest & stream key", "Private endpoints for your athlete account")
    if (saved?.optString("status") == "active") {
        val ingest = saved.optJSONObject("ingest_endpoints")
        ingest?.optString("rtmp")?.takeIf { it.isNotBlank() }?.let { value ->
            text(mediaUrl(value).substringBeforeLast('/') + "/••••", 13f)
            button("Copy RTMP ingest endpoint") { copyValue("Private ingest URL", mediaUrl(value)) }
        }
        button("Rotate / manage stream key") { streamKeysScreen() }
        button("End broadcast • revoke key") { confirm("Revoke your publishing key and disconnect active ingest?") {
            creatorWrite("/api/v1/streams/key", JSONObject().put("action", "revoke").put("label", "Android studio")) { result ->
                SessionStore(this, "creator_keys").save(JSONObject().put("owner", owner).put("key", result)); streamDirector()
            }
        } }
    } else button("Generate stream key") { streamKeysScreen() }
    sectionTitle("Interactive match room")
    val match = field("Match ID", matchId.ifBlank { "match-m1" })
    button("Open live chat & merchandise") { val id = match.text.toString().trim(); if (validEntity(id)) arenaScreen(id) else match.error = "Enter a match ID" }
    button("Match QR & telemetry") { val id = match.text.toString().trim(); if (validEntity(id)) { prefs.edit().putString("telemetry_match", id).apply(); matchTelemetry() } else match.error = "Enter a match ID" }
    if (player.isNotBlank()) button("My athlete profile") { athleteScreen(player) }
}
fun MainActivity.matchTelemetry() {
    start("Match Telemetry"); sectionTitle("Match telemetry details", "Arena scanner • dynamic QR • live match")
    val initial = prefs.getString("telemetry_match", null) ?: matchId.ifBlank { "match-m1" }
    val match = field("Match ID", initial)
    button("Arena scanner") { qrScannerScreen() }
    button("My pass & dynamic QR") { val id = match.text.toString().trim(); if (validEntity(id)) qrExportScreen(id) else match.error = "Enter a match ID" }
    button("Load live match telemetry") {
        val id = match.text.toString().trim()
        if (!validEntity(id)) { match.error = "Enter a valid match ID"; return@button }
        prefs.edit().putString("telemetry_match", id).apply()
        fetch("/audit/v1/score/$id") { score ->
            val home = score.getJSONObject("home"); val away = score.getJSONObject("away")
            assetImage(R.drawable.match_arena, "Live match arena", 125)
            card("${home.optString("team_id")} vs ${away.optString("team_id")}", score.optString("sport_id").uppercase())
            creatorStats(listOf("HOME" to home.optInt("score").toString(), "AWAY" to away.optInt("score").toString(), "VERSION" to score.optInt("version").toString()))
            card("Match status", score.optString("status").replace('_', ' ').uppercase())
            button("Open Match Center") { matchScreen(id) }
            button("Watch live match") { arenaScreen(id) }
            button("Share match deep link") { copyValue("Match deep link", "nascon://entity/$id") }
            button("Export match QR") { qrExportScreen(id) }
        }
    }
}
