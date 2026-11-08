/* Native economy check (beta): the page's cost under a slow CPU (docs/specs/ECONOMY_SHADOW_PLAN.md 4 budgets, WP6f). The real page,
   with the check on and a stand-in NativeShadow (tests/native-shadow-mock.js), on the established save grown to 1 MB and to 3 MB,
   with Chromium's CPU slowed 1x, 4x (the usual stand-in for a mid-range phone) and 6x (Emulation.setCPUThrottlingRate, set once the
   page is up so the boot is not what is timed). Each run takes the same 24 taps (quests ticked and unticked, Logs, cat care, bare
   saves) and reports per action:
   - capture: what the page's budget counts (the two passes over the shadow keys inside the action, LmaShadow's stats.costs);
   - send: the envelope built and posted after the action (its delta and text, in the setTimeout(0) after it), measured as the time
     from the action's end to a timer queued after the check's own, less the same with the check off (the page's own deferred work);
   - overhead: the whole action with the check on, less the same action with the check off.
   The budgets: p50 under 5 ms and p95 under 15 ms at 1 MB (targets), five actions in a row over 50 ms turn the page side off (the
   hard limit, counted on capture). Every run says whether that limit tripped. The self-disable is then checked where it should and
   should not trip: four slow actions and a quick one leave the check on, five in a row stop it ("slow"); two page faults leave it
   on, the third stops it ("faults"); a stopped check sends nothing more and the page's actions still run. Last, a plugin that
   answers slowly (2 s) or never: every action is still posted, none waits for it, nothing queues and nothing counts as a fault.
   The numbers depend on the machine: the hard checks are the self-disable rules, the plugin that answers late or never, and that
   the page side stops exactly when the slow rule says; the budgets (1 MB within the targets at 1x, the hard limit not tripping at
   4x) are hard only with PERF_STRICT=1 and printed as NOTE lines otherwise. RATES picks the CPU slow-downs (default 1,4; the plan's
   numbers are RATES=1,4,6).
   Run: [RATES=1,4,6] [PERF_STRICT=1] NODE_PATH=$(npm root -g) node tests/native-shadow-perf.test.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('./native-shadow-mock.js');
const file = require('./current-page')(process.argv[2]);
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'quest-content.json'), 'utf8'));
const SAVE = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', 'established.json'), 'utf8')).state;
const STRICT = process.env.PERF_STRICT === '1', MB = 1024 * 1024, TAPS = 24, RATES = (process.env.RATES || '1,4').split(',').map(Number);
let pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

function grown(bytes) {
  const big = JSON.parse(JSON.stringify(SAVE)), recs = SAVE.activities;
  const per = JSON.stringify(recs).length / recs.length, n = Math.ceil(bytes / per);
  big.activities = Array.from({ length: n }, (_, i) => Object.assign({}, recs[i % recs.length], { id: 'big-' + i }));
  return big;
}

async function open(browser, save, on) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: 'Europe/London' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(save)) + ');');
  await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash, on, quests: true, day: true }));
  await p.goto('file://' + file);
  await p.waitForFunction(on => typeof window.LmaShadow === 'object' && typeof recordActivity === 'function' && (!on || LmaShadow.status().live), on, { timeout: 120000 });
  await p.waitForTimeout(1500);
  return p;
}

/* in the page: the taps, each timed whole and to the end of what the check posts after it */
function taps(n) {
  const out = [], quests = state.quests.filter(q => !q.hand);
  const steps = [
    i => toggleQuest(quests[i % quests.length].id, true),
    i => toggleQuest(quests[(i - 1) % quests.length].id, false),
    () => recordActivity({ type: 'reading', customName: '', duration: '30', quality: '1', skill: '', primary: '', secondary: '', notes: '' }),
    () => ecoCare('feed'),
    () => save(),
  ];
  let chain = Promise.resolve();
  for (let i = 0; i < n; i++) chain = chain.then(() => new Promise(done => {
    const t = performance.now();
    steps[i % steps.length](i);
    const end = performance.now();
    setTimeout(() => { out.push({ action: end - t, send: performance.now() - end }); setTimeout(done, 20) }, 0);
  }));
  return chain.then(() => ({ out, status: LmaShadow.status() }));
}

