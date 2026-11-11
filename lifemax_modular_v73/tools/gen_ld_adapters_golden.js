/* Golden cases for the Life Director host adapter (WP-LD11): the page's LifeMaxHost (engine.js:2355-2620), the surface through which Life
   Director reads quests, plan days, activities and meals and pays XP and quest bounties, recorded from the real page in Chromium, so that
   the native adapters (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/hostadapters) are accepted only when, for
   the same save, clock, device zone and calls, they give the same answers and leave the same economy behind. Regenerate when the host or
   the economy changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_adapters_golden.js [built-page.html]
   Output: tests/fixtures/ld-adapters-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-adapters-golden.test.js records every case again and fails when the page and the file disagree. Cases: tools/ld_adapters_cases.js.

   File: {header, cases}. header.base is the fresh save every case's state is written against (the economy golden's method,
   tools/gen_economy_golden.js). A case is {id, family, rule, input, output}:
     input   {now, zone, state, calls}: now the clock (ISO, frozen while the case runs), zone the device's time zone, state the save fields
             that differ from header.base (as load() leaves them: rules merged, attrXP and skillXP rebuilt from the ledger), calls run in order:
             {fn, args, at?, env?}. fn is LifeMaxHost.<name> (LifeMaxHost._test.<name> for its private helpers), LifeMaxMeals.resolveScheduled,
             or mealFor(block, tz): the reference logic.js:1076-1081 builds for a meal block (MEAL_REF below), then resolveScheduled.
             A case definition may also name plan days to tick (tick: [key]): the set-up ticks every required item of those days, as the
             Plan screen does, before the state is recorded, so the recorded state already holds the ticks; and meals: {log, skip} ([day,
             index] pairs) logs or skips those Smart Diet meals as the Food screen does (sdLogMeal, the skip marks).
             at moves the clock for that call; env.failSave 'storage' or 'readonly' makes the save fail as the economy golden does.
     output  {returns, state, deleted?, toasts?, outside?}: each call's return value (null for none, {threw: <error name>} if it threw),
             every economy field (FIELDS) the calls changed with its value after, the fields removed, the messages shown, and outside: what
             the page's own plan and meal code answered the host while it ran (planDay(key) as the page returns it, dayDone(key), and
             sdDay(day) reduced to what LifeMaxMeals reads of it: each item's slot, name, kcal, logged, skipped; a call that threw answers
             null or false), which the native adapter is given through its ports because that content is not native yet.

   What is held still: the clock is Playwright's, paused; crypto.randomUUID counts; Math.random restarts at every call; toasts captured. The
   Daily Hand neither deals nor checks its cards (QUEST_LIBRARY hidden and dhBusy on, as the economy golden does), except: the library is
   visible during retiredUntimedQuest and activityDescription, which read it, and the verify family lets the Hand check its cards after the
   award's save (SAVE-2). Every case is recorded twice on fresh state and must give the same answer both times. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES, T0 } = require('./ld_adapters_cases.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-adapters-golden.json.gz');
const BOOT = '2026-10-28T06:00:00.000Z';
/* the economy's save fields (the economy golden's FIELDS) and the host's other reads */
const FIELDS = ['activities', 'attrXP', 'skillXP', 'restedXP', 'sparks', 'perk', 'perksOwned', 'attrStars', 'ascended', 'decayTotal',
  'decayApplied', 'decayLog', 'decay', 'legacyCharXPBonus', 'xpSchemaV2', 'customRules', 'quests', 'questDone', 'restDays', 'restTokens',
  'restBoughtAt', 'streakShields', 'shieldTick', 'shieldMiss', 'shieldDays', 'shieldMilestone', 'shieldBoughtAt', 'themesOwned', 'theme',
  'toysBought', 'nutriDone', 'careDay', 'mealsMeta', 'foodLog', 'money', 'actXP', 'plan', 'health', 'devMode'];
const EXTRA = ['created', 'name', 'dhMig', 'dailyDraw'];
/* logic.js:1076-1078, scheduledMealRef, as the page builds the reference it hands Smart Diet; the web test checks logic.js still holds it */
const MEAL_REF = "const start = b.mealStartEpoch || b.startEpoch;\n    return { title: b.title, startEpoch: start, day: U.ymdKey(U.localParts(start, tz())) };";

