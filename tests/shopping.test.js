import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateWeek, setMeal, toggleSkip, mulberry32 } from '../js/planner.js';
import { buildShoppingList, buildDayList, allOccurrences, occKeysOf, migrateShopState, dayListToText, ingredientsFromPlan, formatQty, coupangUrl, extractCoupangLink, guessCategory, itemKey, listToText } from '../js/shopping.js';

const plan = generateWeek('2026-11-02', undefined, {}, mulberry32(11));

test('재료 합산: 같은 이름+단위는 하나로', () => {
  let p = plan;
  p = setMeal(p, 0, 'main', 'k-bulgogi');
  p = setMeal(p, 1, 'main', 'k-japchae');
  const map = ingredientsFromPlan(p);
  const beef = map.get(itemKey('소고기 불고기용', 'g'));
  assert.ok(beef.qty >= 700);
  assert.ok(beef.uses.some((u) => u.includes('소불고기')) && beef.uses.some((u) => u.includes('잡채')));
});

test('외식한 끼니는 장보기에서 빠진다', () => {
  let p = setMeal(plan, 0, 'main', 'k-galbijjim');
  for (let d = 1; d < 7; d++) if (p.days[d].dinner.main === 'k-galbijjim') p = setMeal(p, d, 'main', 'k-curry');
  assert.ok(ingredientsFromPlan(p).has(itemKey('소갈비(찜용)', 'g')));
  p = toggleSkip(p, 0, 'dinner');
  assert.ok(!ingredientsFromPlan(p).has(itemKey('소갈비(찜용)', 'g')));
});

test('넣기/빼기/구매완료 (메뉴별 키)', () => {
  const first = buildShoppingList(plan).items.find((it) => it.cat !== 'kit');
  const state = { removed: [...first.occ], bought: [], custom: [{ id: 'x1', name: '우유', qty: 1000, unit: 'ml' }, { id: 'y', name: '물티슈', qty: 1, unit: '팩' }] };
  const { items, removed } = buildShoppingList(plan, state, [{ id: 's', name: '계란', qty: 10, unit: '개' }]);
  assert.ok(!items.some((it) => it.key === first.key));
  assert.ok(removed.some((it) => it.key === first.key));
  const tissue = items.find((it) => it.name === '물티슈');
  assert.equal(tissue.cat, 'etc');
  assert.ok(items.find((it) => it.name === '우유').uses.includes('직접 추가'));
  assert.ok(items.find((it) => it.key === itemKey('계란', '개')).uses.includes('매주 고정'));
});

test('요일별 목록: 요일 → 끼니 → 메뉴 → 재료', () => {
  let p = setMeal(plan, 0, 'main', 'k-bulgogi');
  p = setMeal(p, 2, 'main', 'k-japchae');
  const { days, extras } = buildDayList(p, { custom: [{ id: 'a', name: '물티슈', qty: 1, unit: '팩' }] });
  assert.equal(days.length, 7);
  const mon = days[0].meals[1];
  assert.equal(mon.meal, 'dinner');
  assert.equal(mon.recipes[0].id, 'k-bulgogi');
  const beef = mon.items.find((it) => it.name === '소고기 불고기용');
  assert.equal(beef.qty, 500);
  assert.equal(beef.recipeId, 'k-bulgogi');
  assert.ok(beef.otherDays.includes(2), '잡채(수)에도 쓰는 재료 표시');
  assert.ok(mon.pantry.some((it) => it.name === '간장'), '양념은 따로');
  assert.ok(!mon.items.some((it) => it.cat === 'pantry'));
  assert.ok(days[0].meals[0].items.some((it) => it.slot === 'fruit'), '아침에 과일 포함');
  assert.equal(extras[0].name, '물티슈');
});

test('요일별에서 체크한 것은 종류별에서도 반영 (한 메뉴만 사면 일부 구매)', () => {
  let p = setMeal(plan, 0, 'main', 'k-bulgogi');
  p = setMeal(p, 2, 'main', 'k-japchae');
  const occs = allOccurrences(p);
  const beefKeys = occKeysOf(occs, itemKey('소고기 불고기용', 'g'));
  assert.ok(beefKeys.length >= 2);
  let merged = buildShoppingList(p, { bought: [beefKeys[0]] }).items.find((it) => it.name === '소고기 불고기용');
  assert.equal(merged.bought, false, '일부만 산 상태');
  assert.equal(merged.boughtCount, 1);
  merged = buildShoppingList(p, { bought: beefKeys }).items.find((it) => it.name === '소고기 불고기용');
  assert.equal(merged.bought, true);
  const day = buildDayList(p, { bought: [beefKeys[0]] });
  assert.equal(day.days[0].meals[1].items.find((it) => it.name === '소고기 불고기용').bought, true);
  assert.equal(day.days[2].meals[1].items.find((it) => it.name === '소고기 불고기용').bought, false);
});

test('한 메뉴에서만 빼면 그 양만 빠진다', () => {
  let p = setMeal(plan, 0, 'main', 'k-bulgogi');
  p = setMeal(p, 2, 'main', 'k-japchae');
  const total = buildShoppingList(p).items.find((it) => it.name === '소고기 불고기용').qty;
  const monKey = '0:main:' + itemKey('소고기 불고기용', 'g');
  const { items, removed } = buildShoppingList(p, { removed: [monKey] });
  assert.equal(items.find((it) => it.name === '소고기 불고기용').qty, total - 500);
  assert.equal(removed.find((it) => it.name === '소고기 불고기용').qty, 500);
  const day = buildDayList(p, { removed: [monKey] });
  assert.ok(!day.days[0].meals[1].items.some((it) => it.name === '소고기 불고기용'));
  assert.ok(day.removed.some((it) => it.okey === monKey));
});

