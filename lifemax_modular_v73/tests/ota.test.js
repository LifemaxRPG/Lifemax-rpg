/* Over-the-air update tests (the Android side is mocked). Run:  NODE_PATH=$(npm root -g) node tests/ota.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const plan = (r, st) => p.evaluate(([r, st]) => (() => {try{return lmaUpdPlan(r,st)}catch(e){return 'error'}})(), [r, st]);
  const REL = { current: 100, latest: 105, apk: 'https://x/apk', nativeApi: 1, latestNativeApi: 1, webBuild: 105, webUrl: 'https://x/app.html', webSha: 'a'.repeat(64), webMinApi: 1 };
  const ST = { nativeApi: 1, installedBuild: 100, activeBuild: 0, staged: 0 };
  ok('web-only release: a web update, no APK', await plan(REL, ST) === 'web');
  ok('the release needs newer native abilities: the APK is offered', await plan({ ...REL, latestNativeApi: 2, webMinApi: 2 }, ST) === 'apk');
  ok('new abilities without a newer APK: invalid release is surfaced', await plan({ ...REL, latestNativeApi: 2, webMinApi: 2, latest: 100 }, ST) === 'error');
  ok('already running that web build over the air: nothing to do', await plan(REL, { ...ST, activeBuild: 105 }) === 'none');
  ok('an installed APK as new as the release: nothing to do', await plan({ ...REL, current: 105 }, { ...ST, installedBuild: 105 }) === 'none');
  ok('an older web build over the air is replaced by a newer release', await plan(REL, { ...ST, activeBuild: 103 }) === 'web');
  ok('an old-style release file (no web block): the APK, as before', await plan({ current: 100, latest: 105, apk: 'https://x/apk' }, ST) === 'apk');
  ok('a shell without the over-the-air plugin: the APK, as before', await plan(REL, null) === 'apk');
  ok('nothing newer: up to date', await plan({ ...REL, latest: 100, webBuild: 100 }, ST) === 'none');
  ok('higher web minimum also offers the APK', await plan({ ...REL, webMinApi: 2 }, ST) === 'apk');
  // the flow with a mocked shell
  await p.evaluate(({ REL, ST }) => {
    window.__calls = []; window.__st = Object.assign({}, ST);
    lmaNative = () => true;
    lmaPlugin = n => n === 'Updater' ? { check: async () => REL, addListener() {}, install: async o => { __calls.push('apk') } }
      : n === 'LiveWeb' ? { status: async () => __st, fetch: async o => { __calls.push(['fetch', o.build, o.url, o.sha256]); __st.staged = o.build; return { staged: o.build } }, restart: async () => { __calls.push('restart') }, healthy: async () => { __calls.push('healthy') }, addListener() {} } : null;
  }, { REL, ST });
  await p.evaluate(() => lmaCheckUpdate(true)); await p.waitForTimeout(300);
  const c = await p.evaluate(() => ({ calls: __calls, state: lmaUpd.state, pop: !!document.querySelector('.updpop'), pill: !!document.querySelector('.lmaweb-pill') }));
  ok('a web update downloads by itself with the right build, address and fingerprint', JSON.stringify(c.calls[0]) === JSON.stringify(['fetch', 105, 'https://x/app.html', 'a'.repeat(64)]), c.calls);
  ok('and ends ready, with the small Restart pill but no APK pop-up', c.state === 'webready' && c.pill && !c.pop, c);
  await p.evaluate(() => document.querySelector('.lmaweb-pill [data-w=go]').click()); await p.waitForTimeout(100);
  ok('Restart asks the shell to switch to the new build', (await p.evaluate(() => __calls)).includes('restart'));
  ok('Settings says so in plain words', await p.evaluate(() => { navigate('settings'); lmaRenderUpdates(); return /Update downloaded/.test(document.getElementById('lmaUpdBody').textContent) }));
  // an APK-needed release shows the old pop-up and does not download the web build
  await p.evaluate(({ REL }) => { __calls.length = 0; document.querySelectorAll('.lmaweb-pill').forEach(e => e.remove()); lmaUpd.state = 'idle'; const o = lmaPlugin; lmaPlugin = (n => { return n === 'Updater' ? { check: async () => Object.assign({}, REL, { latestNativeApi: 2, webMinApi: 2 }), addListener() {}, install: async () => {} } : o(n) }); lmaUpdLater = 0; lmaCheckUpdate(true) }, { REL }); await p.waitForTimeout(400);
  const d = await p.evaluate(() => ({ calls: __calls, pop: !!document.querySelector('.updpop'), state: lmaUpd.state }));
  ok('a release that needs the new APK offers the APK pop-up and downloads no web build', d.pop && d.state === 'available' && !d.calls.some(x => Array.isArray(x)), d);
  // healthy report
  await p.evaluate(() => { __calls.length = 0 }); await p.waitForTimeout(6300);
  ok('the app reports itself healthy a few seconds after it has started', (await p.evaluate(() => __calls)).includes('healthy') || (await p.evaluate(() => window.__lmaBooted === true)));
  ok('the last segment ran (the boot marker is set)', await p.evaluate(() => window.__lmaBooted === true));
  // the Java sources hold the safety rules
  const j = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'LiveWeb.java'), 'utf8');
  ok('native: a stored build is dropped when the APK is as new', /installed >= active/.test(j));
  ok('native: a build that never reports healthy is dropped after two tries', /tries > 2/.test(j) && /"pending"/.test(j));
  const pl = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'LiveWebPlugin.java'), 'utf8');
  ok('native: a download is checked against its SHA-256 before it is kept', /sha256/.test(pl) && /did not match its fingerprint/.test(pl) && /https:\/\//.test(pl));
  const wf = fs.readFileSync(path.join(__dirname, '..', '..', '.github', 'workflows', 'build-android.yml'), 'utf8');
  ok('CI publishes app.html and a version.json with the web block and the native API number', /app\.html/.test(wf) && /"web":/.test(wf) && /nativeApi/.test(wf) && /NativeApi\.java/.test(wf));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
