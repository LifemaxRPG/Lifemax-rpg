/* Golden cases for the quest board, recorded from the real page, so the native board (android-wrapper/native/src/main/java/com/lifemax/rpg/
   core/quests: QuestRules, QuestBoard, ClassPacks, CustomQuests) is accepted only when it shows, files and creates quests bit for bit as the
   page does. Regenerate when the page's quest board changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_quests_board_golden.js [built-page.html]
   Output: tests/fixtures/quests-board-golden.json, one case per line. tests/quests-board-golden.test.js records every case again in memory
   and fails when the page and the file disagree. The cases are defined in tools/quests_board_cases.js.

   A case is {id, family, rule, input, output}, laid out as in gen_quests_golden.js:
     family  weekday | key | stage | pack | create | board | ledger | odd | fuzz.
     input   {now, zone, state, calls}. state holds the save fields the board reads (IN below), exactly as the page held them when the first
             call ran; a field not listed is absent from the save (the case definitions may write {D<n>}, {I<n> HH:MM:SS} and {W} as in the
             Daily Hand cases). calls run in order: {fn, args, at?, ctx} (at moves the clock for that call and the ones after it). ctx is what the call reads outside the save, as the page computed
             it just before the call: today (getToday()), day (getDay()), week (the weekly period's Monday), now (Date.now()), level
             (characterLevel().level, or null when that threw) and, for createQuest, fields: what the three form fields held after the
             page's own sanitising (a number input drops text that is not a number). The native code takes these as plain inputs.
             FN: questToday(quest), questKey(quest), isQuestDone(quest), stageLvl(stage) (a stage written {"$": "undefined" | "NaN" | "-0" |
             "Infinity"} is that value), stageOf(quest) (stageLvl(quest.stage)), questLocked(quest), applyClassContent(), createQuest(name,
             cadence, xp), renderQuests() (the board's rows, summary and empty lines, read back from the page's own markup), renderLedger()
             (the 3D table's quest sections: the page's own renderLedger text, run outside the scene) and assign(field, value).
     output  {calls: [{returns, state?, deleted?, toasts?, saves?}]} as in gen_quests_golden.js. A call that threw returns {threw: message}. */
