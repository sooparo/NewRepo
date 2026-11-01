import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateWeek, setMeal, toggleSkip, mulberry32 } from '../js/planner.js';
import { buildShoppingList, ingredientsFromPlan, formatQty, coupangUrl, extractCoupangLink, guessCategory, itemKey, listToText } from '../js/shopping.js';

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

test('넣기/빼기/구매완료', () => {
  const first = buildShoppingList(plan).items[0];
  const state = { removed: [first.key], bought: [], custom: [{ id: 'x', name: '우유', qty: 1000, unit: 'ml' }, { id: 'y', name: '물티슈', qty: 1, unit: '팩' }] };
  const { items, removed } = buildShoppingList(plan, state, [{ id: 's', name: '계란', qty: 10, unit: '개' }]);
  assert.ok(!items.some((it) => it.key === first.key));
  assert.ok(removed.some((it) => it.key === first.key));
  const tissue = items.find((it) => it.name === '물티슈');
  assert.equal(tissue.cat, 'etc');
  assert.ok(items.find((it) => it.name === '우유').uses.includes('직접 추가'));
  assert.ok(items.find((it) => it.key === itemKey('계란', '개')).uses.includes('매주 고정'));
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
