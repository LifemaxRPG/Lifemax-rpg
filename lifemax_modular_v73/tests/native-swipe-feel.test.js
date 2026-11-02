/* Native tab swipe, phase 3: the web swipe with the native feel. With Native tab swipe (beta) on, the swipe between web tabs
   (34-navigation) follows the native curve (1:1, then heavy, a catch with a pop and a tick), releases by the native rule (past the
   catch commits; a hard flick after a little travel commits; a casual flick or a flick back cancels), stretches only a little at the
   ends, and settles a cancel on a spring's curve. Switched off it is the old swipe, unchanged (tests/swipe.test.js).
   Checked: the curve against native's golden values and clamps, the drag in the page (the cards' place, the one tick), every kind of
   release against the old rule, the ends, reduced motion, and the switch off.
   Run: NODE_PATH=$(npm root -g) node tests/native-swipe-feel.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 500) : '')) } };
const near = (a, b, e) => Math.abs(a - b) <= e;

(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, hasTouch: true })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lma_qdeal','off')");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } navigate('dashboard'); window.scrollTo(0, 0) });
  await p.waitForTimeout(600);
  const W = 412;
  const flag = (on) => p.evaluate((on) => { lmaLoad().nativeSwipe = on; lmaSave() }, on);
  // the page reads a touch's time from Date.now while it handles it, so for that moment Date.now gives at
  const touch = (type, x, y, at) => p.evaluate(([type, x, y, at]) => { const now = Date.now; if (at) Date.now = () => at; try { const tgt = document.elementFromPoint(Math.min(Math.max(x, 1), innerWidth - 1), y) || document.body; const t = new Touch({ identifier: 1, target: tgt, clientX: x, clientY: y }); tgt.dispatchEvent(new TouchEvent(type, { bubbles: true, cancelable: true, touches: type === 'touchend' ? [] : [t], changedTouches: [t], targetTouches: type === 'touchend' ? [] : [t] })) } finally { Date.now = now } }, [type, x, y, at]);
  const view = () => p.evaluate(() => lmaView());
  const swx = () => p.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => { const v = document.querySelector('.view.active'); r(v ? parseFloat(v.style.getPropertyValue('--swx') || '0') : 0) }))));
  const settle = async () => { await p.waitForFunction(() => !lmaPgView, null, { timeout: 5000 }); await p.waitForTimeout(500) };
  /* a finger from x0 moving at pxPerMs for distPx, one touch every 8 ms (the times are the page's own, whatever a busy machine does),
     and the last touch's time, for the lift. A finger moving left (toward the next tab) is a negative pxPerMs. */
  const drag = async (x0, distPx, pxPerMs, lift = true) => {
    const t0 = await p.evaluate(() => Date.now()), step = 8 * Math.abs(pxPerMs), n = Math.max(1, Math.round(distPx / step)), sign = pxPerMs < 0 ? -1 : 1;
    await touch('touchstart', x0, 400, t0);
    for (let i = 1; i <= n; i++) await touch('touchmove', x0 + sign * step * i, 400 + (i % 2), t0 + 8 * i);
    const end = t0 + 8 * n;
    if (lift) await touch('touchend', x0 + sign * step * n, 402, end);
    return end;
  };
  const feelUp = () => p.evaluate(() => ({ latched: lmaSw && lmaSw.feel && lmaSw.feel.latched, u: lmaSw && lmaSw.feel && lmaSw.feel.u }));
  const start = async (v = 'dashboard') => { await p.evaluate((v) => { navigate(v); window.scrollTo(0, 0) }, v); await p.waitForTimeout(450); };

  /* ---------- the curve, against native's golden values and clamps (TensionCurveTest, SwipeTuneTest) ---------- */
  ok('the page\'s curve gives native\'s golden values .10/.25/.30/.35/.40/.45 -> .100/.243/.280/.310/.333/.349', await p.evaluate(() => [[.1, .1], [.25, .243], [.3, .28], [.35, .31], [.4, .333], [.45, .349]].every(([u, o]) => Math.abs(SwipeFeel.hold(u, SwipeFeel.tune({})) - o) < 1e-3)));
  ok('the slope is 1 at the knee and the heaviness at the catch', await p.evaluate(() => { const t = SwipeFeel.tune({}), h = 1e-4, s = (u) => (SwipeFeel.hold(u + h, t) - SwipeFeel.hold(u - h, t)) / (2 * h); return Math.abs(s(t.knee - 0.01) - 1) < 0.05 && Math.abs(s(t.breakAt - 0.0001) - (1 - (1 - t.heavy) * 1)) < 0.05 }));
  ok('a wall gives at most its maximum, 8% of the width, and starts at the slope', await p.evaluate(() => { const t = SwipeFeel.tune({}); let max = 0; for (let i = 0; i <= 1000; i++) max = Math.max(max, SwipeFeel.wall(i / 100, t)); return max <= t.wallMax && Math.abs(SwipeFeel.wall(1e-4, t) / 1e-4 - t.wallSlope) < 1e-2 }));
  const clamps = await p.evaluate(() => {
    const t = SwipeFeel.tune({ knee: 0.9, heavy: 0, hyst: 5, popDamping: 0, popStiffness: 1, wallMax: 1, wallSlope: 9, flingDp: 1, flingMin: 0, backFlingDp: 9e9, breakAt: 0.1 });
    return t;
  });
  ok('every value is held to native\'s clamp', clamps.knee === 0.35 && clamps.heavy === 0.05 && clamps.hyst === 0.15 && clamps.popDamping === 0.4 && clamps.popStiffness === 400 && clamps.wallMax === 0.15 && clamps.wallSlope === 0.6 && clamps.flingDp === 500 && clamps.flingMin === 0.05 && clamps.backFlingDp === 2000, clamps);
  ok('the catch is at least 0.10 past the knee, and a value that is not a number keeps the default', await p.evaluate(() => { const t = SwipeFeel.tune({ knee: 0.3, breakAt: 0.32 }), n = SwipeFeel.tune({ knee: NaN, heavy: 'x' }); return near(t.breakAt, 0.4) && n.knee === 0.18 && n.heavy === 0.25; function near(a, c) { return Math.abs(a - c) < 1e-9 } }));
  ok('the cancel settles on a spring\'s curve: 0 to 1, monotonic, 95% by about 165 ms', await p.evaluate(() => { const f = SwipeFeel.settle; let prev = -1, mono = true; for (let i = 0; i <= 100; i++) { const v = f(i / 100); if (v < prev - 1e-9) mono = false; prev = v } let k95 = 1; for (let i = 0; i <= 1000; i++) if (f(i / 1000) >= 0.95) { k95 = i / 1000; break } const ms = k95 * SwipeFeel.CANCEL_MS; return f(0) === 0 && Math.abs(f(1) - 1) < 1e-9 && mono && ms > 130 && ms < 210 }));

  /* ---------- the switch ---------- */
  await flag(false);
  ok('with the switch off the feel is not asked for', await p.evaluate(() => !SwipeFeel.on()));
  await start();
  await touch('touchstart', 360, 400); await touch('touchmove', 340, 401); await touch('touchmove', 200, 402); await p.waitForTimeout(80);
  ok('and the cards follow the finger 1:1 as they always did (about -160 px)', near(await swx(), -160, 8), await swx());
  await touch('touchend', 200, 402, Date.now() + 500); await settle(); await start();
  await flag(true);
  ok('with it on, and the page\'s motion on, it is', await p.evaluate(() => SwipeFeel.on()));
  ok('but not when the page\'s motion is off (nothing follows the finger then)', await p.evaluate(() => { lmaLoad().swipeAnim = false; const r = !SwipeFeel.on(); lmaLoad().swipeAnim = true; return r }));

  /* ---------- the drag ---------- */
  await start();
  await p.evaluate(() => { window.__ticks = []; const was = window.lmaBuzz; window.__was = was; window.lmaBuzz = (k) => { window.__ticks.push(k) } });
  // claim at the second event: 24 px in; the cards start at 0 under the finger, then follow the curve
  const t0 = await p.evaluate(() => Date.now());
  await touch('touchstart', 340, 400, t0); await touch('touchmove', 330, 401, t0 + 8); await touch('touchmove', 316, 400, t0 + 16);
  ok('the cards start at 0 where the drag is claimed, not at the slop', near(await swx(), 0, 1), await swx());
  let T = t0 + 16; // the page's clock: its springs run on the touches' own times
  const at = (u, dt = 8) => touch('touchmove', 316 - u * W, 400 + (Math.round(T) % 2), (T += dt));
  await at(0.10); ok('at 10% of the width the cards are at 10% (1:1)', near(await swx(), -0.1 * W, 2), await swx());
  await at(0.25); ok('at 25% they are at 24.3%: heavy now', near(await swx(), -0.243 * W, 2), await swx());
  await at(0.40); ok('at 40% they are at 33.3%', near(await swx(), -0.333 * W, 2), await swx());
  ok('no tick before the catch', await p.evaluate(() => window.__ticks.length === 0));
  await at(0.46);
  ok('past the catch the tick plays once', await p.evaluate(() => window.__ticks.length === 1 && window.__ticks[0] === 'tick'), await p.evaluate(() => window.__ticks));
  await at(0.46, 300);
  ok('and the cards have popped to the finger: 46%', near(await swx(), -0.46 * W, 3), await swx());
  ok('the drag is latched', (await feelUp()).latched === true);
  await at(0.42); ok('pulled back to just under the catch (46% to 42%) it holds on: still latched, 1:1', (await feelUp()).latched === true && near(await swx(), -0.42 * W, 4), await swx());
  await at(0.30); await at(0.30, 300);
  ok('pulled back well under it the tension returns (30% of travel: 28%) with no second tick', near(await swx(), -0.28 * W, 4) && (await feelUp()).latched === false && (await p.evaluate(() => window.__ticks.length)) === 1, [await swx(), await p.evaluate(() => window.__ticks)]);
  await at(0.50); ok('over the catch again it ticks again', await p.evaluate(() => window.__ticks.length === 2));
  await touch('touchend', 316 - 0.5 * W, 400, T + 400); await settle();
  ok('released past the catch it goes to the next tab', (await view()) === 'character', await view());
  await p.evaluate(() => { window.lmaBuzz = window.__was });

  /* ---------- the release rule against the old one ---------- */
  const outcome = async (label, x0, dist, pxPerMs, from = 'dashboard') => {
    await start(from); await flag(true);
    await drag(x0, dist, pxPerMs); await settle(); const feel = await view();
    await start(from); await flag(false);
    await drag(x0, dist, pxPerMs); await settle(); const old = await view();
    await flag(true);
    return { feel: feel !== from, old: old !== from, label };
  };
  // travel is counted from the claim (about 24 px in); 412 px wide
  let r = await outcome('slow, 30% of the width', 340, 0.3 * W + 24, -0.12);
  ok('a slow pull to 30% springs back (the old rule switched at 22%)', r.old === true && r.feel === false, r);
  r = await outcome('slow, past the catch', 340, 0.5 * W + 24, -0.12);
  ok('a slow pull past the catch goes', r.feel === true, r);
  r = await outcome('casual flick, 900 dp/s', 340, 0.3 * W, -0.9);
  ok('a casual flick of 900 dp/s over 30% springs back: that is the "really swipe"', r.feel === false && r.old === true, r);
  r = await outcome('hard flick, 1500 dp/s', 340, 0.3 * W, -1.5);
  ok('a hard flick of 1500 dp/s over 30% goes', r.feel === true, r);
  r = await outcome('hard flick, too short', 340, 0.07 * W + 24, -3);
  ok('a hard flick over less than 12% of travel springs back (the old rule went at 36 px)', r.feel === false, r);
  // latched and then flicked back: cancels even past the catch
  await start(); await flag(true);
  const e1 = await drag(340, 0.5 * W + 24, -0.2, false);
  const back = await p.evaluate(() => Date.now());
  await touch('touchmove', 340 - 0.5 * W - 24 + 40, 400, back); await touch('touchmove', 340 - 0.5 * W - 24 + 90, 401, back + 50);
  await touch('touchend', 340 - 0.5 * W - 24 + 100, 402, back + 60); await settle();
  ok('a flick back (800 dp/s away) cancels even past the catch', (await view()) === 'dashboard', await view());

  /* ---------- the ends ---------- */
  await start('dashboard'); await flag(true);
  await drag(60, 0.6 * W, 0.5, false);
  const wall = await swx();
  ok('before the first tab the cards stretch at most 8% of the width, however far the finger goes', wall > 0 && wall <= 0.08 * W + 1, wall);
  await touch('touchend', 60 + 0.6 * W, 402, Date.now() + 40); await settle();
  ok('and spring back; nothing switches', (await view()) === 'dashboard' && (await swx()) === 0, await view());

  /* ---------- a cancel settles, then nothing is left over ---------- */
  await start(); await flag(true);
  await drag(340, 0.2 * W + 24, -0.12, false);
  await touch('touchend', 340 - 0.2 * W - 24, 400, Date.now() + 20);
  await p.waitForTimeout(120); const mid = await swx();
  ok('a cancel is under way after 120 ms: neither at the finger\'s place nor home', mid < 0 && mid > -0.15 * W, mid);
  await settle();
  ok('then it is home and clear: no drag state, no leftover offset', await p.evaluate(() => !document.documentElement.classList.contains('lma-drag') && !document.querySelector('.view.active').style.getPropertyValue('--swx')));

  await flag(false);
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
