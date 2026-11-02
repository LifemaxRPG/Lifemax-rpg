/* Produces the projections the native screens are tested and screenshotted with, by running the web app's own projections:
   Food's NativeNutritionBeta.snapshot() on a fixed day with a fixed diary, Log's NativeLogProjection.snapshot() for a few drafts
   on that day, the Workout plan's NativePlanTabProjection.snapshot() after ticking sets with the page's own planToggle, planVal
   and planLog, Life Director's tab snapshot on the lived-in day of tests/fixtures/director-day.js, what the Recipes tab opens its
   screen with, the Library's Media pane (NativeLibraryTabProjection.snapshot()) with a list built through the page's own
   mediaUpsert and attachAudioFile, Settings (NativeSettingsTabProjection.snapshot()) on a phone as Android shows it to the
   page, changed through the screen's own commands, and the native tab swipe's feel and state. Regenerate only when a projection
   changes, all of them or the ones named:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js [food log plan director recipes media settings swipe overview glance journal planrunner]
   Output in android-wrapper/native/src/test/resources: food-{log,plan,shop}.json, log-{default,strength,capped,refused}.json,
   fixtures/plan-{midweek,complete,scholar}.json, fixtures/director-*.json (with director-replies.json, director-replies-today.json and
   director-replies-time.json; phase 4B's in tools/gen_director_time_fixtures.js), fixtures/recipes-*.json,
   fixtures/media-{full,search,empty,tip,sheets}.json, fixtures/media-filter-parity.json, fixtures/settings-{default,many,light,bar}.json
   fixtures/swipe-{tune,state}.json, the Overview cards' fixtures/overview-*.json (tools/gen_overview_fixtures.js), and more states of Life Director's glance,
   fixtures/director-glance-*.json (tools/gen_director_glance_fixtures.js), and the native Journal's fixtures/journal-*.json
   (tools/gen_journal_fixtures.js) */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const directorDay = require('../tests/fixtures/director-day.js');
const settingsAndroid = require('../tests/fixtures/settings-android.js');
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

/* The native tab swipe, which belongs to the shell rather than a screen:
   swipe-tune  the page's LMA_TABSWIPE, which native's defaults (SwipeTune.kt) must equal (SwipeTuneTest)
   swipe-state the swipe field of the bar scenes two native screens open with, every native tab on and the bar as it comes:
               Character's navScene (NativeTabKit) and Food's chromeScene, as the page sends them (SwipeStateTest)
   Fake plugins keep the scene each screen is opened with. */
