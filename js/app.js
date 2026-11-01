// 우리집 식단 — 화면 렌더링과 이벤트 처리
import { RECIPES, CATEGORIES } from './data/recipes.js';
import { FRUITS, fruitById, seasonalFruits } from './data/fruits.js';
import { generateWeek, reroll, setMeal, toggleSkip, getRecipe } from './planner.js';
import {
  buildShoppingList, groupByCategory, formatQty, coupangUrl, extractCoupangLink, guessCategory, listToText, itemKey, parseItemInput,
} from './shopping.js';
import { loadDB, saveDB, parseBackup, pruneOld, emptyDB } from './storage.js';
import { mondayOf, addDays, DAY_NAMES, shortDate, weekLabel, monthOfWeek, toKey } from './date.js';

const APP_VERSION = '1.0.0';
const $ = (sel) => document.querySelector(sel);

let db = loadDB();
const ui = {
  tab: 'plan',
  weekOffset: 0,
  sheet: null, // { type, ... }
  sheetPushed: false,
  recipeFilter: 'all',
  recipeSearch: '',
};

// ───────────────────────────── 유틸 ─────────────────────────────

const esc = (s) =>
  String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const thisWeek = () => mondayOf(new Date());
const currentWeek = () => addDays(thisWeek(), ui.weekOffset * 7);

function persist() {
  if (!saveDB(db)) toast('저장 공간이 부족해서 저장하지 못했어요.');
}

let toastTimer;
function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 2000);
}

function getPlan(week = currentWeek(), create = true) {
  if (!db.plans[week] && create && week >= thisWeek()) {
    db.plans[week] = generateWeek(week, db.settings, db.prefs);
    persist();
  }
  return db.plans[week] ?? null;
}

function savePlan(plan) {
  db.plans[plan.weekStart] = plan;
  persist();
}

function shopState(week = currentWeek()) {
  db.shopping[week] ??= { removed: [], bought: [], custom: [] };
  return db.shopping[week];
}

const isFav = (id) => db.prefs.favorites.includes(id);
const isDisliked = (id) => db.prefs.disliked.includes(id);

function toggleIn(arr, v) {
  const i = arr.indexOf(v);
  if (i >= 0) arr.splice(i, 1);
  else arr.push(v);
  return i < 0;
}

const SLOT_LABEL = { breakfast: '아침', main: '저녁 메인', soup: '국·찌개', fruit: '과일' };
const TYPE_LABEL = { breakfast: '아침', main: '한식', soup: '국·찌개', western: '양식' };

function badges(r) {
  const b = [`<span class="badge">⏱ ${r.time}분</span>`];
  if (r.type === 'western') b.push('<span class="badge western">양식</span>');
  if (r.spicy) b.push('<span class="badge spicy">🌶 매움</span>');
  if (r.oneDish) b.push('<span class="badge">한 그릇</span>');
  if (isFav(r.id)) b.push('<span class="badge fav">❤️</span>');
  return b.join(' ');
}

// ───────────────────────────── 상단 바 / 탭 ─────────────────────────────

function renderTopbar() {
  const el = $('#topbar');
  if (ui.tab === 'plan' || ui.tab === 'shop') {
    const week = currentWeek();
    const rel = { '-1': '지난주', 0: '이번 주', 1: '다음 주' }[ui.weekOffset] ?? (ui.weekOffset > 0 ? `${ui.weekOffset}주 뒤` : `${-ui.weekOffset}주 전`);
    el.innerHTML = `
      <div class="weeknav">
        <button class="iconbtn" data-action="week" data-dir="-1" aria-label="이전 주" ${ui.weekOffset <= -4 ? 'disabled' : ''}>‹</button>
        <div class="wk"><strong>${ui.tab === 'plan' ? '식단' : '장보기'} · ${rel}</strong><small>${weekLabel(week)}</small></div>
        <button class="iconbtn" data-action="week" data-dir="1" aria-label="다음 주" ${ui.weekOffset >= 3 ? 'disabled' : ''}>›</button>
      </div>`;
  } else {
    el.innerHTML = `<h1>${ui.tab === 'recipes' ? '📖 레시피' : '⚙️ 설정'}</h1>`;
  }
}

function renderTabbar() {
  for (const b of document.querySelectorAll('.tabbar button')) {
    if (b.dataset.tab === ui.tab) b.setAttribute('aria-current', 'page');
    else b.removeAttribute('aria-current');
  }
}

// ───────────────────────────── 식단 탭 ─────────────────────────────

