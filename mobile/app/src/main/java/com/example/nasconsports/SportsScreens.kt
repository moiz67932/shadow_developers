package com.example.nasconsports

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun MainActivity.leaguePicker(after:()->Unit){
    val leagues=(data.optJSONArray("leagues")?:JSONArray()).objects()
    if(leagues.isEmpty()){card("No leagues yet","Ask the organizer to prepare the competition demonstration, then refresh.");return}
    val p=picker("League",leagues.map{it.optString("name",it.optString("league_id"))})
    val initial=leagues.indexOfFirst{it.optString("league_id")==selectedLeague}.coerceAtLeast(0)
    selectedLeague=leagues[initial].getString("league_id");p.setSelection(initial)
    p.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
        override fun onNothingSelected(v:AdapterView<*>?){}
        override fun onItemSelected(a:AdapterView<*>?,v:View?,pos:Int,id:Long){val chosen=leagues[pos].getString("league_id");if(chosen!=selectedLeague){selectedLeague=chosen;after()}}
    }
}

fun MainActivity.fixtureCard(f:JSONObject,index:Int=0,parent:LinearLayout=content):LinearLayout{
    val view=layoutInflater.inflate(R.layout.item_fixture_card,parent,false) as LinearLayout
    val h=f.optJSONObject("home");val a=f.optJSONObject("away")
    val home=h?.optString("team_id")?.takeIf{it.isNotBlank()}?:f.optString("home","Home")
    val away=a?.optString("team_id")?.takeIf{it.isNotBlank()}?:f.optString("away","Away")
    val statusValue=f.optString("status","upcoming").replace('_',' ').uppercase()
    val league=(data.optJSONArray("leagues")?:JSONArray()).objects().firstOrNull{it.optString("league_id")==f.optString("league_id")}
    view.findViewById<TextView>(R.id.fixture_competition).text=(league?.optString("name")?:f.optString("sport_id","NASCON CUP")).uppercase()
    view.findViewById<TextView>(R.id.fixture_status).apply{text=if(statusValue=="IN PROGRESS")"● LIVE"else statusValue;setTextColor(if(statusValue=="IN PROGRESS")Color.rgb(248,113,113)else Color.rgb(0,210,255))}
    view.findViewById<TextView>(R.id.fixture_home).text=home.replace('-',' ')
    view.findViewById<TextView>(R.id.fixture_away).text=away.replace('-',' ')
    view.findViewById<TextView>(R.id.fixture_score).text=if(h!=null)"${h.optInt("score")} : ${a?.optInt("score")?:0}"else"VS"
    view.findViewById<ImageView>(R.id.fixture_home_badge).setImageResource(if(index%2==0)R.drawable.team_crest_blue else R.drawable.team_crest_gold)
    view.findViewById<ImageView>(R.id.fixture_away_badge).setImageResource(if(index%2==0)R.drawable.team_crest_gold else R.drawable.team_crest_blue)
    view.findViewById<TextView>(R.id.fixture_meta).text="${f.optString("sport_id","SPORT").uppercase()}  •  ${f.optString("scheduled_at","TIME TBA")}  •  MATCH CENTER  ›"
    view.isClickable=true;view.isFocusable=true;view.contentDescription="$home versus $away. $statusValue. Open match center";view.setOnClickListener{matchScreen(f.getString("match_id"))}
    parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
    return view
}

private fun MainActivity.filterRail(){
    val scroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(2),0,dp(10))}
    scroll.addView(row)
    listOf("All Leagues","Premier Cup","Varsity Cup","Division 1").forEachIndexed{i,label->
        TextView(this).apply{text=label;textSize=12f;gravity=Gravity.CENTER;setPadding(dp(16),0,dp(16),0);setTextColor(if(i==0)Color.rgb(7,11,19)else Color.rgb(195,198,215));typeface=ResourcesCompat.getFont(this@filterRail,R.font.space_grotesk);setTypeface(typeface,Typeface.BOLD);background=android.graphics.drawable.GradientDrawable().apply{setColor(if(i==0)Color.rgb(0,210,255)else Color.rgb(22,27,41));cornerRadius=dp(999).toFloat();setStroke(dp(1),if(i==0)Color.rgb(56,189,248)else Color.rgb(51,65,85))}}.also{row.addView(it,LinearLayout.LayoutParams(-2,dp(38)).apply{rightMargin=dp(8)})}
    }
    content.addView(scroll)
}

