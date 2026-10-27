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
  ok('the page has not switched yet', (await view()) === 'dashboard');
  await touch('touchend', 120, 402); const t0 = Date.now();
  await p.waitForFunction(() => lmaView() !== 'dashboard', null, { timeout: 2000 }); const lat = Date.now() - t0;
  ok('a drag past a fifth of the screen switches to the next tab', (await view()) === 'character', await view());
  ok('and it does so quickly after release', lat < 400, lat);
  await settle();
  ok('the movement is cleared afterwards (no leftover transform)', await p.evaluate(() => { const c = document.querySelector('main.content'); return !c.style.transform }));
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
  // after the swipe nothing replays, and exactly one tab is lit
  await p.evaluate(() => { document.documentElement.classList.remove('reduce-motion'); navigate('dashboard') }); await p.waitForTimeout(500);
  await swipe(330, 130, 400, 120, 8); await touch('touchend', 130, 402); await settle(); await p.waitForTimeout(500);
  const post = await p.evaluate(() => ({ anims: document.querySelectorAll('.view.active > *').length && [...document.querySelectorAll('.view.active > *')].filter(e => e.getAnimations().length).length, lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView(), cls: document.documentElement.classList.contains('lma-swiping'), layer: getComputedStyle(document.querySelector('main.content')).willChange }));
  ok('after a swipe the cards do not start fading in again (no stutter)', post.anims === 0 && post.cls, post);
  ok('after a swipe exactly one tab is highlighted, the one you are on', post.lit.length === 1 && post.lit[0] === post.view, post);
  ok('the page keeps its own layer between swipes (no layer churn that blinks the bar)', await p.evaluate(() => getComputedStyle(document.querySelector('main.content')).backfaceVisibility === 'hidden') && post.layer === 'auto', post.layer);
  await p.evaluate(() => lmaNavTo('quests')); await p.waitForTimeout(100);
  ok('a normal tab change turns the card animations back on', await p.evaluate(() => !document.documentElement.classList.contains('lma-swiping')));
  await p.waitForTimeout(600);
  // many quick swipes and taps in a row never leave two tabs lit
  for (let i = 0; i < 6; i++) { await swipe(320, 110, 400, 50, 4); await touch('touchend', 110, 402); await p.waitForTimeout(60); await p.evaluate(() => { const b = [...document.querySelectorAll('.navbtn[data-view]')]; b[Math.floor(Math.random() * b.length)].click() }); await p.waitForTimeout(40) }
  await settle(); const lit = await p.evaluate(() => ({ lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView() }));
  ok('after many quick swipes and taps only the current tab is lit', lit.lit.length === 1 && lit.lit[0] === (lit.view === 'media' || lit.view === 'history' ? 'library' : lit.view === 'meals' || lit.view === 'recipes' ? 'food' : lit.view), lit);
  const hov = await p.evaluate(() => { let bad = 0, seen = 0; for (const sh of document.styleSheets) { let rs; try { rs = sh.cssRules } catch (e) { continue } const walk = (rs, ok) => { for (const r of rs) { if (r.cssRules && r.media) walk(r.cssRules, ok || /hover:\s*hover/.test(r.media.mediaText)); else if (r.selectorText && /\.navbtn:hover/.test(r.selectorText)) { seen++; if (!ok) bad++ } } }; walk(rs, false) } return { bad, seen } });
  ok('the tab hover look only applies with a mouse, so a tapped tab does not stay shaded on a phone', hov.seen >= 1 && hov.bad === 0, hov);
  // the bottom bar never goes missing while the page slides
  await p.evaluate(() => { navigate('dashboard'); window.__s = []; const bar = document.querySelector('.sidebar'); const f = () => { const r = bar.getBoundingClientRect(), cs = getComputedStyle(bar); window.__s.push([r.top, cs.opacity, cs.visibility, cs.display]); window.__raf = requestAnimationFrame(f) }; f() });
  await p.waitForTimeout(400); await swipe(330, 130, 400, 100, 8); await touch('touchend', 130, 402); await p.waitForTimeout(900);
  const bar = await p.evaluate(() => { cancelAnimationFrame(window.__raf); const s = window.__s, bad = s.filter(x => x[0] < 700 || x[1] !== '1' || x[2] !== 'visible' || x[3] === 'none'); const cs = getComputedStyle(document.querySelector('.sidebar')); return { frames: s.length, bad: bad.length, blur: cs.backdropFilter, bg: cs.backgroundColor } });
  ok('the bottom bar stays in place and visible on every frame of a swipe', bar.frames > 20 && bar.bad === 0, bar);
  ok('the bottom bar does not blur the page behind it on a phone', bar.blur === 'none', bar);
  // the bar's own scrolling has one mover, and the lit tab stays on screen
  await p.setViewportSize({ width: 360, height: 780 }); await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await p.evaluate(() => { window.__s = []; const bar = document.querySelector('.sidebar'); const f = () => { const a = bar.querySelector('.navbtn.active'), r = a ? a.getBoundingClientRect() : null; window.__s.push([bar.scrollLeft, r && r.left, r && r.right]); window.__raf = requestAnimationFrame(f) }; f() });
  for (let k = 0; k < 8; k++) { await swipe(300, 120, 400, 70, 6); await touch('touchend', 120, 402); await p.waitForTimeout(420) }
  const bs = await p.evaluate(() => { cancelAnimationFrame(window.__raf); return window.__s });
  let rev = 0, dir = 0, prev = null, off = 0; for (const x of bs) { if (prev !== null && x[0] !== prev) { const d = Math.sign(x[0] - prev); if (dir && d !== dir) rev++; dir = d } prev = x[0]; if (x[1] != null && (x[1] < -1 || x[2] > 361)) off++ }
  ok('swiping through the tabs: the bar only ever scrolls one way at a time (no jumping back)', bs.length > 100 && rev === 0, { rev, frames: bs.length });
  ok('and the lit tab is never off the screen', off === 0, off);
  ok('the bar has no smooth-scroll fight: no scrollIntoView left in navigation', !/scrollIntoView/.test(await p.evaluate(() => navigate.toString() + lmaNavTo.toString())));
  // the setting
  await p.evaluate(() => { lmaSetSwipeAnim(false); navigate('dashboard') }); await p.waitForTimeout(400);
  await touch('touchstart', 300, 400); await touch('touchmove', 280, 401); await touch('touchmove', 200, 402); await p.waitForTimeout(40);
  ok('with "page slides with your finger" off, nothing moves during the swipe', (await tx()) === 0);
  await touch('touchmove', 100, 402); await touch('touchend', 100, 402); await p.waitForTimeout(400);
  ok('and the swipe still switches the tab, instantly', (await view()) !== 'dashboard', await view());
  ok('the Settings page has the switch', await p.evaluate(() => { navigate('settings'); return !!document.getElementById('lmaSwipeAnimToggle') }));
  await p.evaluate(() => lmaSetSwipeAnim(true));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