/* ---------- the page side ---------- */
function hostRunner(args) {
  const { fields, mealRef } = args;
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  const realSetItem = Storage.prototype.setItem, library = window.QUEST_LIBRARY;
  let uuid = 0, seed = 1, toasts = [], before = null, outside = null;
  window.toast = m => { toasts.push(String(m)) };
  window.confirm = () => true; window.prompt = () => null; window.alert = () => { };
  crypto.randomUUID = () => '00000000-0000-4000-8000-' + String(++uuid).padStart(12, '0');
  Math.random = () => (seed = seed * 16807 % 2147483647) / 2147483647;
  /* what the host asks the page's plan and meal code, logged while a call runs */
  const realPlanDay = planDay, realDayDone = dayDone, realSdDay = sdDay;
  const note = (kind, key, value) => { if (!outside) return; outside[kind] = outside[kind] || {}; if (!(String(key) in outside[kind])) outside[kind][String(key)] = value };
  planDay = function (key) { let v; try { v = realPlanDay.apply(this, arguments) } catch (e) { note('planDay', key, null); throw e } note('planDay', key, v === undefined ? null : clone(v)); return v };
  dayDone = function (key) { let v; try { v = realDayDone.apply(this, arguments) } catch (e) { note('dayDone', key, false); throw e } note('dayDone', key, !!v); return v };
  sdDay = function (day) {
    let v; try { v = realSdDay.apply(this, arguments) } catch (e) { note('sdDay', day, null); throw e }
    note('sdDay', day, v && Array.isArray(v.items) ? { items: v.items.map(it => ({ slot: it.meal.slot, name: it.meal.name, kcal: it.tot.kcal, logged: !!it.logged, skipped: !!it.skipped })) } : null);
    return v;
  };
  const mealFor = new Function('b', 'tz0', 'const U = LifeDirectorEngine.util, tz = () => tz0; return LifeMaxMeals.resolveScheduled((() => {' + mealRef + '})());');
  const LIBRARY_CALLS = ['LifeMaxHost.retiredUntimedQuest', 'LifeMaxHost.activityDescription'];
  const pick = keys => { const o = {}; for (const k of keys) if (state[k] !== undefined) o[k] = clone(state[k]); if (o.health) delete o.health.snap; return o };
  function fresh(verify) {
    window.QUEST_LIBRARY = undefined;
    saveReadOnly = ''; saveReadOnlyTold = false; lmaHealthBusy = false; shBusy = false;
    dhBusy = !verify;
    state = Object.assign(freshState(), { xpSchemaV2: true, quests: [] });
    rules = structuredClone(DEFAULT_RULES); state.customRules = rules;
  }
  function draw() { const real = save; save = () => true; try { renderAll() } catch { /* a malformed save cannot be drawn */ } finally { save = real } }
  function call(c) {
    const env = c.env || {}, undo = [];
    seed = 1; shBusy = false; lmaHealthBusy = false;
    if (env.failSave === 'storage') { Storage.prototype.setItem = function () { throw new Error('quota') }; undo.push(() => { Storage.prototype.setItem = realSetItem }) }
    if (env.failSave === 'readonly') { saveReadOnly = 'read-only'; saveReadOnlyTold = true; undo.push(() => { saveReadOnly = ''; saveReadOnlyTold = false }) }
    if (LIBRARY_CALLS.includes(c.fn)) { window.QUEST_LIBRARY = library; undo.push(() => { window.QUEST_LIBRARY = undefined }) }
    const log = /readPlanSessions|isPlanDayDone|activityDescription|resolveScheduled|mealFor/.test(c.fn);
    if (log) outside = outside || {};
    const was = outside; if (!log) outside = null;
    let out;
    try {
      const f = c.fn === 'mealFor' ? mealFor : (0, eval)(c.fn); // LifeMaxHost is a top-level const of the page's script, not a window member
      out = f(...(c.args || []));
    } catch (e) { out = { threw: String((e && e.name) || e) } } finally { undo.reverse().forEach(f => f()); if (!log) outside = was }
    return out === undefined ? null : clone(out);
  }
  const host = {
    base() { fresh(false); draw(); return pick(fields) },
    setup(spec) {
      fresh(spec.verify);
      const s = clone(spec.state || {});
      if (s.customRules) { rules = Object.assign(structuredClone(DEFAULT_RULES), s.customRules); delete s.customRules }
      Object.assign(state, s); state.customRules = rules;
      /* tick: plan days whose every required item is ticked done, as the Plan screen leaves them (the state recorded is the result) */
      for (const key of spec.tick || []) {
        const d = realPlanDay(key); if (!d || !Array.isArray(d.items)) continue;
        const c = state.plan.checks[key] = state.plan.checks[key] || {}; c.s = c.s || {};
        for (const it of d.items) if (!it.opt) c.s[it.k] = Array(it.n).fill(true);
      }
      /* meals: Smart Diet meals logged or skipped as the Food screen does it ([day, index] pairs), recorded in the state */
      seed = 1;
      for (const [day, i] of (spec.meals && spec.meals.log) || []) sdLogMeal(day, i, true);
      for (const [day, i] of (spec.meals && spec.meals.skip) || []) { const S = sdS(); S.skip = S.skip || {}; (S.skip[day] = S.skip[day] || {})[i] = true }
      if (Array.isArray(state.activities) && state.activities.length) { rebuildAttrXP(); rebuildSkillXP() }
      const busy = dhBusy; dhBusy = true;
      try { draw() } finally { dhBusy = busy }
      uuid = 0; toasts = []; outside = null; before = pick(fields);
      return before;
    },
    call,
    finish() {
      const after = pick(fields), changed = {}, deleted = [];
      for (const k of fields) if (JSON.stringify(after[k]) !== JSON.stringify(before[k])) { if (after[k] === undefined) deleted.push(k); else changed[k] = after[k] }
      const out = { state: changed, deleted, toasts, outside };
      outside = null;
      return out;
    },
  };
  window.__ldHost = host;
}

