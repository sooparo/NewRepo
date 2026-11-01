// 장보기 목록: 주간 식단 → 재료 합산 + 직접 넣기/빼기
import { RECIPES, CATEGORIES } from './data/recipes.js';
import { fruitById } from './data/fruits.js';
import { getRecipe } from './planner.js';
import { DAY_NAMES, shortDate, addDays } from './date.js';

export const itemKey = (name, unit) => `${name}|${unit ?? ''}`;

/** 레시피에 나오는 재료 이름 → 카테고리 (직접 넣은 항목 자동 분류용) */
const knownCategory = new Map();
const knownUnit = new Map(); // 재료 이름 → 레시피에서 쓰는 단위
for (const r of RECIPES) {
  for (const [name, qty, unit, cat] of r.ingredients) {
    knownCategory.set(name, cat);
    if (qty && !knownUnit.has(name)) knownUnit.set(name, unit);
  }
}
for (const f of fruitById.values()) knownCategory.set(f.name, 'fruit');

export function guessCategory(name) {
  const n = name.trim();
  if (knownCategory.has(n)) return knownCategory.get(n);
  for (const [k, cat] of knownCategory) if (cat !== 'pantry' && (n.includes(k) || k.includes(n))) return cat;
  return 'etc';
}

// ───────────────────────── 메뉴별 재료 (occurrence) ─────────────────────────
// 장보기의 기본 단위는 "어느 요일, 어느 메뉴에 쓰이는 재료" 하나하나다.
//   okey  : 이 재료 하나의 고유 키  (예: '0:main:양파|개' = 월요일 저녁 메인의 양파)
//           직접 넣은 것은 'c:<id>', 매주 고정은 'x:<id>'
//   key   : 품목 키 (이름|단위) — 종류별 화면에서는 같은 key끼리 합산
// 구매 완료/빼기 상태는 okey로 저장되므로, 요일별 화면과 종류별 화면이 서로 연동된다.

const isOccKey = (k) => /^(\d+|c|x):/.test(k);

/** 식단 → 요일·메뉴별 재료 목록 (외식/건너뛴 끼니 제외) */
export function occurrencesFromPlan(plan) {
  const out = [];
  if (!plan) return out;
  plan.days.forEach((d, day) => {
    const push = (meal, slot, recipe, [name, qty, unit, cat], use) =>
      out.push({ okey: `${day}:${slot}:${itemKey(name, unit)}`, key: itemKey(name, unit), day, meal, slot, recipeId: recipe?.id ?? null, name, qty, unit, cat, use });
    if (!d.skip?.breakfast) {
      const r = getRecipe(d.breakfast);
      if (r) for (const ing of r.ingredients) push('breakfast', 'breakfast', r, ing, `${DAY_NAMES[day]} ${r.name}`);
      const f = fruitById.get(d.fruit);
      if (f) push('breakfast', 'fruit', null, [f.name, f.perDay[0], f.perDay[1], 'fruit'], `${DAY_NAMES[day]} 아침`);
    }
    if (!d.skip?.dinner) {
      for (const slot of ['main', 'soup']) {
        const r = getRecipe(d.dinner?.[slot]);
        if (r) for (const ing of r.ingredients) push('dinner', slot, r, ing, `${DAY_NAMES[day]} ${r.name}`);
      }
    }
  });
  return out;
}

/** 직접 넣은 것 + 매주 고정 */
export function extraOccurrences(state = {}, staples = []) {
  const mk = (okey, x, use) => ({
    okey, key: itemKey(x.name, x.unit ?? ''), day: null, meal: 'extra', slot: 'extra', recipeId: null,
    name: x.name, qty: Number(x.qty) || 0, unit: x.unit ?? '', cat: x.cat ?? guessCategory(x.name), use,
  });
  return [
    ...staples.map((x) => mk(`x:${x.id}`, x, '매주 고정')),
    ...(state.custom ?? []).map((x) => mk(`c:${x.id}`, x, '직접 추가')),
  ];
}

export function allOccurrences(plan, state = {}, staples = []) {
  return [...occurrencesFromPlan(plan), ...extraOccurrences(state, staples)];
}

/**
 * 예전 버전(품목 키 'name|unit'로 저장)의 구매/빼기 상태를 메뉴별 키로 바꾼다.
 * 이미 새 형식이면 그대로 둔다. state를 직접 수정하고 변경 여부를 돌려준다.
 */
export function migrateShopState(occs, state) {
  let changed = false;
  for (const field of ['bought', 'removed']) {
    const arr = state[field] ?? [];
    if (arr.every(isOccKey)) continue;
    const legacy = new Set(arr.filter((k) => !isOccKey(k)));
    const next = new Set(arr.filter(isOccKey));
    for (const o of occs) if (legacy.has(o.key)) next.add(o.okey);
    state[field] = [...next];
    changed = true;
  }
  return changed;
}

