package com.sooparo.schedulecatcher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 감지 알림의 [캘린더] [알람] [둘 다] 버튼을 눌렀을 때 실행되는 화면 없는 Activity.
 * (Activity 로 처리해야 시계 앱/캘린더 앱 호출이 백그라운드 제한에 걸리지 않는다)
 */
class QuickSaveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = ScheduleData.fromIntent(intent)
        val target = intent.getStringExtra(EXTRA_TARGET)?.let { runCatching { SaveTarget.valueOf(it) }.getOrNull() }
        if (data == null || target == null) {
            finish()
            return
        }
        Notifications.cancel(this, data.notificationId)

        lifecycleScope.launch {
            val result = ScheduleSaver(this@QuickSaveActivity).save(data, target)
            Toast.makeText(applicationContext, result.summary, Toast.LENGTH_LONG).show()
            if (!result.allOk) Notifications.showResult(applicationContext, data, result.summary)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_TARGET = "save.target"
    }
}
