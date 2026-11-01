import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateWeek, reroll, setMeal, toggleSkip, getRecipe, mulberry32, DEFAULT_SETTINGS } from '../js/planner.js';
import { RECIPES } from '../js/data/recipes.js';
import { FRUITS, seasonalFruits } from '../js/data/fruits.js';

const WEEK = '2026-11-02'; // 월요일

test('레시피 데이터 형식이 올바르다', () => {
  const ids = new Set();
  const cats = new Set(['kit', 'veg', 'fruit', 'meat', 'sea', 'egg', 'proc', 'grain', 'etc', 'pantry']);
  for (const r of RECIPES) {
    assert.ok(!ids.has(r.id), `중복 id ${r.id}`);
    ids.add(r.id);
    assert.ok(['breakfast', 'main', 'soup', 'western'].includes(r.type), r.id);
    assert.ok(r.time > 0 && r.steps.length > 0, r.id);
    for (const ing of r.ingredients) {
      assert.equal(ing.length, 4, `${r.id} ${ing}`);
      assert.ok(cats.has(ing[3]), `${r.id} ${ing}`);
    }
  }
});

test('같은 재료 이름은 단위가 하나로 통일되어 있다 (장보기 합산용)', () => {
  const units = new Map();
  for (const r of RECIPES) for (const [name, qty, unit] of r.ingredients) {
    if (!qty) continue;
    if (units.has(name)) assert.equal(units.get(name), unit, `${name}: ${units.get(name)} vs ${unit} (${r.id})`);
    else units.set(name, unit);
  }
});

test('모든 달에 제철 과일이 3종 이상', () => {
  for (let m = 1; m <= 12; m++) assert.ok(seasonalFruits(m).length >= 3, `${m}월`);
});

for (let seed = 1; seed <= 200; seed++) {
  test(`주간 식단 규칙 (seed ${seed})`, () => {
    const plan = generateWeek(WEEK, DEFAULT_SETTINGS, {}, mulberry32(seed));
    assert.equal(plan.days.length, 7);
    const breakfasts = plan.days.map((d) => d.breakfast);
    const mains = plan.days.map((d) => d.dinner.main);
    assert.equal(new Set(breakfasts).size, 7, '아침 중복');
    assert.equal(new Set(mains).size, 7, '저녁 중복');
    const western = mains.filter((id) => getRecipe(id).type === 'western').length;
    assert.equal(western, 2, '양식 2회');
    const kitDays = mains.map((id, i) => (getRecipe(id).kit ? i : -1)).filter((i) => i >= 0);
    assert.equal(kitDays.length, 2, '밀키트 2회');
    assert.ok(kitDays.every((i) => i < 5), '밀키트는 평일에만');
    const spicy = plan.days.flatMap((d) => [d.dinner.main, d.dinner.soup]).filter((id) => id && getRecipe(id).spicy).length;
    assert.ok(spicy <= 1, '매운 요리 1회 이하');
    plan.days.forEach((d, day) => {
      const m = getRecipe(d.dinner.main);
      const limit = day >= 5 ? 90 : 40;
      assert.ok(m.time <= limit, `${day}일 ${m.name} ${m.time}분`);
      if (day < 5) assert.ok(getRecipe(d.breakfast).time <= 15, '평일 아침 15분 이내');
      if (m.type === 'main' && !m.oneDish) assert.ok(d.dinner.soup, `${m.name}에 국 없음`);
      if (m.type === 'western' || m.oneDish) assert.equal(d.dinner.soup, null);
      if (day > 0) assert.notEqual(getRecipe(plan.days[day - 1].dinner.main).protein, m.protein, '연속 같은 단백질');
      const fruit = FRUITS.find((f) => f.id === d.fruit);
      assert.ok(fruit.months.includes(11), '11월 제철');
    });
  });
}

