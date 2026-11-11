/* The Life Director host-level golden cases (fixtures/ld-host-golden.json.gz, written by tools/gen_ld_host_golden.js) still describe the
   page. Every case is recorded again from logic.js inside the built page in Chromium and must come out byte for byte as the file has it,
   so the file cannot go stale; a change to Today's or Do's commands, completion, the tick, the dial or the plan refusal either is a mistake
   or comes with the regenerated file (and the native host commands in
   android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/host, which replay every step).
   The recorder runs each case twice on fresh modules and throws when the answers differ, so a pass also shows the commands deterministic
   under a fixed clock. The same cases are recorded in node and must agree, except where node's exp differs in the last bit from the
   page's (LD20 of the engine spec: an explanation's penalty), which is the only difference allowed.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_HOST_SPEC.md, the definitions equal to the file's, that the recorder's three hooks are found exactly once in
   logic.js and that the built page carries logic.js and the engine exactly as the files have them, and that the cases reach every refusal
   and repeat the native commands must give (each message, as the page words it).
   Run: NODE_PATH=$(npm root -g) node tests/ld-host-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_host_golden.js */
'use strict';
process.env.TZ = 'UTC'; // the device zone the page and node read (deviceTz), as the Chromium context is set
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_host_golden.js');
const hostVm = require('../tools/ld_host_vm.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_HOST_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/* Every refusal and repeat the commands in scope can answer, as the page words them (a prefix where the words go on). */
const MESSAGES = ['That is no longer on the plan.', 'Already recorded.', 'You can mark it done on the day, or from 6 hours before.', 'Nothing was logged.',
  'Already skipped.', 'Its timer is running. Stop the timer first.', 'Already back on the plan.', 'That can no longer be taken back.',
  'The plan has changed since, so that cannot be taken back.', 'That time is not on the plan.', 'It does not fit there. Nothing was moved.',
  'Life Director cannot do that.', 'Nothing was added.', 'Already added', 'That is not one of the choices.', 'Enter a date, start and end',
  'Nothing was changed.', 'That question is no longer showing.', 'Choose one of the answers.', 'That goal is gone.', 'That is not a priority.',
  'That is not a kind of goal.', 'That is not a setting.', 'That is not a day.', 'Backup is not valid: ', 'Not saved: ', 'Life Director was not reset: ',
  'Enter both times', 'That is under 5 hours of sleep. Please check the times.', 'Enter start and end times', 'Pick at least one day', 'Pick the first day on',
  'That commitment was removed.', 'That is not a commitment.', 'Make a plan for this day first.', 'Give the activity a name (up to 80 characters).',
  'Choose an activity type.', 'Choose a date, a 15-minute start time and a duration from 15 to 180 minutes.', 'That local time does not exist. Choose another time.',
  'Choose an upcoming time. Use Log activity for something already done.', 'This day has not been planned yet. Make a plan for it before adding activities.',
  'That time overlaps sleep, work or another activity. Choose a free time.', 'Activity could not be saved. Nothing was added.', 'Give the goal a name',
  'Save your sleep times first', 'There is no change to make. Nothing was changed.', 'Backup restored', 'Life Director reset'];

/* the path of the first difference between two recorded values */
const differ = (a, b, p) => {
  if (JSON.stringify(a) === JSON.stringify(b)) return null;
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    for (const k of new Set(Object.keys(a).concat(Object.keys(b)))) { const d = differ(a[k], b[k], p + '.' + k); if (d) return d; }
  }
  return p;
};

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (hundreds)', lines.length >= 800);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(HC-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_HOST_SPEC.md', !unknown.length, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, meta.map(({ out, ...c }) => c)));
  const replies = new Set();
  for (const c of meta) for (const o of c.out) if (o.reply && typeof o.reply.message === 'string') replies.add(o.reply.message);
  const unreached = MESSAGES.filter(m => ![...replies].some(r => r === m || (m.endsWith(' ') && r.startsWith(m))));
  ok('the cases reach every refusal and repeat (' + MESSAGES.length + ' messages)', !unreached.length, unreached);
  const threw = meta.flatMap(c => c.out.filter(o => o.reply && o.reply.threw).map(() => c.id));
  ok('only the import of a ledger-less backup makes a step throw (LH01)', threw.every(id => id === 'rollover/zone-change@London'), threw);
  meta.length = 0;

  let hooked = true;
  try { hostVm.hostSource(); } catch (e) { hooked = false; }
  ok('the recorder\'s three hooks are each found exactly once in logic.js', hooked);
  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries logic.js exactly as the file has it', html.includes(hostVm.logicText()));
  ok('the built page carries the engine and the store exactly as the recorder runs them', html.includes(engineVm.engineSource()) && html.includes(engineVm.storeSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_host_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs);
  const nodeDiffer = node.map((l, i) => (l === lines[i] ? null : { id: defs[i].id, at: differ(JSON.parse(lines[i]).out, JSON.parse(l).out, '') })).filter(Boolean);
  const allowed = d => /\.explanation\.penalties\.[a-zA-Z]+$/.test(d.at);
  ok('node agrees with the file on every case but an explanation penalty\'s last bit (' + (lines.length - nodeDiffer.length) + ' of ' + lines.length + ' exactly)',
    nodeDiffer.every(allowed), nodeDiffer.filter(d => !allowed(d)).slice(0, 8));
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
