/* Swipe between tabs: the page follows the finger. Run:  NODE_PATH=$(npm root -g) node tests/swipe.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, hasTouch: true })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } navigate('dashboard'); window.scrollTo(0, 0) });
  await p.waitForTimeout(600);
  const touch = (type, x, y) => p.evaluate(([type, x, y]) => { const tgt = document.elementFromPoint(Math.min(Math.max(x, 1), innerWidth - 1), y) || document.body; const t = new Touch({ identifier: 1, target: tgt, clientX: x, clientY: y }); tgt.dispatchEvent(new TouchEvent(type, { bubbles: true, cancelable: true, touches: type === 'touchend' ? [] : [t], changedTouches: [t], targetTouches: type === 'touchend' ? [] : [t] })) }, [type, x, y]);
  const view = () => p.evaluate(() => lmaView());
  const tx = () => p.evaluate(() => { const c = document.querySelector('main.content'), m = /translate3d\(([-\d.]+)px/.exec(c.style.transform || ''); return m ? +m[1] : 0 });
  const swipe = async (x0, x1, y, ms, steps) => { await touch('touchstart', x0, y); for (let i = 1; i <= steps; i++) { await touch('touchmove', x0 + (x1 - x0) * i / steps, y + (i % 2)); if (ms) await p.waitForTimeout(ms / steps) } };
  const settle = () => p.waitForTimeout(700);
  // follows the finger
  await touch('touchstart', 300, 400); await touch('touchmove', 280, 401); await touch('touchmove', 200, 402); await touch('touchmove', 120, 402); await p.waitForTimeout(60);
  const dragged = await tx(); const op = await p.evaluate(() => +document.querySelector('main.content').style.opacity);
  ok('while the finger is down, the page moves with it (left)', dragged < -120 && dragged > -200, dragged);
  ok('and it fades a little', op < 1 && op > .7, op);
  ok('the page has not switched yet', (await view()) === 'dashboard');
  await touch('touchend', 120, 402); const t0 = Date.now();
  await p.waitForFunction(() => lmaView() !== 'dashboard', null, { timeout: 2000 }); const lat = Date.now() - t0;
  ok('a drag past a fifth of the screen switches to the next tab', (await view()) === 'character', await view());
  ok('and it does so quickly after release', lat < 400, lat);
  await settle();
  ok('the movement is cleared afterwards (no leftover transform)', await p.evaluate(() => { const c = document.querySelector('main.content'); return !c.style.transform && !c.style.opacity && !document.documentElement.classList.contains('lma-swiping') }));
  // a short, slow drag springs back
  const before = await view(); await swipe(260, 230, 400, 400, 6); await touch('touchend', 230, 402); await settle();
  ok('a short slow drag springs back and stays on the page', (await view()) === before && (await tx()) === 0 && await p.evaluate(() => !document.querySelector('main.content').style.transform), await view());
  // a quick flick commits even when short
  await swipe(300, 250, 400, 40, 5); await touch('touchend', 250, 402); await settle();
  ok('a quick flick of 50 px switches tabs', (await view()) !== before, await view());
  // swipe back
  const at = await view(); await swipe(100, 320, 400, 120, 8); await touch('touchend', 320, 402); await settle();
  ok('swiping right goes to the previous tab', (await view()) !== at, [at, await view()]);
  // end of the line: rubber band
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await touch('touchstart', 100, 400); await touch('touchmove', 140, 401); await touch('touchmove', 300, 402); await p.waitForTimeout(60);
  const rb = await tx(); await touch('touchend', 300, 402); await settle();
  ok('before the first tab it only stretches a little and comes back', rb > 0 && rb < 80 && (await view()) === 'dashboard', rb);
  // scrolling is left alone
  await touch('touchstart', 200, 500); await touch('touchmove', 202, 470); await touch('touchmove', 205, 420); await touch('touchmove', 207, 380); await p.waitForTimeout(50);
  ok('a vertical scroll does not move the page sideways', (await tx()) === 0); await touch('touchend', 207, 380);
  // switched off
  await p.evaluate(() => { lmaLoad().swipe = false; lmaSave() }); await swipe(300, 100, 400, 100, 6); await touch('touchend', 100, 402); await settle();
  ok('with the setting off nothing happens', (await view()) === 'dashboard' && (await tx()) === 0);
  await p.evaluate(() => { lmaLoad().swipe = true; lmaSave() });
  // starting at the very edge is left to the phone's back gesture
  await swipe(10, 200, 400, 100, 6); await touch('touchend', 200, 402); await settle(); ok('a swipe that starts at the screen edge is ignored', (await view()) === 'dashboard');
  // reduced motion falls back to the old release-and-go
  await p.evaluate(() => document.documentElement.classList.add('reduce-motion')); await swipe(330, 120, 400, 100, 6); await touch('touchend', 120, 402); await settle();
  ok('with reduced motion it still switches, without the movement', (await view()) === 'character' && (await tx()) === 0, await view());
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