test('싫어요 레시피는 추천하지 않는다', () => {
  const disliked = RECIPES.filter((r) => r.type === 'breakfast').slice(0, 5).map((r) => r.id);
  for (let seed = 1; seed < 50; seed++) {
    const plan = generateWeek(WEEK, DEFAULT_SETTINGS, { disliked }, mulberry32(seed));
    for (const d of plan.days) assert.ok(!disliked.includes(d.breakfast));
  }
});

test('양식 0회 / 매운요리 0회 / 밀키트 0회 설정', () => {
  for (let seed = 1; seed < 50; seed++) {
    const plan = generateWeek(WEEK, { westernPerWeek: 0, spicyPerWeek: 0, mealKitPerWeek: 0 }, {}, mulberry32(seed));
    for (const d of plan.days) {
      assert.notEqual(getRecipe(d.dinner.main).type, 'western');
      assert.ok(!getRecipe(d.dinner.main).kit);
      assert.ok(!getRecipe(d.dinner.main).spicy && !(d.dinner.soup && getRecipe(d.dinner.soup).spicy));
    }
  }
});

test('한 끼 다시 추천은 다른 메뉴를 주고 나머지는 유지', () => {
  const rng = mulberry32(7);
  const plan = generateWeek(WEEK, DEFAULT_SETTINGS, {}, rng);
  for (const slot of ['breakfast', 'main', 'fruit']) {
    for (let day = 0; day < 7; day++) {
      const next = reroll(plan, day, slot, DEFAULT_SETTINGS, {}, rng);
      const get = (p) => (slot === 'main' ? p.days[day].dinner.main : p.days[day][slot]);
      assert.notEqual(get(next), get(plan), `${slot} ${day}`);
      next.days.forEach((d, i) => { if (i !== day) assert.deepEqual(d, plan.days[i]); });
    }
  }
  // 국 다시 추천
  const day = plan.days.findIndex((d) => d.dinner.soup);
  const next = reroll(plan, day, 'soup', DEFAULT_SETTINGS, {}, rng);
  assert.notEqual(next.days[day].dinner.soup, plan.days[day].dinner.soup);
  assert.equal(next.days[day].dinner.main, plan.days[day].dinner.main);
});

test('직접 고르기 / 외식 토글', () => {
  const plan = generateWeek(WEEK, DEFAULT_SETTINGS, {}, mulberry32(3));
  const p2 = setMeal(plan, 0, 'main', 'k-curry');
  assert.equal(p2.days[0].dinner.main, 'k-curry');
  assert.equal(p2.days[0].dinner.soup, null, '한그릇 요리는 국 없음');
  const p3 = toggleSkip(p2, 0, 'dinner');
  assert.equal(p3.days[0].skip.dinner, true);
  assert.equal(toggleSkip(p3, 0, 'dinner').days[0].skip.dinner, false);
});

test('밀키트 3회 + 양식 3회도 규칙대로', () => {
  for (let seed = 1; seed < 100; seed++) {
    const plan = generateWeek(WEEK, { westernPerWeek: 3, mealKitPerWeek: 3 }, {}, mulberry32(seed));
    const mains = plan.days.map((d) => getRecipe(d.dinner.main));
    assert.equal(mains.filter((r) => r.kit).length, 3);
    assert.equal(mains.filter((r) => r.type === 'western').length, 3);
    assert.equal(new Set(mains.map((r) => r.id)).size, 7);
  }
});

test('밀키트로 바꾸기 / 직접 요리로 바꾸기', () => {
  const rng = mulberry32(5);
  const plan = generateWeek(WEEK, DEFAULT_SETTINGS, {}, rng);
  for (let day = 0; day < 7; day++) {
    const isKit = !!getRecipe(plan.days[day].dinner.main).kit;
    const toggled = reroll(plan, day, 'main', DEFAULT_SETTINGS, {}, rng, { kit: !isKit });
    assert.equal(!!getRecipe(toggled.days[day].dinner.main).kit, !isKit, `day ${day}`);
    const same = reroll(plan, day, 'main', DEFAULT_SETTINGS, {}, rng);
    assert.equal(!!getRecipe(same.days[day].dinner.main).kit, isKit, '다시 추천은 밀키트 여부 유지');
  }
});
