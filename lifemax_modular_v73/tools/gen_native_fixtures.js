/* Produces the projections the native screens are tested and screenshotted with, by running the web app's own projections on
   a fixed day: Food (NativeNutritionBeta.snapshot() with a fixed diary) and Workout plan (NativePlanTabProjection.snapshot()
   after ticking sets with the page's own planToggle, planVal and planLog). Regenerate only when a projection changes:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js
   Output: android-wrapper/native/src/test/resources/food-{log,plan,shop}.json and fixtures/plan-{midweek,complete,scholar}.json */
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
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));localStorage.setItem('lma_gwr3d','off');");
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
  fs.mkdirSync(target, { recursive: true });
  for (const [pane, data] of Object.entries(shots)) {
    if (!data) throw new Error('no snapshot for ' + pane);
    fs.writeFileSync(path.join(target, 'food-' + pane + '.json'), JSON.stringify(data, null, 1) + '\n');
    console.log('wrote food-' + pane + '.json', JSON.stringify(data).length, 'bytes; pane', data.pane, 'kcal', data.totals && data.totals.kcal);
  }
  // Workout plan, today Wednesday 2026-10-28: the athlete foundation program mid-week and in week 3 with today done, and the
  // scholar program, joined on a Tuesday, on its rest day with a guided session paused
  const plans = await p.evaluate(() => {
    const begin = (classId, id, start, from) => { state.classId = classId; state.plan = { id, classId, start, from, checks: {} }; state.guidedWorkout = null; planSel = null; save() };
    const tick = (date, keys) => planDay(date).items.forEach(it => { if (!keys || keys.includes(it.k)) for (let i = 0; i < it.n; i++) planToggle(date, it.k, i, true) });
    const res = {};
    begin('athlete', 'athlete_foundation8', '2026-10-26', '2026-10-26');
    tick('2026-10-26'); planVal('2026-10-26', 'E0', '60 lb'); planLog('2026-10-26', 'E0', '3×10 @ 60 lb, felt solid');
    tick('2026-10-27', ['warm0', 'warm1']);
    tick('2026-10-28', ['warm0', 'warm1', 'warm2']); planToggle('2026-10-28', 'E0', 0, true); planToggle('2026-10-28', 'E0', 1, true); planVal('2026-10-28', 'E0', '65 lb');
    res.midweek = NativePlanTabProjection.snapshot();
    begin('athlete', 'athlete_foundation8', '2026-10-12', '2026-10-12');
    ['2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16', '2026-10-17', '2026-10-19', '2026-10-21', '2026-10-23', '2026-10-26', '2026-10-28'].forEach(d => tick(d));
    planVal('2026-10-21', 'E1', '65 lb'); planLog('2026-10-21', 'E1', 'Slow on the way down');
    planVal('2026-10-28', 'E1', '70 lb'); planLog('2026-10-28', 'E1', 'Felt strong, added 5 lb');
    res.complete = NativePlanTabProjection.snapshot();
    begin('scholar', 'scholar_light8', '2026-10-26', '2026-10-27');
    gwrStart('2026-10-27'); tick('2026-10-27', ['warm']); gwrPause();
    res.scholar = NativePlanTabProjection.snapshot();
    return res;
  });
  fs.mkdirSync(path.join(target, 'fixtures'), { recursive: true });
  for (const [name, data] of Object.entries(plans)) {
    Object.assign(data, { webSessionId: 'plan-fixture', sequence: 1, selected: Object.assign(data.selected, { revision: 1 }) });
    fs.writeFileSync(path.join(target, 'fixtures', 'plan-' + name + '.json'), JSON.stringify(data, null, 1) + '\n');
    console.log('wrote fixtures/plan-' + name + '.json', JSON.stringify(data).length, 'bytes;', data.program, '·', data.status, '·', data.summary);
  }
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
