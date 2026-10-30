/* Life Director sheets (add a goal, goal form) sit above the bottom bar and the page footer.
   Run: NODE_PATH=$(npm root -g) node tests/ldsheet.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file); await p.waitForTimeout(1500);
  await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('life-director'); LifeMaxFeatures.open('life-director') }); await p.waitForSelector('#view-life-director [data-tab=goals]', { timeout: 15000 });
  await p.evaluate(() => document.querySelector('#view-life-director [data-tab=goals]').click()); await p.waitForTimeout(500);
  await p.evaluate(() => document.querySelector('#view-life-director [data-act=addGoal]').click()); await p.waitForTimeout(700);
  const at = () => p.evaluate(() => { const nav = document.querySelector('.sidebar').getBoundingClientRect(), e = document.elementFromPoint(innerWidth / 2, nav.top + nav.height / 2), f = document.querySelector('.footer').getBoundingClientRect(), e2 = document.elementFromPoint(f.left + 10, Math.min(innerHeight - 1, f.top + f.height / 2)); return { nav: e && (e.closest('.ld-sheet') ? 'sheet' : 'other'), foot: e2 && (e2.closest('.ld-sheet') ? 'sheet' : 'other'), open: !!document.querySelector('.ld-sheet.open') } });
  let r = await at(); ok('the sheet is open and covers the bottom bar', r.open && r.nav === 'sheet', r);
  ok('the page footer does not show through the sheet', r.foot === 'sheet', r);
  ok('the last goal option ("Something else") is reachable above the bottom edge', await p.evaluate(() => { const btn = [...document.querySelectorAll('.ld-sheet-panel button')].find(b => /Something else/.test(b.textContent)); if (!btn) return false; btn.scrollIntoView({ block: 'end' }); const r = btn.getBoundingClientRect(), e = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2); return !!e && !!e.closest('.ld-sheet-panel') && r.bottom <= innerHeight }));
  await p.evaluate(() => document.querySelector('.ld-sheet .ld-x').click()); await p.waitForTimeout(500);
  r = await at(); ok('closing the sheet gives the bar back', !r.open && r.nav === 'other', r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
