/* Golden cases for the Daily Hand draw, recorded from the real page, so the native draw (android-wrapper/native/src/main/java/com/lifemax/rpg/
   core/quests: HandRandom, HandPick, HandDeal) is accepted only when it deals bit for bit the same cards. Regenerate when the page's Daily
   Hand changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_quests_golden.js [built-page.html]
   Output: tests/fixtures/quests-golden.json, one case per line. tests/quests-golden.test.js records every case again in memory and fails
   when the page and the file disagree, so the file cannot go stale. The cases themselves are defined in tools/quests_golden_cases.js.

   A case is {id, family, rule, input, output}:
     family  seed | rng | quest | recent | draw | ensure | reroll | odd | fuzz (what it exercises; the Kotlin test counts each family).
     rule    the QUESTS_SPEC rule and defect IDs it exercises, separated by spaces.
     input   {now, zone, state, calls}. now (ISO) is the clock, frozen while the case runs, and zone the device time zone. state holds the
             save fields the draw reads (IN below) exactly as the page held them when the first call ran; a field not listed is absent from
             the save. The case definitions may write {D<n>} for the local day n days from today and {I<n> HH:MM:SS} for that local wall
             time as an ISO instant, and {W} for this week's local Monday; the recorder resolves them in the page, so the file holds only plain values.
             calls run in order: {fn, args, at?, ctx}. fn is one of FN below; at moves the clock for that call (and later ones until the
             next at). ctx is what the call reads outside the draw, as the page computed it just before the call: today (getToday()),
             luck (luckToday().score, or null when luckToday() gives nothing: the draw then uses 5500) and rest ({day: ecoRestDay(day)} for
             today and for a dhDraw's own date; null when ecoRestDay threw). The native draw takes these as plain inputs.
             FN: dhSeed(text), dhRng(seed, n) (its first n outputs), dhMakeQuest(templateId, date, slot, nonce), dhRecentAttrXP(days),
             dhWeakestAttr(), dhDraw(date, nonce, slot, taken), dhEnsure(), dhCards(), dhRerollCost(), dhReroll(slot), and assign(field,
             value), which sets state[field] (the owner switching class between two calls, say).
     output  {calls: [{returns, state?, deleted?, toasts?, saves?}]}, one per call: its return value ({threw: message} if it threw; a
             template is given by its id, cards and quests as the page holds them), the OUT fields the call changed (compared as
             JSON.stringify text, so key order counts) with their value after, the OUT fields it removed, the messages it showed, and how
             many times it asked to save.

   What is held still: the clock is Playwright's, paused; toast is captured; save() is counted and not run (what saving does, auto-verify
   included, is the economy's SAVE-1 to SAVE-4 and has its own golden cases); the Daily Hand's auto-verify never runs. */
