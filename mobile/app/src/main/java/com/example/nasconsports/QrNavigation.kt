package com.example.nasconsports

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

fun MainActivity.nativeQrEntry() {
    start("QR Scanner")
    val body = creatorWorkspace("Scan your arena", "Match, athlete, club or co-stream. One scan takes you there.", "NATIVE QR SCANNER")
    val frame = ImageView(this).apply {
        setImageResource(R.drawable.qr_viewfinder); setBackgroundResource(R.drawable.bg_arena_glow)
        setPadding(dp(24), dp(24), dp(24), dp(24)); contentDescription = "Open camera QR scanner"
        isClickable = true; isFocusable = true
        setOnClickListener { startActivityForResult(Intent(this@nativeQrEntry, QrScannerActivity::class.java), 2031) }
    }
    body.addView(frame, LinearLayout.LayoutParams(-1, dp(210)).apply { bottomMargin = dp(12) })
    button("Open native camera scanner", body) { startActivityForResult(Intent(this, QrScannerActivity::class.java), 2031) }
    val panel = infoPanel("Have a link instead?", "Paste a NaSCon QR link, deep link or entity ID.", body)
    val link = field("NaSCon link or entity ID", parent = panel)
    button("Open destination", panel) { openArenaLink(link.text.toString().trim()) }
    inlineActions(body, listOf("Share / export my QR" to { qrExportScreen() }, "Match telemetry" to { matchTelemetry() }))
}

fun MainActivity.resolveArenaLink(value: String) {
    fun openEntity(id: String, type: String? = null) {
        if (!validEntity(id)) { notice("Invalid entity ID."); return }
        when {
            type == "players" || id.startsWith("player-") -> athleteScreen(id)
            type == "clubs" || id.startsWith("club-") -> clubProfile(id)
            type == "matches" || (data.optJSONArray("fixtures")?.objects()?.any { it.optString("match_id") == id } == true) || id.startsWith("match-") -> matchScreen(id)
            else -> arenaScreen(id)
        }
    }
    if (validEntity(value)) { openEntity(value); return }
    val uri = Uri.parse(value)
    if (uri.scheme == "nascon" && uri.host in listOf("entity", "stream") && uri.pathSegments.size == 1) { openEntity(uri.lastPathSegment.orEmpty()); return }
    val server = Uri.parse(api.base)
    if (uri.scheme !in listOf("http", "https") || uri.host !in listOf(server.host, "localhost", "127.0.0.1") || uri.userInfo != null) {
        notice("Use a NaSCon entity or QR link for your configured server."); return
    }
    val segments = uri.pathSegments
    if (segments.size >= 3 && segments[0] == "web" && segments[1] in listOf("matches", "players", "clubs", "costream")) {
        openEntity(segments[2], segments[1]); return
    }
    if (segments.size == 2 && segments[0] == "q" && segments[1].matches(Regex("[A-Za-z0-9_-]+"))) {
        if (forcedOffline()) { notice("Short QR links need a connection to resolve. Use the entity ID offline."); return }
        work({
            // Resolve the existing redirect once, without following arbitrary external locations.
            val connection = URL(api.base + "/q/" + segments[1]).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false; connection.connectTimeout = 5000; connection.readTimeout = 5000
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) throw ApiError("QR_UNAVAILABLE", "This QR link could not be resolved.")
                JSONObject().put("destination", connection.getHeaderField("Location"))
            } finally { connection.disconnect() }
        }, { result ->
            val destination = result.optString("destination")
            if (destination.startsWith("/web/")) resolveArenaLink(api.base + destination)
            else notice("The server returned an unsupported QR destination.")
        })
        return
    }
    notice("This link is not a supported NaSCon destination.")
}

fun MainActivity.clubProfile(id: String) {
    start("Club profile"); routeArgs.put("club", id)
    val club = data.optJSONArray("clubs")?.objects()?.find { it.optString("club_id") == id }
    val body = creatorWorkspace(club?.optString("name") ?: id, "Club directory and tournament squad", "CLUB PROFILE")
    if (club == null) { infoPanel("Club unavailable", "Refresh your workspace or sign in to an account with access to this club.", body); return }
    infoPanel("Club identity", "${club.optString("name")}\n${club.optString("venue", "Venue not supplied")}", body)
    button("Open club roster", body) { rosterScreen(id) }
    inlineActions(body, listOf("Watch club arena" to { arenaScreen(id) }, "Share club QR" to { qrExportScreen(id) }))
}
