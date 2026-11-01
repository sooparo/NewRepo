// 장보기 목록: 주간 식단 → 재료 합산 + 직접 넣기/빼기
import { RECIPES, CATEGORIES } from './data/recipes.js';
import { fruitById } from './data/fruits.js';
import { getRecipe } from './planner.js';
import { DAY_NAMES } from './date.js';

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

function add(map, name, qty, unit, cat, use) {
  const key = itemKey(name, unit);
  const item = map.get(key) ?? { key, name, qty: 0, unit, cat, uses: [] };
  item.qty += qty;
  if (use && !item.uses.includes(use)) item.uses.push(use);
  map.set(key, item);
}

/** 식단에서 필요한 재료 합산 (외식/건너뛴 끼니 제외) */
export function ingredientsFromPlan(plan) {
  const map = new Map();
  plan.days.forEach((d, day) => {
    const label = DAY_NAMES[day];
    const meals = [];
    if (!d.skip?.breakfast) meals.push(getRecipe(d.breakfast));
    if (!d.skip?.dinner) meals.push(getRecipe(d.dinner?.main), getRecipe(d.dinner?.soup));
    for (const r of meals) {
      if (!r) continue;
      for (const [name, qty, unit, cat] of r.ingredients) add(map, name, qty, unit, cat, `${label} ${r.name}`);
    }
    const fruit = fruitById.get(d.fruit);
    if (fruit && !d.skip?.breakfast) add(map, fruit.name, fruit.perDay[0], fruit.perDay[1], 'fruit', `${label} 아침`);
  });
  return map;
}

/**
 * 최종 장보기 목록
 * @param plan    주간 식단
 * @param state   { removed: string[], bought: string[], custom: Item[] }  (해당 주)
 * @param staples 매주 고정으로 넣는 항목 Item[]
 * @returns { items, removed } — items는 표시용(빼지 않은 것), removed는 뺀 항목
 */
export function buildShoppingList(plan, state = {}, staples = []) {
  const removedKeys = new Set(state.removed ?? []);
  const bought = new Set(state.bought ?? []);
  const map = plan ? ingredientsFromPlan(plan) : new Map();

  for (const s of staples) add(map, s.name, Number(s.qty) || 0, s.unit ?? '', s.cat ?? guessCategory(s.name), '매주 고정');
  for (const c of state.custom ?? []) add(map, c.name, Number(c.qty) || 0, c.unit ?? '', c.cat ?? guessCategory(c.name), '직접 추가');

  const all = [...map.values()].map((it) => ({ ...it, bought: bought.has(it.key) }));
  const catOrder = CATEGORIES.map((c) => c.key);
  all.sort((a, b) => catOrder.indexOf(a.cat) - catOrder.indexOf(b.cat) || a.name.localeCompare(b.name, 'ko'));
  return {
    items: all.filter((it) => !removedKeys.has(it.key)),
    removed: all.filter((it) => removedKeys.has(it.key)),
  };
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
