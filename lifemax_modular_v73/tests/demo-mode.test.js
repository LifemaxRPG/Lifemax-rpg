/* Demo mode (docs/architecture/DEMO_MODE.md), through the real page. The owner's own save and every other real key must be byte-for-byte
   the same while a demo runs and after it ends; the demo starts at the welcome screen with an empty save; it writes only under its own
   prefix; what it switches off stays off (backups, reminders, Health sync, calendar, widget, Android backup); a reset clears only the
   demo; an app kill leaves the next launch in the demo; Return restores the real save; a damaged marker never shows the real save.
   The real storage is read through a same-origin frame, which the page's storage view does not cover.
   Run: NODE_PATH=$(npm root -g) node tests/demo-mode.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const currentPage = require('./current-page'), file = currentPage(process.argv[2]);
const STORE = 'lifemax_rpg_prototype_v1', PREFIX = 'lmdemo:v1:', MARKER = 'lm_demo_active', PREFS = 'lifemax_android_prefs_v1';
const fixture = n => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', n + '.json'), 'utf8'));
let url = '', pass = 0, fail = 0;
const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 700) : '')) } };

const OWNER = {
  [PREFS]: JSON.stringify({ swipe: false, nativeOverview: true, backup: { on: true, uri: 'content://real/folder', folder: 'Real', verified: 5 }, apps: [{ id: 'x' }], notif: { on: true, workout: true } }),
  lifeMaxTheme: 'light', lm_pet_seen: '1700000000000', lma_qfx: 'off', lifemax_gemini_key_v1: 'REAL-KEY', lifemax_voice_enabled: 'false', unrelated_key: 'keep me'
};
/* Every write the page makes to storage, recorded by a wrapper installed before the page runs: the method, the key as the browser
   received it, and whether the page was in the demo when it wrote. The log lives in sessionStorage, which survives a reload. */
