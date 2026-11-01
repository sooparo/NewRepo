package com.sooparo.schedulecatcher

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sooparo.schedulecatcher.core.CalDavClient
import com.sooparo.schedulecatcher.core.ScheduleParser
import com.sooparo.schedulecatcher.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 설정 화면: 권한, 네이버 캘린더 연결, 알람 방식, 감지 대상 앱, 인식 테스트 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { Prefs(this) }
    private var lastTest: ScheduleData? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 권한 ---
        binding.btnListenerAccess.setOnClickListener {
            openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnAppInfo.setOnClickListener {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        binding.btnPostNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        binding.btnExactAlarm.setOnClickListener {
            AlarmScheduler.exactAlarmSettingsIntent(this)?.let { openSettings(it) }
        }
        binding.btnFullScreen.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 34) {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }

        // --- 네이버 캘린더 ---
        binding.inputNaverId.setText(prefs.naverId)
        binding.inputNaverPw.setText(prefs.naverAppPassword)
        binding.btnConnect.setOnClickListener { connectNaver() }

        // --- 알람 ---
        setupLeadSpinner()
        binding.switchClockApp.isChecked = prefs.useClockApp
        binding.switchClockApp.setOnCheckedChangeListener { _, checked -> prefs.useClockApp = checked }
        binding.switchCalendarReminder.isChecked = prefs.calendarReminder
        binding.switchCalendarReminder.setOnCheckedChangeListener { _, checked -> prefs.calendarReminder = checked }
        binding.switchIncludeText.isChecked = prefs.includeOriginalText
        binding.switchIncludeText.setOnCheckedChangeListener { _, checked -> prefs.includeOriginalText = checked }

        // --- 위젯 · 브리핑 ---
        binding.btnOpenTasks.setOnClickListener { startActivity(Intent(this, TasksActivity::class.java)) }
        binding.switchBriefing.isChecked = prefs.briefingEnabled
        binding.switchBriefing.setOnCheckedChangeListener { _, checked ->
            prefs.briefingEnabled = checked
            BriefingScheduler.schedule(this)
        }
        binding.btnBriefingTime.setOnClickListener {
            val m = prefs.briefingMinuteOfDay
            TimePickerDialog(this, { _, h, min ->
                prefs.briefingMinuteOfDay = h * 60 + min
                BriefingScheduler.schedule(this)
                refreshStatus()
            }, m / 60, m % 60, false).show()
        }
        binding.btnBriefingNow.setOnClickListener { BriefingScheduler.show(this, force = true) }
        binding.switchThirdBoth.isChecked = prefs.thirdButtonBoth
        binding.switchThirdBoth.setOnCheckedChangeListener { _, checked -> prefs.thirdButtonBoth = checked }

        // --- 감지 대상 앱 ---
        binding.btnPackages.setOnClickListener { choosePackages() }

        // --- 인식 테스트 ---
        binding.btnTest.setOnClickListener { runTest() }
        binding.btnTestOpen.setOnClickListener {
            lastTest?.let { startActivity(it.putInto(Intent(this, ConfirmActivity::class.java))) }
        }
        binding.btnTestNotify.setOnClickListener {
            lastTest?.let { Notifications.showDetected(this, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.statusListener.text = status(listenerOn, "알림 읽기 권한")
        binding.btnListenerAccess.visibility = if (listenerOn) View.GONE else View.VISIBLE
        binding.restrictedHint.visibility = if (listenerOn || Build.VERSION.SDK_INT < 33) View.GONE else View.VISIBLE
        binding.btnAppInfo.visibility = binding.restrictedHint.visibility

        val postOn = Notifications.canPost(this)
        binding.statusPostNotifications.text = status(postOn, "알림 표시 권한")
        binding.btnPostNotifications.visibility = if (postOn) View.GONE else View.VISIBLE

        val exactOn = AlarmScheduler.canScheduleExact(this)
        binding.statusExactAlarm.text = status(exactOn, "알람 및 리마인더 권한")
        binding.btnExactAlarm.visibility = if (exactOn) View.GONE else View.VISIBLE

        val fullScreenOn = Build.VERSION.SDK_INT < 34 ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        binding.statusFullScreen.text = status(fullScreenOn, "잠금화면 알람 화면 권한")
        binding.btnFullScreen.visibility = if (fullScreenOn) View.GONE else View.VISIBLE

        binding.calendarStatus.text = if (prefs.hasNaverCalendar) {
            "✅ 연결됨: ${prefs.calendarName}"
        } else {
            "⚠️ 연결 안 됨 (캘린더 저장 시 기기 캘린더 앱이 열립니다)"
        }
        val bm = prefs.briefingMinuteOfDay
        binding.btnBriefingTime.text = "브리핑 시각: ${Formats.time(java.time.LocalTime.of(bm / 60, bm % 60))}"
        binding.packagesText.text = prefs.monitoredPackages.joinToString("\n") { "• ${AppLabels.label(this, it)}  ($it)" }
            .ifEmpty { "선택된 앱 없음" }
    }

    private fun status(ok: Boolean, label: String) = if (ok) "✅ $label" else "❌ $label 필요"

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "설정 화면을 열 수 없어요.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun connectNaver() {
        val id = binding.inputNaverId.text?.toString()?.trim().orEmpty()
        val pw = binding.inputNaverPw.text?.toString()?.trim().orEmpty()
        if (id.isEmpty() || pw.isEmpty()) {
            Toast.makeText(this, "네이버 아이디와 애플리케이션 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.naverId = id
        prefs.naverAppPassword = pw

        binding.btnConnect.isEnabled = false
        binding.calendarStatus.text = "연결 중…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { CalDavClient(id, pw).discoverCalendars() }
            }
            binding.btnConnect.isEnabled = true
            result.onSuccess { calendars ->
                if (calendars.isEmpty()) {
                    binding.calendarStatus.text = "⚠️ 캘린더를 찾지 못했어요."
                    return@onSuccess
                }
                val names = calendars.map { it.displayName }.toTypedArray()
                val checked = calendars.indexOfFirst { it.url == prefs.calendarUrl }.coerceAtLeast(0)
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("저장할 캘린더 선택")
                    .setSingleChoiceItems(names, checked) { dialog, which ->
                        prefs.calendarUrl = calendars[which].url
                        prefs.calendarName = calendars[which].displayName
                        dialog.dismiss()
                        refreshStatus()
                    }
                    .setOnDismissListener { refreshStatus() }
                    .show()
            }.onFailure { e ->
                binding.calendarStatus.text = "❌ ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun setupLeadSpinner() {
        val options = Formats.LEAD_OPTIONS
        binding.spinnerLead.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options.map { Formats.leadText(it) },
        )
        binding.spinnerLead.setSelection(options.indexOf(prefs.alarmLeadMinutes).coerceAtLeast(0))
        binding.spinnerLead.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.alarmLeadMinutes = options[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun choosePackages() {
        val candidates = (Prefs.DEFAULT_PACKAGES.keys + prefs.monitoredPackages + prefs.seenPackages).distinct()
        val labels = candidates.map { "${AppLabels.label(this, it)}\n$it" }.toTypedArray()
        val selected = candidates.map { it in prefs.monitoredPackages }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("일정을 감지할 앱")
            .setMultiChoiceItems(labels, selected) { _, which, isChecked -> selected[which] = isChecked }
            .setPositiveButton("저장") { _, _ ->
                prefs.monitoredPackages = candidates.filterIndexed { i, _ -> selected[i] }.toSet()
                refreshStatus()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun runTest() {
        val text = binding.inputTest.text?.toString().orEmpty()
        val parser = ScheduleParser()
        val detection = com.sooparo.schedulecatcher.core.MessageAnalyzer.Detection(
            parser.parse(text),
            com.sooparo.schedulecatcher.core.TodoDetector(parser).detect(text),
            false,
        )
        val data = ScheduleData.from(detection, "테스트", null)
        if (data == null) {
            lastTest = null
            binding.testResult.text = "일정으로 인식되지 않았어요."
            binding.btnTestOpen.isEnabled = false
            binding.btnTestNotify.isEnabled = false
            return
        }
        lastTest = data
        binding.testResult.text = buildString {
            append(if (data.isTask) "종류: 할 일\n" else "종류: 일정\n")
            append("제목: ").append(data.title).append('\n')
            append("일시: ").append(Formats.whenText(data))
            data.location?.let { append("\n장소: ").append(it) }
        }
        binding.btnTestOpen.isEnabled = true
        binding.btnTestNotify.isEnabled = true
    }
}
