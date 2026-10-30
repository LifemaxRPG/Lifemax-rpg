/* Journal lock tests (the Android prompt is replaced by a mock). Run:  NODE_PATH=$(npm root -g) node tests/lock.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(2200);
  // web: no native plugin, the toggle is disabled and nothing locks
  await p.evaluate(() => navigate('settings')); await p.waitForTimeout(200);
  ok('in a browser the toggle is disabled with a note', await p.evaluate(() => document.getElementById('jlToggle').disabled && /Android app/.test(document.getElementById('jlSettingsBody').textContent)));
  await p.evaluate(() => navigate('history')); ok('a browser never shows the lock', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  // mock the Android side
  await p.evaluate(() => {
    window.__bio = { auth: [], secure: [], status: { available: true, kind: 'ok' }, next: { ok: true } };
    lmaNative = () => true;
    lmaPlugin = n => n === 'BiometricLock' ? { status: async () => __bio.status, authenticate: async o => { __bio.auth.push(o.subtitle); return __bio.next }, secure: async o => { __bio.secure.push(o.on) } } : (n === 'PageThumb' ? { snap: async () => ({ img: 'x'.repeat(500) }) } : {});
    state.activities.push({ id: 'z', type: 'custom', name: 'Read', icon: 'book', duration: 20, skill: 'Read', primary: 'intelligence', secondary: '', xp: 40, date: new Date().toISOString(), notes: '', xpSplit: [{ attr: 'intelligence', xp: 40 }] }); state.dayReflections = { [getToday()]: 'secret thoughts' };
    navigate('settings'); jlSettingsRender();
  });
  const msg = () => p.evaluate(() => document.getElementById('jlMsg').textContent);
  const click = () => p.evaluate(() => { const t = document.getElementById('jlToggle'); t.checked = !t.checked; t.dispatchEvent(new Event('change')) });
  // no screen lock set up on the phone
  await p.evaluate(() => { __bio.status = { available: false, kind: 'none_enrolled' } }); await click(); await p.waitForTimeout(200);
  ok('with no fingerprint or screen lock set up it explains and stays off', !(await p.evaluate(() => jlOn())) && /Set up a fingerprint/.test(await msg()) && !(await p.evaluate(() => document.getElementById('jlToggle').checked)));
  // turn on: asks you to confirm first
  await p.evaluate(() => { __bio.status = { available: true, kind: 'ok' }; __bio.next = { ok: false, cancelled: true } }); await click(); await p.waitForTimeout(200);
  ok('turning it on needs you to confirm; cancelling leaves it off', !(await p.evaluate(() => jlOn())));
  await p.evaluate(() => { __bio.next = { ok: true } }); await click(); await p.waitForTimeout(250);
  ok('confirmed: lock is on and saved', await p.evaluate(() => jlOn() && state.journalLock.on === true));
  // just turned it on, so this session is unlocked
  await p.evaluate(() => { navigate('history') }); await p.waitForTimeout(300);
  ok('right after turning it on the Journal opens without a second prompt', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  ok('the screen is marked secure while the Journal is open', await p.evaluate(() => __bio.secure.slice(-1)[0] === true));
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(200);
  ok('and unmarked when you leave it', await p.evaluate(() => __bio.secure.slice(-1)[0] === false));
  // a quick return (inside a minute) stays unlocked; a long absence locks
  await p.evaluate(() => { navigate('history') }); ok('back within a minute: still unlocked', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  await p.evaluate(() => { navigate('dashboard'); window.__bio.next = { ok: false, cancelled: true } }); await p.evaluate(() => { /* pretend two minutes passed */ jlLeftAt = Date.now() - 120000 });
  await p.evaluate(() => navigate('history')); await p.waitForTimeout(300);
  ok('after a longer absence the Journal is locked', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked')));
  ok('locked content is hidden, but the Media | Journal switch still works', await p.evaluate(() => getComputedStyle(document.getElementById('journalEntry')).visibility === 'hidden' && getComputedStyle(document.querySelector('#view-history .libseg')).visibility !== 'hidden'));
  ok('the cover is shown with an Unlock button', await p.evaluate(() => getComputedStyle(document.getElementById('jlOverlay')).display !== 'none' && !!document.getElementById('jlUnlockBtn')));
  ok('the reflection text is not on screen while locked', !(await p.evaluate(() => { const e = document.getElementById('journalEntry'); return getComputedStyle(e).visibility !== 'hidden' && /secret thoughts/.test(e.innerHTML) })));
  await p.evaluate(() => { __bio.next = { ok: false, cancelled: false } }); await p.evaluate(() => document.getElementById('jlUnlockBtn').click()); await p.waitForTimeout(300);
  ok('a failed unlock stays locked and says so', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked') && /Could not unlock/.test(document.getElementById('jlErr').textContent)));
  await p.evaluate(() => { __bio.next = { ok: true } }); await p.evaluate(() => document.getElementById('jlUnlockBtn').click()); await p.waitForTimeout(400);
  ok('a good unlock opens the Journal', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked') && /secret thoughts/.test(document.getElementById('journalEntry').innerHTML)));
  // leaving the app locks it straight away
  await p.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')) });
  ok('putting the app in the background locks the Journal', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked')));
  await p.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); __bio.next = { ok: true }; jlPrompt() }); await p.waitForTimeout(300);
  // tab previews never keep a picture of the Journal
  const snap = await p.evaluate(async () => { navigate('history'); return await lmaThumbSnapNow('history') });
  ok('tab previews skip a locked-Journal screen', snap === false);
  await p.evaluate(() => { lmaThumbs.history = 'old'; lmaThumbs.library = 'old' });
  // turning it off needs a confirmation too
  await p.evaluate(() => { navigate('settings'); jlSettingsRender(); __bio.next = { ok: false, cancelled: true } }); await click(); await p.waitForTimeout(200);
  ok('turning it off needs you to confirm; cancelling keeps it on', await p.evaluate(() => jlOn() && document.getElementById('jlToggle').checked));
  await p.evaluate(() => { __bio.next = { ok: true } }); await click(); await p.waitForTimeout(250);
  ok('confirmed: lock is off', await p.evaluate(() => !jlOn()));
  await p.evaluate(() => { navigate('dashboard'); jlLeftAt = 0; navigate('history') }); ok('with the lock off the Journal opens freely', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