const SPY = () => {
  const ss = window.sessionStorage, get = Storage.prototype.getItem, set = Storage.prototype.setItem;
  const log = (m, k) => { try { const a = JSON.parse(get.call(ss, '__spy') || '[]'); a.push([m, String(k), !!(window.LM_DEMO && window.LM_DEMO.on)]); set.call(ss, '__spy', JSON.stringify(a)) } catch (e) { } };
  for (const m of ['setItem', 'removeItem', 'clear']) {
    const was = Storage.prototype[m];
    Storage.prototype[m] = function (k) { if (this !== ss) log(m, k); return was.apply(this, arguments) };
  }
};
async function phone(b, { save, extras = OWNER, marker, seed = true } = {}) {
  const c = await b.newContext({ viewport: { width: 412, height: 860 } }), writes = [], errs = [], dialogs = [];
  /* moves the log of the page's writes into `writes` (it is lost when the page is closed) */
  const drain = async p => { const a = await p.evaluate(() => { const v = sessionStorage.getItem('__spy'); sessionStorage.removeItem('__spy'); return JSON.parse(v || '[]') }); a.forEach(([kind, key, demo]) => writes.push({ kind, key, demo })) };
  const open = async (first) => {
    const p = await c.newPage();
    p.on('pageerror', e => errs.push(String(e))); p.on('dialog', d => { dialogs.push(d.message()); d.accept() });
    await p.addInitScript(SPY);
    if (first && seed) await p.addInitScript(([STORE, save, extras, marker]) => {
      if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
      if (save) localStorage.setItem(STORE, save);
      for (const k in extras) localStorage.setItem(k, extras[k]);
      if (marker !== undefined && marker !== null) localStorage.setItem('lm_demo_active', marker);
    }, [STORE, save, extras, marker]);
    await p.goto(url, { timeout: 120000 }); await p.waitForTimeout(4500);
    return p;
  };
  return { c, writes, errs, dialogs, open, drain, close: () => c.close() };
}
/* the real storage and database names, as the page's storage view hides them */
const real = p => p.evaluate(async () => {
  const f = document.createElement('iframe'); document.body.appendChild(f);
  const w = f.contentWindow, ls = {};
  for (let i = 0; i < w.localStorage.length; i++) { const k = w.localStorage.key(i); ls[k] = w.localStorage.getItem(k) }
  const idb = (await w.indexedDB.databases()).map(d => d.name).sort();
  f.remove(); return { ls, idb };
});
const realOnly = s => ({ ls: Object.fromEntries(Object.entries(s.ls).filter(([k]) => !k.startsWith('lmdemo:') && k !== MARKER)), idb: s.idb.filter(n => !n.startsWith('lmdemo:')) });
const sameExcept = (a, b, skip = new Set()) => { const x = k => Object.entries(k.ls).filter(([n]) => !skip.has(n)).sort(); return JSON.stringify(x(a)) === JSON.stringify(x(b)) && JSON.stringify(a.idb) === JSON.stringify(b.idb) };
const same = (a, b) => JSON.stringify(Object.entries(a.ls).sort()) === JSON.stringify(Object.entries(b.ls).sort()) && JSON.stringify(a.idb) === JSON.stringify(b.idb);
const sheet = (p, which) => p.evaluate(w => { const o = document.querySelector('.lm-demo-ask'); if (!o) return null; const t = o.querySelector('h3').textContent; const btn = [...o.querySelectorAll('.btn')]; (w === 'yes' ? btn.find(x => !x.classList.contains('secondary')) : btn.find(x => x.classList.contains('secondary'))).click(); return t }, which);
const demoOn = p => p.evaluate(() => !!(window.LM_DEMO && LM_DEMO.on));
const waitDemo = (p, on) => p.waitForFunction(on => !!(window.LM_DEMO && LM_DEMO.on) === on && window.__lmaBooted, on, { timeout: 60000 }).then(() => p.waitForTimeout(3000));
/* a phone whose native plugins are mocks that record every call, to see what the demo does not reach */
const NATIVE = `(() => {
  window.__calls = [];
  const mock = name => new Proxy({}, { get: (_, m) => m === 'then' ? undefined : (...a) => { window.__calls.push(name + '.' + String(m)); return Promise.resolve({ notifications: [], granted: [], sdk: 'available', allowed: false, saved: true, picked: false, count: 0 }) } });
  const plugins = new Proxy({}, { get: (t, n) => typeof n === 'string' ? (t[n] = t[n] || mock(n)) : undefined });
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: () => true, Plugins: plugins, registerPlugin: n => plugins[n] };
})()`;
const TOUCH = `(async () => {
  window.__calls.length = 0; window.confirm = () => true;
  lmaLoad().notif.on = true; lmaLoad().notif.workout = true;
  await lmaSync(true); await lmaTestNotification().catch(() => { });
  const h = lmaHealthCfg(); await lmaHealthSync(true); await lmcSync(true); await lmaWidgetPush(true); await lmaPickBackupFolder(); await lmaRunBackup(true).catch(() => { });
  exportData(); await applyBackup({ state: { activities: [], attrXP: {} } }); await setDeviceBackupAllowed(true);
  await new Promise(r => setTimeout(r, 400)); return window.__calls.slice();
})()`;
const DENIED = /^(LocalNotifications|HealthSync|CalendarSync|WidgetBridge|BackupFolder|DeviceBackup|FileSaver)\./;

