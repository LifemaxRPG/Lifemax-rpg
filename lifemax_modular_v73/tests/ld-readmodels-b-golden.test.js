/* The Life Director read-model golden cases, part b (fixtures/ld-readmodels-b-golden.json.gz, written by tools/gen_ld_readmodels_b_golden.js)
   still describe the page. The store seeds are captured again from the built page, every case is defined again from them and recorded
   again in Chromium, and the whole file must come out byte for byte as it is, so it cannot go stale; a change to Goals, Quests, Settings,
   My time, the forms, Something came up, Move, the work rules and shifts or the dial's handles and stops either is a mistake or comes with
   the regenerated file (and the native read models in
   android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels, which replay every case). The page words the native
   side reads (android-wrapper/native/src/main/resources/lifedirector/readmodel-words-b.json) must be what the page exports now, and every
   state a case names must be part a's state byte for byte (part b's file does not repeat them).
   The recorder runs each case twice on fresh copies of its state and throws when the answers differ, so a pass also shows the read models
   deterministic.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_READMODELS_SPEC.md, the throws limited to the states built to throw, and that the cases reach what they are
   there for.
   Run: NODE_PATH=$(npm root -g) node tests/ld-readmodels-b-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_readmodels_b_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_readmodels_b_golden.js');
const genA = require('../tools/gen_ld_readmodels_golden.js');
const logicVm = require('../tools/ld_logic_vm.js');
const QUICK = process.env.LD_QUICK === '1'; // the checks without the page only
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_READMODELS_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/* What the cases reach, by section: each name is an outcome a case must show. */
function reached(meta) {
  const seen = new Set(), add = s => seen.add(s);
  const v = x => x && !x.threw;
  for (const c of meta) {
    const o = c.out;
    if (v(o.goals)) {
      if (!o.goals.cards.length) add('goals: none');
      const zeroed = (c.set || []).some(e => e[0][1] === 'goalAllocation' && e[0][3] === 'targetShare' && e[1] === 0);
      if (zeroed && o.goals.cards.some(k => k.share && k.share.target > 0)) add('goals: repaired share');
      for (const k of o.goals.cards) {
        if (!k.share) add('goals: no share');
        if (k.warning) add('goals: warning');
        if (k.clarify) add('goals: clarify');
        if (k.paused) add('goals: paused');
        if (/^Custom/.test(k.sub)) add('goals: custom');
      }
    }
    if (v(o.quests)) {
      if (!o.quests.head.open) add('quests: not connected');
      if (o.quests.milestones) add('quests: milestones');
      if (o.quests.passive.length) add('quests: reminders');
      for (const r of o.quests.rows) { add('quests: ' + r.status.replace(/^\d+ /, 'n ')); if (r.why) add('quests: why'); add('quests: ' + r.when.replace(/ .*/, '')); }
    }
    if (o.settings && o.settings.threw) add('settings: throws');
    if (v(o.settings)) {
      if (o.settings.data.storageUnavailable) add('settings: storage unavailable');
      if (o.settings.lab.on) add('settings: lab');
      if (/last rejected/.test(o.settings.diagnostics.line)) add('settings: rejected plan');
      if (/no plan/.test(o.settings.diagnostics.line)) add('settings: no plan');
      if (/host missing/.test(o.settings.diagnostics.line)) add('settings: host missing');
    }
    if (v(o.time)) {
      add('time: ' + (o.time.first ? 'set-up' : 'planned'));
      if (o.time.sleep.weekend.on) add('time: weekend sleep');
      if (!o.time.sleep.saved) add('time: no sleep');
      if (!o.time.routines.meals.on) add('time: meals off');
      for (const r of o.time.commitments.rows) add('time: ' + r.line.split(' · ')[1].replace(/^(rotating|[A-Z][a-z][a-z] ).*/, '$1').trim());
    }
    for (const f of o.forms) {
      if (f === null) add('forms: none');
      else if (f.gone) add('forms: gone');
      else if (f.kind === 'goalType') add('forms: goal kinds');
      else if (f.kind === 'rule') add('forms: rule ' + (f.id === null ? 'new' : 'edit') + (f.fields[2].value ? ' rotating' : ''));
      else if (f.kind === 'goalForm') add('forms: goal ' + (/^at:/.test(f.id) ? 'at' : f.id === 'CUSTOM' ? 'custom' : f.fold ? 'with target' : 'plain'));
    }
    if (o.disrupt === null) add('disrupt: none');
    else if (v(o.disrupt)) add('disrupt: sheet');
    for (const m of o.moves) if (m === null) add('move: none'); else if (m.gone) add('move: gone'); else if (v(m)) add('move: ' + (m.times.length ? m.times.length === 48 ? 'full' : 'times' : 'no time'));
    for (const s of o.shifts) if (Array.isArray(s)) for (const x of s) add('shifts: ' + (x.ruleId === null ? 'one-off' : 'rule'));
    if ((c.set || []).some(e => e[0].join('/') === 'availability/blackouts')) add('shifts: blackouts');
    for (const h of o.handles) {
      if (h === null) add('handles: no day');
      else if (v(h)) {
        add(h.handles.length ? 'handles: some' : 'handles: none');
        if (h.stops.length && h.stops.length !== 96) add('handles: clock-change day');
        for (const x of h.handles) { add('handles: ' + x.kind + ' ' + x.end); if (x.lengths.includes('')) add('handles: too short'); if (/^Choir /.test(x.label)) add('handles: appointment'); }
      }
    }
  }
  return seen;
}

