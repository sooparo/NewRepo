package com.sooparo.schedulecatcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.sooparo.schedulecatcher.core.CalDavClient
import com.sooparo.schedulecatcher.core.ICalendarBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** 사용자가 고른 대상(캘린더/알람/할 일)으로 일정을 저장하고, 저장 기록을 남긴다. */
class ScheduleSaver(private val activity: Activity) {

    sealed class Outcome(val message: String) {
        class Ok(message: String) : Outcome(message)
        class Fail(message: String) : Outcome(message)
    }

    data class Result(val calendar: Outcome?, val alarm: Outcome?, val todo: Outcome?) {
        val allOk: Boolean get() = listOf(calendar, alarm, todo).none { it is Outcome.Fail }
        val summary: String get() = listOfNotNull(calendar?.message, alarm?.message, todo?.message).joinToString("\n")
    }

    private val prefs = Prefs(activity)
    private val zone: ZoneId = ZoneId.systemDefault()

    // 이번 저장에서 생긴 결과 (기록용)
    private var eventUrl: String? = null
    private var openedCalendarApp = false
    private var alarmId: Int? = null
    private var todoId: String? = null

    suspend fun save(data: ScheduleData, targets: SaveTargets, leadMinutes: Int = prefs.alarmLeadMinutes): Result {
        val calendar = if (targets.calendar) saveCalendar(data, leadMinutes) else null
        val alarm = if (targets.alarm) saveAlarm(data, leadMinutes) else null
        val todo = if (targets.todo) saveTodo(data) else null

        if (eventUrl != null || openedCalendarApp || alarmId != null || todoId != null) {
            HistoryStore.upsert(
                activity,
                HistoryStore.Entry(
                    id = data.id,
                    title = data.title,
                    start = data.start,
                    allDay = data.allDay,
                    dateKnown = data.dateKnown,
                    location = data.location,
                    rrule = data.rrule,
                    savedAt = System.currentTimeMillis(),
                    eventUrl = eventUrl,
                    openedCalendarApp = openedCalendarApp,
                    alarmId = alarmId,
                    todoId = todoId,
                ),
            )
        }
        prefs.lastTargets = targets
        return Result(calendar, alarm, todo)
    }

    // ---------------------------------------------------------------------------------------------
    // 네이버 캘린더 (CalDAV)
    // ---------------------------------------------------------------------------------------------

    private suspend fun saveCalendar(data: ScheduleData, leadMinutes: Int): Outcome {
        if (!data.dateKnown) return Outcome.Fail("📅 날짜가 없어 캘린더에 저장할 수 없어요. 날짜를 지정해 주세요.")
        if (!prefs.hasNaverCalendar) return openCalendarInsert(data)

        val description = buildString {
            data.sourceLabel?.let { append("출처: ").append(it) }
            if (prefs.includeOriginalText && data.sourceText.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(data.sourceText)
            }
        }
        val event = ICalendarBuilder.Event(
            uid = data.id,
            title = data.title,
            start = data.start,
            end = data.end,
            allDay = data.allDay,
            location = data.location,
            description = description.ifEmpty { null },
            reminderMinutesBefore = if (prefs.calendarReminder) leadMinutes else null,
            rrule = data.rrule,
        )

        return withContext(Dispatchers.IO) {
            try {
                eventUrl = CalDavClient(prefs.naverId, prefs.naverAppPassword)
                    .createEvent(prefs.calendarUrl!!, data.id, ICalendarBuilder.build(event, zone))
                val repeat = data.recurrence?.let { " (${it.label()})" }.orEmpty()
                Outcome.Ok("📅 네이버 캘린더(${prefs.calendarName ?: "기본"})에 저장했어요.$repeat")
            } catch (e: CalDavClient.CalDavException) {
                if (e.code == 412) {
                    Outcome.Ok("📅 이미 네이버 캘린더에 저장된 일정이에요.")
                } else {
                    Outcome.Fail("📅 캘린더 저장 실패: ${e.message}")
                }
            } catch (e: Exception) {
                Outcome.Fail("📅 캘린더 저장 실패: 네트워크 연결을 확인하세요. (${e.javaClass.simpleName})")
            }
        }
    }