/** 품목 하나(key)에 속한 메뉴별 키 전부 */
export const occKeysOf = (occs, key) => occs.filter((o) => o.key === key).map((o) => o.okey);

function merge(map, o, bought) {
  const it = map.get(o.key) ?? { key: o.key, name: o.name, qty: 0, unit: o.unit, cat: o.cat, uses: [], occ: [], boughtCount: 0 };
  it.qty += o.qty;
  if (o.use && !it.uses.includes(o.use)) it.uses.push(o.use);
  it.occ.push(o.okey);
  if (bought.has(o.okey)) it.boughtCount++;
  map.set(o.key, it);
}

/** 식단에서 필요한 재료를 품목별로 합산 (Map key → item) */
export function ingredientsFromPlan(plan) {
  const map = new Map();
  for (const o of occurrencesFromPlan(plan)) merge(map, o, new Set());
  return map;
}

function sortItems(list) {
  const catOrder = CATEGORIES.map((c) => c.key);
  return list.sort((a, b) => catOrder.indexOf(a.cat) - catOrder.indexOf(b.cat) || a.name.localeCompare(b.name, 'ko'));
}

/**
 * 종류별(합산) 장보기 목록
 * @param plan    주간 식단
 * @param state   { removed: okey[], bought: okey[], custom: Item[] }  (해당 주)
 * @param staples 매주 고정으로 넣는 항목 Item[]
 * @returns { items, removed } — 품목이 일부 메뉴에서만 빠졌다면 남은 양은 items, 뺀 양은 removed에
 */
export function buildShoppingList(plan, state = {}, staples = []) {
  const bought = new Set(state.bought ?? []);
  const removed = new Set(state.removed ?? []);
  const active = new Map();
  const gone = new Map();
  for (const o of allOccurrences(plan, state, staples)) merge(removed.has(o.okey) ? gone : active, o, bought);
  const finish = (map) => sortItems([...map.values()].map((it) => ({ ...it, bought: it.occ.length > 0 && it.boughtCount === it.occ.length })));
  return { items: finish(active), removed: finish(gone) };
}

/**
 * 요일별 장보기 목록: 요일 → 아침/저녁 → 메뉴 → 재료
 * 각 재료에는 같은 재료를 쓰는 다른 요일(otherDays)을 붙여서 한 번에 살 수 있게 안내한다.
 */
export function buildDayList(plan, state = {}, staples = []) {
  const bought = new Set(state.bought ?? []);
  const removedSet = new Set(state.removed ?? []);
  const occs = occurrencesFromPlan(plan);

  const daysByKey = new Map();
  for (const o of occs) {
    if (o.cat === 'pantry' || removedSet.has(o.okey)) continue;
    if (!daysByKey.has(o.key)) daysByKey.set(o.key, new Set());
    daysByKey.get(o.key).add(o.day);
  }

  const days = (plan?.days ?? []).map((d, day) => ({
    day,
    meals: [
      { meal: 'breakfast', skipped: !!d.skip?.breakfast, recipes: [getRecipe(d.breakfast)].filter(Boolean), fruit: fruitById.get(d.fruit) ?? null, items: [], pantry: [] },
      { meal: 'dinner', skipped: !!d.skip?.dinner, recipes: [getRecipe(d.dinner?.main), getRecipe(d.dinner?.soup)].filter(Boolean), fruit: null, items: [], pantry: [] },
    ],
  }));

  const removed = [];
  for (const o of occs) {
    const x = { ...o, bought: bought.has(o.okey), otherDays: [...(daysByKey.get(o.key) ?? [])].filter((dd) => dd !== o.day).sort() };
    const meal = days[o.day].meals[o.meal === 'breakfast' ? 0 : 1];
    if (removedSet.has(o.okey)) removed.push(x);
    else if (o.cat === 'pantry') meal.pantry.push(x);
    else meal.items.push(x);
  }

  const extras = [];
  for (const o of extraOccurrences(state, staples)) {
    const x = { ...o, bought: bought.has(o.okey), otherDays: [] };
    (removedSet.has(o.okey) ? removed : extras).push(x);
  }
  return { days, extras, removed };
}

export function groupByCategory(items) {
  return CATEGORIES.map((c) => ({ ...c, items: items.filter((it) => it.cat === c.key) })).filter((g) => g.items.length);
}

const COUNT_UNITS = new Set(['개', '대', '마리', '통', '송이', '모', '봉', '팩', '장', '캔', '공기']);

