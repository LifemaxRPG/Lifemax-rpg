/* The Life Director availability layer's golden cases (fixtures/ld-availability-golden.json, written by tools/gen_ld_availability_golden.js)
   still describe the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has
   it, so the file cannot go stale; a change to the engine's availability code either is a mistake or comes with the regenerated file
   (and the native availability code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The same cases are recorded in node from the engine text in a vm and must agree too: every zone is a strict zone of the WP-LD1 matrix,
   whose tz data was identical in every data set measured (LIFE_DIRECTOR_ENGINE_SPEC.md 6.2(8)).
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, that no case uses a tz-fragile zone, that the scenarios recorded in two zones only plan the
   office scenario's availability, and where the page's own copy of the rule question differs from the engine's (plan defect D3).
   Run: NODE_PATH=$(npm root -g) node tests/ld-availability-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_availability_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_availability_golden.js');
const { ZONES, SAME_AS_OFFICE } = require('../tools/ld_availability_cases.js');
const { TZ_FRAGILE } = require('../tools/ld_kernel_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

/* The `applies` cases (their tag before ':') where the page's copy of the rule question (logic.js ruleOn) answers differently from the
   engine's (normalizeRule + ruleAppliesOn): LIFE_DIRECTOR_ENGINE_SPEC.md LDA-9 explains each kind. The native replay checks the same set. */
const D3_DIFFERENCES = ['oddrotation0', 'oddrotation1', 'oddrotation2', 'oddrotation3', 'oddrotation4', 'oddrotation5', 'oddrotation7', 'oddrotation20',
  'oddrotation21', 'oddrotation22', 'legacy0', 'legacy1', 'legacy2', 'legacy3'];

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const differing = (fresh, stored) => fresh.map((c, i) => (sameText(c, stored[i]) ? null : c.id)).filter(Boolean);
/* every zone name a case passes (as an argument or as params.timezone / playerAvailability.timezone) */
const zonesOf = c => {
  const out = [], walk = (v, k) => {
    if (typeof v === 'string' && (k === 'timezone' || k === 'arg')) out.push(v);
    else if (Array.isArray(v)) v.forEach(x => walk(x)); else if (v && typeof v === 'object') for (const [kk, x] of Object.entries(v)) walk(x, kk);
  };
  if (['materializeRule', 'deriveSleep', 'paint'].includes(c.fn)) walk(c.args[c.fn === 'paint' ? 2 : 3], 'arg');
  if (c.fn === 'deriveAvailability') walk(c.args[0]);
  return out;
};

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), file = JSON.parse(raw), stored = file.cases;
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (several thousand), each with a unique id', stored.length >= 3000 && !dupes.length, dupes.slice(0, 10));
  ok('the file is written exactly as the generator writes it (one case per line)', gen.serialize(file.header, stored) === raw);
  const families = [...new Set(stored.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LD[A-Z]-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(stored.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ENGINE_SPEC.md', !unknown.length, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, stored.map(({ out, ...c }) => c)));
  const used = new Set(stored.flatMap(zonesOf)), outside = [...used].filter(z => !ZONES.includes(z) && z !== 'Mars/Phobos' && z !== 'utc');
  ok('every zone a case uses is a strict zone of the matrix (' + [...used].sort().join(', ') + ')', !outside.length && ![...used].some(z => TZ_FRAGILE.includes(z)), outside);

  /* the scenarios recorded in two zones only plan exactly the office scenario's availability, for any seed */
  const E = engineVm.load(engineVm.AVAILABILITY), pick = s => { const i = E.buildScenario(s.name, s.seed).input; return [i.playerAvailability.rules, i.playerAvailability.sleepRequirementMinutes]; };
  const notOffice = SAME_AS_OFFICE.filter(name => [...Array(20).keys()].some(k => !sameText(pick({ name, seed: k + 1 }), pick({ name: 'office', seed: k + 1 }))));
  ok('manygoals, deadlines, misses and workchange plan the office scenario\'s rules and sleep for seeds 1 to 20', !notOffice.length, notOffice);

  /* D3: where the page's copy of the rule question differs from the engine's */
  const engine = new Map(stored.filter(c => c.family === 'applies').map(c => [c.id.split('/').slice(2).join('/'), c.out]));
  const d3 = [...new Set(stored.filter(c => c.family === 'ruleon' && !sameText(engine.get(c.id.split('/').slice(2).join('/')), c.out))
    .map(c => c.id.split('/').slice(2).join('/').split(':')[0]))];
  ok('the page\'s copy of the rule question differs from the engine\'s exactly where the spec says (' + d3.length + ' rules, plan D3)', sameText(d3.sort(), [...D3_DIFFERENCES].sort()), d3);

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module and the page\'s rule question exactly as the recorder runs them',
    html.includes(engineVm.engineSource()) && gen.pageFunctions().ruleOn.split('\n').every(line => html.includes(line)));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = differing(chromium.cases, stored);
    ok('every case comes out of the page in Chromium byte for byte as the file has it (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)',
      !changed.length && chromium.cases.length === stored.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_availability_golden.js if the change is intended' });
  }
  const node = differing(gen.recordInNode(defs), stored);
  ok('node (its own ICU) agrees with the file on every case (' + node.length + ' differ)', !node.length, node.slice(0, 8));
  if (chromium && chromium.browser + '' !== file.header.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + file.header.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
