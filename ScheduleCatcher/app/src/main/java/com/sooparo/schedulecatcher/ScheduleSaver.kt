package com.sooparo.schedulecatcher

import android.app.Activity
import android.content.ActivityNotFoundException
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

/** 사용자가 고른 방식(캘린더/알람/둘 다)으로 일정을 저장한다. */
class ScheduleSaver(private val activity: Activity) {

    sealed class Outcome(val message: String) {
        class Ok(message: String) : Outcome(message)
        class Fail(message: String) : Outcome(message)
    }

    data class Result(val calendar: Outcome?, val alarm: Outcome?) {
        val allOk: Boolean get() = calendar !is Outcome.Fail && alarm !is Outcome.Fail
        val summary: String get() = listOfNotNull(calendar?.message, alarm?.message).joinToString("\n")
    }

    private val prefs = Prefs(activity)
    private val zone: ZoneId = ZoneId.systemDefault()

    suspend fun save(data: ScheduleData, target: SaveTarget, leadMinutes: Int = prefs.alarmLeadMinutes): Result {
        val calendar = if (target.calendar) saveCalendar(data, leadMinutes) else null
        val alarm = if (target.alarm) saveAlarm(data, leadMinutes) else null
        return Result(calendar, alarm)
    }

    // ---------------------------------------------------------------------------------------------
    // 네이버 캘린더 (CalDAV)
    // ---------------------------------------------------------------------------------------------

    private suspend fun saveCalendar(data: ScheduleData, leadMinutes: Int): Outcome {
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
        )

        return withContext(Dispatchers.IO) {
            try {
                CalDavClient(prefs.naverId, prefs.naverAppPassword)
                    .createEvent(prefs.calendarUrl!!, data.id, ICalendarBuilder.build(event, zone))
                Outcome.Ok("📅 네이버 캘린더(${prefs.calendarName ?: "기본"})에 저장했어요.")
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
            .apply { data.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) } }
        return try {
            activity.startActivity(intent)
            Outcome.Ok("📅 네이버 계정이 설정되지 않아 캘린더 앱을 열었어요. 저장을 마무리해 주세요.")
        } catch (e: ActivityNotFoundException) {
            Outcome.Fail("📅 네이버 캘린더 계정을 먼저 설정하세요 (앱 설정 화면).")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 알람
    // ---------------------------------------------------------------------------------------------

    private fun saveAlarm(data: ScheduleData, leadMinutes: Int): Outcome {
        val now = LocalDateTime.now()
        val eventTime = if (data.allDay) data.start.toLocalDate().atTime(ALL_DAY_ALARM_HOUR, 0) else data.start
        var alarmAt = eventTime.minusMinutes(leadMinutes.toLong())
        var note = ""
        if (!alarmAt.isAfter(now)) {
            if (!eventTime.isAfter(now)) return Outcome.Fail("⏰ 이미 지난 시각이라 알람을 설정할 수 없어요.")
            alarmAt = eventTime
            note = " (설정한 알림 시점이 지나서 일정 시각으로 맞췄어요)"
        }

        val info = buildString {
            append(Formats.whenText(data))
            data.location?.let { append(" · ").append(it) }
        }

        if (prefs.useClockApp) {
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
        )
        if (!AlarmScheduler.schedule(activity, entry)) {
            AlarmScheduler.exactAlarmSettingsIntent(activity)?.let { runCatching { activity.startActivity(it) } }
            return Outcome.Fail("⏰ '알람 및 리마인더' 권한을 허용한 뒤 다시 시도하세요.")
        }
        return Outcome.Ok("⏰ ${Formats.dateTime(alarmAt)} 알람을 예약했어요.$note")
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

    companion object {
        /** 종일 일정의 알람 기준 시각 */
        const val ALL_DAY_ALARM_HOUR = 8
    }
}
