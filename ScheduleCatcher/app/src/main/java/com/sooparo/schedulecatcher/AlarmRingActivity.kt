package com.sooparo.schedulecatcher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.sooparo.schedulecatcher.databinding.ActivityAlarmRingBinding

/** 잠금화면 위에 표시되는 알람 화면. 알람음은 알림(FLAG_INSISTENT)이 담당한다. */
class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bind(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
    }

    private fun bind(intent: Intent) {
        val entry = AlarmStore.Entry(
            id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1),
            triggerAt = System.currentTimeMillis(),
            title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE) ?: "일정 알람",
            info = intent.getStringExtra(AlarmReceiver.EXTRA_INFO).orEmpty(),
        )
        binding.alarmTitle.text = entry.title
        binding.alarmInfo.text = entry.info

        binding.btnDismiss.setOnClickListener {
            sendBroadcast(AlarmReceiver.actionIntent(this, AlarmReceiver.ACTION_DISMISS, entry))
            finish()
        }
        binding.btnSnooze.setOnClickListener {
            sendBroadcast(AlarmReceiver.actionIntent(this, AlarmReceiver.ACTION_SNOOZE, entry))
            finish()
        }
    }
}
