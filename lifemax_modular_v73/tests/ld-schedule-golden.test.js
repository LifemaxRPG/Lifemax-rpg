/* The Life Director schedule golden cases (fixtures/ld-schedule-golden.json.gz, written by tools/gen_ld_schedule_golden.js) still describe
   the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so the file
   cannot go stale; a change to the engine's repair, output or validation code either is a mistake or comes with the regenerated file (and
   the native code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The recorder runs each case twice on fresh state and throws when the answers differ, so a pass also shows the plan deterministic. The
   same cases are recorded in node from the engine text in a vm: node's JavaScript engine computes Math.exp and Math.pow with fdlibm, the
   recording Chromium correctly rounded (LIFE_DIRECTOR_ENGINE_SPEC.md LDF-1, LD20), and a score one bit apart can move a session, so node
   is only required to agree on every validate and hash case (they read no exp or pow) and on most of the others; the count is printed.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, that no case uses a zone outside the strict matrix, that the stage calls the recorder puts into
   generateSchedule leave its result unchanged, and that the cases reach what they are there for (repairs that hit their budget, every
   violation code of validateSchedule).
   Run: NODE_PATH=$(npm root -g) node tests/ld-schedule-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_schedule_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_schedule_golden.js');
const { ZONES, SCENARIO_NAMES } = require('../tools/ld_schedule_cases.js');
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
const withoutTime = o => Object.assign({}, o, { computeTimeMs: 0 });

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (thousands)', lines.length >= 2500);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => { const c = JSON.parse(l); return { id: c.id, family: c.family, rule: c.rule, fn: c.fn, args: c.args, out: c.out }; });
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LD[A-Z]-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ENGINE_SPEC.md', !unknown.length, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, meta.map(({ out, ...c }) => c)));
  /* the hash cases name zones only as data, and validate's bad-zone case names a zone that does not exist on purpose */
  const used = new Set(meta.filter(c => c.family !== 'hash' && !/bad-zone$/.test(c.id)).flatMap(zonesOf)), outside = [...used].filter(z => !ZONES.includes(z));
  ok('every zone a case uses (but the hash cases and validate\'s bad zone) is a strict zone of the matrix (' + [...used].sort().join(', ') + ')', !outside.length, outside);
  const threw = meta.filter(c => c.out && c.out.threw).map(c => c.id);
  ok('only the cases built to throw do (' + threw.join(', ') + ')', threw.every(id => /no-links|phases-object|no-quest-links|overlap-locked|null-item|planned-text|no-input|bad-zone/.test(id)), threw);
  const budget = meta.filter(c => c.fn === 'generate' && c.out.iterations >= 400).length;
  ok('some repairs spend their whole budget of 400 moves (' + budget + ' do)', budget >= 10);
  const codes = new Set(meta.flatMap(c => (c.fn === 'validate' ? [c.out] : c.fn === 'generate' ? [c.out.validation] : [])
    .filter(v => v && v.violatedInvariants).flatMap(v => v.violatedInvariants.map(x => x.code).concat(v.warnings.map(x => x.code)))));
  const want = ['I1', 'I1_OVERLAP', 'I1_SLEEP_CROSS', 'I3', 'I5', 'I7', 'I10', 'H_DEADLINE', 'H_WINDOW', 'H_SPACING', 'H_DAILY_CAP', 'RECOVERY_SHORTFALL', 'SLEEP_BELOW_FLOOR', 'SLEEP_COMMITMENT_OVERLAP'];
  ok('the cases reach every violation and warning code of validateSchedule', want.every(c => codes.has(c)), want.filter(c => !codes.has(c)));
  const moves = new Set(meta.filter(c => c.fn === 'generate' && c.out.repair).flatMap(c => c.out.repair.moves.map(m => m.type)));
  ok('the cases reach every repair move (' + [...moves].sort().join(', ') + ')', ['COMPRESSION', 'EVICT', 'SWAP'].every(m => moves.has(m)), [...moves]);
  meta.length = 0;

  const plain = engineVm.load(engineVm.DEMAND), hooked = engineVm.load(engineVm.SCHEDULE, engineVm.scheduleSource);
  const moved = SCENARIO_NAMES.filter(n => !sameText(withoutTime(plain.generateSchedule(plain.buildScenario(n, 1).input)),
    withoutTime(hooked.generateSchedule(hooked.buildScenario(n, 1).input))));
  ok('the recorder\'s stage calls change no plan: the ten scenarios plan the same with and without them', !moved.length, moved);

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module exactly as the recorder runs it', html.includes(engineVm.engineSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_schedule_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs).cases.map(c => JSON.stringify(c));
  const nodeDiffer = node.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
  const strict = nodeDiffer.filter(id => /^(validate|hash)\//.test(id));
  ok('node (fdlibm exp and pow) agrees on every validate and hash case and on most others (' + (lines.length - nodeDiffer.length) + ' of ' + lines.length + ' agree)',
    !strict.length && nodeDiffer.length * 20 < lines.length, { strict, others: nodeDiffer.slice(0, 8) });
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
