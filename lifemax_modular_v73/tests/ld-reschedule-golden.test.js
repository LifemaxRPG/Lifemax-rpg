/* The Life Director rescheduling golden cases (fixtures/ld-reschedule-golden.json.gz, written by tools/gen_ld_reschedule_golden.js) still
   describe the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so the
   file cannot go stale; a change to rescheduleActivity, its hold or its placement by radius either is a mistake or comes with the
   regenerated file (and the native code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The recorder runs each case twice on fresh state and throws when the answers differ, so a pass also shows rescheduling deterministic. The
   same cases are recorded in node from the engine text in a vm: node computes Math.exp and Math.pow with fdlibm, the recording Chromium
   correctly rounded (LIFE_DIRECTOR_ENGINE_SPEC.md LDF-1, LD20), so node is only required to agree on most cases; the count is printed.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, that no case uses a zone outside the strict matrix, that only the steps built to throw do, and
   that the cases reach what they are there for (every trigger, every refusal, every radius, every kind of action).
   Run: NODE_PATH=$(npm root -g) node tests/ld-reschedule-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_reschedule_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_reschedule_golden.js');
const { ZONES } = require('../tools/ld_reschedule_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const zonesOf = c => {
  const out = [], walk = (v, k) => {
    if (typeof v === 'string' && (k === 'timezone' || k === 'tz')) out.push(v);
    else if (Array.isArray(v)) v.forEach(x => walk(x)); else if (v && typeof v === 'object') for (const [kk, x] of Object.entries(v)) walk(x, kk);
  };
  walk(c.args);
  return out;
};
const missing = (want, have) => want.filter(x => !have.has(x));

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (thousands)', lines.length >= 2000);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const ids = new Set(), dupes = [], families = new Set(), rules = new Set(), zones = new Set(), threwWrong = [];
  const reasons = new Set(), refusals = new Set(), actions = new Set(), radii = new Set(), markers = new Set();
  let steps = 0;
  for (const l of lines) {
    const c = JSON.parse(l);
    if (ids.has(c.id)) dupes.push(c.id); ids.add(c.id);
    families.add(c.family);
    c.rule.split(' ').forEach(r => rules.add(r));
    zonesOf(c).forEach(z => zones.add(z));
    c.out.steps.forEach((s, k) => {
      steps++;
      const t = c.args[1][k].trigger || {};
      if (s.threw) { if (c.family !== 'refuse' && t.instanceId !== 'i_none') threwWrong.push(c.id + ' step ' + k); return; }
      if (s.rejectedMove) refusals.add(s.rejectedMove.reason);
      if (s.changeRecord) {
        reasons.add(t.type + ':' + (s.changeRecord.triggerReason || '-'));
        for (const a of s.changeRecord.actions) actions.add(a.action + (a.reason ? ':' + a.reason.replace(/^(MISSED|SKIPPED|MOVE|RESIDUAL|NEW_COMMITMENT|BLOCK_HOURS|BLACKOUT_DAY|SICK|TIME_CHANGED|QUEST_CHANGED)$/, 'TRIGGER') : ''));
        for (const r of s.changeRecord.radiusUsed) radii.add(r.radius);
      }
      for (const m of [s.engineInput, s.updatedSchedule]) if (m && (m.$same || m.$equal)) markers.add(m.$same ? '$same:' + m.$same : '$equal:' + m.$equal);
    });
  }
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), [...families]);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LD[A-Z]-\d+)\*\*/g)].map(m => m[1]));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ENGINE_SPEC.md', [...rules].every(r => defined.has(r)), [...rules].filter(r => !defined.has(r)));
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs.map(c => c.id), lines.map(l => JSON.parse(l).id))
    && defs.every((c, i) => sameText(c, (({ out, ...x }) => x)(JSON.parse(lines[i])))));
  ok('every zone a case uses is a strict zone of the matrix (' + [...zones].sort().join(', ') + ')', [...zones].every(z => ZONES.includes(z)), [...zones]);
  ok('only the steps built to throw do (refusals, and a plan with no session to name)', !threwWrong.length, threwWrong.slice(0, 10));
  const TRIGGERS = ['MISSED:MISSED_TASK_REPAIR', 'SKIPPED:USER_SKIP', 'RESIDUAL:PARTIAL_COMPLETION', 'MOVE:USER_MANUAL_MOVE', 'NEW_COMMITMENT:NEW_COMMITMENT_ADDED',
    'BLOCK_HOURS:TIME_BLOCKED', 'BLACKOUT_DAY:DAY_BLACKOUT', 'SICK:SICK_MODE_ACTIVATED', 'QUEST_CHANGED:QUEST_MODIFIED', 'TIME_CHANGED:-'];
  ok('the cases reach every trigger (' + steps + ' steps)', !missing(TRIGGERS, reasons).length, missing(TRIGGERS, reasons));
  ok('the cases reach every refusal of a move', !missing(['BLOCKED_TIME', 'OUTSIDE_PLAN', 'RECOVERY_PROTECTED'], refusals).length, [...refusals]);
  ok('the cases reach every radius', !missing([1, 2, 3, 4], radii).length, [...radii]);
  const ACTIONS = ['MISS', 'SKIP', 'MOVE', 'INSERT', 'COMPRESS', 'EXCUSE:TRIGGER', 'EXCUSE:PREREQUISITE_MOVED', 'EXCUSE:QUEST_DEADLINE_CHANGED', 'DELETE:QUEST_CLOSED', 'RELINK',
    'SHED:LOW_TIER_AFTER_DISRUPTION', 'SHED:DISPLACED_BY_HIGHER_PRIORITY'];
  ok('the cases reach every kind of action', !missing(ACTIONS, actions).length, missing(ACTIONS, actions));
  ok('the cases reach every result marker', !missing(['$same:engineInput', '$same:currentOutput', '$equal:engineInput'], markers).length, [...markers]);

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module exactly as the recorder runs it', html.includes(engineVm.engineSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_reschedule_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs).cases.map(c => JSON.stringify(c));
  const nodeDiffer = node.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
  ok('node (fdlibm exp and pow) agrees on most cases (' + (lines.length - nodeDiffer.length) + ' of ' + lines.length + ' agree)',
    nodeDiffer.length * 20 < lines.length, { others: nodeDiffer.slice(0, 8) });
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
