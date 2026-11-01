package com.sooparo.schedulecatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** AlarmManager.setAlarmClock 으로 특정 날짜/시각에 울리는 알람을 예약한다. */
object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    /** @return 예약 성공 여부 (정확한 알람 권한이 없으면 false) */
    fun schedule(context: Context, entry: AlarmStore.Entry): Boolean {
        if (!canScheduleExact(context)) return false
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context,
            entry.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(entry.triggerAt, show), firePendingIntent(context, entry.id))
            AlarmStore.put(context, entry)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, id))
        AlarmStore.remove(context, id)
    }

    /** 재부팅/앱 업데이트 후 남아있는 알람 재등록 */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        AlarmStore.all(context).forEach { entry ->
            if (entry.triggerAt > now) schedule(context, entry) else AlarmStore.remove(context, entry.id)
        }
    }

    private fun firePendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        id,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE).putExtra(AlarmReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
