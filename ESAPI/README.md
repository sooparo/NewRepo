# ESAPI Plan Check Scripts (Eclipse 18.1)

## Pancreas HFRT SIB — PTV 4.0 / 2.5 Gy × 15 Fx

파일: [`Pancreas_HFRT_SIB_4.0_2.5Gy_15Fx_Check.cs`](Pancreas_HFRT_SIB_4.0_2.5Gy_15Fx_Check.cs)
(Windows 파일명에 `/` 를 쓸 수 없어 `4.0_2.5` 로 표기 — 실행 창 제목은 `4.0/2.5`)

- 형식: **Single-file plug-in**, 읽기 전용 (데이터 수정 없음, script approval 불필요)
- 실행: 플랜 또는 Plan Sum 을 연 상태에서 **Tools ▸ Scripts** ▸ 파일 선택 ▸ **Run**

### 평가 기준

| 구분 | 구조물 | 항목 | 기준 | 비고 |
|---|---|---|---|---|
| Rx | Plan | 분할/처방 | 15 Fx, 4.0 또는 2.5 Gy/Fx | Plan Sum 은 분할 합계 15 Fx |
| [1] Target | PTV_2.5 | V37.5Gy | > 98 % | |
| [1] Target | GTV | V60Gy | > 95 % | 90–95 % = **허용(Minor)**, ≤ 90 % = FAIL |
| [2] OAR | iStomach / iDuodenum (각각) | Dmax | < 41 Gy | |
| [2] OAR | Liver | Dmean | < 24 Gy | |
| [2] OAR | Liver | 24 Gy 미만 부피 | ≥ 700 cc | = Liver 부피 − V24Gy(cc) |
| [2] OAR | Spinal Cord | Dmax | < 30 Gy | |
| [2] OAR | Kidney_L, Kidney_R (각각) | V15Gy / V20Gy | < 45 % / < 33 % | |
| [2] OAR | Kidney L+R (합산) | V15Gy / V20Gy | < 45 % / < 33 % | (좌 Vx cc + 우 Vx cc) / (좌+우 부피) |
| 참고 | PTV_4.0 | V60Gy | — | 판정 없음 |
| 참고 | Plan (3D) | Dmax | — | 60 Gy 대비 % 표시 |

판정 규칙
- 값은 표시 자릿수(Gy·% 소수 2자리, cc 소수 1자리)로 반올림한 뒤 판정 → 화면 값과 판정이 항상 일치
  (예: Cord Dmax 29.996 Gy → `30.00 Gy` → `< 30 Gy` 불만족 → FAIL)
- Dmax = 점 최대선량(DVH Max). 비고란에 D0.03cc 함께 표시
- Vx / Dx 는 ESAPI `GetVolumeAtDose` / `GetDoseAtVolume` (Eclipse DVH 와 같은 계산) 사용
- 시스템 선량 단위가 Gy / cGy 어느 쪽이어도 자동 처리

### 구조물 매핑

이름으로 자동 선택하고(대소문자·공백·`_`·`-`·괄호 무시, `.` 은 구분), 창 위쪽 콤보박스에서 바꾸면 즉시 재평가합니다.

| 역할 | 자동 매칭 후보 (앞쪽 우선) |
|---|---|
| GTV | GTV, iGTV, GTV_60, GTV_60Gy, GTV_6000, GTVp, GTV_Pancreas |
| PTV_4.0 | PTV_4.0, PTV_4.0Gy, PTV_60, PTV_60Gy, PTV_6000, PTV_High, PTV_Boost, PTV_SIB |
| PTV_2.5 | PTV_2.5, PTV_2.5Gy, PTV_37.5, PTV_37.5Gy, PTV_3750, PTV_Low |
| iStomach | iStomach, Stomach_i, Stomach ※ |
| iDuodenum | iDuodenum, Duodenum_i, Duodenum ※ |
| Liver | Liver |
| Spinal Cord | SpinalCord, Cord, SC, Cord_Spinal |
| Kidney_L | Kidney_L, Kidney_Lt, Kidney_Left, L_Kidney, Lt_Kidney, Left_Kidney |
| Kidney_R | Kidney_R, Kidney_Rt, Kidney_Right, R_Kidney, Rt_Kidney, Right_Kidney |

※ iStomach / iDuodenum 이 없거나 비어 있어 Stomach / Duodenum 으로 평가하면 비고에 경고가 표시됩니다.

### 설정 변경

스크립트 안 `Protocol` 클래스만 수정하면 됩니다.
- `Constraints()` : 기준 추가/삭제/수정
- `Roles()` : 구조물 후보 이름
- `DmaxAsD003cc` : `true` 로 바꾸면 Dmax 를 D0.03cc 로 판정
- `KidneyEach` / `KidneyCombined` : 신장 좌/우 각각, 양측 합산 표시 여부

### 주의

- **C# 5 문법만 사용** — Eclipse single-file plug-in 컴파일러 호환 (`$"..."`, `?.`, `=>` 멤버 등 사용 금지)
- **UTF-8 (BOM 포함)** 으로 저장해야 한글이 깨지지 않습니다 (메모장 등으로 다시 저장할 때 주의)
- 결과 복사 버튼: 탭 구분 텍스트 → Excel / Word 에 표로 붙여넣기
- 임상 사용 전, 몇 개 플랜에서 Eclipse DVH / Clinical Goals 값과 대조 확인 권장