function renderPlan() {
  const week = currentWeek();
  const plan = getPlan(week);
  if (!plan) return `<p class="empty">이 주에는 기록된 식단이 없어요.</p>`;

  const month = monthOfWeek(week);
  const season = seasonalFruits(month).map((f) => `${f.emoji} ${f.name}`).join(' · ');
  const todayIdx = week === thisWeek() ? (new Date().getDay() + 6) % 7 : -1;

  const days = plan.days
    .map((d, i) => {
      const b = getRecipe(d.breakfast);
      const fruit = fruitById.get(d.fruit);
      const m = getRecipe(d.dinner.main);
      const s = getRecipe(d.dinner.soup);
      const skipTag = '<span class="skip-tag">건너뜀</span>';
      return `
      <article class="card day ${i === todayIdx ? 'today' : ''}">
        <div class="day-head">
          <span class="dname ${i >= 5 ? 'weekend' : ''}">${DAY_NAMES[i]}</span>
          <span class="ddate">${shortDate(addDays(week, i))}</span>
          ${i === todayIdx ? '<span class="today-badge">오늘</span>' : ''}
        </div>
        <button class="meal ${d.skip?.breakfast ? 'skipped' : ''}" data-action="open-meal" data-day="${i}" data-slot="breakfast">
          <span class="meal-label">아침</span>
          <span class="meal-main">${b ? `${b.emoji} ${esc(b.name)}` : '-'} ${d.skip?.breakfast ? skipTag : ''}</span>
          <span class="meal-go">›</span>
          <span class="meal-sub">${fruit ? `${fruit.emoji} ${fruit.name}` : ''}</span>
        </button>
        <button class="meal ${d.skip?.dinner ? 'skipped' : ''}" data-action="open-meal" data-day="${i}" data-slot="dinner">
          <span class="meal-label">저녁</span>
          <span class="meal-main">${m ? `${m.emoji} ${esc(m.name)}` : '-'} ${d.skip?.dinner ? '<span class="skip-tag">외식</span>' : ''}</span>
          <span class="meal-go">›</span>
          <span class="meal-sub">${[s ? `+ ${s.emoji} ${esc(s.name)}` : '', m?.type === 'western' ? '<span class="badge western">양식</span>' : '', m ? `⏱${m.time}분` : '', m?.spicy || s?.spicy ? '🌶' : ''].filter(Boolean).join(' ')}</span>
        </button>
      </article>`;
    })
    .join('');

  return `
    <div class="season">🧺 <b>${month}월 제철 과일</b> · ${season}</div>
    <div class="days">${days}</div>
    <button class="btn wide regen" data-action="regen-week">🔄 이번 주 식단 전체 새로 추천</button>
    <p class="hint" style="text-align:center">메뉴를 누르면 레시피를 보고, 바꾸거나 외식으로 표시할 수 있어요.</p>`;
}

// ───────────────────────────── 레시피 블록 (시트 공용) ─────────────────────────────

function recipeBody(r) {
  const shop = r.ingredients.filter((i) => i[3] !== 'pantry');
  const pantry = r.ingredients.filter((i) => i[3] === 'pantry');
  const ing = (i) => `<li>${esc(i[0])} <small>${i[1] ? formatIngredientQty(i[1], i[2]) : ''}</small></li>`;
  return `
    <div>${badges(r)}</div>
    ${r.kidTip ? `<div class="kidtip">👶 ${esc(r.kidTip)}</div>` : ''}
    <div class="sub-h">재료 (4인 가족 기준)</div>
    <ul class="ings">${shop.map(ing).join('')}</ul>
    ${pantry.length ? `<ul class="ings pantry">${pantry.map(ing).join('')}</ul>` : ''}
    <div class="sub-h">만드는 법</div>
    <ol class="steps">${r.steps.map((s) => `<li>${esc(s)}</li>`).join('')}</ol>
    <div class="btnrow" style="margin-top:10px">
      <button class="btn small" data-action="fav" data-id="${r.id}">${isFav(r.id) ? '❤️ 즐겨찾기됨' : '🤍 즐겨찾기'}</button>
      <button class="btn small ${isDisliked(r.id) ? 'danger' : ''}" data-action="dislike" data-id="${r.id}">${isDisliked(r.id) ? '🚫 추천 안 함 (해제)' : '🚫 다시 추천 안 함'}</button>
    </div>`;
}

/** 레시피 재료 수량은 정확한 값으로 표시 (장보기처럼 올림하지 않음) */
function formatIngredientQty(qty, unit) {
  if (unit === 'g' && qty >= 1000) return `${qty / 1000}kg`;
  return `${qty}${unit}`;
}

function recipeBlock(r, kind, actions) {
  return `
    <section class="card block">
      <div class="block-title"><span class="em">${r.emoji}</span><div><div class="kind">${kind}</div><h3>${esc(r.name)}</h3></div></div>
      ${actions ? `<div class="btnrow" style="margin-bottom:8px">${actions}</div>` : ''}
      ${recipeBody(r)}
    </section>`;
}