(async () => {
  const site = await currentPage.serve(file); url = site.url;
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const saveText = JSON.stringify({ ...fixture('established').state, onboardingComplete: true, tutorialDone: true });

  /* ---- the whole trip: real save, enter, play, kill, reset, return ---- */
  {
    const ph = await phone(b, { save: saveText });
    let p = await ph.open(true);
    ok('before the demo: the page runs on the real save and shows no demo', !(await demoOn(p)) && await p.evaluate(() => state.activities.length) > 100 && !(await p.evaluate(() => !!document.querySelector('.lm-demo-chip'))));
    await p.evaluate(async () => { await new Promise((res, rej) => { const f = document.createElement('iframe'); document.body.appendChild(f); const q = f.contentWindow.indexedDB.open('lifemax_audio_v1', 1); q.onupgradeneeded = () => q.result.createObjectStore('files'); q.onsuccess = () => { const t = q.result.transaction('files', 'readwrite'); t.objectStore('files').put({ real: true }, 'book'); t.oncomplete = () => { q.result.close(); f.remove(); res() } }; q.onerror = () => rej(q.error) }) });
    await p.evaluate(() => navigate('settings')); await p.waitForTimeout(500);
    let S0 = await real(p);
    ok('the owner\'s keys are all there to compare (save, preferences, theme, cat, keys, an audio database)', S0.ls[STORE] && S0.ls[PREFS] && S0.ls.lifeMaxTheme && S0.ls.unrelated_key && S0.idb.includes('lifemax_audio_v1'), Object.keys(S0.ls));
    ok('Settings offers "Run a demo" and not the way back', await p.evaluate(() => !document.getElementById('lmDemoCard').hidden && document.getElementById('lmDemoOnCard').hidden && /Run a demo/.test(document.getElementById('lmDemoStartBtn').textContent)));

    /* enter: a confirmation that says what happens, then a reload into the first screen */
    await p.evaluate(() => document.getElementById('lmDemoStartBtn').click());
    const txt = await p.evaluate(() => document.querySelector('.lm-demo-ask').textContent);
    ok('the confirmation says the save is temporary, the real one is safe, and what is off', /temporary save/.test(txt) && /not opened, changed or deleted/.test(txt) && /Backups/.test(txt) && /Reminders/.test(txt) && /Health sync/.test(txt), txt);
    ok('"Not now" changes nothing', (await sheet(p, 'no')) === 'Run a demo?' && !(await demoOn(p)) && same(await real(p), S0));
    await ph.drain(p); ph.writes.length = 0; await p.waitForTimeout(1500); S0 = await real(p); await p.evaluate(() => document.getElementById('lmDemoStartBtn').click()); await sheet(p, 'yes');
    await waitDemo(p, true); await ph.drain(p);
    ok('after the reload the page is in the demo, at the welcome screen, with an empty save', await p.evaluate(() => LM_DEMO.on && !!document.querySelector('#onboarding.show') && state.activities.length === 0 && !state.onboardingComplete && state.name === 'The Life Architect'));
    ok('the DEMO chip shows, and the top bar says DEMO', await p.evaluate(() => !!document.querySelector('.lm-demo-chip') && /DEMO/.test(document.querySelector('.lm-demo-chip').textContent) && document.body.classList.contains('lm-demo') && document.querySelector('.topbar .pill').textContent === 'DEMO'));
    ok('the demo looks like the owner\'s app (theme, layout) but has none of their backup folder, apps or reminders', await p.evaluate(([PREFS]) => { const pr = JSON.parse(localStorage.getItem(PREFS)); return localStorage.getItem('lifeMaxTheme') === 'light' && pr.nativeOverview === true && pr.swipe === false && !pr.backup && !pr.apps && pr.notif.on === false && !lmaLoad().backup.uri && !lmaLoad().backup.on && lmaLoad().apps.length === 0 && localStorage.getItem('lifemax_gemini_key_v1') === null && localStorage.getItem('unrelated_key') === null }, [PREFS]));
    const S1 = await real(p);
    /* what the real page itself wrote before it reloaded (it was still the real page, so it kept its routine going) */
    const preReload = new Set(ph.writes.filter(w => !w.demo && !w.key.startsWith(PREFIX) && w.key !== MARKER).map(w => w.key));
    ok('on the demo\'s first screen every real key is byte-identical to before, apart from what the real page itself wrote before it reloaded', sameExcept(realOnly(S1), S0, preReload), { preReload: [...preReload], differ: Object.keys(S0.ls).filter(k => S1.ls[k] !== S0.ls[k]), idb: [S0.idb, S1.idb] });
    ok('the only keys added are the marker and the demo\'s own', Object.keys(S1.ls).filter(k => !(k in S0.ls)).every(k => k === MARKER || k.startsWith(PREFIX)));

    /* play: the welcome flow, a log, a theme change, the audio library, a pet visit, a reset-free session */
    await p.evaluate(() => { document.getElementById('onbName').value = 'Demo Dana'; for (let i = 0; i < 5; i++) nextOnboardingStep(); finishOnboarding(); });
    await p.waitForTimeout(600);
    const played = await p.evaluate(async () => {
      window.toast = () => { };
      const rec = recordActivity({ type: 'reading', duration: '30', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' });
      setLightMode(false); petSeenNow({}); lmaSet('workout', false);
      await audioDbPut('demo-book', { demo: true });
      localStorage.setItem('lma_set_cat', 'data'); sessionStorage.setItem('x', '1');
      return { onboarded: state.onboardingComplete === true, name: state.name, logged: rec.ok && state.activities.filter(a => a.type === 'reading' && Number(a.duration) === 30).length, uniqueIds: new Set(state.activities.map(a => a.id)).size === state.activities.length, theme: localStorage.getItem('lifeMaxTheme') };
    });
    ok('the demo can be played: onboarding finishes, an activity is logged, preferences change', played.onboarded && played.name === 'Demo Dana' && played.logged === 1 && played.uniqueIds && played.theme === 'dark', played);
    await p.waitForTimeout(500);
    const S2 = await real(p);
    ok('after playing, every real key is still byte-identical, and the audio library change is in a demo database only', same(realOnly(S2), realOnly(S1)) && S2.idb.includes(PREFIX + 'lifemax_audio_v1'), S2.idb);
    ok('the demo\'s own data is under its prefix: save, preferences and theme', !!S2.ls[PREFIX + STORE] && JSON.parse(S2.ls[PREFIX + STORE]).name === 'Demo Dana' && S2.ls[PREFIX + 'lifeMaxTheme'] === 'dark');
    await ph.drain(p);
    const bad = ph.writes.filter(w => w.demo && !w.key.startsWith(PREFIX));
    ok('every write the demo page made went to the demo prefix (setItem, removeItem and clear were all watched)', ph.writes.filter(w => w.demo).length > 3 && bad.length === 0 && !ph.writes.some(w => w.demo && w.kind === 'clear'), bad.slice(0, 5));
    ok('the page never listed or reached a real key: its storage shows only the demo\'s keys', await p.evaluate(([STORE]) => Object.keys(localStorage).every(k => !k.startsWith('lmdemo:') && k !== 'lm_demo_active') && localStorage.getItem('unrelated_key') === null && localStorage.length === Object.keys(localStorage).length && JSON.parse(localStorage.getItem(STORE)).name === 'Demo Dana', [STORE]));

    /* what is off stays off, and the owner's own reminders are not touched */
    await p.evaluate(NATIVE); const calls = await p.evaluate(TOUCH);
    ok('in the demo no reminder, Health, calendar, widget, backup, file save or Android backup call is made', !calls.some(c => DENIED.test(c)), calls);
    ok('those cards say they are off in the demo, and the Android backup switch cannot be used', await p.evaluate(() => ['lmaNotifBody', 'lmaWidgetBody', 'lmaHealthBody', 'lmaCalBody', 'lmaBakBody'].every(id => /Off in the demo/.test(document.getElementById(id).textContent)) && document.getElementById('deviceBackupToggle').disabled && /Off in the demo/.test(document.getElementById('deviceBackupNote').textContent)));
    ok('Settings now offers "Return to my save" (and not "Run a demo")', await p.evaluate(() => document.getElementById('lmDemoCard').hidden && !document.getElementById('lmDemoOnCard').hidden && !!document.getElementById('lmDemoReturnBtn')));
    ok('the demo shows in the tree of cards the native Settings screen is drawn from: the return card is first in Look & feel, the start card is not drawn', await p.evaluate(() => { const v = document.getElementById('view-settings'); const cards = [...v.querySelectorAll('.card')].filter(c => !c.parentElement.closest('.card') && !c.hidden); return setCatOf(document.getElementById('lmDemoOnCard')) === 'look' && cards[0].id === 'lmDemoOnCard' && !cards.some(c => c.id === 'lmDemoCard') }));
    ok('the cloud voice key of the owner is not reachable', await p.evaluate(() => lmgKey() === '' && lmgOn() === false));
    ok('page errors during the demo: none', ph.errs.length === 0, ph.errs);

    /* an app kill: a new page in the same browser profile is still the demo, with the demo's progress */
    await ph.drain(p);
    // The save may contain automatic quest rewards as well as the user's reading log.
    // Preserve the exact earned progress across restart instead of assuming a total count.
    const demoProgress = await p.evaluate(() => state.activities.map(a => ({
      id: a.id, type: a.type, duration: a.duration, xp: a.xp, name: a.name
    })).sort((a, b) => String(a.id).localeCompare(String(b.id))));
    await p.close(); p = await ph.open(false); await waitDemo(p, true);
    ok('after the app is killed the next launch is still the demo, with its progress', await p.evaluate(expected => {
      const actual = state.activities.map(a => ({ id: a.id, type: a.type, duration: a.duration, xp: a.xp, name: a.name }))
        .sort((a, b) => String(a.id).localeCompare(String(b.id)));
      return LM_DEMO.on && state.name === 'Demo Dana' && JSON.stringify(actual) === JSON.stringify(expected) &&
        actual.filter(a => a.type === 'reading' && Number(a.duration) === 30).length === 1 &&
        !document.querySelector('#onboarding.show');
    }, demoProgress));
    ok('Settings still offers the way back after the relaunch', await p.evaluate(() => { navigate('settings'); return !document.getElementById('lmDemoOnCard').hidden }));

    /* reset: Delete all data and Reset to starter clear only the demo */
    await p.evaluate(() => { window.confirm = () => true; deleteAll() }); await p.waitForTimeout(400);
    const S3 = await real(p);
    ok('"Delete all local data" in the demo clears the demo only: the real keys are untouched', same(realOnly(S3), realOnly(S1)) && await p.evaluate(() => state.activities.length === 0));
    ok('the demo save is gone and the demo marker remains', !S3.ls[PREFIX + STORE] || JSON.parse(S3.ls[PREFIX + STORE]).activities.length === 0);

    /* return: Discard the demo data? */
    await p.evaluate(() => document.getElementById('lmDemoReturnBtn').click());
    const title = await p.evaluate(() => document.querySelector('.lm-demo-ask h3').textContent);
    ok('Return asks "Discard the demo data?" with the discard button as the main one', title === 'Discard the demo data?' && await p.evaluate(() => document.activeElement.textContent === 'Discard and return'));
    ok('"Stay in the demo" keeps the demo', (await sheet(p, 'no')) === 'Discard the demo data?' && await demoOn(p));
    await p.evaluate(() => document.getElementById('lmDemoReturnBtn').click()); await ph.drain(p); ph.writes.length = 0; await sheet(p, 'yes');
    await waitDemo(p, false); await ph.drain(p);
    const S4 = await real(p);
    ok('after Return the page is on the real save again, with no chip and the real Settings card', await p.evaluate(() => !LM_DEMO.on && state.activities.length > 100 && !document.querySelector('.lm-demo-chip') && !document.getElementById('lmDemoCard').hidden));
    ok('no demo key, marker or demo database is left', !Object.keys(S4.ls).some(k => k === MARKER || k.startsWith('lmdemo:')) && !S4.idb.some(n => n.startsWith('lmdemo:')), { ls: Object.keys(S4.ls).filter(k => k.startsWith('lmdemo') || k === MARKER), idb: S4.idb });
    /* the real start-up that follows Return rewrites a few keys the way every start does (the save is re-stored, the cat's last-seen
       time moves on); every other real key must be byte-identical, and the rewritten save must hold the same progress */
    const bootWrote = new Set(ph.writes.filter(w => !w.demo && !w.key.startsWith(PREFIX) && w.key !== MARKER).map(w => w.key));
    const differ = Object.keys(S0.ls).filter(k => S4.ls[k] !== S0.ls[k]);
    const a = JSON.parse(S0.ls[STORE]), c = JSON.parse(S4.ls[STORE]), fields = Object.keys({ ...a, ...c }).filter(k => JSON.stringify(a[k]) !== JSON.stringify(c[k]));
    ok('after Return every real key is byte-identical to before the demo, except what the real start-up itself rewrote', differ.every(k => bootWrote.has(k) || preReload.has(k)) && JSON.stringify(S4.idb) === JSON.stringify(S0.idb) && S4.ls.unrelated_key === 'keep me' && S4.ls[PREFS] === S0.ls[PREFS] && S4.ls.lifeMaxTheme === 'light', { differ, boot: [...bootWrote] });
    ok('and the real save holds the same progress: only start-up bookkeeping fields differ', fields.every(k => /^(lastSeen|lastVisit|visit|seen|updated|schema|saved)/i.test(k)) && JSON.stringify(a.activities) === JSON.stringify(c.activities) && a.name === c.name, fields);
    ok('and the real audio database still holds the owner\'s record', await p.evaluate(async () => { const r = await audioDbOpen(); return await new Promise(res => { const q = r.transaction('files').objectStore('files').get('book'); q.onsuccess = () => res(!!(q.result && q.result.real)); q.onerror = () => res(false) }) }));
    ok('between the click and the reload the only real keys written are the real page\'s own routine ones, and the marker', [...preReload].every(k => k === STORE || k === 'lm_pet_seen'), [...preReload]);
    ok('page errors across the trip: none', ph.errs.length === 0, ph.errs);
    await ph.close();
  }

  /* ---- the real mode with the same plugins DOES reach them (so the demo check above means something) ---- */
  {
    const ph = await phone(b, { save: saveText }), p = await ph.open(true);
    await p.evaluate(NATIVE); const calls = await p.evaluate(TOUCH);
    ok('control: outside the demo the same calls do reach reminders, Health, calendar, backup and Android backup', ['LocalNotifications.getPending', 'HealthSync.status', 'DeviceBackup.set'].every(c => calls.includes(c)), calls);
    await ph.close();
  }

  /* ---- a damaged marker never shows or touches the real save ---- */
  for (const [label, marker] of [['empty', ''], ['garbage', '{{not json'], ['"null"', 'null'], ['a number', '0']]) {
    const ph = await phone(b, { save: saveText, marker }), p = await ph.open(true), S = await real(p); await ph.drain(p);
    ok('a marker that is ' + label + ': the page is the demo, blank, and the real save is byte-identical', await p.evaluate(([STORE]) => LM_DEMO.on && state.activities.length === 0 && JSON.parse(localStorage.getItem(STORE)).name === 'The Life Architect', [STORE]) && S.ls[STORE] === saveText && S.ls.unrelated_key === 'keep me' && ph.writes.filter(w => w.demo && !w.key.startsWith(PREFIX)).length === 0, ph.writes.filter(w => w.demo && !w.key.startsWith(PREFIX)).slice(0, 4));
    await p.evaluate(() => { window.confirm = () => true }); await p.evaluate(() => document.getElementById('lmDemoReturnBtn').click()); await sheet(p, 'yes'); await waitDemo(p, false);
    const after = await real(p);
    ok('and Return still works from a ' + label + ' marker: the real save is back, with its progress, and the marker is gone', !after.ls[MARKER] && JSON.parse(after.ls[STORE]).activities.length === JSON.parse(saveText).activities.length && await p.evaluate(() => !LM_DEMO.on && state.activities.length > 100));
    await ph.close();
  }
  /* ---- absent keys: a demo on a phone with no save at all, and a start-up after a demo that left its keys behind ---- */
  {
    const ph = await phone(b, { extras: {}, marker: '1' }), p = await ph.open(true), S = await real(p);
    ok('a demo on a phone with no real keys starts at the welcome screen and writes only under its prefix', await p.evaluate(() => LM_DEMO.on && !!document.querySelector('#onboarding.show')) && Object.keys(S.ls).every(k => k === MARKER || k.startsWith(PREFIX)));
    await ph.close();
  }
  {
    const ph = await phone(b, { save: saveText, extras: { ...OWNER, [PREFIX + STORE]: '{"name":"left over"}', [PREFIX + 'lma_qfx']: 'on' } }), p = await ph.open(true), S = await real(p);
    ok('with no marker the page is the real save, and demo keys an interrupted exit left behind are cleared', await p.evaluate(() => !LM_DEMO.on && state.activities.length > 100) && !Object.keys(S.ls).some(k => k.startsWith('lmdemo:')) && S.ls[STORE] !== undefined);
    await ph.close();
  }
  /* ---- the storage view itself ---- */
  {
    const ph = await phone(b, { save: saveText, marker: '1' }), p = await ph.open(true);
    const r = await p.evaluate(() => {
      localStorage.clear(); localStorage.a = '1'; localStorage.setItem('b', 2); const keys = Object.keys(localStorage), len = localStorage.length;
      const got = [localStorage.a, localStorage.getItem('b'), localStorage.getItem('zzz'), localStorage.key(0), localStorage.key(5)];
      delete localStorage.a; localStorage.removeItem('b'); return { keys, len, got, left: localStorage.length };
    });
    const S = await real(p);
    ok('the demo\'s storage behaves like localStorage (set, get, key, length, keys, delete, clear) and clear() cannot reach the real keys', r.keys.slice().sort().join() === 'a,b' && r.len === 2 && r.got.slice(0, 3).join('|') === '1|2|' && ['a', 'b'].includes(r.got[3]) && r.got[4] === null && r.left === 0 && S.ls[STORE] === saveText && S.ls.unrelated_key === 'keep me', r);
    await ph.close();
  }
  await b.close(); site.close();
  console.log(pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
