/* Native economy check (beta): the shadow is transparent (docs/specs/ECONOMY_SHADOW_PLAN.md WP6a). The economy, quests and quest
   board golden cases are recorded again with the check switched on and a stand-in NativeShadow (tests/native-shadow-mock.js) that
   answers like native, one that rejects every call and one that throws at once: every case must come out of the page exactly as the
   file has it (returns, state, toasts), so the wrappers change nothing the page does, whatever native answers. A first check proves
   the wrappers are really installed in such a page (the comparison is not vacuous). With the check off the same goldens are the
   ordinary economy-golden, quests-golden and quests-board-golden tests.
   Run: NODE_PATH=$(npm root -g) node tests/native-shadow-transparent.test.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('./native-shadow-mock.js');
const file = require('./current-page')(process.argv[2]);
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'quest-content.json'), 'utf8'));
let pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
function same(a, b) {
  if (a === b) return true;
  if (typeof a !== 'object' || typeof b !== 'object' || a === null || b === null || Array.isArray(a) !== Array.isArray(b)) return false;
  const ka = Object.keys(a), kb = Object.keys(b);
  return ka.length === kb.length && ka.every(k => Object.prototype.hasOwnProperty.call(b, k) && same(a[k], b[k]));
}
const GOLDENS = [
  ['economy', require('../tools/gen_economy_golden.js'), ['record', 'reject', 'throw']],
  ['quests', require('../tools/gen_quests_golden.js'), ['record']],
  ['quests board', require('../tools/gen_quests_board_golden.js'), ['record', 'throw']],
];

(async () => {
  /* the wrappers are really there in a golden page (the generators' clock and boot) */
  {
    const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
    try {
      const p = await (await browser.newContext({ timezoneId: 'UTC', locale: 'en-US' })).newPage();
      await p.clock.install({ time: new Date('2026-10-28T11:00:00Z') });
      await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
      await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash }));
      await p.goto('file://' + file);
      await p.waitForFunction(() => typeof window.LmaShadow === 'object' && LmaShadow.status().installed > 30, null, { timeout: 60000 });
      const r = await p.evaluate(() => { ecoTakeRest(); return { status: LmaShadow.status(), wrapped: !!ecoTakeRest.__lmaShadow } });
      ok('in a golden page with the check on, the shadow points are wrapped and live', r.wrapped && r.status.live && r.status.installed > 30, r.status);
    } finally { await browser.close() }
  }
  /* A page that believes it runs on a phone draws a little differently from the file's desktop recording (the Health card fills in
     state.health, so four economy cases list it): such cases are compared with the same phone-like page with the check off. */
  const differing = (a, b) => a.filter((c, i) => !b[i] || b[i].id !== c.id || !same(c, b[i])).map(c => c.id);
  for (const [name, gen, modes] of GOLDENS) {
    const stored = JSON.parse(fs.readFileSync(gen.FILE, 'utf8'));
    let phone = null;
    for (const mode of modes) {
      let fresh = null;
      try { fresh = await gen.generate(file, () => { }, mock.script({ fingerprint: CONTENT.contentHash, mode })) } catch (e) { ok(name + ' golden records with the check on (' + mode + ')', false, String((e && e.stack) || e).slice(0, 600)); continue }
      let changed = differing(stored, fresh), against = 'the file';
      if (changed.length) {
        phone = phone || await gen.generate(file, () => { }, mock.script({ mode: 'absent', on: false }));
        const platform = differing(stored, phone);
        if (changed.every(id => platform.includes(id))) { changed = differing(phone, fresh); against = 'the file (' + platform.length + ' cases as a phone draws them, check off)'; }
      }
      ok(name + ' golden: all ' + stored.length + ' cases as ' + against + ' with the check on and native ' + (mode === 'record' ? 'answering' : mode === 'reject' ? 'rejecting every call' : 'throwing'), fresh.length === stored.length && !changed.length, changed.slice(0, 10));
    }
  }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
