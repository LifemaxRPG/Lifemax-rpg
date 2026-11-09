/* Golden cases for the Life Director engine's whole plan (WP-LD5: everything generateSchedule does after the construction: the repair
   with its snapshots and roll-backs, the fill pass, the rejections, explanations, segments, capacity, recovery blocks, the goal allocation,
   quest coverage, the output and its inputHash; and validateSchedule), recorded from the real page in Chromium, so that the native code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it gives the same answer for every case.
   Regenerate when the engine changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_schedule_golden.js [built-page.html]
   Output: tests/fixtures/ld-schedule-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-schedule-golden.test.js records every case again, in Chromium and in node, and fails when Chromium disagrees with the file.
   The cases are defined in tools/ld_schedule_cases.js; the method is WP-LD1's: the engine text in a vm with its private helpers hooked
   (tools/ld_engine_vm.js, SCHEDULE, scheduleSource), run inside the built page in Chromium and in node. scheduleSource puts three stage
   calls into generateSchedule that only look (SCHEDULE_STAGES there): at the start of each repair target, after the repair, after the
   fill pass.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  scenario | seeds | dst | rules | fuzz | small | tight | replan | repair | validate | hash.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      what was called:
             generate: args [input]: generateSchedule(input) (engine.js:1205-1307); out is its whole result except computeTimeMs (a
               measured time, LIFE_DIRECTOR_NATIVE_PLAN.md 4.2(7));
             repair: args [input]: generateSchedule(input), watched: out is {targets, repaired, filled}: at the start of each repair
               target the target, the repair's counts and the state (STATE below), after the repair its log, the state and every
               placement's selection record, after the fill pass the state and the records again (engine.js:1291-1376);
             validate: args [v]: validateSchedule(v) (engine.js:1537-1614), v without the engine state (its fairness reads 1);
             hash: args [input]: inputHash(input) (engine.js:1529-1532).
     args    the arguments, as JSON.
     out     the result as JSON (canon below: typed arrays as arrays, members holding undefined left out, NaN, -0 and the infinities as
             {$num}), or {threw: <error name>}.
   Every case is run twice on fresh state and must give the same answer both times (the recorder throws otherwise).

   What is held still: nothing here reads a clock or a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_schedule_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-schedule-golden.json.gz');

/* Runs every case; E is the hooked engine (tools/ld_engine_vm.js scheduleSource), K its __kernel. Self-contained: it is also sent into the
   page. */
