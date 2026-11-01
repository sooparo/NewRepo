package com.sooparo.schedulecatcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅/업데이트/정확한 알람 권한 허용 후 알람 재등록 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
