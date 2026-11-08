/* Native economy check (beta), its Settings card (docs/specs/ECONOMY_SHADOW_PLAN.md 3.3, 3.4, 5.3; WP6e), on the classic page
   with a stand-in for NativeShadow (tests/native-shadow-mock.js). Covered: every line under the switch (not available without the
   plugin or the capability, off, starting, checking with its counts, a difference with its kind and time, paused for Battery
   Saver, for the matching app update and in a demo, stopped by native for each reason, a plugin that does not answer); the owner's
   tools only with an app that says economy.shadow.report.v1 and only while the switch is on; Copy report, Clear and Save case
   each asking native only on the owner's tap, Clear and Save case only after the card's own question, every answer shown only
   once native gave it (and "One moment" while it works), a refusal said in plain words and nothing changed; the controls' names
   for TalkBack. Run: NODE_PATH=$(npm root -g) node tests/native-shadow-card.test.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('./native-shadow-mock.js');
const file = require('./current-page')(process.argv[2]);
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'quest-content.json'), 'utf8'));
let pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const CASES = [{ id: 'm-17', kind: 'quest.toggle', at: Date.UTC(2026, 10, 18, 9, 31), count: 2 }, { id: 'm-3', kind: 'log.record', at: Date.UTC(2026, 10, 12, 20, 5), count: 1 }];

async function open(browser, opts) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: 'Europe/London' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  const s = { onboardingComplete: true, name: 'N', tutorialDone: true, classId: 'athlete', tipsSeen: {} };
  await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(s)) + ');' + (opts.demo ? 'localStorage.setItem("lm_demo_active","1");' : ''));
  await p.addInitScript(mock.script(Object.assign({ fingerprint: CONTENT.contentHash }, opts)));
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof window.LmaShadow === 'object' && typeof window.LmaShadowCard === 'object', null, { timeout: 60000 });
  await p.waitForTimeout(1200);
  await p.evaluate(async () => { navigate('settings'); await LmaShadow.refresh(); });
  return p;
}
/* what the card shows now: each part's words when it is shown, null when it is hidden */
const card = p => p.evaluate(() => {
  const el = id => document.getElementById(id);
  const hidden = e => { for (let x = e; x && x.id !== 'nativeShadowCard'; x = x.parentElement) if (x.style.display === 'none') return true; return false; };
  const seen = id => { const e = el(id); return e && !hidden(e) ? e.textContent.replace(/\s+/g, ' ').trim() : null; };
  const t = el('nativeShadowToggle'), sel = el('nativeShadowCaseSel');
  return { on: t.checked, disabled: t.disabled, status: seen('nativeShadowStatus'), last: seen('nativeShadowLast'), tools: seen('nativeShadowTools') !== null,
    said: seen('nativeShadowSaid'), clearAsk: seen('nativeShadowClearAsk') !== null, caseRow: seen('nativeShadowCaseRow') !== null, caseAsk: seen('nativeShadowCaseAsk') !== null,
    options: sel ? [...sel.options].map(o => [o.value, o.textContent]) : [], asked: window.__shadowMock ? window.__shadowMock.asked.slice() : [],
    busy: el('nativeShadowCopyBtn').disabled };
});
const tap = (p, id) => p.evaluate(async id => { document.getElementById(id).click(); await new Promise(r => setTimeout(r, 80)); }, id);