async function swipe(b) {
  const p = await page(b);
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    window.__opened={};
    const caps=['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v1','recipes.projection.v1','library.media.projection.v1','shell.swipe.v1'];
    const fake=name=>{const ls={};return {addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__opened[name]=JSON.parse(JSON.stringify(a.navScene||a.chromeScene))},update:async()=>{},swipeSettle:async()=>{},
      close:async a=>{(ls.closed||[]).forEach(f=>f({session:a&&a.session}))},
      handshake:async()=>({protocol:1,capabilities:caps}),capabilities:async()=>({version:1,authoritativeData:true,swipe:1})}};
    const P={};for(const n of ['NativeCharacter','NativeNutrition','NativeLog','NativePlan','NativeLifeDirector','NativeRecipes','NativeMedia','NativeCash'])P[n]=fake(n);
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`);
  await p.goto('file://' + out); await p.waitForTimeout(2500);
  const { tune, state } = await p.evaluate(async () => {
    const wait = (ms) => new Promise((r) => setTimeout(r, ms));
    const scene = async (tab, plugin) => {
      delete window.__opened[plugin];
      lmaNavTo(tab, { fast: true });
      for (let t = 0; t < 8000 && !window.__opened[plugin]; t += 50) await wait(50);
      const swipe = window.__opened[plugin]?.swipe;
      if (!swipe || !swipe.on) throw Error(tab + ' opened without a swipe that is on: ' + JSON.stringify(swipe));
      return swipe;
    };
    lmaThumbWarm = async () => { }; lmaThumbWarmBusy = false;
    Object.assign(lmaLoad(), { nativeOverview: false, nativeCharacter: true, nativeNutrition: true, nativeLog: true, nativePlan: true, nativeLibrary: true, nativeRecipes: true, nativeDirector: true, nativeSwipe: true });
    lmaSave();
    return { tune: LMA_TABSWIPE, state: { character: await scene('character', 'NativeCharacter'), meals: await scene('food', 'NativeNutrition') } };
  });
  write('fixtures/swipe-tune.json', tune);
  write('fixtures/swipe-state.json', state);
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
  // the add-food sheet, as the screen drives it: the recent tab, a search, a food's portion view (at 175 g, for dinner), quick
  // calories, and the message the page sends after a log (with Undo). Without the plan and the shopping list, which it does not draw.
  const sheets = await p.evaluate(async () => {
    const B = NativeNutritionBeta, wait = (ms) => new Promise((r) => setTimeout(r, ms));
    const snap = () => { const d = JSON.parse(JSON.stringify(B.snapshot())); d.plan = null; d.shop = null; return d; };
    const res = {};
    fv2S().pane = 'log'; state.mealsMeta.calGoal = 2500;
    B.sheetAct('sheetOpen', { slot: 'lunch' }); res.search = snap();
    B.sheetAct('sheetQuery', { q: 'chicken breast' }); await wait(600); res.results = snap();
    const row = res.results.sheet.rows[0];
    B.sheetAct('sheetPick', { index: row.index, id: row.id }); B.sheetAct('sheetGrams', { grams: 175 }); B.sheetAct('sheetSlot', { slot: 'dinner' }); res.portion = snap();
    B.sheetAct('sheetLog'); res.notice = snap();
    B.sheetAct('sheetOpen', { slot: 'lunch' }); B.sheetAct('sheetQuick'); res.quick = snap();
    // phase 2: the create form, the daily targets after a preset, an entry of yesterday being edited, and a write's answer
    B.sheetAct('sheetOpen', { slot: 'dinner', forms: true }); B.sheetAct('sheetCreate'); res.create = snap();
    B.sheetAct('sheetGoal'); B.sheetAct('sheetGoalPreset', { preset: 'hi' }); res.goal = snap();
    const yest = fv2DayShift(mealsDayKey(), -1), rice = fv2LocalFoods(fv2SearchTokens('rice'))[0];
    state.foodLog.push(Object.assign({ id: 'f9', date: yest, slot: 'dinner', foodId: rice.id, name: rice.n, grams: 150 }, scaleFood(rice, 150)));
    mealsDay = yest; // the entry is tapped on its own day, as the screen shows it
    B.sheetAct('sheetEntry', { id: 'f9' }); res.edit = snap();
    B.sheetAct('sheetEditDelete', { op: 'op-1', id: 'f9' }); res.answer = snap();
    mealsDay = getToday();
    for (const k of ['create', 'goal', 'edit']) res[k].notice = null; // the log's message has long gone by then
    B.sheetAct('sheetClose');
    return res;
  });
  for (const [name, data] of Object.entries(sheets)) write('food-sheet-' + name + '.json', data);
}

/* Log on the same day: the default draft, a strength workout, that workout under the daily cap, and refused as a fourth of its type */
async function log(b) {
  const p = await page(b);
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + out); await p.waitForTimeout(2500);
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
  for (const [name, data] of Object.entries(logs)) write('log-' + name + '.json', data);
}

/* Workout plan, today Wednesday 2026-10-28: the athlete foundation program mid-week and in week 3 with today done, and the
   scholar program, joined on a Tuesday, on its rest day with a guided session paused */
async function plan(b) {
  const p = await page(b);
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));localStorage.setItem('lma_gwr3d','off');");
  await p.goto('file://' + out); await p.waitForTimeout(2500);
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
  for (const [name, data] of Object.entries(plans)) {
    Object.assign(data, { webSessionId: 'plan-fixture', sequence: 1, selected: Object.assign(data.selected, { revision: 1 }) });
    write('fixtures/plan-' + name + '.json', data);
  }
}

/* Life Director on the lived-in Wednesday, each state reached through the page as a user reaches it:
   today      mid-afternoon: a past overlay, work under way, the evening ahead; tomorrow, the next day of the strip
   banner     a re-plan that could not be saved: the page's bad banner and its message
   timer      the evening session started early, 12 minutes 34 seconds ago
   soon       two minutes before that session: the dentist wrapping up, the session about to start
   late       a quarter of an hour into it, not started
   evening    late evening: that session done in part, the last one done, nothing doable before tomorrow
   unplanned  a new player's Life Director, which has no plan yet
   build      mid-afternoon, just after the bedtime handle was dropped at 10:30 PM and Change it ran: the handle where it was
              dropped and the repair's build
   replan     just after Re-plan from now: the full build, with its sweep
   reduced    the same Change it with reduced motion: the frame says so and carries no build
   skip       just after the next session was skipped from its sheet: a repair whose build flashes that session (flips)
   and director-replies.json: what the page answers each of the handles' commands (dialDrop, dialApply), in the envelope
   NativeTabKit sends them in */
async function director(b) {
  let last = null; // one page at a time: each holds a whole app
  const live = async (planned) => {
    if (last) await last.context().close();
    const p = last = await page(b);
    await directorDay.seed(p, { planned });
    await p.goto('file://' + out, { timeout: 120000 });
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

  /* the handles: each command answered by the page, as NativeTabKit sends the reply ({command, requestId, ...}) */
  p = await live(true);
  let request = 0;
  const cmd = (p, command, payload) => p.evaluate(([c, x]) => NativeDirectorProjection.commands[c](x), [command, payload]).then((reply) => Object.assign({ command, requestId: 'director-' + ++request }, reply));
  const key = (p, rule, end) => p.evaluate(([r, e]) => LifeDirector.native.today(0).dial.handles.find((h) => h.rule === r && h.end === e).key, [rule, end]);
  const bed = await key(p, 'sleep', 'start');
  const replies = {};
  replies.drop = await cmd(p, 'dialDrop', { key: bed, minute: 1350 });
  replies.dropWhereItWas = await cmd(p, 'dialDrop', { key: bed, minute: 1380 });
  replies.dropWithProblem = await cmd(p, 'dialDrop', { key: bed, minute: 240 });
  replies.dropPastMidnight = await cmd(p, 'dialDrop', { key: bed, minute: 60 });
  replies.dropStale = await cmd(p, 'dialDrop', { key: 'sleep|start|2026-10-28|1', minute: 1350 });
  replies.dropOffTheDial = await cmd(p, 'dialDrop', { key: bed, minute: 1352 });
  replies.step = await cmd(p, 'dialDrop', { key: await key(p, 'work', 'end'), step: 1 });
  replies.applyWithProblem = await cmd(p, 'dialApply', { key: bed, minute: 240 });
  replies.applyWhereItWas = await cmd(p, 'dialApply', { key: bed, minute: 1380 });
  replies.apply = await cmd(p, 'dialApply', { key: bed, minute: 1350 });
  replies.applyAgain = await cmd(p, 'dialApply', { key: bed, minute: 1350 });
  await shoot(p, 'build');
  await p.evaluate(() => {
    const real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) };
  });
  replies.applyNotSaved = await cmd(p, 'dialApply', { key: await key(p, 'sleep', 'start'), minute: 1320 });
  write('fixtures/director-replies.json', replies);

  p = await live(true);
  await act(p, 'replan');
  await shoot(p, 'replan');

  // phase 3A: Week and Quests, from a page whose screen says it draws them (a fake plugin answers the kit's warm handshake)
  const covered = async (caps) => {
    if (last) await last.context().close();
    const p = last = await page(b);
    await directorDay.seed(p, { planned: true });
    await p.addInitScript(`(() => { const ls = {}; window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector', getPlatform: () => 'android',
      registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativeLifeDirector: { addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
        handshake: async () => ({ protocol: 1, capabilities: ${JSON.stringify(caps)} }), open: async () => {}, update: async () => {}, close: async () => { (ls.closed || []).forEach(f => f({})) } } } } })()`);
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { }; lmaLoad().nativeOverview = false; setNativeDirectorEnabled(true) });
    await directorDay.play(p);
    await p.evaluate(() => lmaNavTo('life-director'));
    await p.clock.runFor(1500);
    return p;
  };
  p = await covered(['director.today.v2', 'director.week.v1', 'director.goals.v1']);
  for (const section of ['week', 'quests', 'goals', 'settings']) {
    await p.evaluate((s) => { if (!LifeDirector.native.section(s).ok) throw Error('no section ' + s) }, section);
    await p.clock.runFor(300);
    const snap = await p.evaluate(() => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }));
    if (!snap[section] || snap.today !== null || !snap.covers.includes(section)) throw Error(section + ' is not in the projection');
    write('fixtures/director-' + section + '.json', snap);
  }

  /* phase 4A: Today's sheets as a screen that draws them (director.sheets.v1) gets them, through the sheet command: a session now,
     later and past, the timer running and paused, a meal, Move and Something came up; and director-replies-today.json, what the
     page answers Something came up's commands (a repeat, a refusal, a refused save and the same operation once it clears) */
  p = await covered(['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1']);
  const ids = await p.evaluate(() => {
    const t0 = LifeDirector.native.today(0), t1 = LifeDirector.native.today(1);
    return { now: t0.upNext.actions.find((a) => a.act === 'focusStart').id, later: t1.rows.ahead.find((r) => r.act === 'inst').id,
      past: t0.rows.earlier.rows.find((r) => r.act === 'inst').id, meal: t0.rows.ahead.find((r) => r.act === 'meal').id };
  });
  const sheet = async (name, kind, id) => {
    if (!(await cmd(p, 'sheet', { kind, id })).ok) throw Error('no sheet ' + kind);
    const snap = await p.evaluate(() => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }));
    if (!snap.sheet || snap.sheet.kind !== kind || snap.sheet.gone) throw Error(name + ': the sheet is not in the projection');
    write('fixtures/director-sheet-' + name + '.json', snap);
  };
  await sheet('session-now', 'session', ids.now);
  await sheet('session-later', 'session', ids.later);
  await sheet('session-past', 'session', ids.past);
  await sheet('meal', 'meal', ids.meal);
  await sheet('move', 'move', ids.later);
  await sheet('disrupt', 'disrupt', '');
  if (!(await cmd(p, 'today', { name: 'timer', act: 'start', id: ids.now })).ok) throw Error('the timer did not start');
  await p.clock.runFor((12 * 60 + 34) * 1000);
  await sheet('timer', 'timer', ids.now);
  await cmd(p, 'today', { name: 'timer', act: 'pause', id: ids.now });
  await p.clock.runFor(3 * 60 * 1000);
  await sheet('timer-paused', 'timer', ids.now);
  const today = {}, work = { name: 'disrupt', kind: 'work', op: 'fixture-work', date: directorDay.DAY, from: 20 * 60, until: 21 * 60, commute: 15 };
  today.disrupt = await cmd(p, 'today', work);
  today.disruptAgain = await cmd(p, 'today', work);
  today.disruptInvalid = await cmd(p, 'today', { name: 'disrupt', kind: 'work', op: 'fixture-bad', date: '', from: 600, until: 700 });
  today.disruptNotOffered = await cmd(p, 'today', { name: 'disrupt', kind: 'block', op: 'fixture-b5', hours: 5 });
  await p.evaluate(() => {
    const real = window.__realSet = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) };
  });
  today.disruptNotSaved = await cmd(p, 'today', { name: 'disrupt', kind: 'block', op: 'fixture-block', hours: 1 });
  await p.evaluate(() => { Storage.prototype.setItem = window.__realSet; });
  today.disruptAfterFault = await cmd(p, 'today', { name: 'disrupt', kind: 'block', op: 'fixture-block', hours: 1 });
  today.energy = await cmd(p, 'today', { name: 'energy', level: 'LOW' });
  today.replan = await cmd(p, 'today', { name: 'replan' });
  today.regroupNotShowing = await cmd(p, 'today', { name: 'regroup', v: 'pause', op: 'fixture-pause' });
  today.dismissBannerNotShowing = await cmd(p, 'today', { name: 'dismissBanner', text: 'Not showing' });
  write('fixtures/director-replies-today.json', today);
  // Today's guide, not seen yet, as a screen that draws it gets it: each step's part, the dial's mode and the page's name tags
  await cmd(p, 'sheet', { kind: '' });
  await p.evaluate(() => { if (!LifeDirector.native.run('tourReset', {}).ok) throw Error('the guides were not reset') });
  await p.clock.runFor(300);
  {
    const snap = await p.evaluate(() => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }));
    if (!snap.today || !snap.guide || snap.guide.key !== 'today' || !snap.guide.auto) throw Error('Today\'s guide is not in the projection');
    write('fixtures/director-guide-today.json', snap);
  }
  await require('./gen_director_time_fixtures.js')({ b, out, write, directorDay }); // phase 4B: My time, first run and the forms

  /* phase 4A, a screen that draws celebrations (director.celebrations.v1): director-replies-celebrate.json, the page's answer to a
     done that pays XP and to its repeat; director-intent.json, the projection once a session is tapped on the Overview's glance */
  p = await covered(['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1', 'director.celebrations.v1']);
  const paid = await p.evaluate(() => LifeDirector.native.today(0).upNext.actions.find((a) => a.act === 'focusStart').id);
  const celebrate = { done: await cmd(p, 'today', { name: 'done', id: paid, minutes: 20 }) };
  if (!(celebrate.done.celebrations || []).length) throw Error('done paid no XP');
  celebrate.doneAgain = await cmd(p, 'today', { name: 'done', id: paid, minutes: 20 });
  write('fixtures/director-replies-celebrate.json', celebrate);
  await p.evaluate(() => lmaNavTo('dashboard'));
  await p.clock.runFor(800);
  await p.evaluate(() => document.querySelector('#ovPlan .ld-dial [data-act="inst"]').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  await p.clock.runFor(800);
  const glanced = await p.evaluate(() => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }));
  if (!glanced.intent || glanced.intent.sheet !== 'session') throw Error('the glance asked for no sheet');
  write('fixtures/director-intent.json', glanced);

  p = await live(true);
  await act(p, 'inst', await upNext(p));
  await p.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open [data-act=skip]').click());
  await shoot(p, 'skip');

  p = await live(true);
  await p.emulateMedia({ reducedMotion: 'reduce' });
  await cmd(p, 'dialApply', { key: await key(p, 'sleep', 'start'), minute: 1350 });
  await shoot(p, 'reduced');
}

/* Recipes as the page opens the screen with them: a Scholar (cookbook and class recipes), an Entrepreneur (two Snack recipes, which
   only "All recipes" shows), a Disciple (no class recipes) and the Scholar's diet card asking for a recipe, which the screen takes
   over. recipes-filters.json holds, for each, the ids the page's own filter (recipeHits) shows for sample searches and categories,
   so the native filter can be tested for exact parity. A fake NativeRecipes plugin keeps what the page opens it with. */
async function recipes(b) {
  const p = await page(b);
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'scholar'}));
    window.__opened=[];const ls={};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeRecipes',getPlatform:()=>'android',registerPlugin:()=>({}),convertFileSrc:u=>u,
      Plugins:{NativeRecipes:{addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},handshake:async()=>({protocol:1,capabilities:['recipes.projection.v1']}),
        open:async a=>{window.__opened.push(a.snapshot)},update:async()=>{},close:async()=>{(ls.closed||[]).forEach(f=>f({}))}}}};`);
  await p.goto('file://' + out); await p.waitForTimeout(2500);
  const { opened, filters } = await p.evaluate(async () => {
    const wait = (ms) => new Promise((r) => setTimeout(r, ms));
    const open = async (go) => {
      const n = window.__opened.length;
      go();
      for (let t = 0; t < 8000 && window.__opened.length === n; t += 50) await wait(50);
      if (window.__opened.length === n) throw Error('the screen did not open');
      navigate('dashboard');
      return window.__opened[n];
    };
    const CASES = [['', 'all'], ['chicken', 'all'], ['  CHICKEN ', 'all'], ['egg', 'all'], ['chicken rice', 'all'], ['oz chicken', 'all'], ['breakfast 12 eggs', 'all'], ['breakfast', 'all'],
      ['lunch & dinner', 'all'], ['snack', 'all'], ['preheat', 'all'], ['½', 'all'], ['zzz', 'all'], ['', 'Breakfast'], ['', 'Pre-workout'],
      ['', 'Post-workout'], ['', 'Lunch & dinner'], ['oats', 'Breakfast'], ['chicken', 'Breakfast']];
    const cases = (extra) => [...CASES, ...extra].map(([query, category]) => ({ query, category, ids: recipeHits(query, category).map((r) => r.id) }));
    lmaThumbWarm = async () => { };
    const S = lmaLoad(); S.nativeRecipes = true; S.nativeOverview = false; lmaSave();
    const opened = {}, filters = {};
    for (const cls of ['scholar', 'entrepreneur', 'disciple']) {
      state.classId = cls;
      opened[cls] = await open(() => navigate('recipes'));
      filters[cls] = cases([]);
    }
    state.classId = 'scholar';
    fv2S().pane = 'log'; // the diet card is on Meals' Log pane
    navigate('meals');
    const recipe = document.querySelector('#dietPlanWrap .diet-menu-item button[onclick^="dietOpenRecipe"]');
    opened.prefill = await open(() => recipe.click());
    filters.prefill = cases([[opened.prefill.filter.query, opened.prefill.filter.category]]);
    return { opened, filters };
  });
  for (const [name, data] of Object.entries(opened)) write('fixtures/recipes-' + name + '.json', Object.assign(data, { webSessionId: 'recipes-fixture', sequence: 1 }));
  write('fixtures/recipes-filters.json', filters);
}

