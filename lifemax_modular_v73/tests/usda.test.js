/* USDA branded-foods database: import from Settings, search in the add-food sheet, remove.
   Run: NODE_PATH=$(npm root -g) node tests/usda.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(1200);
  await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('settings'); setPickCat('data') }); await p.waitForTimeout(500);
  ok('the Food database card is in Settings (Data & advanced)', await p.evaluate(() => SET_CATS.find(c => c.id === 'data').cards.indexOf('Food database') >= 0 && !!document.getElementById('usdaSettingsBody') && /Branded foods/.test(document.getElementById('usdaSettingsBody').textContent)));
  await p.waitForFunction(() => /Not imported/.test(document.getElementById('usdaImportStatus').textContent), null, { timeout: 5000 });
  ok('the card is on screen in the Data & advanced group', await p.evaluate(() => document.getElementById('usdaCard').offsetParent !== null));
  ok('it starts empty and Remove is disabled', await p.evaluate(() => document.getElementById('usdaRemoveBtn').disabled));
  await p.setInputFiles('#usdaImportFile', path.join(__dirname, 'fixtures', 'usda-mini.jsonl.gz'));
  await p.waitForFunction(() => /ready · 2 foods/.test(document.getElementById('usdaImportStatus').textContent), null, { timeout: 15000 });
  ok('the import stores both foods and says so', true);
  /* found by the new add-food sheet */
  await p.evaluate(() => { navigate('meals') }); await p.waitForTimeout(600);
  const found = await p.evaluate(async () => { const r = await (async () => { const db = await usdaDb(); const x = db.transaction('foods').objectStore('foods').index('terms').getAll(IDBKeyRange.bound('zorbax', 'zorbax￿')); const out = await new Promise(res => x.onsuccess = () => res(x.result)); db.close(); return out })(); return r.map(f => f.n + '|' + f.brand) });
  ok('a branded food is searchable by brand', found.join() === 'Zorbax Protein Bar|Zorbax', found);
  await p.evaluate(() => { fv2Open && fv2Open() }); await p.waitForTimeout(500);
  const hits = await p.evaluate(async () => { const sh = fv2S().sh; sh.q = 'zorbax'; await fv2Search(); await new Promise(r => setTimeout(r, 400)); return (sh.list || []).map(f => f.n) });
  ok('the add-food sheet shows it', hits.indexOf('Zorbax Protein Bar') >= 0, hits);
  /* remove (two taps) */
  await p.evaluate(() => { fv2Close && fv2Close(); navigate('settings'); setPickCat('data'); fv2UsdaRender() }); await p.waitForFunction(() => !document.getElementById('usdaRemoveBtn').disabled, null, { timeout: 5000 });
  await p.click('#usdaRemoveBtn'); ok('the first tap only asks', await p.evaluate(() => /Tap again/.test(document.getElementById('usdaRemoveBtn').textContent)));
  await p.click('#usdaRemoveBtn'); await p.waitForFunction(() => /Not imported/.test(document.getElementById('usdaImportStatus').textContent), null, { timeout: 8000 });
  ok('the second tap removes the database', await p.evaluate(async () => (await usdaCount()) === 0));
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
