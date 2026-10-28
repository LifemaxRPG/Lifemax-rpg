/* Deep links from Assistant / Gemini / shortcuts: parsing, feeding both cats, scheduling hand-off, queueing before ready.
   Run: NODE_PATH=$(npm root -g) node tests/deeplink.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  const P = u => p.evaluate(u => LifeMaxDeepLink.parse(u), u);
  ok('schedule link parses title and description', JSON.stringify(await P('lifemax://schedule/add?title=Dentist%20at%203&description=cleaning')) === JSON.stringify({ kind: 'schedule', title: 'Dentist at 3', description: 'cleaning' }));
  ok('plus signs decode as spaces', (await P('lifemax://schedule/add?title=Go+for+a+run')).title === 'Go for a run');
  ok('feed link', (await P('lifemax://cats/feed')).kind === 'feed');
  const feats = await p.evaluate(() => ['feed the cats', 'Feed my cat', 'schedule', 'life director', 'quests', 'my cats', 'banana'].map(f => LifeMaxDeepLink.parse('lifemax://open?feature=' + encodeURIComponent(f)).kind));
  ok('open?feature maps spoken names', JSON.stringify(feats) === JSON.stringify(['feed', 'feed', 'schedule', 'schedule', 'quests', 'cats', 'none']), feats);
  ok('foreign links are ignored', await p.evaluate(() => LifeMaxDeepLink.parse('https://example.com') === null && LifeMaxDeepLink.handle('https://example.com') === false));
  /* feeding with no cat */
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?x=1') });
  ok('feeding with no cat does not throw', errs.length === 0, errs);
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Mo', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = Date.now() - 6 * 3600e3; save(); navigate('pet') }); await p.waitForTimeout(2500);
  await p.evaluate(() => { petDevPair('adopt') }); await p.waitForFunction(() => petPairOn(), null, { timeout: 15000 }); await p.waitForTimeout(500);
  await p.evaluate(() => { state.pet.lastFed = Date.now() - 6 * 3600e3; state.pet2.lastFed = Date.now() - 6 * 3600e3; state.pet.fedToday = 0; state.pet2.fedToday = 0 });
  await p.evaluate(() => { window.__t = []; const o = window.toast; window.toast = m => { __t.push(m); try { o(m) } catch (e) {} } });
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?a=1') });
  const fed = await p.evaluate(() => ({ a: Date.now() - state.pet.lastFed < 5000, b: Date.now() - state.pet2.lastFed < 5000, t: __t.join('|') }));
  ok('feed link feeds both cats', fed.a && fed.b, fed);
  ok('the toast names the cats', /Fed .* and /.test(fed.t), fed);
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://cats/feed?a=2') }); await p.waitForTimeout(3200);
  await p.evaluate(() => { __t = []; LifeMaxDeepLink.handle('lifemax://cats/feed?a=3') });
  ok('feeding again says the cats are not hungry', await p.evaluate(() => /not hungry|full/.test(__t.join('|'))), await p.evaluate(() => __t));
  /* schedule hand-off goes to the voice engine */
  await p.evaluate(() => { window.__u = []; const o = lmvHandle; lmvHandle = (t, x) => { __u.push(t); return o(t, x) } });
  await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://schedule/add?title=Call%20mom') }); await p.waitForTimeout(400);
  ok('schedule link hands the title to the voice engine', await p.evaluate(() => __u.length === 1 && /Call mom/.test(__u[0])), await p.evaluate(() => __u));
  ok('the voice panel is open', await p.evaluate(() => !!(lmvUI && lmvUI.open && lmvUI.hud.classList.contains('on'))));
  ok('the same link twice in a row is handled once', await p.evaluate(() => { __u = []; LifeMaxDeepLink.handle('lifemax://schedule/add?title=Twice'); LifeMaxDeepLink.handle('lifemax://schedule/add?title=Twice'); return __u.length === 1 }));
  /* queued before ready */
  const q = await p.evaluate(() => { LM_DL.ready = false; __u = []; LifeMaxDeepLink.handle('lifemax://schedule/add?title=Queued'); const before = __u.length; lmDlFlush(); return { before, after: __u.length } });
  ok('links before ready wait, then run', q.before === 0 && q.after === 1, q);
  ok('open?feature=quests navigates', await p.evaluate(() => { LifeMaxDeepLink.handle('lifemax://open?feature=quests'); return true }) && errs.length === 0, errs);
  ok('no script errors', errs.length === 0, errs);
  console.log(`\n${pass} passed, ${fail} failed`); await b.close(); process.exit(fail ? 1 : 0);
})();
