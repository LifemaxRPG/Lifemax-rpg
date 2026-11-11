/* Golden cases for the Life Director read models, part b (WP-LD8b: the goals, quests, settings, My time, the forms, Something came up,
   Move, the work rules and shifts, and the dial's handles and stops; logic.js nativeGoals, nativeQuests, nativeSettings, nativeTime,
   nativeForm, nativeSheet('disrupt'), nativeSheet('move'), workRules, shifts and nativeHandles over dialHandles, with what they are made
   of), recorded from the real page in Chromium, so that the native read models
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels) are accepted only when they give the same answer for
   every case. Also writes the page words those read models use, exported from the page, never retyped:
   android-wrapper/native/src/main/resources/lifedirector/readmodel-words-b.json (WORDS below).
   Regenerate when the page changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_readmodels_b_golden.js [built-page.html]
   Output: tests/fixtures/ld-readmodels-b-golden.json.gz (gzip of one state or case per line; the text is what serialize() writes).
   tests/ld-readmodels-b-golden.test.js re-captures the store seeds, records every case again and fails when anything differs.

   Method: as part a (tools/gen_ld_readmodels_golden.js): the controller module of logic.js with its one hook (tools/ld_logic_vm.js
   readModelSource, the names of READ_MODELS_B) is evaluated inside the built page with the page's own engine, a LifeMax host whose
   capabilities() and milestones() give the case's answers (host 'page': the page's own host, its answers recorded) and the page's Date
   pinned to the case's moment. The hook sets the controller's ST to a fresh copy of the case's state and its load report to the case's.
   Nothing is saved; the page's own controller and storage are not touched.

   File: {header, cases}. The states are part a's (tests/fixtures/ld-readmodels-golden.json.gz, header.statesFrom): every state a case
   names is a state line of that file, byte for byte as part b defines it (the web test checks), so it is not written twice. A case is tools/ld_readmodels_b_cases.js's with out, one member per read
   model, each the answer or {threw: <error name>}:
     goals     nativeGoals() (RM-23, RM-24)
     quests    nativeQuests() (RM-25)
     settings  nativeSettings() (RM-26, RM-27)
     time      nativeTime() (RM-28 to RM-30)
     disrupt   nativeSheet('disrupt', '') (RM-34)
     workRules workRules() (RM-36)
     forms     nativeSheet(kind, id) for each of the case's forms (RM-31 to RM-33)
     moves     nativeSheet('move', id) for each of the case's moves (RM-35)
     shifts    shifts(from, to) for each of the case's windows (RM-37)
     handles   for each of the case's handleDays: null when there is no such day, else {startEpoch, endEpoch} of the day and
               nativeHandles(dialHandles(day), startEpoch, endEpoch)'s members (RM-38 to RM-40)
     host      what the host answered: {capabilities: {connected, missing}, milestones} (the page's own answers for host 'page')
   JSON canon as part a. Every case is run twice on fresh copies of its state and must give the same answer both times.

   What is held still: the page's clock (fixed at RECORDED_AT), its zone (UTC) and locale (en-US, so every time is written in en-US and
   with a 12-hour clock: RM-22). */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_readmodels_b_cases.js');
const seeds = require('./ld_store_seeds.js');
const logicVm = require('./ld_logic_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-readmodels-b-golden.json.gz');
const WORDS_FILE = path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'main', 'resources', 'lifedirector', 'readmodel-words-b.json');
const RECORDED_AT = '2026-10-28T12:00:00Z';

/* The page words of part b. Tables: the controller's constants, exported whole. Phrases: strings written inline in a function, named
   by the function and the position of the string among the function's string literals (fn.toString()); the literal counts are fixed and
   `same` pins literals that must stay equal, so a phrase added to or taken from one of these functions stops the export until this
   table is checked. Literals not taken are codes (states, kinds, field keys, commands) the native code names as the page's API. */
const TABLES = ['PRIO', 'GOALS_EXPLAIN', 'GOALS_EMPTY', 'GOAL_REMOVE_CONFIRM', 'CLARIFY_TEXT', 'QUEST_HEAD', 'MILESTONE_NOTE', 'KIND_LABEL', 'ROUTINES',
  'MEAL_RANGE', 'RULE_KINDS', 'RULE_LOADS', 'NEW_RULE', 'CUSTOM_TAGS', 'GOAL_LEVELS', 'INTENSITY', 'PREF_RANGES', 'GRACE', 'HORIZON', 'MISSES',
  'SETTINGS_TEXT', 'DRAG_WORDS', 'DIAL', 'MINI_SNAP', 'MINI_GROUP', 'DISRUPT', 'TAG_ICON', 'DOW'];
