// 날짜 유틸 (주 시작 = 월요일, 로컬 시간 기준)

const pad = (n) => String(n).padStart(2, '0');

export function toKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function fromKey(key) {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(key, n) {
  const d = fromKey(key);
  d.setDate(d.getDate() + n);
  return toKey(d);
}

/** 해당 날짜가 속한 주의 월요일 */
export function mondayOf(date = new Date()) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const dow = (d.getDay() + 6) % 7; // 월=0 … 일=6
  d.setDate(d.getDate() - dow);
  return toKey(d);
}

/** 주의 대표 월 (목요일 기준) */
export function monthOfWeek(weekStart) {
  return fromKey(addDays(weekStart, 3)).getMonth() + 1;
}

export const DAY_NAMES = ['월', '화', '수', '목', '금', '토', '일'];

export function shortDate(key) {
  const d = fromKey(key);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function weekLabel(weekStart) {
  const s = fromKey(weekStart);
  const e = fromKey(addDays(weekStart, 6));
  const end = s.getMonth() === e.getMonth() ? `${e.getDate()}일` : `${e.getMonth() + 1}월 ${e.getDate()}일`;
  return `${s.getMonth() + 1}월 ${s.getDate()}일 ~ ${end}`;
}