// ───────────────────────────── 시트 ─────────────────────────────

function renderSheet() {
  const root = $('#sheet-root');
  const prevScroll = root.querySelector('.sheet-body')?.scrollTop ?? 0;
  const s = ui.sheet;
  if (!s) {
    root.innerHTML = '';
    document.body.style.overflow = '';
    return;
  }
  let title = '';
  let body = '';
  if (s.type === 'meal') ({ title, body } = mealSheet(s));
  else if (s.type === 'pick') ({ title, body } = pickSheet(s));
  else if (s.type === 'recipe') ({ title, body } = recipeSheet(s));
  else if (s.type === 'item') ({ title, body } = itemSheet(s));

  root.innerHTML = `
    <div class="sheet-backdrop" data-action="close-sheet"></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="sheet-head"><h2>${title}</h2><button class="iconbtn" data-action="close-sheet" aria-label="닫기">✕</button></div>
      <div class="sheet-body">${body}</div>
    </div>`;
  document.body.style.overflow = 'hidden';
  if (s.keepScroll) root.querySelector('.sheet-body').scrollTop = prevScroll;
  s.keepScroll = true;
}

function mealSheet({ day, slot }) {
  const plan = getPlan();
  const d = plan.days[day];
  const dayLabel = `${DAY_NAMES[day]} ${shortDate(addDays(plan.weekStart, day))}`;

  if (slot === 'breakfast') {
    const r = getRecipe(d.breakfast);
    const f = fruitById.get(d.fruit);
    return {
      title: `${dayLabel} 아침`,
      body: `
        <div class="btnrow" style="margin-bottom:12px">
          <button class="btn small ${d.skip?.breakfast ? 'primary' : ''}" data-action="skip" data-day="${day}" data-meal="breakfast">${d.skip?.breakfast ? '✅ 건너뛰기 해제' : '🙅 이날 아침 건너뛰기'}</button>
        </div>
        ${r ? recipeBlock(r, '아침', `<button class="btn small primary" data-action="reroll" data-day="${day}" data-slot="breakfast">🔄 다른 메뉴</button><button class="btn small" data-action="pick" data-day="${day}" data-slot="breakfast">📋 직접 고르기</button>`) : ''}
        ${f ? `
        <section class="card block">
          <div class="fruit-row"><span class="em">${f.emoji}</span><div style="flex:1"><div class="sub-h" style="margin:0">오늘의 과일</div><h3>${f.name}</h3></div></div>
          ${f.tip ? `<div class="kidtip">👶 ${esc(f.tip)}</div>` : ''}
          <div class="btnrow"><button class="btn small" data-action="reroll" data-day="${day}" data-slot="fruit">🔄 다른 과일</button><button class="btn small" data-action="pick" data-day="${day}" data-slot="fruit">📋 직접 고르기</button></div>
        </section>` : ''}`,
    };
  }

  const m = getRecipe(d.dinner.main);
  const s = getRecipe(d.dinner.soup);
  const canSoup = m && m.type === 'main';
  let soupHtml = '';
  if (s) {
    soupHtml = recipeBlock(s, '국·찌개', `
      <button class="btn small" data-action="reroll" data-day="${day}" data-slot="soup">🔄 다른 국</button>
      <button class="btn small" data-action="pick" data-day="${day}" data-slot="soup">📋 직접 고르기</button>
      <button class="btn small" data-action="no-soup" data-day="${day}">✕ 국 빼기</button>`);
  } else if (canSoup) {
    soupHtml = `<button class="btn wide" data-action="reroll" data-day="${day}" data-slot="soup">＋ 국·찌개 추가</button>`;
  }
  return {
    title: `${dayLabel} 저녁`,
    body: `
      <div class="btnrow" style="margin-bottom:12px">
        <button class="btn small ${d.skip?.dinner ? 'primary' : ''}" data-action="skip" data-day="${day}" data-meal="dinner">${d.skip?.dinner ? '✅ 외식 해제' : '🍽 외식 / 건너뛰기'}</button>
      </div>
      ${d.skip?.dinner ? '<p class="hint">외식으로 표시한 끼니는 장보기 목록에서 빠져요.</p>' : ''}
      ${m ? recipeBlock(m, m.type === 'western' ? '양식 메인' : '한식 메인', `<button class="btn small primary" data-action="reroll" data-day="${day}" data-slot="main">🔄 다른 메뉴</button><button class="btn small" data-action="pick" data-day="${day}" data-slot="main">📋 직접 고르기</button>`) : ''}
      ${soupHtml}`,
  };
}

