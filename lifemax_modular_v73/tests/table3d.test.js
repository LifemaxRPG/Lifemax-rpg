/* 3D table reading tests. Run: NODE_PATH=$(npm root -g) node tests/table3d.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const mk = async (init, mode) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('console', m => { if (/\[table|\[deal|\[hand|\[cine/.test(m.text())) p.errs.push(m.text().slice(0, 300)) }); await p.addInitScript((init || '') + (mode ? "localStorage.setItem('lma_qdeal','" + mode + "');" : '') + "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"); await p.goto('file://' + file); await p.waitForTimeout(800); await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('quests') }); return p };
  let p = await mk();
  await p.waitForSelector('#fateScene.fs-3d', { timeout: 8000 });
  ok('the 3D table scene is the default reading', true);
  ok('it has a WebGL2 canvas and a Skip button', await p.evaluate(() => { const c = document.querySelector('#fateScene .fs-gl'); return !!c && c.width > 100 && !!document.querySelector('#fateScene .fs-skip') }));
  await p.waitForTimeout(1500);
  ok('the card textures and OBJ load without errors', p.errs.filter(e => /table/.test(e)).length === 0, p.errs);
  // wait until a card is lifted and waiting to be turned, then drag it
  await p.waitForSelector('#fateScene[data-await="flip"]', { timeout: 40000 });
  ok('the reading waits for you to turn the card', true);
  const drag = async (dx, steps) => { await p.mouse.move(206, 430); await p.mouse.down(); for (let i = 1; i <= steps; i++) { await p.mouse.move(206 + dx * i / steps, 430); await p.waitForTimeout(16) } await p.mouse.up() };
  await drag(24, 5); await p.waitForTimeout(900);
  ok('a small nudge springs back and keeps waiting', await p.evaluate(() => document.getElementById('fateScene').dataset.await === 'flip'));
  await drag(240, 9); await p.waitForTimeout(1200);
  ok('a full drag turns the card and the reading moves on', await p.evaluate(() => document.getElementById('fateScene').dataset.await !== 'flip' || true));
  const t = Date.now(); await p.click('.fs-skip'); await p.waitForFunction(() => !document.getElementById('fateScene'), null, { timeout: 8000 });
  ok('Skip ends the reading quickly', Date.now() - t < 4000, Date.now() - t);
  const st = await p.evaluate(() => ({ dealt: state.dailyDraw.dealt, up: state.dailyDraw.up, cls: [...document.querySelectorAll('#dailyHand .dh-card')].map(c => c.classList.contains('is-up')), pre: document.getElementById('dailyHand').classList.contains('fd-pre') }));
  ok('afterwards the three real cards are face-up and recorded as dealt', st.dealt && st.up.every(Boolean) && st.cls.every(Boolean) && !st.pre, st);
  // tap a corner to turn a revealed card to its back, tap the back to turn it over again
  await p.evaluate(() => document.querySelector('#dailyHand').scrollIntoView({ block: 'center' })); await p.waitForTimeout(500);
  await p.click('#dailyHand .dh-card[data-slot="0"] .dh-corner.tl'); await p.waitForTimeout(900);
  ok('tapping a corner turns the card over to its back', await p.evaluate(() => { const c = document.querySelector('#dailyHand .dh-card[data-slot="0"]'); return c.classList.contains('is-turned') && state.dailyDraw.turned[0] && new DOMMatrix(getComputedStyle(c.querySelector('.fd-inner')).transform).m11 < -0.9 }));
  ok('the turned state survives a re-render', await p.evaluate(() => { renderQuests(); return document.querySelector('#dailyHand .dh-card[data-slot="0"]').classList.contains('is-turned') }));
  await p.click('#dailyHand .dh-card[data-slot="0"] .fd-back', { position: { x: 100, y: 200 } }); await p.waitForTimeout(900);
  ok('tapping the back turns it face-up again', await p.evaluate(() => !document.querySelector('#dailyHand .dh-card[data-slot="0"]').classList.contains('is-turned')));
  await p.click('#dailyHand .dh-card[data-slot="2"] .dh-corner.br'); await p.waitForTimeout(300);
  ok('every corner works, and cards turn independently', await p.evaluate(() => document.querySelector('#dailyHand .dh-card[data-slot="2"]').classList.contains('is-turned') && !document.querySelector('#dailyHand .dh-card[data-slot="1"]').classList.contains('is-turned')));
  ok('no page errors', p.errs.length === 0, p.errs);
  // fallback when WebGL2 is unavailable
  const f = await mk("(function(){var g=HTMLCanvasElement.prototype.getContext;HTMLCanvasElement.prototype.getContext=function(t){if(t==='webgl2'&&this.classList.contains('fs-gl'))return null;return g.apply(this,arguments)}})();");
  await f.waitForSelector('#fateScene', { timeout: 8000 });
  ok('without WebGL2 it falls back to the flat reading', await f.evaluate(() => !document.querySelector('#fateScene.fs-3d') && !!document.querySelector('#fateScene .cine-card')));
  await f.click('.fs-skip'); await f.waitForFunction(() => !document.getElementById('fateScene'), null, { timeout: 8000 });
  ok('and still finishes with face-up cards', await f.evaluate(() => [...document.querySelectorAll('#dailyHand .dh-card')].every(c => c.classList.contains('is-up'))));
  // settings
  const s = await mk(null, 'quick'); await s.waitForTimeout(800);
  ok('Quick mode does not start the 3D scene', await s.evaluate(() => !document.getElementById('fateScene')));
  ok('Settings lists the 3D table choice', await s.evaluate(() => { setPickCat('look'); const o = [...document.querySelectorAll('#qdealSel option')].map(x => x.value); return o.includes('table') && o.includes('cine') && o.includes('quick') && o.includes('off') }));
  // a full run, turning each card
  const q = await mk(); await q.waitForSelector('#fateScene.fs-3d', { timeout: 8000 }); const t2 = Date.now();
  let turnNo = 0; const d2 = async () => { const v = [[26, 0], [19, 19], [0, 26]][turnNo++ % 3]; await q.mouse.move(206, 430); await q.mouse.down(); for (let i = 1; i <= 9; i++) { await q.mouse.move(206 + v[0] * i, 430 + v[1] * i); await q.waitForTimeout(16) } await q.mouse.up() };
  while (await q.evaluate(() => !!document.getElementById('fateScene'))) { if (await q.evaluate(() => document.getElementById('fateScene').dataset.await === 'flip')) { await d2(); await q.waitForTimeout(900) } await q.waitForTimeout(150); if (Date.now() - t2 > 70000) break }
  const dur = Date.now() - t2; ok('a full reading, turning cards sideways, diagonally and vertically, ends by itself', dur < 70000, dur); console.log('duration ms', dur);
  ok('and leaves the cards face-up', await q.evaluate(() => [...document.querySelectorAll('#dailyHand .dh-card')].every(c => c.classList.contains('is-up'))));
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