'use strict';
const fs = require('fs'), path = require('path');
const { defineCases } = require('./quests_golden_cases.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'quests-golden.json');
const BOOT = '2026-10-28T06:00:00.000Z';
/* the save fields a case sets (everything else is as freshState() has it, minus these), and the fields a call's output reports */
const IN = ['quests', 'dailyDraw', 'dhMig', 'activities', 'classId', 'sparks', 'questDone', 'luck', 'restDays', 'plan'];
const OUT = ['quests', 'dailyDraw', 'dhMig', 'sparks', 'questDone'];

/* ---------- the page side: runs in the built page, against its own functions ---------- */
function pageRunner({ IN, OUT }) {
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  let toasts = [], saves = 0;
  window.toast = m => { toasts.push(String(m)) };
  save = function () { saves++; return true };
  dhBusy = true; // no auto-verify: it runs only inside the real save()
  const day = n => { const d = new Date(); d.setDate(d.getDate() + n); return d.toLocaleDateString('en-CA') };
  const instant = (n, hms) => { const d = new Date(); d.setDate(d.getDate() + n); const [h, m, s] = hms.split(':').map(Number); d.setHours(h, m, s, 0); return d.toISOString() };
  const monday = () => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return d.toLocaleDateString('en-CA') };
  const resolve = v => typeof v === 'string'
    ? v.replace(/\{W\}/g, monday).replace(/\{D([+-]?\d+)\}/g, (_, n) => day(+n)).replace(/\{I([+-]?\d+) (\d\d:\d\d:\d\d)\}/g, (_, n, t) => instant(+n, t))
    : Array.isArray(v) ? v.map(resolve) : v && typeof v === 'object' ? Object.fromEntries(Object.entries(v).map(([k, x]) => [resolve(k), resolve(x)])) : v;
  const text = () => Object.fromEntries(OUT.map(k => [k, JSON.stringify(state[k])]));
  const rest = d => { try { return !!ecoRestDay(d) } catch (e) { return null } };
  const FN = {
    dhSeed: s => dhSeed(s),
    dhRng: (seed, n) => { const r = dhRng(seed), out = []; for (let i = 0; i < n; i++) out.push(r()); return out },
    dhMakeQuest: (tpl, date, slot, nonce) => dhMakeQuest(QUEST_LIBRARY.find(x => x.id === tpl), date, slot, nonce),
    dhRecentAttrXP: days => dhRecentAttrXP(days),
    dhWeakestAttr: () => dhWeakestAttr(),
    dhDraw: (date, nonce, slot, taken) => { const t = dhDraw(date, nonce, slot, taken); return t ? t.id : null },
    dhEnsure: () => dhEnsure(),
    dhCards: () => dhCards(),
    dhRerollCost: () => dhRerollCost(),
    dhReroll: slot => dhReroll(slot),
    assign: (field, value) => { state[field] = value; return null }, // the owner changing something between two calls (a class switch)
  };
  window.__dh = {
    setup(given) {
      state = Object.assign(freshState(), { xpSchemaV2: true });
      for (const k of IN) delete state[k];
      Object.assign(state, resolve(clone(given || {})));
      const s = {};
      for (const k of IN) if (state[k] !== undefined) s[k] = clone(state[k]);
      return s;
    },
    resolve: v => resolve(v),
    call(c) {
      if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
      const args = resolve(clone(c.args || [])), L = luckToday(), today = getToday(), r = {};
      r[today] = rest(today);
      if (c.fn === 'dhDraw' && typeof args[0] === 'string') r[args[0]] = rest(args[0]);
      const ctx = { today, luck: L && typeof L.score === 'number' ? L.score : null, rest: r };
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
      return { call: { fn: c.fn, args, ...(c.at ? { at: c.at } : {}), ctx }, out };
    },
  };
}

/* ---------- recording ---------- */
async function openPage(browser, file, zone) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: zone, locale: 'en-US' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date(BOOT) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof dhEnsure === 'function' && typeof dhReroll === 'function' && typeof ecoRestDay === 'function' && typeof luckToday === 'function', null, { timeout: 120000 });
  await p.clock.pauseAt(new Date('2026-10-28T12:00:00.000Z')); // the boot's own timers have run; from here time moves only when a case moves it
  await p.evaluate(pageRunner, { IN, OUT });
  return p;
}

async function record(p, c) {
  await p.clock.setSystemTime(new Date(c.now));
  const state = await p.evaluate(s => window.__dh.setup(s), c.state);
  const calls = [], outs = [];
  for (const x of c.calls) {
    await p.clock.setSystemTime(new Date(x.at || c.now));
    const r = await p.evaluate(x => window.__dh.call(x), x);
    calls.push(r.call); outs.push(r.out);
  }
  return { id: c.id, family: c.family, rule: c.rule, input: { now: c.now, zone: c.zone, state, calls }, output: { calls: outs } };
}

/* Records every case in the built page `file`; resolves to the list of cases. */
async function generate(file, log = () => { }) {
  const { chromium } = require('playwright');
  const cases = defineCases(), browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const pages = {}, out = [], page = async zone => pages[zone] || (pages[zone] = await openPage(browser, file, zone));
    for (const c of cases) {
      out.push(JSON.parse(JSON.stringify(await record(await page(c.zone), c)))); // as the file will hold it
      if (out.length % 200 === 0) log(out.length + ' of ' + cases.length + ' cases recorded');
    }
    const errors = Object.values(pages).flatMap(p => p.errors);
    if (errors.length) throw new Error('the page threw while recording: ' + errors.slice(0, 3).join(' | '));
    return out;
  } finally { await browser.close() }
}

/* one case per line, so a change to the page shows up in a diff as the cases it changed */
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