    /** 네이버 계정 미설정 시: 기기 캘린더 앱의 '새 일정' 화면을 미리 채워서 연다. */
    private fun openCalendarInsert(data: ScheduleData): Outcome {
        val begin = data.start.atZone(zone).toInstant().toEpochMilli()
        val end = (data.end ?: if (data.allDay) data.start.plusDays(1) else data.start.plusHours(1))
            .atZone(zone).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, data.allDay)
            .putExtra(CalendarContract.Events.TITLE, data.title)
            .apply {
                data.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                data.rrule?.let { putExtra(CalendarContract.Events.RRULE, it) }
            }
        return try {
            activity.startActivity(intent)
            openedCalendarApp = true
            Outcome.Ok("📅 네이버 계정이 설정되지 않아 캘린더 앱을 열었어요. 저장을 마무리해 주세요.")
        } catch (e: ActivityNotFoundException) {
            Outcome.Fail("📅 네이버 캘린더 계정을 먼저 설정하세요 (앱 설정 화면).")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 알람
    // ---------------------------------------------------------------------------------------------

    private fun saveAlarm(data: ScheduleData, leadMinutes: Int): Outcome {
        if (!data.dateKnown) return Outcome.Fail("⏰ 날짜가 없어 알람을 설정할 수 없어요. 날짜를 지정해 주세요.")
        val now = LocalDateTime.now()
        val rec = data.recurrence
        val anchorEvent = if (data.allDay) data.start.toLocalDate().atTime(ALL_DAY_ALARM_HOUR, 0) else data.start
        val lead = leadMinutes.toLong()

        // 이번 회차(또는 첫 회차)의 일정 시각: 알람 시점이 이미 지났으면 반복 일정은 다음 회차로
        var eventTime = anchorEvent
        if (rec != null) {
            while (!eventTime.minusMinutes(lead).isAfter(now)) {
                eventTime = rec.nextAfter(anchorEvent, eventTime) ?: break
            }
        }
        var alarmAt = eventTime.minusMinutes(lead)
        var note = ""
        if (!alarmAt.isAfter(now)) {
            if (!eventTime.isAfter(now)) return Outcome.Fail("⏰ 이미 지난 시각이라 알람을 설정할 수 없어요.")
            alarmAt = eventTime
            note = " (설정한 알림 시점이 지나서 일정 시각으로 맞췄어요)"
        }

        val info = buildString {
            if (rec != null) {
                append(rec.label())
                if (!data.allDay) append(' ').append(Formats.time(data.start.toLocalTime()))
            } else {
                append(Formats.whenText(data))
            }
            data.location?.let { append(" · ").append(it) }
        }

        if (prefs.useClockApp && rec == null) {
            val withinDay = Duration.between(now, alarmAt) < Duration.ofHours(23)
            if (withinDay && setClockAppAlarm(alarmAt, data.title)) {
                return Outcome.Ok("⏰ 시계 앱에 ${Formats.time(alarmAt.toLocalTime())} 알람을 등록했어요.$note")
            }
            if (!withinDay) note += " (시계 앱 알람은 24시간 이내만 가능해 앱 알람으로 등록)"
        }

        val entry = AlarmStore.Entry(
            id = data.notificationId,
            triggerAt = alarmAt.atZone(zone).toInstant().toEpochMilli(),
            title = data.title,
            info = info,
            rrule = rec?.toRRule(),
            anchorAt = if (rec != null) anchorEvent.atZone(zone).toInstant().toEpochMilli() else null,
            leadMs = Duration.between(alarmAt, eventTime).toMillis(),
        )
        if (!AlarmScheduler.schedule(activity, entry)) {
            AlarmScheduler.exactAlarmSettingsIntent(activity)?.let { runCatching { activity.startActivity(it) } }
            return Outcome.Fail("⏰ '알람 및 리마인더' 권한을 허용한 뒤 다시 시도하세요.")
        }
        alarmId = entry.id
        val repeat = rec?.let { " · ${it.label()} 반복" }.orEmpty()
        return Outcome.Ok("⏰ ${Formats.dateTime(alarmAt)} 알람을 예약했어요$repeat.$note")
    }

    /** 기본 시계 앱 알람(시:분만 지정 가능 → 다음 해당 시각에 울림) */
    private fun setClockAppAlarm(at: LocalDateTime, title: String): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, at.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, title.take(40))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return try {
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 할 일
    // ---------------------------------------------------------------------------------------------

    private fun saveTodo(data: ScheduleData): Outcome {
        val existing = HistoryStore.get(activity, data.id)?.todoId?.let { id -> TodoStore.all(activity).firstOrNull { it.id == id } }
        if (existing != null) {
            todoId = existing.id
            return Outcome.Ok("✅ 이미 할 일에 있어요.")
        }
        val todo = TodoStore.add(
            activity,
            title = data.title,
            dueDate = if (data.dateKnown) data.start.toLocalDate() else null,
            dueTime = if (data.dateKnown && !data.allDay) data.start.toLocalTime() else null,
            source = data.sourceLabel,
        )
        todoId = todo.id
        val due = todo.dueDate?.let { " (마감 ${Formats.date(it)})" }.orEmpty()
        return Outcome.Ok("✅ 할 일에 추가했어요$due.")
    }

    companion object {
        /** 종일 일정의 알람 기준 시각 */
        const val ALL_DAY_ALARM_HOUR = 8

        /**
         * 저장 취소: 네이버 캘린더 일정 삭제, 알람 취소, 할 일 삭제 후 기록 제거.
         * @return 사용자에게 보여줄 결과 메시지
         */
        suspend fun undo(context: Context, entry: HistoryStore.Entry): String {
            val messages = mutableListOf<String>()
            var calendarFailed = false
            entry.eventUrl?.let { url ->
                val prefs = Prefs(context)
                val result = withContext(Dispatchers.IO) {
                    runCatching { CalDavClient(prefs.naverId, prefs.naverAppPassword).deleteEvent(url) }
                }
                if (result.isSuccess) {
                    messages += "📅 네이버 캘린더에서 삭제했어요."
                } else {
                    calendarFailed = true
                    messages += "📅 캘린더 삭제 실패: ${result.exceptionOrNull()?.message ?: "네트워크 확인"}"
                }
            }
            if (entry.openedCalendarApp) messages += "📅 기기 캘린더 앱에 저장한 일정은 캘린더 앱에서 직접 지워 주세요."
            entry.alarmId?.let {
                AlarmScheduler.cancel(context, it)
                messages += "⏰ 알람을 취소했어요."
            }
            entry.todoId?.let {
                TodoStore.remove(context, it)
                messages += "✅ 할 일에서 지웠어요."
            }
            // 캘린더 삭제가 실패하면 다시 시도할 수 있도록 기록을 남긴다
            if (calendarFailed) {
                HistoryStore.replace(context, entry.copy(alarmId = null, todoId = null, openedCalendarApp = false))
            } else {
                HistoryStore.remove(context, entry.id)
            }
            return messages.joinToString("\n").ifEmpty { "기록을 지웠어요." }
        }
    }
}
