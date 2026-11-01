/* Journal lock tests (the Android prompt is replaced by a mock): the switch, the cover, the grace minute, what a native screen follows
   (blocksNative and onChange, state and onStatus), and the Journal's words kept out of the page while it is locked: at load, after a
   save, and right after each relock (the background, the grace minute, relock()).
   Run:  NODE_PATH=$(npm root -g) node tests/lock.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
/* In the page: the Journal's words are not there. Its hero, day list and entry are empty, and no canary (the reflection, the activity's
   name or its note) is anywhere in the Journal's view. */
const BARE = () => { window.__bare = () => ['journalHero', 'journalDays', 'journalEntry'].every(id => document.getElementById(id).innerHTML === '') && !/secret thoughts|Canary walk|canary note/.test(document.getElementById('view-history').innerHTML) };
const canary = () => ({ id: 'z', type: 'custom', name: 'Canary walk', icon: 'book', duration: 20, skill: 'Walk', primary: 'adventure', secondary: '', xp: 40, date: new Date().toISOString(), notes: 'canary note', xpSplit: [{ attr: 'adventure', xp: 40 }] });
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 412, height: 860 } }); const p = await ctx.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  const until = (page, fn) => page.waitForFunction(fn, null, { timeout: 15000 }).then(() => true, () => false);
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(2200);
  // web: no native plugin, the toggle is disabled and nothing locks
  await p.evaluate(() => navigate('settings')); await p.waitForTimeout(200);
  ok('in a browser the toggle is disabled with a note', await p.evaluate(() => document.getElementById('jlToggle').disabled && /Android app/.test(document.getElementById('jlSettingsBody').textContent)));
  await p.evaluate(() => navigate('history')); ok('a browser never shows the lock', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  // mock the Android side
  await p.evaluate(act => {
    window.__bio = { auth: [], secure: [], status: { available: true, kind: 'ok' }, next: { ok: true } };
    lmaNative = () => true;
    lmaPlugin = n => n === 'BiometricLock' ? { status: async () => __bio.status, authenticate: async o => { __bio.auth.push(o.subtitle); return __bio.next }, secure: async o => { __bio.secure.push(o.on) } } : (n === 'PageThumb' ? { snap: async () => ({ img: 'x'.repeat(500) }) } : {});
    state.activities.push(act); state.dayReflections = { [getToday()]: 'secret thoughts' };
    navigate('settings'); jlSettingsRender();
  }, canary());
  const msg = () => p.evaluate(() => document.getElementById('jlMsg').textContent);
  // what a native screen showing journal text follows: blocksNative(), and onChange told each time it changes
  await p.evaluate(() => { window.__told = []; LifeMaxJournalLock.onChange(b => __told.push(b)) });
  const native = () => p.evaluate(() => JSON.stringify([LifeMaxJournalLock.blocksNative(), __told]));
  // what a lock card follows: state(), and onStatus told each time the prompt starts or ends or the error line changes
  await p.evaluate(() => { window.__status = []; LifeMaxJournalLock.onStatus(s => __status.push(s)) }); await p.evaluate(BARE);
  const lockState = () => p.evaluate(() => JSON.stringify(LifeMaxJournalLock.state()));
  const statusTold = n => p.evaluate(n => JSON.stringify(__status.slice(-n)), n);
  const st = (on, locked, prompting, error) => JSON.stringify({ on, locked, prompting, error });
  const click = () => p.evaluate(() => { const t = document.getElementById('jlToggle'); t.checked = !t.checked; t.dispatchEvent(new Event('change')) });
  // no screen lock set up on the phone
  await p.evaluate(() => { __bio.status = { available: false, kind: 'none_enrolled' } }); await click(); await p.waitForTimeout(200);
  ok('with no fingerprint or screen lock set up it explains and stays off', !(await p.evaluate(() => jlOn())) && /Set up a fingerprint/.test(await msg()) && !(await p.evaluate(() => document.getElementById('jlToggle').checked)));
  // turn on: asks you to confirm first
  await p.evaluate(() => { __bio.status = { available: true, kind: 'ok' }; __bio.next = { ok: false, cancelled: true } }); await click(); await p.waitForTimeout(200);
  ok('turning it on needs you to confirm; cancelling leaves it off', !(await p.evaluate(() => jlOn())));
  await p.evaluate(() => { __bio.next = { ok: true } }); await click(); await p.waitForTimeout(250);
  ok('confirmed: lock is on and saved', await p.evaluate(() => jlOn() && state.journalLock.on === true));
  ok('native screens: turning the lock on unlocks this session, so nothing is kept out and nothing is told', await native() === '[false,[]]', await native());
  ok('lock card: state() says on and unlocked, with no prompt and no error, and no status was told', await lockState() === st(true, false, false, '') && await statusTold(9) === '[]', await lockState());
  // just turned it on, so this session is unlocked
  await p.evaluate(() => { navigate('history') }); await p.waitForTimeout(300);
  ok('right after turning it on the Journal opens without a second prompt', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  ok('unlocked, the Journal is drawn with its words', await p.evaluate(() => /secret thoughts/.test(document.getElementById('journalEntry').innerHTML) && /Canary walk/.test(document.getElementById('journalEntry').innerHTML) && document.getElementById('journalHero').innerHTML !== '' && document.getElementById('journalDays').innerHTML !== ''));
  ok('the screen is marked secure while the Journal is open', await p.evaluate(() => __bio.secure.slice(-1)[0] === true));
  await p.evaluate(() => { navigate('dashboard') }); await p.waitForTimeout(200);
  ok('and unmarked when you leave it', await p.evaluate(() => __bio.secure.slice(-1)[0] === false));
  // a quick return (inside a minute) stays unlocked; a long absence locks
  await p.evaluate(() => { navigate('history') }); ok('back within a minute: still unlocked', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  await p.evaluate(() => { navigate('dashboard'); window.__bio.next = { ok: false, cancelled: true } }); await p.evaluate(() => { /* pretend two minutes passed */ jlLeftAt = Date.now() - 120000 });
  const graced = await p.evaluate(() => { navigate('history'); return __bare() }); await until(p, () => __status.length >= 2);
  ok('after a longer absence the Journal is locked', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked')));
  ok('and its words leave the page with the grace minute\'s relock', graced);
  ok('native screens: the relock keeps them out and is told once', await native() === '[true,[true]]', await native());
  ok('lock card: told when the prompt starts and when it ends; a cancel leaves no error', await statusTold(9) === '[' + st(true, true, true, '') + ',' + st(true, true, false, '') + ']', await statusTold(9));
  ok('locked content is hidden, but the Media | Journal switch still works', await p.evaluate(() => getComputedStyle(document.getElementById('journalEntry')).visibility === 'hidden' && getComputedStyle(document.querySelector('#view-history .libseg')).visibility !== 'hidden'));
  ok('the cover is shown with an Unlock button', await p.evaluate(() => getComputedStyle(document.getElementById('jlOverlay')).display !== 'none' && !!document.getElementById('jlUnlockBtn')));
  ok('the reflection text is not on screen while locked', !(await p.evaluate(() => { const e = document.getElementById('journalEntry'); return getComputedStyle(e).visibility !== 'hidden' && /secret thoughts/.test(e.innerHTML) })));
  await p.evaluate(() => { __bio.next = { ok: false, cancelled: false } }); await p.evaluate(() => document.getElementById('jlUnlockBtn').click()); await until(p, () => __status.length >= 4);
  ok('a failed unlock stays locked and says so', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked') && /Could not unlock/.test(document.getElementById('jlErr').textContent)));
  ok('native screens: a failed unlock changes nothing and tells nothing', await native() === '[true,[true]]', await native());
  ok('lock card: a failure is told, and state() carries its error line', await statusTold(2) === '[' + st(true, true, true, '') + ',' + st(true, true, false, 'Could not unlock. Try again.') + ']' && await lockState() === st(true, true, false, 'Could not unlock. Try again.'), await statusTold(2));
  // a good unlock draws the Journal under the cover first: whoever onChange tells finds it drawn
  await p.evaluate(() => {
    window.__renders = 0; const real = renderHistory; renderHistory = function () { __renders++; return real.apply(this, arguments) }; window.__realRender = real;
    window.__atUnlock = null; LifeMaxJournalLock.onChange(b => { if (!b && !__atUnlock) __atUnlock = { renders: __renders, entry: /secret thoughts/.test(document.getElementById('journalEntry').innerHTML), hero: document.getElementById('journalHero').innerHTML !== '' } });
  });
  await p.evaluate(() => { __bio.next = { ok: true } }); await p.evaluate(() => document.getElementById('jlUnlockBtn').click()); await until(p, () => __status.length >= 6);
  ok('a good unlock opens the Journal', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked') && /secret thoughts/.test(document.getElementById('journalEntry').innerHTML)));
  ok('native screens: the unlock lets them in and is told', await native() === '[false,[true,false]]', await native());
  const atUnlock = await p.evaluate(() => { renderHistory = __realRender; return __atUnlock });
  ok('the unlock draws the Journal before it tells: the watcher finds the entry and the hero drawn', !!atUnlock && atUnlock.renders === 1 && atUnlock.entry && atUnlock.hero, atUnlock);
  ok('lock card: the prompt\'s end is told after the unlock, with the error line cleared', await statusTold(2) === '[' + st(true, true, true, '') + ',' + st(true, false, false, '') + ']' && await lockState() === st(true, false, false, ''), await statusTold(2));
  ok('prompt() answers nothing when there is nothing to ask (unlocked), and tells nothing', await p.evaluate(async () => { const n = __status.length, r = await LifeMaxJournalLock.prompt(); return r === undefined && __status.length === n }));
  // leaving the app locks it straight away, and what was being typed is saved first
  await p.evaluate(() => { histSel = getToday(); renderHistory() }); await p.focus('#dayReflection'); await p.keyboard.press('Control+End'); await p.keyboard.type(' and more');
  const hidden = await p.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }); document.dispatchEvent(new Event('visibilitychange')); return __bare() });
  ok('putting the app in the background locks the Journal', await p.evaluate(() => document.getElementById('view-history').classList.contains('jlocked')));
  ok('and its words leave the page at once', hidden);
  ok('a reflection being typed is saved before the cover hides it', await until(p, () => state.dayReflections[getToday()] === 'secret thoughts and more' && JSON.parse(localStorage.getItem(STORE)).dayReflections[getToday()] === 'secret thoughts and more'));
  ok('native screens: going to the background keeps them out and is told', await native() === '[true,[true,false,true]]', await native());
  await p.evaluate(() => { Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' }); __bio.next = { ok: true }; jlPrompt() }); await until(p, () => !jlBlocksNative());
  const relocked = await p.evaluate(() => { LifeMaxJournalLock.relock(); return { blocked: LifeMaxJournalLock.blocksNative(), bare: __bare() } });
  ok('relock() locks the Journal and its words leave the page', relocked.blocked && relocked.bare, relocked);
  await p.evaluate(() => { jlPrompt() }); await until(p, () => !jlBlocksNative());
  ok('unlocked again, the Journal is drawn with what was typed', await p.evaluate(() => /secret thoughts and more/.test(document.getElementById('journalEntry').innerHTML)));
  // tab previews never keep a picture of the Journal
  const snap = await p.evaluate(async () => { navigate('history'); return await lmaThumbSnapNow('history') });
  ok('tab previews skip a locked-Journal screen', snap === false);
  await p.evaluate(() => { lmaThumbs.history = 'old'; lmaThumbs.library = 'old' });
  // turning it off needs a confirmation too
  await p.evaluate(() => { navigate('settings'); jlSettingsRender(); __bio.next = { ok: false, cancelled: true } }); await click(); await p.waitForTimeout(200);
  ok('turning it off needs you to confirm; cancelling keeps it on', await p.evaluate(() => jlOn() && document.getElementById('jlToggle').checked));
  await p.evaluate(() => { __bio.next = { ok: true } }); await click(); await p.waitForTimeout(250);
  ok('confirmed: lock is off', await p.evaluate(() => !jlOn()));
  const [blocks, told] = JSON.parse(await native());
  ok('native screens: with the lock off nothing is kept out, and every change was told once, in turn', !blocks && told.length >= 4 && told.every((b, i) => b === (i % 2 === 0)) && told[told.length - 1] === false, told);
  await p.evaluate(() => { navigate('dashboard'); jlLeftAt = 0; navigate('history') }); ok('with the lock off the Journal opens freely', await p.evaluate(() => !document.getElementById('view-history').classList.contains('jlocked')));
  await p.close();

  // a page loaded with the lock on: the Journal is never drawn, neither by the start-up drawing nor by the lock's start-up check, and a
  // save while locked draws it again empty, still deciding the selected day as an open drawing does
  const boot = await ctx.newPage(); boot.on('pageerror', e => errs.push(String(e)));
  await boot.addInitScript(act => {
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({ onboardingComplete: true, name: 'N', tutorialDone: true, journalLock: { on: true }, activities: [act], dayReflections: { [new Date().toLocaleDateString('en-CA')]: 'secret thoughts' } }));
    window.__drawn = [];
    new MutationObserver(rs => rs.forEach(r => { const t = r.target.closest && r.target.closest('#journalHero,#journalDays,#journalEntry'); if (t && r.addedNodes.length) __drawn.push(t.id) })).observe(document, { childList: true, subtree: true });
  }, canary());
  await boot.addInitScript(BARE);
  await boot.goto('file://' + file);
  const loaded = await boot.evaluate(() => ({ drawn: __drawn.slice(), bare: __bare(), locked: jlBlocksNative(), days: journalDays().length }));
  ok('a page loaded with the lock on never draws the Journal', loaded.locked && loaded.days === 1 && loaded.drawn.length === 0 && loaded.bare, loaded);
  await boot.waitForTimeout(2200); // the lock's start-up check (1.8 s) applies the lock and asks
  ok('nor does the lock\'s start-up check', await boot.evaluate(() => __drawn.length === 0 && __bare() && jlBlocksNative()));
  const saved = await boot.evaluate(() => { const real = jcDecide, seen = []; jcDecide = function (d) { seen.push(d); return real.apply(this, arguments) }; let stored; try { stored = save() } finally { jcDecide = real } return { stored, seen, sel: histSel, drawn: __drawn.length, bare: __bare() } });
  ok('a save while locked leaves the Journal empty', saved.stored && saved.drawn === 0 && saved.bare, saved);
  ok('and the locked drawing still decides the selected day (jcDecide)', JSON.stringify(saved.seen) === JSON.stringify([saved.sel]), saved);
  const opened = await boot.evaluate(async () => {
    lmaNative = () => true; lmaPlugin = n => n === 'BiometricLock' ? { status: async () => ({ available: true, kind: 'ok' }), authenticate: async () => ({ ok: true }), secure: async () => { } } : {};
    const r = await jlPrompt(); return { r, entry: /secret thoughts/.test(document.getElementById('journalEntry').innerHTML) && /canary note/.test(document.getElementById('journalEntry').innerHTML), hero: document.getElementById('journalHero').innerHTML !== '' };
  });
  ok('unlocked, that page draws the Journal', opened.r === true && opened.entry && opened.hero, opened);
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
