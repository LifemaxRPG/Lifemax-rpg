/* Swipe between tabs. The cards on the page follow the finger and the next page's cards slide in; the page container itself is never moved
   (that made the bottom bar blink on some phones). Run:  NODE_PATH=$(npm root -g) node tests/swipe.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, hasTouch: true })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lma_qdeal','off')");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } navigate('dashboard'); window.scrollTo(0, 0) });
  await p.waitForTimeout(600);
  // at: the time the touch happens. The page reads a touch's time from Date.now while it handles it, so for that moment Date.now gives at
  const touch = (type, x, y, at) => p.evaluate(([type, x, y, at]) => { const now = Date.now; if (at) Date.now = () => at; try { const tgt = document.elementFromPoint(Math.min(Math.max(x, 1), innerWidth - 1), y) || document.body; const t = new Touch({ identifier: 1, target: tgt, clientX: x, clientY: y }); tgt.dispatchEvent(new TouchEvent(type, { bubbles: true, cancelable: true, touches: type === 'touchend' ? [] : [t], changedTouches: [t], targetTouches: type === 'touchend' ? [] : [t] })) } finally { Date.now = now } }, [type, x, y, at]);
  const view = () => p.evaluate(() => lmaView());
  // read at the next frame: the page moves the cards once a frame, and on a busy machine a fixed wait did not always see a frame
  const drag = () => p.evaluate(() => new Promise(r => requestAnimationFrame(() => { const v = document.querySelector('.view.active'), k = v && v.firstElementChild; const m = k && new DOMMatrix(getComputedStyle(k).transform === 'none' ? undefined : getComputedStyle(k).transform); r({ on: document.documentElement.classList.contains('lma-drag'), swx: v ? parseFloat(v.style.getPropertyValue('--swx') || '0') : 0, cardX: m ? m.m41 : 0, sw: document.documentElement.scrollWidth, iw: innerWidth }) })));
  const moved = () => p.evaluate(() => { const c = document.querySelector('main.content'); return { t: c.style.transform, o: c.style.opacity, w: c.style.willChange } });
  // the touches of a swipe happen ms apart over its steps, whatever a round trip to the page costs: the page reads a flick's speed from
  // their times, and on a busy machine the round trips made a 40 ms flick last over 100 ms, which the page rightly took for a slow drag
  const swipe = async (x0, x1, y, ms, steps) => { const t0 = await p.evaluate(() => Date.now()); await touch('touchstart', x0, y, t0); for (let i = 1; i <= steps; i++) { await touch('touchmove', x0 + (x1 - x0) * i / steps, y + (i % 2), t0 + ms * i / steps); if (ms) await p.waitForTimeout(ms / steps) } };
  const settle = () => p.waitForTimeout(700);
  // during a pull: the cards follow the finger, the container does not move
  await touch('touchstart', 300, 400); await touch('touchmove', 280, 401); await touch('touchmove', 240, 402); await p.waitForTimeout(80);
  let d1 = await drag(), mv = await moved();
  ok('while pulling, the cards follow the finger (about 60 px left)', d1.on && d1.swx < -50 && d1.swx > -70 && d1.cardX < -50 && d1.cardX > -70, d1);
  ok('the page container itself is not moved, faded or promoted', !mv.t && !mv.o && !mv.w, mv);
  ok('and the page does not become wider than the screen', d1.sw <= d1.iw, d1);
  await touch('touchmove', 150, 402); await p.waitForTimeout(60); d1 = await drag();
  ok('they keep following', d1.swx < -140 && d1.swx > -160, d1);
  ok('the page has not switched yet', (await view()) === 'dashboard');
  // timed, and the new page looked at, in the page the moment it arrives: round trips to the page, or a stalled frame, on a busy
  // machine added to the time and could outlast the cards' 0.26 s entry
  await p.evaluate(() => { addEventListener('touchend', () => { window.__end = performance.now() }, { capture: true, once: true }); const mo = new MutationObserver(() => { if (lmaView() === 'dashboard') return; mo.disconnect(); window.__arr = { view: lmaView(), lat: Math.round(performance.now() - window.__end), dir: document.documentElement.dataset.swipeDir, slide: [...document.querySelectorAll('.view.active > *')].some(e => e.getAnimations().some(a => a.animationName === 'viewSlideNext')) } }); mo.observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'] }) });
  await touch('touchend', 150, 402); const arr = await (await p.waitForFunction(() => window.__arr, null, { timeout: 2000 })).jsonValue();
  ok('past a fifth of the screen it carries on and switches quickly', arr.view === 'character' && arr.lat < 400, arr);
  ok('the drag state is cleared as the new page arrives', await p.evaluate(() => !document.documentElement.classList.contains('lma-drag') && !document.querySelector('.view.active').style.getPropertyValue('--swx')));
  ok('the new cards enter from the side you swiped toward', arr.dir === 'next' && arr.slide, arr);
  await settle();
  ok('after it settles nothing is animating and the page container was never given a transform', await p.evaluate(() => [...document.querySelectorAll('.view.active > *')].every(e => e.getAnimations().length === 0)) && !(await moved()).t);
  // short slow drag: nothing happens
  const before = await view(); await swipe(260, 235, 400, 400, 6); await touch('touchend', 235, 402); await settle();
  ok('a short slow drag springs back and clears itself', (await view()) === before && !(await drag()).on && (await drag()).swx === 0, await view());
  // flick
  await swipe(300, 250, 400, 40, 5); await touch('touchend', 250, 402); await settle();
  ok('a quick flick of 50 px switches tabs', (await view()) !== before, await view());
  // back
  const at = await view(); await swipe(100, 320, 400, 120, 8); await p.waitForTimeout(40); const ip = await drag(); await touch('touchend', 320, 402); await settle();
  ok('swiping right moves the cards right and goes to the previous tab', (await view()) !== at && ip.swx > 100, [at, await view(), ip]);
  ok('and its cards come in from the left', await p.evaluate(() => document.documentElement.dataset.swipeDir === 'prev'));
  // ends
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await touch('touchstart', 100, 400); await touch('touchmove', 140, 401); await touch('touchmove', 300, 402); await p.waitForTimeout(60);
  const edge = await drag(); await touch('touchend', 300, 402); await settle();
  ok('before the first tab the cards only stretch a little and come back', edge.swx > 0 && edge.swx < 70 && (await view()) === 'dashboard' && !(await drag()).on, edge);
  // scrolling
  await touch('touchstart', 200, 500); await touch('touchmove', 202, 470); await touch('touchmove', 205, 420); await touch('touchmove', 207, 380); await p.waitForTimeout(50);
  ok('a vertical scroll never moves the cards', !(await drag()).on && (await drag()).swx === 0); await touch('touchend', 207, 380);
  // disabled
  await p.evaluate(() => { lmaLoad().swipe = false; lmaSave() }); await swipe(300, 100, 400, 100, 6); await touch('touchend', 100, 402); await settle();
  ok('with swiping switched off nothing happens', (await view()) === 'dashboard' && !(await drag()).on);
  await p.evaluate(() => { lmaLoad().swipe = true; lmaSave() });
  await swipe(10, 200, 400, 100, 6); await touch('touchend', 200, 402); await settle(); ok('a swipe that starts at the screen edge is ignored', (await view()) === 'dashboard');
  // reduced motion
  await p.evaluate(() => document.documentElement.classList.add('reduce-motion')); await swipe(330, 120, 400, 100, 6); const rmD = await drag(); await touch('touchend', 120, 402); await settle();
  ok('with reduced motion it still switches, with no movement and no entry animation', (await view()) === 'character' && !rmD.on && !(await p.evaluate(() => document.documentElement.dataset.swipeDir)), rmD);
  await p.evaluate(() => document.documentElement.classList.remove('reduce-motion'));
  // after a swipe nothing replays; one tab lit
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(500);
  await swipe(330, 130, 400, 120, 8); await touch('touchend', 130, 402); await settle(); await p.waitForTimeout(500);
  const post = await p.evaluate(() => ({ anims: [...document.querySelectorAll('.view.active > *')].filter(e => e.getAnimations().length).length, lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView() }));
  ok('after a swipe the cards do not replay their animation (no stutter)', post.anims === 0, post);
  ok('after a swipe exactly one tab is highlighted, the one you are on', post.lit.length === 1 && post.lit[0] === post.view, post);
  // a normal tab change runs in a view transition, which changes the page a frame or more later: wait for it, not for a fixed 100 ms
  await p.evaluate(() => lmaNavTo('quests')); await p.waitForFunction(() => lmaView() === 'quests', null, { timeout: 2000 });
  ok('a normal tab change goes back to the usual fade-in', await p.evaluate(() => !document.documentElement.dataset.swipeDir));
  await p.waitForTimeout(600);
  for (let i = 0; i < 6; i++) { await swipe(320, 110, 400, 50, 4); await touch('touchend', 110, 402); await p.waitForTimeout(60); await p.evaluate(() => { const b = [...document.querySelectorAll('.navbtn[data-view]')]; b[Math.floor(Math.random() * b.length)].click() }); await p.waitForTimeout(40) }
  await settle(); const lit = await p.evaluate(() => ({ lit: [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view), view: lmaView() }));
  ok('after many quick swipes and taps only the current tab is lit', lit.lit.length === 1 && lit.lit[0] === (lit.view === 'media' || lit.view === 'history' ? 'library' : lit.view === 'meals' || lit.view === 'recipes' ? 'food' : lit.view), lit);
  const hov = await p.evaluate(() => { let bad = 0, seen = 0; for (const sh of document.styleSheets) { let rs; try { rs = sh.cssRules } catch (e) { continue } const walk = (rs, ok) => { for (const r of rs) { if (r.cssRules && r.media) walk(r.cssRules, ok || /hover:\s*hover/.test(r.media.mediaText)); else if (r.selectorText && /\.navbtn:hover/.test(r.selectorText)) { seen++; if (!ok) bad++ } } }; walk(rs, false) } return { bad, seen } });
  ok('the tab hover look only applies with a mouse', hov.seen >= 1 && hov.bad === 0, hov);
  // the bottom bar: in place on every frame of a swipe, nothing animating on the page itself
  await p.evaluate(() => { navigate('dashboard'); window.__s = []; const bar = document.querySelector('.sidebar'), c = document.querySelector('main.content'); const f = () => { const r = bar.getBoundingClientRect(), cs = getComputedStyle(bar); window.__s.push([r.top, cs.opacity, cs.visibility, cs.display, c.getAnimations().length, c.style.transform]); const v = document.querySelector('.view.active'); window.__maxSwx = Math.max(window.__maxSwx || 0, Math.abs(parseFloat((v && v.style.getPropertyValue('--swx')) || '0'))); window.__raf = requestAnimationFrame(f) }; f() });
  await p.waitForTimeout(400); await swipe(330, 130, 400, 100, 8); await touch('touchend', 130, 402); await p.waitForTimeout(900);
  const bar = await p.evaluate(() => { cancelAnimationFrame(window.__raf); const s = window.__s, bad = s.filter(x => x[0] < 700 || x[1] !== '1' || x[2] !== 'visible' || x[3] === 'none'); return { frames: s.length, bad: bad.length, pageAnims: s.filter(x => x[4] > 0 || x[5]).length, blur: getComputedStyle(document.querySelector('.sidebar')).backdropFilter } });
  ok('the bottom bar stays in place and visible on every frame of a swipe', bar.frames > 20 && bar.bad === 0, bar);
  ok('and the page container is never animated or transformed during one', bar.pageAnims === 0, bar);
  ok('the cards did follow the finger during that swipe', await p.evaluate(() => window.__maxSwx > 100), await p.evaluate(() => window.__maxSwx));
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
  ok('with "Swipe animation" off nothing follows the finger', !(await drag()).on);
  await touch('touchmove', 100, 402); await touch('touchend', 100, 402); await p.waitForTimeout(400);
  ok('and the swipe still switches the tab, instantly and without the card slide', (await view()) !== 'dashboard' && !(await p.evaluate(() => document.documentElement.dataset.swipeDir)), await view());
  ok('the Settings page has the switch', await p.evaluate(() => { navigate('settings'); return !!document.getElementById('lmaSwipeAnimToggle') }));
  await p.evaluate(() => lmaSetSwipeAnim(true));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
