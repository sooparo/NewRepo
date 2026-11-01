package com.sooparo.schedulecatcher

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {

        private sealed class Row {
            data class Section(val label: String) : Row()
            data class Event(val event: Agenda.Event) : Row()
            data class Todo(val todo: TodoStore.Todo) : Row()
            data class Hint(val text: String) : Row()
        }

        private var rows: List<Row> = emptyList()

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            val now = LocalDateTime.now()
            val events = Agenda.upcomingEvents(context, now)
            val todos = Agenda.todos(context, now.toLocalDate())
            rows = buildList {
                add(Row.Section("📅 일정"))
                if (events.isEmpty()) add(Row.Hint("다가오는 일정이 없어요")) else events.forEach { add(Row.Event(it)) }
                add(Row.Section("✅ 할 일"))
                if (todos.isEmpty()) add(Row.Hint("할 일이 없어요  ( + 로 추가)")) else todos.take(15).forEach { add(Row.Todo(it)) }
            }
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 3
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val today = LocalDate.now()
            return when (val row = rows.getOrNull(position)) {
                is Row.Section -> RemoteViews(context.packageName, R.layout.widget_item_section).apply {
                    setTextViewText(R.id.sectionText, row.label)
                }
                is Row.Hint -> RemoteViews(context.packageName, R.layout.widget_item_section).apply {
                    setTextViewText(R.id.sectionText, row.text)
                }
                is Row.Event -> RemoteViews(context.packageName, R.layout.widget_item_event).apply {
                    setTextViewText(R.id.eventTime, Formats.shortWhen(row.event.at, row.event.allDay, today))
                    setTextViewText(R.id.eventTitle, row.event.title + (row.event.location?.let { " · $it" } ?: ""))
                    setOnClickFillInIntent(R.id.eventRow, Intent())
                }
                is Row.Todo -> RemoteViews(context.packageName, R.layout.widget_item_todo).apply {
                    val t = row.todo
                    setTextViewText(R.id.todoCheck, if (t.done) "☑" else "☐")
                    setTextViewText(R.id.todoTitle, t.title)
                    setTextViewText(R.id.todoDue, if (t.done) "완료" else t.dueDate?.let { Formats.shortDue(it, today) } ?: "")
                    setOnClickFillInIntent(R.id.todoRow, Intent().putExtra(ScheduleWidgetProvider.EXTRA_TODO_ID, t.id))
                }
                null -> RemoteViews(context.packageName, R.layout.widget_item_section)
            }
        }
    }
}
