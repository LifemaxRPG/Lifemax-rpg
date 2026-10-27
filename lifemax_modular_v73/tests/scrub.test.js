/* Scrubbing along the bottom bar. Run:  NODE_PATH=$(npm root -g) node tests/scrub.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 393, height: 844 }, hasTouch: true })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.goto('file://' + file); await p.waitForTimeout(1000);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } navigate('dashboard') }); await p.waitForTimeout(500);
  const touch = (type, x, y) => p.evaluate(([type, x, y]) => { const tgt = document.elementFromPoint(x, y) || document.body; const t = new Touch({ identifier: 1, target: tgt, clientX: x, clientY: y }); tgt.dispatchEvent(new TouchEvent(type, { bubbles: true, cancelable: true, touches: type === 'touchend' ? [] : [t], changedTouches: [t] })) }, [type, x, y]);
  const Y = 800;
  const scrub = async (gain, dx) => { await p.evaluate(g => { lmaSetScrubSpeed(g); navigate('dashboard'); lmaScrub.sel = 0 }, gain); await p.waitForTimeout(400); const x0 = 60; await touch('touchstart', x0, Y); for (let i = 1; i <= 8; i++) { await touch('touchmove', x0 + dx * i / 8, Y); await p.waitForTimeout(8) } const sel = await p.evaluate(() => lmaScrub.sel); await touch('touchend', x0 + dx, Y); await p.waitForTimeout(600); return sel };
  const nrm = await scrub('normal', 100), fst = await scrub('fast', 100), fas = await scrub('faster', 100);
  ok('the same 100 px slide gets through more tabs at each speed', nrm < fst && fst < fas, [nrm, fst, fas]);
  ok('"Normal" is the old pace (about two tabs for 100 px)', nrm >= 1 && nrm <= 3, nrm);
  ok('"Fast" is clearly quicker than before', fst >= nrm + 1, [nrm, fst]);
  ok('the default is Fast', await p.evaluate(() => { delete lmaLoad().scrubSpeed; return lmaScrubGain() === LMA_SCRUB_GAINS.fast }));
  const n = await p.evaluate(() => lmaScrub.items.length);
  const far = await scrub('faster', 220); ok('with Faster, the last tab is reached well before the screen edge', far === n - 1, [far, n]);
  ok('and letting go there opens that tab', await p.evaluate(() => lmaTabKey(lmaView())) === await p.evaluate(() => lmaScrub.items[lmaScrub.items.length - 1].dataset.view));
  // a small slide starts at once
  await p.evaluate(() => { lmaSetScrubSpeed('fast'); navigate('dashboard') }); await p.waitForTimeout(400); await touch('touchstart', 200, Y); await touch('touchmove', 207, Y); const started = await p.evaluate(() => !!(lmaScrub.drag && lmaScrub.drag.moved)); await touch('touchend', 207, Y);
  ok('a slide of 7 px already starts scrubbing', started);
  await p.waitForTimeout(500);
  // going back left works too
  await p.evaluate(() => { lmaSetScrubSpeed('fast'); lmaNavTo('quests', { fast: true }) }); await p.waitForTimeout(500);
  const startIdx = await p.evaluate(() => lmaScrubIndex('quests')); await touch('touchstart', 250, Y); for (let i = 1; i <= 8; i++) { await touch('touchmove', 250 - 15 * i, Y); await p.waitForTimeout(8) } const back = await p.evaluate(() => lmaScrub.sel); await touch('touchend', 130, Y); await p.waitForTimeout(500);
  ok('sliding left goes to earlier tabs', back < startIdx, [startIdx, back]);
  ok('the Settings page has the speed choice', await p.evaluate(() => { navigate('settings'); const s = document.getElementById('lmaScrubSpeed'); return !!s && [...s.options].map(o => o.value).join() === 'normal,fast,faster' }));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
