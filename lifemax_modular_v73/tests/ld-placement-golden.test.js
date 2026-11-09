/* The Life Director placement golden cases (fixtures/ld-placement-golden.json.gz, written by tools/gen_ld_placement_golden.js) still
   describe the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so
   the file cannot go stale; a change to the engine's placement code either is a mistake or comes with the regenerated file (and the native
   placement code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The recorder runs each prepare and place case twice on fresh state and throws when the answers differ, so a pass also shows placement
   deterministic. The same cases are recorded in node from the engine text in a vm: node's JavaScript engine computes Math.exp and Math.pow
   with fdlibm, the recording Chromium correctly rounded (LIFE_DIRECTOR_ENGINE_SPEC.md LDF-1, LD20), and a score one bit apart can move a
   session, so node is only required to agree on every case whose answer reads no exp or pow (the anchors) and on most of the others; the
   count is printed.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, that no case uses a zone outside the strict matrix, and that the stage calls the recorder puts
   into generateSchedule leave its result unchanged (the hooked engine with no stage function set plans every scenario as the page does).
   Run: NODE_PATH=$(npm root -g) node tests/ld-placement-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_placement_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_placement_golden.js');
const { ZONES, SCENARIO_NAMES } = require('../tools/ld_placement_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const differing = (fresh, stored) => fresh.map((c, i) => (sameText(c, stored[i]) ? null : c.id)).filter(Boolean);
/* every zone a case names, as `tz` or `timezone` */
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
  const raw = gen.readFile(), file = JSON.parse(raw), stored = file.cases;
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (thousands), each with a unique id', stored.length >= 2500 && !dupes.length, dupes.slice(0, 10));
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(file.header, stored) === raw);
  const families = [...new Set(stored.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LD[A-Z]-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(stored.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ENGINE_SPEC.md', !unknown.length, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, stored.map(({ out, ...c }) => c)));
  const used = new Set(stored.flatMap(zonesOf)), outside = [...used].filter(z => !ZONES.includes(z));
  ok('every zone a case uses is a strict zone of the matrix (' + [...used].sort().join(', ') + ')', !outside.length, outside);
  const threw = stored.filter(c => c.out && c.out.threw).map(c => c.id);
  ok('only the cases built to throw do (' + threw.join(', ') + ')', threw.every(id => /no-links|phases-object/.test(id)), threw);

  const plain = engineVm.load(engineVm.DEMAND), hooked = engineVm.load(engineVm.PLACEMENT, engineVm.placementSource);
  const moved = SCENARIO_NAMES.filter(n => !sameText(withoutTime(plain.generateSchedule(plain.buildScenario(n, 1).input)),
    withoutTime(hooked.generateSchedule(hooked.buildScenario(n, 1).input))));
  ok('the recorder\'s stage calls change no plan: the ten scenarios plan the same with and without them', !moved.length, moved);

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module exactly as the recorder runs it', html.includes(engineVm.engineSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = differing(chromium.cases, stored);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)',
      !changed.length && chromium.cases.length === stored.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_placement_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs), nodeDiffer = differing(node.cases, stored);
  const anchorsDiffer = nodeDiffer.filter(id => id.startsWith('anchors/'));
  ok('node (fdlibm exp and pow) agrees on every anchors case and on most others (' + (stored.length - nodeDiffer.length) + ' of ' + stored.length + ' agree)',
    !anchorsDiffer.length && nodeDiffer.length * 5 < stored.length, { anchors: anchorsDiffer, others: nodeDiffer.slice(0, 8) });
  if (chromium && chromium.browser + '' !== file.header.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + file.header.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
