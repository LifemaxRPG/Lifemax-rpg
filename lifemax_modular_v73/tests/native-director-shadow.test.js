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
/* one Today or Do command as a native screen sends it (LifeDirector.native.todayRun / run), with the state's text before and after;
   a session named {s: k} is the k-th session still planned later today */
async function command(p, kind, name, payload) {
  return p.evaluate(([kind, name, payload]) => {
    const ST = () => LifeDirectorApp._debug().ST, now = Date.now() / 1000, day = new Date(Date.now()).toISOString().slice(0, 10);
    const later = ST().schedule.scheduledInstances.filter((i) => i.lifecycleState === 'PLANNED' && i.startEpoch >= now && new Date(i.startEpoch * 1000).toISOString().slice(0, 10) === day);
    const pick = (v) => (v && typeof v === 'object' && 's' in v ? (later[v.s] || {}).instanceId : v && typeof v === 'object' && 'focus' in v ? (ST().ui.focus || {}).start : v);
    const body = {};
    for (const k of Object.keys(payload || {})) body[k] = pick(payload[k]);
    const before = JSON.stringify(ST()), seq = window.__dsMock.envelopes.length;
    const reply = kind === 'today' ? LifeDirector.native.todayRun(name, body) : LifeDirector.native.run(name, body);
    return { before, after: JSON.stringify(ST()), reply: JSON.parse(JSON.stringify(reply === undefined ? null : reply)), seq, body };
  }, [kind, name, payload]);
}
/* the commands a native Today, My time and Settings send, in the order a person might */
const COMMANDS = [
  ['today', 'pin', { id: { s: 0 }, on: true }], ['today', 'pin', { id: { s: 0 }, on: true }],
  ['today', 'skip', { id: { s: 1 } }], ['today', 'unskip', { id: 'SKIPPED', op: 'u-1' }], ['today', 'skip', { id: { s: 1 } }],
  ['today', 'done', { id: { s: 0 }, minutes: 25 }], ['today', 'done', { id: 'DONE' }],
  ['today', 'logToday', { id: { s: 0 }, minutes: 20, op: 'log-1' }], ['today', 'logToday', { id: { s: 0 }, minutes: 20, op: 'log-1' }],
  ['today', 'timer', { act: 'start', id: { s: 0 } }], ['today', 'timer', { act: 'pause', id: { s: 0 } }], ['today', 'timer', { act: 'cancel', id: { s: 0 }, start: { focus: 1 } }],
  ['today', 'disrupt', { kind: 'block', op: 'dis-1', hours: 2 }], ['today', 'energy', { level: 'LOW' }], ['today', 'replan', {}],
  ['do', 'goalPriority', { id: 'g-read', value: 5 }], ['do', 'restDay', { day: 0, on: true }], ['do', 'pref', { key: 'intensityLevel', value: 'LIGHT' }],
  ['do', 'goalAdd', { op: 'goal-op-1', arch: 'CUSTOM', title: 'Practice guitar', tag: 'creative.practice', minutes: 30, perWeek: 2, priority: 3 }],
  ['do', 'exportBackup', {}], ['do', 'goalPriority', { id: 'g-gone', value: 5 }],
];

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
      /* the commands (SH-36 to SH-46): each one envelope, the copy at the page's state before it and after it, its reply, its host calls */
      const ran = [];
      let skipped = null, done = null;
      for (const [kind, name, payload] of COMMANDS) {
        const pl = Object.assign({}, payload);
        if (pl.id === 'SKIPPED') pl.id = skipped;
        if (pl.id === 'DONE') pl.id = done;
        const r = await command(p, kind, name, pl);
        if (name === 'skip') skipped = r.body.id;
        if (name === 'done' && r.reply && r.reply.ok) done = r.body.id;
        await settle(p, 100);
        const e = await p.evaluate((n) => { const m = window.__dsMock, e = m.envelopes.slice(n).filter((x) => x.kind === 'director.cmd'); const t = m.texts.filter((x) => x.kind === 'director.cmd').pop(); return e.length === 1 ? { cmd: e[0].cmd, flags: e[0].flags, before: t && t.before, after: t && t.text, n: e.length } : { n: e.length }; }, r.seq);
        ran.push({ kind, name, r, e });
      }
      ok('each command: one envelope with its op, name and payload', ran.every((x) => x.e.n === 1 && x.e.cmd.op === x.kind && x.e.cmd.name === x.name && JSON.stringify(x.e.cmd.payload) === JSON.stringify(x.r.body)), ran.map((x) => [x.name, x.e.n]));
      ok('each command: native\'s copy is the page\'s state before it and after it, byte for byte', ran.every((x) => x.e.before === x.r.before && x.e.after === x.r.after), ran.filter((x) => x.e.before !== x.r.before || x.e.after !== x.r.after).map((x) => x.name));
      ok('each command: its reply as the page answered', ran.every((x) => JSON.stringify(x.e.cmd.reply) === JSON.stringify(x.r.reply)), ran.map((x) => [x.name, x.e.cmd && x.e.cmd.reply]));
      const fns = (x) => x.e.cmd.calls.map((c) => c[0]);
      const doneRun = ran.find((x) => x.name === 'done' && x.r.reply.ok);
      ok('a done: the award, the quests read again, the save and the toast, as the host was called', doneRun && ['awardActivity', 'readQuests', 'store.save', 'toast'].every((f) => fns(doneRun).includes(f)) && doneRun.e.cmd.reply.ok, doneRun && fns(doneRun));
      const award = doneRun && doneRun.e.cmd.calls.find((c) => c[0] === 'awardActivity');
      ok('an award\'s arguments and answer travel as JSON texts', award && JSON.parse(doneRun.e.cmd.texts[award[1]])[0].eventId && 'awarded' in JSON.parse(doneRun.e.cmd.texts[award[2]]), award);
      const unskip = ran.find((x) => x.name === 'unskip');
      ok('the take-back goes in full once (its plan, ledger and the plan it left), then as its digest', unskip && unskip.e.cmd.session.before.takeBack && unskip.e.cmd.session.before.takeBack.left && unskip.e.cmd.session.before.takeBack.before.schedule
        && ran.filter((x) => x.e.cmd && x.e.cmd.session.before.takeBack && x.e.cmd.session.before.takeBack.before).length <= 2, unskip && unskip.e.cmd.session.before.takeBack && Object.keys(unskip.e.cmd.session.before.takeBack));
      ok('the screen\'s drawing asks the host nothing the command is held to: no LifeMaxHost.connected from render among a pin\'s calls', fns(ran[0]).every((f) => f !== 'connected'), fns(ran[0]));
      ok('no command flagged across a second under the test clock, none skipped', ran.every((x) => !x.e.flags.length), ran.map((x) => x.e.flags));
      /* the tick: Life Director on screen, five minutes; the Overview's glance, its three steps (a quiet step at most once a minute) */
      await p.evaluate(() => { state.activities.push({ id: 'act-elsewhere', type: 'reading', name: 'Read on the bus', duration: 20, date: new Date(Date.now() - 600000).toISOString() }); });
      await p.evaluate(() => lmaNavTo('life-director'));
      await settle(p, 1000);
      await p.clock.runFor(5 * 60 * 1000 + 1000);
      await settle(p, 400);
      const ticks = await p.evaluate(() => window.__dsMock.envelopes.filter((e) => e.kind === 'director.tick').map((e) => ({ op: e.cmd.op, calls: e.cmd.calls.map((c) => c[0]), ops: e.cmd.after.length })));
      ok('the tick: one envelope each time it runs, with LifeMax\'s log read and the activity logged elsewhere taken in', ticks.some((t) => t.op === 'tick' && t.calls.includes('activitiesSince') && t.ops > 0), ticks);
      const from = await p.evaluate(() => window.__dsMock.envelopes.length);
      await p.evaluate(() => lmaNavTo('dashboard'));
      await settle(p, 1000);
      await p.clock.runFor(2 * 60 * 1000);
      await settle(p, 400);
      const glance = await p.evaluate((n) => window.__dsMock.envelopes.slice(n).filter((e) => e.kind === 'director.tick' && e.cmd.op !== 'tick').map((e) => ({ op: e.cmd.op, t: e.t0 })), from);
      const quiet = await p.evaluate(() => LmaDirectorShadowStatus().stats.skipped.quiet || 0);
      ok('the glance\'s steps: sync, misses and rollover each sent, the quiet ones at most once a minute', ['syncHost', 'processMisses', 'maybeRollover'].every((op) => glance.some((g) => g.op === op)) && quiet > 0 && glance.length <= 3 * 3, { glance, quiet });
      const r = await M(p);
      const kinds = new Set(r.kinds);
      ok('the day sends saves, plans, a repair and read checks', kinds.has('director.save') && kinds.has('director.plan') && kinds.has('director.repair') && kinds.has('director.read'), r.kinds);
      ok('the day sends commands and ticks too', kinds.has('director.cmd') && kinds.has('director.tick'), r.kinds);
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
