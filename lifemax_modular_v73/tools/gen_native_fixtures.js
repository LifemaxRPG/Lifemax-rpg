/* Produces the Food and Log projections the native screens are tested and screenshotted with, by running the web app's own
   NativeNutritionBeta.snapshot() and NativeLogProjection.snapshot() on a fixed day with a fixed diary. Regenerate only when a
   projection changes:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js
   Output: android-wrapper/native/src/test/resources/food-{log,plan,shop}.json and log-{default,strength,capped,refused}.json */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_fixture_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const target = path.resolve(root, '../android-wrapper/native/src/test/resources');
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage();
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + out); await p.waitForTimeout(2500);
  const shots = await p.evaluate(() => {
    const day = mealsDayKey(); mealsEnsure();
    state.foodLog = [
      { id: 'f1', date: day, slot: 'breakfast', name: 'Greek yogurt with berries', grams: 250, kcal: 210, p: 22, c: 24, f: 3 },
      { id: 'f2', date: day, slot: 'breakfast', name: 'Black coffee', grams: 240, kcal: 5, p: 0, c: 1, f: 0 },
      { id: 'f3', date: day, slot: 'lunch', name: 'Chicken burrito bowl', grams: 420, kcal: 640, p: 48, c: 62, f: 20 },
      { id: 'f4', date: day, slot: 'snack', name: 'Almonds', grams: 30, kcal: 174, p: 6, c: 6, f: 15 },
    ];
    state.waterLog = [{ id: 'w1', date: day, ml: 500 }, { id: 'w2', date: day, ml: 350 }];
    const res = {};
    for (const pane of ['log', 'plan', 'shop']) { fv2S().pane = pane; res[pane] = JSON.parse(JSON.stringify(NativeNutritionBeta.snapshot())); }
    return res;
  });
  const logs = await p.evaluate(() => {
    const L = NativeLogProjection;
    const at = (sequence) => Object.assign({ protocol: 1, webSessionId: 'log-fixture', sequence, tokens: NativeTabKit.tokens(), light: false }, JSON.parse(JSON.stringify(L.snapshot())));
    const res = { default: at(1) };
    L.setDraft(Object.assign(L.draftFor('strength'), { duration: '45', quality: '1.1', notes: 'Push day: bench 5x5' }));
    res.strength = at(2);
    // the same workout on a day that already earned 225 XP: only what is left under the daily cap is paid
    state.activities.push({ id: 'f-cap', type: 'business', name: 'Focused business work', icon: 'briefcase', duration: 180, quality: 1, skill: 'Entrepreneurship', primary: 'business', secondary: '', xp: 225, date: new Date().toISOString(), notes: '', xpSplit: [{ attr: 'business', xp: 225 }] });
    res.capped = at(3);
    // and after three workouts today: a fourth of the same type pays nothing, and the page says why
    for (const n of [1, 2, 3]) state.activities.push({ id: 'f-lift' + n, type: 'strength', name: 'Strength workout', icon: 'dumbbell', duration: 20, quality: 1, skill: 'Lifting', primary: 'physique', secondary: '', xp: 0, date: new Date().toISOString(), notes: '', xpSplit: [] });
    res.refused = at(4);
    return res;
  });
  fs.mkdirSync(target, { recursive: true });
  for (const [pane, data] of Object.entries(shots)) {
    if (!data) throw new Error('no snapshot for ' + pane);
    fs.writeFileSync(path.join(target, 'food-' + pane + '.json'), JSON.stringify(data, null, 1) + '\n');
    console.log('wrote food-' + pane + '.json', JSON.stringify(data).length, 'bytes; pane', data.pane, 'kcal', data.totals && data.totals.kcal);
  }
  for (const [name, data] of Object.entries(logs)) {
    fs.writeFileSync(path.join(target, 'log-' + name + '.json'), JSON.stringify(data, null, 1) + '\n');
    console.log('wrote log-' + name + '.json; preview', JSON.stringify(data.preview));
  }
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
