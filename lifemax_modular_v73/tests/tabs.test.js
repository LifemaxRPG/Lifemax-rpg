/* Bottom bar editor tests. Run: NODE_PATH=$(npm root -g) node tests/tabs.test.js [built html] */
const { chromium } = require('playwright'); const currentPage = require('./current-page'), file = currentPage(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const site = await currentPage.serve(file); // it reloads the page
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 412, height: 860 } }); const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));localStorage.setItem('lma_qdeal','off');");
  await p.goto(site.url); await p.waitForTimeout(800);
  const order = () => p.evaluate(() => [...document.querySelectorAll('.sidebar .navbtn[data-view]')].map(b => b.dataset.view));
  const shown = () => p.evaluate(() => [...document.querySelectorAll('.sidebar .navbtn[data-view]')].filter(b => b.offsetWidth > 0 || b.getClientRects().length).map(b => b.dataset.view));
  const o0 = await order(); ok('the default bar has the expected tabs', o0.slice(0, 6).join() === 'dashboard,character,food,pet,log,quests', o0);
  ok('the editor lists every tab', await p.evaluate(() => document.querySelectorAll('#tabsEditBody .tabrow').length >= 9));
  await p.evaluate(() => lmaTabMove('quests', -1)); const o1 = await order();
  ok('moving Quests earlier swaps it with the tab before it', o1.indexOf('quests') === o0.indexOf('quests') - 1 && o1.indexOf('log') === o0.indexOf('log') + 1, o1);
  ok('the first tab cannot move earlier, the last cannot move later', await p.evaluate(() => { const r = [...document.querySelectorAll('#tabsEditBody .tabrow')]; const rr = r.filter(e => e.querySelectorAll('.tb').length); return rr[0].querySelectorAll('.tb')[0].disabled && rr[rr.length - 1].querySelectorAll('.tb')[1].disabled }));
  await p.evaluate(() => lmaTabShow('money', false));
  ok('hiding Money removes it from the bar but keeps it in the list', !(await shown()).includes('money') && (await order()).includes('money') && await p.evaluate(() => [...document.querySelectorAll('#tabsEditBody .tabrow.off')].some(r => /Money/.test(r.textContent))));
  ok('swiping and scrubbing use the new order and skip hidden tabs', await p.evaluate(() => { const l = lmaTabList().map(b => b.dataset.view); return !l.includes('money') && l.indexOf('quests') < l.indexOf('log') }));
  await p.reload(); await p.waitForTimeout(800);
  const o2 = await order(); ok('the order survives a restart', o2.indexOf('quests') === o0.indexOf('quests') - 1, o2);
  ok('and so does the hidden tab', !(await shown()).includes('money'));
  ok('Swipe animation setting also survives a restart now', await p.evaluate(() => { lmaSetSwipeAnim(false); return true }) && (await (async () => { await p.reload(); await p.waitForTimeout(700); return p.evaluate(() => lmaLoad().swipeAnim === false) })()));
  // at least two tabs
  await p.evaluate(() => { lmaTabsReset(); const v = [...document.querySelectorAll('.sidebar .navbtn[data-view]')].map(b => b.dataset.view); v.slice(2).forEach(x => lmaTabShow(x, false)) });
  ok('the bar always keeps at least two tabs', (await shown()).length === 2, await shown());
  ok('and says so', await p.evaluate(() => document.querySelectorAll('#tabsEditBody .tabrow:not(.off)').length === 2));
  // extras
  await p.evaluate(() => { lmaTabsReset(); lmaTabShow('settings', true) });
  const o3 = await order(); ok('Settings can be added to the bar, at the end', o3[o3.length - 1] === 'settings' && (await shown()).includes('settings'), o3);
  await p.evaluate(() => document.querySelector('.navbtn[data-view="settings"]').click()); await p.waitForTimeout(700);
  ok('the added tab opens Settings', await p.evaluate(() => lmaView() === 'settings'));
  await p.evaluate(() => lmaTabShow('settings', false)); ok('and can be removed again', !(await order()).includes('settings'));
  // reset
  await p.evaluate(() => { lmaTabMove('pet', 1); lmaTabShow('plan', false); lmaTabsReset() }); const o4 = await order();
  ok('Reset restores the original order and shows every tab', o4.join() === o0.join() && (await shown()).length === o0.length, o4);
  ok('Bottom bar editor sits in Settings, Look & feel', await p.evaluate(() => { setPickCat('look'); const c = document.getElementById('tabsCard'); return !!c && !c.classList.contains('set-hide') }));
  ok('no page errors', errs.length === 0, errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); site.close(); process.exit(fail ? 1 : 0);
})();
