/* Golden cases for the Life Director engine's placement (WP-LD4: prepareState, the energy model, feasibility, scoring, the construction
   phase of generateSchedule up to its repair, anchors, and why a session stays unplaced), recorded from the real page in Chromium, so that
   the native placement code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) is accepted only when it gives the
   same answer for every case.
   Regenerate when the engine's placement code changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_placement_golden.js [built-page.html]
   Output: tests/fixtures/ld-placement-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-placement-golden.test.js records every case again, in Chromium and in node, and fails when Chromium disagrees with the file.
   The cases are defined in tools/ld_placement_cases.js; the method is WP-LD1's: the engine text in a vm with its private helpers hooked
   (tools/ld_engine_vm.js, PLACEMENT, placementSource), run inside the built page in Chromium and in node. placementSource also puts two
   stage calls into generateSchedule (STAGES there): the recorder reads the first scores of every task ('cells') and, when the
   construction phase ends, the whole state ('phase1'), then stops the plan by throwing, so the repair (WP-LD5) never runs.

   File: {header, cases}. A case is {id, family, rule, fn, args, out}:
     family  prepare | anchors | scenario | seeds | dst | rules | fuzz | small.
     rule    the LIFE_DIRECTOR_ENGINE_SPEC.md rule IDs it exercises, separated by spaces.
     fn      what was called:
             prepare: args [input]: prepareState(input) (engine.js:1163-1188); out is the state it builds (PREPARED below), with every slot
               array in full and energyAt for both axes at every slot;
             anchors: args [tasks, taskId]: anchorsDependents({tasks}, {taskId}) (engine.js:1106-1109), taskId null meaning undefined;
             place: args [input, probes]: generateSchedule(input) up to its repair (engine.js:1204-1306); out is PLACED below: the first
               scores, every placement in order with its selection numbers, the state when construction ends, anchorsDependents of every
               instance, buildRejection of every unplaced task on that state (engine.js:1385-1428), and, for the tasks probes names
               ('all' every active task; 'few' the first two and the first four others left unplaced; 'one' the first), evalAcross, evalInBwc with its reason tally in each
               waking day, and scorePlacement's detail at the best slot.
     args    the arguments, as JSON.
     out     the result as JSON (canon below: typed arrays as arrays, members holding undefined left out, NaN and the infinities as
             {$num}), or {threw: <error name>}.
   Every prepare and place case is run twice on fresh state and must give the same answer both times (the recorder throws otherwise).

   What is held still: nothing here reads a clock or a device zone; the page boots with the usual test save and is not touched. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { defineCases, FAMILIES } = require('./ld_placement_cases.js');
const engineVm = require('./ld_engine_vm.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-placement-golden.json.gz');

/* Runs every case; E is the hooked engine (tools/ld_engine_vm.js placementSource), K its __kernel. Self-contained: it is also sent into the
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
  /* what placement decides about an instance (the members commit copies from its task are the task's, WP-LD3's cases) */
  const INSTANCE = ['instanceId', 'taskId', 'templateId', 'goalId', 'linkedGoalIds', 'startEpoch', 'endEpoch', 'durationSlots', 'minDurationSlots', 'optDurationSlots',
    'tier', 'lifecycleState', 'isUserLocked', 'bwcId', 'IP', 'IC', 'spacingKey', 'compressed', 'origStartEpoch', 'kept'];
  const instance = i => {
    const o = {};
    for (const k of INSTANCE) if (k in i) o[k] = i[k];
    o.links = Array.isArray(i.linkedQuestRequirementIds) ? i.linkedQuestRequirementIds.map(x => (x && typeof x === 'object' ? x.questId + '|' + x.reqId : x)) : i.linkedQuestRequirementIds;
    return o;
  };
  const ids = list => list.map(i => i.instanceId);
  const cycles = S => S.bw.map(b => ({ id: b.id, perKey: b.perKey, goalMin: b.goalMin, highCount: b.highCount, instances: ids(b.instances),
    freeSlots: b.freeSlots, recoverySlots: b.recoverySlots, recoveryShortfall: b.recoveryShortfall }));
  const common = (S, x) => ({ nowSlot: S.nowSlot, history: ids(x.history), kept: ids(x.kept), displaced: x.displaced.map(d => [d.inst.instanceId, d.slotCode]),
    replannable: [...x.replannable.keys()], bw: cycles(S), instances: S.instances.map(instance), spacing: [...S.spacing], fair: S.fair,
    type: Array.from(S.g.type).join('') });
  /* PREPARED */
  const prepare = input => {
    const x = K.prepareState(input), S = x.S, g = S.g, slots = Array.from(g.type, (_, i) => i);
    return Object.assign(common(S, x), { sleepTarget: S.sleepTarget, checkin: S.checkin, sleepFactors: S.bw.map(b => b.sleepFactor), taskOwner: g.taskOwner,
      taskIP: g.taskIP, taskIC: g.taskIC, baseP: g.baseP, baseC: g.baseC, drainP: g.drainP, drainC: g.drainC, runLen: g.runLen, runBack: g.runBack,
      energyP: slots.map(i => K.energyAt(S, i, 0)), energyC: slots.map(i => K.energyAt(S, i, 1)) });
  };
  /* PLACED */
  const STOP = { stop: true };
  const probe = (S, t) => {
    const el = K.taskEarliestLatest(S, t), across = K.evalAcross(S, t);
    const days = el.blocked ? null : S.bw.map(bw => { const tally = {}, r = K.evalInBwc(S, t, bw, el, tally); return [r.score, r.slot, r.n, tally]; });
    return { id: t.id, el, across, days, detail: across.feasible ? K.scorePlacement(S, t, S.bw[across.bwc], across.slot, across.n, true) : null };
  };
  const place = (input, probes) => {
    let cells = null, out = null;
    K.stage((name, x) => {
      if (name === 'cells') { cells = x.active.map((t, k) => Array.from({ length: x.nB }, (_, b) => [x.cS[k * x.nB + b], x.cSlot[k * x.nB + b], x.cN[k * x.nB + b]])); return; }
      const S = x.S, unplaced = x.tasks.filter(t => !t.isPlaced);
      const chosen = probes === 'all' ? x.active : probes === 'one' ? x.active.slice(0, 1)
        : x.active.filter((t, k) => k < 2).concat(x.active.filter((t, k) => k >= 2 && !t.isPlaced).slice(0, 4));
      out = canon(Object.assign(common(S, x), { active: x.active.map(t => t.id), cells, placements: [...x.placedMeta].map(([id, m]) => Object.assign({ id }, m)),
        tasks: x.tasks.map(t => [t.id, t.isPlaced, t.coveredBy || null, t.origStartEpoch]),
        anchors: S.instances.map(i => K.anchorsDependents(S, i)), rejections: unplaced.map(t => K.buildRejection(S, t)), probes: chosen.map(t => probe(S, t)) }));
      throw STOP;
    });
    try { E.generateSchedule(input); } catch (e) { if (e !== STOP) throw e; } finally { K.stage(null); }
    return out;
  };
  const FN = {
    prepare,
    anchors: (tasks, taskId) => K.anchorsDependents({ tasks }, { taskId: taskId === null ? undefined : taskId }),
    place,
  };
  const once = c => { try { return JSON.stringify(canon(FN[c.fn](...JSON.parse(JSON.stringify(c.args))))); } catch (e) { return JSON.stringify({ threw: (e && e.name) || 'Error' }); } };
  return cases.map(c => {
    if (!(c.fn in FN)) throw new Error('no adaptor for ' + c.fn);
    const out = once(c);
    if (c.fn !== 'anchors' && once(c) !== out) throw new Error('not deterministic: ' + c.id);
    return Object.assign({}, c, { out: JSON.parse(out) });
  });
}