/* ---------- recording ---------- */
async function openPage(browser, file, zone) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: zone, locale: 'en-US' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date(BOOT) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof ecoAward === 'function' && typeof LifeMaxHost === 'object' && typeof LifeMaxMeals === 'object' && typeof sdDay === 'function', null, { timeout: 120000 });
  await p.clock.pauseAt(new Date(T0));
  await p.evaluate(hostRunner, { fields: FIELDS.concat(EXTRA), mealRef: MEAL_REF });
  return p;
}

async function runOnce(p, c) {
  await p.clock.setSystemTime(new Date(c.now));
  const spec = { state: c.state, verify: c.family === 'verify', tick: c.tick, meals: c.meals };
  const before = await p.evaluate(s => window.__ldHost.setup(s), spec), returns = [];
  for (const x of c.calls) {
    await p.clock.setSystemTime(new Date(x.at || c.now));
    returns.push(await p.evaluate(x => window.__ldHost.call(x), x));
  }
  return { before, returns, ...await p.evaluate(() => window.__ldHost.finish()) };
}

async function record(p, c, base) {
  const a = await runOnce(p, c), b = await runOnce(p, c);
  if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error('case ' + c.id + ' gave two different answers');
  const state = {};
  for (const [k, v] of Object.entries(a.before)) if (JSON.stringify(v) !== JSON.stringify(base[k])) state[k] = v;
  const deleted = Object.keys(base).filter(k => !(k in a.before));
  const input = { now: c.now, zone: c.zone, state, ...(deleted.length ? { absent: deleted } : {}), calls: c.calls };
  const output = { returns: a.returns, state: a.state };
  if (a.deleted.length) output.deleted = a.deleted;
  if (a.toasts.length) output.toasts = a.toasts;
  if (a.outside) output.outside = a.outside;
  return JSON.stringify({ id: c.id, family: c.family, rule: c.rule, input, output });
}

/* Records every case in the built page `file`; resolves to {lines, base, browser}. */
async function generate(file, log = () => { }) {
  const { chromium } = require('playwright');
  const cases = defineCases(), browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const pages = {}, lines = [], page = async zone => pages[zone] || (pages[zone] = await openPage(browser, file, zone));
    const utc = await page('UTC');
    await utc.clock.setSystemTime(new Date(T0));
    const base = await utc.evaluate(() => window.__ldHost.base());
    for (const c of cases) {
      lines.push(await record(await page(c.zone), c, base));
      if (lines.length % 200 === 0) log(lines.length + ' of ' + cases.length + ' cases recorded');
    }
    const errors = Object.values(pages).flatMap(p => p.errors);
    if (errors.length) throw new Error('the page threw while recording: ' + errors.slice(0, 3).join(' | '));
    return { lines, base, browser: browser.version() };
  } finally { await browser.close() }
}

const header = (browser, base) => ({ about: 'Life Director host adapter golden cases; see tools/gen_ld_adapters_golden.js', recordedIn: 'Chromium ' + browser, base });
const serialize = (head, lines) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + lines.join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));

module.exports = { generate, defineCases, FAMILIES, FIELDS, MEAL_REF, serialize, header, readFile, readLines, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file, m => console.log(m)).then(({ lines, base, browser }) => {
    const text = serialize(header(browser, base), lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of defineCases()) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1) });
}