test('예전 형식(품목 키) 상태를 메뉴별 키로 변환', () => {
  const p = setMeal(plan, 0, 'main', 'k-bulgogi');
  const occs = allOccurrences(p);
  const state = { bought: [itemKey('양파', '개')], removed: [] };
  assert.equal(migrateShopState(occs, state), true);
  assert.ok(state.bought.length >= 1 && state.bought.every((k) => /^\d+:/.test(k)));
  assert.equal(buildShoppingList(p, state).items.find((it) => it.name === '양파').bought, true);
  assert.equal(migrateShopState(occs, state), false, '이미 변환됨');
});

test('외식한 끼니는 요일별 목록에서도 재료 없음', () => {
  const p = toggleSkip(plan, 1, 'dinner');
  const { days } = buildDayList(p);
  assert.equal(days[1].meals[1].skipped, true);
  assert.equal(days[1].meals[1].items.length, 0);
});

test('요일별 공유 텍스트', () => {
  const p = setMeal(plan, 0, 'main', 'k-bulgogi');
  const text = dayListToText(buildDayList(p), p.weekStart);
  assert.ok(text.includes('[월 11/2]'));
  assert.ok(text.includes('🌙 소불고기: '));
  assert.ok(!/(간장|참기름|설탕) \d/.test(text), '양념 제외');
});

test('수량 표시', () => {
  assert.equal(formatQty(1.8, '개'), '2개');
  assert.equal(formatQty(0.3, '대'), '1대');
  assert.equal(formatQty(2, '개'), '2개');
  assert.equal(formatQty(730, 'g'), '750g');
  assert.equal(formatQty(1200, 'g'), '1.2kg');
  assert.equal(formatQty(2400, 'ml'), '2.4L');
  assert.equal(formatQty(3.3, '큰술'), '3.5큰술');
  assert.equal(formatQty(0, ''), '약간');
});

test('쿠팡 링크', () => {
  assert.equal(coupangUrl('돼지고기 앞다리(불고기용)'), 'https://www.coupang.com/np/search?q=' + encodeURIComponent('돼지고기 앞다리 불고기용'));
  assert.equal(coupangUrl('양파', { 양파: 'https://www.coupang.com/vp/products/1' }), 'https://www.coupang.com/vp/products/1');
  assert.equal(extractCoupangLink('[쿠팡] 국내산 양파 3kg https://link.coupang.com/a/abc123 '), 'https://link.coupang.com/a/abc123');
  assert.equal(extractCoupangLink('https://coupa.ng/xyz'), 'https://coupa.ng/xyz');
  assert.equal(extractCoupangLink('https://evil.com/coupang.com'), null);
  assert.equal(extractCoupangLink('그냥 텍스트'), null);
});

test('카테고리 추측 / 텍스트 공유', () => {
  assert.equal(guessCategory('양파'), 'veg');
  assert.equal(guessCategory('사과'), 'fruit');
  assert.equal(guessCategory('키친타월'), 'etc');
  const text = listToText(buildShoppingList(plan).items);
  assert.ok(text.startsWith('🛒'));
  assert.ok(!text.includes('간장'), '기본 양념은 공유 텍스트에서 제외');
});

test('직접 넣기 입력 해석', async () => {
  const { parseItemInput } = await import('../js/shopping.js');
  assert.deepEqual(parseItemInput('우유 1L'), { name: '우유', qty: 1000, unit: 'ml' });
  assert.deepEqual(parseItemInput('계란 30개'), { name: '계란', qty: 30, unit: '개' });
  assert.deepEqual(parseItemInput('양파 1.5kg'), { name: '양파', qty: 1500, unit: 'g' });
  assert.deepEqual(parseItemInput('두부 2'), { name: '두부', qty: 2, unit: '모' });
  assert.deepEqual(parseItemInput('물티슈 3'), { name: '물티슈', qty: 3, unit: '개' });
  assert.deepEqual(parseItemInput('  물티슈 '), { name: '물티슈', qty: 0, unit: '' });
  assert.deepEqual(parseItemInput('비타500'), { name: '비타500', qty: 0, unit: '' });
});

test('이마트몰 링크', async () => {
  const { storeUrl, extractStoreLink, STORES } = await import('../js/shopping.js');
  assert.equal(storeUrl('emart', '돼지고기 앞다리(불고기용)'), 'https://emart.ssg.com/search.ssg?target=all&query=' + encodeURIComponent('돼지고기 앞다리 불고기용'));
  assert.equal(storeUrl('emart', '양파', { 양파: 'https://emart.ssg.com/item/itemView.ssg?itemId=1' }), 'https://emart.ssg.com/item/itemView.ssg?itemId=1');
  assert.equal(storeUrl('coupang', '양파'), 'https://www.coupang.com/np/search?q=' + encodeURIComponent('양파'));
  assert.equal(extractStoreLink('emart', '[이마트몰] 양파 https://emart.ssg.com/item/itemView.ssg?itemId=1000 '), 'https://emart.ssg.com/item/itemView.ssg?itemId=1000');
  assert.equal(extractStoreLink('emart', 'https://m.ssg.com/item/1'), 'https://m.ssg.com/item/1');
  assert.equal(extractStoreLink('emart', 'https://link.coupang.com/a/x'), null, '쿠팡 링크는 이마트 칸에 저장 안 됨');
  assert.equal(extractStoreLink('coupang', 'https://emart.ssg.com/item/1'), null);
  assert.equal(extractStoreLink('emart', 'https://notssg.com.evil.io/'), null);
  assert.deepEqual(Object.keys(STORES), ['coupang', 'emart']);
});
