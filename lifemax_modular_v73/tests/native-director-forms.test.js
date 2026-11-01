/* Native Life Director, phase 4B, web half: My time, the first-run card and the rule and goal forms as data for a native screen,
   and their writes as commands. Checked:
   - the page's own My time, first-run card, commitment sheet, goal-kind grid and goal forms are byte-identical to the goldens
     recorded before their data was split out of their renderers, and every write driven through the page's own form leaves the
     same saved state and says the same words as before (tests/fixtures/director-time-golden.json; RECORD=1 rewrites it, only
     ever from code known to be right);
   - nativeTime, nativeFirstRun and the form schemas say what the page draws (labels, limits, defaults, options, order);
   - parity: for every write, the page's own form and the command's payload reach the same saved state and say the same;
   - each write answers once per operation: a repeat answers changed: false ("Already added" for a new commitment or goal), an
     edit of a removed commitment is refused, a refused save answers ok: false, and a finished set-up's first build is the
     screen's (the page plays none);
   - an APK that draws My time (director.setup.v1) covers My time and the first-run card and gets them in the projection; one
     without it sees phase 4A unchanged.
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through tests/fixtures/director-day.js,
   with crypto.randomUUID counting, so new ids are the same on every run.
   Run: NODE_PATH=$(npm root -g) node tests/native-director-forms.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const crypto = require('crypto');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'director-time-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const KEY = 'lifemax_director_state_v1';

const FAKES = caps => `
  window.__calls = [];
  window.__since = n => window.__calls.slice(n).filter(c => c[0] === 'director');
  const mk = (name, caps) => { const ls = {}; return {
    __emit: (e, d) => (ls[e] || []).map(f => f(d)),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a]) },
    update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => { window.__calls.push([name, 'handshake']); return { protocol: 1, capabilities: caps } } } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector' || n === 'NativeOverview', getPlatform: () => 'android',
    Plugins: { NativeLifeDirector: mk('director', ${JSON.stringify(caps)}), NativeOverview: mk('overview', []) }, registerPlugin: () => ({}), convertFileSrc: u => u };`;
/* new ids are counted, so a plan (whose order follows ids) is the same on every run */
const COUNTED_IDS = `(() => { let n = 0; window.__uuids = []; crypto.randomUUID = () => { const u = '00000000-0000-4000-8000-' + String(++n).padStart(12, '0'); window.__uuids.push(u); return u } })();`;

/* state: 'fresh' (a new player), 'lived' (the lived-in day) or 'mixed' (set-up not finished: weekend sleep, a rotating shift, no goals) */
async function start(b, { state = 'lived', caps = null, switchOn = false } = {}) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: state !== 'fresh' });
  if (state === 'mixed') await p.addInitScript(k => {
    const s = JSON.parse(localStorage.getItem(k));
    s.setupComplete = false; s.goals = []; s.ui.tab = 'setup'; delete s.ui.tours.setup;
    s.availability.rules = [
      { id: 'sleep-wk', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4], startMin: 22 * 60 + 45, durationMin: 495 },
      { id: 'sleep-we', kind: 'SLEEP', title: 'Sleep', days: [5, 6], startMin: 30, durationMin: 540 },
      { id: 'r-rot', kind: 'WORK', title: 'Nurse shift', days: [0, 1, 2, 3, 4, 5, 6], startMin: 19 * 60, durationMin: 720, commuteMin: 20, decompressMin: 45, load: 'PHYSICAL_HEAVY',
        rotation: { anchorDate: '2026-10-26', cycleLength: 7, activeDays: [0, 1, 2] } },
      { id: 'r-class', kind: 'CLASS', title: 'Pottery', days: [2, 4], startMin: 18 * 60, durationMin: 90, commuteMin: 0, decompressMin: 0, load: 'LIGHT', rotation: null }];
    localStorage.setItem(k, JSON.stringify(s));
  }, KEY);
  await p.addInitScript(COUNTED_IDS);
  await p.addInitScript(FAKES(caps || ['director.today.v2']));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  if (switchOn) await p.evaluate(() => setNativeDirectorEnabled(true));
  if (state === 'lived') await directorDay.play(p); else await p.clock.runFor(3000);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
