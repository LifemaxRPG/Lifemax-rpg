/* Golden cases for the Life Director engine's rescheduling (WP-LD5b: rescheduleActivity for every trigger, its hold placement, the
   placement by radius, the refusals of a move, the change record and the rebuilt output with its validation), recorded from the real page
   in Chromium, so that the native code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it
   gives the same answer for every case.
   Regenerate when the engine changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_reschedule_golden.js [built-page.html]
   Output: tests/fixtures/ld-reschedule-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-reschedule-golden.test.js records every case again, in Chromium and in node, and fails when Chromium disagrees with the file.
   The cases are defined in tools/ld_reschedule_cases.js; the method is WP-LD1's: the engine text in a vm (tools/ld_engine_vm.js, SCHEDULE,
   scheduleSource, whose stage calls stay off here), run inside the built page in Chromium and in node.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  scenario | seeds | dst | miss | move | residual | disrupt | quest | time | refuse | hold | radius | sequence.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      always reschedule: args [input, steps]. The runner plans input (generateSchedule, engine.js:1205-1307; not recorded), then
             plays each step {trigger, set?, store?}: set replaces top-level members of the engine input, store sets its
             existingSchedule to the current plan's sessions, and rescheduleActivity({engineInput, currentOutput, trigger})
             (engine.js:1619-1790) runs; its engineInput and updatedSchedule are the next step's.
     out     {steps}: per step {threw: <error name>}, or the result: rejectedMove, changeRecord and updatedSchedule without their
             measured computeTimeMs (LIFE_DIRECTOR_NATIVE_PLAN.md 4.2(7)), engineInput; a result member that is the very object passed in
             is {$same: 'engineInput' | 'currentOutput'}, an engine input equal to the one passed in (as JSON text) is {$equal:
             'engineInput'}. JSON canon as tools/gen_ld_schedule_golden.js (typed arrays as arrays, members holding undefined left out,
             NaN, -0 and the infinities as {$num}).
   Every case is run twice on fresh state and must give the same answer both times (the recorder throws otherwise).

   What is held still: nothing here reads a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_reschedule_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-reschedule-golden.json.gz');

/* Runs every case; E is the engine (tools/ld_engine_vm.js scheduleSource). Self-contained: it is also sent into the page. */
function runCases(E, K, cases) {
  void K;
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' }
      : Object.is(v, -0) ? { $num: '-0' } : v;
    if (ArrayBuffer.isView(v)) return Array.from(v, canon);
    if (v instanceof Map) return [...v].map(canon);
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) o[k] = canon(v[k]); return o; }
    return v === undefined ? null : v;
  };
  const json = v => JSON.parse(JSON.stringify(v));
  const reschedule = (input, steps) => {
    let engineInput = input, current = E.generateSchedule(input);
    const out = [];
    for (const s of steps) {
      if (s.set) engineInput = Object.assign({}, engineInput, json(s.set));
      if (s.store) engineInput = Object.assign({}, engineInput, { existingSchedule: json(current.scheduledInstances) });
      let r;
      try { r = E.rescheduleActivity({ engineInput, currentOutput: current, trigger: s.trigger === undefined ? undefined : json(s.trigger) }); } catch (e) { out.push({ threw: (e && e.name) || 'Error' }); continue; }
      const rec = { rejectedMove: r.rejectedMove, changeRecord: r.changeRecord && Object.assign({}, r.changeRecord) };
      if (rec.changeRecord) delete rec.changeRecord.computeTimeMs;
      rec.engineInput = r.engineInput === engineInput ? { $same: 'engineInput' } : JSON.stringify(r.engineInput) === JSON.stringify(engineInput) ? { $equal: 'engineInput' } : r.engineInput;
      if (r.updatedSchedule === current) rec.updatedSchedule = { $same: 'currentOutput' };
      else { rec.updatedSchedule = Object.assign({}, r.updatedSchedule); delete rec.updatedSchedule.computeTimeMs; }
      out.push(canon(rec));
      engineInput = r.engineInput; current = r.updatedSchedule;
    }
    return { steps: out };
  };
  const FN = { reschedule };
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

/* In Chromium: the same engine text, evaluated in the built page (which must contain the engine text unchanged; the web test checks it),
   so the page's own JavaScript engine computes every value. Cases go in parts, and each part's answer comes back as text and is kept as
   text, so memory stays low. */
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
      window.__ldReschedule = { E: holder.LifeDirectorEngine, run: new Function('return ' + runner)() };
    }, { src: engineVm.scheduleSource(engineVm.SCHEDULE), runner: runCases.toString() });
    const out = [];
    for (let i = 0; i < cases.length; i += 20) {
      const part = await p.evaluate(cs => { const L = window.__ldReschedule; return L.run(L.E, L.E.__kernel, cs).map(c => JSON.stringify(c)); }, cases.slice(i, i + 20));
      if (onPart) onPart(i + part.length);
      out.push(...part);
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { lines: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director rescheduling golden cases; see tools/gen_ld_reschedule_golden.js',
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
  recordInChromium(file, cases, n => { if (n % 200 === 0) console.log('recorded ' + n); }).then(({ lines, browser }) => {
    const text = serialize(header(browser), lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const node = recordInNode(cases).cases.map(c => JSON.stringify(c)), differ = node.map((l, i) => (l === lines[i] ? null : cases[i].id)).filter(Boolean);
    console.log('node (fdlibm exp and pow) disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
