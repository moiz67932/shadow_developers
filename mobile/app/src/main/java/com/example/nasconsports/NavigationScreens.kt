package com.example.nasconsports

import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject

data class ArenaAction(val title: String, val subtitle: String, val icon: Int, val open: () -> Unit)

fun MainActivity.actionGrid(actions: List<ArenaAction>, parent: LinearLayout = content) {
    actions.chunked(2).forEach { pair ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        pair.forEachIndexed { index, action ->
            val tile = layoutInflater.inflate(R.layout.item_arena_action, row, false)
            tile.findViewById<ImageView>(R.id.action_icon).setImageResource(action.icon)
            tile.findViewById<TextView>(R.id.action_title).text = action.title
            tile.findViewById<TextView>(R.id.action_description).text = action.subtitle
            tile.contentDescription = "${action.title}. ${action.subtitle}"
            tile.setOnClickListener { action.open() }
            row.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (index == 0) rightMargin = dp(6) else leftMargin = dp(6) })
        }
        if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    }
}

fun MainActivity.restoreRoute(route: JSONObject) {
    val args = route.optJSONObject("args") ?: JSONObject()
    when (route.optString("screen")) {
        "Match detail" -> matchScreen(args.optString("match", matchId))
        "Product detail" -> args.optJSONObject("product")?.let { productScreen(it) } ?: storeScreen()
        "Cart & checkout" -> cartScreen()
        "Paid receipt" -> local.get("receipt:$owner")?.let { receiptScreen(it) } ?: storeScreen()
        "Roster action" -> {
            val roster = args.optJSONObject("roster"); val player = args.optJSONObject("player")
            if (roster != null && player != null) rosterForm(roster, player) else rosterScreen()
        }
        "Athlete Profile" -> athleteScreen(args.optString("athlete", prefs.getString("creator_athlete", "") ?: ""))
        "Live Arena" -> arenaScreen(args.optString("entity", defaultArenaEntity()))
        "Co-stream Host Room" -> hostRoom(args.optString("session", prefs.getString("creator_session", "") ?: ""))
        "QR Share & Export" -> qrExportScreen(args.optString("entity"))
        "VOD Archive" -> vodArchive(args.optString("athlete"), args.optString("query"), args.optString("filter", "All VODs"))
        "VOD Player" -> args.optJSONObject("vod")?.let { vodPlayer(it, args.optInt("position")) } ?: vodArchive()
        else -> go(route.optString("screen", "Home"))
    }
}

fun MainActivity.nativeProfile() {
    start("Profile")
    val view = layoutInflater.inflate(R.layout.screen_profile, content, true)
    view.findViewById<TextView>(R.id.hero_title).text = "Your arena. Your account."
    view.findViewById<TextView>(R.id.hero_subtitle).text = "Manage your tournament workspace."
    view.findViewById<TextView>(R.id.profile_email).text = user.optString("email")
    view.findViewById<TextView>(R.id.profile_role).text = role.replace('_', ' ').uppercase() + " · " + user.optString("tenant_id")
    val actions = mutableListOf(
        ArenaAction("Creator tools", "Studio, live rooms and QR", R.drawable.ic_play_circle) { creatorHub() },
        ArenaAction("Club & roster", "Players and squad history", R.drawable.ic_person) { rosterScreen() },
        ArenaAction("Saved replays", "Your recording library", R.drawable.ic_play_circle) { vodArchive(initialFilter = "Saved") },
        ArenaAction("Sync queue", "Offline changes and conflicts", R.drawable.ic_calendar) { queueScreen() },
        ArenaAction("Settings", "Server and offline mode", R.drawable.ic_person) { settings() },
        ArenaAction("Athletes", "Profiles and following", R.drawable.ic_person) { athleteDirectory() }
    )
    if (canScore) actions.add(ArenaAction("Admin dashboard", "League and scoring operations", R.drawable.ic_standings) { adminDashboard() })
    if (playerIdentity().isNotBlank()) actions.add(ArenaAction("My athlete profile", "Career and recordings", R.drawable.ic_person) { athleteScreen(playerIdentity()) })
    actionGrid(actions, view.findViewById(R.id.profile_actions))
    view.findViewById<MaterialButton>(R.id.profile_logout).setOnClickListener {
        confirm("Sign out? Pending changes remain saved for this account and server.") {
            sessions.clear(); session = null; api.token = ""; data = JSONObject(); cart.clear(); orderRequest = null
            history.clear(); pendingRoute = null; login()
        }
    }
}

fun MainActivity.adminDashboard() {
    start("Admin dashboard")
    if (!canScore) { card("Organizer access required", "This dashboard is available to platform administrators and league organizers."); return }
    val view = layoutInflater.inflate(R.layout.screen_admin_dashboard, content, true)
    view.findViewById<TextView>(R.id.hero_title).text = "Run the tournament."
    view.findViewById<TextView>(R.id.hero_subtitle).text = "${role.replace('_', ' ')} · ${user.optString("tenant_id")}"
    val fixtures = (data.optJSONArray("fixtures") ?: JSONArray()).objects()
    view.findViewById<TextView>(R.id.admin_summary).text = "${data.optJSONArray("leagues")?.length() ?: 0} leagues · ${fixtures.size} fixtures · ${fixtures.count { it.optString("status") == "in_progress" }} live matches\n${local.actions(owner, true).length()} pending offline changes"
    actionGrid(listOf(
        ArenaAction("Match operations", "Fixtures, scoring and corrections", R.drawable.ic_calendar) { fixturesScreen() },
        ArenaAction("Standings", "League tables and rankings", R.drawable.ic_standings) { standingsScreen() },
        ArenaAction("Club directory", "View clubs and rosters", R.drawable.ic_person) { rosterScreen() },
        ArenaAction("Broadcast rooms", "Studio and co-stream controls", R.drawable.ic_play_circle) { creatorHub() },
        ArenaAction("Sync queue", "Review submitted changes", R.drawable.ic_calendar) { queueScreen() },
        ArenaAction("Server settings", "Connection and cached data", R.drawable.ic_person) { settings() }
    ), view.findViewById(R.id.admin_actions))
    view.findViewById<MaterialButton>(R.id.admin_refresh).setOnClickListener { refresh { adminDashboard() } }
}