/* The Library's Media pane on the same day:
   full    a lived-in list: the default books (two finished, one in progress) under the owner's own items, newest first: an
           audiobook half listened, an article, a course, a podcast, an audiobook not played yet, a documentary, a video and an
           item imported without a type or a status
   search  the same list as a voice search leaves it ("Search my library for wealth")
   empty   every item removed, with the page's last "Removed from library" still showing
   tip     the first visit: the default books and the pane's feature tip
   sheets  for a screen that draws the Media sheets (library.media.write.v1): the default books under an item of a type the form
           no longer offers and one whose stored link is not http, with the form, its words and the confirms
   media-filter-parity.json holds the rows the page itself shows for sample filters of the full list, for the screen's own
   filter to match exactly. */
async function media(b) {
  // the page names a new item media_<ms>_<random>: a fixed clock and a seeded Math.random give the same names on every run
  const visit = async (tipsSeen, caps) => {
    const p = await page(b);
    await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
    // a screen that answers the handshake with caps, for the fields only such a screen gets
    if (caps) await p.addInitScript(`window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n==='NativeMedia',getPlatform:()=>'android',registerPlugin:()=>({}),convertFileSrc:u=>u,
      Plugins:{NativeMedia:{addListener:async()=>({remove(){}}),handshake:async()=>({protocol:1,capabilities:${JSON.stringify(caps)}}),open:async()=>{},update:async()=>{},close:async()=>{}}}};`);
    await p.addInitScript(`let seed=1;Math.random=()=>{let t=seed+=0x6D2B79F5;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296};
      localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',tipsSeen:${JSON.stringify(tipsSeen)}}));`);
    await p.goto('file://' + out); await p.waitForTimeout(2500);
    await p.evaluate(() => { window.confirm = () => true; lmaNavTo('media') });
    return p;
  };
  // the page's toast comes and goes on its own timer, so a fixture carries it only when asked to, shot at once after it
  const shoot = async (p, name, toast = false) => write('fixtures/media-' + name + '.json', await p.evaluate((toast) => {
    if (!toast) document.getElementById('toast').classList.remove('show');
    return Object.assign(NativeLibraryTabProjection.snapshot(), { webSessionId: 'media-fixture', sequence: 1 });
  }, toast));

  let p = await visit({ media: true });
  await p.evaluate(async () => {
    const add = (f) => { const r = mediaUpsert(f); if (!r.ok) throw Error(r.message); return r.id };
    const attach = async (id, name, mb) => { await attachAudioFile(id, new File([new Uint8Array(Math.round(mb * 1048576))], name, { type: 'audio/mp4' })); closeAudioPlayer() };
    toggleMediaFinished('reading_atomic_habits', true); toggleMediaFinished('reading_deep_work', true); toggleMediaFinished('reading_meditations', false);
    add({ title: 'Old import', creator: 'Unknown', type: '', status: '', notes: 'Imported from an older backup' });
    add({ title: 'Essence of Linear Algebra', type: 'Video', creator: '3Blue1Brown', status: 'unread', link: 'https://example.com/linear-algebra' });
    add({ title: 'Planet Earth II', type: 'Documentary', creator: 'BBC', status: 'finished', notes: 'Watch "Cities" again' });
    await attach(add({ title: 'Voice memos', type: 'Audiobook', status: 'unread' }), 'memos.m4a', 1);
    add({ title: 'Huberman Lab: Sleep', type: 'Podcast', creator: 'Andrew Huberman', status: 'progress', link: 'http://example.org/sleep' });
    add({ title: 'Learning How to Learn', type: 'Course', creator: 'Barbara Oakley', status: 'progress', notes: 'Week 2 of 4: chunking and recall' });
    add({ title: 'The Bitter Lesson', type: 'Article', creator: 'Rich Sutton', status: 'finished', link: 'https://example.com/bitter-lesson' });
    const book = add({ title: 'Project Hail Mary', type: 'Audiobook', creator: 'Andy Weir', status: 'progress', notes: 'Chapter 12, the Astrophage tests' });
    await attach(book, 'project-hail-mary.m4b', 9.6);
    updateMediaAudioProgress(book, 15000, 58200); save();
  });
  await shoot(p, 'full');
  const cases = await p.evaluate(() => {
    const set = (f) => { document.getElementById('mediaSearch').value = f.search; document.getElementById('mediaFilter').value = f.status; document.getElementById('mediaTypeFilter').value = f.type; renderMedia() };
    const res = [
      ['', 'all', 'all'], ['wealth', 'all', 'all'], ['  DEEP ', 'all', 'all'], ['weir', 'all', 'all'], ['audiobook', 'all', 'all'], ['month 3', 'all', 'all'],
      ['', 'unread', 'all'], ['', 'progress', 'all'], ['', 'finished', 'all'], ['', 'all', 'Book'], ['', 'all', 'Audiobook'], ['', 'all', 'Other'],
      ['', 'progress', 'Book'], ['the', 'unread', 'Book'], ['nothing like this', 'all', 'all'],
    ].map(([search, status, type]) => {
      set({ search, status, type });
      return { filter: { search, status, type }, ids: [...document.querySelectorAll('#mediaList .media-item')].map((r) => r.dataset.mediaId) };
    });
    set({ search: '', status: 'all', type: 'all' });
    return res;
  });
  write('fixtures/media-filter-parity.json', { source: 'fixtures/media-full.json', note: 'the rows the page shows for each filter, in order (renderMedia over the full list)', cases });
  await p.evaluate(() => LifeMaxVoice.simulateUtterance('Search my library for wealth'));
  await p.waitForTimeout(400);
  await shoot(p, 'search');

  p = await visit({ media: true });
  await p.evaluate(() => state.mediaItems.map((x) => x.id).forEach(deleteMediaItem));
  await shoot(p, 'empty', true);

  await shoot(await visit({}), 'tip');

  p = await visit({ media: true }, ['library.media.projection.v1', 'library.media.write.v1']);
  await p.evaluate(async () => {
    const add = (f) => { const r = mediaUpsert(f); if (!r.ok) throw Error(r.message); return r.id };
    add({ title: 'Saga, volume one', type: 'Comic', creator: 'Brian K. Vaughan', status: 'progress', link: 'https://example.com/saga', notes: 'An old type the form no longer offers' });
    const old = add({ title: 'Old bookmark', type: 'Article', status: 'unread' });
    mediaById(old).link = 'ftp://example.com/old'; save(); // as an old save stored it, before the form checked links
    navigate('media'); await new Promise((r) => setTimeout(r, 400)); // on the pane, so the screen opens and asks the handshake
    await openNativeLibrary();
  });
  await shoot(p, 'sheets');
}

