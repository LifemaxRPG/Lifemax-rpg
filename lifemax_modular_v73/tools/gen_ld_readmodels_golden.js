/* Golden cases for the Life Director read models (WP-LD8a: horizonDays, unplannedSegments, dayBlocks, calendarDays, liveTimeline, nowState,
   weekModel and the week as the projection carries it, nativeWeek; logic.js:394-457, 1043-1135, 1302-1345, 1363-1371, 3272-3315), recorded
   from the real page in Chromium, so that the native read models
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels) are accepted only when they give the same answer for
   every case. Also writes the page words those read models use, exported from the page, never retyped:
   android-wrapper/native/src/main/resources/lifedirector/readmodel-words.json (WORDS below).
   Regenerate when the page changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_readmodels_golden.js [built-page.html]
   Output: tests/fixtures/ld-readmodels-golden.json.gz (gzip of one state or case per line; the text is what serialize() writes).
   tests/ld-readmodels-golden.test.js re-captures the store seeds, records every case again and fails when anything differs.

   Method: the controller module of logic.js with one hook added (tools/ld_logic_vm.js readModelSource) is evaluated inside the built page,
   with the page's own engine, the page's own LifeMax host (connected() answered per case, xpFor() watched: every call and its answer is
   recorded) and the page's Date pinned to the case's moment. The read models only read: the hook sets the controller's ST to a fresh copy
   of the case's state, and nothing is saved (the copy is the controller's own; the page's controller and its storage are not touched).

   File: {header, states, cases}. A state line is {state, st}: a saved Life Director state (tools/ld_readmodels_cases.js says where each
   comes from). A case is {id, family, rule, state, set, nowMs, deviceZone, host, calendar?, out}; out has one member per read model, each
   the answer or {threw: <error name>}:
     days      horizonDays(ST.schedule, tz()) (RM-3)
     unplanned unplannedSegments(days[0], tz()) (RM-5)
     blocks    dayBlocks(ST.schedule, day, tz()) for each of days (RM-6 to RM-10)
     timeline  liveTimeline(now) (RM-11)
     now       nowState(now): cur, next, nextTask and soon.b as blocks (copies), alert as 'kick', 'soon' or null (which of the two it is),
               isSes and running as booleans (the page holds null, undefined or false for "no"; every reader tests truthiness) (RM-12, RM-13)
     week      weekModel(): rows without d and blocks (the recorder checks they are days[k] and blocks[k]), stats, attrs, maxA, fitText
               (RM-14 to RM-18, RM-20)
     native    nativeWeek() (RM-19)
     calendar  for each offset of the case's calendar: {offset, days: calendarDays(offset, tz()), blocks: each day's dayBlocks} (RM-4)
     xp        every LifeMaxHost.xpFor call the read models made, in order: {args: [type, minutes, quality, dateKey], out: {xp, primary,
               split} or null} (RM-14)
   In a block a session (inst) is named by where it is stored: 's<k>' scheduledInstances[k], 'h<k>' historyInstances[k]. JSON canon as
   tools/gen_ld_schedule_golden.js (members holding undefined left out, NaN, -0 and the infinities as {$num}). Every case is run twice on
   fresh copies of its state and must give the same answer both times (the recorder throws otherwise).

   What is held still: the page's clock (Playwright, fixed at RECORDED_AT, which the host's XP preview reads), its zone (UTC) and locale
   (en-US); the page boots with the usual test save. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_readmodels_cases.js');
const seeds = require('./ld_store_seeds.js');
const logicVm = require('./ld_logic_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-readmodels-golden.json.gz');
const WORDS_FILE = path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'main', 'resources', 'lifedirector', 'readmodel-words.json');
const RECORDED_AT = '2026-10-28T12:00:00Z';

/* The page words: tables named by the controller's constant, and phrases written inline in a function, named here by the function and
   the position of the string among the function's string literals (in the page's own text of the function, fn.toString()). The literal
   counts are fixed, so a phrase added to or taken from one of these functions stops the export until this table is checked. */