function runCases(E, K, cases) {
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (ArrayBuffer.isView(v)) return Array.from(v, canon);
    if (v instanceof Map) return [...v].map(canon);
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const sum = a => { let s = 0; for (let i = 0; i < a.length; i++) s += a[i]; return s; };
  /* STATE (copied when taken: the engine keeps changing its objects): what the repair changes and a roll-back must give back: the sessions in order (id @ start + slots), each task's placed flag
     and cover, the fairness ledger, each waking day's counts, sessions and keys, the spacing ledger, the slot codes, and the sums of the
     drain and free-run arrays. */
  const state = S => ({
    instances: S.instances.map(i => i.instanceId + '@' + i.startEpoch + '+' + i.durationSlots + (i.linkedQuestRequirementIds.length ? '#' + i.linkedQuestRequirementIds.length : '')),
    placed: S.tasks.map(t => (t.isPlaced ? (t.coveredBy ? 'c' : '1') : t.displacedBy ? 'd' : '0')).join(''),
    fair: { goalMin: Object.assign({}, S.fair.goalMin), total: S.fair.total },
    bw: S.bw.map(b => [b.goalMin, b.highCount, b.instances.map(i => i.instanceId).join(','), Object.assign({}, b.perKey)]),
    spacing: [...S.spacing].map(([k, v]) => [k, v.slice()]), type: Array.from(S.g.type).join(''),
    sums: [sum(S.g.drainP), sum(S.g.drainC), sum(S.g.runLen), sum(S.g.runBack), sum(S.g.taskIP), sum(S.g.taskIC), sum(S.g.taskOwner)]
  });
  const records = m => [...m].map(([id, r]) => Object.assign({ id }, r));
  const repair = input => {
    const targets = [];
    let repaired = null, filled = null;
    K.stage((name, x) => {
      if (name === 'target') targets.push({ t: x.t.id, tier: x.t.tier, placed: x.t.isPlaced, iterations: x.S.iterations,
        log: [x.log.attempts, x.log.successes, x.log.rollbacks, x.log.changed], state: state(x.S) });
      else if (name === 'repaired') repaired = { log: JSON.parse(JSON.stringify(x.repairLog)), iterations: x.S.iterations, state: state(x.S), records: records(x.placedMeta) };
      else if (name === 'filled') filled = { state: state(x.S), records: records(x.placedMeta) };
    });
    try { E.generateSchedule(input); } finally { K.stage(null); }
    return { targets, repaired, filled };
  };
  const FN = {
    generate: input => { const out = E.generateSchedule(input); delete out.computeTimeMs; return out; },
    repair,
    validate: v => E.validateSchedule(v),
    hash: input => K.inputHash(input),
  };
  const once = c => { try { return JSON.stringify(canon(FN[c.fn](...JSON.parse(JSON.stringify(c.args))))); } catch (e) { return JSON.stringify({ threw: (e && e.name) || 'Error' }); } };
  return cases.map(c => {
    if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
    const out = once(c);
    if (once(c) !== out) throw new Error('not deterministic: ' + c.id);
    return Object.assign({}, c, { out: JSON.parse(out) });
  });
}

/* In node: the engine text in a vm. */
function recordInNode(cases) {
  const E = engineVm.load(engineVm.SCHEDULE, engineVm.scheduleSource);
  return { cases: runCases(E, E.__kernel, cases) };
}

/* In Chromium: the same engine text with the hooks, evaluated in the built page (which must contain the engine text unchanged; the web test
   checks it), so the page's own JavaScript engine computes every value. Cases go in parts, and each part's answer comes back as text and
   is kept as text, so memory stays low. */
async function recordInChromium(file, cases, onPart) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    await p.waitForFunction(() => !!(window.LifeDirectorEngine && window.LifeDirectorEngine.util), null, { timeout: 120000 });
    await p.evaluate(({ src, runner }) => {
      const holder = {};
      new Function('self', src)(holder);
      window.__ldSchedule = { E: holder.LifeDirectorEngine, run: new Function('return ' + runner)() };
    }, { src: engineVm.scheduleSource(engineVm.SCHEDULE), runner: runCases.toString() });
    const out = [];
    for (let i = 0; i < cases.length; i += 50) {
      const part = await p.evaluate(cs => { const L = window.__ldSchedule; return L.run(L.E, L.E.__kernel, cs).map(c => JSON.stringify(c)); }, cases.slice(i, i + 50));
      if (onPart) onPart(i + part.length);
      out.push(...part);
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { lines: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director schedule golden cases; see tools/gen_ld_schedule_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff of the unzipped text as the cases it changed; lines are the cases' JSON texts */
const serialize = (head, lines) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + lines.join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
/* The file's case lines (each a case's JSON text), without parsing the whole file at once. */
const readLines = () => readFile().split('\n').filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));
const definitions = () => defineCases(engineVm.load(engineVm.SCHEDULE, engineVm.scheduleSource));

module.exports = { definitions, FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, readFile, readLines, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  console.log('defined ' + cases.length + ' cases');
  recordInChromium(file, cases, n => { if (n % 500 === 0) console.log('recorded ' + n); }).then(({ lines, browser }) => {
    const text = serialize(header(browser), lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const node = recordInNode(cases).cases.map(c => JSON.stringify(c)), differ = node.map((l, i) => (l === lines[i] ? null : cases[i].id)).filter(Boolean);
    console.log('node (fdlibm exp and pow) disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
