/* Golden cases for the Life Director engine's time and hash kernel (WP-LD1), recorded from the real page in Chromium, so that the
   native kernel (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it gives the same
   answer, bit for bit, for every case. Regenerate when the engine's time or hash code changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_kernel_golden.js [built-page.html]
   Output: tests/fixtures/ld-kernel-golden.json. tests/ld-kernel-golden.test.js records every case again, in Chromium and in node, and
   fails when either disagrees with the file, so the file cannot go stale. The cases are defined in tools/ld_kernel_cases.js.

   Why Chromium: the engine's zone arithmetic is Intl.DateTimeFormat (engine.js:68-127), so its answers are whatever the JavaScript
   engine's ICU time zone data says. On the phone that is the WebView, which is Chromium, so the cases are recorded in Chromium. Node
   runs the same engine text in a vm (tools/ld_engine_vm.js) as a second runner: it uses a different ICU build, and the test requires
   both to agree with the file, so a tz database difference between the two shows up as a failing case, not as a silent change.

   File: {header, cases}. header holds the versions the cases were recorded with (informational: a newer Chromium or node may record
   the same cases with other versions). A case is {id, family, rule, fn, args, out}:
     family  hash | stable | event | rng | num | zone | alias | parts | offset | wall | cal | horizon | grid | slot | week | fuzz.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      what was called (FN below): the engine's own function of that name (engine.js; the private ones through the kernel hook of
             tools/ld_engine_vm.js), except grid (createGrid over buildHorizon: H0, HN, n, localMin, dayOf), slotOf/slotCeil/epochOf
             (with a grid whose H0 is args[0]), dayEndAfter (with input {timezone: args[1]}), mulberry32 (its first args[1] outputs),
             and weekKey/weekBounds, the page's own functions (logic.js weekKey, the LifeMax host's weekBounds in engine.js) taken from
             the source text and run with the zone given.
     args    the arguments, as JSON. {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'} is that number; {$parse: key} is parseYmd(key).
     out     the return value as JSON, numbers in the same {$num} form when JSON cannot hold them, or {threw: <error name>}.

   What is held still: nothing here reads a clock or a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path');
const { defineCases, FAMILIES } = require('./ld_kernel_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-kernel-golden.json');
const SRC = path.join(__dirname, '..', 'src', 'features', 'life-director');

/* The text of `function <name>(` up to its matching brace. */
function functionText(file, name) {
  const text = fs.readFileSync(path.join(SRC, file), 'utf8'), at = text.indexOf('function ' + name + '(');
  if (at < 0) throw new Error(file + ': function ' + name + ' not found');
  let depth = 0;
  for (let i = text.indexOf('{', at); i < text.length; i++) {
    if (text[i] === '{') depth++;
    else if (text[i] === '}' && --depth === 0) return text.slice(at, i + 1);
  }
  throw new Error(file + ': function ' + name + ' does not end');
}
const pageFunctions = () => ({ weekKey: functionText('logic.js', 'weekKey'), weekBounds: functionText('engine.js', 'weekBounds') });