function pickSheet({ day, slot }) {
  const plan = getPlan();
  const d = plan.days[day];
  let items;
  let current;
  if (slot === 'fruit') {
    const month = monthOfWeek(plan.weekStart);
    const list = [...FRUITS].sort((a, b) => Number(b.months.includes(month)) - Number(a.months.includes(month)));
    current = d.fruit;
    items = list.map((f) => ({ id: f.id, emoji: f.emoji, name: f.name, meta: f.months.includes(month) && !f.always ? '제철' : '' }));
  } else {
    const types = slot === 'main' ? ['main', 'western'] : [slot];
    current = slot === 'breakfast' ? d.breakfast : slot === 'main' ? d.dinner.main : d.dinner.soup;
    items = RECIPES.filter((r) => types.includes(r.type))
      .sort((a, b) => Number(isFav(b.id)) - Number(isFav(a.id)) || types.indexOf(a.type) - types.indexOf(b.type))
      .map((r) => ({ id: r.id, emoji: r.emoji, name: r.name, meta: `${TYPE_LABEL[r.type]} · ${r.time}분${r.spicy ? ' · 🌶' : ''}${isFav(r.id) ? ' · ❤️' : ''}${isDisliked(r.id) ? ' · 🚫' : ''}` }));
  }
  return {
    title: `${DAY_NAMES[day]}요일 ${SLOT_LABEL[slot]} 고르기`,
    body: `<div class="picklist">${items
      .map((it) => `
        <button class="pick ${it.id === current ? 'current' : ''}" data-action="choose" data-day="${day}" data-slot="${slot}" data-id="${it.id}">
          <span class="em">${it.emoji}</span><span class="nm">${esc(it.name)}</span><span class="hint">${it.meta}</span>
        </button>`)
      .join('')}</div>`,
  };
}

function recipeSheet({ id }) {
  const r = getRecipe(id);
  return { title: `${r.emoji} ${esc(r.name)}`, body: `<section class="card block">${recipeBody(r)}</section>` };
}

function itemSheet({ key }) {
  const week = currentWeek();
  const { items, removed } = buildShoppingList(getPlan(week, false), shopState(week), db.prefs.staples);
  const it = [...items, ...removed].find((x) => x.key === key);
  if (!it) return { title: '항목', body: '<p class="empty">이미 목록에서 사라진 항목이에요.</p>' };
  const saved = db.prefs.links[it.name];
  const isStaple = db.prefs.staples.some((s) => itemKey(s.name, s.unit) === key);
  const isRemoved = removed.some((x) => x.key === key);
  return {
    title: `${esc(it.name)} <small class="iqty">${formatQty(it.qty, it.unit)}</small>`,
    body: `
      ${it.uses.length ? `<p class="hint">사용: ${esc(it.uses.join(', '))}</p>` : ''}
      <a class="btn primary wide" href="${esc(coupangUrl(it.name, db.prefs.links))}" target="_blank" rel="noopener">🛒 쿠팡에서 ${saved ? '저장한 상품 열기' : '검색하기'}</a>
      ${saved ? `<a class="btn wide" style="margin-top:8px" href="${esc(coupangUrl(it.name))}" target="_blank" rel="noopener">🔍 다른 상품 검색</a>` : ''}

      <section class="card set" style="margin-top:14px">
        <h3>⭐ 자주 사는 상품 저장</h3>
        <p class="hint">쿠팡 앱에서 원하는 상품 → <b>공유</b> → <b>링크 복사</b> 후 아래에 붙여넣으세요.<br>다음부터 이 재료의 ‘쿠팡’ 버튼이 그 상품으로 바로 열려요.</p>
        <input class="input" id="link-input" style="width:100%" placeholder="https://link.coupang.com/..." value="${esc(saved ?? '')}" inputmode="url" autocomplete="off">
        <div class="btnrow" style="margin-top:8px">
          <button class="btn small" data-action="paste-link">📋 붙여넣기</button>
          <button class="btn small primary" data-action="save-link" data-name="${esc(it.name)}">저장</button>
          ${saved ? `<button class="btn small danger" data-action="clear-link" data-name="${esc(it.name)}">링크 지우기</button>` : ''}
        </div>
      </section>

      <div class="btnrow">
        <button class="btn wide" data-action="staple" data-key="${esc(key)}">${isStaple ? '📌 매주 고정 해제' : '📌 매주 자동으로 넣기'}</button>
        ${isRemoved
          ? `<button class="btn wide" data-action="shop-restore" data-key="${esc(key)}">↩️ 다시 목록에 넣기</button>`
          : `<button class="btn wide danger" data-action="shop-remove" data-key="${esc(key)}">✕ 이번 주 목록에서 빼기</button>`}
      </div>`,
  };
}

