/* Golden cases for the Life Director engine's availability layer (WP-LD2: rules, preferences, sleep, the painted grid, wake cycles,
   maintenance and meals), recorded from the real page in Chromium, so that the native availability code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it gives the same answer for every case.
   Regenerate when the engine's availability code changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_availability_golden.js [built-page.html]
   Output: tests/fixtures/ld-availability-golden.json. tests/ld-availability-golden.test.js records every case again, in Chromium and in node,
   and fails when either disagrees with the file. The cases are defined in tools/ld_availability_cases.js; the method is WP-LD1's
   (tools/gen_ld_kernel_golden.js): the engine text in a vm with its private helpers hooked (tools/ld_engine_vm.js, AVAILABILITY), run
   inside the built page in Chromium and in node.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  helper | rule | applies | ruleon | materialize | prefs | sleep | paint | avail | scenario | dst | fuzz.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      what was called (FN below):
             clamp, cmpStr, normalizeRule, defaultPreferences, normalizePreferences: the engine's own function with args as given;
             ruleAppliesOn: normalizeRule(args[0], 0), then ruleAppliesOn on args[2] dates from args[1] (ymdAdd by 0, 1, ...);
               out is one character per date, '1' applies, '0' does not;
             ruleOn: the same with the page's own copy of the question (logic.js ruleOn, taken from the source text) on the raw rule;
             materializeRule: normalizeRule(args[0], 0) materialised over buildHorizon(args[1], args[2], args[3]); each interval
               without its `rule` member (the normalised rule itself);
             deriveSleep: args [rules, start, days, zone, prefs, sleepRequirement, hardIntervals]: rules through normalizeRule, the days of
               buildHorizon, prefs through normalizePreferences; the intervals without `rule`;
             paint: args [start, days, zone, ops]: a fresh createGrid(buildHorizon(...)), then paint(grid, ...op) for each op; out is
               {returns, ...grid} with the grid summary below;
             deriveAvailability: args [params]; out is {tz, ruleIds, warnings, wakeCycles, ...grid, preferred}; `prefs` and `rules` of
               the result are normalizePreferences and normalizeRule outputs, which their own families pin, so only the rules' ids
               (their order) are kept.
             The grid summary: H0, HN, n, blocks (g.blocks as painted), and the typed arrays type, load, tag, bwcOf (and preferredMask
             as `preferred`) written as runs, 'value*count' joined by ',' (`0*96,4*32`).
     args    the arguments, as JSON; {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'} is that number.
     out     the return value as JSON, numbers in the same {$num} form when JSON cannot hold them, or {threw: <error name>}.

   What is held still: nothing here reads a clock or a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path');
const { defineCases, FAMILIES } = require('./ld_availability_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-availability-golden.json');
const SRC = path.join(__dirname, '..', 'src', 'features', 'life-director');

/* The text of `function <name>(` up to its matching brace, and the one line `const <name> = ...`. */
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
function constLine(file, name) {
  const text = fs.readFileSync(path.join(SRC, file), 'utf8'), at = text.indexOf('const ' + name + ' = ');
  if (at < 0) throw new Error(file + ': const ' + name + ' not found');
  return text.slice(at, text.indexOf('\n', at));
}
/* The page's own copy of the rule question (logic.js ruleOn and the ymdDow it calls; plan defect D3). */
const pageFunctions = () => ({ ruleOn: constLine('logic.js', 'ymdDow') + '\n' + functionText('logic.js', 'ruleOn') });