'use strict';
const fs = require('fs'), path = require('path');
const { defineCases } = require('./quests_board_cases.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'quests-board-golden.json');
const LEDGER_SRC = path.join(__dirname, '..', 'src', 'runtime', 'segments', '50-quests-table.js');
const BOOT = '2026-10-28T06:00:00.000Z';
const IN = ['quests', 'questDone', 'classId', 'classContent', 'attrXP', 'legacyCharXPBonus', 'ascended'];
const OUT = ['quests', 'questDone', 'classId', 'classContent'];

/* the page's own renderLedger, cut out of the segment (it lives inside the 3D scene, which needs WebGL), up to where it compares with the last draw */
function ledgerSource() {
  const text = fs.readFileSync(LEDGER_SRC, 'utf8'), a = text.indexOf('var lgRow=function'), b = text.indexOf('if(html===lgHtml)return;', a);
  if (a < 0 || b < 0) throw new Error('renderLedger moved: update ledgerSource() in tools/gen_quests_board_golden.js');
  return text.slice(a, b);
}

/* ---------- the page side: runs in the built page, against its own functions ---------- */
function pageRunner({ IN, OUT, ledgerSrc }) {
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  let toasts = [], saves = 0;
  window.toast = m => { toasts.push(String(m)) };
  save = function () { saves++; return true };
  dhBusy = true; // no auto-verify
  dhRenderHand = function () { }; // renderQuests deals today's Hand first (dhEnsure, the Daily Hand's own cases); the board is recorded over the save as given
  const day = n => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA') };
  const instant = (n, hms) => { const d = new Date(); d.setDate(d.getDate() + n); const [h, m, s] = hms.split(':').map(Number); d.setHours(h, m, s, 0); return d.toISOString() };
  const monday = (off = 0) => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + off); return d.toLocaleDateString('en-CA') };
  const resolve = v => typeof v === 'string'
    ? v.replace(/\{W([+-]\d+)?\}/g, (_, n) => monday(n ? +n : 0)).replace(/\{D([+-]?\d+)\}/g, (_, n) => day(+n)).replace(/\{I([+-]?\d+) (\d\d:\d\d:\d\d)\}/g, (_, n, t) => instant(+n, t))
    : Array.isArray(v) ? v.map(resolve) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [resolve(k), resolve(x)])) : v;
  const lit = v => (v && typeof v === 'object' && !Array.isArray(v) && '$' in v) ? { undefined: undefined, NaN: NaN, '-0': -0, Infinity: Infinity }[v.$] : v;
  const text = () => Object.fromEntries(OUT.map(k => [k, JSON.stringify(state[k])]));
  const q = (root, sel) => root.querySelector(sel);
  const rowsOf = id => [...document.getElementById(id).querySelectorAll('.qos-quest')].map(r => {
    const box = q(r, 'input[type=checkbox]'), small = q(r, '.qos-quest-copy small');
    return { id: (box.getAttribute('onchange').match(/^qosToggle\('([\s\S]*)',this\.checked\)$/) || [])[1], name: q(r, 'strong').textContent, desc: small ? small.textContent : null,
      reward: q(r, '.qos-reward').textContent, done: box.checked, note: !!q(r, '.qnote') };
  });
  const emptyOf = id => { const e = document.getElementById(id).querySelector('.qos-empty span:last-child'); return e ? e.textContent : null };
  const board = () => {
    renderQuests();
    const norm = h => { const d = document.createElement('div'); d.innerHTML = h; return d.innerHTML }, icons = { lock: norm(px('lock')), trophy: norm(px('trophy')), flag: norm(px('flag')) };
    const epics = [...document.getElementById('achievements').querySelectorAll('.qos-epic')].map(r => {
      const box = q(r, 'input[type=checkbox]'), locked = r.classList.contains('is-locked'), svg = q(r, '.qos-epic-icon').innerHTML;
      return { id: box ? (box.getAttribute('onchange').match(/^qosToggle\('([\s\S]*)',this\.checked\)$/) || [])[1] : null, name: q(r, 'strong').textContent, sub: q(r, 'small').textContent,
        reward: q(r, '.qos-epic-xp').textContent, locked, done: box ? box.checked : null, icon: Object.keys(icons).find(k => icons[k] === svg) };
    });
    const t = id => document.getElementById(id).textContent;
    return { daily: { rows: rowsOf('dailyQuests'), empty: emptyOf('dailyQuests') }, weekly: { rows: rowsOf('weeklyQuests'), empty: emptyOf('weeklyQuests') },
      nutrition: { visible: document.getElementById('nutritionCard').style.display !== 'none', rows: rowsOf('nutritionQuests') },
      epics: { rows: epics, empty: emptyOf('achievements') },
      summary: { progress: t('qosDailyProgress'), percent: t('qosDailyPct'), dailyTotal: t('qosDailyTotal'), weeklyTotal: t('qosWeeklyTotal'), available: t('qosAvailableXP'),
        dailyCount: t('qosDailyCount'), weeklyCount: t('qosWeeklyCount'), nutritionCount: t('qosNutritionCount'), motivation: t('qosMotivation'), fill: document.getElementById('qosDailyFill').style.width } };
  };
  const renderLedger = new Function('qpxText', ledgerSrc + 'return html};return renderLedger')(t => '<span>' + String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</span>');
  const ledger = () => {
    const box = document.createElement('div'); box.innerHTML = renderLedger();
    const sections = [];
    for (const el of box.children) {
      if (el.classList.contains('lg-badges')) break;
      if (el.classList.contains('lg-sec')) { if (el.textContent.startsWith('BADGES')) break; sections.push({ title: el.textContent, rows: [], empty: null }) }
      else if (el.classList.contains('lg-row')) { const b = q(el, '.lg-chk'); sections[sections.length - 1].rows.push({ id: b.dataset.id, name: q(el, 'strong').textContent, sub: q(el, 'small').textContent, reward: q(el, '.lg-xp').textContent,
        done: el.classList.contains('done'), locked: el.classList.contains('locked'), mark: b.textContent }) }
      else if (el.classList.contains('lg-empty')) sections[sections.length - 1].empty = el.textContent;
    }
    return { sections };
  };
  const field = id => document.getElementById(id);
  const create = (name, cad, xp) => { field('newQuestName').value = name; field('newQuestCadence').value = cad; field('newQuestXP').value = xp; createQuest(); field('newQuestName').value = '' };
  const FN = {
    questToday: x => questToday(x), questKey: x => questKey(x), isQuestDone: x => isQuestDone(x), stageLvl: s => stageLvl(lit(s)), stageOf: x => stageLvl(x.stage),
    questLocked: x => questLocked(x), applyClassContent: () => { applyClassContent(); return null }, createQuest: (n, c, x) => { create(n, c, x); return null },
    renderQuests: board, renderLedger: ledger, assign: (f, v) => { state[f] = v; return null },
  };
  window.__qb = {
    setup(given) {
      state = Object.assign(freshState(), { xpSchemaV2: true });
      for (const k of IN) delete state[k];
      Object.assign(state, resolve(clone(given || {})));
      const s = {};
      for (const k of IN) if (state[k] !== undefined) s[k] = clone(state[k]);
      return s;
    },
    call(c) {
      if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
      const args = resolve(clone(c.args || []));
      const ctx = { today: getToday(), day: new Date().getDay(), week: monday(0), now: Date.now(), level: null };
      try { ctx.level = characterLevel().level } catch (e) { }
      if (c.fn === 'createQuest') { // what the three fields hold once the browser has sanitised the text
        field('newQuestName').value = args[0]; field('newQuestCadence').value = args[1]; field('newQuestXP').value = args[2];
        ctx.fields = { name: field('newQuestName').value, cadence: field('newQuestCadence').value, xp: field('newQuestXP').value };
      }
      const before = text();
      toasts = []; saves = 0;
      let returns;
      try { const v = FN[c.fn](...args); returns = v === undefined ? null : clone(v) } catch (e) { returns = { threw: String((e && e.message) || e) } }
      const after = text(), out = { returns }, changed = {}, deleted = [];
      for (const k of OUT) if (after[k] !== before[k]) { if (state[k] === undefined) deleted.push(k); else changed[k] = clone(state[k]) }
      if (Object.keys(changed).length) out.state = changed;
      if (deleted.length) out.deleted = deleted;
      if (toasts.length) out.toasts = toasts.slice();
      if (saves) out.saves = saves;
      return { call: { fn: c.fn, args: clone(c.args || []).map(x => resolve(x)), ...(c.at ? { at: c.at } : {}), ctx }, out };
    },
  };
}

