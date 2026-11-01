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
const COUNTED_IDS = `(() => { let n = 0; window.__uuids = []; crypto.randomUUID = () => { const u = String(++n).padStart(8, '0') + '-0000-4000-8000-000000000000'; window.__uuids.push(u); return u } })();`;

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

/* the screen's command, as the native side sends it, and the reply that comes back with the next update */
async function command(p, command, payload) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, 400);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null, calls: __since(n).map(c => c[1]) } }, n);
}

/* a form's values by key, as a native screen starts its draft from the schema */
const vals = form => Object.fromEntries(form.fields.concat(form.fold ? form.fold.fields : []).map(f => [f.key, f.value]));
/* every write the page's own forms make, in order, on a new player and on the lived-in day (what is saved and said after each is the
   golden), with the command a screen sends for the same change: (S: the screen's data now, u: the id the page drew) => [name, payload] */
const FRESH_WRITES = [
  ['finish set-up before sleep', ['[data-act=finishSetup]'], () => ['finishSetup', {}]],
  ['sleep under 5 hours', [{ ldBed: '02:00', ldWake: '06:00' }, '[data-act=saveSleep]'], () => ['sleepSave', { bed: 120, wake: 360, weekend: null }]],
  ['sleep', [{ ldBed: '23:30', ldWake: '07:15' }, '[data-act=saveSleep]'], () => ['sleepSave', { bed: 1410, wake: 435, weekend: null }]],
  ['sleep, different at the weekend', [{ ldWeekendOn: true, ldBedWe: '00:30', ldWakeWe: '09:00' }, '[data-act=saveSleep]'], () => ['sleepSave', { bed: 1410, wake: 435, weekend: { bed: 30, wake: 540 } }]],
  ['getting ready, longer', ['[data-act=routine][data-k=morningRoutineMinutes][data-v="5"]'], S => ['routine', { key: 'morningRoutineMinutes', value: S.time.routines.rows[0].value + 5 }]],
  ['wind-down, shorter at 0', ['[data-act=routine][data-k=windDownMinutes][data-v="-5"]'], S => ['routine', { key: 'windDownMinutes', value: S.time.routines.rows[1].value - 5 }]],
  ['meals off', ['[data-act=meals]'], () => ['meals', { on: false }]],
  ['a weekly commitment', ['[data-act=addRule]', { rTitle: 'Gym job', rKind: 'APPOINTMENT', rStart: '06:00', rEnd: '08:30', rCommute: '15', rUnwind: '10', rLoad: 'LIGHT', rDays: [1, 3] }, '[data-act=saveRule]'],
    (S, u) => ['ruleSave', Object.assign(vals(S.rule), { op: u, title: 'Gym job', kind: 'APPOINTMENT', start: 360, end: 510, commute: 15, unwind: 10, load: 'LIGHT', days: [1, 3] })]],
  ['a commitment without days', ['[data-act=addRule]', { rDays: [] }, '[data-act=saveRule]'], S => ['ruleSave', Object.assign(vals(S.rule), { op: 'never-drawn', days: [] })]],
  ['a rotating shift', [{ rRot: true, rTitle: 'Ward', rOn: '3', rOff: '4', rAnchor: '2026-10-30', rStart: '19:00', rEnd: '07:00', rLoad: 'PHYSICAL_HEAVY' }, '[data-act=saveRule]'],
    (S, u) => ['ruleSave', Object.assign(vals(S.rule), { op: u, days: [], rotating: true, title: 'Ward', on: 3, off: 4, anchor: '2026-10-30', start: 1140, end: 420, load: 'PHYSICAL_HEAVY' })]],
  ['a goal without a name', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=HABIT_READING]', { gTitle: ' ' }, '[data-act=saveGoal]'],
    S => ['goalAdd', Object.assign(vals(S.goal.HABIT_READING), { arch: 'HABIT_READING', op: 'never-drawn', title: ' ' })]],
  ['a reading goal with a target', [{ gTitle: 'Read 12 books', gPrio: '4', gLevel: 'INTERMEDIATE', gUnits: '3600', gUnit: 'pages', gRate: '30', gDeadline: '2027-03-31' }, '[data-act=saveGoal]'],
    (S, u) => ['goalAdd', Object.assign(vals(S.goal.HABIT_READING), { arch: 'HABIT_READING', op: u, title: 'Read 12 books', priority: '4', level: 'INTERMEDIATE', units: 3600, unit: 'pages', rate: 30, deadline: '2027-03-31' })]],
  ['a custom goal', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=CUSTOM]', { gTitle: 'Guitar', gTag: 'creative.practice', gMin: '45', gFreq: '4', gIP: '1', gIC: '3', gPrio: '5' }, '[data-act=saveGoal]'],
    (S, u) => ['goalAdd', Object.assign(vals(S.goal.CUSTOM), { arch: 'CUSTOM', op: u, title: 'Guitar', tag: 'creative.practice', minutes: 45, perWeek: 4, physical: 1, mental: 3, priority: '5' })]],
  ['a language goal', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=SKILL_LANGUAGE]', { gSubject: 'french' }, '[data-act=saveGoal]'],
    (S, u) => ['goalAdd', Object.assign(vals(S.goal.SKILL_LANGUAGE), { arch: 'SKILL_LANGUAGE', op: u, subject: 'french' })]],
  ['build my week', ['[data-act=finishSetup]'], () => ['finishSetup', {}]],
];
const LIVED_WRITES = [
  ['work, edited', ['[data-act=editRule][data-id=work]', { rEnd: '16:30', rCommute: '20' }, '[data-act=saveRule]'], S => ['ruleSave', Object.assign(vals(S.work), { id: 'work', end: 990, commute: 20 })]],
  ['sleep, after set-up', [{ ldBed: '22:45', ldWake: '06:45' }, '[data-act=saveSleep]'], () => ['sleepSave', { bed: 1365, wake: 405, weekend: null }]],
  ['meals longer', ['[data-act=routine][data-k=mealMinutes][data-v="5"]'], S => ['routine', { key: 'mealMinutes', value: S.time.routines.meals.value + 5 }]],
  ['a goal after set-up', ['[data-act=addGoal]', '[data-act=pickArch][data-arch=FITNESS_CARDIO]', '[data-act=saveGoal]'], (S, u) => ['goalAdd', Object.assign(vals(S.goal.FITNESS_CARDIO), { arch: 'FITNESS_CARDIO', op: u })]],
  ['work removed', ['[data-act=editRule][data-id=work]', '[data-act=delRule]'], () => ['ruleRemove', { id: 'work' }]],
  ['re-plan now', ['[data-act=finishSetup]'], () => ['finishSetup', {}]],
];
const drawn = {}; // the id the page drew for each write, for the same write as a command
async function writesOf(b, state, writes) {
  const p = await start(b, { state });
  await go(p, 'life-director', 1500);
  await pageSection(p, 'setup');
  const out = {}; drawn[state] = {};
  for (const [name, steps] of writes) { out[name] = await pageWrite(p, steps); drawn[state][name] = await p.evaluate(() => window.__uuids.slice(-1)[0] || '') }
  ok('no page errors writing through the page\'s own forms (' + state + ')', !p.errors.length, p.errors);
  await p.context().close();
  return out;
}
/* what a screen has to draw from: My time, and the forms the writes above start from */
const screenData = p => p.evaluate(() => {
  const ld = LifeDirector.native;
  return { time: ld.time(), rule: ld.sheet('rule', ''), work: ld.sheet('rule', 'work'), goal: Object.fromEntries(['HABIT_READING', 'CUSTOM', 'SKILL_LANGUAGE', 'FITNESS_CARDIO'].map(a => [a, ld.sheet('goalForm', a)])) };
});
const doCommand = (p, name, payload) => p.evaluate(([n, x]) => NativeDirectorProjection.commands.do(Object.assign({ name: n }, x)), [name, payload]);
/* the page's open form against its schema, control by control (ids: the page's control for each key): order, label, value, limits,
   options, what the check reveals and hides, and the sheet's words */