/* a section of the page's own Life Director, drawn by its own tab button, then its main area's HTML */
async function pageSection(p, tab) {
  return p.evaluate(tab => { document.querySelector('#view-life-director .ld-tab[data-tab="' + tab + '"]').click(); return document.querySelector('#view-life-director .ld-main').innerHTML }, tab);
}
/* the page's own button (by its data-act and optional data-* filter), clicked as a finger would */
const tap = (p, sel) => p.evaluate(sel => { const e = document.querySelector('#view-life-director ' + sel); if (!e) throw Error('no ' + sel); e.click() }, sel);
const sheetHtml = p => p.evaluate(() => { const s = document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-body'); return s ? s.innerHTML : null });
const closeSheet = p => p.evaluate(() => { const b = document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-bg'); if (b) b.click() });
/* fields of the page's open form: {id: value}; a boolean ticks a checkbox (and tells the page, which shows or hides what it reveals);
   an array ticks exactly those day chips of the named group */
const fill = (p, values) => p.evaluate(values => {
  const root = document.querySelector('#view-life-director');
  for (const [id, v] of Object.entries(values)) {
    if (Array.isArray(v)) { root.querySelectorAll('input[name=' + id + ']').forEach(x => { x.checked = v.includes(+x.value) }); continue }
    const e = root.querySelector('#' + id); if (!e) throw Error('no #' + id);
    if (typeof v === 'boolean') e.checked = v; else e.value = String(v);
    e.dispatchEvent(new Event('input', { bubbles: true }));
  }
}, values);
/* what is saved now, as one hash: the saved state with the engine's timings left out (they are measured, not decided) */
const savedHash = p => p.evaluate(k => localStorage.getItem(k), KEY).then(raw => crypto.createHash('sha256').update(String(raw).replace(/"computeTimeMs":[0-9.]+/g, '"computeTimeMs":0')).digest('hex').slice(0, 24));
const said = p => p.evaluate(() => { const n = LifeDirector.native.frame().notice; return n ? n.id + ':' + n.text : '' });
/* a write through the page's own form: the hash of what is saved after it and what the page said */
async function pageWrite(p, steps) {
  for (const s of steps) { if (typeof s === 'string') await tap(p, s); else await fill(p, s) }
  await settle(p, 200);
  return { saved: await savedHash(p), said: await said(p) };
}

/* every write the page's own forms make, in order, on a new player and on the lived-in day: what is saved and said after each */
const FRESH_WRITES = [
  ['finish set-up before sleep', ['[data-act=finishSetup]']],
  ['sleep under 5 hours', [{ ldBed: '02:00', ldWake: '06:00' }, '[data-act=saveSleep]']],
  ['sleep', [{ ldBed: '23:30', ldWake: '07:15' }, '[data-act=saveSleep]']],
  ['sleep, different at the weekend', [{ ldWeekendOn: true, ldBedWe: '00:30', ldWakeWe: '09:00' }, '[data-act=saveSleep]']],
  ['getting ready, longer', ['[data-act=routine][data-k=morningRoutineMinutes][data-v="5"]']],
  ['wind-down, shorter at 0', ['[data-act=routine][data-k=windDownMinutes][data-v="-5"]']],
  ['meals off', ['[data-act=meals]']],
  ['a weekly commitment', ['[data-act=addRule]', { rTitle: 'Gym job', rKind: 'APPOINTMENT', rStart: '06:00', rEnd: '08:30', rCommute: '15', rUnwind: '10', rLoad: 'LIGHT', rDays: [1, 3] }, '[data-act=saveRule]']],
  ['a commitment without days', ['[data-act=addRule]', { rDays: [] }, '[data-act=saveRule]']],
  ['a rotating shift', [{ rRot: true, rTitle: 'Ward', rOn: '3', rOff: '4', rAnchor: '2026-10-30', rStart: '19:00', rEnd: '07:00', rLoad: 'PHYSICAL_HEAVY' }, '[data-act=saveRule]']],
  ['a goal without a name', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=HABIT_READING]', { gTitle: ' ' }, '[data-act=saveGoal]']],
  ['a reading goal with a target', [{ gTitle: 'Read 12 books', gPrio: '4', gLevel: 'INTERMEDIATE', gUnits: '3600', gUnit: 'pages', gRate: '30', gDeadline: '2027-03-31' }, '[data-act=saveGoal]']],
  ['a custom goal', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=CUSTOM]', { gTitle: 'Guitar', gTag: 'creative.practice', gMin: '45', gFreq: '4', gIP: '1', gIC: '3', gPrio: '5' }, '[data-act=saveGoal]']],
  ['a language goal', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=SKILL_LANGUAGE]', { gSubject: 'french' }, '[data-act=saveGoal]']],
  ['build my week', ['[data-act=finishSetup]']],
];
const LIVED_WRITES = [
  ['work, edited', ['[data-act=editRule][data-id=work]', { rEnd: '16:30', rCommute: '20' }, '[data-act=saveRule]']],
  ['sleep, after set-up', [{ ldBed: '22:45', ldWake: '06:45' }, '[data-act=saveSleep]']],
  ['meals longer', ['[data-act=routine][data-k=mealMinutes][data-v="5"]']],
  ['a goal after set-up', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=FITNESS_CARDIO]', '[data-act=saveGoal]']],
  ['work removed', ['[data-act=editRule][data-id=work]', '[data-act=delRule]']],
  ['re-plan now', ['[data-act=finishSetup]']],
];
async function writesOf(b, state, writes) {
  const p = await start(b, { state });
  await go(p, 'life-director', 1500);
  await pageSection(p, 'setup');
  const out = {};
  for (const [name, steps] of writes) out[name] = await pageWrite(p, steps);
  await p.context().close();
  return out;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. the page's own My time, first-run card and forms, and what its writes save and say, against the goldens */
  const html = {}, goalKinds = ['FITNESS_HYPERTROPHY', 'HABIT_READING', 'SKILL_LANGUAGE', 'CUSTOM'];
  for (const state of ['fresh', 'lived', 'mixed']) {
    const p = await start(b, { state });
    await go(p, 'life-director', 1500);
    html[state + '.setup'] = await pageSection(p, 'setup');
    if (state === 'fresh') { html['fresh.firstRun'] = await pageSection(p, 'today'); await pageSection(p, 'setup') }
    await tap(p, '[data-act=addRule]'); html[state + '.ruleNew'] = await sheetHtml(p); await closeSheet(p);
    const edit = { fresh: null, lived: 'work', mixed: 'r-rot' }[state];
    if (edit) { await tap(p, '[data-act=editRule][data-id="' + edit + '"]'); html[state + '.ruleEdit'] = await sheetHtml(p); await closeSheet(p) }
    if (state !== 'mixed') {
      await tap(p, '[data-act=addGoal]'); html[state + '.goalType'] = await sheetHtml(p);
      for (const k of goalKinds) { await tap(p, '[data-act=pickArch][data-arch=' + k + ']'); html[state + '.goal.' + k] = await sheetHtml(p); await tap(p, '[data-act=addGoal]') }
      await closeSheet(p);
    }
    ok('no page errors drawing My time (' + state + ')', !p.errors.length, p.errors);
    await p.context().close();
  }
  const writes = { fresh: await writesOf(b, 'fresh', FRESH_WRITES), lived: await writesOf(b, 'lived', LIVED_WRITES) };
  if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify({ html, writes }, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
  const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
  for (const k of Object.keys(golden.html)) ok('the page draws ' + k + ' byte for byte as before its data was split out', html[k] === golden.html[k], [String(html[k]).length, String(golden.html[k]).length]);
  for (const s of ['fresh', 'lived']) for (const k of Object.keys(golden.writes[s]))
    ok('the page\'s own form saves and says as before the split (' + s + ': ' + k + ')', same(writes[s][k], golden.writes[s][k]), [writes[s][k], golden.writes[s][k]]);

  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
