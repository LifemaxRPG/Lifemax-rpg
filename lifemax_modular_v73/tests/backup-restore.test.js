/* The save and its backups as a tested contract (ADR 0004). Golden saves in fixtures/saves (new, established, legacy, with extras;
   synthetic, made once from the page's own functions and never regenerated) load at SAVE_SCHEMA; a save from a newer build is
   shown but never written; a backup counts only once the file read back from the folder checks out; a snapshot comes before every
   risky change; and each golden save, backed up and restored on another phone, comes back the same and stays so once the app starts.
   Run: NODE_PATH=$(npm root -g) node tests/backup-restore.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const currentPage = require('./current-page'), file = currentPage(process.argv[2]), root = path.resolve(__dirname, '..');
const STORE = 'lifemax_rpg_prototype_v1', fixture = n => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', n + '.json'), 'utf8'));
let url = '', pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
/* A phone: a fresh browser profile holding a save and extra keys before the app starts (block: a key that does not fit). */
async function phone(b, { save = JSON.stringify({ onboardingComplete: true, name: 'N', tutorialDone: true }), extras = {}, block = '' } = {}) {
  const c = await b.newContext({ viewport: { width: 412, height: 860 } }), p = await c.newPage(), dialogs = [], errs = [];
  p.on('pageerror', e => errs.push(String(e))); p.on('dialog', d => { dialogs.push(d.message()); d.accept() });
  await p.addInitScript(([STORE, save, extras, block]) => {
    if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
    localStorage.setItem(STORE, save); for (const k in extras) localStorage.setItem(k, extras[k]);
    if (block) { const set = Storage.prototype.setItem; Storage.prototype.setItem = function (k, v) { if (String(k).includes(block)) throw new Error('QuotaExceededError'); return set.call(this, k, v) } }
  }, [STORE, save, extras, block]);
  await p.goto(url, { timeout: 120000 }); await p.waitForTimeout(4500);
  return { p, dialogs, errs, close: () => c.close() };
}
/* An Android backup folder in memory, installed once the app has started: the files, the plugin calls in order and the folders they
   went to, a way to spoil what is read back, a promise that holds a file while it is written, and another folder to pick. */