const PHRASES = {
  goalCards: { count: 18, take: { custom: 1, sep: 2, level: 3, pausedTail: 4, sessions: 6, week: 7, space: 8, dash: 9, times: 11, comma: 12, projected: 14,
    period: 15, resume: 16, pause: 17 } },
  nativeGoals: { count: 4, take: { percent: 0, remove: 2, add: 3 }, same: [[0, 1]] },
  questsModel: { count: 35, take: { notDetected: 0, missing: 1, comma: 2, period: 3, nextSuffix: 5, allDayReminder: 7, donePeriod: 9, session: 10,
    plural: 12, scheduled: 13, couldNotFit: 14, locked: 16, daily: 19, due: 20, noDeadline: 21, toneOk: 23, tonePassive: 25, toneNo: 26,
    tracked: 28, allDay: 29, of: 30, earnedTail: 31, earned: 32, inProgress: 33, empty: 34 }, same: [[13, 17]] },
  nativeSettings: { count: 4, take: { restTokens: 1, restTail: 2 } },
  graceLabel: { count: 1, take: { minuteMark: 0 } },
  diagnosticsLine: { count: 14, take: { engine: 0, sep: 1, ms: 3, planned: 5, noPlan: 6, events: 7, host: 8, connected: 9, missing: 10, comma: 11,
    rejected: 12 }, same: [[1, 2], [1, 4]] },
  timeModel: { count: 60, take: { off: 4, less: 6, more: 7, stepSleep: 8, stepOk: 9, stepCurrent: 10, stepWork: 11, stepGoals: 15, stepBuild: 19,
    ledeFirst: 21, lede: 22, sleepTitle: 23, sleepNote: 24, sleepCentre: 25, weeknightBed: 26, bedtime: 27, wakeUp: 28, weekendToggle: 29,
    weekendBed: 30, weekendWake: 31, saveSleep: 34, saveSleepFirst: 35, commitTitle: 36, dotBlock: 38, dotWork: 39, sep: 40, commitEmpty: 41,
    commitAdd: 42, routinesTitle: 43, routinesText: 44, turnOff: 46, turnOn: 47, shorter: 48, longer: 49, routinesNote: 50, goalsTitle: 51,
    goalsEmpty: 52, goalsAdd: 53, goalsManage: 54, buildTitle: 55, zone: 56, buildTail: 57, replan: 59 },
    same: [[4, 5], [4, 45], [27, 32], [28, 33], [55, 58], [9, 12], [9, 16], [10, 13], [10, 17]] },
  daysText: { count: 15, take: { rotating: 0, onOff: 1, offTail: 2, everyDay: 3, weekdays: 5, weekends: 7, sunThu: 10, friSat: 13, space: 14 } },
  ruleText: { count: 5, take: { sep: 0, dash: 1, travel: 3 }, same: [[0, 2]] },
  hourWord: { count: 6, take: { midnight: 0, noon: 1, am: 2, pm: 3, pad: 4, oclock: 5 } },
  ruleForm: { count: 54, take: { editPrefix: 3, addTitle: 4, name: 8, type: 11, rotating: 14, days: 21, daysOn: 24, daysOff: 27, firstDay: 30, starts: 33,
    ends: 36, travel: 39, unwind: 42, tiring: 45, note: 47, save: 48, add: 49, cancel: 51, remove: 52 } },
  goalTypeModel: { count: 8, take: { title: 1, text: 2, comma: 3, customIcon: 5, customTitle: 6, customSub: 7 } },
  goalFormModel: { count: 60, take: { name: 2, namePlaceholder: 4, language: 8, languageValue: 9, activity: 12, minutes: 15, perWeek: 18, physical: 21,
    mental: 24, priority: 27, experience: 31, customTitle: 35, foldSummary: 37, foldNote: 38, total: 41, totalPlaceholder: 42, unit: 45,
    unitPlaceholder: 47, rate: 50, ratePlaceholder: 51, by: 54, save: 57, cancel: 59 } },
  activityAtForm: { count: 23, take: { title: 1, text: 2, name: 5, activity: 9, date: 12, start: 15, duration: 18, note: 19, save: 20, cancel: 22 } },
  disruptParts: { count: 45, take: { workTitle: 2, workButton: 4, date: 7, travel: 10, from: 13, until: 16, blockTitle: 18, hourMark: 21, dayTitle: 23,
    dayButton: 25, sickTitle: 27, day: 30, plural: 31, sickNote: 33, energyTitle: 35, low: 39, normal: 41, high: 43, energyNote: 44 } },
  moveTimes: { count: 3, take: { space: 2 } },
  moveModel: { count: 5, take: { titleOpen: 0, titleClose: 1, none: 2, notePrefix: 3, noteTail: 4 } },
  workRules: { count: 2, take: { work: 1 } },
  shifts: { count: 11, take: { keySep: 1, work: 2, oneKey: 9, extra: 10 } },
  handleLabel: { count: 8, take: { bedtime: 2, wakeUp: 3, commitment: 4, starts: 6, ends: 7 } },
  dragPreview: { count: 6, take: { ofSleep: 4 } },
  nativeHandles: { count: 3, take: { space: 0 } },
  dialHandles: { count: 10, take: { keySep: 7 }, same: [[7, 8], [7, 9]] },
};