/* Runs every case; E is the engine (window.LifeDirectorEngine or the vm's), K its hook. Self-contained: it is also sent into the page. */
function runCases(E, K, cases, texts) {
  const U = E.util;
  const ruleOn = new Function('U', texts.ruleOn + '\nreturn ruleOn;')(U);
  const NUMS = { NaN: NaN, Infinity: Infinity, '-Infinity': -Infinity, '-0': -0 };
  const decode = v => (Array.isArray(v) ? v.map(decode) : v && typeof v === 'object'
    ? ('$num' in v ? NUMS[v.$num] : Object.fromEntries(Object.entries(v).map(([k, x]) => [k, decode(x)]))) : v);
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (ArrayBuffer.isView(v)) return Array.from(v, canon);
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const runs = a => { const out = []; let i = 0; while (i < a.length) { let j = i; while (j < a.length && a[j] === a[i]) j++; out.push(a[i] + '*' + (j - i)); i = j; } return out.join(','); };
  const grid = g => ({ H0: g.H0, HN: g.HN, n: g.n, type: runs(g.type), load: runs(g.load), tag: runs(g.tag), bwcOf: runs(g.bwcOf), blocks: g.blocks });
  const strip = ({ rule, ...rest }) => rest;
  const dates = (start, count, test) => { let s = ''; for (let i = 0; i < count; i++) s += test(U.ymdAdd(start, i)) ? '1' : '0'; return s; };
  const FN = {
    clamp: (x, lo, hi) => K.clamp(x, lo, hi),
    cmpStr: (a, b) => K.cmpStr(a, b),
    normalizeRule: (...a) => K.normalizeRule(...a),
    ruleAppliesOn: (r, start, count) => { const rule = K.normalizeRule(r, 0); return dates(start, count, ymd => K.ruleAppliesOn(rule, ymd)); },
    ruleOn: (r, start, count) => dates(start, count, ymd => ruleOn(r, ymd)),
    materializeRule: (r, t, days, tz) => K.materializeRule(K.normalizeRule(r, 0), K.buildHorizon(t, days, tz), tz).map(strip),
    defaultPreferences: () => E.defaultPreferences(),
    normalizePreferences: (...a) => E.normalizePreferences(...a),
    deriveSleep: (rules, t, days, tz, prefs, req, hard) => {
      const out = K.deriveSleep(rules.map(K.normalizeRule), K.buildHorizon(t, days, tz), tz, E.normalizePreferences(prefs), req, hard);
      return { intervals: out.intervals.map(strip), warnings: out.warnings };
    },
    paint: (t, days, tz, ops) => {
      const g = K.createGrid(K.buildHorizon(t, days, tz), tz), returns = ops.map(([s, e, code, meta]) => K.paint(g, s, e, code, meta));
      return Object.assign({ returns }, grid(g));
    },
    deriveAvailability: params => {
      const out = E.deriveAvailability(params);
      return Object.assign({ tz: out.tz, ruleIds: out.rules.map(r => r.id), warnings: out.warnings, wakeCycles: out.wakeCycles }, grid(out.grid),
        { preferred: runs(out.grid.preferredMask) });
    },
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
  const E = engineVm.load(engineVm.AVAILABILITY);
  return runCases(E, E.__kernel, cases, pageFunctions());
}

/* In Chromium: the built page's own LifeDirectorEngine for the exported functions, plus the same engine text (with the hook) evaluated in
   that page for the private helpers. The page must contain the engine text unchanged (tests/ld-availability-golden.test.js checks it). */
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
      return run(window.LifeDirectorEngine, holder.LifeDirectorEngine.__kernel, cases, texts);
    }, { kernel: engineVm.kernelSource(engineVm.AVAILABILITY), cases, texts: pageFunctions(), runner: runCases.toString() });
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { cases: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director availability golden cases; see tools/gen_ld_availability_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff as the cases it changed */
const serialize = (head, cases) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]}\n';
const definitions = () => defineCases(engineVm.load(engineVm.AVAILABILITY));

module.exports = { definitions, FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, pageFunctions, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  recordInChromium(file, cases).then(({ cases: recorded, browser }) => {
    const node = recordInNode(cases), differ = recorded.filter((c, i) => JSON.stringify(c) !== JSON.stringify(node[i])).map(c => c.id);
    if (differ.length) console.log('WARNING: node disagrees with Chromium on ' + differ.length + ' cases, e.g. ' + differ.slice(0, 5).join(', '));
    fs.writeFileSync(FILE, serialize(header(browser), recorded));
    const by = {};
    for (const c of recorded) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + recorded.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1); });
}
