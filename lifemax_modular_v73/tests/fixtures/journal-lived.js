/* A lived-in Journal for the native Journal's tests and fixtures (tests/native-journal.test.js, tools/gen_journal_fixtures.js), on the
   fixed clock they run at (Wednesday 2026-10-28, 12:00 UTC, en-US, UTC): six weeks of morning study and evening runs, a quiet
   stretch, three quests, a workout plan with its notes (one day with the session, one with notes only), a rest day, reflections, a
   paid weekly review answer, and a canary in each kind of private text (a reflection, an activity's name and note, a plan note,
   a review answer). Built through plain state (no awards, so no clock-dependent ids reach a projection) and stored with save().
   lived() runs in the page and answers {withAct, notesOnly, week, month, canaries, privates}: the plan days' offsets, the weekly
   and monthly review keys, the canaries, and every private string of the journal (names, notes, reflections, answers, plan notes
   and weights), for a test that scans a withheld frame. */
function lived() {
  const Y = (o) => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);
  const at = (o, h, m) => new Date(Date.UTC(2026, 9, 28 - o, h, m || 5)).toISOString();
  let n = 0;
  const act = (o, h, name, xp, attr, extra) =>
    state.activities.push(Object.assign({ id: 'p' + ++n, type: 'custom', name, icon: 'book', duration: 30, quality: 1, skill: name, primary: attr, secondary: '', xp, date: at(o, h), notes: '', xpSplit: [{ attr, xp }] }, extra || {}));
  const planKeys = (d) => ((planDay(d) || {}).items || []).map((i) => i.k).filter((k) => k !== 'warm');
  state.activities = []; state.dayReflections = {}; state.restDays = {}; state.journalCoach = {};
  state.plan = { id: 'athlete_beginner8', classId: 'athlete', start: '2026-09-28', from: '2026-09-28', checks: {} };
  for (let o = 0; o <= 34; o++) {
    if ((o >= 19 && o <= 22) || o === 11) continue;
    act(o, 9, 'Study', 80 + ((o * 13) % 50), 'intelligence', { skill: 'Knowledge' });
    if (o % 3 === 0) act(o, 18, 'Evening run', 40 + ((o * 7) % 30), 'endurance', { icon: 'run', duration: 25, skill: 'Running', notes: o % 6 === 0 ? 'Easy pace along the river.' : '' });
  }
  act(0, 7, 'Canary sprint drills', 35, 'physique', { icon: '🏋️', duration: 20, skill: 'Sprinting', notes: 'Canary note: knees felt odd' });
  act(1, 10, 'Big push', 320, 'physique', { icon: 'dumbbell', duration: 90, skill: 'Lifting' });
  ['Morning walk', 'Inbox zero', 'Call a friend'].forEach((q, i) => act(2, 11 + i, 'Quest: ' + q, 25, 'discipline', { type: 'quest', icon: 'flag', duration: 0, skill: 'Quest Completion', notes: 'Quest completed' }));
  act(4, 13, 'Reading "Dune" & notes <ch. 3>', 45, 'intelligence', { notes: "Paul's test: \"fear is the mind-killer\" & more" });
  const plan = [4, 5, 6, 7, 8, 9, 10].filter((o) => planKeys(Y(o)).length >= 2), withAct = plan[0], notesOnly = plan[1];
  const logs = (d, a, b) => { const k = planKeys(d); state.plan.checks[d] = { log: { [k[0]]: a, [k[1]]: '' }, v: { [k[0]]: '60 kg', [k[1]]: b } }; };
  act(withAct, 7, 'Workout plan session', 60, 'physique', { type: 'plan', icon: 'dumbbell', duration: 45, skill: 'Strength', planDate: Y(withAct) });
  logs(Y(withAct), 'Canary plan note, kept 2 in reserve', '12 kg');
  logs(Y(notesOnly), 'Felt heavy today', '');
  state.restDays[Y(9)] = true;
  Object.assign(state.dayReflections, {
    [Y(0)]: 'Canary reflection: slow start, but the sprint drills woke me up properly.',
    [Y(1)]: 'Big push today and my legs are done, but I am proud of the effort.',
    [Y(6)]: 'An older day that I wrote about at length, so it is long enough to count.',
    [Y(11)]: 'Stayed in and read all afternoon.',
  });
  save();
  const reviews = LifeMaxCoach.reviewList(journalDays()), week = reviews.find((r) => r.kind === 'W').key, month = reviews.find((r) => r.kind === 'M').key;
  const store = LifeMaxCoach.store();
  store.reviews[week] = { answer: 'Canary answer: lights out by eleven, every weeknight.', paid: true };
  save();
  const canaries = ['Canary reflection', 'Canary sprint drills', 'Canary note', 'Canary plan note', 'Canary answer'];
  const privates = new Set();
  state.activities.forEach((a) => [a.name, a.notes, a.skill].forEach((s) => s && String(s).trim().length >= 4 && privates.add(String(s).trim())));
  Object.values(state.dayReflections).forEach((s) => privates.add(s));
  Object.values(store.reviews).forEach((r) => r.answer && privates.add(r.answer));
  Object.values(state.plan.checks).forEach((c) => [...Object.values(c.log || {}), ...Object.values(c.v || {})].forEach((s) => s && String(s).trim().length >= 4 && privates.add(String(s).trim())));
  return { withAct, notesOnly, week, month, canaries, privates: [...privates], Y0: Y(0) };
}
module.exports = { lived };