const PHRASES = {
  dayBlocks: { count: 15, take: { freeTime: 11 }, same: [[11, 14]] },
  weekModel: { count: 24, take: { today: 6, ariaGap: 8, ariaColon: 9, ariaSessions: 10, fallbackColour: 12, statSessions: 13, statComma: 14,
    statDone: 15, statPlanned: 17, statAbout: 18, statNone: 19, statXp: 20, fitDowntime: 21, fitFree: 22, fitCoverage: 23 }, same: [[6, 7]] },
  nativeWeek: { count: 9, take: { doneMark: 1, noMinutes: 3, yieldTitle: 4, fitTitle: 5, times: 6, tier: 8 } },
  unfitSummary: { count: 4, take: { unfitSession: 0, unfitPlural: 2, unfitTail: 3 } },
  mins: { count: 5, take: { hours: 0, gap: 1, minutes: 2 }, same: [[2, 4]] },
};

/* In the page: makes the read models (R) from the hooked controller text and returns run(states, cases) and words(). Self-contained: it is
   sent into the page as text. */
function pageRunner(src, phrases) {
  const RealDate = Date;
  let ms = 0, connected = true, calls = [];
  class PinnedDate extends RealDate {
    constructor(...a) { if (a.length) super(...a); else super(ms); }
    static now() { return ms; }
  }
  const host = Object.create(LifeMaxHost);
  host.connected = () => connected;
  host.xpFor = (type, minutes, quality, dateKey) => {
    let out = null, error = null;
    try { out = LifeMaxHost.xpFor(type, minutes, quality, dateKey); } catch (e) { error = e; }
    calls.push({ args: [type, minutes, quality, dateKey], out: out ? { xp: out.xp, primary: out.rule.primary, split: out.split.map(s => ({ attr: s.attr, xp: s.xp })) } : null });
    if (error) throw error;
    return out;
  };
  let R = null;
  new Function('LifeDirectorEngine', 'LifeMaxHost', 'Date', '__ldReadModels', src)(LifeDirectorEngine, host, PinnedDate, x => { R = x; });
  if (!R) throw new Error('the read-model hook did not run');
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
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
    ms = c.nowMs; connected = c.host !== 'off'; calls = [];
    R.setState(st);
    const s = st.schedule;
    const ref = i => { let k = s && (s.scheduledInstances || []).indexOf(i); if (k >= 0) return 's' + k; k = s && (s.historyInstances || []).indexOf(i); return k >= 0 ? 'h' + k : '?'; };
    const blockOut = b => (b.inst ? Object.assign({}, b, { inst: ref(b.inst) }) : Object.assign({}, b));
    const out = {};
    let days = null, dayBlocks = null;
    out.days = section(() => (days = R.horizonDays(s, R.tz())));
    out.unplanned = section(() => R.unplannedSegments(days[0], R.tz()));
    out.blocks = section(() => (dayBlocks = days.map(d => R.dayBlocks(s, d, R.tz()).map(blockOut))));
    out.timeline = section(() => R.liveTimeline(R.nowEpoch()).map(blockOut));
    out.now = section(() => {
      const n = R.nowState(R.nowEpoch()), b = x => (x ? blockOut(x) : null);
      return { cur: b(n.cur), next: b(n.next), nextTask: b(n.nextTask), kick: n.kick, soon: n.soon ? { kind: n.soon.kind, b: b(n.soon.b), sec: n.soon.sec } : null,
        alert: n.alert ? (n.alert === n.kick ? 'kick' : 'soon') : null, isSes: !!n.isSes, running: !!n.running };
    });
    out.week = section(() => {
      const W = R.weekModel();
      W.rows.forEach((r, k) => {
        if (JSON.stringify(r.d) !== JSON.stringify(days[k]) || JSON.stringify(r.blocks.map(blockOut)) !== JSON.stringify(dayBlocks[k])) throw new Error('weekModel row ' + k + ' is not the day');
      });
      return { rows: W.rows.map(r => ({ k: r.k, n: r.n, done: r.done, m: r.m, name: r.name, aria: r.aria })), stats: W.stats, attrs: W.attrs, maxA: W.maxA, fitText: W.fitText };
    });
    out.native = section(() => R.nativeWeek());
    if (c.calendar) out.calendar = c.calendar.map(offset => ({ offset, days: section(() => R.calendarDays(offset, R.tz())),
      blocks: section(() => { const cd = R.calendarDays(offset, R.tz()); return cd ? cd.map(d => R.dayBlocks(s, d, R.tz()).map(blockOut)) : null; }) }));
    out.xp = canon(calls);
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
    const out = { dayNames: R.DOW.slice(), kindClass: Object.assign({}, R.KIND_CLASS), weekLegend: R.WEEK_LEGEND.map(([kind, label]) => ({ kind, label })),
      weekTicks: R.WEEK_TICKS.slice(), yieldNote: R.YIELD_NOTE, allFit: R.ALL_FIT, thresholds: { soon: R.SOON, ending: R.ENDING, late: R.LATE }, phrases: {} };
    for (const [fn, spec] of Object.entries(phrases)) {
      const lits = literals(R[fn]);
      if (lits.length !== spec.count) throw new Error(fn + ' has ' + lits.length + ' string literals, the export expects ' + spec.count + ': check PHRASES');
      for (const [a, b] of spec.same || []) if (lits[a] !== lits[b]) throw new Error(fn + ': literals ' + a + ' and ' + b + ' are no longer the same');
      for (const [key, at] of Object.entries(spec.take)) out.phrases[key] = lits[at];
    }
    const attrs = {};
    for (const a of Object.keys(ATTR_COL)) attrs[a] = NativeTabKit.color(ATTR_COL[a]);
    out.colours = { attributes: attrs, fallback: NativeTabKit.color(out.phrases.fallbackColour) };
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
  if (!(await p.evaluate(() => typeof LifeDirectorApp !== 'undefined' && typeof LifeMaxHost !== 'undefined' && typeof NativeTabKit !== 'undefined'))) throw new Error('the page has no Life Director');
  await p.evaluate(({ src, runner, phrases }) => { window.__ldRm = new Function('return ' + runner)()(src, phrases); },
    { src: logicVm.readModelSource(), runner: pageRunner.toString(), phrases: PHRASES });
  return { p, ctx, errors };
}

