package com.example.nasconsports

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun MainActivity.accountClubId(): String = try {
    JSONObject(String(android.util.Base64.decode(api.token.split('.')[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))).optString("club_id")
} catch (_: Exception) { user.optString("club_id") }

fun MainActivity.creatorWorkspace(title: String, subtitle: String, label: String = "CREATOR ARENA"): LinearLayout {
    val view = layoutInflater.inflate(R.layout.screen_creator_workspace, content, true)
    view.findViewById<TextView>(R.id.hero_title).text = title
    view.findViewById<TextView>(R.id.hero_label).text = label
    view.findViewById<TextView>(R.id.hero_subtitle).text = subtitle
    return view.findViewById(R.id.workspace_body)
}

fun MainActivity.infoPanel(title: String, subtitle: String, parent: LinearLayout = content): LinearLayout {
    val view = layoutInflater.inflate(R.layout.item_arena_panel, parent, false)
    view.findViewById<TextView>(R.id.panel_title).text = title.uppercase()
    view.findViewById<TextView>(R.id.panel_subtitle).apply { text = subtitle; visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE }
    parent.addView(view)
    return view.findViewById(R.id.panel_body)
}

fun MainActivity.inlineActions(parent: LinearLayout, actions: List<Pair<String, () -> Unit>>) {
    actions.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(row)
        for ((index, action) in pair.withIndex()) {
            val button = MaterialButton(this).apply {
                text = action.first; textSize = 12f; isAllCaps = false; minHeight = dp(48); cornerRadius = dp(10)
                setTextColor(getColor(R.color.lavender)); backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_high))
                setOnClickListener { if (!busy || busyGeneration != generation) action.second() }
            }
            row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) rightMargin = dp(4) else leftMargin = dp(4) })
        }
    }
}

fun MainActivity.nativeCreatorHub() {
    start("Creator tools")
    creatorWorkspace("Create. Connect. Compete.", "Broadcast your game and bring your squad together.")
    actionGrid(listOf(
        ArenaAction("Broadcast Studio", "Camera, microphone and stream", R.drawable.ic_play_circle) { broadcastStudio() },
        ArenaAction("Co-stream lobby", "Create a room or join your squad", R.drawable.ic_person) { costreamLobby() },
        ArenaAction("Stream keys", "Secure publishing credentials", R.drawable.ic_bell) { streamKeysScreen() },
        ArenaAction("Live arena", "Watch, chat and shop", R.drawable.ic_play_circle) { arenaScreen(defaultArenaEntity()) },
        ArenaAction("Arena scanner", "Scan a match or athlete QR", R.drawable.ic_search) { qrScannerScreen() },
        ArenaAction("Share a QR", "Dynamic match and profile links", R.drawable.ic_person) { qrExportScreen() },
        ArenaAction("Athletes", "Profiles, records and following", R.drawable.ic_person) { athleteDirectory() },
        ArenaAction("VOD archive", "Watch tournament recordings", R.drawable.ic_play_circle) { vodArchive() },
        ArenaAction("Stream director", "Publishing and room controls", R.drawable.ic_calendar) { streamDirector() },
        ArenaAction("Match telemetry", "Live scores and match QR", R.drawable.ic_standings) { matchTelemetry() }
    ))
}

