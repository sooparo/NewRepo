// 주간 식단 추천 로직 (DOM 의존 없음 → node 테스트 가능)
import { RECIPES } from './data/recipes.js';
import { FRUITS, fruitById, seasonalFruits } from './data/fruits.js';
import { monthOfWeek } from './date.js';

export const DEFAULT_SETTINGS = {
  westernPerWeek: 2, // 양식 저녁 횟수
  mealKitPerWeek: 2, // 밀키트 저녁 횟수 (평일에 배치)
  spicyPerWeek: 1, // 매운 요리 허용 횟수 (아이용 덜어두기 팁 제공)
  weekdayMaxMinutes: 40, // 평일 저녁 조리 시간 한도
  weekendMaxMinutes: 90, // 주말 저녁 조리 시간 한도
  weekdayBreakfastMax: 15, // 평일 아침 조리 시간 한도
};

const recipeById = new Map(RECIPES.map((r) => [r.id, r]));
export const getRecipe = (id) => recipeById.get(id);
export const recipesOfType = (type) => RECIPES.filter((r) => r.type === type);

/** 재현 가능한 난수 (테스트용 시드 지원) */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const isWeekend = (day) => day >= 5; // 0=월 … 5=토, 6=일

function shuffle(arr, rng) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

const shoppingIngredients = (r) => (r ? r.ingredients.filter((i) => i[3] !== 'pantry').map((i) => i[0]) : []);

/** 점수가 가장 높은 후보 선택 (점수 + 무작위성) */
function pickBest(candidates, scoreFn, rng, noise = 3) {
  let best = null;
  let bestScore = -Infinity;
  for (const c of candidates) {
    const s = scoreFn(c) + rng() * noise;
    if (s > bestScore) {
      bestScore = s;
      best = c;
    }
  }
  return best;
}

/**
 * 조건을 단계적으로 완화하며 후보 고르기.
 * filters: 엄격한 순서대로의 필터 배열. 결과가 비면 뒤쪽 필터부터 하나씩 제거.
 */
function pickWithRelax(pool, filters, scoreFn, rng) {
  for (let n = filters.length; n >= 0; n--) {
    const active = filters.slice(0, n);
    const cands = pool.filter((r) => active.every((f) => f(r)));
    if (cands.length) return pickBest(cands, scoreFn, rng);
  }
  return null;
}

// ───────────────────────────── 과일 ─────────────────────────────

/** 일주일 과일 배정: 제철 과일 3종을 3·2·2일씩 (장보기 품목 수 줄이기) */
export function assignFruits(month, rng) {
  let pool = shuffle(seasonalFruits(month), rng).slice(0, 3);
  if (pool.length < 3) pool = [...pool, ...FRUITS.filter((f) => f.always)].slice(0, 3);
  const pattern = [0, 0, 0, 1, 1, 2, 2];
  return pattern.map((i) => pool[i % pool.length].id);
}

export function rerollFruit(plan, day, rng = Math.random) {
  const month = monthOfWeek(plan.weekStart);
  const current = plan.days[day].fruit;
  const cands = [...seasonalFruits(month), ...FRUITS.filter((f) => f.always)].filter((f) => f.id !== current);
  const pick = cands[Math.floor(rng() * cands.length)];
  return updateDay(plan, day, { fruit: pick.id });
}

// ───────────────────────────── 아침 ─────────────────────────────

function pickBreakfast(day, days, prefs, settings, rng) {
  const used = new Set(days.filter((d, i) => i !== day && d).map((d) => d.breakfast));
  const prevBase = getRecipe(days[day - 1]?.breakfast)?.base;
  const nextBase = getRecipe(days[day + 1]?.breakfast)?.base;
  const pool = recipesOfType('breakfast').filter((r) => !prefs.disliked.includes(r.id));
  const filters = [
    (r) => !used.has(r.id),
    (r) => isWeekend(day) || r.time <= settings.weekdayBreakfastMax,
  ];
  const score = (r) =>
    (prefs.favorites.includes(r.id) ? 2 : 0) -
    (r.base === prevBase ? 1.5 : 0) -
    (r.base === nextBase ? 1.5 : 0) +
    (isWeekend(day) && r.time > settings.weekdayBreakfastMax ? 1 : 0); // 주말엔 조금 특별하게
  return pickWithRelax(pool, filters, score, rng)?.id ?? null;
}

// ───────────────────────────── 저녁 ─────────────────────────────