function openSheet(sheet) {
  const wasOpen = !!ui.sheet;
  ui.sheet = sheet;
  if (!wasOpen) {
    history.pushState({ sheet: true }, '');
    ui.sheetPushed = true;
  }
  renderSheet();
}

function closeSheet() {
  if (ui.sheetPushed) history.back(); // popstate에서 닫힘 (안드로이드 뒤로가기와 동일하게)
  else {
    ui.sheet = null;
    render();
  }
}

window.addEventListener('popstate', () => {
  if (ui.sheet) {
    ui.sheet = null;
    ui.sheetPushed = false;
    render();
  }
});

// ───────────────────────────── 장보기 탭 ─────────────────────────────

function renderShop() {
  const week = currentWeek();
  const plan = getPlan(week);
  if (!plan) return `<p class="empty">이 주에는 기록된 식단이 없어요.</p>`;
  const { items, removed } = buildShoppingList(plan, shopState(week), db.prefs.staples);
  const buyable = items.filter((it) => it.cat !== 'pantry');
  const done = buyable.filter((it) => it.bought).length;
  const pct = buyable.length ? Math.round((done / buyable.length) * 100) : 0;

  const row = (it) => `
    <li class="item ${it.bought ? 'bought' : ''}">
      <button class="check" data-action="shop-toggle" data-key="${esc(it.key)}" aria-label="${esc(it.name)} 구매 완료"><i>✓</i></button>
      <button class="item-body" data-action="open-item" data-key="${esc(it.key)}">
        <span class="iname">${esc(it.name)}</span><span class="iqty">${formatQty(it.qty, it.unit)}</span>
        <span class="iuse">${esc(it.uses.slice(0, 2).join(', '))}${it.uses.length > 2 ? ` 외 ${it.uses.length - 2}` : ''}</span>
      </button>
      <a class="cp ${db.prefs.links[it.name] ? 'saved' : ''}" href="${esc(coupangUrl(it.name, db.prefs.links))}" target="_blank" rel="noopener">${db.prefs.links[it.name] ? '⭐쿠팡' : '쿠팡'}</a>
      <button class="x" data-action="shop-remove" data-key="${esc(it.key)}" aria-label="${esc(it.name)} 빼기">✕</button>
    </li>`;

  const groups = groupByCategory(items)
    .map((g) => {
      const list = [...g.items].sort((a, b) => Number(a.bought) - Number(b.bought));
      const inner = `<ul class="items card">${list.map(row).join('')}</ul>`;
      if (g.key === 'pantry') {
        return `<details class="cat"><summary>${g.emoji} ${g.label} · ${g.items.length}</summary>${inner}</details>`;
      }
      return `<section class="cat"><h3>${g.emoji} ${g.label} · ${g.items.length}</h3>${inner}</section>`;
    })
    .join('');

  const removedHtml = removed.length
    ? `<details class="cat"><summary>🗑 뺀 항목 · ${removed.length}</summary>
        <ul class="items card">${removed
          .map((it) => `
          <li class="item">
            <button class="item-body" data-action="open-item" data-key="${esc(it.key)}" style="padding-left:10px">
              <span class="iname">${esc(it.name)}</span><span class="iqty">${formatQty(it.qty, it.unit)}</span>
            </button>
            <button class="btn small" data-action="shop-restore" data-key="${esc(it.key)}">다시 넣기</button>
          </li>`)
          .join('')}</ul></details>`
    : '';

  return `
    <div class="shop-summary">
      <div class="txt"><strong>${done} / ${buyable.length}</strong> 구매 완료
        <div class="progress"><i style="width:${pct}%"></i></div>
      </div>
      <button class="btn small" data-action="share-list">📤 공유</button>
    </div>
    <form class="add-row" data-form="shop-add" autocomplete="off">
      <input name="entry" placeholder="넣을 것 (예: 우유 1L, 계란 30개)" enterkeyhint="done" aria-label="장보기 항목 추가">
      <button class="btn primary" type="submit">넣기</button>
    </form>
    <p class="hint">‘쿠팡’을 누르면 상품 검색이 열려요. 품목 이름을 누르면 자주 사는 상품 링크를 저장할 수 있어요(⭐).</p>
    ${groups || '<p class="empty">장보기 목록이 비어 있어요.</p>'}
    ${removedHtml}`;
}

// ───────────────────────────── 레시피 탭 ─────────────────────────────

const RECIPE_FILTERS = [
  ['all', '전체'], ['breakfast', '☀️ 아침'], ['main', '🍚 한식'], ['soup', '🍲 국·찌개'], ['western', '🍝 양식'], ['fav', '❤️ 즐겨찾기'], ['disliked', '🚫 제외'],
];

