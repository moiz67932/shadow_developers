package com.example.nasconsports
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
class LocalStore(context: Context): SQLiteOpenHelper(context,"nascon.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE cache (key TEXT PRIMARY KEY, value TEXT NOT NULL)"); db.execSQL("CREATE TABLE actions (id TEXT PRIMARY KEY, owner TEXT NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL, result TEXT)") }
    override fun onUpgrade(db: SQLiteDatabase,old: Int,new: Int) {}
    fun put(key: String,value: JSONObject) { writableDatabase.insertWithOnConflict("cache",null,ContentValues().apply { put("key",key); put("value",value.toString()) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun get(key: String): JSONObject? = readableDatabase.rawQuery("SELECT value FROM cache WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) JSONObject(it.getString(0)) else null }
    fun queue(owner: String,type: String,base: Int,payload: JSONObject): JSONObject {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val action = JSONObject().put("action_id",UUID.randomUUID().toString()).put("type",type).put("queued_at",format.format(Date())).put("base_version",base).put("payload",payload)
        writableDatabase.insertOrThrow("actions",null,ContentValues().apply { put("id",action.getString("action_id"));put("owner",owner);put("body",action.toString());put("status","pending") }); return action
    }
    fun actions(owner: String,pending: Boolean = false): JSONArray { val a=JSONArray();readableDatabase.rawQuery("SELECT body,status,result FROM actions WHERE owner=?"+(if(pending) " AND status='pending'" else "")+" ORDER BY rowid",arrayOf(owner)).use { while(it.moveToNext()) { val row=JSONObject(it.getString(0));if(!pending) { row.put("local_status",it.getString(1));if(!it.isNull(2))row.put("result",JSONObject(it.getString(2))) };a.put(row) } };return a }
    fun resolve(owner: String,r: JSONObject) { writableDatabase.update("actions",ContentValues().apply{put("status",r.getString("status"));put("result",r.toString())},"id=? AND owner=?",arrayOf(r.getString("action_id"),owner)) }
}