function dinnerContext(days, day) {
  const others = days.filter((d, i) => i !== day && d);
  const mains = others.map((d) => getRecipe(d.dinner?.main)).filter(Boolean);
  const soups = others.map((d) => getRecipe(d.dinner?.soup)).filter(Boolean);
  const ingredients = new Set([...mains, ...soups].flatMap(shoppingIngredients));
  const spicyCount = [...mains, ...soups].filter((r) => r.spicy).length;
  return { mains, soups, ingredients, spicyCount };
}

function overlap(recipe, ingredientSet) {
  return shoppingIngredients(recipe).filter((n) => ingredientSet.has(n)).length;
}

function pickMain(day, days, cuisine, kit, prefs, settings, rng) {
  const ctx = dinnerContext(days, day);
  const limit = isWeekend(day) ? settings.weekendMaxMinutes : settings.weekdayMaxMinutes;
  const usedIds = new Set(ctx.mains.map((r) => r.id));
  const neighborProteins = [days[day - 1], days[day + 1]]
    .map((d) => getRecipe(d?.dinner?.main)?.protein)
    .filter(Boolean);
  const type = cuisine === 'western' ? 'western' : 'main';
  // 밀키트 날은 밀키트만, 직접 요리하는 날은 밀키트 제외 (완화하지 않는 조건)
  const pool = recipesOfType(type).filter((r) => !prefs.disliked.includes(r.id) && !!r.kit === kit);
  const filters = [
    (r) => !usedIds.has(r.id),
    (r) => r.time <= limit,
    (r) => !r.spicy || ctx.spicyCount < settings.spicyPerWeek,
    (r) => !neighborProteins.includes(r.protein),
  ];
  const score = (r) =>
    (prefs.favorites.includes(r.id) ? 2 : 0) +
    overlap(r, ctx.ingredients) * 0.5 + // 재료 겹치면 장보기 줄어듦
    (isWeekend(day) && r.time > settings.weekdayMaxMinutes ? 1 : 0); // 주말엔 시간 드는 특식 우대
  return pickWithRelax(pool, filters, score, rng);
}

function pickSoup(day, days, main, prefs, settings, rng) {
  if (!main || main.type !== 'main' || main.oneDish) return null;
  const ctx = dinnerContext(days, day);
  const limit = isWeekend(day) ? settings.weekendMaxMinutes : settings.weekdayMaxMinutes;
  const usedIds = new Set(ctx.soups.map((r) => r.id));
  const spicyNow = ctx.spicyCount + (main.spicy ? 1 : 0);
  const pool = recipesOfType('soup').filter((r) => !prefs.disliked.includes(r.id));
  const filters = [
    (r) => !usedIds.has(r.id),
    (r) => !r.spicy || (!main.spicy && spicyNow < settings.spicyPerWeek),
    (r) => r.time <= limit,
  ];
  const mainIngredients = new Set(shoppingIngredients(main));
  const score = (r) =>
    (prefs.favorites.includes(r.id) ? 2 : 0) +
    overlap(r, ctx.ingredients) * 0.3 -
    (r.protein === main.protein ? 2 : 0) - // 불고기 + 소고기무국 같은 조합 피하기
    overlap(r, mainIngredients) * 0.3;
  return pickWithRelax(pool, filters, score, rng);
}

/** 요일 고르기: 가능한 한 서로 붙지 않게 */
function chooseSpreadDays(count, candidates, rng) {
  const chosen = [];
  const order = shuffle(candidates, rng);
  for (const d of order) {
    if (chosen.length >= count) break;
    if (chosen.some((c) => Math.abs(c - d) === 1)) continue;
    chosen.push(d);
  }
  for (const d of order) if (chosen.length < count && !chosen.includes(d)) chosen.push(d); // 자리가 모자라면 붙여서라도
  return chosen;
}

// ───────────────────────────── 주간 생성 ─────────────────────────────

const normalizePrefs = (prefs = {}) => ({ favorites: prefs.favorites ?? [], disliked: prefs.disliked ?? [] });

/**
 * 한 주 식단 생성
 * @param {string} weekStart 'YYYY-MM-DD' (월요일)
 */
