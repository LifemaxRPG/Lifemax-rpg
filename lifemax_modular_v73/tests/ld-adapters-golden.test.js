/* The Life Director host adapter golden cases (fixtures/ld-adapters-golden.json.gz, written by tools/gen_ld_adapters_golden.js) still
   describe the page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so the
   file cannot go stale; a change to LifeMaxHost (engine.js:2355-2620), to the economy or quest board under it, to Smart Diet's meal link or
   to the plan's days either is a mistake or comes with the regenerated file (and the native adapters in
   android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/hostadapters, which replay every case).
   The recorder runs each case twice on fresh state and throws when the answers differ, so a pass also shows the host deterministic.
   Also checked without a browser: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_ADAPTERS_SPEC.md, logic.js still building the meal reference the recorder copies, and that the cases reach what
   they are there for (every award and bounty answer, every quest status, done plan days, meals found and not, the D1 split between the
   device's day and the plan's).
   Run: NODE_PATH=$(npm root -g) node tests/ld-adapters-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_adapters_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_adapters_golden.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ADAPTERS_SPEC.md');
const LOGIC = path.join(__dirname, '..', 'src', 'features', 'life-director', 'logic.js');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (more than 1,500)', lines.length > 1500);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const defs = gen.defineCases();
  ok('the generator defines the same cases as the file, in the same order', JSON.stringify(defs.map(c => c.id)) === JSON.stringify(ids));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', JSON.stringify([...families].sort()) === JSON.stringify([...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(HA-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_ADAPTERS_SPEC.md', !unknown.length, unknown);
  ok('logic.js still builds the meal reference the recorder copies (scheduledMealRef)', fs.readFileSync(LOGIC, 'utf8').includes(gen.MEAL_REF));

  const seen = new Set();
  for (const c of meta) c.input.calls.forEach((call, i) => {
    const r = c.output.returns[i], fn = call.fn.replace(/^LifeMaxHost\./, '');
    if (r && r.threw) seen.add(fn + ':threw:' + r.threw);
    if (fn === 'awardActivity') seen.add('award:' + (r.awarded ? 'paid' : r.reason));
    if (fn === 'completeQuest') seen.add('bounty:' + (r.completed ? 'paid' : r.reason || 'refused'));
    if (fn === 'readQuests' && Array.isArray(r)) r.forEach(q => { seen.add('quest:' + q.status); seen.add('mode:' + q.schedulingMode); if (q.hostRef && q.hostRef.future) seen.add('quest:next') });
    if (fn === 'readPlanSessions' && Array.isArray(r)) r.forEach(s => seen.add('plan:' + s.status));
    if ((fn === 'LifeMaxMeals.resolveScheduled' || fn === 'mealFor') && !(r && r.threw)) seen.add('meal:' + (r ? 'found' : 'none'));
    if (fn === 'retiredUntimedQuest') seen.add('retired:' + (r && !r.threw ? 'found' : 'none'));
    if (fn === 'activityDescription') seen.add('describe:' + (r ? 'text' : 'empty'));
    if (fn === 'milestones' && Array.isArray(r)) r.forEach(m => { if (m.done) seen.add('milestone:done'); if (m.locked) seen.add('milestone:locked') });
  });
  for (const c of meta.filter(c => c.family === 'verify')) if (c.output.state.questDone) seen.add('verify:paid');
  const want = ['award:paid', 'award:ALREADY_IN_LIFEMAX', 'award:HOST_SAVE_FAILED', 'award:HOST_READ_ONLY', 'award:HOST_NOT_CONNECTED', 'bounty:paid',
    'bounty:QUEST_GONE', 'bounty:PERIOD_CHANGED', 'bounty:ALREADY_DONE', 'bounty:refused', 'quest:ACTIVE', 'quest:COMPLETED', 'quest:LOCKED', 'quest:next',
    'mode:NONE', 'mode:SESSIONS', 'plan:ACTIVE', 'plan:COMPLETED', 'meal:found', 'meal:none', 'retired:found', 'retired:none', 'describe:text',
    'describe:empty', 'milestone:done', 'milestone:locked', 'verify:paid', '_test.weekBounds:threw:RangeError', 'readQuests:threw:RangeError'];
  ok('the cases reach every answer they are there for', want.every(w => seen.has(w)), want.filter(w => !seen.has(w)));
  /* D1: an award whose device day is not its UTC day */
  const split = meta.filter(c => c.family === 'award' && /^award\/d1\//.test(c.id)).filter(c => {
    const act = (c.output.state.activities || []).find(a => a.ldEventId);
    if (!act) return false;
    const day = new Intl.DateTimeFormat('en-CA', { timeZone: c.input.zone }).format(new Date(act.date));
    return day !== act.date.slice(0, 10);
  });
  ok('some awards are dated on a device day other than the UTC day (D1; ' + split.length + ')', split.length >= 4);
  meta.length = 0;

  const page = require('./current-page.js')(process.argv[2]);
  let recorded = null;
  try { recorded = await gen.generate(page) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (recorded) {
    const changed = recorded.lines.map((l, i) => (l === lines[i] ? null : ids[i])).filter(Boolean);
    ok('every case comes out of the page byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && recorded.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_adapters_golden.js if the change is intended' });
    ok('the fresh save is the one the file was written against', JSON.stringify(recorded.base) === JSON.stringify(head.base));
    if ('Chromium ' + recorded.browser !== head.recordedIn) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + recorded.browser);
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
