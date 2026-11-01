/* Life Director's glance as the native Overview gets it (LifeDirector.native.glance(), overview.cards.v1's tiles.plan), part of
   tools/gen_native_fixtures.js (its glance job), on the lived-in day of tests/fixtures/director-day.js:
     director-glance-setup.json    a player who has not set Life Director up: the empty state and its button
     director-glance-day.json      3:10 PM, in the middle of work: what is on now, the commute next, the foot
     director-glance-next.json     7:13 PM: the evening session starting in two minutes, with Start early
     director-glance-timer.json    a session's timer started from Today, 12 min 34 s in
     director-glance-paused.json   the same timer paused, 3 minutes later
     director-glance-evening.json  10:40 PM: every session done or past
   Each file is the glance exactly as the page returns it. */
module.exports = async function directorGlance({ b, out, write, directorDay }) {
  let last = null;
  const live = async (planned) => {
    if (last) await last.context().close();
    const p = last = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await directorDay.seed(p, { planned });
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false });
    if (planned) await directorDay.play(p); else await p.clock.runFor(3000);
    return p;
  };
  const shoot = async (p, name, check) => {
    const g = await p.evaluate(() => LifeDirector.native.glance());
    if (!check(g)) throw Error('glance ' + name + ': not the state it should be: ' + JSON.stringify(g).slice(0, 300));
    write('fixtures/director-glance-' + name + '.json', g);
  };
  const timer = async (p) => {
    await p.evaluate(() => lmaNavTo('life-director'));
    await p.clock.runFor(1500);
    const id = await p.evaluate(() => { const id = LifeDirector.native.today(0).upNext.actions.find((a) => a.act === 'focusStart').id; if (!LifeDirector.native.todayRun('timer', { act: 'start', id }).ok) throw Error('no timer'); return id });
    await p.clock.runFor((12 * 60 + 34) * 1000);
    await p.evaluate(() => lmaNavTo('dashboard'));
    return id;
  };

  await shoot(await live(false), 'setup', (g) => g.state === 'setup');
  let p = await live(true);
  await shoot(p, 'day', (g) => g.state === 'day' && !g.core.timer);
  await p.clock.pauseAt(directorDay.at('19:13'));
  await shoot(p, 'next', (g) => g.core.next && g.core.next.live);
  await p.clock.pauseAt(directorDay.at('22:40'));
  await shoot(p, 'evening', (g) => g.foot.text === 'Every session done');
  p = await live(true);
  const id = await timer(p);
  await shoot(p, 'timer', (g) => g.core.timer && g.core.timer.paused === null);
  await p.evaluate((id) => { if (!LifeDirector.native.todayRun('timer', { act: 'pause', id }).ok) throw Error('no pause') }, id);
  await p.clock.runFor(3 * 60 * 1000);
  await shoot(p, 'paused', (g) => g.core.timer && g.core.timer.paused !== null);
  await last.context().close();
};