export function generateWeek(weekStart, settings = DEFAULT_SETTINGS, prefsIn = {}, rng = Math.random) {
  const s = { ...DEFAULT_SETTINGS, ...settings };
  const prefs = normalizePrefs(prefsIn);
  const fruits = assignFruits(monthOfWeek(weekStart), rng);
  const westernDays = new Set(chooseSpreadDays(s.westernPerWeek, [0, 1, 2, 3, 4, 5, 6], rng));
  const kitDays = new Set(chooseSpreadDays(s.mealKitPerWeek, [0, 1, 2, 3, 4], rng)); // 바쁜 평일에
  const days = new Array(7).fill(null);

  for (let day = 0; day < 7; day++) {
    days[day] = { breakfast: null, fruit: fruits[day], dinner: { main: null, soup: null }, skip: {} };
    days[day].breakfast = pickBreakfast(day, days, prefs, s, rng);
    const main = pickMain(day, days, westernDays.has(day) ? 'western' : 'korean', kitDays.has(day), prefs, s, rng);
    days[day].dinner.main = main?.id ?? null;
    days[day].dinner.soup = pickSoup(day, days, main, prefs, s, rng)?.id ?? null;
  }
  return { weekStart, createdAt: Date.now(), days };
}

function updateDay(plan, day, patch) {
  const days = plan.days.map((d, i) => (i === day ? { ...d, ...patch } : d));
  return { ...plan, days };
}

/**
 * 한 끼(아침/메인/국)만 다시 추천
 * options.kit: 메인을 밀키트(true) / 직접 요리(false)로 바꾸기. 생략하면 지금 상태 유지
 */
export function reroll(plan, day, slot, settings = DEFAULT_SETTINGS, prefsIn = {}, rng = Math.random, options = {}) {
  const s = { ...DEFAULT_SETTINGS, ...settings };
  const prefs = normalizePrefs(prefsIn);
  const current = plan.days[day];
  // 지금 메뉴는 이번에는 제외
  const tmpPrefs = (id) => ({ ...prefs, disliked: [...prefs.disliked, id] });

  if (slot === 'breakfast') {
    const id = pickBreakfast(day, plan.days, tmpPrefs(current.breakfast), s, rng);
    return updateDay(plan, day, { breakfast: id ?? current.breakfast });
  }
  if (slot === 'fruit') return rerollFruit(plan, day, rng);

  const days = plan.days.map((d, i) => (i === day ? { ...d, dinner: { main: null, soup: null } } : d));
  if (slot === 'main') {
    const cur = getRecipe(current.dinner.main);
    const cuisine = cur?.cuisine ?? 'korean';
    const kit = options.kit ?? !!cur?.kit;
    const main =
      pickMain(day, days, cuisine, kit, tmpPrefs(current.dinner.main), s, rng) ??
      pickMain(day, days, cuisine === 'western' ? 'korean' : 'western', kit, tmpPrefs(current.dinner.main), s, rng) ??
      cur;
    days[day] = { ...days[day], dinner: { main: main.id, soup: null } };
    const keepSoup = main.type === 'main' && !main.oneDish && current.dinner.soup;
    const soup = keepSoup ? getRecipe(current.dinner.soup) : pickSoup(day, days, main, prefs, s, rng);
    return updateDay(plan, day, { dinner: { main: main.id, soup: soup?.id ?? null } });
  }
  if (slot === 'soup') {
    const main = getRecipe(current.dinner.main);
    days[day] = { ...days[day], dinner: { main: current.dinner.main, soup: null } };
    const exclude = current.dinner.soup ? tmpPrefs(current.dinner.soup) : prefs;
    const forced = main && main.type === 'main' ? { ...main, oneDish: false } : main; // 한그릇요리에 국 추가 요청 시
    const soup = pickSoup(day, days, forced, exclude, s, rng);
    return updateDay(plan, day, { dinner: { main: current.dinner.main, soup: soup?.id ?? null } });
  }
  throw new Error(`unknown slot ${slot}`);
}

/** 직접 고른 메뉴로 바꾸기 */
export function setMeal(plan, day, slot, recipeId) {
  const d = plan.days[day];
  if (slot === 'breakfast') return updateDay(plan, day, { breakfast: recipeId });
  if (slot === 'fruit') return updateDay(plan, day, { fruit: recipeId });
  if (slot === 'main') {
    const r = getRecipe(recipeId);
    const soup = r.type === 'main' && !r.oneDish ? d.dinner.soup : null;
    return updateDay(plan, day, { dinner: { main: recipeId, soup } });
  }
  if (slot === 'soup') return updateDay(plan, day, { dinner: { ...d.dinner, soup: recipeId } });
  throw new Error(`unknown slot ${slot}`);
}

/** 외식/건너뛰기 토글 (장보기에서 빠짐) */
export function toggleSkip(plan, day, meal) {
  const skip = { ...(plan.days[day].skip ?? {}) };
  skip[meal] = !skip[meal];
  return updateDay(plan, day, { skip });
}

export { fruitById };