private fun MainActivity.matchDayRail(fixtures:List<JSONObject>){
    val dates=fixtures.mapNotNull{it.optString("scheduled_at").take(10).takeIf{s->s.length==10&&s.any{c->c.isDigit()}}}.distinct().sorted().take(6)
    val rail=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false}
    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(3),0,dp(10))}
    rail.addView(row)
    val today=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(java.util.Date())
    val values=(if(dates.isEmpty())listOf(today)else dates).toMutableList()
    if(today !in values)values.add(0,today)
    values.take(6).forEachIndexed{index,date->
        val label=try{val parsed=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).parse(date)!!;java.text.SimpleDateFormat("EEE",java.util.Locale.US).format(parsed).uppercase()+"\n"+java.text.SimpleDateFormat("dd",java.util.Locale.US).format(parsed)}catch(_:Exception){"DAY\n${index+1}"}
        TextView(this).apply{text=if(date==today)"TODAY\n${date.takeLast(2)}"else label;gravity=Gravity.CENTER;setLineSpacing(0f,1f);setPadding(dp(17),0,dp(17),0);typeface=ResourcesCompat.getFont(this@matchDayRail,R.font.space_grotesk);setTypeface(typeface,Typeface.BOLD);textSize=11f;setTextColor(if(index==0)Color.WHITE else Color.rgb(195,198,215));background=android.graphics.drawable.GradientDrawable().apply{setColor(if(index==0)Color.rgb(37,99,235)else Color.rgb(22,27,41));cornerRadius=dp(12).toFloat();setStroke(dp(1),if(index==0)Color.rgb(0,210,255)else Color.rgb(51,65,85))}}.also{row.addView(it,LinearLayout.LayoutParams(-2,dp(58)).apply{rightMargin=dp(8)})}
    }
    content.addView(rail,0)
}

fun MainActivity.fixturesScreen(){
    start("Fixtures")
    sectionTitle("Match calendar","Follow every NaSCon fixture, live score and result")
    val fixtures=(data.optJSONArray("fixtures")?:JSONArray()).objects().filter{it.optString("league_id")==selectedLeague}
    matchDayRail(fixtures);filterRail();leaguePicker{fixturesScreen()};button("Refresh fixtures"){refresh{fixturesScreen()}}
    if(fixtures.isEmpty()){card("No fixtures scheduled","Switch league or refresh your arena workspace.");return}
    val live=fixtures.filter{it.optString("status") in listOf("in_progress","live")}
    val upcoming=fixtures.filter{it.optString("status")=="upcoming"}
    val completed=fixtures.filter{it.optString("status") !in listOf("in_progress","live","upcoming")}
    if(live.isNotEmpty()){sectionTitle("● Live now","${live.size} fixtures active");live.forEachIndexed{i,f->fixtureCard(f,i)}}
    if(upcoming.isNotEmpty()){sectionTitle("Upcoming fixtures","Pakistan Standard Time");upcoming.forEachIndexed{i,f->fixtureCard(f,i+live.size)}}
    if(completed.isNotEmpty()){sectionTitle("Full time results","Earlier today");completed.forEachIndexed{i,f->fixtureCard(f,i+live.size+upcoming.size)}}
}

fun MainActivity.standingsScreen(){
    start("Standings");sectionTitle("League standings","Qualification race • live table")
    leaguePicker{standingsScreen()};button("Refresh standings"){standingsScreen()}
    if((data.optJSONArray("leagues")?.length()?:0)==0)return
    fetch("/audit/v1/standings/$selectedLeague"){r->
        card("Top ${r.optInt("qualification_spots")} qualify","Updated from the authoritative match ledger • PTS then goal difference")
        val table=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundResource(R.drawable.bg_card);setPadding(0,0,0,dp(6))}
        content.addView(table,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),0);setBackgroundResource(R.drawable.bg_table_header)}
        fun headerCell(label:String,width:Int,weight:Float=0f){TextView(this).apply{text=label;textSize=9f;gravity=Gravity.CENTER;letterSpacing=.08f;setTextColor(Color.rgb(148,163,184));typeface=ResourcesCompat.getFont(this@standingsScreen,R.font.space_grotesk);setTypeface(typeface,Typeface.BOLD)}.also{header.addView(it,LinearLayout.LayoutParams(if(weight>0)0 else dp(width),dp(42),weight))}}
        headerCell("",3);headerCell("POS",34);headerCell("",30);headerCell("CLUB",0,1f);headerCell("PL",30);headerCell("GD",36);headerCell("PTS",38);table.addView(header)
        for((index,row) in (r.optJSONArray("table")?:JSONArray()).objects().withIndex()){
            val item=layoutInflater.inflate(R.layout.item_standing_row,table,false)
            item.setBackgroundColor(if(index%2==0)Color.TRANSPARENT else Color.argb(30,255,255,255))
            item.findViewById<View>(R.id.qualification_bar).visibility=if(row.optString("status").contains("qualif"))View.VISIBLE else View.INVISIBLE
            item.findViewById<TextView>(R.id.standing_rank).text=row.optInt("rank").toString()
            item.findViewById<ImageView>(R.id.standing_badge).setImageResource(if(index%2==0)R.drawable.team_crest_blue else R.drawable.team_crest_gold)
            item.findViewById<TextView>(R.id.standing_team).text=row.optString("team_id").replace('-',' ')
            item.findViewById<TextView>(R.id.standing_played).text=row.optInt("played").toString()
            item.findViewById<TextView>(R.id.standing_diff).text=row.optString("diff","0")
            item.findViewById<TextView>(R.id.standing_points).text=row.optInt("points").toString()
            table.addView(item)
        }
        sectionTitle("League matches","Open a match for live timeline and controls")
        (data.optJSONArray("fixtures")?:JSONArray()).objects().filter{it.optString("league_id")==selectedLeague}.take(4).forEachIndexed{i,f->fixtureCard(f,i)}
    }
}