function recipeListHtml() {
  const q = ui.recipeSearch.trim();
  const f = ui.recipeFilter;
  const list = RECIPES.filter((r) => {
    if (f === 'fav' && !isFav(r.id)) return false;
    if (f === 'disliked' && !isDisliked(r.id)) return false;
    if (!['all', 'fav', 'disliked'].includes(f) && r.type !== f) return false;
    if (q && !(r.name.includes(q) || r.ingredients.some((i) => i[0].includes(q)))) return false;
    return true;
  });
  if (!list.length) return '<p class="empty">해당하는 레시피가 없어요.</p>';
  return list
    .map((r) => `
      <button class="pick recipe-row" data-action="open-recipe" data-id="${r.id}">
        <span class="em">${r.emoji}</span>
        <span class="nm">${esc(r.name)}<br><span class="meta">${TYPE_LABEL[r.type]} · ${r.time}분${r.spicy ? ' · 🌶' : ''}${r.oneDish ? ' · 한 그릇' : ''}</span></span>
        <span>${isFav(r.id) ? '❤️' : ''}${isDisliked(r.id) ? '🚫' : ''}</span>
      </button>`)
    .join('');
}

function renderRecipes() {
  return `
    <input class="input search" type="search" data-input="recipe-search" placeholder="레시피·재료 검색 (예: 감자)" value="${esc(ui.recipeSearch)}">
    <div class="chips">${RECIPE_FILTERS.map(([k, label]) => `<button class="chip" data-action="recipe-filter" data-filter="${k}" aria-pressed="${ui.recipeFilter === k}">${label}</button>`).join('')}</div>
    <p class="hint">총 ${RECIPES.length}개 · ❤️ 즐겨찾기는 더 자주, 🚫 제외는 추천하지 않아요.</p>
    <div class="picklist" id="recipe-list">${recipeListHtml()}</div>`;
}

// ───────────────────────────── 설정 탭 ─────────────────────────────

function seg(key, options) {
  return `<div class="seg">${options
    .map(([v, label]) => `<button data-action="setting" data-key="${key}" data-value="${v}" aria-pressed="${db.settings[key] === v}">${label}</button>`)
    .join('')}</div>`;
}

function renderSettings() {
  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;
  return `
    <section class="card set">
      <h3>🍝 양식 저녁</h3><p class="hint">일주일에 몇 번 양식을 넣을까요?</p>
      ${seg('westernPerWeek', [[0, '안 함'], [1, '1회'], [2, '2회'], [3, '3회']])}
    </section>
    <section class="card set">
      <h3>🌶 매운 요리</h3><p class="hint">아이용으로 덜어두는 팁과 함께 추천해요.</p>
      ${seg('spicyPerWeek', [[0, '안 함'], [1, '1회'], [2, '2회'], [3, '3회']])}
    </section>
    <section class="card set">
      <h3>⏱ 평일 저녁 조리 시간</h3>
      ${seg('weekdayMaxMinutes', [[20, '20분'], [30, '30분'], [40, '40분'], [60, '60분']])}
    </section>
    <section class="card set">
      <h3>⏱ 주말 저녁 조리 시간</h3>
      ${seg('weekendMaxMinutes', [[40, '40분'], [60, '60분'], [90, '90분'], [999, '제한 없음']])}
      <p class="hint">설정은 다음에 ‘새로 추천’할 때부터 적용돼요.</p>
    </section>

    <section class="card set">
      <h3>📌 매주 고정 장보기</h3>
      ${db.prefs.staples.length
        ? `<ul class="ings">${db.prefs.staples.map((s) => `<li>${esc(s.name)} <small>${formatQty(s.qty, s.unit)}</small> <button data-action="staple-del" data-id="${esc(s.id)}" aria-label="삭제">✕</button></li>`).join('')}</ul>`
        : '<p class="hint">장보기 품목을 누르고 ‘매주 자동으로 넣기’를 누르면 여기에 추가돼요. (예: 우유, 계란)</p>'}
    </section>

    ${standalone ? '' : `
    <section class="card set install">
      <h3>📱 홈 화면에 설치하기</h3>
      ${isIOS
        ? '<ol><li>Safari 아래쪽 <b>공유 버튼(□↑)</b>을 누르세요.</li><li><b>홈 화면에 추가</b>를 누르세요.</li></ol>'
        : '<ol><li>Chrome/삼성인터넷 오른쪽 위 <b>⋮ 메뉴</b>를 누르세요.</li><li><b>홈 화면에 추가</b> 또는 <b>앱 설치</b>를 누르세요.</li></ol>'}
      ${deferredInstall ? '<button class="btn primary wide" style="margin-top:10px" data-action="install">지금 설치하기</button>' : ''}
    </section>`}

    <section class="card set">
      <h3>💾 백업 / 복원</h3>
      <p class="hint">데이터는 이 휴대폰에만 저장돼요. 휴대폰을 바꾸기 전에 백업 파일을 저장해 두세요.</p>
      <div class="btnrow">
        <button class="btn small" data-action="export">백업 파일 저장</button>
        <label class="btn small">백업 불러오기<input type="file" accept="application/json,.json" data-input="import" hidden></label>
        <button class="btn small danger" data-action="reset">전체 초기화</button>
      </div>
    </section>
    <p class="hint" style="text-align:center">우리집 식단 v${APP_VERSION} · 레시피 ${RECIPES.length}개</p>`;
}

