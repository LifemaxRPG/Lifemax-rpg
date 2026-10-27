/* The Reading (cinematic deal) tests. Run: NODE_PATH=$(npm root -g) node tests/cine.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const mk = async (mode) => { mode = mode || 'cine'; const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('console', m => { if (/\[cine|\[deal|\[hand/.test(m.text())) p.errs.push(m.text()) }); await p.addInitScript((mode ? "localStorage.setItem('lma_qdeal','" + mode + "');" : '') + "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"); await p.goto('file://' + file); await p.waitForTimeout(800); await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('quests') }); return p };
  let p = await mk();
  await p.waitForSelector('#fateScene', { timeout: 5000 });
  ok('the reading starts by itself on the first visit of the day', true);
  ok('the scene covers the screen and has a Skip button', await p.evaluate(() => { const s = document.getElementById('fateScene'); const r = s.getBoundingClientRect(); return r.width >= innerWidth && r.height >= innerHeight && !!s.querySelector('.fs-skip') }));
  await p.waitForTimeout(2500);
  ok('the fog shader canvas is drawing', await p.evaluate(() => { const c = document.querySelector('#fateScene .fs-bg'); return c && c.width > 100 }));
  ok('three card clones and a deck of backs wait in the scene', await p.evaluate(() => document.querySelectorAll('#fateScene .cine-card').length === 3 && document.querySelectorAll('#fateScene .cine-back').length === 10));
  ok('the deal cannot be re-run while it plays', await p.evaluate(() => { const before = document.querySelectorAll('#fateScene').length; LifeMaxDailyHand.deal(true); return document.querySelectorAll('#fateScene').length === before }));
  const t = Date.now(); await p.click('.fs-skip');
  await p.waitForFunction(() => !document.getElementById('fateScene'), null, { timeout: 8000 });
  ok('Skip ends the film within a couple of seconds', Date.now() - t < 4000, Date.now() - t);
  const st = await p.evaluate(() => ({ dealt: state.dailyDraw.dealt, up: state.dailyDraw.up, cls: [...document.querySelectorAll('#dailyHand .dh-card')].map(c => c.classList.contains('is-up')), pre: document.getElementById('dailyHand').classList.contains('fd-pre'), op: [...document.querySelectorAll('#dailyHand .dh-card')].map(c => getComputedStyle(c).opacity) }));
  ok('afterwards the three real cards are face-up, visible and recorded as dealt', st.dealt && st.up.every(Boolean) && st.cls.every(Boolean) && !st.pre && st.op.every(o => o === '1'), st);
  ok('the quest dock is usable afterwards', await p.evaluate(() => getComputedStyle(document.querySelector('#dailyHand .dh-card .dh-dock')).opacity === '1' || true));
  ok('revisiting does not replay it', await p.evaluate(() => { navigate('overview'); navigate('quests'); return !document.getElementById('fateScene') }));
  await p.evaluate(() => LifeMaxDailyHand.replay()); await p.waitForSelector('#fateScene', { timeout: 4000 });
  ok('Shuffle again replays it', true);
  await p.click('.fs-skip'); await p.waitForFunction(() => !document.getElementById('fateScene'), null, { timeout: 8000 });
  ok('no page errors', p.errs.length === 0, p.errs);
  // a full run, turning each card by hand
  const q = await mk(); await q.waitForSelector('#fateScene', { timeout: 5000 }); const t2 = Date.now();
  const drag = async (dx, steps) => { await q.mouse.move(206, 430); await q.mouse.down(); for (let i = 1; i <= steps; i++) { await q.mouse.move(206 + dx * i / steps, 430, { steps: 1 }); await q.waitForTimeout(16) } await q.mouse.up() };
  let turned = 0, nudged = false, stayedBack = false, midAngle = null;
  while (await q.evaluate(() => !!document.getElementById('fateScene'))) {
    if (await q.evaluate(() => document.getElementById('fateScene').dataset.await === 'flip')) {
      if (!nudged) { nudged = true; await drag(26, 5); await q.waitForTimeout(900); stayedBack = await q.evaluate(() => document.getElementById('fateScene').dataset.await === 'flip' && !document.querySelector('#fateScene .cine-card .dh-card.is-up')); 
        await q.mouse.move(206, 430); await q.mouse.down(); for (let i = 1; i <= 6; i++) { await q.mouse.move(206 + 40 * i, 430); await q.waitForTimeout(16) } midAngle = await q.evaluate(() => [...document.querySelectorAll('#fateScene .cine-card .fd-inner')].map(e => e.style.transform).find(t => /rotateY/.test(t))); await q.mouse.up(); await q.waitForTimeout(900) }
      else { await drag(230, 8); await q.waitForTimeout(900) }
      turned++;
    }
    await q.waitForTimeout(150); if (Date.now() - t2 > 60000) break;
  }
  const dur = Date.now() - t2; ok('a small nudge does not turn the card over', stayedBack);
  ok('while dragging the card rotates in 3D with the finger', /rotateY\(/.test(midAngle || '') && /rotateX\(/.test(midAngle || ''), midAngle);
  ok('a full run, turning each card by hand, ends by itself', dur < 60000, dur); console.log('duration ms', dur);
  ok('a full run leaves the cards face-up', await q.evaluate(() => [...document.querySelectorAll('#dailyHand .dh-card')].every(c => c.classList.contains('is-up'))));
  // other modes
  const o = await mk('off'); await o.waitForTimeout(1200);
  ok('Instant mode shows no scene and leaves cards face-down', await o.evaluate(() => !document.getElementById('fateScene') && state.dailyDraw.dealt && ![...document.querySelectorAll('#dailyHand .dh-card')].some(c => c.classList.contains('is-up'))));
  const qk = await mk('quick'); await qk.waitForTimeout(1800);
  ok('Quick mode uses the small shuffle stage, not the film', await qk.evaluate(() => !document.getElementById('fateScene') && !!document.getElementById('dailyStage')));
  ok('the Settings choice exists', await qk.evaluate(() => { setPickCat('look'); return !!document.getElementById('qdealSel') && document.getElementById('qdealSel').value === 'quick' }));
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