/* In the page: makes the read models (R) from the hooked controller text and returns run(states, cases) and words(). Self-contained: it is
   sent into the page as text. */
function pageRunner(src, phrases, tables) {
  const RealDate = Date;
  let ms = 0, answer = null, seen = null;
  class PinnedDate extends RealDate {
    constructor(...a) { if (a.length) super(...a); else super(ms); }
    static now() { return ms; }
  }
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const host = Object.create(LifeMaxHost);
  host.capabilities = () => {
    const c = answer === 'page' ? LifeMaxHost.capabilities() : { connected: answer.connected, missing: answer.missing.slice() };
    seen.capabilities = { connected: !!c.connected, missing: c.missing.slice() };
    return c;
  };
  host.connected = () => host.capabilities().connected;
  host.milestones = () => {
    const m = answer === 'page' ? LifeMaxHost.milestones() : JSON.parse(JSON.stringify(answer.milestones));
    seen.milestones = canon(m);
    return m;
  };
  let R = null;
  new Function('LifeDirectorEngine', 'LifeMaxHost', 'Date', '__ldReadModels', src)(LifeDirectorEngine, host, PinnedDate, x => { R = x; });
  if (!R) throw new Error('the read-model hook did not run');
  const apply = (st, set) => {
    for (const e of set) {
      let o = st; const p = e[0];
      for (const k of p.slice(0, -1)) o = o[k];
      if (e.length === 1) delete o[p[p.length - 1]]; else o[p[p.length - 1]] = JSON.parse(JSON.stringify(e[1]));
    }
    return st;
  };
  const section = f => { try { return canon(f()); } catch (e) { return { threw: (e && e.name) || 'Error' }; } };
  const once = (stateText, c) => {
    const st = apply(JSON.parse(stateText), c.set || []);
    ms = c.nowMs; answer = c.host; seen = {};
    R.setState(st); R.setLoad(c.load ? { source: c.load } : null);
    const out = {};
    out.goals = section(() => R.nativeGoals());
    out.quests = section(() => R.nativeQuests());
    out.settings = section(() => R.nativeSettings());
    out.time = section(() => R.nativeTime());
    out.disrupt = section(() => R.nativeSheet('disrupt', ''));
    out.workRules = section(() => R.workRules());
    out.forms = c.forms.map(([kind, id]) => section(() => R.nativeSheet(kind, id)));
    out.moves = c.moves.map(id => section(() => R.nativeSheet('move', id)));
    out.shifts = c.shifts.map(([a, b]) => section(() => R.shifts(a, b)));
    out.handles = c.handleDays.map(k => section(() => {
      const days = st.schedule ? R.horizonDays(st.schedule, R.tz()) : R.calendarDays(0, R.tz()), d = days[k];
      if (!d) return null;
      return Object.assign({ startEpoch: d.startEpoch, endEpoch: d.endEpoch }, R.nativeHandles(R.dialHandles(d), d.startEpoch, d.endEpoch));
    }));
    out.host = canon(seen);
    return JSON.stringify(out);
  };
  const run = (states, cases) => cases.map(c => {
    const a = once(states[c.state], c);
    if (once(states[c.state], c) !== a) throw new Error('not deterministic: ' + c.id);
    return JSON.stringify(Object.assign({}, c, { out: JSON.parse(a) }));
  });
  const LIT = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"/g;
  const literals = f => (f.toString().match(LIT) || []).map(l => new Function('return ' + l)());
  const words = () => {
    const out = { tables: {}, engineVersion: LifeDirectorEngine.ENGINE_VERSION, h12: R.H12, phrases: {} };
    for (const t of tables) out.tables[t] = JSON.parse(JSON.stringify(R[t]));
    for (const [fn, spec] of Object.entries(phrases)) {
      const lits = literals(R[fn]);
      if (lits.length !== spec.count) throw new Error(fn + ' has ' + lits.length + ' string literals, the export expects ' + spec.count + ': check PHRASES');
      for (const [a, b] of spec.same || []) if (lits[a] !== lits[b]) throw new Error(fn + ': literals ' + a + ' and ' + b + ' are no longer the same');
      out.phrases[fn] = {};
      for (const [key, at] of Object.entries(spec.take)) out.phrases[fn][key] = lits[at];
    }
    return out;
  };
  return { run, words };
}

