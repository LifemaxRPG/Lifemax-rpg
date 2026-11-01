/* The economy's golden cases (fixtures/economy-golden.json, written by tools/gen_economy_golden.js) still describe the page.
   Every case is recorded again from the current page, in memory, and must come out the same as the file, so the file cannot go
   stale: a change to XP, rested XP, caps, quests, Sparks or decay either is a mistake or comes with the regenerated file (and the
   spec, docs/specs/ECONOMY_SPEC.md, that the file is evidence for). Also checked without the page: every rule and defect ID in
   the spec has at least one case or a stated reason for none, and every case cites only IDs the spec defines.
   Run: NODE_PATH=$(npm root -g) node tests/economy-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_economy_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_economy_golden.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'ECONOMY_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};

/* equal as JSON values, whatever the order of an object's keys (arrays keep their order) */
function same(a, b) {
  if (a === b) return true;
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null || Array.isArray(a) !== Array.isArray(b)) return false;
  const ka = Object.keys(a), kb = Object.keys(b);
  return ka.length === kb.length && ka.every(k => Object.prototype.hasOwnProperty.call(b, k) && same(a[k], b[k]));
}
/* where two values first differ, as a path with both sides, for a readable failure */
function firstDifference(a, b, at = '') {
  if (same(a, b)) return null;
  if (typeof a === 'object' && typeof b === 'object' && a && b && Array.isArray(a) === Array.isArray(b)) {
    for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
      const d = firstDifference(a[k], b[k], at + (Array.isArray(a) ? '[' + k + ']' : '.' + k));
      if (d) return d;
    }
  }
  return { at: at || '(whole case)', file: a === undefined ? '(absent)' : a, page: b === undefined ? '(absent)' : b };
}

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), stored = JSON.parse(raw), spec = fs.readFileSync(SPEC, 'utf8');

  /* the file itself */
  const ids = stored.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('the file holds ' + stored.length + ' cases (several hundred), each with a unique id', stored.length >= 300 && !dupes.length, dupes);
  ok('the file is written exactly as the generator writes it (one case per line)', gen.serialize(stored) === raw);
  const threw = stored.filter(c => c.output.returns.some(r => r && typeof r === 'object' && 'threw' in r)).map(c => c.id);
  ok('no recorded call threw', !threw.length, threw);

  /* the file against the spec: rule IDs are defined as **ID** at the start of a rule, defects as - **Dnn** in section 20 */
  const rules = new Set([...spec.matchAll(/\*\*([A-Z]+(?:-[A-Z]+)*-\d+)\*\*/g)].map(m => m[1]));
  const defects = new Set([...spec.matchAll(/^- \*\*(D\d{2})\*\*/gm)].map(m => m[1]));
  const known = new Set([...rules, ...defects]), cited = new Set(stored.flatMap(c => c.rule.split(' ')));
  ok('the spec defines rule IDs (' + rules.size + ') and defects (' + defects.size + ')', rules.size > 50 && defects.size > 30);
  const unknown = [...cited].filter(r => !known.has(r));
  ok('every case cites only rule and defect IDs the spec defines', !unknown.length, unknown);
  const uncovered = [...known].filter(r => !cited.has(r) && !gen.NOT_RECORDED[r]);
  ok('every rule and defect in the spec has a case, or a reason for none in the generator (NOT_RECORDED)', !uncovered.length, uncovered);
  const needless = Object.keys(gen.NOT_RECORDED).filter(r => cited.has(r) || !known.has(r));
  ok('every reason for no case names an ID the spec defines and no case cites', !needless.length, needless);
  const generated = gen.defineCases();
  ok('the generator defines the same cases as the file, in the same order', same(generated.map(c => c.id), ids),
    { onlyInFile: ids.filter(id => !generated.some(c => c.id === id)).slice(0, 10), onlyInGenerator: generated.map(c => c.id).filter(id => !ids.includes(id)).slice(0, 10) });

  /* the file against the page */
  let fresh = null;
  try { fresh = await gen.generate(require('./current-page.js')(process.argv[2])) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (fresh) {
    ok('the page records the same ' + stored.length + ' cases, in the same order', same(fresh.map(c => c.id), ids));
    const changed = [];
    for (const c of stored) {
      const now = fresh.find(x => x.id === c.id);
      if (now && !same(c, now)) changed.push({ id: c.id, ...firstDifference(c, now) });
    }
    ok('every case comes out of the page as the file has it (' + (stored.length - changed.length) + ' of ' + stored.length + ' do)', !changed.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_economy_golden.js if the change is intended, and update docs/specs/ECONOMY_SPEC.md' });
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