fun MainActivity.matchScreen(id:String){
    matchId=id;start("Match detail");val stamp=generation
    sectionTitle("Match center","Live score, venue and authoritative event timeline")
    assetImage(R.drawable.match_arena,"Floodlit NaSCon match arena",126)
    button("Refresh score"){matchScreen(id)}
    fetch("/audit/v1/score/$id"){m->
        val home=m.getJSONObject("home");val away=m.getJSONObject("away")
        val score=layoutInflater.inflate(R.layout.item_fixture_card,content,false) as LinearLayout
        score.findViewById<TextView>(R.id.fixture_competition).text="${m.optString("sport_id").uppercase()} • MATCH CENTER"
        score.findViewById<TextView>(R.id.fixture_status).text=m.optString("status").replace('_',' ').uppercase()
        score.findViewById<TextView>(R.id.fixture_home).text=home.getString("team_id").replace('-',' ')
        score.findViewById<TextView>(R.id.fixture_away).text=away.getString("team_id").replace('-',' ')
        score.findViewById<TextView>(R.id.fixture_score).text="${home.optInt("score")} : ${away.optInt("score")}"
        score.findViewById<ImageView>(R.id.fixture_home_badge).setImageResource(R.drawable.team_crest_blue)
        score.findViewById<ImageView>(R.id.fixture_away_badge).setImageResource(R.drawable.team_crest_gold)
        score.findViewById<TextView>(R.id.fixture_meta).text="${m.optString("outcome","MATCH ACTIVE").replace('_',' ').uppercase()}  •  VERSION ${m.optInt("version")}"
        content.addView(score,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
        if(canScore){
            if(m.optString("status")=="upcoming")button("Start match"){submitMutation("score_event",m.optInt("version"),JSONObject().put("event_id",UUID.randomUUID().toString()).put("match_id",id).put("type","match_start").put("payload",JSONObject())){matchScreen(id)}}
            if(m.optString("status")=="in_progress"){
                val teams=listOf(home.getString("team_id"),away.getString("team_id"));val tp=picker("Scoring team",teams)
                val config=data.optJSONObject("sports")?.optJSONObject(m.optString("sport_id"))?.optJSONObject("scoring_events")
                val events=config?.keys()?.asSequence()?.toList()?:emptyList();val ep=picker("Configured scoring event",events.map{it.replace('_',' ')})
                if(events.isNotEmpty())button("Append score event"){val body=JSONObject().put("event_id",UUID.randomUUID().toString()).put("match_id",id).put("type","score").put("payload",JSONObject().put("team_id",teams[tp.selectedItemPosition]).put("event_type",events[ep.selectedItemPosition]));submitMutation("score_event",m.optInt("version"),body){matchScreen(id)}}
            }
        }
        sectionTitle("Match timeline","Verified events in ledger order")
        val timeline=(m.optJSONArray("events")?:JSONArray()).objects()
        if(timeline.isEmpty())card("Kick-off is ahead","Scoring events will appear here.")
        for(e in timeline){
            val p=e.optJSONObject("payload")?:JSONObject();val box=card("${e.optInt("sequence").toString().padStart(2,'0')}  •  ${e.optString("type").replace('_',' ').uppercase()}",listOf(p.optString("team_id"),p.optString("event_type").replace('_',' '),if(e.optBoolean("compensated"))"Corrected by a compensating event"else"",p.optString("reason")).filter{it.isNotBlank()}.joinToString(" • "))
            if(canScore&&e.optString("type") in listOf("score","discipline","tiebreak_winner")&&!e.optBoolean("compensated"))button("Correct this event",box){val reason=EditText(this).apply{hint="Reason for correction";minHeight=dp(56)};androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Append compensation").setView(reason).setNegativeButton("Cancel",null).setPositiveButton("Confirm"){_,_->if(reason.text.isBlank())notice("A correction reason is required.")else{val b=JSONObject().put("event_id",UUID.randomUUID().toString()).put("match_id",id).put("type","compensation").put("payload",JSONObject().put("compensates",e.getString("event_id")).put("reason",reason.text.toString()));submitMutation("score_event",m.optInt("version"),b){matchScreen(id)}}}.show()}
        }
        button("View league standings"){selectedLeague=m.optString("league_id",selectedLeague);standingsScreen()}
        if(m.optString("status")=="in_progress"&&online)Handler(Looper.getMainLooper()).postDelayed({if(!isDestroyed&&generation==stamp&&screen=="Match detail"&&hasWindowFocus()&&!busy)pollMatch(id,m.optInt("version"),stamp)},12000)
    }
}

fun MainActivity.pollMatch(id:String,version:Int,stamp:Int){if(generation!=stamp)return;work({val r=api.call("/audit/v1/score/$id");local.put("$owner:/audit/v1/score/$id",r);r},{r->if(r.optInt("version")!=version)matchScreen(id)else Handler(Looper.getMainLooper()).postDelayed({if(!isDestroyed&&generation==stamp&&hasWindowFocus()&&!busy)pollMatch(id,version,stamp)},12000)},{banner()})}

fun MainActivity.submitMutation(type:String,base:Int,payload:JSONObject,done:()->Unit){
    val path=if(type=="score_event")"/api/v1/events"else"/api/v1/roster/transfer"
    if(forcedOffline()||!online){online=false;local.queue(owner,type,base,payload);banner();notice("Saved on this device. Review and sync from Offline changes.");done();return}
    work({api.call(path,payload)},{r->notice("Accepted • "+r.optString("state",if(type=="score_event")"Score event recorded"else"Roster updated"));done()},{e->if(e is ApiError)notice((e.message?:"Action rejected")+"\n"+e.code.replace('_',' ').lowercase())else{local.queue(owner,type,base,payload);banner();notice("Connection lost. Action saved with a stable ID for safe sync.");done()}})
}

fun MainActivity.queueScreen(){
    start("Sync queue");sectionTitle("Offline changes","Signed-in account: ${user.optString("email")}")
    val actions=local.actions(owner);val pending=local.actions(owner,true)
    button("Sync ${pending.length()} pending changes"){
        if(forcedOffline())notice("Restore the network and turn off Work offline in Settings before syncing.")else if(pending.length()==0)notice("There are no pending changes.")else{
            banner("Syncing…");val device=prefs.getString("device",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("device",it).commit()}
            work({api.call("/api/v1/offline-sync",JSONObject().put("device_id",device).put("actions",pending)).also{r->for(result in (r.optJSONArray("results")?:JSONArray()).objects())local.resolve(owner,result)}},{r->for(result in (r.optJSONArray("results")?:JSONArray()).objects())local.resolve(owner,result);val summary=r.getJSONObject("summary");notice("Sync complete: ${summary.optInt("applied")} applied, ${summary.optInt("rejected")} rejected, ${summary.optInt("duplicate")} duplicates. Review each result below.");refresh{queueScreen()}})
        }
    }
    button("Check connection"){work({api.call("/health")},{queueScreen()})}
    if(actions.length()==0)card("All caught up","Offline scores and roster actions will be kept here until the server resolves them.")
    for(a in actions.objects()){val result=a.optJSONObject("result");val p=a.optJSONObject("payload");card(a.optString("type").replace('_',' ').replaceFirstChar{it.uppercase()},"${a.optString("local_status").uppercase()} • ${a.optString("queued_at")}\n${p?.optString("match_id")?:""}${p?.optString("player_id")?:""}"+(if(result!=null)"\n${result.optString("resolution").replace('_',' ')} • Server version ${result.optInt("server_version")} • Processed #${result.optInt("processed_order")}\n"+(if(result.isNull("error_code"))"Resolved by server"else"Rejected: "+result.optString("error_code").replace('_',' ').lowercase())else"\nWaiting to sync"))}
}
