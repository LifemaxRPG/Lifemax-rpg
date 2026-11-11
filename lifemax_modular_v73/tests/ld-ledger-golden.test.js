/* The Life Director ledger golden cases (fixtures/ld-ledger-golden.json.gz, written by tools/gen_ld_ledger_golden.js) still describe the
   page. Every case is recorded again from the built page in Chromium and must come out byte for byte as the file has it, so the file cannot
   go stale; a change to the engine's ledger, quest progress, misses or transitions either is a mistake or comes with the regenerated file
   (and the native code in android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, which replays every case).
   The recorder runs each case twice on fresh state and throws when the answers differ, so a pass also shows the ledger deterministic. The
   same cases are recorded in node from the engine text in a vm and must agree on every one (the ledger reads no exp or pow; a stream's day
   key reads Intl, and the zones are the strict matrix, where node and the page agree).
   Also checked without the page: the file's layout, unique ids, the families, every rule a case cites defined in
   docs/specs/LIFE_DIRECTOR_LEDGER_SPEC.md, the definitions equal to the file's, strict zones only, only the cases built to throw do, and
   that the cases reach what they are there for (every state, consequence and note, tokens earned and spent, residual tasks, bounties,
   released sessions, compaction, the second award of LG01, clock changes inside sessions).
   Run: NODE_PATH=$(npm root -g) node tests/ld-ledger-golden.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_ld_ledger_golden.js */
'use strict';
const fs = require('fs'), path = require('path');
const gen = require('../tools/gen_ld_ledger_golden.js');
const { ZONES } = require('../tools/ld_ledger_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_LEDGER_SPEC.md');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const sameText = (a, b) => JSON.stringify(a) === JSON.stringify(b);

