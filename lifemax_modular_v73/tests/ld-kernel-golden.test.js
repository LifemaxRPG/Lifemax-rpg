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
const { CHANGES, TZ_FRAGILE, TZ_CHANGED_IN, zoneOf } = require('../tools/ld_kernel_cases.js');
const engineVm = require('../tools/ld_engine_vm.js');
const SPEC = path.join(__dirname, '..', '..', 'docs', 'specs', 'LIFE_DIRECTOR_ENGINE_SPEC.md');

/* Node is a second runner with its own ICU build. When its ICU and tz versions are the ones the file's header records for node, it
   must agree with the file on every case except these (recorded at generation: that node's ICU has no "Factory" zone, Chromium's
   has). When its versions differ (another node on another machine), it may also disagree on the zones of TZ_FRAGILE
   (tools/ld_kernel_cases.js, each listed from a run that showed it); a disagreement in any other zone fails either way. */
const NODE_DIFFERS = {
  'zone/isValidTimeZone/505:Factory': 'node ICU has no Factory zone',
  'alias/tzOffsetSec/505:Factory@jan': 'node ICU has no Factory zone',
  'alias/tzOffsetSec/505:Factory@jul': 'node ICU has no Factory zone',
};
const nodeData = v => 'ICU ' + v.icu + ' tz ' + v.tz;
/* A JavaScript engine feature, not data: Intl's offset time zones ('+05:30'). Node 20 (V8 11.3) and node 21 (V8 11.8) refuse them;
   node 22 (V8 12.4) and the recording's Chromium accept them. On a node without them, its disagreement on offset names is set aside
   below, provided it is exactly a refusal. (An Android WebView that old would refuse them too: LIFE_DIRECTOR_ENGINE_SPEC.md 6.2(7).) */
const OFFSET_NAME = /^[+\-\u2212]\d\d(:?\d\d)?$/;
const offsetZonesSupported = () => { try { new Intl.DateTimeFormat('en-US', { timeZone: '+01:00' }); return true } catch (e) { return false } };

/* The verdict on node's disagreements: {pass, why, zones}. differ is [{id, zone}]; recorded is the header's nodeCheck text. */
function nodeVerdict(differ, recorded, versions) {
  const zones = [...new Set(differ.map(d => d.zone))].sort();
  const same = recorded.endsWith(nodeData(versions));
  if (same) {
    const unlisted = differ.filter(d => !(d.id in NODE_DIFFERS)).map(d => d.id), stale = Object.keys(NODE_DIFFERS).filter(id => !differ.some(d => d.id === id));
    return { pass: !unlisted.length && !stale.length, why: 'same node data as the recording (' + nodeData(versions) + ')', zones, unlisted, noLongerDiffer: stale };
  }
  const older = Object.keys(TZ_CHANGED_IN).filter(z => String(versions.tz) < TZ_CHANGED_IN[z]);
  const outside = differ.filter(d => !TZ_FRAGILE.includes(d.zone) && !older.includes(d.zone)).map(d => d.id);
  return { pass: !outside.length, zones, unlisted: outside,
    why: 'node data ' + nodeData(versions) + ' differs from the recording\'s (' + recorded + '): only the tz-fragile zones ' + TZ_FRAGILE.join(', ') +
      (older.length ? ' and the zones changed in a later tz release, ' + older.join(', ') + ',' : '') + ' may differ' };
}

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
  /* the verdict itself, on made-up disagreements, so both branches are known to work on any machine */
  const v = process.versions, here = 'node x ' + nodeData(v), other = 'node x ICU 0 tz 1999a';
  const fx = [{ id: 'zone/isValidTimeZone/505:Factory', zone: 'Factory' }, { id: 'alias/tzOffsetSec/505:Factory@jan', zone: 'Factory' }, { id: 'alias/tzOffsetSec/505:Factory@jul', zone: 'Factory' }];
  const morocco = { id: 'fuzz/dayEndAfter/66:Africa/Casablanca', zone: 'Africa/Casablanca' }, ny = { id: 'wall/x', zone: 'America/New_York' }, py = { id: 'fuzz/x', zone: 'America/Asuncion' };
  ok('the node verdict: same data allows only the listed cases; other data also the tz-fragile zones and zones changed after it; never any other zone',
    nodeVerdict(fx, here, v).pass && !nodeVerdict(fx.concat([morocco]), here, v).pass && nodeVerdict(fx.concat([morocco]), other, v).pass
    && nodeVerdict([morocco], other, v).pass && !nodeVerdict(fx.concat([ny]), other, v).pass && !nodeVerdict(fx.concat([ny]), here, v).pass
    && nodeVerdict([py], other, { icu: '74.2', tz: '2024a' }).pass && !nodeVerdict([py], other, { icu: '78.1', tz: '2026a' }).pass);

  const node = gen.recordInNode(defs), byId = new Map(stored.map(c => [c.id, c]));
  const nodeById = new Map(node.map(c => [c.id, c]));
  const refusal = out => out === false || (out && typeof out === 'object' && out.threw === 'RangeError');
  const all = differing(node, stored).map(id => ({ id, zone: zoneOf(byId.get(id)) }));
  const setAside = offsetZonesSupported() ? [] : all.filter(d => OFFSET_NAME.test(d.zone || '') && refusal(nodeById.get(d.id).out));
  const differ = all.filter(d => !setAside.includes(d));
  if (setAside.length) console.log('note: this node (V8 ' + process.versions.v8 + ') has no offset time zones; ' + setAside.length + ' cases on offset names set aside');
  const verdict = nodeVerdict(differ, file.header.nodeCheck, process.versions);
  console.log('node ' + process.versions.node + ' ' + nodeData(process.versions) + ': ' + differ.length + ' cases differ from Chromium, in zones ' + JSON.stringify(verdict.zones) + '; ' + verdict.why);
  ok('node (its own ICU) agrees with the file on every case it must (' + differ.length + ' differ, in ' + verdict.zones.length + ' zones)', verdict.pass,
    { unlisted: verdict.unlisted.slice(0, 8), noLongerDiffer: verdict.noLongerDiffer, zones: verdict.zones });
  if (chromium && chromium.browser + '' !== file.header.recordedIn.replace('Chromium ', '')) console.log('note: recorded in ' + file.header.recordedIn + ', checked in Chromium ' + chromium.browser);
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
