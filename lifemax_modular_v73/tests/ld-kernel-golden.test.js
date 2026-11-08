/* The Life Director time and hash kernel's golden cases (fixtures/ld-kernel-golden.json, written by tools/gen_ld_kernel_golden.js) still
   describe the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so
   the file cannot go stale; a change to the engine's time or hash code either is a mistake or comes with the regenerated file (and the
   native kernel in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case). The same cases are
   also recorded in node from the engine text in a vm: node's ICU is a different build, and every case where it disagrees with Chromium
   must be listed below with the reason, so a tz database difference between two JavaScript engines cannot pass unseen.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ENGINE_SPEC.md, the clock-change table the cases are built from, and that the built page carries the engine
   text the recorder hooks.
   Run: NODE_PATH=$(npm root -g) node tests/ld-kernel-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_kernel_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_kernel_golden.js');
const { CHANGES } = require('../tools/ld_kernel_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

/* Cases where node's ICU (77.1, tz 2025b at the time of writing) answers differently from Chromium's: Chromium's ICU knows the tz
   database's "Factory" zone, node's does not. The file holds Chromium's answer, which is the WebView's. */
const NODE_DIFFERS = {
  'zone/isValidTimeZone/505:Factory': 'node ICU has no Factory zone',
  'alias/tzOffsetSec/505:Factory@jan': 'node ICU has no Factory zone',
  'alias/tzOffsetSec/505:Factory@jul': 'node ICU has no Factory zone',
};

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const differing = (fresh, stored) => fresh.map((c, i) => (sameText(c, stored[i]) ? null : c.id)).filter(Boolean);

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), file = JSON.parse(raw), stored = file.cases;
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (several thousand), each with a unique id', stored.length >= 5000 && !dupes.length, dupes.slice(0, 10));
  ok('the file is written exactly as the generator writes it (one case per line)', gen.serialize(file.header, stored) === raw);
  const families = [...new Set(stored.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText(families, gen.FAMILIES), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LD[THN]-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(stored.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ENGINE_SPEC.md', !unknown.length, unknown);
  const defs = gen.defineCases();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, stored.map(({ out, ...c }) => c)));

  /* the table of clock changes the definitions use agrees with what the page recorded on either side of each change */
  const offsetAt = (zone, t) => { const c = stored.find(x => x.fn === 'tzOffsetSec' && x.args[0] === t && x.args[1] === zone); return c ? c.out : undefined };
  const wrongTable = CHANGES.filter(c => offsetAt(c.zone, c.at - 1) !== c.before * 60 || offsetAt(c.zone, c.at + 1) !== c.after * 60)
    .map(c => ({ zone: c.zone, at: c.at, before: offsetAt(c.zone, c.at - 1), after: offsetAt(c.zone, c.at + 1) }));
  ok('every clock change of the definitions\' table is where the page has it (' + CHANGES.length + ' changes)', !wrongTable.length, wrongTable);

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module and the week functions exactly as the recorder runs them',
    html.includes(engineVm.engineSource()) && html.includes(gen.pageFunctions().weekKey) && html.includes(gen.pageFunctions().weekBounds));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = differing(chromium.cases, stored);
    ok('every case comes out of the page in Chromium byte for byte as the file has it (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)',
      !changed.length && chromium.cases.length === stored.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_kernel_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs), nodeChanged = differing(node, stored);
  const unlisted = nodeChanged.filter(id => !(id in NODE_DIFFERS)), stale = Object.keys(NODE_DIFFERS).filter(id => !nodeChanged.includes(id));
  ok('node (its own ICU) agrees with the file on every case except the ' + Object.keys(NODE_DIFFERS).length + ' listed ones (' + nodeChanged.length + ' differ)',
    !unlisted.length && !stale.length, { unlisted: unlisted.slice(0, 8), noLongerDiffer: stale, node: process.versions.icu + ' tz ' + process.versions.tz });
  if (chromium && chromium.browser + '' !== file.header.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + file.header.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