/* Captures the store seeds, defines the cases and records them (and the words) in Chromium. */
async function recordInChromium(file, onPart, given) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const seedTexts = await seeds.capture(browser, file);
    const { states, cases } = given ? given(seedTexts) : defineCases(seedTexts);
    const stateTexts = {};
    for (const [id, st] of states) stateTexts[id] = JSON.stringify(st);
    const { p, ctx, errors } = await openPage(browser, file);
    const words = await p.evaluate(() => window.__ldRm.words());
    const lines = [];
    for (let i = 0; i < cases.length; i += 40) {
      const part = cases.slice(i, i + 40), need = {};
      for (const c of part) need[c.state] = stateTexts[c.state];
      lines.push(...(await p.evaluate(([s, cs]) => window.__ldRm.run(s, cs), [need, part])));
      if (onPart) onPart(lines.length);
    }
    await ctx.close();
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    const stateLines = [...states.keys()].map(id => JSON.stringify({ state: id, st: JSON.parse(stateTexts[id]) }));
    return { stateLines, lines, words, cases, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({ about: 'Life Director read-model golden cases; see tools/gen_ld_readmodels_golden.js', recordedIn: 'Chromium ' + browser, recordedAt: RECORDED_AT });
const serialize = (head, stateLines, lines) => '{"header":' + JSON.stringify(head) + ',"states":[\n' + stateLines.join(',\n') + '\n],"cases":[\n' + lines.join(',\n') + '\n]}\n';
const wordsText = words => JSON.stringify(Object.assign({ about: 'The Life Director page words the native read models use, exported from the page by lifemax_modular_v73/tools/gen_ld_readmodels_golden.js; do not edit' }, words), null, 1) + '\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const strip = l => (l.endsWith(',') ? l.slice(0, -1) : l);
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(strip);
const readStateLines = () => readFile().split('\n').filter(l => l.startsWith('{"state"')).map(strip);

module.exports = { recordInChromium, pageRunner, serialize, header, wordsText, readFile, readLines, readStateLines, compress, FILE, WORDS_FILE, FAMILIES, PHRASES, RECORDED_AT };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  recordInChromium(file, n => { if (n % 400 === 0) console.log('recorded ' + n); }).then(({ stateLines, lines, words, cases, browser }) => {
    const text = serialize(header(browser), stateLines, lines);
    fs.writeFileSync(FILE, compress(text));
    fs.writeFileSync(WORDS_FILE, wordsText(words));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + stateLines.length + ' states, ' + lines.length + ' cases ' + JSON.stringify(by));
    console.log('wrote ' + path.relative(process.cwd(), WORDS_FILE));
  }).catch(e => { console.error(e); process.exit(1); });
}