// ───────────────────────────── 렌더 ─────────────────────────────

function render() {
  renderTopbar();
  renderTabbar();
  const view = $('#view');
  if (ui.tab === 'plan') view.innerHTML = renderPlan();
  else if (ui.tab === 'shop') view.innerHTML = renderShop();
  else if (ui.tab === 'recipes') view.innerHTML = renderRecipes();
  else view.innerHTML = renderSettings();
  renderSheet();
}

// ───────────────────────────── 액션 ─────────────────────────────

const actions = {
  tab: ({ tab }) => {
    ui.tab = tab;
    window.scrollTo(0, 0);
  },
  week: ({ dir }) => {
    ui.weekOffset = Math.max(-4, Math.min(3, ui.weekOffset + Number(dir)));
  },
  'open-meal': ({ day, slot }) => openSheet({ type: 'meal', day: +day, slot }),
  reroll: ({ day, slot }) => {
    savePlan(reroll(getPlan(), +day, slot, db.settings, db.prefs));
    toast('다른 메뉴로 바꿨어요');
  },
  pick: ({ day, slot }) => {
    ui.sheet = { type: 'pick', day: +day, slot, back: ui.sheet };
  },
  choose: ({ day, slot, id }) => {
    savePlan(setMeal(getPlan(), +day, slot, id));
    ui.sheet = ui.sheet.back ?? null;
    if (ui.sheet) ui.sheet.keepScroll = false;
    toast('메뉴를 바꿨어요');
  },
  'no-soup': ({ day }) => savePlan(setMeal(getPlan(), +day, 'soup', null)),
  skip: ({ day, meal }) => {
    const plan = toggleSkip(getPlan(), +day, meal);
    savePlan(plan);
    toast(plan.days[day].skip[meal] ? '장보기 목록에서 뺐어요' : '다시 장보기 목록에 넣었어요');
  },
  'regen-week': () => {
    if (!confirm('이번 주 식단을 전부 새로 추천할까요?\n(직접 바꾼 메뉴도 모두 바뀌어요)')) return;
    const week = currentWeek();
    db.plans[week] = generateWeek(week, db.settings, db.prefs);
    persist();
    toast('새 식단을 추천했어요');
  },
  fav: ({ id }) => {
    const on = toggleIn(db.prefs.favorites, id);
    if (on) db.prefs.disliked = db.prefs.disliked.filter((x) => x !== id);
    persist();
    toast(on ? '즐겨찾기에 추가했어요 — 더 자주 추천할게요' : '즐겨찾기를 해제했어요');
  },
  dislike: ({ id }) => {
    const on = toggleIn(db.prefs.disliked, id);
    if (on) db.prefs.favorites = db.prefs.favorites.filter((x) => x !== id);
    persist();
    toast(on ? '앞으로 추천하지 않을게요' : '다시 추천 목록에 넣었어요');
  },
  'open-recipe': ({ id }) => openSheet({ type: 'recipe', id }),
  'recipe-filter': ({ filter }) => {
    ui.recipeFilter = filter;
  },

  // 장보기
  'shop-toggle': ({ key }) => {
    toggleIn(shopState().bought, key);
    persist();
  },
  'shop-remove': ({ key }) => {
    const st = shopState();
    const before = st.custom.length;
    st.custom = st.custom.filter((c) => itemKey(c.name, c.unit) !== key);
    if (st.custom.length === before || buildShoppingList(getPlan(), st, db.prefs.staples).items.some((it) => it.key === key)) {
      if (!st.removed.includes(key)) st.removed.push(key);
    }
    persist();
    toast('목록에서 뺐어요 (아래 ‘뺀 항목’에서 되돌릴 수 있어요)');
    if (ui.sheet?.type === 'item') {
      closeSheet(); // 닫히면서 다시 그려짐
      return false;
    }
  },
  'shop-restore': ({ key }) => {
    const st = shopState();
    st.removed = st.removed.filter((k) => k !== key);
    persist();
    toast('다시 넣었어요');
  },
  'open-item': ({ key }) => openSheet({ type: 'item', key }),
  'paste-link': async () => {
    try {
      const text = await navigator.clipboard.readText();
      $('#link-input').value = extractCoupangLink(text) ?? text;
    } catch {
      toast('입력칸을 길게 눌러 붙여넣기 해주세요');
    }
    return false; // 다시 그리지 않음
  },
  'save-link': ({ name }) => {
    const link = extractCoupangLink($('#link-input').value);
    if (!link) {
      toast('쿠팡 링크를 찾지 못했어요');
      return false;
    }
    db.prefs.links[name] = link;
    persist();
    toast('저장했어요 — 다음부터 바로 이 상품이 열려요');
  },
  'clear-link': ({ name }) => {
    delete db.prefs.links[name];
    persist();
  },
  staple: ({ key }) => {
    const idx = db.prefs.staples.findIndex((s) => itemKey(s.name, s.unit) === key);
    if (idx >= 0) {
      db.prefs.staples.splice(idx, 1);
      toast('매주 고정에서 뺐어요');
    } else {
      const st = shopState();
      const custom = st.custom.find((c) => itemKey(c.name, c.unit) === key);
      const [name, unit] = key.split('|');
      db.prefs.staples.push({ id: String(Date.now()), name, unit, qty: custom?.qty ?? 1, cat: custom?.cat ?? guessCategory(name) });
      st.custom = st.custom.filter((c) => c !== custom); // 중복 방지
      toast('매주 자동으로 넣을게요');
    }
    persist();
  },
  'staple-del': ({ id }) => {
    db.prefs.staples = db.prefs.staples.filter((s) => s.id !== id);
    persist();
  },
  'share-list': async () => {
    const week = currentWeek();
    const { items } = buildShoppingList(getPlan(week), shopState(week), db.prefs.staples);
    const text = listToText(items, `장보기 (${weekLabel(week)})`);
    try {
      if (navigator.share) await navigator.share({ text });
      else {
        await navigator.clipboard.writeText(text);
        toast('목록을 복사했어요');
      }
    } catch {
      /* 공유 취소 */
    }
    return false;
  },

  // 설정
  setting: ({ key, value }) => {
    db.settings[key] = Number(value);
    persist();
  },
  install: async () => {
    if (!deferredInstall) return false;
    deferredInstall.prompt();
    await deferredInstall.userChoice;
    deferredInstall = null;
  },
  export: () => {
    const blob = new Blob([JSON.stringify(db, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `우리집식단-백업-${toKey(new Date())}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
    return false;
  },
  reset: () => {
    if (!confirm('모든 식단, 장보기, 즐겨찾기, 저장한 링크를 지울까요?')) return false;
    db = emptyDB();
    persist();
    toast('초기화했어요');
  },
  'close-sheet': () => {
    closeSheet();
    return false;
  },
};

document.addEventListener('click', async (e) => {
  const el = e.target.closest('[data-action]');
  if (!el) return;
  const fn = actions[el.dataset.action];
  if (!fn) return;
  if (el.tagName === 'BUTTON') e.preventDefault();
  const result = await fn({ ...el.dataset });
  if (result !== false) render();
});

document.addEventListener('submit', (e) => {
  const form = e.target.closest('[data-form="shop-add"]');
  if (!form) return;
  e.preventDefault();
  const input = form.elements.entry;
  const text = input.value.trim();
  if (!text) return;
  const { name, qty, unit } = parseItemInput(text);
  const st = shopState();
  const key = itemKey(name, unit);
  st.removed = st.removed.filter((k) => k !== key);
  st.custom.push({ id: String(Date.now()), name, qty, unit, cat: guessCategory(name) });
  persist();
  render();
  toast(`‘${name}’ 넣었어요`);
  $('[data-form="shop-add"] input')?.focus();
});

document.addEventListener('input', (e) => {
  if (e.target.dataset.input === 'recipe-search') {
    ui.recipeSearch = e.target.value;
    $('#recipe-list').innerHTML = recipeListHtml();
  }
});

document.addEventListener('change', async (e) => {
  if (e.target.dataset.input !== 'import') return;
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    db = parseBackup(await file.text());
    persist();
    toast('백업을 불러왔어요');
    render();
  } catch (err) {
    toast(err.message || '파일을 읽지 못했어요');
  }
});

// ───────────────────────────── PWA ─────────────────────────────

let deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredInstall = e;
  if (ui.tab === 'settings') render();
});

if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
}

// 날짜가 바뀐 뒤 앱을 다시 열면 '오늘' 표시 갱신
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') render();
});

pruneOld(db, thisWeek());
persist();
render();
