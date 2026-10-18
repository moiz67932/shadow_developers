package com.example.nasconsports

import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.widget.*
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.res.ResourcesCompat
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity: AppCompatActivity() {
    lateinit var content: LinearLayout
    lateinit var status: TextView
    lateinit var progress: View
    lateinit var nav: LinearLayout
    lateinit var api: ApiClient
    lateinit var local: LocalStore
    lateinit var sessions: SessionStore
    val executor=Executors.newSingleThreadExecutor()
    val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    var session: JSONObject?=null
    var data=JSONObject()
    var screen="Home"
    var selectedLeague="demo-alpha-cup"
    var matchId=""
    var arenaVideo: VideoView?=null
    var busy=false
    var busyGeneration=-1
    lateinit var remembered: SessionStore
    var offlineChoice=false
    fun forcedOffline()=prefs.getBoolean("offline",false)||android.provider.Settings.Global.getInt(contentResolver,android.provider.Settings.Global.AIRPLANE_MODE_ON,0)==1
    override fun onResume(){super.onResume();if(::api.isInitialized){online=!forcedOffline();banner()}}
    fun passwordProof(email:String,password:String)=java.security.MessageDigest.getInstance("SHA-256").digest((api.base+"|"+email+"|"+password).toByteArray()).joinToString(""){"%02x".format(it)}
    fun validRemembered(r:JSONObject):Boolean=try{val payload=r.getString("token").split(".")[1];JSONObject(String(android.util.Base64.decode(payload,android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))).getLong("exp")>System.currentTimeMillis()/1000}catch(_:Exception){false}
    var online=true
    var generation=0
    val history=java.util.ArrayDeque<JSONObject>()
    var routeArgs=JSONObject()
    var pendingRoute:JSONObject?=null
    var restoringBack=false
    val cart=linkedMapOf<String,Int>()
    var orderRequest: JSONObject?=null
    val user get()=session?.optJSONObject("user")?:JSONObject()
    val role get()=user.optString("role")
    val owner get()=api.base+"|"+user.optString("email")
    val canScore get()=role in listOf("super_admin","league_organizer")
    val canRoster get()=role in listOf("super_admin","club_manager")
    fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    override fun onCreate(state:Bundle?) {
        super.onCreate(state);setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);i}
        content=findViewById(R.id.content);status=findViewById(R.id.status);progress=findViewById(R.id.progress);nav=findViewById(R.id.nav)
        remembered=SessionStore(this,"remembered_accounts");local=LocalStore(this);sessions=SessionStore(this);session=sessions.read();api=ApiClient(prefs.getString("server","http://127.0.0.1:8080")!!,session?.optString("token")?:"")
        findViewById<View>(R.id.settings).setOnClickListener { if(session==null)settings()else profileScreen() };findViewById<View>(R.id.global_search).setOnClickListener { if(session==null)signUpScreen()else vodArchive() };findViewById<View>(R.id.back).setOnClickListener { back() }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){back()}})
        selectedLeague=state?.getString("league")?:"demo-alpha-cup";matchId=state?.getString("match")?:""
        val incomingEntity=intent.getStringExtra("arena_entity"); if(incomingEntity!=null&&validEntity(incomingEntity))prefs.edit().putString("creator_entity",incomingEntity).apply()
        state?.getString("history")?.let { raw -> try { JSONArray(raw).objects().forEach { history.addLast(it) } } catch (_:Exception) {} };pendingRoute=state?.getString("route")?.let { try { JSONObject(it) } catch (_:Exception) { null } }
        val saved=if(incomingEntity!=null&&validEntity(incomingEntity))"Live Arena" else state?.getString("screen")?:"Home"
        online=!forcedOffline();if(session!=null){orderRequest=local.get("checkout:$owner")?.takeIf{it.has("order_id")};val savedCart=local.get("cart:$owner");savedCart?.keys()?.forEach{cart[it]=savedCart.optInt(it)}};if(session==null)login() else {data=local.get("bootstrap:$owner")?:JSONObject();refresh {if(incomingEntity!=null)go(saved)else pendingRoute?.let { restoringBack=true;restoreRoute(it);restoringBack=false;pendingRoute=null }?:go(saved)} }
    }
    override fun onSaveInstanceState(out:Bundle){super.onSaveInstanceState(out);out.putString("screen",screen);out.putString("route",JSONObject().put("screen",screen).put("args",routeArgs).toString());out.putString("history",JSONArray(history.toList()).toString());out.putString("match",matchId);out.putString("league",selectedLeague);if(session!=null)local.put("cart:$owner",JSONObject(cart.toMap()))}
    override fun onPause(){if(screen=="VOD Player")routeArgs.put("position",arenaVideo?.currentPosition?:0);arenaVideo?.pause();super.onPause()}
    override fun onDestroy(){arenaVideo?.stopPlayback();super.onDestroy();executor.shutdown()}
    fun back(){
        if(screen=="Sign in")finish()
        else if(session==null)login()
        else if(history.isEmpty()){if(screen=="Home")moveTaskToBack(true)else home()}
        else {val previous=history.removeLast();restoringBack=true;restoreRoute(previous);restoringBack=false}
    }
    fun start(title:String){
        if(screen=="VOD Player")routeArgs.put("position",arenaVideo?.currentPosition?:0)
        arenaVideo?.stopPlayback();arenaVideo=null
        val roots=setOf("Home","Fixtures","Standings","Store","Profile")
        if(!restoringBack&&screen!=title&&screen!="Sign in"&&session!=null){
            if(title in roots)history.clear()
            else {if(history.size>20)history.removeFirst();history.addLast(JSONObject().put("screen",screen).put("args",JSONObject(routeArgs.toString())))}
        }
        routeArgs=JSONObject()
        generation++;screen=title;findViewById<View>(R.id.global_search).contentDescription=if(session==null)"Create account"else"Search recordings";findViewById<TextView>(R.id.title).text=title.uppercase();content.removeAllViews();findViewById<ScrollView>(R.id.scroll).scrollTo(0,0)
        val topLevel=title in listOf("Home","Fixtures","Standings","Store","Profile","Sign in")
        findViewById<View>(R.id.back).visibility=if(topLevel)View.INVISIBLE else View.VISIBLE
        nav.visibility=if(session==null)View.GONE else View.VISIBLE;nav.removeAllViews()
        if(session!=null){
            val selectedTab=when(title){
                "Match detail"->"Fixtures"
                "Product detail","Cart & checkout","Paid receipt"->"Store"
                "Live Arena","VOD Archive","VOD Player"->"Home"
                "Home","Fixtures","Standings","Store","Profile"->title
                else->"Profile"
            }
            val items=listOf(Triple(R.drawable.ic_home,"Home","Home"),Triple(R.drawable.ic_calendar,"Fixtures","Fixtures"),Triple(R.drawable.ic_standings,"Standings","Standings"),Triple(R.drawable.ic_bag,"Store","Store"),Triple(R.drawable.ic_person,"Profile","Profile"))
            for((icon,label,target) in items){
                val selected=label==selectedTab
                val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;minimumHeight=dp(60);contentDescription=label;if(selected)setBackgroundResource(R.drawable.bg_nav_selected);setOnClickListener{go(target)}}
                ImageView(this).apply{setImageResource(icon);setColorFilter(if(selected)Color.rgb(0,210,255)else Color.rgb(148,163,184));contentDescription=null}.also{item.addView(it,LinearLayout.LayoutParams(dp(24),dp(30)))}
                TextView(this).apply{text=label;textSize=10f;gravity=Gravity.CENTER;setTextColor(if(selected)Color.WHITE else Color.rgb(148,163,184));typeface=ResourcesCompat.getFont(this@MainActivity,R.font.space_grotesk);setTypeface(typeface,if(selected)Typeface.BOLD else Typeface.NORMAL)}.also{item.addView(it,LinearLayout.LayoutParams(-1,dp(22)))}
                nav.addView(item,LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(dp(1),0,dp(1),0)})
            }
        };banner()
    }
    fun banner(extra:String=""){if(!::status.isInitialized)return;if(forcedOffline())online=false;val n=if(session==null)0 else local.actions(owner,true).length();status.text=if(extra.isNotBlank())extra else (if(online)"●  ONLINE • ARENA SYNCED" else "○  OFFLINE • SAVED DATA")+if(n>0)" • $n QUEUED" else "";status.setTextColor(if(online)Color.rgb(0,210,255)else Color.rgb(245,158,11));status.setOnClickListener{if(session!=null)queueScreen()}}
    fun text(value:String,size:Float=16f,bold:Boolean=false,parent:LinearLayout=content):TextView{val t=TextView(this).apply{text=value;textSize=size;setTextColor(Color.rgb(248,250,252));setPadding(0,dp(4),0,dp(7));typeface=ResourcesCompat.getFont(this@MainActivity,if(bold)R.font.space_grotesk else R.font.plus_jakarta_sans);if(bold)setTypeface(typeface,Typeface.BOLD)};parent.addView(t);return t}
    fun sectionTitle(value:String,subtitle:String=""){val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(8),0,dp(8))};content.addView(row);text(value.uppercase(),18f,true,row).setTextColor(Color.WHITE);if(subtitle.isNotBlank())text(subtitle,12f,false,row).setTextColor(Color.rgb(148,163,184))}
    fun assetImage(resource:Int,description:String,height:Int=150,parent:LinearLayout=content){val frame=FrameLayout(this).apply{setBackgroundResource(R.drawable.bg_card_feature);clipToOutline=true};val image=ImageView(this).apply{setImageResource(resource);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=description};frame.addView(image,FrameLayout.LayoutParams(-1,-1));parent.addView(frame,LinearLayout.LayoutParams(-1,dp(height)).apply{bottomMargin=dp(14)})}
    fun teamBadge(parent:LinearLayout,alternate:Boolean=false){val image=ImageView(this).apply{setImageResource(if(alternate)R.drawable.team_crest_gold else R.drawable.team_crest_blue);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="Team crest"};parent.addView(image,LinearLayout.LayoutParams(-1,dp(54)))}
    fun card(title:String,subtitle:String="",click:(()->Unit)?=null):LinearLayout{val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));setBackgroundResource(R.drawable.bg_card);elevation=dp(2).toFloat()};content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)});text(title,16f,true,box);if(subtitle.isNotEmpty()){val sub=text(subtitle,12f,false,box);sub.setTextColor(Color.rgb(148,163,184));sub.setLineSpacing(dp(2).toFloat(),1.05f)};if(click!=null){box.isClickable=true;box.isFocusable=true;box.contentDescription="$title. $subtitle. Open details";box.setOnClickListener{click()}};return box}
    fun button(label:String,parent:LinearLayout=content,action:()->Unit):com.google.android.material.button.MaterialButton{val b=com.google.android.material.button.MaterialButton(this).apply{text=label;textSize=13f;isAllCaps=false;minHeight=dp(48);cornerRadius=dp(12);insetTop=0;insetBottom=0;setTextColor(Color.WHITE);typeface=ResourcesCompat.getFont(this@MainActivity,R.font.space_grotesk);setTypeface(typeface,Typeface.BOLD);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(37,99,235));strokeColor=android.content.res.ColorStateList.valueOf(Color.rgb(56,189,248));strokeWidth=dp(1);setPadding(dp(16),0,dp(16),0);setOnClickListener{if(!busy)action()}};parent.addView(b,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(8)});return b}
    fun field(label:String,value:String="",type:Int=InputType.TYPE_CLASS_TEXT,parent:LinearLayout=content):EditText{val wrap=com.google.android.material.textfield.TextInputLayout(this).apply{hint=label;boxBackgroundMode=com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE;boxBackgroundColor=Color.rgb(15,23,42);boxStrokeColor=Color.rgb(37,99,235);hintTextColor=android.content.res.ColorStateList.valueOf(Color.rgb(148,163,184));setBoxCornerRadii(dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat(),dp(12).toFloat())};val e=com.google.android.material.textfield.TextInputEditText(wrap.context).apply{setText(value);setSingleLine(true);inputType=type;minHeight=dp(52);textSize=14f;setTextColor(Color.rgb(248,250,252));typeface=ResourcesCompat.getFont(this@MainActivity,R.font.plus_jakarta_sans)};wrap.addView(e);if(type and InputType.TYPE_TEXT_VARIATION_PASSWORD!=0)wrap.endIconMode=com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE;parent.addView(wrap,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)});return e}
    fun picker(label:String,values:List<String>,parent:LinearLayout=content):Spinner{text(label,13f,true,parent);return Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,if(values.isEmpty())listOf("No choices available")else values);minimumHeight=dp(50);parent.addView(this,LinearLayout.LayoutParams(-1,dp(50)))}}
    fun notice(message:String){androidx.appcompat.app.AlertDialog.Builder(this).setTitle("NaSCon Sports").setMessage(message).setPositiveButton("OK",null).show()}
    fun confirm(message:String,action:()->Unit){androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Confirm action").setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Confirm"){_,_->action()}.show()}
    fun work(task:()->JSONObject,done:(JSONObject)->Unit,failed:((Exception)->Unit)?=null){
        if(busy&&busyGeneration==generation)return
        busy=true;busyGeneration=generation;progress.visibility=View.VISIBLE;banner("Connecting…")
        val stamp=generation
        executor.execute {
            try {
                val result=task()
                runOnUiThread {
                    if(isDestroyed)return@runOnUiThread
                    if(stamp==busyGeneration){busy=false;progress.visibility=View.GONE}
                    if(stamp==generation){online=true;prefs.edit().putString("last",java.text.DateFormat.getDateTimeInstance().format(java.util.Date())).apply();banner();done(result)}
                }
            }catch(error:Exception){
                runOnUiThread {
                    if(isDestroyed)return@runOnUiThread
                    if(stamp==busyGeneration){busy=false;progress.visibility=View.GONE}
                    if(stamp==generation){if(error !is ApiError)online=false;banner();if(failed!=null)failed(error)else notice(if(error is ApiError)error.message?:error.code else "Could not reach your server. Check USB forwarding or hotspot, then retry.")}
                }
            }
        }
    }
    fun fetch(path:String,done:(JSONObject)->Unit){val key="$owner:$path";if(forcedOffline()){online=false;banner();val cached=local.get(key);if(cached!=null)done(cached)else{card("Not saved on this device","Load this screen online first.");button("Retry"){restoreRoute(JSONObject().put("screen",screen).put("args",routeArgs))}};return};work({val r=api.call(path);local.put(key,r);r},done,{e->val cached=local.get(key);if(cached!=null){online=false;banner();done(cached)}else{card("Unable to load",if(e is ApiError)e.message?:"Request failed" else "Connect to your server to load this screen for the first time.");button("Retry"){restoreRoute(JSONObject().put("screen",screen).put("args",routeArgs))}}})}
    fun refresh(done:()->Unit){if(forcedOffline()){online=false;data=local.get("bootstrap:$owner")?:JSONObject();done();banner();return};work({val r=api.call("/api/v1/bootstrap");local.put("bootstrap:$owner",r);r},{r->data=r;done()},{e->if(e is ApiError&&e.code=="UNAUTHENTICATED"){sessions.clear();session=null;api.token="";login();notice("Your session expired. Please sign in again.")}else{data=local.get("bootstrap:$owner")?:JSONObject();done()}})}
    fun login(){signInScreen()}
    fun settings(){start("Server settings");card("Connect your arena","USB: http://127.0.0.1:8080\nEmulator: http://10.0.2.2:8080\nHotspot: your configured laptop address");val url=field("Server URL",api.base,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val off=android.widget.Switch(this).apply{text="Work offline • use cache and queue actions";isChecked=prefs.getBoolean("offline",false);minHeight=dp(56);setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("offline",checked).commit();online=!forcedOffline();banner()}};content.addView(off)
        fun valid():String?{val value=url.text.toString().trim().trimEnd('/');return try{val u=java.net.URI(value);if(u.scheme !in listOf("http","https")||u.host.isNullOrBlank()||u.userInfo!=null||u.query!=null||u.fragment!=null||!u.path.isNullOrEmpty()){url.error="Use http(s)://host:port without a path";null}else value}catch(_:Exception){url.error="Enter a valid server URL";null}}
        button("Save server"){valid()?.let{v->if(v!=api.base){prefs.edit().putString("server",v).commit();api.base=v;sessions.clear();session=null;api.token="";data=JSONObject();cart.clear();login();notice("Server saved. Sign in to this server.")}else notice("Server saved.")}}
        button("Test connection"){valid()?.let{v->work({ApiClient(v).call("/health")},{notice("Connection successful: ${it.optString("status")}");settings()})}};card("Last successful connection",prefs.getString("last","No successful connection yet")!!);text("For USB, run adb reverse tcp:8080 tcp:8080 on your laptop. If hotspot HTTP is blocked, use USB or HTTPS; only explicitly permitted development hosts allow cleartext.",14f)
    }
    @Deprecated("Scanner activity result compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,result:android.content.Intent?){super.onActivityResult(requestCode,resultCode,result);if(requestCode==2031&&resultCode==RESULT_OK)result?.getStringExtra("SCAN_RESULT")?.let{openArenaLink(it)}}
    fun go(name:String){when(name){"Sign in"->login();"Sign up"->signUpScreen();"Server settings"->settings();"VOD Archive"->vodArchive();"VOD Player"->vodPlayer(prefs.getString("last_vod",null)?.let{JSONObject(it)}?:JSONObject());"Admin dashboard"->adminDashboard();"Active Stream Director"->streamDirector();"Match Telemetry"->matchTelemetry();"Creator tools"->creatorHub();"Broadcast Studio"->broadcastStudio();"Stream Key Controls"->streamKeysScreen();"Co-stream Lobby"->costreamLobby();"Co-stream Host Room"->hostRoom();"QR Scanner"->qrScannerScreen();"QR Share & Export"->qrExportScreen();"Athlete Profiles"->athleteDirectory();"Athlete Profile"->athleteScreen(prefs.getString("creator_athlete","player-alpha-1")!!);"Live Arena"->arenaScreen(prefs.getString("creator_entity","match-m1")!!);"Fixtures"->fixturesScreen();"Standings"->standingsScreen();"Store"->storeScreen();"Profile"->profileScreen();"Match detail"->matchScreen(matchId);"Club roster"->rosterScreen();"Sync queue"->queueScreen();else->home()}}
    fun home(){
        start("Home");sectionTitle("Matchday arena","Live scores, big clashes and tournament operations")
        card("●  NASCON NATIONAL CUP '26","ARENA VERIFIED  •  ${if(online)"SYNCED NOW"else"OFFLINE CACHE"}")
        assetImage(R.drawable.home_player,"NaSCon athlete under stadium floodlights",148)
        val fixtures=data.optJSONArray("fixtures")?:JSONArray();val all=fixtures.objects();val live=all.firstOrNull{it.optString("status").lowercase() in listOf("in_progress","live")}
        if(live!=null){sectionTitle("● Live spotlight","Follow the action from Match Center");fixtureCard(live,0)}
        else card("Next match loading","No live fixture right now. Upcoming clashes are listed below.")
        val summary=card("Arena pulse","${data.optJSONArray("leagues")?.length()?:0} competitions • ${all.size} fixtures • ${role.replace('_',' ').uppercase()}")
        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};summary.addView(stats)
        for((label,value) in listOf("LIVE" to all.count{it.optString("status")=="in_progress"}.toString(),"UPCOMING" to all.count{it.optString("status")=="upcoming"}.toString(),"QUEUED" to local.actions(owner,true).length().toString())){
            val cell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(4),dp(8),dp(4),0)};text(value,20f,true,cell).apply{gravity=Gravity.CENTER;setTextColor(Color.rgb(0,210,255))};text(label,9f,true,cell).apply{gravity=Gravity.CENTER;setTextColor(Color.rgb(148,163,184))};stats.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
        }
        val upcoming=all.filter{it.optString("status")=="upcoming"}.take(2)
        if(upcoming.isNotEmpty()){sectionTitle("Big clashes today","Tap a fixture to open Match Center");upcoming.forEachIndexed{i,f->fixtureCard(f,i+1)}}
        sectionTitle("Arena access","Tournament tools for your role")
        button("Watch live arena"){arenaScreen(defaultArenaEntity())};button("VOD archive & replays"){vodArchive()};button("Go live • Broadcast Studio"){broadcastStudio()};button("Creator tools • Live, QR & athletes"){creatorHub()}
        button("Explore fixtures"){go("Fixtures")};button("View standings"){go("Standings")};button("Club & roster"){rosterScreen()};button("Store & merchandise"){go("Store")};button("Offline changes & sync"){queueScreen()};button("Refresh workspace"){refresh{home()}}
        sectionTitle("Latest from NaSCon","Matchday highlights")
        assetImage(R.drawable.highlight_goal,"Browse tournament recordings",112);content.getChildAt(content.childCount-1).apply{isClickable=true;isFocusable=true;contentDescription="Browse tournament recordings";setOnClickListener{vodArchive()}}
        if(canScore)button("Admin dashboard"){adminDashboard()};if(canScore)button("Prepare competition demonstration"){confirm("Create the tenant's demonstration league and roster once? Existing purchases and roster actions will be preserved."){work({api.call("/api/v1/demo/prepare",JSONObject())},{refresh{home()}})}}
        if(fixtures.length()==0)card("Your workspace is ready","An organizer can prepare the competition demonstration to populate matches. Refresh after setup.")
    }
    fun profileScreen(){nativeProfile()}
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull{optJSONObject(it)}
