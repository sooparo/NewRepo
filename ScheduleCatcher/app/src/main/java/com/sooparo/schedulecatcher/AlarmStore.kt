package com.sooparo.schedulecatcher

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** 예약된 앱 알람 목록(재부팅 후 다시 등록하기 위해 저장). */
object AlarmStore {
    data class Entry(val id: Int, val triggerAt: Long, val title: String, val info: String)

    private const val PREFS = "alarms"
    private const val KEY = "entries"

    @Synchronized
    fun all(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getInt("id"), o.getLong("at"), o.optString("title"), o.optString("info"))
            }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: Int): Entry? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun put(context: Context, entry: Entry) = save(context, all(context).filter { it.id != entry.id } + entry)

    @Synchronized
    fun remove(context: Context, id: Int) = save(context, all(context).filter { it.id != id })

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().put("id", it.id).put("at", it.triggerAt).put("title", it.title).put("info", it.info))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, arr.toString()) }
    }
}