async function openPage(browser, file) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
  const p = await ctx.newPage(), errors = [];
  p.on('pageerror', e => errors.push(String(e)));
  await p.clock.setFixedTime(new Date(RECORDED_AT));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file);
  if (!(await p.evaluate(() => typeof LifeDirectorApp !== 'undefined' && typeof LifeMaxHost !== 'undefined'))) throw new Error('the page has no Life Director');
  await p.evaluate(({ src, runner, phrases, tables }) => { window.__ldRmB = new Function('return ' + runner)()(src, phrases, tables); },
    { src: logicVm.readModelSource(logicVm.READ_MODELS_B), runner: pageRunner.toString(), phrases: PHRASES, tables: TABLES });
  return { p, ctx, errors };
}

/* Captures the store seeds, defines the cases and records them (and the words) in Chromium. */
async function recordInChromium(file, onPart) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const seedTexts = await seeds.capture(browser, file);
    const { states, cases } = defineCases(seedTexts);
    const stateTexts = {};
    for (const [id, st] of states) stateTexts[id] = JSON.stringify(st);
    const { p, ctx, errors } = await openPage(browser, file);
    const words = await p.evaluate(() => window.__ldRmB.words());
    const lines = [];
    for (let i = 0; i < cases.length; i += 25) {
      const part = cases.slice(i, i + 25), need = {};
      for (const c of part) need[c.state] = stateTexts[c.state];
      lines.push(...(await p.evaluate(([s, cs]) => window.__ldRmB.run(s, cs), [need, part])));
      if (onPart) onPart(lines.length);
    }
    await ctx.close();
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    const stateLines = [...states.keys()].map(id => JSON.stringify({ state: id, st: JSON.parse(stateTexts[id]) }));
    return { stateLines, lines, words, cases, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({ about: 'Life Director read-model golden cases, part b; see tools/gen_ld_readmodels_b_golden.js', recordedIn: 'Chromium ' + browser, recordedAt: RECORDED_AT,
  statesFrom: 'ld-readmodels-golden.json.gz' });
const serialize = (head, lines) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + lines.join(',\n') + '\n]}\n';
const wordsText = words => JSON.stringify(Object.assign({ about: 'The Life Director page words the native read models of part b use, exported from the page by lifemax_modular_v73/tools/gen_ld_readmodels_b_golden.js; do not edit' }, words), null, 1) + '\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const strip = l => (l.endsWith(',') ? l.slice(0, -1) : l);
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(strip);

module.exports = { recordInChromium, pageRunner, serialize, header, wordsText, readFile, readLines, compress, FILE, WORDS_FILE, FAMILIES, PHRASES, TABLES, RECORDED_AT };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  recordInChromium(file, n => { if (n % 500 === 0) console.log('recorded ' + n); }).then(({ stateLines, lines, words, cases, browser }) => {
    const text = serialize(header(browser), lines), partA = new Set(require('./gen_ld_readmodels_golden.js').readStateLines());
    const missing = stateLines.filter(l => !partA.has(l));
    if (missing.length) throw new Error(missing.length + ' states are not part a\'s byte for byte (first ' + JSON.parse(missing[0]).state + '): regenerate part a first');
    fs.writeFileSync(FILE, compress(text));
    fs.writeFileSync(WORDS_FILE, wordsText(words));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + stateLines.length + ' states, ' + lines.length + ' cases ' + JSON.stringify(by));
    console.log('wrote ' + path.relative(process.cwd(), WORDS_FILE));
  }).catch(e => { console.error(e); process.exit(1); });
}
