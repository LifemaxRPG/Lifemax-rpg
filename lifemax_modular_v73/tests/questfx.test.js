/* Quest effects tests (WebGL2 particles, foil, tilt, switch). Run: NODE_PATH=$(npm root -g) node tests/questfx.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const mk = async (init) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); p.on('console', m => { if (/\[qfx|\[deal|\[hand/.test(m.text())) p.errs.push(m.text()) }); await p.addInitScript("localStorage.setItem('lma_qdeal','quick');" + "localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));" + (init || '')); await p.goto('file://' + file); await p.waitForTimeout(700); return p };
  const p = await mk();
  const ev = (f, a) => p.evaluate(f, a);
  ok('WebGL2 is available here', await ev(() => QFX.supported()));
  ok('effects are on by default', await ev(() => QFX.enabled() && QFX.on()));
  await ev(() => { state.dailyDraw.dealt = true; document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('quests') }); await p.waitForTimeout(600);
  const lit0 = await ev(() => QFX.probe()); ok('an idle overlay draws nothing', lit0 === 0, lit0);
  await ev(() => QFX.burst({ left: 100, top: 300, width: 200, height: 300, bottom: 600, right: 300 }, 3));
  await p.waitForTimeout(120);
  const lit1 = await ev(() => QFX.probe()); ok('a burst lights real pixels on the GPU canvas', lit1 > 500, lit1);
  await p.waitForTimeout(2600);
  const lit2 = await ev(() => QFX.probe()); ok('the particles die out on their own', lit2 === 0, lit2);
  ok('the overlay never blocks touches', await ev(() => getComputedStyle(document.getElementById('qfx')).pointerEvents === 'none'));
  // foil: turn over a 3-star card and check a foil canvas appears and has pixels
  await ev(() => { const d = state.dailyDraw; const t = LifeMaxDailyHand.library.find(x => x.tier === 3 && x.verify === 'self'); d.cards[2] = { tpl: t.id, id: 'dh_' + d.date + '_2_5', n: 5 }; state.quests = state.quests.filter(x => !x.hand); LifeMaxDailyHand.ensure(); renderQuests(); LifeMaxDailyHand.reveal(2) });
  await p.waitForTimeout(1500);
  ok('a 3-star card gets a foil canvas', await ev(() => !!document.querySelector('#dailyHand .dh-card.fd-t3 canvas.fd-foil')));
  ok('the foil canvas has coloured pixels', await ev(() => { const c = document.querySelector('#dailyHand canvas.fd-foil'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data; let n = 0; for (let i = 3; i < d.length; i += 4) if (d[i] > 20) n++; return n > 2000 }));
  const t1 = await ev(() => document.querySelector('#dailyHand .dh-card.is-up.fd-t3').style.getPropertyValue('--ry'));
  await p.mouse.move(60, 400); await p.waitForTimeout(150); await p.mouse.move(380, 430); await p.waitForTimeout(250);
  const t2 = await ev(() => document.querySelector('#dailyHand .dh-card.is-up.fd-t3').style.getPropertyValue('--ry'));
  ok('the card leans toward the pointer', t1 !== t2 || t2 !== '', { t1, t2 });
  ok('tier 1 cards get no foil', await ev(() => !document.querySelector('#dailyHand .dh-card.fd-t1 canvas.fd-foil')));
  // switch off
  await ev(() => qfxSet(false)); await p.waitForTimeout(300);
  ok('switching effects off hides the overlay and foil', await ev(() => !QFX.on() && (!document.getElementById('qfx') || document.getElementById('qfx').style.display === 'none') && !document.querySelector('#dailyHand canvas.fd-foil')));
  ok('the setting is remembered', await ev(() => localStorage.getItem('lma_qfx') === 'off'));
  ok('the Settings switch exists in Look & feel', await ev(() => { setPickCat('look'); const c = document.getElementById('qfxCard'); return !!c && !c.classList.contains('set-hide') }));
  ok('no page errors', p.errs.length === 0, p.errs);
  // reduced motion: effects off
  const q = await mk(); await q.emulateMedia({ reducedMotion: 'reduce' }); await q.reload(); await q.waitForTimeout(700);
  ok('reduced motion disables effects', await q.evaluate(() => !QFX.on()));
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
