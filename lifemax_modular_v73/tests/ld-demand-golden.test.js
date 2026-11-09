/* The Life Director demand layer's golden cases (fixtures/ld-demand-golden.json.gz, written by tools/gen_ld_demand_golden.js) and its
   tables (android-wrapper/native/src/main/resources/lifedirector/demand-tables.json) still describe the page. Every case is recorded again
   from the built page in Chromium and must come out byte for byte as the file has it, and the tables exported again must equal the file
   the native code reads, so neither can go stale; a change to the engine's demand code either is a mistake or comes with the regenerated
   files (and the native demand code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The same cases are recorded in node from the engine text in a vm. Node's JavaScript engine computes Math.exp and Math.pow with fdlibm,
   the recording Chromium correctly rounded (LIFE_DIRECTOR_ENGINE_SPEC.md LDF-1, LD20), so node must agree on every case except in the
   last bit of those values: the float family's results and the prepared tasks' urgency (_urgBase), and nowhere else.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, and that no case uses a zone outside the strict matrix.
   Run: NODE_PATH=$(npm root -g) node tests/ld-demand-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_demand_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_demand_golden.js');
const { ZONES } = require('../tools/ld_demand_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const differing = (fresh, stored) => fresh.map((c, i) => (sameText(c, stored[i]) ? null : c.id)).filter(Boolean);
/* every zone a case names: as `tz`, `timezone`, or the zone argument of weeksOf */
const zonesOf = c => {
  const out = [], walk = (v, k) => {
    if (typeof v === 'string' && (k === 'timezone' || k === 'tz')) out.push(v);
    else if (Array.isArray(v)) v.forEach(x => walk(x)); else if (v && typeof v === 'object') for (const [kk, x] of Object.entries(v)) walk(x, kk);
  };
  walk(c.args);
  if (c.fn === 'weeksOf') out.push(c.args[2]);
  return out;
};
/* a case with every exp or pow value taken out: the float family's result and each prepared task's _urgBase */
const withoutExp = c => (c.family === 'float' ? Object.assign({}, c, { out: null }) : JSON.parse(JSON.stringify(c), (k, v) => (k === '_urgBase' ? undefined : v)));

(async () => {
  const raw = gen.readFile(), file = JSON.parse(raw), stored = file.cases;
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (several thousand), each with a unique id', stored.length >= 5000 && !dupes.length, dupes.slice(0, 10));
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

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module exactly as the recorder runs it', html.includes(engineVm.engineSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = differing(chromium.cases, stored);
    ok('every case comes out of the page in Chromium byte for byte as the file has it (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)',
      !changed.length && chromium.cases.length === stored.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_demand_golden.js if the change is intended' });
    ok('the demand tables the native code reads are the page\'s, byte for byte', fs.readFileSync(gen.TABLES, 'utf8') === chromium.tables,
      'regenerate with node tools/gen_ld_demand_golden.js');
  }
  const node = gen.recordInNode(defs), nodeDiffer = differing(node.cases, stored);
  const beyondExp = node.cases.filter((c, i) => !sameText(withoutExp(c), withoutExp(stored[i]))).map(c => c.id);
  ok('node (fdlibm exp and pow) agrees with the file on every case but in the exp and pow values (' + nodeDiffer.length + ' cases differ only there)',
    !beyondExp.length, beyondExp.slice(0, 8));
  ok('node exports the same tables', node.tables === fs.readFileSync(gen.TABLES, 'utf8'));
  if (chromium && chromium.browser + '' !== file.header.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + file.header.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
