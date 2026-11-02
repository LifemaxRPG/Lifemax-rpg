/* The Journal's markup through the coach split. The page now draws the Journal from data builders (jcHeroData, jcListData,
   jcDayData, jcReviewData, journalDayData) through thin HTML renderers, the same data a native Journal will project. These scenarios
   must draw, byte for byte, what the page drew before the split: fixtures/journal-parity.json was recorded with --write from main
   8ad7560, the last build that drew straight from the store. Each scenario also records the coach's decisions after the drawing (pattern
   chips, cooldowns, welcome records, the reset baseline), which must be the same, apart from the empty day and review records the old
   drawing created and the split no longer does.
   The page runs on a fixed clock (Wednesday 2026-10-28, 12:00 UTC), in en-US and UTC, so dates, times and "Today" never move.
   Run: NODE_PATH=$(npm root -g) node tests/journal-parity.test.js [built html] [--write] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const args = process.argv.slice(2), write = args.includes('--write');
const file = require('./current-page')(args.find(a => a !== '--write'));
const goldenPath = path.join(__dirname, 'fixtures', 'journal-parity.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

/* Runs in the page: builds a history through plain state (no awards, so no clock-dependent ids or times reach the markup), stores it
   once with save() so migrations and quest checks have settled, then clears the coach's records, selects the scenario's entry and
   draws the Journal once. */
