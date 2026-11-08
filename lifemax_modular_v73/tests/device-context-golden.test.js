/* The device-context golden cases (fixtures/device-context-golden.json, written by tools/gen_device_context_golden.js) still describe the
   page. Every case is recorded again from the current page, in memory, and must come out byte for byte the same as the file, so the file
   cannot go stale: a change to the page's day keys, week period, time zone handling, Daily Hand checks, luck or rest-day rule either is a
   mistake or comes with the regenerated file (and the native adapters in android-wrapper/native/src/main/java/com/lifemax/rpg/core/adapters,
   which replay every case). Also checked without the page: the file's layout, unique ids, every family present, and every rule a case cites
   defined in docs/specs.
   Run: NODE_PATH=$(npm root -g) node tests/device-context-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_device_context_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_device_context_golden.js');
const SPECS = ['QUESTS_SPEC.md', 'ECONOMY_SPEC.md'].map(f => path.join(__dirname, '..', '..', 'docs', 'specs', f));
const FAMILIES = ['clock', 'clockmove', 'zonemove', 'exotic', 'facts', 'factsodd'];

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), stored = JSON.parse(raw);
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (at least ninety), each with a unique id', stored.length >= 90 && !dupes.length, dupes);
  ok('the file is written exactly as the generator writes it (one case per line)', gen.serialize(stored) === raw);
  const families = [...new Set(stored.map(c => c.family))];
  ok('every family has cases, and no other family exists', JSON.stringify(families) === JSON.stringify(FAMILIES), families);
  const defined = new Set(SPECS.flatMap(f => [...fs.readFileSync(f, 'utf8').matchAll(/\*\*((?:QST-|QD)\d+|[A-Z]+(?:-[A-Z]+)*-\d+)\*\*/g)].map(m => m[1])));
  const unknown = [...new Set(stored.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in QUESTS_SPEC.md or ECONOMY_SPEC.md', !unknown.length, unknown);
  const zones = new Set(stored.map(c => c.input.zone));
  ok('the clock matrix covers daylight saving, a missing midnight, UTC+14, UTC-11 and a skipped day',
    ['America/New_York', 'Europe/London', 'America/Santiago', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'Pacific/Apia'].every(z => zones.has(z)), [...zones]);
  const generated = gen.defineCases().map(c => c.id);
  ok('the generator defines the same cases as the file, in the same order', JSON.stringify(generated) === JSON.stringify(ids));
  const threw = stored.filter(c => c.family !== 'factsodd' && JSON.stringify(c.output).includes('"threw"')).map(c => c.id);
  ok('only the factsodd family records a check the page threw on', !threw.length, threw);

  let fresh = null;
  try { fresh = await gen.generate(require('./current-page.js')(process.argv[2])) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (fresh) {
    const changed = [];
    fresh.forEach((c, i) => { const a = JSON.stringify(c), b = JSON.stringify(stored[i]); if (a !== b) changed.push({ id: c.id, at: [...a].findIndex((ch, k) => ch !== b[k]) }) });
    ok('every case comes out of the page byte for byte as the file has it (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)', !changed.length && fresh.length === stored.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_device_context_golden.js if the change is intended' });
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