/* Settings as the native screen opens on it, on a phone (tests/fixtures/settings-android.js: reminders allowed, a widget on the
   home screen, this build the latest), on the same day:
   default  a new player's Settings, Look & feel
   many     most switches on, through the screen's own commands where it has them (every native tab, tab timing, reminders'
            check-in, a quieter card deal, strong haptics) and through the page where Android asks first (health sync, calendar
            import) or the page keeps it (a backup folder, developer mode); on Phone & sync
   light    the light theme, Look & feel
   bar      the bottom-bar editor after the screen moved Quests to second, hid Cat and Money and added Settings */
async function settings(b) {
  const visit = async (theme) => {
    const p = await page(b);
    await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
    await p.addInitScript(settingsAndroid.script({ theme }));
    await p.goto('file://' + out); await p.waitForTimeout(3500);
    await p.evaluate(async () => {
      lmaThumbWarm = async () => { };
      const S = lmaLoad(); S.nativeOverview = false; S.nativeSettings = true; lmaSave();
      navigate('settings');
      for (let t = 0; t < 8000 && !window.__calls.some((c) => c[0] === 'settings' && c[1] === 'open'); t += 50) await new Promise((r) => setTimeout(r, 50));
    });
    return p;
  };
  const command = (p, list) => p.evaluate(async (list) => {
    for (const [command, payload] of list) {
      // an id of its own for each request, as the screen gives them (the page answers a repeated id from its receipt)
      const requestId = command + '-' + (window.__rid = (window.__rid || 0) + 1);
      const [reply] = await Capacitor.Plugins.NativeSettings.__emit('command', { command, payload, requestId });
      if (!reply.ok && reply.changed !== false) throw Error(command + ' ' + JSON.stringify(payload) + ': ' + reply.message);
    }
  }, list);
  // the page's own time, seconds after it began
  const after = (p, seconds) => p.waitForFunction((s) => Date.now() >= window.__bootAt + s * 1000, seconds, { polling: 100, timeout: 120000 });
  const SHOT = Date.parse('2026-10-28T12:01:00Z'); // the moment every fixture is taken
  const shoot = async (p, name) => {
    // every fixture at the same moment, a minute in: every boot timer has run (the widget's first picture at 9 s, the first
    // health sync at 14 s), none of the periodic ones has, and the renderers that wait for a plugin have drawn
    // the tab previews: whether the boot visit's picture was taken before the page went to Settings depends on the machine, so
    // every fixture has exactly the one of Settings, taken by the page's own capture
    await p.evaluate(async () => {
      Object.keys(lmaThumbs).forEach((k) => delete lmaThumbs[k]);
      await lmaThumbSnapNow('settings');
      lmaThumbRender();
    });
    await p.clock.pauseAt(new Date(SHOT));
    await p.evaluate(() => navigate('settings')); // the cards drawn again at that moment, as on arriving, so every "… ago" is of it
    await p.waitForTimeout(300);
    write('fixtures/settings-' + name + '.json', await p.evaluate(() => {
      document.getElementById('toast').classList.remove('show'); // the page's toast comes and goes on its own timer
      return Object.assign(NativeSettingsTabProjection.snapshot(), { webSessionId: 'settings-fixture', sequence: 1 });
    }));
  };

  await shoot(await visit(), 'default');

  let p = await visit();
  await command(p, ['nativeCharacter', 'nativeLog', 'nativePlan', 'nativeDirector', 'nativeRecipes', 'nativeLibrary', 'nativeNutrition', 'tabTiming', 'reminders.checkin'].map((id) => ['toggle', { id, on: true }]));
  await command(p, [['choose', { id: 'cardDeal', value: 'quick' }], ['choose', { id: 'haptics.strength', value: 'strong' }], ['choose', { id: 'reminders.lead', value: '30' }]]);
  await after(p, 16); // health sync's first run, 14 s after the page began, has found it off, so what is set here stays as set
  await p.evaluate(async (shot) => {
    Object.assign(lmaHealthCfg(), { on: true, granted: ['exercise', 'steps', 'sleep', 'weight'], last: shot - 42 * 60e3, src: 'Samsung Health', today: { count: 6240 } }); save();
    Object.assign(lmaLoad().backup, { uri: 'content://tree/primary%3ADocuments%2FLifeMax', folder: 'LifeMax', on: true, verified: shot - 3 * 3600e3, last: shot - 3 * 3600e3, status: 'ok', lastName: 'life-max-auto-2026-10-28-0900.json' }); lmaSave();
    await lmcSet('on', true);
    const dev = document.getElementById('devModeOn'); dev.checked = true; dev.dispatchEvent(new Event('change')); // as the owner ticks it
    lmaHealthRender(); lmaRenderBackup();
  }, SHOT);
  await command(p, [['choose', { id: 'backup.keep', value: '14' }], ['category', { id: 'phone' }]]);
  await shoot(p, 'many');

  p = await visit('light');
  await shoot(p, 'light');

  p = await visit();
  await command(p, [['press', { id: 'tabs.quests.earlier' }], ['press', { id: 'tabs.quests.earlier' }], ['press', { id: 'tabs.quests.earlier' }], ['press', { id: 'tabs.quests.earlier' }],
    ['toggle', { id: 'tabs.pet.show', on: false }], ['toggle', { id: 'tabs.money.show', on: false }], ['toggle', { id: 'tabs.settings.show', on: true }]]);
  await shoot(p, 'bar');
}