(async () => {
  const raw = gen.readFile(), lines = gen.readLines();
  const head = JSON.parse(raw.slice(0, raw.indexOf('\n')).replace(/,"cases":\[$/, '}')).header;
  ok('the file holds ' + lines.length + ' cases (thousands)', lines.length >= 2000);
  ok('the file is written exactly as the generator writes it (one case per line, gzip of that text)', gen.serialize(head, lines) === raw);
  const meta = lines.map(l => JSON.parse(l));
  const ids = meta.map(c => c.id), dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  ok('every case has a unique id', !dupes.length, dupes.slice(0, 10));
  const families = [...new Set(meta.map(c => c.family))];
  ok('every family has cases, and no other family exists', sameText([...families].sort(), [...gen.FAMILIES].sort()), families);
  const spec = fs.existsSync(SPEC) ? fs.readFileSync(SPEC, 'utf8') : '';
  const defined = new Set([...spec.matchAll(/\*\*(LED-\d+)\*\*/g)].map(m => m[1]));
  const unknown = [...new Set(meta.flatMap(c => c.rule.split(' ')))].filter(r => !defined.has(r));
  ok('every rule a case cites is defined in LIFE_DIRECTOR_LEDGER_SPEC.md', !unknown.length, unknown);
  const defs = gen.definitions();
  ok('the generator defines the same cases as the file, in the same order', sameText(defs, meta.map(({ out, ...c }) => c)));
  const streams = meta.filter(c => c.fn === 'stream');
  const outside = [...new Set(streams.map(c => c.args[0].tz))].filter(z => !ZONES.includes(z));
  ok('every stream runs in a strict zone of the matrix', !outside.length, outside);
  const threw = meta.filter(c => c.out && c.out.threw && c.family !== 'malformed').map(c => c.id);
  const built = /^event\/completionEventId\/(\[[^\]]*(null|""|0),(null|""|0)|client(null|""|0|"\$absent"))|^transition\/canTransition\/(constructor|__|hasOwnProperty|isPrototypeOf|propertyIsEnumerable|toString|valueOf|toLocaleString)|no-client|client-empty|instance-id-absent-no-client/;
  ok('outside the malformed family only the cases built to throw do (' + threw.length + ')', threw.every(id => built.test(id)), threw.filter(id => !built.test(id)));
  const steps = streams.flatMap(c => c.out.steps);
  const seen = new Set();
  for (const s of steps) {
    seen.add(s.op + (s.applied === true ? '+' : s.applied === false ? '-' + s.reason : ''));
    if (s.lifecycleState) seen.add('state:' + s.lifecycleState);
    for (const c of [s].concat(s.consequences || [])) { if (c.consequence) seen.add('consequence:' + c.consequence.type); if (c.note) seen.add('note:' + c.note); if (c.regroupPrompt) seen.add('regroup'); }
    if (s.tokenEarned) seen.add('token-earned'); if (s.residualTask) seen.add('residual'); if (s.revoked) seen.add('revoked'); if (s.reopened) seen.add('reopened');
    if (s.progress && s.progress.questBountyRewards.length) seen.add('bounty'); if (s.progress && s.progress.removedInstanceIds.length) seen.add('released');
    if (s.progress && s.progress.questsChanged.some(q => q && q.completedForDayKey)) seen.add('daily');
    if (s.has === true) seen.add('has'); if (s.has === false) seen.add('has-not');
  }
  const want = ['complete+', 'complete-DUPLICATE_EVENT_ID', 'complete-NOT_COMPLETABLE_SKIPPED_VOLUNTARY', 'adhoc+', 'adhoc-DUPLICATE_EVENT_ID', 'state:COMPLETED_EARLY',
    'state:COMPLETED_ON_TIME', 'state:COMPLETED_LATE', 'state:PARTIALLY_COMPLETED', 'consequence:REST_TOKEN_SPENT', 'consequence:STREAK_DECAY', 'consequence:HP_DRAIN',
    'note:LENIENT_MODE', 'note:ABOVE_MINIMUM_NO_PENALTY', 'note:TACTICAL_REGROUP_PAUSED', 'note:ONE_PER_DAY_LIMIT', 'regroup', 'token-earned', 'residual', 'revoked',
    'reopened', 'bounty', 'released', 'daily', 'has', 'has-not'];
  ok('the streams reach every outcome (' + steps.length + ' steps)', want.every(w => seen.has(w)), want.filter(w => !seen.has(w)));
  const lg01 = meta.find(c => c.id === 'record/recordCompletion/archived-then-again');
  ok('LG01 is recorded: an archived event id is applied again', lg01 && lg01.out.applied === true);
  const compacted = meta.filter(c => c.out && c.out.archivedIds && c.out.archivedIds.length || c.out && c.out.ledger && c.out.ledger.archivedIds && c.out.ledger.archivedIds.length > 2).length;
  ok('some cases compact the ledger (' + compacted + ')', compacted >= 8);
  const dstLong = meta.filter(c => c.family === 'dst' && /^gap@/.test(c.id.split('/')[2]))
    .flatMap(c => c.out.steps.filter(s => s.event && s.event.scheduledDurationMinutes !== 60)).length;
  ok('sessions laid out by the wall clock across a clock change are not 60 minutes long (' + dstLong + ' completions)', dstLong >= 4);
  meta.length = 0;

  const page = require('./current-page.js')(process.argv[2]), html = fs.readFileSync(page, 'utf8');
  ok('the built page carries the engine module exactly as the recorder runs it', html.includes(engineVm.engineSource()));

  let chromium = null;
  try { chromium = await gen.recordInChromium(page, defs) } catch (e) { ok('the page records every case without an error', false, String((e && e.stack) || e).slice(0, 800)) }
  if (chromium) {
    const changed = chromium.lines.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
    ok('every case comes out of the page in Chromium byte for byte as the file has it, twice the same (' + (lines.length - changed.length) + ' of ' + lines.length + ' do)',
      !changed.length && chromium.lines.length === lines.length,
      { changed: changed.length, first: changed.slice(0, 8), hint: 'regenerate with node tools/gen_ld_ledger_golden.js if the change is intended' });
  }
  const node = gen.recordInNode(defs).cases.map(c => JSON.stringify(c));
  const nodeDiffer = node.map((l, i) => (l === lines[i] ? null : defs[i].id)).filter(Boolean);
  ok('node agrees with the file on every case (' + (lines.length - nodeDiffer.length) + ' of ' + lines.length + ')', !nodeDiffer.length, nodeDiffer.slice(0, 8));
  if (chromium && chromium.browser + '' !== head.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + head.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
