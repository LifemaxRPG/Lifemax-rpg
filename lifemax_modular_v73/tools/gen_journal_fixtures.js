/* The native Journal's fixtures (library.journal.projection.v1), the journal job of tools/gen_native_fixtures.js: what the page
   projects (NativeJournalTabProjection.snapshot()) on the lived-in journal of tests/fixtures/journal-lived.js, at the fixed clock
   2026-10-28T12:00Z, en-US, UTC:
     journal-today.json        today: the hero, the strip, insight with its pattern, the question, the reflection, the timeline
     journal-pastday.json      a plan session day with its notes; journal-notes.json a day with workout notes on their own card;
     journal-blank.json        a blank page (only a reflection); journal-care.json the care note
     journal-welcome1.json, journal-welcome1-claimed.json, journal-welcome2.json, journal-welcome3.json   the welcome card's tiers
     journal-week.json         the weekly review (answered, paid); journal-month.json the monthly chronicle
     journal-empty.json        no entries; journal-tip.json the feature tip showing; journal-longstrip.json 400 days of history
     journal-locked.json, journal-locked-prompting.json, journal-locked-error.json   withheld frames (the lock card, its status)
     journal-notice.json       a withheld frame carrying the page's toast ("Backup saved")
     journal-counter.json      the page's counter line and meter for sample lengths (jcCounterText, jcMeter), for the screen's choice
     journal-replies.json      every command answered, in the envelope NativeTabKit sends a reply in, with its payload
   The generator fails if a withheld fixture holds any private string of the journal. A fake BiometricLock answers the prompts. */