fun MainActivity.nativeBroadcastStudio() {
    start("Broadcast Studio")
    val body = creatorWorkspace("Solo broadcast studio", "Your stage. Your signal. Your match.", "BROADCAST CONSOLE")
    val player = playerIdentity()
    if (player.isBlank()) {
        infoPanel("Athlete access required", "Use an athlete account to publish a personal stream. You can still watch live games and follow athletes.", body)
        actionGrid(listOf(ArenaAction("Watch live", "Join the fan conversation", R.drawable.ic_play_circle) { arenaScreen(defaultArenaEntity()) }, ArenaAction("Athlete directory", "Explore the tournament", R.drawable.ic_person) { athleteDirectory() }), body)
        return
    }
    val saved = SessionStore(this, "creator_keys").read()?.takeIf { it.optString("owner") == owner }?.optJSONObject("key")
    infoPanel("Broadcast identity", "$player\nPublishing key: ${saved?.optString("status") ?: "not generated"}", body)
    actionGrid(listOf(ArenaAction("Stream keys", "Generate or rotate credentials", R.drawable.ic_bell) { streamKeysScreen() }, ArenaAction("My athlete profile", "Career, followers and highlights", R.drawable.ic_person) { athleteScreen(player) }), body)
    if (saved?.optString("status") == "active") {
        val endpoints = saved.optJSONObject("ingest_endpoints")
        val url = endpoints?.optString("webrtc")?.takeIf { it.isNotBlank() }?.let { mediaUrl(it.removeSuffix("/whip") + "/publish") }.orEmpty()
        val panel = infoPanel("Camera & microphone", "Open the media publisher, allow permissions and connect your signal.", body)
        val publisher = field("Camera publisher URL", url, parent = panel)
        button("Open camera publisher", panel) {
            if (forcedOffline()) notice("Reconnect before broadcasting.")
            else startActivity(Intent(this, BroadcastActivity::class.java).putExtra("publisher", publisher.text.toString().trim()))
        }
        inlineActions(panel, listOf("Watch my stream" to { arenaScreen(player) }, "Stream director" to { streamDirector() }))
        val record = infoPanel("Broadcast recording", "Record an active signal. The recording appears in the archive after the server finalizes it.", body)
        button("Record active stream", record) { creatorWrite("/api/v1/streams/$player/record", JSONObject()) { notice(if (it.optBoolean("started")) "Recording request accepted by server." else "Recording is already active or no new process was started.") } }
    } else button("Generate publishing key", body) { streamKeysScreen() }
    text("Camera publishing uses the self-hosted MediaMTX publisher. HTTPS or loopback is required; media ports must be reachable.", 11f, parent = body)
}

fun MainActivity.nativeStreamKeys() {
    start("Stream Key Controls")
    val body = creatorWorkspace("Stream security", "Your private credentials, secured on this device.", "KEY MANAGEMENT")
    if (playerIdentity().isBlank()) { infoPanel("Athlete access required", "Publishing keys belong to athlete accounts.", body); return }
    val vault = SessionStore(this, "creator_keys")
    val saved = vault.read()?.takeIf { it.optString("owner") == owner }?.optJSONObject("key")
    val keyPanel = infoPanel("Publishing key · ${saved?.optString("status") ?: "not generated"}", "Only share credentials with your trusted encoder.", body)
    val label = field("Key label", saved?.optString("label") ?: "Android studio", parent = keyPanel)
    if (saved?.optString("status") == "active") {
        val key = saved.optString("stream_key")
        val display = text("•••• •••• •••• ${key.takeLast(4)}", 17f, true, keyPanel)
        var revealed = false
        inlineActions(keyPanel, listOf("Reveal / hide" to { revealed = !revealed; display.text = if (revealed) key else "•••• •••• •••• ${key.takeLast(4)}" }, "Copy key" to { copyValue("Private stream key", key) }))
        val ingest = infoPanel("Encoder endpoints", "Paste an ingest endpoint into your streaming software.", body)
        val endpoints = saved.optJSONObject("ingest_endpoints")
        for (kind in listOf("rtmp", "webrtc")) endpoints?.optString(kind)?.takeIf { it.isNotBlank() }?.let { endpoint ->
            text(kind.uppercase() + " · " + mediaUrl(endpoint).replace(Regex("/live/[^/]+"), "/live/••••"), 11f, parent = ingest)
            inlineActions(ingest, listOf("Copy ${kind.uppercase()} URL" to { copyValue("Private ingest URL", mediaUrl(endpoint)) }))
        }
    }
    val controls = infoPanel("Key lifecycle", "Rotating or revoking invalidates the previous publishing credential.", body)
    for (action in listOf("generate", "rotate", "revoke")) {
        if (action == "revoke" && saved?.optString("status") != "active") continue
        button("${action.replaceFirstChar { it.uppercase() }} key", controls) {
            confirm("${action.replaceFirstChar { it.uppercase() }} your publishing key?") {
                creatorWrite("/api/v1/streams/key", JSONObject().put("action", action).put("label", label.text.toString().trim().ifBlank { "Android studio" })) {
                    vault.save(JSONObject().put("owner", owner).put("key", it)); streamKeysScreen()
                }
            }
        }.apply { if (action == "revoke") backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(112, 35, 49)) }
    }
    button("Return to Broadcast Studio", body) { broadcastStudio() }
}

