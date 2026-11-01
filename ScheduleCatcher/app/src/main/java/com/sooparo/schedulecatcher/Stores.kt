package com.sooparo.schedulecatcher

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** 할 일 목록 (기기 안에만 저장) */
object TodoStore {
    data class Todo(
        val id: String,
        val title: String,
        val dueDate: LocalDate?,
        val dueTime: LocalTime?,
        val done: Boolean,
        val createdAt: Long,
        val doneAt: Long?,
        val source: String?,
    )

    private const val PREFS = "todos"
    private const val KEY = "items"

    @Synchronized
    fun all(context: Context): List<Todo> = readArray(context, PREFS, KEY).mapNotNull { o ->
        runCatching {
            Todo(
                id = o.getString("id"),
                title = o.getString("title"),
                dueDate = o.optStringOrNull("dueDate")?.let(LocalDate::parse),
                dueTime = o.optStringOrNull("dueTime")?.let(LocalTime::parse),
                done = o.optBoolean("done"),
                createdAt = o.optLong("createdAt"),
                doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
                source = o.optStringOrNull("source"),
            )
        }.getOrNull()
    }

    fun add(context: Context, title: String, dueDate: LocalDate?, dueTime: LocalTime?, source: String?): Todo {
        val todo = Todo(UUID.randomUUID().toString(), title, dueDate, dueTime, false, System.currentTimeMillis(), null, source)
        save(context, all(context) + todo)
        return todo
    }

    fun setDone(context: Context, id: String, done: Boolean) = update(context) { list ->
        list.map { if (it.id == id) it.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null) else it }
    }

    fun toggle(context: Context, id: String) {
        val t = all(context).firstOrNull { it.id == id } ?: return
        setDone(context, id, !t.done)
    }

    fun remove(context: Context, id: String) = update(context) { list -> list.filter { it.id != id } }

    /** 완료 후 7일 지난 항목 정리 */
    fun purgeOld(context: Context) {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 3600_000L
        update(context) { list -> list.filter { !it.done || (it.doneAt ?: Long.MAX_VALUE) > cutoff } }
    }

    /** 미완료 → 마감 빠른 순(마감 없음은 뒤) → 완료 */
    fun sorted(list: List<Todo>): List<Todo> = list.sortedWith(
        compareBy<Todo> { it.done }
            .thenBy { it.dueDate ?: LocalDate.MAX }
            .thenBy { it.dueTime ?: LocalTime.MAX }
            .thenBy { it.createdAt },
    )

    @Synchronized
    private fun update(context: Context, block: (List<Todo>) -> List<Todo>) = save(context, block(all(context)))

    @Synchronized
    private fun save(context: Context, list: List<Todo>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .putOpt("dueDate", t.dueDate?.toString())
                    .putOpt("dueTime", t.dueTime?.toString())
                    .put("done", t.done)
                    .put("createdAt", t.createdAt)
                    .putOpt("doneAt", t.doneAt)
                    .putOpt("source", t.source),
            )
        }
        writeArray(context, PREFS, KEY, arr)
        WidgetUpdater.refresh(context)
    }
}

/** 저장 기록 (취소/위젯/브리핑에 사용) */
object HistoryStore {
    data class Entry(
        val id: String,
        val title: String,
        val start: LocalDateTime,
        val allDay: Boolean,
        val dateKnown: Boolean,
        val location: String?,
        val rrule: String?,
        val savedAt: Long,
        /** 네이버 캘린더 일정 URL (CalDAV) */
        val eventUrl: String?,
        /** 기기 캘린더 앱으로 넘긴 경우 (취소 불가) */
        val openedCalendarApp: Boolean,
        val alarmId: Int?,
        val todoId: String?,
    ) {
        /** 캘린더 또는 알람으로 저장된 '일정' 인지 (할 일만 저장한 건 제외) */
        val isSchedule: Boolean get() = dateKnown && (eventUrl != null || openedCalendarApp || alarmId != null)
    }

    private const val PREFS = "history"
    private const val KEY = "entries"
    private const val MAX = 100

    @Synchronized
    fun all(context: Context): List<Entry> = readArray(context, PREFS, KEY).mapNotNull { o ->
        runCatching {
            Entry(
                id = o.getString("id"),
                title = o.getString("title"),
                start = LocalDateTime.parse(o.getString("start")),
                allDay = o.optBoolean("allDay"),
                dateKnown = o.optBoolean("dateKnown", true),
                location = o.optStringOrNull("location"),
                rrule = o.optStringOrNull("rrule"),
                savedAt = o.optLong("savedAt"),
                eventUrl = o.optStringOrNull("eventUrl"),
                openedCalendarApp = o.optBoolean("openedCalendarApp"),
                alarmId = if (o.has("alarmId")) o.getInt("alarmId") else null,
                todoId = o.optStringOrNull("todoId"),
            )
        }.getOrNull()
    }

    fun get(context: Context, id: String) = all(context).firstOrNull { it.id == id }

    /** 같은 id 로 다시 저장하면(예: 캘린더 후 알람 추가) 기존 기록에 합친다 */
    @Synchronized
    fun upsert(context: Context, entry: Entry) {
        val list = all(context)
        val old = list.firstOrNull { it.id == entry.id }
        val merged = if (old == null) {
            entry
        } else {
            entry.copy(
                eventUrl = entry.eventUrl ?: old.eventUrl,
                openedCalendarApp = entry.openedCalendarApp || old.openedCalendarApp,
                alarmId = entry.alarmId ?: old.alarmId,
                todoId = entry.todoId ?: old.todoId,
            )
        }
        save(context, (listOf(merged) + list.filter { it.id != entry.id }).take(MAX))
    }

    /** 합치지 않고 그대로 덮어쓴다 */
    @Synchronized
    fun replace(context: Context, entry: Entry) =
        save(context, all(context).map { if (it.id == entry.id) entry else it })

    @Synchronized
    fun remove(context: Context, id: String) = save(context, all(context).filter { it.id != id })

    @Synchronized
    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("start", e.start.toString())
                    .put("allDay", e.allDay)
                    .put("dateKnown", e.dateKnown)
                    .putOpt("location", e.location)
                    .putOpt("rrule", e.rrule)
                    .put("savedAt", e.savedAt)
                    .putOpt("eventUrl", e.eventUrl)
                    .put("openedCalendarApp", e.openedCalendarApp)
                    .putOpt("alarmId", e.alarmId)
                    .putOpt("todoId", e.todoId),
            )
        }
        writeArray(context, PREFS, KEY, arr)
        WidgetUpdater.refresh(context)
    }
}

private fun readArray(context: Context, prefs: String, key: String): List<JSONObject> {
    val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, "[]")
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())
}

private fun writeArray(context: Context, prefs: String, key: String, arr: JSONArray) {
    context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit { putString(key, arr.toString()) }
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null