/* ---------- recording ---------- */
async function openPage(browser, file, zone, init) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: zone, locale: 'en-US' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date(BOOT) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  if (init) await p.addInitScript(init); // a test's extra set-up (tests/native-shadow-transparent.test.js); the generator passes none
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof renderQuests === 'function' && typeof applyClassContent === 'function' && typeof characterLevel === 'function' && typeof dhEnsure === 'function', null, { timeout: 120000 });
  await p.clock.pauseAt(new Date('2026-10-28T12:00:00.000Z'));
  await p.evaluate(pageRunner, { IN, OUT, ledgerSrc: ledgerSource() });
  return p;
}

async function record(p, c) {
  await p.clock.setSystemTime(new Date(c.now));
  const state = await p.evaluate(s => window.__qb.setup(s), c.state);
  const calls = [], outs = [];
  let now = c.now;
  for (const x of c.calls) {
    if (x.at) now = x.at;
    await p.clock.setSystemTime(new Date(now));
    const r = await p.evaluate(x => window.__qb.call(x), x);
    calls.push(r.call); outs.push(r.out);
  }
  return { id: c.id, family: c.family, rule: c.rule, input: { now: c.now, zone: c.zone, state, calls }, output: { calls: outs } };
}

/* Records every case in the built page `file`; resolves to the list of cases. */
async function generate(file, log = () => { }, init) {
  const { chromium } = require('playwright');
  const cases = defineCases(), browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const pages = {}, out = [], page = async zone => pages[zone] || (pages[zone] = await openPage(browser, file, zone, init));
    for (const c of cases) {
      out.push(JSON.parse(JSON.stringify(await record(await page(c.zone), c))));
      if (out.length % 200 === 0) log(out.length + ' of ' + cases.length + ' cases recorded');
    }
    const errors = Object.values(pages).flatMap(p => p.errors);
    if (errors.length) throw new Error('the page threw while recording: ' + errors.slice(0, 3).join(' | '));
    return out;
  } finally { await browser.close() }
}

const serialize = cases => '[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]\n';

module.exports = { generate, defineCases, serialize, FILE, IN, OUT };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file, m => console.log(m)).then(cases => {
    fs.writeFileSync(FILE, serialize(cases));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + cases.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1) });
}