fun MainActivity.registeredAthletes(): List<JSONObject> =
    ((data.optJSONArray("players") ?: JSONArray()).objects() + (data.optJSONArray("rosters") ?: JSONArray()).objects().flatMap { (it.optJSONArray("players") ?: JSONArray()).objects() }).distinctBy { it.optString("player_id") }.filter { validEntity(it.optString("player_id")) }

fun MainActivity.nativeCostreamLobby() {
    start("Co-stream Lobby")
    val body = creatorWorkspace("Your squad. One stream.", "Invite up to four athletes into one synchronized room.", "CO-STREAM LOBBY")
    val mine = playerIdentity()
    val panel = infoPanel("Lobby setup", "Choose the room identifier, squad and broadcast layout.", body)
    val sessionId = field("Session ID", prefs.getString("creator_session", "").orEmpty().ifBlank { "cs-session-${UUID.randomUUID().toString().take(8)}" }, parent = panel)
    inlineActions(panel, listOf("Open existing room" to {
        val id = sessionId.text.toString().trim()
        if (validEntity(id) && id.startsWith("cs-")) { prefs.edit().putString("creator_session", id).apply(); hostRoom(id) } else sessionId.error = "Use a cs- session ID"
    }))
    if (mine.isNotBlank()) {
        val squad = infoPanel("Select participants", "Your athlete is the initial host. Maximum four unique athletes.", body)
        val count = text("1 / 4 participants", 13f, true, squad)
        val selected = linkedSetOf(mine)
        val chips = ChipGroup(this)
        squad.addView(chips)
        val players = registeredAthletes().toMutableList()
        if (players.none { it.optString("player_id") == mine }) players.add(0, JSONObject().put("player_id", mine).put("name", "You (host)"))
        for (player in players) {
            val id = player.getString("player_id")
            val chip = Chip(this).apply {
                text = player.optString("name", id) + if (id == mine) " · Host" else ""
                isCheckable = true; isChecked = id == mine; isEnabled = id != mine; minHeight = dp(48)
                setOnCheckedChangeListener { _, checked ->
                    if (checked && id !in selected && selected.size >= 4) { isChecked = false; Toast.makeText(this@nativeCostreamLobby, "A room supports four athletes.", Toast.LENGTH_SHORT).show() }
                    else if (checked) selected.add(id) else selected.remove(id)
                    count.text = "${selected.size} / 4 participants"
                }
            }
            chips.addView(chip)
        }
        val layouts = listOf("grid_4", "grid_2", "spotlight", "solo")
        val layout = picker("Broadcast layout", layouts, squad)
        button("Create lobby & open host room", squad) {
            val id = sessionId.text.toString().trim()
            val chosen = layouts[layout.selectedItemPosition]
            if (!validEntity(id) || !id.startsWith("cs-")) { sessionId.error = "Use a cs- session ID"; return@button }
            if ((chosen == "solo" && selected.size > 1) || (chosen == "grid_2" && selected.size > 2)) { notice("Choose a layout that fits your participants."); return@button }
            creatorWrite("/audit/v1/costream/session", JSONObject().put("session_id", id).put("host_player_id", mine).put("participants", JSONArray(selected.toList())).put("layout", chosen)) {
                prefs.edit().putString("creator_session", id).apply(); hostRoom(id)
            }
        }
        val join = infoPanel("Have an invitation?", "Paste the signed invitation shared by your host. Invitations expire after five minutes.", body)
        val token = field("Invitation token", parent = join).apply { setSelectAllOnFocus(true); isSaveEnabled = false }
        button("Join invited room", join) {
            val id = sessionId.text.toString().trim()
            if (!validEntity(id) || !id.startsWith("cs-") || token.text.isBlank()) { token.error = "Enter a cs- session ID and your invitation token"; return@button }
            creatorWrite("/api/v1/costream/$id/join", JSONObject().put("invite_token", token.text.toString().trim())) {
                token.setText(""); prefs.edit().putString("creator_session", id).apply(); hostRoom(id)
            }
        }
    } else infoPanel("Spectator access", "You can open an existing room to watch. An athlete account is required to create or join a broadcast.", body)
}

