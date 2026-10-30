/* The save as a tested contract (ADR 0004). Golden saves in fixtures/saves (new, established, legacy, with extras; synthetic, made
   from the page's own functions) load at SAVE_SCHEMA, and a save from a newer build is shown but never written.
   Run: NODE_PATH=$(npm root -g) node tests/backup-restore.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_backup_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const STORE = 'lifemax_rpg_prototype_v1', fixture = n => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', n + '.json'), 'utf8'));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
/* A phone: a fresh browser profile holding a save and extra keys before the app starts (block: a key that does not fit). */
async function phone(b, { save = JSON.stringify({ onboardingComplete: true, name: 'N', tutorialDone: true }), extras = {}, block = '' } = {}) {
  const c = await b.newContext({ viewport: { width: 412, height: 860 } }), p = await c.newPage(), dialogs = [], errs = [];
  p.on('pageerror', e => errs.push(String(e))); p.on('dialog', d => { dialogs.push(d.message()); d.accept() });
  await p.addInitScript(([STORE, save, extras, block]) => {
    if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
    localStorage.setItem(STORE, save); for (const k in extras) localStorage.setItem(k, extras[k]);
    if (block) { const set = Storage.prototype.setItem; Storage.prototype.setItem = function (k, v) { if (String(k).includes(block)) throw new Error('QuotaExceededError'); return set.call(this, k, v) } }
  }, [STORE, save, extras, block]);
  await p.goto('file://' + out); await p.waitForTimeout(4500);
  return { p, dialogs, errs, close: () => c.close() };
}
(async () => {
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
    const r = await ph.p.evaluate(raw => {
      const out = {}; window.toast = () => { };
      out.untouchedAtStart = localStorage.getItem(STORE) === raw;
      out.shown = state.activities.length === JSON.parse(raw).activities.length && state.futureField.kept === true && state.schema === 2;
      out.saveRefused = save() === false;
      const n = state.activities.length, rec = recordActivity({ type: 'reading', duration: '30', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' });
      out.logRefused = !rec.ok && /read-only/.test(rec.message) && state.activities.length === n;
      toggleDevMode(true); saveDayReflection(getToday(), 'not kept'); markVisit(); ovLayoutSave({ order: [], hidden: [] });
      out.untouchedAfter = localStorage.getItem(STORE) === raw;
      out.backupIsStored = JSON.stringify(JSON.parse(backupJson()).state) === raw;
      const imp = applyBackup({ state: { ...JSON.parse(raw), schema: 3 } });
      out.newerImportRefused = !imp.ok && /newer version/.test(imp.message) && localStorage.getItem(STORE) === raw;
      return out;
    }, raw);
    await ph.p.waitForTimeout(500);
    ok('a save from a newer build is shown and never written: save(), the Log, the light writers and the start-up writes all leave it as it was', r.untouchedAtStart && r.shown && r.saveRefused && r.logRefused && r.untouchedAfter, r);
    ok('the owner is told once, in plain words', ph.dialogs.length === 1 && /newer version of LifeMax/.test(ph.dialogs[0]), ph.dialogs);
    ok('a backup taken meanwhile holds the save as stored, and a backup from a newer build is not imported', r.backupIsStored && r.newerImportRefused, r);
    const w = await ph.p.evaluate(([raw, file]) => { window.confirm = () => true; const r = applyBackup(file); const safety = JSON.parse(localStorage.getItem(STORE + '_safety')); return { ok: r.ok, safety: safety.save === raw, writable: save() === true, schema: JSON.parse(localStorage.getItem(STORE)).schema } }, [raw, fixture('new')]);
    ok('the way out is the owner\'s own choice: a confirmed import replaces it, keeps it as the safety copy, and saving works again', w.ok && w.safety && w.writable && w.schema === 1, w);
    errs.push(...ph.errs); await ph.close();
  }

  {
    const ph = await phone(b, { save: '{"name":"Real hero","activities":[', block: '_unreadable' });
    const r = await ph.p.evaluate(() => ({ kept: localStorage.getItem(STORE) === '{"name":"Real hero","activities":[', fresh: state.name !== 'Real hero', refused: save() === false }));
    ok('an unreadable save that cannot be copied aside is left as it is, and nothing is saved over it', r.kept && r.fresh && r.refused && ph.dialogs.length === 1 && /could not be read/.test(ph.dialogs[0]), { r, d: ph.dialogs });
    errs.push(...ph.errs); await ph.close();
  }

  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