/* In node: the engine text in a vm. */
function recordInNode(cases) {
  const E = engineVm.load(engineVm.PLACEMENT, engineVm.placementSource);
  return { cases: runCases(E, E.__kernel, cases) };
}

/* In Chromium: the same engine text with the hooks, evaluated in the built page (which must contain the engine text unchanged; the web test
   checks it), so the page's own JavaScript engine computes every value. */
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
    const out = [];
    for (let i = 0; i < cases.length; i += 200) {
      const part = await p.evaluate(({ src, cases, runner }) => {
        const holder = {};
        new Function('self', src)(holder);
        const E = holder.LifeDirectorEngine, run = new Function('return ' + runner)();
        return JSON.stringify(run(E, E.__kernel, cases));
      }, { src: engineVm.placementSource(engineVm.PLACEMENT), cases: cases.slice(i, i + 200), runner: runCases.toString() });
      out.push(...JSON.parse(part));
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { cases: out, browser: browser.version() };
  } finally { await browser.close(); }
}

const header = browser => ({
  about: 'Life Director placement golden cases; see tools/gen_ld_placement_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node + ' ICU ' + process.versions.icu + ' tz ' + process.versions.tz,
});
/* one case per line, so a change shows up in a diff of the unzipped text as the cases it changed */
const serialize = (head, cases) => '{"header":' + JSON.stringify(head) + ',"cases":[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
const definitions = () => defineCases(engineVm.load(engineVm.PLACEMENT, engineVm.placementSource));

module.exports = { definitions, FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, readFile, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]), cases = definitions();
  recordInChromium(file, cases).then(({ cases: recorded, browser }) => {
    const node = recordInNode(cases), differ = recorded.filter((c, i) => JSON.stringify(c) !== JSON.stringify(node.cases[i])).map(c => c.id);
    if (differ.length) console.log('note: node (fdlibm exp and pow) disagrees with Chromium on ' + differ.length + ' cases, e.g. ' + differ.slice(0, 5).join(', '));
    const text = serialize(header(browser), recorded);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of recorded) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + recorded.length + ' cases ' + JSON.stringify(by));
  }).catch(e => { console.error(e); process.exit(1); });
}
