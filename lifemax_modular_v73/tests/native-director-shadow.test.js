/* Native Life Director check (beta), the page's half of WP-LD12 (docs/specs/LIFE_DIRECTOR_SHADOW_SPEC.md). A stand-in for NativeShadow
   (tests/native-director-shadow-mock.js) keeps a copy built exactly as native's TextMirror does; after every save its stitched text
   must be the text Life Director's store wrote, byte for byte. Covered: the switch (off by default, Data & advanced, "(beta)"), nothing
   at all while it is off, without the plugin, without the capability or in a demo; a stale fingerprint; the first copy (a base) and
   read check; a save, a plan and a repair each sent once with what native needs; the page's read models with their XP previews; a
   resync; a plugin that throws (the page side stops itself, Life Director carries on); turning the switch off; Life Director saving
   and planning the same with the check on as with it off; the cost.
   RECORD=1 writes the message stream of the lived-in day to tests/fixtures/director-shadow-stream.json.gz, which the Kotlin tests
   replay through native's real checks (DirectorShadowStreamTest), so page and native are proved against the same messages.
   Run: NODE_PATH=$(npm root -g) node tests/native-director-shadow.test.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const { chromium } = require('playwright');
const mock = require('./native-director-shadow-mock.js');
const directorDay = require('./fixtures/director-day.js');
const file = require('./current-page')(process.argv[2]);
const STREAM = path.join(__dirname, 'fixtures', 'director-shadow-stream.json.gz');
let pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n); } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')); } };

async function open(browser, opts, demo) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', (e) => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: true });
  if (demo) await p.addInitScript(() => localStorage.setItem('lm_demo_active', '1'));
  if (opts) await p.addInitScript(mock.script(opts));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; });
  return p;
}
async function settle(p, ms = 400) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5); } await p.waitForTimeout(20); }
const M = (p) => p.evaluate(() => { const m = window.__dsMock; return m && { calls: m.calls, kinds: m.envelopes.map((e) => e.kind), bases: m.bases, refused: m.refused, state: m.state, texts: m.texts.map((t) => ({ kind: t.kind, bytes: t.bytes, n: t.text.length })) }; });
const stored = (p) => p.evaluate(() => localStorage.getItem('lifemax_director_state_v1'));

(async () => {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* ---------- off: by default, and every way the check cannot run ---------- */
    {
      const p = await open(browser, { on: false });
      await directorDay.play(p);
      await settle(p);
      const r = await p.evaluate(() => {
        navigate('settings');
        const t = document.getElementById('nativeDirectorShadowToggle');
        return { calls: window.__dsMock.calls.filter((c) => c.endsWith(':director')), live: LmaDirectorShadow.live, off: t && !t.checked && !t.disabled,
          label: t && t.closest('.settingsline').textContent, cat: setCatOf(t.closest('.card')), status: LmaDirectorShadowCard.status() };
      });
      ok('off by default: no director call to native, nothing told by Life Director', r.calls.length === 0 && r.live === false, r);
      ok('the switch is "Native Life Director check (beta)", off, in Data & advanced, with its help line', r.off && /Native Life Director check \(beta\)/.test(r.label) && /Nothing leaves the phone/.test(r.label) && r.cat === 'data', r);
      ok('its line says it is off', /^Off\./.test(r.status), r.status);
      await p.context().close();
    }
    for (const [name, opts, demo] of [['without the plugin', { mode: 'absent' }], ['without director.shadow.v1', { mode: 'nocap' }], ['in a demo', {}, true]]) {
      const p = await open(browser, opts, demo);
      await settle(p);
      const r = await M(p), live = await p.evaluate(() => LmaDirectorShadow.live);
      ok('nothing ' + name + ': not live, no director message', !live && !(r && r.calls.some((c) => /^(configure|base|observe):director/.test(c))), r);
      await p.context().close();
    }
    {
      const p = await open(browser, { mode: 'stale' });
      await settle(p);
      const r = await M(p), s = await p.evaluate(() => ({ live: LmaDirectorShadow.live, line: LmaDirectorShadowCard.status() }));
      ok('a different fingerprint: configured, stale, nothing sent', r.state === 'stale' && !s.live && !r.kinds.length && /matching app update/.test(s.line), [r, s]);
      await p.context().close();
    }

    /* ---------- on: the lived-in day ---------- */
    let onText = null;
    {
      const p = await open(browser, {});
      await settle(p, 600);
      const first = await M(p);
      ok('on: configured, one base, and the first read check carries the copy', first.state === 'on' && first.bases === 1 && first.kinds[0] === 'director.read', first);
      const fp = await p.evaluate(() => window.__dsMock.config.page.fingerprint);
      ok('the fingerprint is the engine version and the store schema', fp === '1.1.0/1', fp);
      await directorDay.play(p);
      await settle(p, 800);
      onText = await stored(p);
      /* later in the day and past midnight: a re-plan each time (a plan, a save and a read check) */
      for (const t of ['2026-10-28T16:30:00Z', '2026-10-28T19:00:00Z', '2026-10-28T23:30:00Z', '2026-10-29T07:30:00Z']) {
        await p.clock.pauseAt(new Date(t));
        await p.evaluate(() => LifeDirector.regenerate('test'));
        await settle(p, 400);
      }
      const r = await M(p);
      const kinds = new Set(r.kinds);
      ok('the day sends saves, plans, a repair and read checks', kinds.has('director.save') && kinds.has('director.plan') && kinds.has('director.repair') && kinds.has('director.read'), r.kinds);
      ok('nothing refused: every envelope laid on the copy', r.refused.length === 0, r.refused);
      const saves = r.texts.filter((t) => t.kind === 'director.save');
      ok('every save: the copy stitches to the length the store wrote', saves.length > 0 && saves.every((t) => t.n === t.bytes), saves.slice(0, 5));
      const last = await p.evaluate(() => { const m = window.__dsMock, s = m.texts.filter((t) => t.kind === 'director.save'); return s.length ? s[s.length - 1].text : null; });
      const now = await stored(p);
      ok('the last save: the copy is the stored text byte for byte', last === now, [last && last.length, now && now.length]);
      const read = await p.evaluate(() => window.__dsMock.envelopes.find((e) => e.kind === 'director.read'));
      ok('a read check: the page\'s days, blocks, now, week, native week and XP previews, with its moment and zones',
        read && ['days', 'unplanned', 'blocks', 'timeline', 'now', 'week', 'native', 'xp'].every((k) => k in read.read.out) && read.read.planZone === 'UTC' && read.read.deviceZone === 'UTC' && read.read.planOffset === 0 && Array.isArray(read.read.out.days) && read.read.out.xp.length > 0,
        read && Object.keys(read.read.out));
      const eng = await p.evaluate(() => window.__dsMock.envelopes.filter((e) => e.kind === 'director.repair' || e.kind === 'director.plan').map((e) => ({ kind: e.kind, ops: e.ops.length, input: !!e.engine.input.engineInput || !!e.engine.input.goals, out: !!(e.engine.output.updatedSchedule || e.engine.output.scheduledInstances), zone: e.engine.planZone })));
      ok('each plan or repair: the engine input and its answer, no state changes', eng.length > 0 && eng.every((e) => e.ops === 0 && e.input && e.out && e.zone === 'UTC'), eng);
      const cost = await p.evaluate(() => LmaDirectorShadowStatus().stats.costs);
      const sorted = cost.slice().sort((a, b) => a - b), p95 = sorted[Math.floor((sorted.length - 1) * 0.95)];
      ok('the page side stays within its budget (p95 under 50 ms)', p95 < 50, { p95, n: cost.length });
      const st = await p.evaluate(() => LmaDirectorShadowStatus());
      ok('still live, no fault', st.live && st.stats.faults === 0, st);
      if (process.env.RECORD === '1') {
        const stream = await p.evaluate(() => window.__dsMock.stream);
        fs.writeFileSync(STREAM, zlib.gzipSync(Buffer.from(JSON.stringify({ about: 'The Life Director check\'s messages for the lived-in day (tests/native-director-shadow.test.js RECORD=1); DirectorShadowStreamTest replays them', stream }), 'utf8'), { level: 9 }));
        console.log('wrote ' + path.relative(process.cwd(), STREAM) + ' (' + stream.length + ' messages)');
      }
      /* the switch off: no more messages, native told */
      const n = await p.evaluate(async () => { await setNativeDirectorShadowEnabled(false); return window.__dsMock.envelopes.length; });
      await p.evaluate(() => LifeDirector.regenerate('test'));
      await settle(p);
      const off = await p.evaluate(() => ({ live: LmaDirectorShadow.live, n: window.__dsMock.envelopes.length, cfg: window.__dsMock.config.enabled }));
      ok('turned off: native told, nothing more sent', !off.live && off.n === n && off.cfg === false, off);
      ok('no page error with the check on', p.errors.length === 0, p.errors);
      await p.context().close();
    }
    {
      const p = await open(browser, { on: false });
      await settle(p, 600);
      await directorDay.play(p);
      await settle(p, 800);
      const offText = await stored(p);
      const strip = (t) => JSON.stringify(JSON.parse(t), (k, v) => (/^(id|instanceId|eventId|scheduleId|clientEventId|inputHash|computeTimeMs|updatedEpoch)$/.test(k) ? 0 : v));
      ok('Life Director saves the same state with the check on as with it off (ids and times aside)', onText && offText && strip(onText) === strip(offText), [onText && onText.length, offText && offText.length]);
      await p.context().close();
    }

    /* ---------- a resync, and a plugin that throws ---------- */
    {
      const p = await open(browser, { resyncAt: [3] });
      await settle(p, 600);
      await directorDay.play(p);
      await settle(p, 800);
      await p.evaluate(() => LifeDirector.regenerate('after the resync')); // the next save after the page heard "resync"
      await settle(p, 400);
      const r = await M(p);
      const last = await p.evaluate(() => { const s = window.__dsMock.texts.filter((t) => t.kind === 'director.save'); return s.length ? s[s.length - 1].text : null; });
      ok('a resync: a new base follows and the copy is right again', r.bases >= 2 && last === (await stored(p)), { bases: r.bases, refused: r.refused });
      await p.context().close();
    }
    {
      const p = await open(browser, { mode: 'throw' });
      await settle(p, 600);
      await directorDay.play(p);
      await settle(p, 800);
      const s = await p.evaluate(() => ({ live: LmaDirectorShadow.live, st: LmaDirectorShadowStatus(), saved: !!localStorage.getItem('lifemax_director_state_v1') }));
      ok('a plugin that throws: not live, Life Director still saves, no page error', !s.live && s.saved && p.errors.length === 0, [s, p.errors]);
      await p.context().close();
    }
  } finally {
    await browser.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
