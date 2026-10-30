/* Produces the projections the native screens are tested and screenshotted with, by running the web app's own projections:
   Food's NativeNutritionBeta.snapshot() on a fixed day with a fixed diary, and Life Director's tab snapshot on the lived-in
   day of tests/fixtures/director-day.js. Regenerate only when a projection changes:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js
   Output in android-wrapper/native/src/test/resources: food-{log,plan,shop}.json, fixtures/director-{today,tomorrow}.json */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const directorDay = require('../tests/fixtures/director-day.js');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_fixture_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const target = path.resolve(root, '../android-wrapper/native/src/test/resources');
const page = async (b) => (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
function write(name, data) {
  fs.mkdirSync(path.dirname(path.join(target, name)), { recursive: true });
  fs.writeFileSync(path.join(target, name), JSON.stringify(data, null, 1) + '\n');
  console.log('wrote ' + name, JSON.stringify(data).length, 'bytes');
}

async function food(b) {
  const p = await page(b);
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
  for (const [pane, data] of Object.entries(shots)) {
    if (!data) throw new Error('no snapshot for ' + pane);
    write('food-' + pane + '.json', data);
  }
}

/* Today (Wednesday, mid-afternoon: a past overlay, work under way, the evening ahead) and Thursday, the next day of the strip */
async function director(b) {
  const p = await page(b);
  await directorDay.seed(p);
  await p.goto('file://' + out);
  await directorDay.play(p);
  await p.evaluate(() => lmaNavTo('life-director'));
  await p.clock.runFor(1500);
  const shots = await p.evaluate(() => {
    const shown = LifeDirector.native.showing();
    if (shown !== 'today') throw Error('Life Director shows ' + shown + ', not Today');
    const snapshot = () => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' });
    const today = snapshot();
    if (!NativeDirectorProjection.commands.day({ index: 1 }).ok) throw Error('no second day on the strip');
    return { today, tomorrow: snapshot() };
  });
  for (const [day, data] of Object.entries(shots)) write('fixtures/director-' + day + '.json', data);
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  await food(b);
  await director(b);
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
