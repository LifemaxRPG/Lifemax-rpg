/* The Food database card's Download button: the Android FoodData plugin (faked here) stages the USDA file from GitHub, the page
   reads it back in 1 MiB pieces and imports it. Run: NODE_PATH=$(npm root -g) node tests/usda-download.test.js [built html] */
const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs'); const zlib = require('zlib');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
/* 5,000 foods, each with a long random note, so the gzip is several MiB and takes several pieces */
function bigGz() {
  const lines = []; for (let i = 0; i < 5000; i++) { const note = require('crypto').randomBytes(450).toString('base64'); lines.push(JSON.stringify({ id: 'g' + i, n: 'Test Food ' + i, brand: 'Brand' + (i % 50), cat: 'Snacks', kcal: 100 + (i % 300), p: 5, c: 10, f: 3, serving: 30, servingUnit: 'g', household: '1 piece', note })) }
  return zlib.gzipSync(Buffer.from(lines.join('\n') + '\n'));
}
(async () => {
  const mini = fs.readFileSync(path.join(__dirname, 'fixtures', 'usda-mini.jsonl.gz')), big = bigGz();
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(1200);
  await p.evaluate(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('settings'); setPickCat('data') }); await p.waitForTimeout(500);
  await p.evaluate(() => { const o = usdaStatus; window.__st = []; usdaStatus = m => { __st.push(m); o(m) }; window.__toasts = []; const t = toast; toast = (m, ...r) => { __toasts.push(m); return t(m, ...r) } });
  const status = () => p.evaluate(() => document.getElementById('usdaImportStatus').textContent);
  const count = () => p.evaluate(() => usdaCount());
  const wipe = () => p.evaluate(() => new Promise(res => { const r = indexedDB.deleteDatabase(USDA_DB_NAME); r.onsuccess = r.onerror = r.onblocked = () => res() }));
  /* the fake plugin: serves `bytes` (base64 from the test), obeys the 1 MiB limit like the real one, and records what the page asks */
  const install = (bytes, opts = {}) => p.evaluate(({ b64, opts }) => {
    const data = Uint8Array.from(atob(b64), c => c.charCodeAt(0)); const listeners = [];
    window.__fd = { calls: [], reads: [], discards: 0, downloads: 0, gate: null, removed: 0 };
    window.__release = null;
    lmaNative = () => true;
    lmaPlugin = n => n === 'FoodData' ? {
      addListener(ev, fn) { __fd.calls.push('listen:' + ev); listeners.push(fn); return Promise.resolve({ remove() { __fd.removed++ } }) },
      async download() {
        __fd.downloads++; __fd.calls.push('download');
        if (opts.gate) await new Promise(r => { window.__release = r });
        for (const pct of [0, 40, 100]) { listeners.forEach(f => f({ pct })); await new Promise(r => setTimeout(r, 30)) }
        if (opts.netFail) throw new Error('Could not reach GitHub. Check the connection and try again.');
        if (opts.badSum) throw new Error('The downloaded file did not match its checksum. Try again.');
        return { bytes: data.length, foods: 0, sha256: 'x' };
      },
      async read(o) {
        __fd.reads.push([o.offset, o.length]); __fd.calls.push('read');
        if (o.length > 1048576) throw new Error('too big');
        if (opts.failOnRead && __fd.reads.length === opts.failOnRead) throw new Error('Could not read the downloaded file.');
        const part = data.slice(o.offset, o.offset + o.length); let s = ''; for (let i = 0; i < part.length; i += 8192) s += String.fromCharCode.apply(null, part.subarray(i, i + 8192));
        return { data: btoa(s), done: o.offset + o.length >= data.length };
      },
      async discard() { __fd.discards++; __fd.calls.push('discard') },
    } : null;
    window.__st.length = 0; window.__toasts.length = 0; fv2UsdaRender();
  }, { b64: Buffer.from(bytes).toString('base64'), opts });
  const settle = (re, ms = 20000) => p.waitForFunction(r => new RegExp(r).test(document.getElementById('usdaImportStatus').textContent) && !document.getElementById('usdaDownloadBtn').disabled, re, { timeout: ms });

  /* 1. no plugin (a browser, an old APK): the button is not shown at all */
  await p.evaluate(() => { lmaNative = () => false; fv2UsdaRender() });
  ok('without the plugin there is no Download button and no hint', await p.evaluate(() => !document.getElementById('usdaDownloadBtn') && !/Download fetches/.test(document.getElementById('usdaSettingsBody').textContent) && ![...document.querySelectorAll('#usdaSettingsBody button')].some(x => /Download/.test(x.textContent))));
  ok('Import file and Remove are still there', await p.evaluate(() => [...document.querySelectorAll('#usdaSettingsBody button')].map(x => x.textContent).join('|') === 'Import file|Remove'));
  await p.evaluate(() => { lmaNative = () => true; lmaPlugin = () => null; fv2UsdaRender() });
  ok('a shell that answers null for the plugin shows no button either', await p.evaluate(() => !document.getElementById('usdaDownloadBtn')));

  /* 2. with the plugin: the button, its name for TalkBack, its hint */
  await install(mini);
  const card = await p.evaluate(() => { const bt = document.getElementById('usdaDownloadBtn'); return { text: bt && bt.textContent, aria: bt && bt.getAttribute('aria-label'), hint: document.getElementById('usdaSettingsBody').textContent, list: [...document.querySelectorAll('#usdaSettingsBody button')].map(x => x.textContent).join('|'), cards: document.querySelectorAll('#view-settings > .card, #view-settings .card').length } });
  ok('the card has a Download button next to Import file', card.text === 'Download' && card.list === 'Download|Import file|Remove', card);
  ok('it has a TalkBack name that says what it fetches', /Download the USDA branded foods from GitHub/.test(card.aria), card.aria);
  ok('and a one-line hint: from GitHub, about 18 MB', /fetches the USDA branded foods from GitHub \(about 18 MB\) and imports them/.test(card.hint), card.hint);
  ok('the card is still one card', await p.evaluate(() => document.querySelectorAll('#usdaCard').length === 1 && document.querySelectorAll('#usdaSettingsBody').length === 1));

  /* 3. success with the small file */
  await p.click('#usdaDownloadBtn');
  ok('the button is disabled the moment it is tapped', await p.evaluate(() => document.getElementById('usdaDownloadBtn').disabled && document.getElementById('usdaImportBtn').disabled));
  await settle('ready · 2 foods');
  let st = await p.evaluate(() => ({ st: __st.slice(), toasts: __toasts.slice(), fd: __fd }));
  ok('progress shows as "Downloading from GitHub… N%"', st.st.includes('Downloading from GitHub… 0%') && st.st.includes('Downloading from GitHub… 40%') && st.st.includes('Downloading from GitHub… 100%'), st.st);
  ok('then the importer takes over, then the same final text', st.st.indexOf('Opening compressed USDA file…') > st.st.indexOf('Downloading from GitHub… 100%') && (await status()) === 'USDA database ready · 2 foods stored on this device', st.st);
  ok('the pieces are read from the start, 1 MiB at a time', JSON.stringify(st.fd.reads) === JSON.stringify([[0, 1048576]]), st.fd.reads);
  ok('the staged file is discarded after the import', st.fd.discards === 1 && st.fd.calls[st.fd.calls.length - 1] === 'discard' && st.fd.calls.indexOf('discard') > st.fd.calls.lastIndexOf('read'), st.fd.calls);
  ok('the progress listener is removed', st.fd.removed === 1);
  ok('the toast says it was imported', st.toasts.includes('USDA food database imported'), st.toasts);
  ok('the foods are stored and searchable', await p.evaluate(async () => { const db = await usdaDb(); const x = db.transaction('foods').objectStore('foods').index('terms').getAll(IDBKeyRange.bound('zorbax', 'zorbax￿')); const out = await new Promise(r => x.onsuccess = () => r(x.result)); db.close(); return out.length === 1 && out[0].n === 'Zorbax Protein Bar' && out[0].port[0].g === 60 }));
  ok('the card is drawn again: buttons enabled, Remove usable', await p.evaluate(() => !document.getElementById('usdaDownloadBtn').disabled && !document.getElementById('usdaImportBtn').disabled && !document.getElementById('usdaRemoveBtn').disabled));

  /* 4. the larger file: several pieces, importer progress */
  ok('the generated file takes at least three pieces', big.length > 2 * 1048576, big.length);
  await wipe(); await install(big);
  await p.click('#usdaDownloadBtn'); await settle('ready · 5,000 foods');
  st = await p.evaluate(() => ({ st: __st.slice(), fd: __fd }));
  ok('5,000 foods are stored', (await count()) === 5000);
  ok('it read several pieces with growing offsets', st.fd.reads.length >= 3 && st.fd.reads.every((r, i) => r[1] === 1048576 && r[0] === i * 1048576), st.fd.reads);
  ok('the importer reported "Importing USDA foods… 5,000 processed"', st.st.includes('Importing USDA foods… 5,000 processed'), st.st.filter(x => !/Downloading/.test(x)));
  ok('the staged file was discarded once', st.fd.discards === 1);

  /* 5. checksum failure and network failure: plain words, nothing imported, the staged file discarded */
  await wipe(); await install(mini, { badSum: true });
  await p.click('#usdaDownloadBtn'); await settle('did not match its checksum');
  st = await p.evaluate(() => ({ fd: __fd, toasts: __toasts.slice() }));
  ok('a checksum failure says so in plain words and keeps the message on the card', (await status()) === 'The downloaded file did not match its checksum. Try again.');
  ok('it imported nothing and read nothing', (await count()) === 0 && st.fd.reads.length === 0);
  ok('it still asks the plugin to discard, and tells the owner by toast', st.fd.discards === 1 && st.toasts.includes('USDA download failed'), st);
  ok('the button is usable again for a retry', await p.evaluate(() => !document.getElementById('usdaDownloadBtn').disabled));
  await install(mini, { netFail: true });
  await p.click('#usdaDownloadBtn'); await settle('Could not reach GitHub');
  ok('no connection says "Could not reach GitHub. Check the connection and try again."', (await status()) === 'Could not reach GitHub. Check the connection and try again.');
  ok('and imported nothing, discarding once', (await count()) === 0 && (await p.evaluate(() => __fd.discards)) === 1);
  await install(mini);
  await p.click('#usdaDownloadBtn'); await settle('ready · 2 foods');
  ok('a retry after a failure works and clears the failure words', (await count()) === 2 && /ready/.test(await status()));

  /* 6. double tap: only one run, the other controls are held while it runs */
  await wipe(); await install(mini, { gate: true });
  await p.evaluate(() => { const bt = document.getElementById('usdaDownloadBtn'); bt.click(); bt.click(); downloadUsdaFoods(); downloadUsdaFoods() });
  await p.waitForTimeout(150);
  ok('three more taps while it runs start nothing', await p.evaluate(() => __fd.downloads === 1));
  ok('while busy the three buttons are held', await p.evaluate(() => ['usdaDownloadBtn', 'usdaImportBtn', 'usdaRemoveBtn'].every(id => document.getElementById(id).disabled)));
  await p.evaluate(() => { navigate('settings'); fv2UsdaRender() }); await p.waitForTimeout(200);
  ok('drawing the card again mid-run keeps it held and keeps the progress words', await p.evaluate(() => document.getElementById('usdaDownloadBtn').disabled && /^Downloading from GitHub/.test(document.getElementById('usdaImportStatus').textContent)));
  await p.evaluate(() => __release()); await settle('ready · 2 foods');
  ok('one run, one discard', await p.evaluate(() => __fd.downloads === 1 && __fd.discards === 1));

  /* 7. failure part-way: the staged file is discarded, what was stored stays, and a retry completes it */
  await wipe(); await install(big, { failOnRead: 3 });
  await p.click('#usdaDownloadBtn'); await settle('Import did not finish');
  st = await p.evaluate(() => ({ fd: __fd, toasts: __toasts.slice() }));
  ok('a read that fails part-way says "Import did not finish: …"', /^Import did not finish: Could not read the downloaded file\.$/.test(await status()), await status());
  ok('the staged file is discarded and the toast says it failed', st.fd.discards === 1 && st.toasts.some(t => /USDA import failed/.test(t)), st);
  const partial = await count();
  ok('it stopped reading after the failure', st.fd.reads.length === 3, st.fd.reads);
  ok('the failure words stay on the card, and the card is usable again', /^Import did not finish/.test(await status()) && (await p.evaluate(() => !document.getElementById('usdaDownloadBtn').disabled)));
  ok('whatever was stored stays (less than all)', partial < 5000, partial);
  await install(big);
  await p.click('#usdaDownloadBtn'); await settle('ready · 5,000 foods');
  ok('a retry stores all 5,000 without duplicates', (await count()) === 5000);

  /* 8. the file import is still the old path */
  const src = fs.readFileSync(path.join(__dirname, '..', 'src', 'runtime', 'segments', '08-runtime.js'), 'utf8');
  ok('importUsdaFoods hands file.stream() to importUsdaStream', /return importUsdaStream\(file\.stream\(\)\)/.test(src));
  await wipe(); await p.evaluate(() => { lmaPlugin = () => null; fv2UsdaRender() });
  await p.setInputFiles('#usdaImportFile', path.join(__dirname, 'fixtures', 'usda-mini.jsonl.gz')); await settle('ready · 2 foods').catch(() => { });
  await p.waitForFunction(() => /ready · 2 foods/.test(document.getElementById('usdaImportStatus').textContent), null, { timeout: 15000 });
  ok('importing a chosen file still works', (await count()) === 2);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