const formAgainstPage = (p, kind, id, ids) => p.evaluate(([kind, id, ids]) => {
  const F = LifeDirector.native.sheet(kind, id), body = document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-body'), bad = [];
  const hm = m => String(Math.floor(m / 60) % 24).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0'), J = JSON.stringify;
  const ctl = f => f.kind === 'days' ? body.querySelector('input[name=' + ids[f.key] + ']') : body.querySelector('#' + ids[f.key]);
  const fields = F.fields.concat(F.fold ? F.fold.fields : []);
  let last = null;
  for (const f of fields) {
    const el = ctl(f);
    if (!el) { bad.push(f.key + ': no control'); continue }
    if (last && !(last.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING)) bad.push(f.key + ': out of order'); last = el;
    const label = f.kind === 'days' ? el.closest('#rDaysWrap').querySelector('.ld-lbl').textContent : f.kind === 'check' ? el.parentElement.textContent.trim() : el.closest('label').firstChild.textContent;
    if (label !== f.label) bad.push(f.key + ': label ' + label);
    const v = f.kind === 'check' ? el.checked : f.kind === 'days' ? [...body.querySelectorAll('input[name=' + ids[f.key] + ']:checked')].map(x => +x.value) : el.value;
    if (J(v) !== J(f.kind === 'time' ? hm(f.value) : f.kind === 'number' ? (f.value == null ? '' : String(f.value)) : f.value)) bad.push(f.key + ': value ' + J(v));
    if (f.kind === 'number') { for (const a of ['min', 'max']) if ((el.getAttribute(a) == null ? null : +el.getAttribute(a)) !== f[a]) bad.push(f.key + ': ' + a); if ((+el.getAttribute('step') || 1) !== f.step) bad.push(f.key + ': step') }
    if ((f.kind === 'number' || f.kind === 'text') && (el.getAttribute('placeholder') || '') !== f.placeholder) bad.push(f.key + ': placeholder');
    if (f.kind === 'text' && (el.maxLength > 0 ? el.maxLength : null) !== f.maxLength) bad.push(f.key + ': maxLength');
    if (f.kind === 'choice' && J([...el.options].map(o => [o.value, o.textContent])) !== J(f.options.map(o => [o.value, o.label]))) bad.push(f.key + ': options');
    if (f.kind === 'days' && J([...body.querySelectorAll('input[name=' + ids[f.key] + ']')].map(x => [+x.value, x.nextElementSibling.textContent])) !== J(f.options.map(o => [o.value, o.label]))) bad.push(f.key + ': day chips');
    if (f.kind === 'check') {
      const hidden = k => !!ctl(fields.find(x => x.key === k)).closest('[hidden]');
      if (f.reveals.some(k => hidden(k) === f.value) || f.hides.some(k => hidden(k) !== f.value)) bad.push(f.key + ': reveals and hides');
    }
  }
  const t = s => { const e = body.querySelector(s); return e ? e.textContent : null };
  if (t('h3') !== F.title) bad.push('title');
  if (t('[data-act=saveRule],[data-act=saveGoal]') !== F.save.label || t('[data-act=closeSheet]') !== F.cancel || t('[data-act=delRule]') !== (F.remove ? F.remove.label : null)) bad.push('buttons');
  if (F.note && t(':scope > p.ld-muted') !== F.note) bad.push('note');
  if (F.fold ? t('summary') !== F.fold.summary || t('details p.ld-muted') !== F.fold.note : t('details')) bad.push('fold');
  return bad;
}, [kind, id, ids]);
const RULE_IDS = { title: 'rTitle', kind: 'rKind', rotating: 'rRot', days: 'rDays', on: 'rOn', off: 'rOff', anchor: 'rAnchor', start: 'rStart', end: 'rEnd', commute: 'rCommute', unwind: 'rUnwind', load: 'rLoad' };
const GOAL_IDS = { title: 'gTitle', subject: 'gSubject', tag: 'gTag', minutes: 'gMin', perWeek: 'gFreq', physical: 'gIP', mental: 'gIC', priority: 'gPrio', level: 'gLevel', units: 'gUnits', unit: 'gUnit', rate: 'gRate', deadline: 'gDeadline' };
/* My time as data against the page's My time */
const timeAgainstPage = p => p.evaluate(() => {
  const T = LifeDirector.native.time(), m = document.querySelector('#view-life-director .ld-main'), all = s => [...m.querySelectorAll(s)], t = s => { const e = m.querySelector(s); return e ? e.textContent : null }, bad = [];
  const J = JSON.stringify, hm = x => String(Math.floor(x / 60) % 24).padStart(2, '0') + ':' + String(x % 60).padStart(2, '0'), S = T.sleep, R = T.routines;
  if (J(T.steps.map(s => [s.label, s.state])) !== J(all('.ld-steps li').map(l => [l.textContent, l.className]))) bad.push('steps');
  if (T.lede !== t('.ld-lede')) bad.push('lede');
  if (m.querySelector('#ldBed').value !== hm(S.bed) || m.querySelector('#ldWake').value !== hm(S.wake) || m.querySelector('#ldWeekendOn').checked !== S.weekend.on ||
    m.querySelector('#ldBedWe').value !== hm(S.weekend.bed) || m.querySelector('#ldWakeWe').value !== hm(S.weekend.wake)) bad.push('sleep values');
  if (m.querySelector('#ldBed').parentElement.firstChild.textContent !== S.labels.bed || m.querySelector('#ldBedWe').parentElement.firstChild.textContent !== S.labels.weekendBed ||
    m.querySelector('#ldWakeWe').parentElement.firstChild.textContent !== S.labels.weekendWake || m.querySelector('#ldWeekendOn').parentElement.textContent.trim() !== S.labels.weekend) bad.push('sleep labels');
  if (t('.ld-mini-dur') !== S.length || t('.ld-mini-span') !== S.bedText + ' – ' + S.wakeText || S.clock[S.bed / S.step] !== S.bedText || S.lengths[(((S.wake - S.bed) + 1440) % 1440) / S.step] !== S.length) bad.push('the mini dial\'s words');
  if (all('.ld-mini .ld-ctx').length !== S.context.length || S.clock.length !== 96 || S.lengths.length !== 96 || S.step !== 15) bad.push('the mini dial');
  if (t('[data-act=saveSleep]') !== S.save.label || m.querySelector('[data-act=saveSleep]').classList.contains('ld-primary') !== S.save.primary) bad.push('save sleep');
  if (J(T.commitments.rows.map(r => [r.id, r.title, r.line, 'ld-rdot ld-c-' + r.dot])) !== J(all('.ld-rules li[data-act=editRule]').map(l => [l.dataset.id, l.querySelector('b').textContent, l.querySelector('small').textContent, l.querySelector('i').className])) ||
    (!T.commitments.rows.length && t('.ld-rules-empty') !== T.commitments.empty) || t('[data-act=addRule]') !== T.commitments.add) bad.push('commitments');
  const rows = all('.ld-card-routines .ld-step-row');
  R.rows.concat(R.meals).forEach((r, k) => {
    const row = rows[k], btns = [...row.querySelectorAll('[data-act=routine]')];
    if (row.querySelector('b').textContent !== r.label || row.querySelector('small').textContent !== r.hint || row.querySelector('output').textContent !== r.text) bad.push(r.key + ' words');
    if (r.on !== false && J(btns.map(x => [+x.dataset.v, +x.dataset.lo, +x.dataset.hi, x.getAttribute('aria-label')])) !== J([[-r.step, r.min, r.max, r.less], [r.step, r.min, r.max, r.more]])) bad.push(r.key + ' stepper');
    if (r.stops.length !== (r.max - r.min) / r.step + 1 || (r.on !== false && r.stops[(r.value - r.min) / r.step] !== r.text)) bad.push(r.key + ' stops');
  });
  if (t('[data-act=meals]') !== R.meals.toggle || t('.ld-card-routines > p.ld-muted:last-child') !== R.note) bad.push('meals');
  if (J(T.goals.chips) !== J(all('.ld-goalchips .ld-chip').map(e => e.textContent)) || t('[data-act=addGoal]') !== T.goals.add.label || m.querySelector('[data-act=addGoal]').classList.contains('ld-primary') !== T.goals.add.primary ||
    t('.ld-card-goals [data-tab=goals]') !== T.goals.manage) bad.push('goals');
  const build = m.querySelector('[data-act=finishSetup]');
  if (T.build ? build.textContent !== T.build.label || t('.ld-build p') !== T.build.text || (build.getAttribute('aria-disabled') !== 'true') !== T.build.enabled || T.replan : build.textContent !== T.replan.label) bad.push('build');
  return { bad, first: T.first, size: J(T).length };
});

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

  /* 2. parity: each write's payload, as a screen sends it (do), saves what the page's own form saved and says the same; the same payload
     again changes nothing ("Already added" for a new commitment or goal) */
  for (const [state, list] of [['fresh', FRESH_WRITES], ['lived', LIVED_WRITES]]) {
    const p = await start(b, { state });
    await go(p, 'life-director', 1500);
    await pageSection(p, 'setup');
    for (const [name, , payload] of list) {
      const [cmd, pl] = payload(await screenData(p), drawn[state][name]), g = golden.writes[state][name], text = g.said.replace(/^\d+:/, '');
      const r = await doCommand(p, cmd, pl);
      // the same command again, straight away (a repeat saves again, so the clock must not have moved for the saved state to compare)
      if (r.ok && r.changed && cmd !== 'finishSetup') {
        const h = await savedHash(p), again = await doCommand(p, cmd, pl);
        ok(`(${state}: ${name}) the same command again changes nothing`, again.ok && again.changed === false && (await savedHash(p)) === h && again.message === (/^(ruleSave|goalAdd)$/.test(cmd) && !pl.id ? 'Already added' : ''), again);
      }
      await settle(p, 200);
      const words = r.ok ? (await said(p)).replace(/^\d+:/, '') : r.message;
      ok(`parity (${state}: ${name}): the command saves what the page's form saved, and says the same`, (await savedHash(p)) === g.saved && words === text && typeof r.changed === 'boolean', [r, words, text]);
    }
    ok('no page errors running the commands (' + state + ')', !p.errors.length, p.errors);
    await p.context().close();
  }

  /* 3. the schemas and My time as data, against what the page draws */
  for (const state of ['fresh', 'lived', 'mixed']) {
    const p = await start(b, { state });
    await go(p, 'life-director', 1500);
    await pageSection(p, 'setup');
    const T = await timeAgainstPage(p);
    ok('My time as data says what the page\'s My time shows (' + state + ')', !T.bad.length && T.first === (state !== 'lived'), T);
    ok('My time as data stays small (' + state + ', under 12 KB)', T.size < 12000, T.size);
    const rules = await p.evaluate(() => [''].concat([...document.querySelectorAll('#view-life-director [data-act=editRule]')].map(e => e.dataset.id)));
    for (const id of rules) {
      await tap(p, id ? '[data-act=editRule][data-id="' + id + '"]' : '[data-act=addRule]');
      const bad = await formAgainstPage(p, 'rule', id, RULE_IDS);
      ok('the commitment sheet as a schema matches the page\'s (' + state + ': ' + (id || 'new') + ')', !bad.length, bad);
      await closeSheet(p);
    }
    if (state === 'lived') {
      await tap(p, '[data-act=addGoal]');
      const grid = await p.evaluate(() => { const G = LifeDirector.native.sheet('goalType', ''), body = document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-body');
        return { ok: G.title === body.querySelector('h3').textContent && G.text === body.querySelector('p').textContent &&
          JSON.stringify(G.options.map(o => [o.arch, o.title, o.sub])) === JSON.stringify([...body.querySelectorAll('[data-act=pickArch]')].map(e => [e.dataset.arch, e.querySelector('b').textContent, e.querySelector('small').textContent])), kinds: G.options.map(o => o.arch) } });
      ok('the goal-kind grid as data matches the page\'s', grid.ok, grid);
      for (const k of grid.kinds) {
        await tap(p, '[data-act=pickArch][data-arch=' + k + ']');
        const bad = await formAgainstPage(p, 'goalForm', k, GOAL_IDS);
        ok('the goal form as a schema matches the page\'s (' + k + ')', !bad.length, bad);
        await tap(p, '[data-act=addGoal]');
      }
      await closeSheet(p);
      const edges = await p.evaluate(() => { const ld = LifeDirector.native; return [ld.sheet('rule', 'sleep'), ld.sheet('rule', 'nope'), ld.sheet('goalForm', 'NOPE')] });
      ok('a commitment that is not there answers gone, and an unknown kind of goal has nothing to draw', same(edges, [{ kind: 'rule', id: 'sleep', gone: true }, { kind: 'rule', id: 'nope', gone: true }, null]), edges);
    }
    if (state === 'fresh') {
      await pageSection(p, 'today');
      const F = await p.evaluate(() => { const F = LifeDirector.native.firstRun(), m = document.querySelector('#view-life-director .ld-main'), btn = m.querySelector('.ld-first button');
        return F && F.title === m.querySelector('.ld-first h3').textContent && F.text === m.querySelector('.ld-first p').textContent && F.button === btn.textContent && F.section === btn.dataset.tab &&
          F.arcs.filter(a => a.ring === 'outer').length === m.querySelectorAll('.ld-first-dial .ld-o').length && F.arcs.filter(a => a.ring === 'inner').length === m.querySelectorAll('.ld-first-dial .ld-sa').length });
      ok('the first-run card as data says what the page\'s card shows', F, F);
    }
    if (state === 'lived') ok('the first-run card is null once there is a plan', (await p.evaluate(() => LifeDirector.native.firstRun())) === null);
    await p.context().close();
  }

  /* 4. refusals: an edit of a commitment removed since, a goal of a kind the page does not know, a payload of the wrong kind; and a
     refused save, which answers ok: false with the page's banner words, until the same operation is retried once storage works */
  {
    const p = await start(b, { state: 'lived' });
    await go(p, 'life-director', 1500);
    const S = await screenData(p);
    const removed = await doCommand(p, 'ruleSave', Object.assign(vals(S.rule), { id: 'r-gone' }));
    const noKind = await doCommand(p, 'goalAdd', { op: 'aaaaaaaa-1', arch: 'JUGGLING', title: 'Juggle' });
    const badDays = await doCommand(p, 'ruleSave', Object.assign(vals(S.rule), { op: 'aaaaaaaa-2', days: 'Mon' }));
    const badTime = await doCommand(p, 'sleepSave', { bed: '23:00', wake: 420, weekend: null });
    const sleepRule = await doCommand(p, 'ruleRemove', { id: 'sleep' });
    const goneAgain = await doCommand(p, 'ruleRemove', { id: 'r-gone' });
    ok('an edit of a commitment removed since is refused, not added again', !removed.ok && removed.changed === false && removed.message === 'That commitment was removed.', removed);
    ok('a goal of a kind the page does not know is refused', !noKind.ok && noKind.message === 'That is not a kind of goal.', noKind);
    ok('days that are not a list of days, and a time that is not minutes, are refused', !badDays.ok && badDays.message === 'Pick at least one day' && !badTime.ok && badTime.message === 'Enter both times', [badDays, badTime]);
    ok('sleep is not a commitment to remove; one already gone changes nothing', !sleepRule.ok && goneAgain.ok && goneAgain.changed === false, [sleepRule, goneAgain]);
    await p.evaluate(() => { const real = window.__realSet = Storage.prototype.setItem;
      Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) } });
    const rule = Object.assign(vals(S.rule), { op: 'bbbbbbbb-0001', title: 'Choir', kind: 'APPOINTMENT', days: [2], start: 1170, end: 1260 });
    const goal = Object.assign(vals(S.goal.HABIT_READING), { arch: 'HABIT_READING', op: 'cccccccc-0001' });
    const faults = {};
    for (const [n, pl] of [['sleepSave', { bed: 1350, wake: 390, weekend: null }], ['routine', { key: 'windDownMinutes', value: 45 }], ['meals', { on: false }], ['ruleSave', rule], ['goalAdd', goal], ['ruleRemove', { id: 'work' }], ['finishSetup', {}]]) faults[n] = await doCommand(p, n, pl);
    ok('every My time write refused by storage answers ok: false with the banner\'s words', Object.values(faults).every(r => r.ok === false && r.changed === true && /^Not saved: Storage is full/.test(r.message)), faults);
    await p.evaluate(() => { Storage.prototype.setItem = window.__realSet });
    const retry = [await doCommand(p, 'ruleSave', rule), await doCommand(p, 'goalAdd', goal)];
    ok('the same new commitment and goal, once storage works: "Already added", and saved now', retry.every(r => r.ok && r.changed === false && r.message === 'Already added') &&
      await p.evaluate(() => { const s = JSON.parse(localStorage.getItem('lifemax_director_state_v1')); return s.availability.rules.some(r => r.id === 'r-bbbbbbbb-0001') && s.goals.some(g => g.id === 'g-cccccccc-000') }), retry);
    ok('no page errors refusing', !p.errors.length, p.errors);
    await p.context().close();
  }

  /* 5. an APK that draws My time (director.setup.v1): it covers My time and the first-run card and gets them, with My time's sheets;
     set-up finished from the screen plays its first build there, never on the page. Without the capability, phase 4A is unchanged. */
  const ALL = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1', 'director.setup.v1'];
  {
    const p = await start(b, { state: 'mixed', caps: ALL, switchOn: true });
    await p.evaluate(() => LifeDirector.native.tourSeen('setup', true));
    await go(p, 'life-director', 1500);
    let n = await mark(p);
    const open = await p.evaluate(() => { const s = NativeDirectorTabProjection.snapshot(); return { shows: LifeDirector.native.showing(), covers: s.covers, time: s.time, firstRun: s.firstRun, today: s.today } });
    const calls = await p.evaluate(n => __calls.slice(0, n).filter(c => c[0] === 'director').map(c => c[1]), n);
    ok('the screen covers My time, and the projection carries it', calls.includes('open') && open.shows === 'setup' && open.covers.includes('setup') && open.covers.includes('first-run') && !!open.time && open.firstRun === null && open.today === null, [calls, open.shows, open.covers]);
    ok('the projection\'s My time is nativeTime\'s', same(open.time, await p.evaluate(() => LifeDirector.native.time())));
    const sheet = await command(p, 'sheet', { kind: 'rule', id: 'r-rot' });
    ok('the sheet command opens My time\'s forms: the rotating shift as a schema', sheet.reply && sheet.reply.ok && sheet.snapshot.sheet && sheet.snapshot.sheet.kind === 'rule' && sheet.snapshot.sheet.id === 'r-rot' &&
      sheet.snapshot.sheet.fields.find(f => f.key === 'rotating').value === true, sheet.reply);
    await command(p, 'sheet', { kind: '' });
    const first = await command(p, 'section', { id: 'today' });
    ok('Today without a plan is the first-run card, covered and in the projection', first.reply.ok && first.snapshot && first.snapshot.firstRun && first.snapshot.firstRun.title === 'Tell it when you’re busy. It plans the rest.' && !first.calls.includes('close'), first.calls);
    await command(p, 'section', { id: 'setup' });
    n = await mark(p);
    const built = await command(p, 'do', { name: 'finishSetup' });
    // the page's build is scripted animation (runBuild's el.animate); the CSS arrival of the rows is not a build
    const pagePlayed = await p.evaluate(() => [...document.querySelectorAll('#view-life-director .ld-dial *')].some(e => e.getAnimations().some(a => !(a instanceof CSSAnimation) && !(a instanceof CSSTransition))));
    ok('Build my week from the screen: saved, Today on the screen with its first build, and the page plays none', built.reply.ok && built.reply.changed && built.snapshot.tab === 'today' && built.snapshot.today &&
      built.snapshot.today.dial.build && !pagePlayed && !built.calls.includes('close'), [built.reply, built.calls, pagePlayed, built.snapshot && built.snapshot.tab]);
    ok('no page errors covering My time', !p.errors.length, p.errors);
    await p.context().close();
  }
  {
    const p = await start(b, { state: 'mixed', caps: ALL.slice(0, 4), switchOn: true });
    await p.evaluate(() => LifeDirector.native.tourSeen('setup', true));
    await go(p, 'life-director', 1500);
    const s = await p.evaluate(() => { const s = NativeDirectorTabProjection.snapshot(); return { covers: s.covers, time: s.time, sheet: NativeDirectorProjection.commands.sheet({ kind: 'rule', id: '' }) } });
    const calls = await p.evaluate(() => __calls.filter(c => c[0] === 'director').map(c => c[1]));
    ok('without director.setup.v1 My time stays the page\'s: not covered, not projected, its forms refused', !s.covers.includes('setup') && s.time === null && !s.sheet.ok && !calls.includes('open'), [s, calls]);
    await p.evaluate(() => setNativeDirectorEnabled(false));
    await tap(p, '[data-act=finishSetup]');
    await settle(p, 300);
    ok('with no screen at all, Build my week plays its first build on the page (what the check above looks for)',
      await p.evaluate(() => [...document.querySelectorAll('#view-life-director .ld-dial *')].some(e => e.getAnimations().some(a => !(a instanceof CSSAnimation) && !(a instanceof CSSTransition)))));
    await p.context().close();
  }

  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
