package com.sooparo.schedulecatcher

import android.content.Context
import com.sooparo.schedulecatcher.core.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime

/** 위젯/아침 브리핑에 보여줄 일정·할 일 모음 */
object Agenda {

    data class Event(val id: String, val title: String, val at: LocalDateTime, val allDay: Boolean, val location: String?)

    /** 이 앱으로 저장한 일정 중 [from] 이후 [days]일 안의 다음 회차들 (반복 일정 포함) */
    fun upcomingEvents(context: Context, now: LocalDateTime = LocalDateTime.now(), days: Long = 7, max: Int = 8): List<Event> {
        val today = now.toLocalDate()
        val until = today.plusDays(days)
        return HistoryStore.all(context)
            .filter { it.isSchedule }
            .mapNotNull { e -> nextOccurrence(e, now)?.let { Event(e.id, e.title, it, e.allDay, e.location) } }
            .filter { it.at.toLocalDate().isBefore(until) }
            .sortedBy { it.at }
            .take(max)
    }

    fun eventsOn(context: Context, date: LocalDate): List<Event> =
        upcomingEvents(context, date.atStartOfDay(), days = 1, max = 20)

    /** 지금 기준 다음(또는 진행 중인) 회차. 지난 지 1시간 이내 일정까지 표시 */
    private fun nextOccurrence(e: HistoryStore.Entry, now: LocalDateTime): LocalDateTime? {
        val today = now.toLocalDate()
        val rec = Recurrence.fromRRule(e.rrule)
        if (rec == null) {
            val visible = if (e.allDay) !e.start.toLocalDate().isBefore(today) else e.start.isAfter(now.minusHours(1))
            return e.start.takeIf { visible }
        }
        return if (e.allDay) {
            rec.firstOnOrAfter(e.start.toLocalDate(), today)?.atStartOfDay()
        } else {
            val after = now.minusHours(1)
            if (e.start.isAfter(after)) e.start else rec.nextAfter(e.start, after)
        }
    }

    /** 미완료 할 일 (마감 순) + 오늘 완료한 할 일 */
    fun todos(context: Context, today: LocalDate = LocalDate.now()): List<TodoStore.Todo> {
        val startOfToday = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return TodoStore.sorted(TodoStore.all(context))
            .filter { !it.done || (it.doneAt ?: 0) >= startOfToday }
    }

    /** 브리핑용: 오늘까지 마감이거나 마감 없는 미완료 할 일 */
    fun todosForToday(context: Context, today: LocalDate = LocalDate.now()): List<TodoStore.Todo> =
        TodoStore.sorted(TodoStore.all(context)).filter { !it.done && (it.dueDate == null || !it.dueDate.isAfter(today)) }
}
