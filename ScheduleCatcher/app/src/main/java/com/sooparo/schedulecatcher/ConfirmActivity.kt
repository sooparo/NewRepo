package com.sooparo.schedulecatcher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sooparo.schedulecatcher.core.ScheduleParser
import com.sooparo.schedulecatcher.databinding.ActivityConfirmBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 감지된 일정을 확인/수정하고 저장 방식을 고르는 화면.
 * [네이버 캘린더] [알람] [캘린더 + 알람] [저장 안 함]
 */
class ConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmBinding
    private val prefs by lazy { Prefs(this) }

    private lateinit var base: ScheduleData
    private lateinit var date: LocalDate
    private lateinit var startTime: LocalTime
    private var endTime: LocalTime? = null
    private var leadMinutes = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDate.setOnClickListener { pickDate() }
        binding.btnStart.setOnClickListener { pickTime(startTime) { startTime = it; refresh() } }
        binding.btnEnd.setOnClickListener { pickTime(endTime ?: startTime.plusHours(1)) { endTime = it; refresh() } }
        binding.btnEnd.setOnLongClickListener { endTime = null; refresh(); true }
        binding.checkAllDay.setOnCheckedChangeListener { _, _ -> refresh() }

        binding.btnCalendar.setOnClickListener { save(SaveTarget.CALENDAR) }
        binding.btnAlarm.setOnClickListener { save(SaveTarget.ALARM) }
        binding.btnBoth.setOnClickListener { save(SaveTarget.BOTH) }
        binding.btnIgnore.setOnClickListener { finish() }

        setupLeadSpinner()
        if (!load(intent)) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(intent: Intent?): Boolean {
        base = ScheduleData.fromIntent(intent) ?: fromSharedText(intent) ?: return false
        Notifications.cancel(this, base.notificationId)

        date = base.start.toLocalDate()
        startTime = if (base.allDay) LocalTime.of(9, 0) else base.start.toLocalTime()
        endTime = base.end?.toLocalTime()

        binding.inputTitle.setText(base.title)
        binding.inputLocation.setText(base.location.orEmpty())
        binding.checkAllDay.isChecked = base.allDay
        binding.sourceLabel.text = base.sourceLabel ?: "원문"
        binding.sourceText.text = base.sourceText.ifBlank { "(원문 없음)" }
        binding.resultText.text = ""
        binding.calendarHint.text = if (prefs.hasNaverCalendar) {
            "네이버 캘린더: ${prefs.calendarName ?: "선택됨"}"
        } else {
            "네이버 계정 미설정 → 캘린더 저장 시 기기 캘린더 앱이 열립니다. (앱 설정에서 연결)"
        }
        refresh()
        return true
    }

    /** 다른 앱에서 '공유'로 받은 텍스트 */
    private fun fromSharedText(intent: Intent?): ScheduleData? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) return null

        ScheduleParser().parse(text)?.let { return ScheduleData.from(it, "공유", null) }

        Toast.makeText(this, "날짜/시간을 찾지 못했어요. 직접 입력해 주세요.", Toast.LENGTH_LONG).show()
        return ScheduleData(
            id = UUID.randomUUID().toString(),
            title = text.lineSequence().first().take(30),
            start = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS),
            end = null,
            allDay = false,
            location = null,
            sourceText = text,
            sourceApp = "공유",
            sender = null,
        )
    }

    private fun setupLeadSpinner() {
        leadMinutes = prefs.alarmLeadMinutes
        val options = Formats.LEAD_OPTIONS.let { if (leadMinutes in it) it else (it + leadMinutes).sorted() }
        binding.spinnerLead.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            options.map { Formats.leadText(it) },
        )
        binding.spinnerLead.setSelection(options.indexOf(leadMinutes))
        binding.spinnerLead.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                leadMinutes = options[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun refresh() {
        val allDay = binding.checkAllDay.isChecked
        binding.btnDate.text = Formats.date(date)
        binding.btnStart.text = Formats.time(startTime)
        binding.btnEnd.text = endTime?.let { "~ ${Formats.time(it)}" } ?: "종료 시간"
        binding.btnStart.visibility = if (allDay) View.GONE else View.VISIBLE
        binding.btnEnd.visibility = if (allDay) View.GONE else View.VISIBLE
        binding.allDayAlarmHint.visibility = if (allDay) View.VISIBLE else View.GONE
    }

    private fun pickDate() {
        DatePickerDialog(
            this,
            { _, y, m, d -> date = LocalDate.of(y, m + 1, d); refresh() },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    private fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(this, { _, h, min -> onPicked(LocalTime.of(h, min)) }, initial.hour, initial.minute, false).show()
    }

    private fun current(): ScheduleData {
        val allDay = binding.checkAllDay.isChecked
        val start = if (allDay) date.atStartOfDay() else date.atTime(startTime)
        val end = if (allDay) null else endTime?.let { date.atTime(it) }?.takeIf { it.isAfter(start) }
        return base.copy(
            title = binding.inputTitle.text?.toString()?.trim().orEmpty().ifEmpty { "새 일정" },
            start = start,
            end = end,
            allDay = allDay,
            location = binding.inputLocation.text?.toString()?.trim()?.ifEmpty { null },
        )
    }

    private fun save(target: SaveTarget) {
        setButtonsEnabled(false)
        binding.resultText.text = "저장 중…"
        lifecycleScope.launch {
            val result = ScheduleSaver(this@ConfirmActivity).save(current(), target, leadMinutes)
            binding.resultText.text = result.summary
            setButtonsEnabled(true)
            if (result.allOk) {
                Toast.makeText(applicationContext, result.summary, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        listOf(binding.btnCalendar, binding.btnAlarm, binding.btnBoth).forEach { it.isEnabled = enabled }
    }
}
