/* The Life Director store golden cases (fixtures/ld-store-golden.json.gz, written by tools/gen_ld_store_golden.js) still describe the
   page. The seed texts are captured again from the real page in Chromium (tools/ld_store_seeds.js: what Life Director itself writes to
   lifemax_director_state_v1 and its .bak), the cases defined again from them, and every case recorded again from the store module inside
   the built page (the browser family on a clean origin's real localStorage); the file must come out byte for byte the same, so it cannot go
   stale: a change to engine.js's store, or to what the page writes, either is a mistake or comes with the regenerated file (and the native
   code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/directorstore, which replays every case).
   The recorder runs each case twice on fresh storage and throws when the answers differ. node runs every case but the browser family a
   second time from the store text in a vm and must agree on all of them (the store reads no clock, zone or float function).
   Also checked without the page: the layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_STORE_SPEC.md, and that the cases reach what they are there for (every refusal and report kind, both
   rotations, the quarantine, a migration that throws, the quota).
   Run: NODE_PATH=$(npm root -g) node tests/ld-store-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_store_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_store_golden.js');
const { defineCases } = require('../tools/ld_store_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_STORE_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

(async () => {
  const { text, head, seeds, lines } = gen.readParts();
  ok('the file holds ' + lines.length + ' cases (thousands)', lines.length >= 3000);
  ok('the file is written exactly as the generator writes it (seeds, then one case per line, gzip of that text)', gen.serialize(head, seeds, lines) === text);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LST-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_STORE_SPEC.md', !unknown.length, unknown);
  ok('the cases defined from the file\'s seeds are the file\'s cases, in the same order', sameText(defineCases(seeds), meta.map(({ out, ...c }) => c)));

  /* what the cases must reach */
  const reports = meta.flatMap(c => (c.fn === 'load' ? [c.out.report] : c.fn === 'sequence' ? c.out.steps.map(s => s.report) : [])).filter(Boolean);
  const sources = new Set(reports.map(r => r.source));
  ok('loads end in every source: main, .bak, NEW, MEMORY_ONLY (' + [...sources].join(', ') + ')',
    ['lifemax_director_state_v1', 'lifemax_director_state_v1.bak', 'NEW', 'MEMORY_ONLY'].every(s => sources.has(s)));
  ok('loads quarantine main and .bak texts', ['', '_bak'].every(s => reports.some(r => r.quarantined === 'lifemax_director_quarantine_lifemax_director_state_v1' + s)));
  ok('loads report migrations from 0 and from -0', reports.some(r => r.migratedFrom === 0) && reports.some(r => r.migratedFrom && r.migratedFrom.$num === '-0'));
  const reasons = new Set(meta.filter(c => c.fn === 'validate').map(c => (c.out.reason || 'ok').replace(/ .*/, '')));
  ok('validate gives every answer (' + [...reasons].join(', ') + ')', ['ok', 'not', 'wrong', 'missing'].every(r => reasons.has(r)));
  const results = meta.flatMap(c => (c.fn === 'save' || c.fn === 'clear' ? [c.out.result] : c.fn === 'sequence' ? c.out.steps.map(s => s.result || s.saved) : [])).filter(Boolean);
  const errors = new Set(results.filter(r => !r.ok).map(r => r.error.replace(/:.*/, '')));
  ok('saves refuse for every reason (' + [...errors].length + ' kinds)', ['Browser storage is unavailable here; changes last only until this page closes.', 'Refusing to save invalid state',
    'Storage is full. Export a backup and clear old Life Director history.', 'Write verification failed', 'The storage is closed.', 'denied'].every(e => errors.has(e)), [...errors]);
  const rotated = meta.filter(c => c.out.ops && c.out.ops.some(o => o[0] === 'set' && o[1] === 'lifemax_director_state_v1.bak')).length;
  ok('saves rotate the main text into .bak (' + rotated + ' cases) and leave it when the text is unchanged', rotated > 100 && meta.some(c => c.id === 'seeds/fresh/over-itself' && c.out.ops.every(o => o[1] !== 'lifemax_director_state_v1.bak')));
  ok('a v0 state whose work is not a list makes the load and the import throw', meta.some(c => c.fn === 'load' && c.out.threw && c.out.threw.name === 'TypeError')
    && meta.some(c => c.fn === 'import' && c.out.threw && c.out.threw.name === 'TypeError'));
  ok('the browser\'s quota is met on both sides of its edge', ['browser/save-fills-exactly', 'browser/save-one-over'].every(id => meta.some(c => c.id === id))
    && meta.find(c => c.id === 'browser/save-fills-exactly').out.result.ok && !meta.find(c => c.id === 'browser/save-one-over').out.result.ok);
  meta.length = 0;

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the store module exactly as the recorder runs it', html.includes(engineVm.storeSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const seedsDiffer = Object.keys(seeds).filter(k => chromium.seeds[k] !== seeds[k]).concat(Object.keys(chromium.seeds).filter(k => !(k in seeds)));
    ok('the page writes the same seed texts as the file has (' + Object.keys(seeds).length + ')', !seedsDiffer.length, { differ: seedsDiffer });
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : chromium.cases[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_store_golden.js if the change is intended' });
  }
  const inNode = defineCases(seeds).filter(c => c.family !== 'browser'), node = gen.recordInNode(inNode, seeds), want = new Map(lines.map(l => [JSON.parse(l).id, l]));
  const nodeDiffer = inNode.filter((c, i) => node[i] !== want.get(c.id)).map(c => c.id);
  ok('node agrees on every case but the browser family (' + (inNode.length - nodeDiffer.length) + ' of ' + inNode.length + ')', !nodeDiffer.length, nodeDiffer.slice(0, 8));
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