const WANT = ['goals: none', 'goals: no share', 'goals: repaired share', 'goals: warning', 'goals: clarify', 'goals: paused', 'goals: custom',
  'quests: not connected', 'quests: milestones', 'quests: reminders', 'quests: All-day reminder', 'quests: Done this period', 'quests: n session scheduled',
  'quests: n sessions scheduled', 'quests: Could not fit', 'quests: Locked', 'quests: n scheduled', 'quests: why', 'quests: Daily', 'quests: Due', 'quests: No',
  'settings: throws', 'settings: storage unavailable', 'settings: lab', 'settings: rejected plan', 'settings: no plan', 'settings: host missing',
  'time: set-up', 'time: planned', 'time: weekend sleep', 'time: no sleep', 'time: meals off', 'time: weekdays', 'time: weekends', 'time: every day',
  'time: rotating',
  'forms: none', 'forms: gone', 'forms: goal kinds', 'forms: rule new', 'forms: rule edit', 'forms: rule edit rotating', 'forms: goal at', 'forms: goal custom',
  'forms: goal with target', 'forms: goal plain', 'disrupt: none', 'disrupt: sheet', 'move: gone', 'move: times', 'move: full', 'move: no time', 'move: none',
  'shifts: one-off', 'shifts: rule', 'shifts: blackouts', 'handles: no day', 'handles: some', 'handles: none', 'handles: clock-change day', 'handles: sleep start',
  'handles: sleep end', 'handles: work start', 'handles: work end', 'handles: too short', 'handles: appointment'];

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (more than a thousand)', lines.length >= 1000);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const stateIds = new Set(genA.readStateLines().map(l => JSON.parse(l).state));
  ok('every case names a state of part a\'s file', meta.every(c => stateIds.has(c.state)));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(RM-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_READMODELS_SPEC.md', !unknown.length, unknown);

  /* only what is built to throw does: Settings without a ledger (TypeError) */
  const threw = [];
  for (const c of meta) for (const [k, val] of Object.entries(c.out)) for (const x of (Array.isArray(val) ? val : [val])) if (x && x.threw) threw.push(c.id + ':' + k + '=' + x.threw);
  const built = /:settings=TypeError$/;
  ok('only the sections built to throw do (' + threw.length + ')', threw.every(t => built.test(t)), threw.filter(t => !built.test(t)).slice(0, 8));
  const seen = reached(meta);
  ok('the cases reach every outcome', WANT.every(w => seen.has(w)), WANT.filter(w => !seen.has(w)));
  meta.length = 0;
  if (QUICK) { console.log(pass + ' passed, ' + fail + ' failed (quick)'); process.exit(fail ? 1 : 0); }

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the controller module exactly as the recorder runs it', html.includes(logicVm.appSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const partA = new Set(genA.readStateLines());
    const notA = chromium.stateLines.filter(l => !partA.has(l)).map(l => JSON.parse(l).state);
    ok('every state is defined again from the page byte for byte as part a\'s file has it (' + chromium.stateLines.length + ')', !notA.length, notA.slice(0, 8));
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : chromium.cases[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_readmodels_b_golden.js if the change is intended' });
    ok('the native read models\' words are what the page exports', fs.readFileSync(gen.WORDS_FILE, 'utf8') === gen.wordsText(chromium.words),
      { hint: 'regenerate with node tools/gen_ld_readmodels_b_golden.js' });
    if (chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
