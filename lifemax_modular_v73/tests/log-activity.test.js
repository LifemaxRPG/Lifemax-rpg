/* The Log activity logic as data. The page form and the native Log screen both go through activityPreview(), activityAward() and
   recordActivity(); 164 recorded cases (fixtures/log-activity-golden.json, recorded from the code before it was split from the DOM) must
   come out the same through the form and through a plain draft object, and the preview's award must be what Save then pays.
   Run: NODE_PATH=$(npm root -g) node tests/log-activity.test.js */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_log_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const golden = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'log-activity-golden.json'), 'utf8')).filter(x => !x.skipped);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 500) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + out); await p.waitForTimeout(2500);
  const r = await p.evaluate(g => {
    const pick = a => !a ? null : { type: a.type, name: a.name, icon: a.icon, duration: a.duration, quality: a.quality, skill: a.skill, primary: a.primary, secondary: a.secondary, xp: a.xp, notes: a.notes, xpSplit: a.xpSplit };
    const msgs = []; window.toast = m => msgs.push(String(m)); window.navigate = () => { };
    const set = (id, v) => { document.getElementById(id).value = v };
    const bad = { form: [], draft: [], preview: [], award: [] };
    g.forEach((x, i) => {
      const c = x.c;
      // 1. through the form
      state = freshState(); msgs.length = 0;
      set('activityType', c.type); set('customName', c.customName); set('duration', c.duration); set('quality', c.quality); set('skillName', c.skill); set('primaryAttr', c.primary); set('secondaryAttr', c.secondary); set('activityNotes', c.notes);
      const prevForm = activityPreview(activityDraftFromForm()); submitActivity({ preventDefault() { } });
      const aForm = state.activities[state.activities.length - 1];
      if (JSON.stringify({ prev: prevForm, toast: msgs, act: pick(aForm), attrXP: state.attrXP, skillXP: state.skillXP }) !== JSON.stringify({ prev: x.prev, toast: x.toast, act: x.act, attrXP: x.attrXP, skillXP: x.skillXP })) bad.form.push(i);
      // 2. through a plain draft (what the native screen sends)
      state = freshState(); msgs.length = 0;
      const prev = activityPreview(c), award = activityAward(c, true), res = recordActivity(c);
      const aDraft = state.activities[state.activities.length - 1];
      if (JSON.stringify(prev) !== JSON.stringify(x.prev)) bad.preview.push(i);
      if (award.xp !== (x.act ? x.act.xp : 0) || award.msg !== (x.saved ? '' : x.toast[0])) bad.award.push(i);
      if (JSON.stringify({ ok: res.ok, message: res.message, act: pick(aDraft), attrXP: state.attrXP, skillXP: state.skillXP }) !== JSON.stringify({ ok: x.saved, message: x.saved ? x.toast[0] : x.toast[0], act: x.act, attrXP: x.attrXP, skillXP: x.skillXP })) bad.draft.push(i);
    });
    return bad;
  }, golden);
  ok('through the page form, all ' + golden.length + ' recorded cases come out identical', r.form.length === 0, r.form.slice(0, 5));
  ok('the preview from a plain draft is identical', r.preview.length === 0, r.preview.slice(0, 5));
  ok('recording from a plain draft saves exactly what the form saves (fields, XP split, attribute and skill XP, message)', r.draft.length === 0, r.draft.slice(0, 5));
  ok('in every recorded case the previewed award is what was saved, and a refusal is previewed with its message', r.award.length === 0, r.award.slice(0, 5));
  const e = await p.evaluate(() => { state = freshState(); const x = recordActivity({ type: 'reading', duration: '2', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' }); return { ok: x.ok, n: state.activities.length, msg: x.message } });
  ok('a too-short session is refused with the same message and saves nothing', e.ok === false && e.n === 0 && /5 minutes/.test(e.msg), e);
  /* the preview tells the truth: on any day, what the web form and the native projection show is what the native Save then pays */
  const truth = await p.evaluate(() => {
    const seed = (type, xp, duration) => ({ id: 'seed-' + type + xp, type, name: type, icon: 'star', duration, quality: 1, skill: 'Seed', primary: 'discipline', secondary: '', xp, date: new Date().toISOString(), notes: '', xpSplit: [{ attr: 'discipline', xp }] });
    const draft = (type, duration, more) => Object.assign(NativeLogProjection.draftFor(type), { duration: String(duration) }, more);
    const fill = d => Object.entries({ activityType: d.type, customName: d.customName, duration: d.duration, quality: d.quality, skillName: d.skill, primaryAttr: d.primary, secondaryAttr: d.secondary, activityNotes: d.notes }).forEach(([id, v]) => { document.getElementById(id).value = v });
    const cases = {
      'fresh day': { d: draft('reading', 30) },
      'long session': { d: draft('strength', 150, { quality: '1.2' }) },
      'custom type': { d: draft('custom', 45, { customName: 'Pottery' }) },
      'repeat today': { seed: [seed('running', 30, 30), seed('running', 31, 30)], d: draft('running', 40) },
      'past the soft cap': { seed: [seed('business', 170, 60)], d: draft('reading', 60) },
      'reaching the cap': { seed: [seed('business', 225, 60)], d: draft('reading', 60) },
      rested: { state: { restedXP: 40 }, d: draft('language', 30) },
      'ascended attribute': { state: { attrStars: { physique: 2 } }, d: draft('strength', 60) },
      'scholar perk': { state: { perk: 'scholar', perksOwned: ['scholar'] }, d: draft('reading', 45) },
      'too short': { d: draft('reading', 3) },
      'fourth of a type': { seed: [seed('reading', 10, 30), seed('reading', 11, 30), seed('reading', 12, 30)], d: draft('reading', 30) },
      'active time used up': { seed: [seed('business', 20, 340)], d: draft('reading', 30) },
      'daily cap': { seed: [seed('business', 240, 60)], d: draft('reading', 30) },
    };
    const out = {};
    for (const [name, c] of Object.entries(cases)) {
      state = Object.assign(freshState(), c.state); state.activities.push(...(c.seed || []));
      const before = JSON.stringify(state), shown = NativeLogProjection.previewOf(c.d);
      fill(c.d); updatePreview();
      const web = { xp: Number(document.getElementById('previewXP').textContent), why: document.getElementById('previewWhy').textContent };
      const untouched = JSON.stringify(state) === before, n = state.activities.length;
      const res = NativeLogProjection.commands.submit({ draft: c.d, operationId: 'op-' + name });
      const saved = state.activities.length > n ? state.activities[state.activities.length - 1] : null;
      out[name] = { total: shown.total, award: shown.award, reason: shown.reason, web, untouched, ok: res.ok, xp: res.xp, message: res.message, saved: saved && saved.xp, split: saved && saved.xpSplit, shownSplit: shown.split, rested: state.restedXP };
    }
    // the same type again and again on a real day: each preview is what the save then pays, until the type is used up
    state = freshState(); out.sequence = [];
    for (let i = 0; i < 4; i++) {
      const d = draft('reading', 30), shown = NativeLogProjection.previewOf(d), res = NativeLogProjection.commands.submit({ draft: d, operationId: 'seq-' + i });
      out.sequence.push([shown.award, res.ok ? res.xp : res.message]);
    }
    return out;
  });
  const paid = (n, reason) => { const t = truth[n]; return t.untouched && t.ok && t.award === t.xp && t.award === t.saved && JSON.stringify(t.split) === JSON.stringify(t.shownSplit) && t.web.xp === t.award && t.web.why === t.reason && reason.test(t.reason) && t.message ==='Activity saved · +' + t.award + ' XP' };
  const refused = (n, message) => { const t = truth[n]; return t.untouched && !t.ok && t.award === 0 && t.reason === message && t.message === message && t.saved === null && t.web.xp === 0 && t.web.why === message };
  ok('fresh day, long session and custom type: the preview shows the raw total, and Save pays exactly that', ['fresh day', 'long session', 'custom type'].every(n => paid(n, /^$/) && truth[n].award === truth[n].total), truth);
  ok('the last log of a type today is previewed at half, with the reason, and pays half', paid('repeat today', /^Last of this type today: half XP$/) && truth['repeat today'].award < truth['repeat today'].total, truth['repeat today']);
  ok('past the daily soft cap and up to the hard cap: the reduced award and the reason are previewed, and paid', paid('past the soft cap', /^XP above 180 a day is halved$/) && paid('reaching the cap', /^Reaches the daily XP cap$/) && truth['reaching the cap'].award === 15, [truth['past the soft cap'], truth['reaching the cap']]);
  ok('rested XP: the preview shows the bonus without spending it; Save pays it and spends it', paid('rested', /^Rested \+12$/) && truth.rested.award === 36 && truth.rested.rested === 28, truth.rested);
  ok('an ascended attribute\'s star bonus is previewed, paid, and is what the save reports', paid('ascended attribute', /^Physique ★★ \+4$/) && truth['ascended attribute'].award === 60, truth['ascended attribute']);
  ok('a perk is previewed and paid', paid('scholar perk', /^Scholar’s Flow \+4$/) && truth['scholar perk'].award === 40, truth['scholar perk']);
  ok('a draft Save would refuse is previewed at 0 with the refusal Save gives, and nothing is saved', refused('too short', 'Log at least 5 minutes to earn XP') && refused('fourth of a type', 'Daily XP limit reached for this activity type') && refused('active time used up', 'Daily active-time limit (6 hours) reached') && refused('daily cap', 'Daily XP cap reached'), truth);
  ok('logging one type four times in a day: every preview matches what the save paid, then the refusal', JSON.stringify(truth.sequence) === JSON.stringify([[24, 24], [24, 24], [12, 12], [0, 'Daily XP limit reached for this activity type']]), truth.sequence);
  /* data safety: what a native Submit relies on */
  const safety = await p.evaluate(() => {
    const out = {}; const d = { type: 'reading', duration: '30', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' };
    // a replayed or double-tapped submit with the same operation id pays out once
    state = freshState(); const a = recordActivity({ ...d, opId: 'op-1' }), xp1 = JSON.stringify(state.attrXP), n1 = state.activities.length;
    const b = recordActivity({ ...d, opId: 'op-1' });
    out.once = a.ok && b.ok && b.duplicate === true && state.activities.length === n1 && JSON.stringify(state.attrXP) === xp1 && b.xp === a.xp;
    out.differentOpAdds = (recordActivity({ ...d, opId: 'op-2' }), state.activities.length === n1 + 1);
    out.noOpIdUnchanged = (state = freshState(), recordActivity(d), recordActivity(d), state.activities.length === 2 && !('opId' in state.activities[0]));
    // a write that fails is reported as a failure, not "saved"
    const real = Storage.prototype.setItem; Storage.prototype.setItem = function () { throw new Error('quota') };
    let r; try { state = freshState(); r = recordActivity(d) } finally { Storage.prototype.setItem = real }
    out.failure = r.ok === false && /Could not save/.test(r.message);
    out.saveReturns = (save() === true);
    // an unreadable save is copied aside before a blank profile is written over it
    localStorage.removeItem(STORE + '_unreadable'); localStorage.setItem(STORE, '{"name":"Real hero","activities":[');
    load(); out.stashed = localStorage.getItem(STORE + '_unreadable') === '{"name":"Real hero","activities":[';
    out.blankStarted = state.name !== 'Real hero';
    localStorage.setItem(STORE, '{"still":"broken'); load();
    out.firstCopyKept = localStorage.getItem(STORE + '_unreadable') === '{"name":"Real hero","activities":[';
    localStorage.removeItem(STORE + '_unreadable');
    return out;
  });
  ok('the same operation id pays out once; a different one is a new entry; no id behaves as before', safety.once && safety.differentOpAdds && safety.noOpIdUnchanged, safety);
  ok('a failed write is reported as a failure, and save() says whether it worked', safety.failure && safety.saveReturns, safety);
  ok('an unreadable save is copied aside before a blank profile is written, and the first copy is never replaced', safety.stashed && safety.blankStarted && safety.firstCopyKept, safety);
  /* a failed save is taken back out, so a retry really retries; and the whole-save replacements keep a safety copy */
  const more = await p.evaluate(() => {
    const out = {}, d = { type: 'reading', duration: '30', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' };
    const real = Storage.prototype.setItem; const realConfirm = window.confirm;
    state = freshState(); save(); const xp0 = JSON.stringify(state.attrXP);
    Storage.prototype.setItem = function () { throw new Error('quota') };
    let r1, r2; try { r1 = recordActivity({ ...d, opId: 'op-r' }); r2 = recordActivity({ ...d, opId: 'op-r' }) } finally { Storage.prototype.setItem = real }
    out.failedTwice = r1.ok === false && r2.ok === false && !r2.duplicate && state.activities.length === 0 && JSON.stringify(state.attrXP) === xp0;
    const r3 = recordActivity({ ...d, opId: 'op-r' });
    out.retrySaved = r3.ok === true && !r3.duplicate && state.activities.length === 1 && localStorage.getItem(STORE).includes('op-r');
    // import: refused file, cancelled import, confirmed import with a safety copy
    const backup = { app: 'Life Max RPG Prototype', version: 1, state: { ...freshState(), name: 'From file', activities: [{ id: 'x', type: 'reading', name: 'Read', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: '', xp: 10, date: new Date().toISOString(), notes: '' }] } };
    const before = localStorage.getItem(STORE); localStorage.removeItem(STORE + '_safety');
    out.badRejected = applyBackup({ state: { activities: {}, attrXP: {} } }).ok === false && localStorage.getItem(STORE) === before;
    window.confirm = () => false;
    const c = applyBackup(backup);
    out.cancelKeeps = c.ok === false && c.cancelled === true && localStorage.getItem(STORE) === before && !localStorage.getItem(STORE + '_safety');
    window.confirm = () => true;
    const ok = applyBackup(backup);
    const safety = JSON.parse(localStorage.getItem(STORE + '_safety') || 'null');
    out.importKeepsCopy = ok.ok === true && state.name === 'From file' && safety && safety.reason === 'import' && safety.save === before;
    // an empty profile is replaced without asking
    state = freshState(); save(); let asked = false; window.confirm = () => { asked = true; return true };
    out.emptyNoAsk = applyBackup(backup).ok === true && asked === false;
    // reset keeps a copy; delete removes the copies as it promises
    window.confirm = () => true; const beforeReset = localStorage.getItem(STORE); resetDemo();
    out.resetKeepsCopy = JSON.parse(localStorage.getItem(STORE + '_safety')).save === beforeReset;
    localStorage.setItem(STORE + '_unreadable', 'x'); deleteAll();
    out.deleteClears = !localStorage.getItem(STORE + '_safety') && !localStorage.getItem(STORE + '_unreadable');
    window.confirm = realConfirm;
    return out;
  });
  ok('a save that fails is taken back out: the retry with the same operation id saves instead of claiming a duplicate', more.failedTwice && more.retrySaved, more);
  ok('an invalid backup is refused and a cancelled import changes nothing', more.badRejected && more.cancelKeeps, more);
  ok('a confirmed import and a reset keep the previous save as a safety copy; an empty profile is replaced without asking', more.importKeepsCopy && more.emptyNoAsk && more.resetKeepsCopy, more);
  ok('delete all removes the safety and unreadable copies too, as its warning promises', more.deleteClears, more);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