const FOLDER = `(() => {
  const F = window.__folder = { files: {}, calls: [], uris: [], n: 0, spoil: null, hold: null }, real = lmaPlugin;
  lmaNative = () => true;
  lmaPlugin = n => n !== 'BackupFolder' ? real(n) : {
    pickFolder: async () => ({ picked: true, uri: 'content://tree/other', name: 'Other' }),
    createBackup: async o => { F.calls.push('create ' + o.name); F.uris.push(o.uri); await F.hold; const file = 'file' + ++F.n; F.files[file] = o.text; return { file, name: o.name } },
    readBackup: async o => { F.calls.push('read'); return { text: F.spoil ? F.spoil(F.files[o.file]) : F.files[o.file] } },
    pruneBackups: async o => { F.calls.push('prune ' + o.prefix + ' ' + o.keep); F.uris.push(o.uri); return { pruned: 0 } },
    deleteBackup: async o => { F.calls.push('delete'); delete F.files[o.file]; return { deleted: true } } };
  Object.assign(lmaLoad().backup, { on: true, uri: 'content://tree/backups', folder: 'Backups' }); lmaSave();
})()`;
(async () => {
  const site = await currentPage.serve(file); url = site.url;
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }), errs = [];

  /* ---- the save format version ---- */
  const segs = fs.readdirSync(path.join(root, 'src', 'runtime', 'segments')).map(f => fs.readFileSync(path.join(root, 'src', 'runtime', 'segments', f), 'utf8')).join('\n');
  ok('the core save has one writer: every other write goes through writeSave()', (segs.match(/localStorage\.setItem\(STORE,/g) || []).length === 1);

  for (const name of ['legacy', 'established']) {
    const f = fixture(name), ph = await phone(b, { save: JSON.stringify(f.state) });
    const r = await ph.p.evaluate(ids => { const s = JSON.parse(localStorage.getItem(STORE)); return { schema: state.schema, stored: s.schema, flags: !!(s.xpSchemaV2 && s.mediaSeededV1), ids: JSON.stringify(s.activities.map(a => a.id)) === JSON.stringify(ids) } }, f.state.activities.map(a => a.id));
    ok('the ' + name + ' save, which has no format number, is brought up to the current format, keeping every activity', r.schema === 1 && r.stored === 1 && r.flags && r.ids, r);
    errs.push(...ph.errs); await ph.close();
  }

  {
    const raw = JSON.stringify({ ...fixture('established').state, schema: 2, futureField: { kept: true } }), ph = await phone(b, { save: raw });
    const r = await ph.p.evaluate(async raw => {
      const out = {}; window.toast = () => { };
      out.untouchedAtStart = localStorage.getItem(STORE) === raw;
      out.shown = state.activities.length === JSON.parse(raw).activities.length && state.futureField.kept === true && state.schema === 2;
      out.saveRefused = save() === false;
      const n = state.activities.length, rec = recordActivity({ type: 'reading', duration: '30', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' });
      out.logRefused = !rec.ok && /read-only/.test(rec.message) && state.activities.length === n;
      toggleDevMode(true); saveDayReflection(getToday(), 'not kept'); markVisit(); ovLayoutSave({ order: [], hidden: [] });
      out.untouchedAfter = localStorage.getItem(STORE) === raw;
      out.backupIsStored = JSON.stringify(JSON.parse(backupJson()).state) === raw;
      const imp = await applyBackup({ state: { ...JSON.parse(raw), schema: 3 } });
      out.newerImportRefused = !imp.ok && /newer version/.test(imp.message) && localStorage.getItem(STORE) === raw;
      return out;
    }, raw);
    await ph.p.waitForTimeout(500);
    ok('a save from a newer build is shown and never written: save(), the Log, the light writers and the start-up writes all leave it as it was', r.untouchedAtStart && r.shown && r.saveRefused && r.logRefused && r.untouchedAfter, r);
    ok('the owner is told once, in plain words', ph.dialogs.length === 1 && /newer version of LifeMax/.test(ph.dialogs[0]), ph.dialogs);
    ok('a backup taken meanwhile holds the save as stored, and a backup from a newer build is not imported', r.backupIsStored && r.newerImportRefused, r);
    const w = await ph.p.evaluate(async ([raw, file]) => { window.confirm = () => true; const r = await applyBackup(file); const safety = JSON.parse(localStorage.getItem(STORE + '_safety')); return { ok: r.ok, safety: safety.save === raw, writable: save() === true, schema: JSON.parse(localStorage.getItem(STORE)).schema } }, [raw, fixture('new')]);
    ok('the way out is the owner\'s own choice: a confirmed import replaces it, keeps it as the safety copy, and saving works again', w.ok && w.safety && w.writable && w.schema === 1, w);
    errs.push(...ph.errs); await ph.close();
  }

  {
    const raw = '{"name":"Real hero","activities":[', ph = await phone(b, { save: raw, block: '_unreadable' });
    await ph.p.evaluate(FOLDER);
    const r = await ph.p.evaluate(async raw => {
      const out = { kept: localStorage.getItem(STORE) === raw, fresh: state.name !== 'Real hero', refused: save() === false }; window.toast = () => { };
      out.backup = await lmaRunBackup(false) === false && lmaLoad().backup.err === 'the save on this phone could not be read' && !__folder.calls.length;
      let file = null; lmaDownload = (text, name) => { file = { text, name } }; exportData();
      out.exported = file && file.text === raw && /^life-max-unreadable-\d{4}-\d\d-\d\d\.json$/.test(file.name);
      return out;
    }, raw);
    ok('an unreadable save that cannot be copied aside is left as it is, and nothing is saved over it', r.kept && r.fresh && r.refused && ph.dialogs.length === 1 && /could not be read/.test(ph.dialogs[0]), { r, d: ph.dialogs });
    ok('no backup is made of it, which Settings says in plain words, and Export backup keeps its text as a file, as the owner is told', r.backup && r.exported && /Export backup/.test(ph.dialogs[0]), { r, d: ph.dialogs });
    errs.push(...ph.errs); await ph.close();
  }

  {
    const ph = await phone(b, { save: JSON.stringify(fixture('established').state) });
    const r = await ph.p.evaluate(async fresh => {
      const out = {}, said = [], set = Storage.prototype.setItem, stored = localStorage.getItem(STORE);
      window.toast = () => { }; window.confirm = () => true; window.alert = m => said.push(m);
      Storage.prototype.setItem = function (k, v) { if (k === STORE) throw new Error('QuotaExceededError'); return set.call(this, k, v) };
      const imp = await applyBackup(fresh);
      out.import = !imp.ok && /nothing was imported/.test(imp.message) && state.activities.length === 420;
      await resetDemo();
      out.reset = /nothing was reset/.test(said[0]) && state.activities.length === 420;
      Storage.prototype.setItem = set;
      out.untouched = localStorage.getItem(STORE) === stored;
      out.laterSaveKeepsIt = save() && JSON.parse(localStorage.getItem(STORE)).activities.length === 420;
      return out;
    }, fixture('new'));
    ok('an import or reset whose new save cannot be stored is not half done: the stored save stays, and is what the app shows and saves next', r.import && r.reset && r.untouched && r.laterSaveKeepsIt, r);
    errs.push(...ph.errs); await ph.close();
  }

  /* ---- verified backups ---- */
  {
    const ph = await phone(b, { save: JSON.stringify(fixture('established').state) });
    await ph.p.evaluate(FOLDER);
    const r = await ph.p.evaluate(async () => {
      const F = __folder, out = {}; window.toast = () => { };
      const data = backupData(), text = JSON.stringify(data, null, 2), want = backupFacts(data, text.length), fewer = JSON.parse(text);
      fewer.state.activities.pop();
      const as = d => backupProblem(JSON.stringify(d, null, 2), want);
      out.problems = { good: backupProblem(text, want), cut: backupProblem(text.slice(0, -300), want), empty: backupProblem('', want), fewer: as(fewer), other: as({ ...data, exportedAt: '2020-01-01T00:00:00.000Z' }), schema: as({ ...data, state: { ...data.state, schema: 0 } }), rules: as({ ...data, rules: {} }), extras: as({ ...data, extras: {} }) };
      out.ran = await lmaRunBackup(true); out.calls = F.calls.splice(0);
      const good = { ...lmaLoad().backup };
      out.good = good.status === 'ok' && good.verified > 0 && good.last === good.verified && JSON.parse(Object.values(F.files)[0]).state.activities.length === state.activities.length;
      F.spoil = () => { throw new Error('Could not open the file') };
      out.ranUnread = await lmaRunBackup(true); out.unreadCalls = F.calls.splice(0);
      F.spoil = t => t.slice(0, t.length >> 1);
      out.ranSpoilt = await lmaRunBackup(true); out.spoiltCalls = F.calls.splice(0); F.spoil = null;
      const bad = lmaLoad().backup;
      out.spoilt = bad.status === 'error' && /cut short/.test(bad.err) && bad.verified === good.verified && Object.keys(F.files).length === 1;
      navigate('settings'); const text2 = () => { lmaRenderBackup(); return document.getElementById('lmaBakBody').textContent }, B = lmaLoad().backup;
      out.failing = text2(); B.status = 'ok'; B.err = '';
      out.fine = text2(); B.on = false; out.off = text2(); B.on = true;
      B.verified = Date.now() - 3 * 86400e3; out.stale = text2(); B.verified = 0; out.never = text2(); B.uri = ''; out.none = text2();
      // asked once, gently, on a page the owner can see
      B.asked = 0; lmaSave(); navigate('dashboard'); const pill = document.querySelector('.lmaweb-pill');
      out.asked = !!pill && /no backup/.test(pill.textContent) && lmaLoad().backup.asked > 0;
      pill.querySelector('[data-w=go]').click(); out.toSettings = lmaView() === 'settings' && setCat === 'phone';
      navigate('dashboard'); out.once = !document.querySelector('.lmaweb-pill');
      // another folder picked while a backup to the old one is being written: that one finishes there, then the new one gets its own
      Object.assign(B, { uri: 'content://tree/backups', status: 'ok' }); F.calls.length = F.uris.length = 0;
      let release; F.hold = new Promise(r => { release = r });
      const old = lmaRunBackup(false); await lmaPickBackupFolder(); F.hold = null; release(); await old; await lmaBakRun;
      out.moved = F.uris.join(' ') === 'content://tree/backups content://tree/backups content://tree/other content://tree/other' && B.uri === 'content://tree/other' && B.status === 'ok' && B.verified > 0;
      // a backup whose preferences cannot be read
      lmaRestoreExtras({ [LMA_PREF_KEY]: '{"apps":[' }); out.prefsKept = lmaLoad().backup.uri === 'content://tree/other';
      return out;
    });
    const pr = r.problems;
    ok('the check passes the file as written and catches a cut-short, empty, mismatched or different file', pr.good === '' && /cut short/.test(pr.cut) && /cut short/.test(pr.empty) && /activities 419 instead of 420/.test(pr.fewer) && /exportedAt/.test(pr.other) && pr.schema === 'has schema 0 instead of 1' && /^has rules 0 instead of [1-9]/.test(pr.rules) && /^has extras 0 instead of [1-9]/.test(pr.extras), pr);
    ok('a backup is written, read back and checked, and only then are older files pruned', r.ran === true && r.good && /^create life-max-auto-\d{4}-\d\d-\d\d-\d{4}\.json$/.test(r.calls[0]) && r.calls[1] === 'read' && r.calls[2] === 'prune life-max-auto- 7' && r.calls.length === 3, r);
    ok('a file that reads back wrong, or cannot be read back, does not count: it is deleted, nothing is pruned, the last verified time stays', r.ranSpoilt === false && r.spoilt && JSON.stringify(r.spoiltCalls.slice(1)) === '["read","delete"]' && r.ranUnread === false && JSON.stringify(r.unreadCalls.slice(1)) === '["read","delete"]', r);
    ok('Settings shows the last verified backup, and warns when backups fail, are off, stale, never verified or have no folder', /The last backup failed: the file read back is cut short/.test(r.failing) && /Last verified backupjust now/.test(r.fine) && !/failed|is off|was made|No backup/.test(r.fine) && /Automatic backup is off/.test(r.off) && /last verified backup was made 3 days ago/.test(r.stale) && /No backup has been verified yet/.test(r.never) && /No backup folder is chosen/.test(r.none), r);
    ok('with no folder the owner is asked once, gently, and the pill leads to the backup settings', r.asked && r.toSettings && r.once, r);
    ok('a folder picked while a backup is being written gets its own backup, and the old one is pruned only in the old folder', r.moved, r);
    ok('a restore whose preferences cannot be read leaves this phone\'s preferences as they are', r.prefsKept, r);
    ok('nothing in the backup paths shows a dialog', ph.dialogs.length === 0, ph.dialogs);
    errs.push(...ph.errs); await ph.close();
  }

  /* ---- a verified snapshot before every risky change ---- */
  {
    const ph = await phone(b, { save: JSON.stringify(fixture('established').state) });
    await ph.p.evaluate(FOLDER);
    const r = await ph.p.evaluate(async ([fresh, est]) => {
      const F = __folder, out = {}, asks = [], toasts = [], cut = t => t.slice(0, t.length >> 1); let answer = true;
      window.toast = m => toasts.push(m); window.confirm = m => { asks.push(m); return /verified backup/.test(m) ? answer : true };
      const snap = () => JSON.parse(F.files['file' + F.n]).state;
      // import, with a folder that works: the snapshot holds the save as it was, in its own rotation, and the schedule is not moved;
      // another import or a reset asked for while it waits for that snapshot is refused
      let release; F.hold = new Promise(r => { release = r });
      const first = applyBackup(fresh), during = applyBackup(est), resetting = resetDemo(); F.hold = null; release();
      let res = await first; const refused = await during; await resetting;
      out.oneAtATime = /already under way/.test(refused.message) && toasts.includes('An import or reset is already under way') && F.calls.length === 3;
      out.importSnap = res.ok && /^create life-max-before-import-/.test(F.calls[0]) && F.calls[2] === 'prune life-max-before-import- 5' && snap().activities.length === 420 && state.activities.length === 0 && lmaLoad().backup.last === 0 && lmaLoad().backup.verified > 0;
      // import, with a folder whose file reads back wrong: the owner is asked; no means nothing changes, yes goes on
      F.spoil = cut; answer = false; let before = localStorage.getItem(STORE), safety = localStorage.getItem(STORE + '_safety');
      res = await applyBackup(est);
      out.importDeclined = res.cancelled === true && localStorage.getItem(STORE) === before && localStorage.getItem(STORE + '_safety') === safety && /verified backup before this import: the file read back is cut short/.test(asks[asks.length - 1]);
      answer = true; res = await applyBackup(est);
      out.importAccepted = res.ok && state.activities.length === 420;
      // reset: the same rule
      answer = false; before = localStorage.getItem(STORE); await resetDemo();
      out.resetDeclined = localStorage.getItem(STORE) === before && state.activities.length === 420;
      F.spoil = null; F.calls.length = 0; await resetDemo();
      out.resetSnap = /^create life-max-before-reset-/.test(F.calls[0]) && F.calls[2] === 'prune life-max-before-reset- 5' && snap().activities.length === 420 && state.activities.length === 0;
      // a web update: unasked (at launch) a failed backup leaves it waiting; asked, the owner decides; a working backup comes first
      let staged = 0; const real = lmaPlugin;
      lmaPlugin = n => n === 'LiveWeb' ? { status: async () => ({ staged }), fetch: async o => { F.calls.push('fetch ' + o.build); staged = o.build }, restart: async () => { F.calls.push('restart') }, addListener() { } }
        : n === 'Updater' ? { install: async () => { F.calls.push('install') }, addListener() { } } : real(n);
      lmaUpd.info = { latest: 301, current: 300, apk: 'https://x/app.apk', webBuild: 301, webUrl: 'https://x/app.html', webSha: 'a'.repeat(64) }; lmaUpd.live = { staged: 0 };
      F.spoil = cut; F.calls.length = 0; const asked0 = asks.length;
      await lmaWebUpdate(false); navigate('settings'); lmaRenderUpdates();
      out.webWaits = !F.calls.some(c => /fetch/.test(c)) && lmaUpd.state === 'web' && asks.length === asked0 && /not downloaded, because the backup before it failed/.test(document.getElementById('lmaUpdBody').textContent);
      answer = false; await lmaWebUpdate(true);
      out.webDeclined = !F.calls.some(c => /fetch/.test(c)) && asks.length === asked0 + 1;
      F.spoil = null; F.calls.length = 0; await lmaWebUpdate(false);
      out.webOrder = /^create life-max-before-update-/.test(F.calls[0]) && F.calls[2] === 'prune life-max-before-update- 5' && F.calls[3] === 'fetch 301' && lmaUpd.state === 'webready';
      F.spoil = cut; F.calls.length = 0; const restarting = lmaWebRestart(), shown = lmaUpd.state; await restarting;
      out.restartDeclined = shown === 'backup' && !F.calls.includes('restart') && lmaUpd.state === 'webready';
      F.spoil = null; F.calls.length = 0; await lmaWebRestart();
      out.restartOrder = /^create life-max-before-update-/.test(F.calls[0]) && F.calls[3] === 'restart';
      F.spoil = cut; F.calls.length = 0; lmaUpd.state = 'available'; await lmaInstallUpdate();
      out.apkDeclined = !F.calls.includes('install') && lmaUpd.state === 'available';
      F.spoil = null; F.calls.length = 0; await lmaInstallUpdate();
      out.apkOrder = /^create life-max-before-update-/.test(F.calls[0]) && F.calls[3] === 'install';
      return out;
    }, [fixture('new'), fixture('established')]);
    ok('an import first takes a verified snapshot of the save it replaces, kept apart from the daily backups and the other snapshots', r.importSnap, r);
    ok('imports and resets run one at a time: another one asked for while an import waits for its snapshot is refused', r.oneAtATime, r);
    ok('when that snapshot fails, the owner is asked: no leaves the save and its safety copy untouched, yes goes on', r.importDeclined && r.importAccepted, r);
    ok('a reset follows the same rule', r.resetDeclined && r.resetSnap, r);
    ok('a web update is backed up before it is downloaded; at launch a failed backup leaves it waiting in Settings, asked the owner decides', r.webWaits && r.webDeclined && r.webOrder, r);
    ok('restarting into a web update and installing a new APK are backed up first too, and Settings shows the backup meanwhile', r.restartDeclined && r.restartOrder && r.apkDeclined && r.apkOrder, r);
    errs.push(...ph.errs); await ph.close();
  }

  /* ---- backup, restore on another phone, compare ---- */
  const canon = v => JSON.stringify(v, (k, x) => x && typeof x === 'object' && !Array.isArray(x) ? Object.fromEntries(Object.keys(x).sort().map(k => [k, x[k]])) : x);
  const differ = (a, b) => [...new Set([...Object.keys(a), ...Object.keys(b)])].filter(k => canon(a[k]) !== canon(b[k]));
  for (const name of ['new', 'established', 'legacy', 'extras']) {
    const f = fixture(name), ids = JSON.stringify(f.state.activities.map(a => a.id));
    const a = await phone(b, { save: JSON.stringify(f.state), extras: f.extras || {} });
    await a.p.evaluate(FOLDER);
    const made = await a.p.evaluate(async f => {
      window.toast = () => { }; window.confirm = () => true;
      const ran = await lmaRunBackup(false), file = __folder.files.file1, imp = await applyBackup(f);
      return { ran, file, imported: imp.ok && state.schema === SAVE_SCHEMA && JSON.stringify(state.activities.map(a => a.id)), folder: JSON.parse(localStorage.getItem(LMA_PREF_KEY)).backup.uri };
    }, f);
    ok('the ' + name + ' golden file imports at the current format with every activity in order, and this phone keeps its backup folder', made.imported === ids && made.folder === 'content://tree/backups', { imported: made.imported, folder: made.folder });
    const c = await phone(b);
    await c.p.evaluate(FOLDER);
    const res = await c.p.evaluate(async file => {
      window.toast = () => { }; window.confirm = () => true;
      const r = await applyBackup(JSON.parse(file));
      return { ok: r.ok, stored: JSON.parse(localStorage.getItem(STORE)), rules, extras: lmaBackupExtras() };
    }, made.file);
    const was = JSON.parse(made.file), stateDiff = differ(was.state, res.stored);
    ok('the ' + name + ' save, backed up and restored on another phone, comes back the same: save, rules and extras', made.ran && res.ok && !stateDiff.length && canon(was.rules) === canon(res.rules) && canon(was.extras) === canon(res.extras), { stateDiff, rules: canon(was.rules) === canon(res.rules), extras: differ(was.extras, res.extras) });
    // the app starts again on the restored save, as it does after an import
    await c.p.reload(); await c.p.waitForTimeout(4500);
    const back = await c.p.evaluate(async () => {
      const stored = JSON.parse(localStorage.getItem(STORE));
      const out = { schema: stored.schema, ids: JSON.stringify(stored.activities.map(a => a.id)), rules, folder: lmaLoad().backup.uri, shortcuts: lmaLoad().apps.length };
      // none of the restored app shortcuts is installed on this phone, so Android has no icon for them
      let icons = 0; const real = lmaPlugin; lmaNative = () => true; lmaPlugin = n => n === 'AppLauncher' ? { getIcons: async () => { icons++; return { icons: {} } } } : real(n);
      ovAppsRender(); await new Promise(r => setTimeout(r, 300));
      return { ...out, icons };
    });
    ok('after a start on the restored ' + name + ' save it is still there at the current format with its rules, and that phone keeps its own backup folder', back.schema === 1 && back.ids === ids && canon(back.rules) === canon(was.rules) && back.folder === 'content://tree/backups', { ...back, rules: canon(back.rules) === canon(was.rules) });
    if (back.shortcuts) ok('restored app shortcuts that are not installed here are asked for once, not forever', back.icons === 1, back);
    errs.push(...a.errs, ...c.errs); await a.close(); await c.close();
  }

  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); site.close(); process.exit(fail ? 1 : 0);
})();
