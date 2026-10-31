/* Produces the projections the native screens are tested and screenshotted with, by running the web app's own projections:
   Food's NativeNutritionBeta.snapshot() on a fixed day with a fixed diary, Log's NativeLogProjection.snapshot() for a few drafts
   on that day, the Workout plan's NativePlanTabProjection.snapshot() after ticking sets with the page's own planToggle, planVal
   and planLog, Life Director's tab snapshot on the lived-in day of tests/fixtures/director-day.js, and the Library's Media pane
   (NativeLibraryTabProjection.snapshot()) with a list built through the page's own mediaUpsert and attachAudioFile. Regenerate
   only when a projection changes:
     NODE_PATH=$(npm root -g) node tools/gen_native_fixtures.js
   Output in android-wrapper/native/src/test/resources: food-{log,plan,shop}.json, log-{default,strength,capped,refused}.json,
   fixtures/plan-{midweek,complete,scholar}.json, fixtures/director-*.json, fixtures/media-{full,search,empty,tip}.json and
   fixtures/media-filter-parity.json */
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

/* The Library's Media pane on the same day:
   full    a lived-in list: the default books (two finished, one in progress) under the owner's own items, newest first: an
           audiobook half listened, an article, a course, a podcast, an audiobook not played yet, a documentary, a video and an
           item imported without a type or a status
   search  the same list as a voice search leaves it ("Search my library for wealth")
   empty   every item removed, with the page's last "Removed from library" still showing
   tip     the first visit: the default books and the pane's feature tip
   media-filter-parity.json holds the rows the page itself shows for sample filters of the full list, for the screen's own
   filter to match exactly. */
async function media(b) {
  // the page names a new item media_<ms>_<random>: a fixed clock and a seeded Math.random give the same names on every run
  const visit = async (tipsSeen) => {
    const p = await page(b);
    await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
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
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  await food(b);
  await log(b);
  await plan(b);
  await director(b);
  await media(b);
  await b.close(); fs.unlinkSync(out);
})().catch(e => { console.error(e); process.exit(1); });