fun MainActivity.nativeHostRoom(id: String) {
    if (!validEntity(id) || !id.startsWith("cs-")) { costreamLobby(); return }
    start("Co-stream Host Room"); routeArgs.put("session", id)
    val body = creatorWorkspace("Co-stream host room", id, "PRODUCTION SUITE")
    fetch("/web/api/context/${Uri.encode(id)}") { context ->
        val room = context.optJSONObject("session")
        if (room == null) { infoPanel("Room unavailable", "Return to the lobby and check your session ID.", body); return@fetch }
        infoPanel("${room.optString("status")} · ${room.optString("layout")}", "Host: ${room.optString("active_host")}\n${room.optInt("participant_count")} active participants", body)
        inlineActions(body, listOf("Watch composite" to { arenaScreen(id) }, "Refresh room" to { hostRoom(id) }))
        val canControl = role == "super_admin" || (playerIdentity().isNotBlank() && playerIdentity() == room.optString("active_host"))
        val members = (room.optJSONArray("active_participants") ?: JSONArray()).let { list -> (0 until list.length()).map { list.getString(it) } }
        if (canControl) {
            val stage = infoPanel("Arena arrangement", "Each participant must have an active key and a published media signal.", body)
            val layouts = listOf("grid_4", "grid_2", "spotlight", "solo")
            val layout = picker("Composite layout", layouts, stage).apply { setSelection(layouts.indexOf(room.optString("layout")).coerceAtLeast(0)) }
            button("Launch co-stream to arena", stage) {
                val selected = layouts[layout.selectedItemPosition]
                if ((selected == "solo" && members.size > 1) || (selected == "grid_2" && members.size > 2)) { notice("This layout cannot fit the active participants."); return@button }
                creatorWrite("/api/v1/costream/$id/start", JSONObject().put("layout", selected)) { notice(if (it.optBoolean("started")) "Server accepted the composite start request." else "The server did not start a new media process.") }
            }
            button("End composite broadcast", stage) { confirm("Stop this composite broadcast?") { creatorWrite("/api/v1/costream/$id/stop", JSONObject()) { notice("Server confirmed stopped=${it.optBoolean("stopped")}") } } }
        } else infoPanel("Participant / spectator view", "Only the active host or a platform administrator can moderate this room.", body)
        for (player in members) {
            val name = registeredAthletes().find { it.optString("player_id") == player }?.optString("name") ?: player
            val participant = infoPanel(name, if (player == room.optString("active_host")) "HOST · $player" else "CO-STREAMER · $player", body)
            if (canControl) {
                inlineActions(participant, listOf("Mute" to { creatorWrite("/api/v1/costream/$id/control", JSONObject().put("action", "mute").put("player_id", player)) { notice("Server confirmed participant muted.") } }, "Unmute" to { creatorWrite("/api/v1/costream/$id/control", JSONObject().put("action", "unmute").put("player_id", player)) { notice("Server confirmed participant unmuted.") } }, "Spotlight" to { creatorWrite("/api/v1/costream/$id/control", JSONObject().put("action", "spotlight").put("player_id", player)) { hostRoom(id) } }, "Remove" to { confirm("Remove $name from the room?") { creatorWrite("/api/v1/costream/$id/control", JSONObject().put("action", "kick").put("player_id", player)) { hostRoom(id) } } }))
                button("Create & share invitation", participant) {
                    creatorWrite("/api/v1/costream/$id/invite", JSONObject().put("player_id", player)) { result ->
                        val token = result.getString("invite_token")
                        copyValue("Invitation token", token)
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "NaSCon co-stream room: $id\nInvitation token (expires in ${result.optInt("expires_in")} seconds):\n$token"), "Share private invitation"))
                    }
                }
            }
        }
        if (members.isEmpty()) infoPanel("No active participants", "The session has ended or all participants have left.", body)
    }
}

