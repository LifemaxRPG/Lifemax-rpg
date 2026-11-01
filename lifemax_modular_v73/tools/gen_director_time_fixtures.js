/* Life Director phase 4B's native fixtures, part of tools/gen_native_fixtures.js's director job: My time, the first-run card and My
   time's forms as a screen that draws them (director.setup.v1) gets them, and what the page answers My time's commands.
     director-time.json             My time on the lived-in day (set up: Re-plan now, no steps)
     director-time-first.json       My time with set-up not finished (tests/fixtures/director-day.js unfinished: steps, weekend sleep)
     director-guide-setup.json      the same with set-up's guide not seen yet: each step's part of My time, the mini dial's tags
     director-firstrun.json         Today for a new player: the first-run card
     director-form-rule.json        the commitment sheet for a new commitment (schema)
     director-form-rule-rotating.json  the commitment sheet editing a rotating night shift
     director-form-goal-type.json   "Add a goal": the goal kinds
     director-form-goal.json        the goal form for Read more, with its measurable target (fold)
     director-form-goal-custom.json the goal form for your own activity
     director-clock.json            the frame's clock and My time's per-quarter-hour clock words in en-US and en-GB, for the screen's own
                                    formatting of a picked time to be checked against the page's
     director-replies-time.json     My time's commands answered, in the envelope NativeTabKit sends them in: each write, its repeat,
                                    refusals, and a refused save */
const CAPS = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1', 'director.setup.v1'];