function scenarios() {
  const Y = o => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);
  const at = (o, h, m) => new Date(Date.UTC(2026, 9, 28 - o, h, m || 5)).toISOString();
  let n = 0;
  const act = (o, h, name, xp, attr, extra) => state.activities.push(Object.assign({ id: 'p' + (++n), type: 'custom', name, icon: 'book', duration: 30, quality: 1, skill: name, primary: attr, secondary: '', xp, date: at(o, h), notes: '', xpSplit: [{ attr, xp }] }, extra || {}));
  const fresh = () => { state.activities = []; state.dayReflections = {}; state.restDays = {}; state.journalCoach = {}; state.plan = null; n = 0 };
  const planKeys = d => ((planDay(d) || {}).items || []).map(i => i.k).filter(k => k !== 'warm');
  /* six lived-in weeks: morning study, evening runs, a quiet stretch, quests, a workout plan, a rest day, reflections */
  const lived = () => {
    fresh();
    state.plan = { id: 'athlete_beginner8', classId: 'athlete', start: '2026-09-28', from: '2026-09-28', checks: {} };
    for (let o = 0; o <= 34; o++) {
      if ((o >= 19 && o <= 22) || o === 11) continue;
      act(o, 9, 'Study', 80 + (o * 13) % 50, 'intelligence', { skill: 'Knowledge' });
      if (o % 3 === 0) act(o, 18, 'Evening run', 40 + (o * 7) % 30, 'endurance', { icon: 'run', duration: 25, skill: 'Running', notes: o % 6 === 0 ? 'Easy pace along the river.' : '' });
    }
    act(1, 10, 'Big push', 320, 'physique', { icon: 'dumbbell', duration: 90, skill: 'Lifting' });
    ['Morning walk', 'Inbox zero', 'Call a friend'].forEach((q, i) => act(2, 11 + i, 'Quest: ' + q, 25, 'discipline', { type: 'quest', icon: 'flag', duration: 0, skill: 'Quest Completion', notes: 'Quest completed' }));
    act(2, 21, 'Journal reflection', 15, 'wellbeing', { type: 'coach', duration: 0, skill: 'Reflection', src: 'coach' });
    act(4, 13, 'Reading "Dune" & notes <ch. 3>', 45, 'intelligence', { notes: "Paul's test: \"fear is the mind-killer\" & more" });
    const plan = [4, 5, 6, 7, 8, 9, 10].filter(o => planKeys(Y(o)).length >= 2), withAct = plan[0], notesOnly = plan[1];
    const logs = (d, a, b) => { const k = planKeys(d); state.plan.checks[d] = { log: { [k[0]]: a, [k[1]]: '' }, v: { [k[0]]: '60 kg', [k[1]]: b } } };
    act(withAct, 7, 'Workout plan session', 60, 'physique', { type: 'plan', icon: 'dumbbell', duration: 45, skill: 'Strength', planDate: Y(withAct) });
    logs(Y(withAct), 'Easy, kept 2 in reserve', '12 kg');
    logs(Y(notesOnly), 'Felt heavy today', '');
    state.restDays[Y(9)] = true;
    Object.assign(state.dayReflections, {
      [Y(0)]: 'Slow start.', [Y(1)]: 'Big push today and my legs are done, but I am proud of the effort.',
      [Y(6)]: 'An older day that I wrote about at length, so it is long enough to count.', [Y(11)]: 'Stayed in and read all afternoon.'
    });
    return { withAct, notesOnly };
  };
  const welcome = gap => { fresh(); for (let o = gap + 1; o <= gap + 16; o++) act(o, 9, 'Study', 90 + (o * 11) % 40, 'intelligence', { skill: 'Knowledge' }); act(0, 9, 'Back at it', 60, 'discipline') };
  const strip = v => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return v;
    const out = {}; Object.keys(v).sort().forEach(k => { const x = strip(v[k]); if (!(x && typeof x === 'object' && !Array.isArray(x) && !Object.keys(x).length)) out[k] = x }); return out;
  };
  const draw = (key, coach, pieces) => {
    save(); state.journalCoach = JSON.parse(JSON.stringify(coach || {}));
    if (document.activeElement) document.activeElement.blur();
    histSel = key; renderHistory();
    const html = id => document.getElementById(id).innerHTML, got = { coach: strip(state.journalCoach) };
    (pieces || ['entry']).forEach(p => { got[p] = html({ hero: 'journalHero', days: 'journalDays', entry: 'journalEntry' }[p]) });
    return got;
  };
  const all = ['hero', 'days', 'entry'], out = {};
  const { withAct, notesOnly } = lived(); const reviews = LifeMaxCoach.reviewList(journalDays()), week = reviews.find(r => r.kind === 'W'), month = reviews.find(r => r.kind === 'M');
  out.today = draw(Y(0), null, all);
  out.yesterdayAwarded = draw(Y(1), { days: { [Y(1)]: { xpAwarded: true } } });
  out.yesterdayReady = draw(Y(1));
  out.quests = draw(Y(2), { days: { [Y(2)]: { promptId: 'PRM_C4' } } });
  out.escaped = draw(Y(4));
  out.planSession = draw(Y(withAct));
  out.workoutNotes = draw(Y(notesOnly));
  out.olderReflection = draw(Y(6));
  out.restDay = draw(Y(9));
  out.blankPage = draw(Y(11));
  out.week = draw(week.key, { reviews: { [week.key]: { answer: 'Lights out by eleven, every weeknight.', paid: true } } }, all);
  out.month = draw(month.key, null, all);
  state.dayReflections[Y(3)] = 'Honestly I feel like there is no reason to live lately.';
  out.care = draw(Y(3), null, all);
  welcome(4); out.welcome1 = draw(Y(0), null, all);
  welcome(4); out.welcome1Claimed = draw(Y(0), { welcome: { [Y(0)]: { tier: 1, gap: 4, claimed: { pet: 1 } } } });
  welcome(9); out.welcome2 = draw(Y(0), null, all);
  welcome(20); out.welcome3 = draw(Y(0), null, all);
  fresh(); out.empty = draw(null, null, all);
  return out;
}

(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file); await p.waitForFunction(() => window.LifeMaxCoach && window.LifeMaxJournalLock);
  const got = await p.evaluate(scenarios);
  if (write) {
    fs.writeFileSync(goldenPath, JSON.stringify(got, null, 1) + '\n');
    console.log('wrote ' + path.relative(process.cwd(), goldenPath) + ' (' + Object.keys(got).length + ' scenarios)');
  } else {
    const want = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));
    ok('the same scenarios are drawn', JSON.stringify(Object.keys(got)) === JSON.stringify(Object.keys(want)), Object.keys(got));
    for (const [name, w] of Object.entries(want)) {
      const g = got[name] || {};
      for (const piece of Object.keys(w)) {
        const a = JSON.stringify(g[piece]), e = JSON.stringify(w[piece]);
        let at = 0; while (at < a.length && a[at] === e[at]) at++;
        ok(name + ': ' + (piece === 'coach' ? 'the same coach decisions' : 'the ' + piece + ' markup is identical'), a === e, a === e ? undefined : { at, got: a.slice(Math.max(0, at - 80), at + 160), want: e.slice(Math.max(0, at - 80), at + 160) });
      }
    }
  }
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