const pct = (xs, q) => { const s = xs.slice().sort((a, b) => a - b); return s.length ? s[Math.min(s.length - 1, Math.floor(q * s.length))] : 0 };
const f = x => x.toFixed(1);

(async () => {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const results = [];
  try {
    for (const size of [1, 3]) {
      const save = grown(size * MB);
      for (const rate of RATES) {
        const runs = {};
        for (const on of [false, true]) {
          const p = await open(browser, save, on);
          const cdp = await p.context().newCDPSession(p);
          await cdp.send('Emulation.setCPUThrottlingRate', { rate });
          const r = await p.evaluate(taps, TAPS);
          await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
          runs[on ? 'on' : 'off'] = Object.assign(r, { errors: p.errors.slice() });
          await p.context().close();
        }
        const capture = runs.on.status.stats.costs.slice(-TAPS);
        const send = runs.on.out.map((x, i) => x.send - runs.off.out[i].send), overhead = runs.on.out.map((x, i) => x.action - runs.off.out[i].action);
        let streak = 0, worst = 0;
        for (const c of capture) { streak = c > 50 ? streak + 1 : 0; worst = Math.max(worst, streak) }
        const row = { size, rate, capture50: pct(capture, 0.5), capture95: pct(capture, 0.95), captureMax: Math.max(...capture), send50: pct(send, 0.5), send95: pct(send, 0.95),
          overhead50: pct(overhead, 0.5), overhead95: pct(overhead, 0.95), slowStreak: worst, live: runs.on.status.live, stopped: runs.on.status.stopped };
        results.push(row);
        console.log('PERF ' + size + ' MB at ' + rate + 'x: capture p50 ' + f(row.capture50) + ' ms p95 ' + f(row.capture95) + ' ms max ' + f(row.captureMax) +
          ' ms; send p50 ' + f(row.send50) + ' ms p95 ' + f(row.send95) + ' ms; overhead p50 ' + f(row.overhead50) + ' ms p95 ' + f(row.overhead95) +
          ' ms; longest run over 50 ms ' + worst + '; ' + (row.live ? 'still on' : 'stopped (' + row.stopped + ')'));
        ok(size + ' MB at ' + rate + 'x: the page threw nothing, check on or off', !runs.on.errors.length && !runs.off.errors.length, runs.on.errors.concat(runs.off.errors));
        ok(size + ' MB at ' + rate + 'x: the page side stops exactly when five actions in a row pass 50 ms', row.live === (worst < 5), row);
        /* machine-dependent: hard only with PERF_STRICT=1 (tools/shadow_proof.sh), a note otherwise (CI runners vary) */
        const budget = (name, c) => STRICT ? ok(name, c, row) : console.log((c ? 'NOTE met: ' : 'NOTE missed: ') + name);
        if (size === 1 && rate === 1) budget('1 MB at 1x: within the targets (capture p50 under 5 ms, p95 under 15 ms)', row.capture50 < 5 && row.capture95 < 15);
        if (size === 1 && rate === 4) budget('1 MB at 4x: the hard limit does not trip', row.live);
      }
    }

    /* ---------- the self-disable rules, with slowness and faults put in on purpose ---------- */
    {
      const p = await open(browser, SAVE, true);
      const r = await p.evaluate(async () => {
        const B = LMA_SHADOW_BOOT, real = B.capture, wait = ms => { const t = performance.now(); while (performance.now() - t < ms) { } };
        const sent = () => window.__shadowMock.envelopes.length, tick = () => new Promise(r => setTimeout(r, 30));
        const q = state.quests.filter(x => !x.hand);
        let slow = false;
        B.capture = function () { if (slow) wait(30); return real.apply(this, arguments) };
        const tap = async (i) => { toggleQuest(q[i % q.length].id, i % 2 === 0); await tick() };
        const out = {};
        slow = true; for (let i = 0; i < 4; i++) await tap(i);
        slow = false; await tap(4);
        out.afterFourSlow = LmaShadow.status().live;
        slow = true; for (let i = 5; i < 9; i++) await tap(i);
        out.afterFourMore = LmaShadow.status().live;
        await tap(9);
        out.afterFive = LmaShadow.status();
        const before = sent(), done = state.questDone && Object.keys(state.questDone).length;
        slow = false; await tap(10);
        out.sentAfterStop = sent() - before;
        out.pageStillRuns = Object.keys(state.questDone).length !== done;
        out.unwrapped = !toggleQuest.__lmaShadow;
        B.capture = real;
        return out;
      });
      ok('four slow actions and a quick one leave the check on', r.afterFourSlow === true, r);
      ok('four slow in a row leave it on, the fifth in a row stops it ("slow")', r.afterFourMore === true && !r.afterFive.live && r.afterFive.stopped === 'slow', r.afterFive);
      ok('once stopped the page sends nothing more, its wrappers are gone and its actions still run', r.sentAfterStop === 0 && r.pageStillRuns && r.unwrapped, r);
      ok('the slow page threw nothing', !p.errors.length, p.errors);
      await p.context().close();
    }
    {
      const p = await open(browser, SAVE, true);
      const r = await p.evaluate(async () => {
        const B = LMA_SHADOW_BOOT, real = B.capture, tick = () => new Promise(r => setTimeout(r, 30));
        const q = state.quests.filter(x => !x.hand);
        let broken = false;
        B.capture = function () { if (broken) { broken = false; throw new Error('a fault') } return real.apply(this, arguments) };
        const out = {}, tap = async (i, fault) => { broken = fault; toggleQuest(q[i % q.length].id, i % 2 === 0); await tick() };
        await tap(0, true); await tap(1, false); await tap(2, true);
        out.afterTwo = LmaShadow.status();
        const done = Object.keys(state.questDone).length;
        await tap(4, true);
        out.afterThree = LmaShadow.status();
        out.pageStillRuns = Object.keys(state.questDone).length !== done;
        B.capture = real;
        return out;
      });
      ok('two page faults leave the check on', r.afterTwo.live && r.afterTwo.stats.faults === 2, r.afterTwo);
      ok('the third fault stops it ("faults"), and the tap that faulted still ran', !r.afterThree.live && r.afterThree.stopped === 'faults' && r.pageStillRuns, r.afterThree);
      ok('the faulting page threw nothing', !p.errors.length, p.errors);
      await p.context().close();
    }
    /* ---------- a plugin that answers slowly, or never: the page never waits for it ---------- */
    for (const [word, answer] of [['slowly (2 s)', 'slow'], ['never', 'never']]) {
      const p = await open(browser, SAVE, true);
      const r = await p.evaluate(async (answer) => {
        const P = window.Capacitor.Plugins.NativeShadow, real = P.observe;
        let asked = 0;
        P.observe = function () { asked++; return answer === 'never' ? new Promise(() => { }) : new Promise(r => setTimeout(() => r(real.apply(P, arguments)), 2000)) };
        const q = state.quests.filter(x => !x.hand), times = [];
        for (let i = 0; i < 20; i++) { const t = performance.now(); toggleQuest(q[i % q.length].id, i % 2 === 0); times.push(performance.now() - t); await new Promise(r => setTimeout(r, 10)) }
        const st = LmaShadow.status();
        return { asked, live: st.live, faults: st.stats.faults, queued: st.queued, slowest: Math.max(...times) };
      }, answer);
      ok('a plugin that answers ' + word + ': every action posted, none waited for, no fault, nothing queued', r.asked === 20 && r.live && r.faults === 0 && r.queued === 0, r);
      ok('a plugin that answers ' + word + ': the page threw nothing', !p.errors.length, p.errors);
      await p.context().close();
    }
  } finally { await browser.close() }
  console.log('PERF table ' + JSON.stringify(results));
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.log('FAIL ' + String((e && e.stack) || e)); process.exit(1) });
