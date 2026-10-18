package com.example.nasconsports
import android.widget.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
fun MainActivity.rosterScreen(clubId:String="") {
    start("Club roster");routeArgs.put("club",clubId)
    sectionTitle("Club & roster","Tournament squads, player profiles and roster history")
    val clubs=(data.optJSONArray("clubs")?:JSONArray()).objects().filter { clubId.isBlank()||it.optString("club_id")==clubId }
    for(club in clubs)card(club.optString("name"),"Home venue: ${club.optString("venue","Not supplied")}"){clubProfile(club.optString("club_id"))}
    button("Refresh club & roster"){refresh{rosterScreen(clubId)}}
    val rosters=(data.optJSONArray("rosters")?:JSONArray()).objects()
    if(rosters.isEmpty()){card("No roster configured","Ask your organizer to prepare the competition demonstration.");return}
    for(roster in rosters){
        val squads=(roster.optJSONArray("squads")?:JSONArray()).objects()
        val players=(roster.optJSONArray("players")?:JSONArray()).objects().filter { player->clubId.isBlank()||squads.any { it.optString("squad_id")==player.optString("squad_id")&&it.optString("club_id")==clubId } }
        if(players.isEmpty())continue
        sectionTitle(roster.getJSONObject("league").optString("league_id"),"${players.size} registered players")
        for(player in players){
            val squad=squads.find{it.optString("squad_id")==player.optString("squad_id")}
            card(player.optString("name"),"${player.optString("position").uppercase()} · ${squad?.optString("name")?:"Released"}"){rosterPlayerScreen(roster,player)}
        }
    }
}
fun MainActivity.rosterPlayerScreen(roster:JSONObject,player:JSONObject){
    start("Roster player");routeArgs.put("roster",roster).put("player",player)
    val squad=(roster.optJSONArray("squads")?:JSONArray()).objects().find{it.optString("squad_id")==player.optString("squad_id")}
    val body=creatorWorkspace(player.optString("name"),"${player.optString("position").uppercase()} · ${squad?.optString("name")?:"Released"}","ROSTER PLAYER")
    infoPanel("Player identity","${player.optString("player_id")}\nRole: ${player.optString("role").replace('_',' ')}",body)
    if(canRoster&&!player.isNull("squad_id")&&(role=="super_admin"||squad?.optString("club_id")==accountClubId()))button("Transfer, loan or release",body){rosterForm(roster,player)}
    button("View public athlete profile",body){athleteScreen(player.optString("player_id"))}
    val history=(roster.optJSONArray("history")?:JSONArray()).objects().filter{it.optString("player_id")==player.optString("player_id")}
    if(history.isEmpty())text("No roster changes recorded for this player.",13f,parent=body)
    for(entry in history)infoPanel(entry.optString("type","Roster action").uppercase(),"${entry.optString("effective_date")} · ${entry.optString("reason")}",body)
}
fun MainActivity.rosterForm(r:JSONObject,p:JSONObject){start("Roster action");routeArgs.put("roster",r).put("player",p);val from=(r.optJSONArray("squads")?:JSONArray()).objects().find{it.optString("squad_id")==p.optString("squad_id")};if(!canRoster||(role!="super_admin"&&from?.optString("club_id")!=accountClubId())){card("Manager access required","Only your club manager or an administrator can modify this roster.");return};card(p.optString("name"),"Current squad: ${p.optString("squad_id")}\nPosition: ${p.optString("position")}");val types=listOf("transfer","loan","release");val type=picker("Action",types);val squads=(r.optJSONArray("squads")?:JSONArray()).objects().filter{it.optString("squad_id")!=p.optString("squad_id")};val dest=picker("Destination (ignored for release)",squads.map{it.optString("name")});val today=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date());val effective=field("Effective date • YYYY-MM-DD",today);val loanEnd=field("Loan end date • YYYY-MM-DD (loans only)","2026-12-01");val reason=field("Reason for this change");button("Review action"){val t=types[type.selectedItemPosition];if(reason.text.isBlank()){reason.error="A reason is required";return@button};if(!effective.text.toString().matches(Regex("\\d{4}-\\d{2}-\\d{2}"))){effective.error="Use YYYY-MM-DD";return@button};if(t!="release"&&squads.isEmpty()){notice("No destination squad is available.");return@button};val b=JSONObject().put("request_id",UUID.randomUUID().toString()).put("league_id",r.getJSONObject("league").getString("league_id")).put("player_id",p.getString("player_id")).put("type",t).put("from_squad_id",p.getString("squad_id")).put("to_squad_id",if(t=="release")JSONObject.NULL else squads[dest.selectedItemPosition].getString("squad_id")).put("effective_date",effective.text.toString()).put("loan_end_date",if(t=="loan")loanEnd.text.toString()else JSONObject.NULL).put("reason",reason.text.toString()).put("requested_by",user.getString("user_id"));val history=(r.optJSONArray("history")?:JSONArray()).objects().count{it.optString("player_id")==p.optString("player_id")};confirm("${t.replaceFirstChar{it.uppercase()}} ${p.optString("name")} effective ${effective.text}?\nReason: ${reason.text}\n"+if(online)"This will be submitted to the server."else"This will be queued on this device."){submitMutation("roster_transfer",history,b){if(online)refresh{rosterScreen()}else queueScreen()}}}}