(async () => {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* ---------- the line under the switch ---------- */
    const lines = [
      ['without the plugin', { mode: 'absent' }, (c) => c.status === 'Not available in this version of the app.' && c.disabled && !c.tools],
      ['an app without economy.shadow.v1', { mode: 'nocap' }, (c) => c.status === 'Not available in this version of the app.' && !c.tools],
      ['off', { on: false, report: true }, (c) => c.status === 'Off. Turn it on to start checking.' && !c.on && !c.disabled && !c.tools && c.last === null],
      ['checking, nothing different', { report: true, compares: { compared: 1150, different: 0 } }, (c) => c.status === 'Checked 1,150 actions since 1 Nov · none different' && c.last === null && c.tools],
      ['checking, three different', { report: true, compares: { compared: 1150, different: 3 } }, (c) => c.status === 'Checked 1,150 actions since 1 Nov · 3 different' && c.last === 'Last difference: a quest, 18 Nov 09:31'],
      ['an app that only follows (WP6a)', {}, (c) => /^Following \d+ actions since 1 Nov \(comparing comes in a later update\)$/.test(c.status) && !c.tools],
      ['Battery Saver', { report: true, paused: true }, (c) => c.status === 'Paused while Battery Saver is on. It carries on when Battery Saver is off.'],
      ['a page updated without the app', { report: true, mode: 'stale' }, (c) => /^Paused: waiting for the matching app update\./.test(c.status)],
      ['a demo', { report: true, demo: true }, (c) => c.status === 'Paused during the demo. It only checks your own save.' && c.disabled && !c.tools],
      ['stopped after errors', { report: true, compares: { compared: 9, different: 0 }, state: 'disabled', reason: 'errors' }, (c) => c.status === 'Stopped itself after 5 errors in a day. Turn it off and on to try again.' && c.tools],
      ['stopped after a resync loop', { report: true, state: 'disabled', reason: 'resyncLoop' }, (c) => c.status === 'Stopped itself: it lost its place more than 4 times in an hour. Turn it off and on to try again.'],
      ['stopped for slowness', { report: true, state: 'disabled', reason: 'slow' }, (c) => c.status === 'Stopped itself because it was too slow on this phone. Turn it off and on to try again.'],
      ['stopped for a large save', { report: true, state: 'disabled', reason: 'tooLarge' }, (c) => c.status === 'Stopped: your save is too large for the check (over 4 MB).'],
      ['stopped for a reason this page does not know', { report: true, state: 'disabled', reason: 'later' }, (c) => c.status === 'Stopped after errors. Turn it off and on to try again.'],
      ['a plugin that throws', { report: true, mode: 'throw' }, (c) => c.status === "The phone's check did not answer. It tries again the next time you come back to the app." && !c.tools],
    ];
    for (const [name, opts, check] of lines) {
      const p = await open(browser, opts);
      const c = await card(p);
      ok('the line: ' + name, check(c) && !c.asked.length, c);
      ok('the line: ' + name + ': the page threw nothing', !p.errors.length, p.errors.slice(0, 3));
      await p.context().close();
    }
    {
      // starting: the moment the owner turns it on, before native answers
      const p = await open(browser, { on: false, report: true });
      const r = await p.evaluate(async () => { const s = window.setNativeShadowEnabled(true), now = document.getElementById('nativeShadowStatus').textContent; await s; await LmaShadow.refresh(); return { now, then: document.getElementById('nativeShadowStatus').textContent } });
      ok('the line: starting, then the counts', r.now === 'Starting…' && /^Following|^Checked/.test(r.then), r);
      await p.context().close();
    }

    /* ---------- Copy report ---------- */
    {
      const p = await open(browser, { report: true, compares: { compared: 40, different: 1 }, hold: 'copyReport' });
      await tap(p, 'nativeShadowCopyBtn');
      const waiting = await card(p);
      await p.evaluate(() => window.__shadowMock.release()); await p.waitForTimeout(80);
      const done = await card(p);
      ok('Copy report asks native once, with nothing from the page', JSON.stringify(done.asked) === JSON.stringify([['copyReport', {}]]), done.asked);
      ok('while native works the card says so and the buttons wait', waiting.said === 'One moment…' && waiting.busy, waiting);
      ok('once native has it on the clipboard the card says to paste it to the assistant', done.said === 'Report copied. Paste it into your chat with the assistant.' && !done.busy, done);
      await p.context().close();
      const q = await open(browser, { report: true, compares: { compared: 40, different: 1 }, answers: { copyReport: { ok: false } } });
      await tap(q, 'nativeShadowCopyBtn');
      ok('a report native could not copy is said', (await card(q)).said === 'Could not copy the report. Try again.');
      await q.context().close();
    }

    /* ---------- Clear ---------- */
    {
      const p = await open(browser, { report: true, compares: { compared: 40, different: 2 }, hold: 'clear' });
      await tap(p, 'nativeShadowClearBtn');
      const asking = await card(p);
      ok('Clear first asks in the card, and asks native nothing', asking.clearAsk && !asking.asked.length, asking);
      await tap(p, 'nativeShadowClearNo');
      const kept = await card(p);
      ok('"Keep them" closes the question and changes nothing', !kept.clearAsk && !kept.asked.length && kept.status === 'Checked 40 actions since 1 Nov · 2 different', kept);
      await tap(p, 'nativeShadowClearBtn'); await tap(p, 'nativeShadowClearYes');
      const waiting = await card(p);
      ok('"Clear now" asks native, and the counts stay until native says the ring is stored', JSON.stringify(waiting.asked) === JSON.stringify([['clear', {}]]) && waiting.said === 'One moment…' && waiting.status === 'Checked 40 actions since 1 Nov · 2 different', waiting);
      await p.evaluate(() => window.__shadowMock.release()); await p.waitForTimeout(80);
      const done = await card(p);
      ok('then the card says it is cleared and shows the counts from nothing', done.said === 'Cleared. The check starts counting again from now.' && done.status === 'Checked 0 actions since 1 Nov · none different' && done.last === null && !done.clearAsk, done);
      await p.context().close();
      const q = await open(browser, { report: true, compares: { compared: 40, different: 2 }, answers: { clear: { ok: false } } });
      await tap(q, 'nativeShadowClearBtn'); await tap(q, 'nativeShadowClearYes');
      const refused = await card(q);
      ok('a clear native could not store is said, and the counts stay', refused.said === 'Could not clear it. Nothing was changed; try again.' && refused.status === 'Checked 40 actions since 1 Nov · 2 different', refused);
      await q.context().close();
    }

    /* ---------- Save case (decision O-4) ---------- */
    {
      const p = await open(browser, { report: true, compares: { compared: 40, different: 2 }, cases: CASES });
      const c = await card(p);
      ok('the differences a case is held for are listed, newest first, by time and kind', c.caseRow && JSON.stringify(c.options) === JSON.stringify([['m-17', '18 Nov 09:31 · a quest (2 times)'], ['m-3', '12 Nov 20:05 · logging an activity']]), c.options);
      ok('nothing is saved, copied or cleared on its own', !c.asked.length && !c.caseAsk, c.asked);
      await tap(p, 'nativeShadowCaseBtn');
      const warned = await card(p);
      const warning = await p.evaluate(() => document.getElementById('nativeShadowCaseAsk').textContent);
      ok('Save case first warns in the card that the file holds the owner\'s real data, and asks native nothing', warned.caseAsk && !warned.asked.length && /real data/.test(warning) && /not hidden/.test(warning) && /Nothing is sent anywhere/.test(warning), warning);
      await tap(p, 'nativeShadowCaseNo');
      ok('Cancel closes the warning and saves nothing', !(await card(p)).caseAsk && !(await card(p)).asked.length);
      await p.evaluate(() => { document.getElementById('nativeShadowCaseSel').value = 'm-3' });
      await tap(p, 'nativeShadowCaseBtn'); await tap(p, 'nativeShadowCaseYes');
      const saved = await card(p);
      ok('"Choose where to save" asks native for the chosen difference only', JSON.stringify(saved.asked) === JSON.stringify([['saveCase', { id: 'm-3' }]]), saved.asked);
      ok('and says it is saved once native wrote the file', saved.said === 'Case saved to the file you chose.' && !saved.caseAsk, saved);
      await p.context().close();
      for (const [reason, words] of [['cancelled', 'Not saved: no file was chosen.'], ['noCase', "That difference's details are no longer held (they last until the app closes). The report still lists it."], ['notWritten', 'Could not write the file. Try another place.']]) {
        const q = await open(browser, { report: true, compares: { compared: 40, different: 2 }, cases: CASES, answers: { saveCase: { ok: false, reason } } });
        await tap(q, 'nativeShadowCaseBtn'); await tap(q, 'nativeShadowCaseYes');
        ok('Save case answered ' + reason + ' is said in plain words', (await card(q)).said === words);
        await q.context().close();
      }
      const none = await open(browser, { report: true, compares: { compared: 40, different: 0 }, cases: [] });
      ok('without a difference to save, there is no Save case row', !(await card(none)).caseRow);
      await none.context().close();
    }

    /* ---------- names for TalkBack, and the tools follow the switch ---------- */
    {
      const p = await open(browser, { report: true, compares: { compared: 40, different: 2 }, cases: CASES });
      const r = await p.evaluate(() => [...document.querySelectorAll('#nativeShadowCard button, #nativeShadowCard select, #nativeShadowCard input')].map(e => [e.id, e.getAttribute('aria-label') || '', e.type || '']));
      ok('every control of the card has a name for TalkBack, and every button is a plain button', r.length === 9 && r.every(([, name, type]) => name.length > 4 && (type === 'button' || type === 'checkbox' || type === 'select-one')), r);
      const roles = await p.evaluate(() => ['nativeShadowStatus', 'nativeShadowSaid'].map(id => document.getElementById(id).getAttribute('role') + '/' + document.getElementById(id).getAttribute('aria-live')));
      ok('the line and the answers are announced politely', roles.every(x => x === 'status/polite'), roles);
      await p.evaluate(async () => { await setNativeShadowEnabled(false) });
      const off = await card(p);
      ok('turned off, the card says so and the tools go', off.status === 'Off. Turn it on to start checking.' && !off.tools && off.last === null, off);
      ok('the page threw nothing', !p.errors.length, p.errors.slice(0, 3));
      await p.context().close();
    }
  } finally { await browser.close() }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