module.exports = async function directorTime({ b, out, write, directorDay }) {
  let last = null;
  /* a page whose screen says it draws My time (a fake plugin answers the kit's warm handshake); setup: 'done', 'unfinished' or 'new' */
  const covered = async (setup, locale = 'en-US') => {
    if (last) await last.context().close();
    const p = last = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale, timezoneId: 'UTC' })).newPage();
    await directorDay.seed(p, { planned: setup !== 'new', setup });
    await p.addInitScript(`(() => { const ls = {}; window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector', getPlatform: () => 'android',
      registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativeLifeDirector: { addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
        handshake: async () => ({ protocol: 1, capabilities: ${JSON.stringify(CAPS)} }), open: async () => {}, update: async () => {}, close: async () => { (ls.closed || []).forEach(f => f({})) } } } } })()`);
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { }; lmaLoad().nativeOverview = false; setNativeDirectorEnabled(true) });
    if (setup === 'done') await directorDay.play(p); else await p.clock.runFor(3000);
    await p.evaluate(() => { LifeDirector.native.tourSeen('setup', true); lmaNavTo('life-director') });
    await p.clock.runFor(1500);
    // the kit learns what the screen draws from its handshake, which answers in its own time on a busy machine
    await p.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('setup'));
    return p;
  };
  let request = 0;
  const cmd = (p, command, payload) => p.evaluate(([c, x]) => NativeDirectorProjection.commands[c](x), [command, payload]).then((reply) => Object.assign({ command, requestId: 'director-' + ++request }, reply));
  const snap = (p) => p.evaluate(() => Object.assign(NativeDirectorTabProjection.snapshot(), { webSessionId: 'director-fixture' }));
  const shoot = async (p, name, check) => { const s = await snap(p); if (!check(s)) throw Error(name + ': not in the projection'); write('fixtures/director-' + name + '.json', s); return s; };
  const section = async (p, id) => { if (!(await cmd(p, 'section', { id })).ok) throw Error('no section ' + id); await p.clock.runFor(300); };
  const form = async (p, name, kind, id) => {
    if (!(await cmd(p, 'sheet', { kind, id })).ok) throw Error('no sheet ' + kind);
    await shoot(p, 'form-' + name, (s) => s.sheet && s.sheet.kind === kind && !s.sheet.gone);
  };
  const vals = (f) => Object.fromEntries(f.fields.concat(f.fold ? f.fold.fields : []).map((x) => [x.key, x.value]));

  let p = await covered('done');
  await section(p, 'setup');
  await shoot(p, 'time', (s) => s.time && !s.time.first && s.covers.includes('setup'));
  await form(p, 'rule', 'rule', '');
  await form(p, 'goal-type', 'goalType', '');
  await form(p, 'goal', 'goalForm', 'HABIT_READING');
  await form(p, 'goal-custom', 'goalForm', 'CUSTOM');
  await cmd(p, 'sheet', { kind: '' });

  const S = await p.evaluate(() => ({ rule: LifeDirector.native.sheet('rule', ''), goal: LifeDirector.native.sheet('goalForm', 'HABIT_READING') }));
  const rule = Object.assign(vals(S.rule), { op: 'fixture-rule', title: 'Choir', kind: 'APPOINTMENT', days: [2], start: 1170, end: 1260, commute: 10, unwind: 0, load: 'LIGHT' });
  const goal = Object.assign(vals(S.goal), { op: 'f1x7ure0-goal', arch: 'HABIT_READING', title: 'Read 12 books', units: 3600, unit: 'pages', rate: 30, deadline: '2027-03-31' });
  const R = {}, run = (name, payload) => cmd(p, 'do', Object.assign({ name }, payload));
  R.sleepSave = await run('sleepSave', { bed: 1350, wake: 390, weekend: { bed: 1410, wake: 480 } });
  R.sleepSaveAgain = await run('sleepSave', { bed: 1350, wake: 390, weekend: { bed: 1410, wake: 480 } });
  R.sleepSaveTooShort = await run('sleepSave', { bed: 120, wake: 360, weekend: null });
  R.ruleSave = await run('ruleSave', rule);
  R.ruleSaveAgain = await run('ruleSave', rule);
  R.ruleSaveNoDays = await run('ruleSave', Object.assign({}, rule, { op: 'fixture-rule-2', days: [] }));
  R.ruleSaveRemoved = await run('ruleSave', Object.assign({}, rule, { op: undefined, id: 'r-gone' }));
  R.ruleSaveEdit = await run('ruleSave', Object.assign({}, rule, { op: undefined, id: 'r-fixture-rule', end: 1290 }));
  R.ruleRemove = await run('ruleRemove', { id: 'r-fixture-rule' });
  R.ruleRemoveAgain = await run('ruleRemove', { id: 'r-fixture-rule' });
  R.routine = await run('routine', { key: 'windDownMinutes', value: 45 });
  R.routineAgain = await run('routine', { key: 'windDownMinutes', value: 45 });
  R.routineClamped = await run('routine', { key: 'mealMinutes', value: 500 });
  R.meals = await run('meals', { on: false });
  R.mealsAgain = await run('meals', { on: false });
  R.goalAdd = await run('goalAdd', goal);
  R.goalAddAgain = await run('goalAdd', goal);
  R.goalAddNoName = await run('goalAdd', Object.assign({}, goal, { op: 'f1x7ure1-goal', title: '  ' }));
  R.finishSetup = await run('finishSetup', {});
  await p.evaluate(() => {
    const real = window.__realSet = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return real.apply(this, arguments) };
  });
  R.sleepSaveNotSaved = await run('sleepSave', { bed: 1380, wake: 420, weekend: null });
  await p.evaluate(() => { Storage.prototype.setItem = window.__realSet; });

  p = await covered('unfinished');
  await section(p, 'setup');
  await shoot(p, 'time-first', (s) => s.time && s.time.first && s.time.sleep.weekend.on);
  await form(p, 'rule-rotating', 'rule', 'r-rot');
  // set-up's guide, not seen yet, as a screen that draws My time gets it: a part of My time for each step, the mini dial's tags
  await p.evaluate(() => { NativeDirectorProjection.commands.sheet({ kind: '' }); if (!LifeDirector.native.run('tourReset', {}).ok) throw Error('the guides were not reset') });
  await p.clock.runFor(300);
  await shoot(p, 'guide-setup', (s) => s.time && s.guide && s.guide.key === 'setup' && s.guide.auto && s.guide.steps.some((x) => x.tags.length));

  p = await covered('new');
  await section(p, 'today');
  await shoot(p, 'firstrun', (s) => s.firstRun && s.covers.includes('first-run'));
  R.finishSetupWithoutSleep = await run('finishSetup', {});
  write('fixtures/director-replies-time.json', R);

  const clock = {};
  for (const locale of ['en-US', 'en-GB']) {
    p = await covered('new', locale);
    clock[locale] = await p.evaluate(() => { const c = LifeDirector.native.frame().clock, t = LifeDirector.native.time(); return { h12: c.h12, locale: c.locale, step: t.sleep.step, clock: t.sleep.clock } });
  }
  write('fixtures/director-clock.json', clock);
  await last.context().close();
};
