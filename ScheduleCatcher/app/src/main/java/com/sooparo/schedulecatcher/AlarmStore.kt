package com.sooparo.schedulecatcher

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** 예약된 앱 알람 목록(재부팅 후 다시 등록하기 위해 저장). */
object AlarmStore {
    data class Entry(
        val id: Int,
        val triggerAt: Long,
        val title: String,
        val info: String,
        /** 반복 알람이면 RRULE */
        val rrule: String? = null,
        /** 반복 기준이 되는 첫 일정 시각 (epoch ms) */
        val anchorAt: Long? = null,
        /** 일정 시각보다 몇 ms 먼저 울리는지 */
        val leadMs: Long = 0,
    )

    private const val PREFS = "alarms"
    private const val KEY = "entries"

    @Synchronized
    fun all(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    id = o.getInt("id"),
                    triggerAt = o.getLong("at"),
                    title = o.optString("title"),
                    info = o.optString("info"),
                    rrule = if (o.has("rrule")) o.getString("rrule") else null,
                    anchorAt = if (o.has("anchorAt")) o.getLong("anchorAt") else null,
                    leadMs = o.optLong("leadMs"),
                )
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
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("at", it.triggerAt)
                    .put("title", it.title)
                    .put("info", it.info)
                    .putOpt("rrule", it.rrule)
                    .putOpt("anchorAt", it.anchorAt)
                    .put("leadMs", it.leadMs),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, arr.toString()) }
    }
}
