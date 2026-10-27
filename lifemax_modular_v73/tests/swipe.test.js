/* Swipe between tabs. The page is never moved during a swipe (that made the bottom bar blink on some phones): a small tag follows the
   pull, and the new page's cards slide in. Run:  NODE_PATH=$(npm root -g) node tests/swipe.test.js [built html] */
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
  const ind = () => p.evaluate(() => { const e = document.getElementById('lmaSwipeInd'); if (!e) return null; const cs = getComputedStyle(e); return { show: e.classList.contains('show'), ready: e.classList.contains('ready'), text: e.textContent, side: e.classList.contains('to-next') ? 'next' : e.classList.contains('to-prev') ? 'prev' : '', op: +cs.opacity, disp: cs.display } });
  const moved = () => p.evaluate(() => { const c = document.querySelector('main.content'); return { t: c.style.transform, o: c.style.opacity, w: c.style.willChange } });
  const swipe = async (x0, x1, y, ms, steps) => { await touch('touchstart', x0, y); for (let i = 1; i <= steps; i++) { await touch('touchmove', x0 + (x1 - x0) * i / steps, y + (i % 2)); if (ms) await p.waitForTimeout(ms / steps) } };
  const settle = () => p.waitForTimeout(700);
  // during a pull: a tag, never a moving page
  await touch('touchstart', 300, 400); await touch('touchmove', 280, 401); await touch('touchmove', 240, 402); await p.waitForTimeout(60);
  let i1 = await ind(), mv = await moved();
  ok('while pulling left, a tag on the right edge names the next tab', i1 && i1.show && i1.side === 'next' && /Character/.test(i1.text) && i1.disp === 'block', i1);
  ok('the page itself is not moved, faded or promoted while you pull', !mv.t && !mv.o && !mv.w, mv);
  await touch('touchmove', 150, 402); await p.waitForTimeout(60); i1 = await ind();
  ok('past a fifth of the screen the tag says "ready"', i1.ready, i1);
  ok('the page has not switched yet', (await view()) === 'dashboard');
  await touch('touchend', 150, 402); const t0 = Date.now();
  await p.waitForFunction(() => lmaView() !== 'dashboard', null, { timeout: 2000 }); const lat = Date.now() - t0;
  ok('letting go switches at once', (await view()) === 'character' && lat < 150, [await view(), lat]);
  ok('and the tag is gone', !(await ind()).show);
  const dirAttr = await p.evaluate(() => document.documentElement.dataset.swipeDir); ok('the cards enter from the side you swiped toward', dirAttr === 'next' && await p.evaluate(() => [...document.querySelectorAll('.view.active > *')].some(e => e.getAnimations().some(a => a.animationName === 'viewSlideNext'))), dirAttr);
  await settle();
  ok('after it settles nothing is left animating, and the page was never given a transform', await p.evaluate(() => [...document.querySelectorAll('.view.active > *')].every(e => e.getAnimations().length === 0)) && !(await moved()).t);
  // short slow drag: nothing happens
  const before = await view(); await swipe(260, 235, 400, 400, 6); await touch('touchend', 235, 402); await settle();
  ok('a short slow drag does nothing and the tag goes away', (await view()) === before && !(await ind()).show, await view());
  // flick
  await swipe(300, 250, 400, 40, 5); await touch('touchend', 250, 402); await settle();
  ok('a quick flick of 50 px switches tabs', (await view()) !== before, await view());
  // back
  const at = await view(); await swipe(100, 320, 400, 120, 8); await p.waitForTimeout(40); const ip = await ind(); await touch('touchend', 320, 402); await settle();
  ok('swiping right goes to the previous tab (tag on the left)', (await view()) !== at && ip.side === 'prev' && /‹/.test(ip.text), [at, await view(), ip]);
  ok('and its cards come in from the left', await p.evaluate(() => document.documentElement.dataset.swipeDir === 'prev'));
  // ends
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await touch('touchstart', 100, 400); await touch('touchmove', 140, 401); await touch('touchmove', 300, 402); await p.waitForTimeout(60);
  const edge = await ind(); await touch('touchend', 300, 402); await settle();
  ok('before the first tab there is no tag and nothing happens', (!edge || !edge.show) && (await view()) === 'dashboard', edge);
  // scrolling
  await touch('touchstart', 200, 500); await touch('touchmove', 202, 470); await touch('touchmove', 205, 420); await touch('touchmove', 207, 380); await p.waitForTimeout(50);
  ok('a vertical scroll never shows the tag', !(await ind()).show); await touch('touchend', 207, 380);
  // disabled
  await p.evaluate(() => { lmaLoad().swipe = false; lmaSave() }); await swipe(300, 100, 400, 100, 6); await touch('touchend', 100, 402); await settle();
  ok('with swiping switched off nothing happens', (await view()) === 'dashboard' && !(await ind()).show);
  await p.evaluate(() => { lmaLoad().swipe = true; lmaSave() });
  await swipe(10, 200, 400, 100, 6); await touch('touchend', 200, 402); await settle(); ok('a swipe that starts at the screen edge is ignored', (await view()) === 'dashboard');
  // reduced motion
  await p.evaluate(() => document.documentElement.classList.add('reduce-motion')); await swipe(330, 120, 400, 100, 6); const rmInd = await ind(); await touch('touchend', 120, 402); await settle();
  ok('with reduced motion it still switches, with no tag and no entry animation', (await view()) === 'character' && (!rmInd || !rmInd.show) && !(await p.evaluate(() => document.documentElement.dataset.swipeDir)), rmInd);
  await p.evaluate(() => document.documentElement.classList.remove('reduce-motion'));
  // after a swipe nothing replays; one tab lit
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await swipe(330, 130, 400, 120, 8); await touch('touchend', 130, 402); await settle(); await p.waitForTimeout(500);
  const post = await p.evaluate(() => ({ anims: [...document.querySelectorAll('.view.active > *')].filter(e => e.getAnimations().length).length, lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView() }));
  ok('after a swipe the cards do not replay their animation (no stutter)', post.anims === 0, post);
  ok('after a swipe exactly one tab is highlighted, the one you are on', post.lit.length === 1 && post.lit[0] === post.view, post);
  await p.evaluate(() => lmaNavTo('quests')); await p.waitForTimeout(100);
  ok('a normal tab change goes back to the usual fade-in', await p.evaluate(() => !document.documentElement.dataset.swipeDir));
  await p.waitForTimeout(600);
  for (let i = 0; i < 6; i++) { await swipe(320, 110, 400, 50, 4); await touch('touchend', 110, 402); await p.waitForTimeout(60); await p.evaluate(() => { const b = [...document.querySelectorAll('.navbtn[data-view]')]; b[Math.floor(Math.random() * b.length)].click() }); await p.waitForTimeout(40) }
  await settle(); const lit = await p.evaluate(() => ({ lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView() }));
  ok('after many quick swipes and taps only the current tab is lit', lit.lit.length === 1 && lit.lit[0] === (lit.view === 'media' || lit.view === 'history' ? 'library' : lit.view === 'meals' || lit.view === 'recipes' ? 'food' : lit.view), lit);
  const hov = await p.evaluate(() => { let bad = 0, seen = 0; for (const sh of document.styleSheets) { let rs; try { rs = sh.cssRules } catch (e) { continue } const walk = (rs, ok) => { for (const r of rs) { if (r.cssRules && r.media) walk(r.cssRules, ok || /hover:\s*hover/.test(r.media.mediaText)); else if (r.selectorText && /\.navbtn:hover/.test(r.selectorText)) { seen++; if (!ok) bad++ } } }; walk(rs, false) } return { bad, seen } });
  ok('the tab hover look only applies with a mouse', hov.seen >= 1 && hov.bad === 0, hov);
  // the bottom bar: in place on every frame of a swipe, nothing animating on the page itself
  await p.evaluate(() => { navigate('dashboard'); window.__s = []; const bar = document.querySelector('.sidebar'), c = document.querySelector('main.content'); const f = () => { const r = bar.getBoundingClientRect(), cs = getComputedStyle(bar); window.__s.push([r.top, cs.opacity, cs.visibility, cs.display, c.getAnimations().length, c.style.transform]); window.__raf = requestAnimationFrame(f) }; f() });
  await p.waitForTimeout(400); await swipe(330, 130, 400, 100, 8); await touch('touchend', 130, 402); await p.waitForTimeout(900);
  const bar = await p.evaluate(() => { cancelAnimationFrame(window.__raf); const s = window.__s, bad = s.filter(x => x[0] < 700 || x[1] !== '1' || x[2] !== 'visible' || x[3] === 'none'); return { frames: s.length, bad: bad.length, pageAnims: s.filter(x => x[4] > 0 || x[5]).length, blur: getComputedStyle(document.querySelector('.sidebar')).backdropFilter } });
  ok('the bottom bar stays in place and visible on every frame of a swipe', bar.frames > 20 && bar.bad === 0, bar);
  ok('and the page container is never animated or transformed during one', bar.pageAnims === 0, bar);
  // the bar's own scrolling
  await p.setViewportSize({ width: 360, height: 780 }); await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await p.evaluate(() => { window.__s = []; const bar = document.querySelector('.sidebar'); const f = () => { const a = bar.querySelector('.navbtn.active'), r = a ? a.getBoundingClientRect() : null; window.__s.push([bar.scrollLeft, r && r.left, r && r.right]); window.__raf = requestAnimationFrame(f) }; f() });
  for (let k = 0; k < 8; k++) { await swipe(300, 120, 400, 70, 6); await touch('touchend', 120, 402); await p.waitForTimeout(420) }
  const bs = await p.evaluate(() => { cancelAnimationFrame(window.__raf); return window.__s });
  let rev = 0, dir = 0, prev = null, off = 0; for (const x of bs) { if (prev !== null && x[0] !== prev) { const d = Math.sign(x[0] - prev); if (dir && d !== dir) rev++; dir = d } prev = x[0]; if (x[1] != null && (x[1] < -1 || x[2] > 361)) off++ }
  ok('swiping through the tabs: the bar only ever scrolls one way at a time', bs.length > 100 && rev === 0, { rev, frames: bs.length });
  ok('and the lit tab is never off the screen', off === 0, off);
  // the switch
  await p.evaluate(() => { lmaSetSwipeAnim(false); navigate('dashboard') }); await p.waitForTimeout(400);
  await touch('touchstart', 300, 400); await touch('touchmove', 280, 401); await touch('touchmove', 200, 402); await p.waitForTimeout(40);
  ok('with "Swipe animation" off there is no tag', !(await ind()).show);
  await touch('touchmove', 100, 402); await touch('touchend', 100, 402); await p.waitForTimeout(400);
  ok('and the swipe still switches the tab, instantly and without the card slide', (await view()) !== 'dashboard' && !(await p.evaluate(() => document.documentElement.dataset.swipeDir)), await view());
  ok('the Settings page has the switch', await p.evaluate(() => { navigate('settings'); return !!document.getElementById('lmaSwipeAnimToggle') }));
  await p.evaluate(() => lmaSetSwipeAnim(true));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
