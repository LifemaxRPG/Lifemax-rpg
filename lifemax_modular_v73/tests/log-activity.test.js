/* The Log activity logic as data. The page form and the native Log screen both go through activityPreview() and recordActivity();
   164 recorded cases (fixtures/log-activity-golden.json, recorded from the code before it was split from the DOM) must come out the same
   through the form and through a plain draft object.
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
    const bad = { form: [], draft: [], preview: [] };
    g.forEach((x, i) => {
      const c = x.c;
      // 1. through the form
      state = freshState(); msgs.length = 0;
      set('activityType', c.type); set('customName', c.customName); set('duration', c.duration); set('quality', c.quality); set('skillName', c.skill); set('primaryAttr', c.primary); set('secondaryAttr', c.secondary); set('activityNotes', c.notes);
      const prevForm = calcPreview(); submitActivity({ preventDefault() { } });
      const aForm = state.activities[state.activities.length - 1];
      if (JSON.stringify({ prev: prevForm, toast: msgs, act: pick(aForm), attrXP: state.attrXP, skillXP: state.skillXP }) !== JSON.stringify({ prev: x.prev, toast: x.toast, act: x.act, attrXP: x.attrXP, skillXP: x.skillXP })) bad.form.push(i);
      // 2. through a plain draft (what the native screen sends)
      state = freshState(); msgs.length = 0;
      const prev = activityPreview(c), res = recordActivity(c);
      const aDraft = state.activities[state.activities.length - 1];
      if (JSON.stringify(prev) !== JSON.stringify(x.prev)) bad.preview.push(i);
      if (JSON.stringify({ ok: res.ok, message: res.message, act: pick(aDraft), attrXP: state.attrXP, skillXP: state.skillXP }) !== JSON.stringify({ ok: x.saved, message: x.saved ? x.toast[0] : x.toast[0], act: x.act, attrXP: x.attrXP, skillXP: x.skillXP })) bad.draft.push(i);
    });
    return bad;
  }, golden);
  ok('through the page form, all ' + golden.length + ' recorded cases come out identical', r.form.length === 0, r.form.slice(0, 5));
  ok('the preview from a plain draft is identical', r.preview.length === 0, r.preview.slice(0, 5));
  ok('recording from a plain draft saves exactly what the form saves (fields, XP split, attribute and skill XP, message)', r.draft.length === 0, r.draft.slice(0, 5));
  const e = await p.evaluate(() => { state = freshState(); const x = recordActivity({ type: 'reading', duration: '2', quality: '1', skill: '', primary: '', secondary: '', customName: '', notes: '' }); return { ok: x.ok, n: state.activities.length, msg: x.message } });
  ok('a too-short session is refused with the same message and saves nothing', e.ok === false && e.n === 0 && /5 minutes/.test(e.msg), e);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
