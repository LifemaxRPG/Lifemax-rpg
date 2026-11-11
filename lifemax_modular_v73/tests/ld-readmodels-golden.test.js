/* The Life Director read-model golden cases (fixtures/ld-readmodels-golden.json.gz, written by tools/gen_ld_readmodels_golden.js) still
   describe the page. The store seeds are captured again from the built page, every state and case is defined again from them and recorded
   again in Chromium, and the whole file must come out byte for byte as it is, so it cannot go stale; a change to the plan's days, the day
   blocks, the live timeline, what is happening now or the week either is a mistake or comes with the regenerated file (and the native read
   models in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels, which replay every case). The page words the
   native side reads (android-wrapper/native/src/main/resources/lifedirector/readmodel-words.json) must be what the page exports now.
   The recorder runs each case twice on fresh copies of its state and throws when the answers differ, so a pass also shows the read models
   deterministic.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_READMODELS_SPEC.md, the throws limited to the states and zones built to throw, and that the cases reach what
   they are there for (clock-change days, calendar previews, every warning of nowState, a running timer, the XP preview off, unplaced work).
   Run: NODE_PATH=$(npm root -g) node tests/ld-readmodels-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_readmodels_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_readmodels_golden.js');
const logicVm = require('../tools/ld_logic_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_READMODELS_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

(async () => {
  const raw = gen.readFile(), lines = gen.readLines(), stateLines = gen.readStateLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"states":\[$/, '}')).header;
  ok('the file holds ' + stateLines.length + ' states and ' + lines.length + ' cases (thousands)', lines.length >= 2000 && stateLines.length >= 100);
  ok('the file is written exactly as the generator writes it (one state or case per line, gzip of that text)', gen.serialize(head, stateLines, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const stateIds = new Set(stateLines.map(l => JSON.parse(l).state));
  ok('every case names a state of the file', meta.every(c => stateIds.has(c.state)));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(RM-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_READMODELS_SPEC.md', !unknown.length, unknown);

  /* only the states built to throw do: the seeds without a plan (TypeError: no schedule) and a zone that does not exist (RangeError) */
  const threw = meta.flatMap(c => Object.entries(c.out).filter(([, v]) => v && v.threw).map(([k, v]) => c.id + ':' + k + '=' + v.threw));
  const built = /^[a-z]+\/(fresh|unfinished|v0)\/[^:]*:(days|unplanned|blocks|week)=TypeError$|\/unknown(#\d+)?:((days|timeline|now|week|native)=RangeError|(unplanned|blocks)=TypeError)$/;
  ok('only the sections built to throw do (' + threw.length + ')', threw.every(t => built.test(t)), threw.filter(t => !built.test(t)).slice(0, 8));
  const seen = new Set();
  for (const c of meta) {
    const o = c.out;
    if (Array.isArray(o.days) && o.days.some(d => d.slots !== 96)) seen.add('clock-change day');
    if (Array.isArray(o.blocks) && o.blocks.flat().some(b => b.clipped)) seen.add('clipped block');
    if (Array.isArray(o.blocks) && o.blocks.flat().some(b => b.mealStartEpoch !== undefined)) seen.add('meal');
    if (Array.isArray(o.blocks) && o.blocks.flat().some(b => b.mealStartEpoch !== undefined && b.mealStartEpoch !== b.startEpoch)) seen.add('meal cut at midnight');
    if (Array.isArray(o.timeline) && o.timeline.some(b => b.mealStartEpoch !== undefined && b.mealStartEpoch === b.startEpoch && b.clipped)) seen.add('meal joined again');
    if (Array.isArray(o.blocks) && o.blocks.flat().some(b => b.inst && b.inst[0] === 'h')) seen.add('history session');
    if (o.now && !o.now.threw) {
      if (o.now.kick) seen.add('kick ' + o.now.kick.kind);
      if (o.now.soon) seen.add('soon');
      if (o.now.running) seen.add('running timer');
      if (o.now.isSes) seen.add('in a session');
      if (!o.now.cur) seen.add('nothing now');
      if (o.now.alert === 'soon') seen.add('alert soon');
    }
    if (o.native === null) seen.add('no plan');
    if (o.native && !o.native.threw) { seen.add(o.native.yield ? 'xp yield' : 'no xp yield'); if (o.native.fit.unscheduled) seen.add('unplaced work'); }
    if (o.calendar) for (const x of o.calendar) if (Array.isArray(x.days) && Array.isArray(x.blocks)) seen.add('calendar day');
    if (c.host === 'off') seen.add('host off');
    if (o.xp && o.xp.some(x => x.out === null)) seen.add('xp preview null');
  }
  const want = ['clock-change day', 'clipped block', 'meal', 'meal cut at midnight', 'meal joined again', 'history session', 'kick late', 'kick ending', 'soon', 'alert soon', 'running timer', 'in a session',
    'nothing now', 'no plan', 'xp yield', 'no xp yield', 'unplaced work', 'calendar day', 'host off'];
  ok('the cases reach every outcome', want.every(w => seen.has(w)), want.filter(w => !seen.has(w)));
  ok('LifeMax not connected: no XP preview is asked for', meta.filter(c => c.host === 'off').every(c => !c.out.xp.length));
  meta.length = 0;

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the controller module exactly as the recorder runs it', html.includes(logicVm.appSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changedStates = chromium.stateLines.map((l, i) => (l === stateLines[i] ? null : JSON.parse(l).state)).filter(Boolean);
    ok('every state is defined again from the page byte for byte as the file has it (' + chromium.stateLines.length + ')',
      !changedStates.length && chromium.stateLines.length === stateLines.length, changedStates.slice(0, 8));
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : chromium.cases[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_readmodels_golden.js if the change is intended' });
    ok('the native read models\' words are what the page exports', fs.readFileSync(gen.WORDS_FILE, 'utf8') === gen.wordsText(chromium.words),
      { hint: 'regenerate with node tools/gen_ld_readmodels_golden.js' });
    if (chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
