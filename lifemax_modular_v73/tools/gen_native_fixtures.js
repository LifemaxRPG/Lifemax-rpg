/* Produces the projections the native screens are tested and screenshotted with, by running the web app's own projections:
   Food's NativeNutritionBeta.snapshot() on a fixed day with a fixed diary, and Life Director's tab snapshot on the lived-in
   day of tests/fixtures/director-day.js. Regenerate only when a projection changes:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js
   Output in android-wrapper/native/src/test/resources: food-{log,plan,shop}.json, fixtures/director-*.json */
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

/* Life Director on the lived-in Wednesday, each state reached through the page as a user reaches it:
   today      mid-afternoon: a past overlay, work under way, the evening ahead; tomorrow, the next day of the strip
   banner     a re-plan that could not be saved: the page's bad banner and its message
   timer      the evening session started early, 12 minutes 34 seconds ago
   soon       two minutes before that session: the dentist wrapping up, the session about to start
   late       a quarter of an hour into it, not started
   evening    late evening: that session done in part, the last one done, nothing doable before tomorrow
   unplanned  a new player's Life Director, which has no plan yet */
async function director(b) {
  const live = async (planned) => {
    const p = await page(b);
    await directorDay.seed(p, { planned });
    await p.goto('file://' + out);
    if (planned) await directorDay.play(p);
    else await p.clock.runFor(3000);
    await p.evaluate(() => lmaNavTo('life-director'));
    await p.clock.runFor(1500);
    return p;
  };
  const shoot = async (p, name) => {
    const data = await p.evaluate(() => ({ shows: LifeDirector.native.showing(), snapshot: Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }) }));
    if (data.snapshot.today && data.shows !== 'today') throw Error(name + ': Life Director shows ' + data.shows + ', not Today');
    write('fixtures/director-' + name + '.json', data.snapshot);
  };
  const act = (p, action, id) => p.evaluate(([a, i]) => { if (!LifeDirector.native.act(a, i)) throw Error(a + ' did nothing') }, [action, id]);
  const closeSheet = (p) => p.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-bg').click());
  const upNext = (p) => p.evaluate(() => LifeDirector.native.today(0).upNext.actions[0].id);
  const until = (p, hm) => p.clock.pauseAt(directorDay.at(hm));

  let p = await live(true);
  await shoot(p, 'today');
  if (!(await p.evaluate(() => NativeDirectorProjection.commands.day({ index: 1 }).ok))) throw Error('no second day on the strip');
  await shoot(p, 'tomorrow');

  p = await live(true);
  await p.evaluate(() => {
    const real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) };
  });
  await act(p, 'replan');
  await shoot(p, 'banner');

  p = await live(true);
  await act(p, 'focusStart', await upNext(p));
  await closeSheet(p);
  await p.clock.runFor((12 * 60 + 34) * 1000);
  await shoot(p, 'timer');

  p = await live(true);
  const evening = await upNext(p);
  await until(p, '19:13');
  await shoot(p, 'soon');
  await until(p, '19:30');
  await shoot(p, 'late');
  await until(p, '20:00');
  await act(p, 'inst', evening);
  await p.evaluate(() => { document.querySelector('#ldDoneMin').value = '20'; document.querySelector('#view-life-director .ld-sheet.open [data-act=done]').click() });
  await until(p, '21:00');
  await p.evaluate((day) => {
    const last = LifeDirector.voice.sessions(day).find((s) => s.title === 'Focused Reading');
    if (!last || !LifeDirector.voice.completeSession(last.id).ok) throw Error('no last session to complete');
  }, directorDay.DAY);
  await until(p, '22:40');
  await shoot(p, 'evening');

  await shoot(await live(false), 'unplanned');
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  await food(b);
  await director(b);
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
