package com.sooparo.schedulecatcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import java.time.LocalDate

/** 홈 화면 위젯: 위쪽 일정, 아래쪽 할 일 체크리스트 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, build(context, it)) }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetList)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ITEM_CLICK) {
            intent.getStringExtra(EXTRA_TODO_ID)?.let { TodoStore.toggle(context, it) } // 저장 시 위젯 자동 갱신
        }
    }

    companion object {
        const val ACTION_ITEM_CLICK = "com.sooparo.schedulecatcher.WIDGET_ITEM_CLICK"
        const val EXTRA_TODO_ID = "widget.todoId"

        fun build(context: Context, widgetId: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_root)
            rv.setTextViewText(R.id.widgetTitle, "오늘 ${Formats.date(LocalDate.now())}")

            val service = Intent(context, WidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.widgetList, service)
            rv.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val template = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_ITEM_CLICK),
                mutable or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setPendingIntentTemplate(R.id.widgetList, template)

            val openTasks = PendingIntent.getActivity(
                context, 1, Intent(context, TasksActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widgetHeader, openTasks)
            rv.setOnClickPendingIntent(R.id.widgetTitle, openTasks)
            rv.setOnClickPendingIntent(R.id.widgetEmpty, openTasks)

            val add = PendingIntent.getActivity(
                context, 2, Intent(context, TodoEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widgetAdd, add)

            val paste = PendingIntent.getActivity(
                context, 3, Intent(context, PasteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widgetPaste, paste)
            return rv
        }
    }
}

object WidgetUpdater {
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(app, ScheduleWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ids.forEach { manager.updateAppWidget(it, ScheduleWidgetProvider.build(app, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
    }
}