fun MainActivity.nativeAthleteProfile(id: String) {
    if (!validEntity(id)) { athleteDirectory(); return }
    start("Athlete Profile"); routeArgs.put("athlete", id)
    prefs.edit().putString("creator_athlete", id).apply()
    val view = layoutInflater.inflate(R.layout.screen_athlete_profile, content, true)
    view.findViewById<ImageView>(R.id.hero_image).setImageResource(R.drawable.home_player)
    view.findViewById<TextView>(R.id.hero_title).text = "Athlete spotlight"
    view.findViewById<TextView>(R.id.hero_subtitle).text = "Career, fixtures and the moments that matter."
    view.findViewById<TextView>(R.id.athlete_name).text = registeredAthletes().find { it.optString("player_id") == id }?.optString("name") ?: id
    val overview = view.findViewById<LinearLayout>(R.id.athlete_overview)
    val fixtures = view.findViewById<LinearLayout>(R.id.athlete_fixtures)
    val recordings = view.findViewById<LinearLayout>(R.id.athlete_recordings)
    val tabs = view.findViewById<TabLayout>(R.id.athlete_tabs)
    for (name in listOf("Overview", "Fixtures", "Replays")) tabs.addTab(tabs.newTab().setText(name))
    tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) { listOf(overview, fixtures, recordings).forEachIndexed { index, section -> section.visibility = if (index == tab.position) View.VISIBLE else View.GONE } }
        override fun onTabUnselected(tab: TabLayout.Tab) {}
        override fun onTabReselected(tab: TabLayout.Tab) {}
    })
    fetch("/api/v1/players/${Uri.encode(id)}/profile") { profile ->
        view.findViewById<TextView>(R.id.athlete_club).text = profile.optJSONObject("club")?.optString("name") ?: id
        val following = infoPanel("${profile.optInt("followers")} followers", "Follow this athlete to stay connected to their tournament journey.", view.findViewById(R.id.athlete_follow_actions))
        inlineActions(following, listOf("Follow athlete" to {
            creatorWrite("/api/v1/players/$id/follow", JSONObject().put("following", true), "PUT") { notice("Following · ${it.optInt("followers")} followers"); athleteScreen(id) }
        }, "Unfollow" to {
            creatorWrite("/api/v1/players/$id/follow", JSONObject().put("following", false), "PUT") { notice("Unfollowed · ${it.optInt("followers")} followers"); athleteScreen(id) }
        }))
        val career = profile.optJSONObject("career") ?: JSONObject()
        infoPanel("Career record", "${career.optInt("attributed_matches")} attributed matches · ${career.optInt("goals")} goals · ${career.optInt("points")} points", overview)
        text(career.optString("record_scope"), 11f, parent = overview)
        inlineActions(overview, listOf("Watch live" to { arenaScreen(id) }, "Share athlete QR" to { qrExportScreen(id) }))
        val matches = (profile.optJSONArray("upcoming") ?: JSONArray()).objects()
        if (matches.isEmpty()) text("No upcoming fixtures.", 13f, parent = fixtures)
        matches.forEachIndexed { index, match -> fixtures.addView(fixtureCard(match, index, fixtures), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }
        val vods = (profile.optJSONArray("vods") ?: JSONArray()).objects()
        if (vods.isEmpty()) text("No recordings yet. Archived broadcasts will appear here.", 13f, parent = recordings)
        for (vod in vods) {
            vod.put("player_id", id)
            val panel = infoPanel("Replay · ${vod.optString("created_at").substringBefore('T')}", vod.optString("vod_id"), recordings)
            button("Play recording", panel) { vodPlayer(vod) }
        }
        button("Browse athlete recordings", recordings) { vodArchive(id) }
    }
}