/** 살 때 기준 수량 표시: 개수 단위는 올림, g는 50g, ml은 100ml 단위 올림 */
export function formatQty(qty, unit) {
  if (!qty) return unit ? '' : '약간';
  if (unit === '큰술') return `${Math.round(qty * 2) / 2}큰술`;
  if (COUNT_UNITS.has(unit)) return `${Math.ceil(qty - 1e-9)}${unit}`;
  if (unit === 'g') {
    const g = Math.ceil(qty / 50) * 50;
    return g >= 1000 ? `${trim(g / 1000)}kg` : `${g}g`;
  }
  if (unit === 'ml') {
    const ml = Math.ceil(qty / 100) * 100;
    return ml >= 1000 ? `${trim(ml / 1000)}L` : `${ml}ml`;
  }
  return `${trim(qty)}${unit ?? ''}`;
}
const trim = (n) => String(Math.round(n * 10) / 10);

/** 쿠팡 검색 링크 (저장된 상품 링크가 있으면 그걸 우선) */
export function coupangUrl(name, links = {}) {
  if (links[name]) return links[name];
  const q = name.replace(/[()]/g, ' ').replace(/\s+/g, ' ').trim();
  return `https://www.coupang.com/np/search?q=${encodeURIComponent(q)}`;
}

/**
 * 붙여넣은 텍스트에서 쿠팡 링크 추출.
 * 쿠팡 앱 '공유 → 복사'는 "[쿠팡] 상품명 https://link.coupang.com/a/..." 형태라 URL만 뽑아낸다.
 */
export function extractCoupangLink(text) {
  const urls = String(text ?? '').match(/https?:\/\/[^\s"'<>]+/g) ?? [];
  for (const raw of urls) {
    try {
      const u = new URL(raw);
      if (/(^|\.)coupang\.com$/.test(u.hostname) || u.hostname === 'coupa.ng') return u.href;
    } catch {
      /* 무시 */
    }
  }
  return null;
}

/** 카톡 공유/복사용 텍스트 */
export function listToText(items, title = '장보기 목록') {
  const lines = [`🛒 ${title}`];
  for (const g of groupByCategory(items.filter((it) => !it.bought && it.cat !== 'pantry'))) {
    lines.push('', `${g.emoji} ${g.label}`);
    for (const it of g.items) lines.push(`- ${it.name} ${formatQty(it.qty, it.unit)}`.trimEnd());
  }
  return lines.join('\n');
}

/** 레시피에 적힌 양 그대로 표시 (요일별 화면용: 메뉴 하나에 필요한 양) */
export function formatNeed(qty, unit) {
  if (!qty) return unit ? '' : '약간';
  if (unit === 'g' && qty >= 1000) return `${trim(qty / 1000)}kg`;
  if (unit === 'ml' && qty >= 1000) return `${trim(qty / 1000)}L`;
  return `${trim(qty)}${unit ?? ''}`;
}

/** 요일별 공유 텍스트: 아직 안 산 것만 */
export function dayListToText(dayList, weekStart, title = '장보기 목록') {
  const lines = [`🛒 ${title}`];
  const left = (items) => items.filter((it) => !it.bought);
  const fmt = (items) => items.map((it) => `${it.name} ${formatNeed(it.qty, it.unit)}`.trim()).join(', ');
  for (const { day, meals } of dayList.days) {
    const dayLines = [];
    for (const m of meals) {
      if (m.skipped) continue;
      const icon = m.meal === 'breakfast' ? '☀️' : '🌙';
      for (const r of m.recipes) {
        const items = left(m.items.filter((it) => it.recipeId === r.id));
        if (items.length) dayLines.push(`${icon} ${r.name}: ${fmt(items)}`);
      }
      const fruit = left(m.items.filter((it) => it.slot === 'fruit'));
      if (fruit.length) dayLines.push(`🍎 과일: ${fmt(fruit)}`);
    }
    if (dayLines.length) lines.push('', `[${DAY_NAMES[day]} ${shortDate(addDays(weekStart, day))}]`, ...dayLines);
  }
  const extras = left(dayList.extras);
  if (extras.length) lines.push('', '[그 외]', fmt(extras));
  return lines.join('\n');
}

/** "우유 1L", "계란 30개", "물티슈" → { name, qty, unit } */
export function parseItemInput(text) {
  const t = text.trim().replace(/\s+/g, ' ');
  const m = t.match(/^(.+?)\s+(\d+(?:\.\d+)?)\s*(kg|g|ml|l|L|개|팩|봉|대|마리|통|송이|모|장|캔|병|묶음|단|박스|롤)?$/);
  if (!m) return { name: t, qty: 0, unit: '' };
  const name = m[1].trim();
  let qty = parseFloat(m[2]);
  let unit = m[3] ?? knownUnit.get(name) ?? '개'; // "두부 2" → 2모 (레시피와 합산되도록)
  if (unit === 'kg') { qty *= 1000; unit = 'g'; }
  if (unit === 'l' || unit === 'L') { qty *= 1000; unit = 'ml'; }
  return { name, qty, unit };
}
