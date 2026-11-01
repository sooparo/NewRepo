package com.sooparo.schedulecatcher.core

/**
 * 일정 분석 전에 민감정보를 가린다. 가린 결과만 이후 단계(제목, 알림, 캘린더)로 넘어간다.
 *
 * - 등록번호: 연속된 8자리 숫자 → ********
 * - 주민번호: 123456-1234567 → ******-*******
 * - 전화번호: 010-1234-5678, 02-123-4567 → ***-****-****
 * - 호칭 붙은 이름: "홍길동 환자", "김OO님", "이영희씨" → "OOO 환자", "OOO님", "OOO씨"
 *   (호칭 없이 쓴 이름은 이름인지 알 수 없어 가리지 못한다)
 */
class PrivacyFilter(
    private val maskRegistrationNumbers: Boolean = true,
    private val maskPersonalNumbers: Boolean = true,
    private val maskHonorificNames: Boolean = true,
) {

    fun redact(text: String): String {
        var s = text
        if (maskPersonalNumbers) {
            s = RRN.replace(s, "******-*******")
            s = PHONE.replace(s, "***-****-****")
        }
        if (maskRegistrationNumbers) {
            s = REG_NO.replace(s, "********")
        }
        if (maskHonorificNames) {
            s = NAME_WITH_TITLE.replace(s) { m ->
                val name = m.groupValues[1]
                if (name in NOT_NAMES || NOT_NAME_SUFFIX.any { name.endsWith(it) }) {
                    m.value
                } else {
                    MASK_NAME + m.groupValues[2] + m.groupValues[3]
                }
            }
        }
        return s
    }

    companion object {
        const val MASK_NAME = "OOO"

        private val RRN = Regex("(?<!\\d)\\d{6}\\s?-\\s?[1-8]\\d{6}(?!\\d)")
        private val PHONE = Regex("(?<![\\d-])(?:01[016789]|0\\d{1,2})[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?![\\d-])")
        private val REG_NO = Regex("(?<![\\d-])\\d{8}(?![\\d-])")

        private val NAME_WITH_TITLE = Regex(
            "(?<![가-힣])([가-힣]{2,4}|[가-힣][Oo○0*xX]{1,2})(\\s?)" +
                "(환자분|환자|보호자|선생님|선생|교수님|과장님|님|씨)(?![가-힣])",
        )

        /** "님/씨/환자" 앞에 와도 사람 이름이 아닌 단어 */
        private val NOT_NAMES = setOf(
            "선생", "교수", "과장", "실장", "팀장", "부장", "원장", "주임", "기사", "간호사", "수간호사",
            "선배", "후배", "사장", "대표", "이사", "차장", "대리", "사원", "주무관", "전공의", "인턴",
            "레지던트", "방사선사", "물리사", "의사", "약사", "고객", "회원", "여러분", "담당", "담당자",
            "외래", "입원", "신규", "대기", "해당", "다음", "오늘", "내일", "모든", "응급", "초진", "재진",
            "치료", "검사", "수술", "방문", "예약", "기존", "소아", "성인", "남자", "여자", "어르신", "부모",
            "보호자", "환자", "아버님", "어머님", "아버지", "어머니", "할머니", "할아버지", "형님", "누님",
        )
        private val NOT_NAME_SUFFIX = listOf("담당자", "선생", "교수", "간호사", "기사", "실장", "팀장", "과장", "부장")
    }
}