/* Runs every case; E is the engine (window.LifeDirectorEngine or the vm's), K its kernel hook. Self-contained: it is also sent into the page. */
function runCases(E, K, cases, texts) {
  const U = E.util;
  let zone = 'UTC';
  const weekKey = new Function('U', 'tz', texts.weekKey + '\nreturn weekKey;')(U, () => zone);
  const weekBounds = new Function('LifeDirectorEngine', texts.weekBounds + '\nreturn weekBounds;')({ util: U });
  const NUMS = { NaN: NaN, Infinity: Infinity, '-Infinity': -Infinity, '-0': -0 };
  const decode = v => (Array.isArray(v) ? v.map(decode) : v && typeof v === 'object'
    ? ('$num' in v ? NUMS[v.$num] : '$parse' in v ? K.parseYmd(v.$parse) : Object.fromEntries(Object.entries(v).map(([k, x]) => [k, decode(x)]))) : v);
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (ArrayBuffer.isView(v)) return Array.from(v, canon);
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const FN = {
    cyrb53: (s, seed) => K.cyrb53(s, seed),
    stableId: (p, s) => U.stableId(p, s),
    completionEventId: (pid, inst, client, rev) => E.completionEventId(pid, inst, client, rev),
    mulberry32: (seed, n) => { const r = U.mulberry32(seed), out = []; for (let i = 0; i < n; i++) out.push(r()); return out; },
    ceilTo15: m => U.ceilTo15(m),
    isValidTimeZone: tz => U.isValidTimeZone(tz),
    localParts: (t, tz) => U.localParts(t, tz),
    tzOffsetSec: (t, tz) => U.tzOffsetSec(t, tz),
    zonedToEpoch: (y, mo, d, m, tz) => U.zonedToEpoch(y, mo, d, m, tz),
    ymdAdd: (ymd, n) => U.ymdAdd(ymd, n),
    ymdKey: ymd => U.ymdKey(ymd),
    parseYmd: key => K.parseYmd(key),
    daysBetween: (a, b) => K.daysBetween(a, b),
    buildHorizon: (t, days, tz) => U.buildHorizon(t, days, tz),
    grid: (t, days, tz) => { const g = K.createGrid(U.buildHorizon(t, days, tz), tz); return { H0: g.H0, HN: g.HN, n: g.n, localMin: g.localMin, dayOf: g.dayOf }; },
    slotOf: (H0, t) => K.slotOf({ H0 }, t),
    slotCeil: (H0, t) => K.slotCeil({ H0 }, t),
    epochOf: (H0, s) => K.epochOf({ H0 }, s),
    SLOT_SEC: () => K.SLOT_SEC,
    SLOTS_PER_HOUR: () => K.SLOTS_PER_HOUR,
    dayEndAfter: (t, tz) => K.dayEndAfter({ timezone: tz }, t),
    weekKey: (t, tz) => { zone = tz; return weekKey(t); },
    weekBounds: (t, tz) => weekBounds(t, tz),
  };
  return cases.map(c => {
    if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
    let out;
    try { out = canon(FN[c.fn](...decode(c.args))); } catch (e) { out = { threw: (e && e.name) || 'Error' }; }
    return Object.assign({}, c, { out });
  });
}

/* In node: the engine text in a vm. */
function recordInNode(cases) {
  const E = engineVm.load();
  return runCases(E, E.__kernel, cases, pageFunctions());
}

/* In Chromium: the built page's own LifeDirectorEngine, plus the same engine text (with the kernel hook) evaluated in that page for
   the private helpers. The page must contain the engine text unchanged (tests/ld-kernel-golden.test.js checks it). */
async function recordInChromium(file, cases) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    await p.waitForFunction(() => !!(window.LifeDirectorEngine && window.LifeDirectorEngine.util), null, { timeout: 120000 });
    const out = await p.evaluate(({ kernel, cases, texts, runner }) => {
      const holder = {};
      new Function('self', kernel)(holder);
      const run = new Function('return ' + runner)();
      return { cases: run(window.LifeDirectorEngine, holder.LifeDirectorEngine.__kernel, cases, texts), version: navigator.userAgent };
    }, { kernel: engineVm.kernelSource(), cases, texts: pageFunctions(), runner: runCases.toString() });
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { cases: out.cases, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director time and hash kernel golden cases; see tools/gen_ld_kernel_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff as the cases it changed */
const serialize = (head, cases) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]}\n';

module.exports = { defineCases, FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, pageFunctions, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = defineCases();
  recordInChromium(file, cases).then(({ cases: recorded, browser }) => {
    const node = recordInNode(cases), differ = recorded.filter((c, i) => JSON.stringify(c) !== JSON.stringify(node[i])).map(c => c.id);
    if (differ.length) console.log('WARNING: node disagrees with Chromium on ' + differ.length + ' cases, e.g. ' + differ.slice(0, 5).join(', '));
    fs.writeFileSync(FILE, serialize(header(browser), recorded));
    const by = {};
    for (const c of recorded) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + recorded.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1); });
}