/* the native Overview cards (overview.cards.v1): tools/gen_overview_fixtures.js */
const overview = (b) => require('./gen_overview_fixtures.js')({ b, out, write, directorDay });
/* more states of Life Director's glance on the native Overview: tools/gen_director_glance_fixtures.js */
const glance = (b) => require('./gen_director_glance_fixtures.js')({ b, out, write, directorDay });
/* the native Journal (library.journal.projection.v1): tools/gen_journal_fixtures.js */
const journal = (b) => require('./gen_journal_fixtures.js')({ b, out, write });
/* the Workout plan's guided workout, fields, Leave plan and offers (plan.runner.v1 and the rest): tools/gen_plan_runner_fixtures.js */
const planrunner = (b) => require('./gen_plan_runner_fixtures.js')({ b, out, write });
/* Food's Smart Diet swap sheet and Recipes' "Log this recipe": tools/gen_food_swap_fixtures.js */
const foodswap = (b) => require('./gen_food_swap_fixtures.js')({ b, out, write });
const JOBS = { food, log, plan, director, recipes, media, settings, swipe, overview, glance, journal, planrunner, foodswap };
(async () => {
  const only = process.argv.slice(2);
  for (const name of only) if (!JOBS[name]) throw Error('no fixtures named ' + name + ' (' + Object.keys(JOBS).join(', ') + ')');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const [name, job] of Object.entries(JOBS)) if (!only.length || only.includes(name)) await job(b);
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
