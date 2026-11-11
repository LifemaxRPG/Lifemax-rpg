/* The Life Director timer golden cases (fixtures/ld-timer-golden.json.gz, written by tools/gen_ld_timer_golden.js) still describe the
   page. Every case is recorded again from logic.js inside the built page in Chromium and must come out byte for byte as the file has it,
   so the file cannot go stale; a change to Today.timer, the timer's once-a-second check (focusTick), or the misses and rollover the wake
   family probes either is a mistake or comes with the regenerated file (and the native timer in
   android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/timer, which replays every step).
   The recorder runs each case twice on fresh modules and throws when the answers differ. The same cases are recorded in node and must agree,
   except in a plan explanation (LD20 of the engine spec), the only difference allowed.
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_TIMER_SPEC.md, the definitions equal to the file's, the recorder's hooks found exactly once in logic.js, the
   built page carrying logic.js and the engine as the files have them, the cases reaching every answer Today.timer can give (each message,
   as the page words it), and each wake case's probes: nothing changed a millisecond before the instant, something at it.
   Run: NODE_PATH=$(npm root -g) node tests/ld-timer-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_timer_golden.js */
'use strict';
process.env.TZ = 'UTC'; // the device zone the page and node read (deviceTz), as the Chromium context is set
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_timer_golden.js');
const hostVm = require('../tools/ld_host_vm.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_TIMER_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/* Every answer Today.timer can give but the one only a broken ledger reaches (Cannot complete this block), as the page words them. */
const MESSAGES = ['That is no longer on the plan.', 'You can start it on the day, or from 6 hours before.', 'Another timer is running. Finish or stop it first.',
  'This session is not available to start yet.', 'The timer changed. Please choose Start again.', 'The new timer could not be saved. Your previous timer is unchanged.',
  'Timer could not be reset. Please try again.', 'Already recorded.', 'That timer is not running.', 'Life Director cannot do that.', 'Not saved: '];
const NOTICES = ['Timer stopped. Nothing was logged.', 'That session is no longer on the plan. Log it from the Log page.'];

const differ = (a, b, p) => {
  if (JSON.stringify(a) === JSON.stringify(b)) return null;
  if (a && b && typeof a === 'object' && typeof b === 'object') {
    for (const k of new Set(Object.keys(a).concat(Object.keys(b)))) { const d = differ(a[k], b[k], p + '.' + k); if (d) return d; }
  }
  return p;
};
const changed = o => Object.keys(o.state.set).some(k => k !== 'updatedEpoch') || o.calls.some(c => c.fn === 'store.save') || o.reply === true;

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (hundreds)', lines.length >= 500);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(TM-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_TIMER_SPEC.md', !unknown.length && defined.size > 0, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, meta.map(({ out, ...c }) => c)));
  const replies = new Set(), notices = new Set();
  for (const c of meta) for (const o of c.out) {
    if (o.reply && typeof o.reply.message === 'string') replies.add(o.reply.message);
    if (o.session.notice) notices.add(o.session.notice.text);
  }
  const unreached = MESSAGES.filter(m => ![...replies].some(r => r === m || (m.endsWith(' ') && r.startsWith(m))));
  ok('the cases reach every answer of Today.timer (' + MESSAGES.length + ' messages)', !unreached.length, unreached);
  ok('the cases reach the timer\'s notices', NOTICES.every(n => notices.has(n)), NOTICES.filter(n => !notices.has(n)));
  const threw = meta.flatMap(c => c.out.filter(o => o.reply && o.reply.threw).map(() => c.id));
  ok('no step throws', !threw.length, threw);
  const wakes = meta.filter(c => c.family === 'wake'), badWakes = wakes.filter(c => {
    const n = c.out.length, a = c.out[n - 2], b = c.out[n - 1];
    return changed(a) || !changed(b) || b.at - a.at !== 1;
  }).map(c => c.id);
  ok('each wake case: nothing changes a millisecond before its instant, something at it (' + wakes.length + ' cases)', wakes.length === 40 && !badWakes.length, badWakes);
  meta.length = 0;

  let hooked = true;
  try { hostVm.hostSource(); } catch (e) { hooked = false; }
  ok('the recorder\'s hooks are each found exactly once in logic.js', hooked);
  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries logic.js exactly as the file has it', html.includes(hostVm.logicText()));
  ok('the built page carries the engine and the store exactly as the recorder runs them', html.includes(engineVm.engineSource()) && html.includes(engineVm.storeSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const moved = chromium.lines.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - moved.length) + ' of ' + lines.length + ' do)',
      !moved.length && chromium.lines.length === lines.length,
      { changed: moved.length, first: moved.slice(0, 8), hint: 'regenerate with node tools/gen_ld_timer_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs);
  const nodeDiffer = node.map((l, i) => (l === lines[i] ? null : { id: defs[i].id, at: differ(JSON.parse(lines[i]).out, JSON.parse(l).out, '') })).filter(Boolean);
  const allowed = d => /\.explanation\./.test(d.at) || /\.takeBack\.left$/.test(d.at);
  ok('node agrees with the file on every case but a plan explanation (' + (lines.length - nodeDiffer.length) + ' of ' + lines.length + ' exactly)',
    nodeDiffer.every(allowed), nodeDiffer.filter(d => !allowed(d)).slice(0, 8));
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
