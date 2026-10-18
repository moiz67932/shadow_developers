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
fun MainActivity.creatorHub() { nativeCreatorHub() }

fun validEntity(value: String) = value.matches(Regex("[A-Za-z0-9_.:-]{1,64}"))
fun MainActivity.broadcastStudio() { nativeBroadcastStudio() }

fun MainActivity.streamKeysScreen() { nativeStreamKeys() }

fun MainActivity.costreamLobby() { nativeCostreamLobby() }

fun MainActivity.hostRoom(id: String = prefs.getString("creator_session", "") ?: "") { nativeHostRoom(id) }

fun MainActivity.qrScannerScreen() { nativeQrEntry() }

fun MainActivity.openArenaLink(value: String) { resolveArenaLink(value) }

fun MainActivity.qrExportScreen(initial: String = "") {
    start("QR Share & Export");routeArgs.put("entity",initial); sectionTitle("One code. Every match state.", "Server-generated dynamic QR exports")
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
fun MainActivity.athleteScreen(id: String) { nativeAthleteProfile(id) }

fun MainActivity.arenaScreen(id: String) { nativeLiveArena(id) }

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
