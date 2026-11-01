package com.sooparo.schedulecatcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** 앱 설정. 네이버 애플리케이션 비밀번호는 암호화 저장소에 보관한다. */
class Prefs(context: Context) {

    private val app = context.applicationContext
    private val sp: SharedPreferences = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                "secure_settings",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                app,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // 키스토어 오류 시 비밀번호를 평문으로 남기지 않도록 메모리 전용 저장소 사용(앱 재시작 시 재입력 필요)
            InMemoryPrefs
        }
    }

    var naverId: String
        get() = sp.getString(KEY_NAVER_ID, "").orEmpty()
        set(value) = sp.edit { putString(KEY_NAVER_ID, value.trim()) }

    var naverAppPassword: String
        get() = secure.getString(KEY_NAVER_PW, "").orEmpty()
        set(value) = secure.edit { putString(KEY_NAVER_PW, value.trim()) }

    var calendarUrl: String?
        get() = sp.getString(KEY_CAL_URL, null)
        set(value) = sp.edit { putString(KEY_CAL_URL, value) }

    var calendarName: String?
        get() = sp.getString(KEY_CAL_NAME, null)
        set(value) = sp.edit { putString(KEY_CAL_NAME, value) }

    val hasNaverCalendar: Boolean
        get() = naverId.isNotEmpty() && naverAppPassword.isNotEmpty() && !calendarUrl.isNullOrEmpty()

    /** 알람: 일정 시작 몇 분 전에 울릴지 */
    var alarmLeadMinutes: Int
        get() = sp.getInt(KEY_LEAD, 30)
        set(value) = sp.edit { putInt(KEY_LEAD, value) }

    /** true: 기본 시계 앱에 등록(24시간 이내 일정만), false: 이 앱의 알람(날짜 지정 가능) */
    var useClockApp: Boolean
        get() = sp.getBoolean(KEY_CLOCK_APP, false)
        set(value) = sp.edit { putBoolean(KEY_CLOCK_APP, value) }

    /** 네이버 캘린더 일정에도 같은 시점의 캘린더 알림을 넣을지 */
    var calendarReminder: Boolean
        get() = sp.getBoolean(KEY_CAL_REMINDER, true)
        set(value) = sp.edit { putBoolean(KEY_CAL_REMINDER, value) }

    /** 메시지 원문을 캘린더 메모에 포함할지 (환자 정보 등 민감정보 유출 방지를 위해 기본 꺼짐) */
    var includeOriginalText: Boolean
        get() = sp.getBoolean(KEY_INCLUDE_TEXT, false)
        set(value) = sp.edit { putBoolean(KEY_INCLUDE_TEXT, value) }

    /** 확인 화면에서 마지막으로 고른 저장 대상 (기본: 캘린더) */
    var lastTargets: SaveTargets
        get() = SaveTargets.decode(sp.getString(KEY_LAST_TARGETS, null)) ?: SaveTargets.CALENDAR
        set(value) = sp.edit { putString(KEY_LAST_TARGETS, value.encode()) }

    /** 감지 알림의 세 번째 버튼: false = [할 일], true = [둘 다(캘린더+알람)] */
    var thirdButtonBoth: Boolean
        get() = sp.getBoolean(KEY_THIRD_BOTH, false)
        set(value) = sp.edit { putBoolean(KEY_THIRD_BOTH, value) }

    /** 아침 브리핑 */
    var briefingEnabled: Boolean
        get() = sp.getBoolean(KEY_BRIEFING, true)
        set(value) = sp.edit { putBoolean(KEY_BRIEFING, value) }

    /** 아침 브리핑 시각 (분 단위, 기본 07:00) */
    var briefingMinuteOfDay: Int
        get() = sp.getInt(KEY_BRIEFING_TIME, 7 * 60)
        set(value) = sp.edit { putInt(KEY_BRIEFING_TIME, value) }

    // ---- 개인정보 가리기 (기본 모두 켜짐) ----
    var maskRegistrationNumbers: Boolean
        get() = sp.getBoolean(KEY_MASK_REG, true)
        set(value) = sp.edit { putBoolean(KEY_MASK_REG, value) }

    var maskPersonalNumbers: Boolean
        get() = sp.getBoolean(KEY_MASK_PERSONAL, true)
        set(value) = sp.edit { putBoolean(KEY_MASK_PERSONAL, value) }

    var maskHonorificNames: Boolean
        get() = sp.getBoolean(KEY_MASK_NAMES, true)
        set(value) = sp.edit { putBoolean(KEY_MASK_NAMES, value) }

    /** 보낸 사람 이름을 저장/표시하지 않음 (앱 이름만) */
    var hideSender: Boolean
        get() = sp.getBoolean(KEY_HIDE_SENDER, true)
        set(value) = sp.edit { putBoolean(KEY_HIDE_SENDER, value) }

    fun privacyFilter() = com.sooparo.schedulecatcher.core.PrivacyFilter(
        maskRegistrationNumbers = maskRegistrationNumbers,
        maskPersonalNumbers = maskPersonalNumbers,
        maskHonorificNames = maskHonorificNames,
    )

    var monitoredPackages: Set<String>
        get() = sp.getStringSet(KEY_PACKAGES, null)?.toSet() ?: DEFAULT_PACKAGES.keys
        set(value) {
            sp.edit { putStringSet(KEY_PACKAGES, value) }
            ScreenReaderService.applyPackages(app)
        }

    /** 최근 메시지 알림을 보낸 앱(감지 대상 선택 목록에 표시) */
    val seenPackages: Set<String>
        get() = sp.getStringSet(KEY_SEEN, emptySet())?.toSet().orEmpty()

    fun recordSeenPackage(pkg: String) {
        val current = seenPackages
        if (pkg in current) return
        sp.edit { putStringSet(KEY_SEEN, (current + pkg).toList().takeLast(30).toSet()) }
    }

    companion object {
        /** 기본 감지 대상 (패키지명 → 표시 이름) */
        val DEFAULT_PACKAGES = linkedMapOf(
            "com.kakao.talk" to "카카오톡",
            "com.samsung.android.messaging" to "삼성 메시지",
            "com.google.android.apps.messaging" to "Google 메시지",
            "com.gworks.oneapp.works" to "네이버 웍스",
        )

        private const val KEY_NAVER_ID = "naver_id"
        private const val KEY_NAVER_PW = "naver_app_pw"
        private const val KEY_CAL_URL = "calendar_url"
        private const val KEY_CAL_NAME = "calendar_name"
        private const val KEY_LEAD = "alarm_lead_minutes"
        private const val KEY_CLOCK_APP = "use_clock_app"
        private const val KEY_CAL_REMINDER = "calendar_reminder"
        private const val KEY_INCLUDE_TEXT = "include_original_text"
        private const val KEY_PACKAGES = "monitored_packages"
        private const val KEY_SEEN = "seen_packages"
        private const val KEY_LAST_TARGETS = "last_targets"
        private const val KEY_THIRD_BOTH = "third_button_both"
        private const val KEY_BRIEFING = "briefing_enabled"
        private const val KEY_BRIEFING_TIME = "briefing_time"
        private const val KEY_MASK_REG = "mask_registration_numbers"
        private const val KEY_MASK_PERSONAL = "mask_personal_numbers"
        private const val KEY_MASK_NAMES = "mask_honorific_names"
        private const val KEY_HIDE_SENDER = "hide_sender"
    }
}

/** 암호화 저장소를 쓸 수 없을 때의 대체 저장소(프로세스 메모리에만 존재). */
private object InMemoryPrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = map[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = Editor()

    private class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearAll = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.toMutableSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            synchronized(map) {
                if (clearAll) map.clear()
                for ((k, v) in pending) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }
}