const { lived } = require('../tests/fixtures/journal-lived.js');
const FAKES = `window.__bio={next:()=>({ok:false,cancelled:true})};
  const P={BiometricLock:{status:async()=>({available:true,kind:'ok'}),authenticate:async o=>window.__bio.next(o),secure:async()=>{}},FileSaver:{saveFile:()=>new Promise(()=>{})}};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

module.exports = async function journal({ b, out, write }) {
  const visit = async (tipsSeen) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await p.clock.setFixedTime(new Date('2026-10-28T12:00:00Z'));
    await p.addInitScript(`localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',tipsSeen:${JSON.stringify(tipsSeen)}}));` + FAKES);
    await p.goto('file://' + out); await p.waitForFunction(() => window.NativeJournalProjection && window.LifeMaxCoach); await p.waitForTimeout(2500);
    await p.evaluate(() => { lmaThumbWarm = async () => { }; navigate('history') });
    return p;
  };
  // the page's toast comes and goes on its own timer, so a fixture carries it only when asked to
  const snap = (p, toast) => p.evaluate((toast) => {
    if (!toast) document.getElementById('toast').classList.remove('show');
    return Object.assign(NativeJournalTabProjection.snapshot(), { webSessionId: 'journal-fixture', sequence: 1 });
  }, !!toast);
  const pick = (p, key) => p.evaluate((key) => { histSel = key; renderHistory() }, key);
  const shoot = async (p, name, key, toast) => { if (key !== undefined) await pick(p, key); write('fixtures/journal-' + name + '.json', await snap(p, toast)); };
  const Y = (o) => new Date(Date.UTC(2026, 9, 28 - o)).toISOString().slice(0, 10);

  let p = await visit({ history: true });
  const seed = await p.evaluate(`(${lived})()`);
  await shoot(p, 'today', Y(0));
  await shoot(p, 'pastday', Y(seed.withAct));
  await shoot(p, 'notes', Y(seed.notesOnly));
  await shoot(p, 'blank', Y(11));
  await shoot(p, 'week', seed.week);
  await shoot(p, 'month', seed.month);
  await p.evaluate(() => { state.dayReflections[new Date(Date.UTC(2026, 9, 25)).toISOString().slice(0, 10)] = 'Honestly I feel like there is no reason to live lately.'; save() });
  await shoot(p, 'care', Y(3));

  // the page's counter for sample lengths: the line it shows (typing or not) and the meter, by awarded and canPay
  write('fixtures/journal-counter.json', await p.evaluate(() => {
    const cases = [];
    for (const [awarded, canPay] of [[false, true], [true, true], [false, false], [true, false]])
      for (const n of [0, 1, 23, 39, 40, 41, 120]) for (const typing of [false, true]) cases.push({ awarded, canPay, n, typing, text: jcCounterText(awarded, canPay, n, typing), meter: jcMeter(awarded, n) });
    return { min: JC.reflectMin, counter: JC_COUNTER, note: 'jcCounterText(awarded, canPay, n, typing) and jcMeter(awarded, n): the page\'s choice among counter\'s four lines', cases };
  }));

  // every command, answered by the page, in the envelope NativeTabKit sends (command and requestId first, never after)
  const replies = await p.evaluate(async () => {
    const C = NativeJournalProjection.commands, outR = {};
    const run = async (name, command, payload) => {
      const r = Object.assign({ command, requestId: 'req-' + name }, await C[command](payload, { requestId: 'req-' + name }));
      delete r.after; outR[name] = { command, payload, reply: r };
    };
    histSel = getToday(); renderHistory();
    const d = getToday(), from = NativeJournalTabProjection.snapshot().entry.reflect.prompt.id;
    const y2 = new Date(Date.UTC(2026, 9, 26)).toISOString().slice(0, 10);
    await run('select', 'select', { key: y2 });
    await run('selectAgain', 'select', { key: y2 });
    await run('selectUnknown', 'select', { key: '1999-01-01' });
    await run('selectReview', 'select', { key: LifeMaxCoach.reviewList(journalDays())[0].key });
    await run('swap', 'swap', { d, from });
    await run('swapAgain', 'swap', { d, from });
    await run('swapNoQuestion', 'swap', { d: '1999-01-01', from: 'PRM_C1' });
    delete state.tipsSeen.history; // the tip showing, so the first dismissal changes it
    await run('tip', 'tip', {});
    await run('tipAgain', 'tip', {});
    await run('backup', 'backup', {});
    await run('write', 'write', { key: d });
    document.activeElement.blur();
    await run('unlockLockOff', 'unlock', {});
    state.journalLock = { on: true }; save(); LifeMaxJournalLock.relock();
    await run('selectLocked', 'select', { key: d });
    await run('writeLocked', 'write', { key: d });
    await run('swapLocked', 'swap', { d, from });
    await run('backupLocked', 'backup', {});
    await run('tipLocked', 'tip', {});
    __bio.next = () => ({ ok: false, cancelled: true }); await run('unlockCancelled', 'unlock', {});
    __bio.next = () => ({ ok: false }); await run('unlockFailed', 'unlock', {});
    __bio.next = () => ({ ok: true }); await run('unlock', 'unlock', {});
    state.journalLock = { on: false }; save();
    return outR;
  });
  write('fixtures/journal-replies.json', Object.assign({
    actions: {
      note: 'actions close the screen; the page runs and the screen comes back once the celebrations have played (NativeTabKit action event)',
      claimPet: { action: 'claim', id: 'pet' }, claimFresh: { action: 'claim', id: 'fresh' }, catchUp: { action: 'catchUp' },
      toMedia: { view: 'media' }, toDirector: { view: 'life-director' },
    },
  }, replies));

  // the lock: withheld frames, idle, prompting and with the error line, and one carrying a toast. None may hold a private string.
  const withheld = async (name, setup, toast) => {
    await p.evaluate(setup);
    const f = await snap(p, toast), json = JSON.stringify(f);
    const leaks = seed.privates.filter((x) => json.includes(x)).concat(/\d{4}-\d\d-\d\d/.test(json) ? ['a date key'] : []);
    if (!f.withheld || leaks.length) throw Error('journal-' + name + ' is not a clean withheld frame: ' + JSON.stringify(leaks));
    write('fixtures/journal-' + name + '.json', f);
  };
  await withheld('locked', () => { state.journalLock = { on: true }; save(); LifeMaxJournalLock.relock() });
  await withheld('locked-prompting', async () => { __bio.next = () => new Promise(() => { }); LifeMaxJournalLock.prompt(); await new Promise((r) => setTimeout(r, 50)) });
  await p.close();
  p = await visit({ history: true });
  await p.evaluate(`(${lived})()`);
  await withheld('locked-error', async () => { state.journalLock = { on: true }; save(); LifeMaxJournalLock.relock(); __bio.next = () => ({ ok: false }); await LifeMaxJournalLock.prompt() });
  await withheld('notice', () => toast('Backup saved'), true);
  await p.close();

  // the feature tip, on the same journal
  p = await visit({});
  await p.evaluate(`(${lived})()`);
  await shoot(p, 'tip', Y(0));
  await p.close();

  // the welcome card's tiers (3-6 quiet days, 7-13, 14 or more), one with its chip claimed
  const welcome = async (name, gap, claimed) => {
    const q = await visit({ history: true });
    await q.evaluate(({ gap, claimed }) => {
      const at = (o) => new Date(Date.UTC(2026, 9, 28 - o, 9, 5)).toISOString();
      state.activities = []; state.dayReflections = {}; state.journalCoach = {}; state.plan = null;
      for (let o = gap + 1; o <= gap + 16; o++) state.activities.push({ id: 'w' + o, type: 'custom', name: 'Study', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: '', xp: 90 + ((o * 11) % 40), date: at(o), notes: '', xpSplit: [{ attr: 'intelligence', xp: 90 + ((o * 11) % 40) }] });
      state.activities.push({ id: 'w0', type: 'custom', name: 'Back at it', icon: 'book', duration: 30, quality: 1, skill: 'Back', primary: 'discipline', secondary: '', xp: 60, date: at(0), notes: '', xpSplit: [{ attr: 'discipline', xp: 60 }] });
      save(); histSel = getToday(); renderHistory();
      if (claimed) { LifeMaxCoach.store().welcome[getToday()].claimed[claimed] = 1; save() }
    }, { gap, claimed });
    await shoot(q, name);
    await q.close();
  };
  await welcome('welcome1', 4);
  await welcome('welcome1-claimed', 4, 'pet');
  await welcome('welcome2', 9);
  await welcome('welcome3', 20);

  // an empty journal, and a long one (400 days: the strip's size)
  p = await visit({ history: true });
  await p.evaluate(() => { state.activities = []; state.dayReflections = {}; state.journalCoach = {}; state.plan = null; save(); renderHistory() });
  await shoot(p, 'empty');
  await p.evaluate(() => {
    for (let o = 0; o < 400; o++) if (o % 9 !== 4) state.activities.push({ id: 'l' + o, type: 'custom', name: 'Study', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: o % 2 ? 'intelligence' : 'endurance', secondary: '', xp: 60 + ((o * 17) % 90), date: new Date(Date.UTC(2026, 9, 28 - o, 9, 5)).toISOString(), notes: '', xpSplit: [{ attr: o % 2 ? 'intelligence' : 'endurance', xp: 60 + ((o * 17) % 90) }] });
    save(); histSel = getToday(); renderHistory();
  });
  await shoot(p, 'longstrip');
  await p.close();
};
