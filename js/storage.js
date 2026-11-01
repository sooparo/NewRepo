// 데이터 저장 (휴대폰 브라우저 localStorage)
import { DEFAULT_SETTINGS } from './planner.js';

const KEY = 'weekly-meal.v1';

export function emptyDB() {
  return {
    version: 1,
    settings: { ...DEFAULT_SETTINGS },
    prefs: {
      favorites: [], // 즐겨찾기 레시피 id
      disliked: [], // 추천 제외 레시피 id
      links: {}, // 재료명 → 저장한 쿠팡 상품 링크
      staples: [], // 매주 고정 장보기 항목
    },
    plans: {}, // weekStart → plan
    shopping: {}, // weekStart → { removed, bought, custom }
  };
}

function merge(db) {
  const base = emptyDB();
  return {
    ...base,
    ...db,
    settings: { ...base.settings, ...(db?.settings ?? {}) },
    prefs: { ...base.prefs, ...(db?.prefs ?? {}) },
    plans: db?.plans ?? {},
    shopping: db?.shopping ?? {},
  };
}

export function loadDB() {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? merge(JSON.parse(raw)) : emptyDB();
  } catch {
    return emptyDB();
  }
}

export function saveDB(db) {
  try {
    localStorage.setItem(KEY, JSON.stringify(db));
    return true;
  } catch {
    return false;
  }
}

/** 백업 파일 → DB (형식 확인 후 병합) */
export function parseBackup(text) {
  const data = JSON.parse(text);
  if (!data || typeof data !== 'object' || data.version !== 1) throw new Error('올바른 백업 파일이 아니에요.');
  return merge(data);
}

/** 오래된 주 데이터 정리 (12주 넘은 것) */
export function pruneOld(db, currentWeek) {
  const keys = Object.keys(db.plans).sort();
  const limit = keys.filter((k) => k < currentWeek).slice(0, -12);
  for (const k of limit) {
    delete db.plans[k];
    delete db.shopping[k];
  }
  return db;
}
