/* The native Overview cards' fixtures (overview.cards.v1), the overview job of tools/gen_native_fixtures.js: what the page projects
   for a screen that draws its cards (NativeOverviewCardsProjection.snapshot()), on the golden saves of tests/fixtures/saves at the
   clocks tests/overview-classic.test.js uses, and the states on top of them:
     overview-cards-{new,established,legacy,extras}.json   the four golden saves (Android: apps and health tiles present, health off)
     overview-cards-pet.json          a hungry cat; overview-cards-two-cats.json two cats; overview-cards-cat-gone.json in memory;
                                      overview-cards-no-cat.json no cat (tiles.cat null) with an avatar (images)
     overview-cards-pet-live.json     a weak (hungry 30 h) cat with Native cats (beta) on: tiles.cat.live carries its coat, stage and weakness;
                                      overview-cards-kitten-live.json a tuxedo kitten (stage 2). Every other fixture has live: null, except
                                      overview-cards-{pet,point,kitten}-live-dna.json: a calico, a colour-point and a long-haired kitten (stage 1)
                                      with genetic coats, sent live because the screen lists overview.cat.coat.v1.
     overview-cards-health.json       health on, partway to the goal, with apps (one with its icon); overview-cards-health-goal.json
                                      the goal reached; overview-cards-browser.json outside the Android app (apps and health null)
     overview-cards-luck.json         luck rolled; overview-cards-over-pace.json today's XP over the pace; overview-cards-tip.json
                                      the feature tip; overview-cards-celebration.json a celebration's held orbs, handed over
                                      while the cards show; overview-cards-notice.json the page's toast showing
     overview-glance-{setup,day,timer}.json  LifeDirector.native.glance() on tests/fixtures/director-day.js (tiles.plan)
     overview-cards-replies.json      every command answered, in the envelope NativeTabKit sends a reply in (the editor's layout
                                      and layoutReset too, one refused for storage full)
     overview-hapscale.json           the page's hapScale at every haptic level, which native HapticScale must equal
   A fake NativeOverviewCards plugin lets the cards open (a celebration is handed over only to an open screen). */
const STORE = 'lifemax_rpg_prototype_v1';
const ICON = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
const BASES = [
  { name: 'new', clock: '2026-10-30T12:00:00Z' },
  { name: 'established', clock: '2026-10-14T21:30:00Z' },
  { name: 'legacy', clock: '2024-08-01T09:00:00Z' },
  { name: 'extras', clock: '2026-10-30T12:00:00Z' },
];
const cat = (name, o) => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-3*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:0,care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o || '{}'})`;
/* states on top of the established save, set up in the page (now is the fixed clock) */
const STATES = {
  'pet': `state.pet=${cat('Miso', '{lastFed:now-20*36e5,bondPts:PET_BOND_AT[3]}')}`,
  'pet-live': `state.pet=${cat('Miso', '{lastFed:now-30*36e5,bondPts:PET_BOND_AT[3]}')};lmaLoad().nativeCats=true`,
  'kitten-live': `state.pet=${cat('Pip', '{coat:"tuxedo",born:now-12*864e5}')};lmaLoad().nativeCats=true`,
  'pet-live-dna': `state.pet=${cat('Miso', '{coat:"dna_black_1_n_2_0_n_0_3"}')};petDnaRegister(state.pet.coat);lmaLoad().nativeCats=true`,
  'point-live-dna': `state.pet=${cat('Miso', '{coat:"dna_black_0_n_0_0_cs_0_4"}')};petDnaRegister(state.pet.coat);lmaLoad().nativeCats=true`,
  'kitten-live-dna': `state.pet=${cat('Pip', '{coat:"dna_red_0_m_3_0_n_1_2",born:now-6*864e5}')};petDnaRegister(state.pet.coat);lmaLoad().nativeCats=true`,
  'two-cats': `state.pet=${cat('Miso', '{bondPts:PET_BOND_AT[2]}')};state.pet2=${cat('Pip', '{coat:"gray"}')}`,
  'cat-gone': `state.pet=${cat('Miso', '{alive:false,passed:now-3*864e5}')}`,
  'no-cat': `delete state.pet;delete state.pet2;state.avatar=${JSON.stringify(ICON)}`,
  'health': `Object.assign(lmaHealthCfg(),{on:true,goal:10000,src:'Samsung Health',last:now-7*6e4,today:{date:getToday(),count:6420},
      snap:{steps:[3100,12050,8000,0,9400,15000,6420].map((c,i)=>({date:new Date(now-(6-i)*864e5).toLocaleDateString('en-CA'),count:c})),ex:{count:2,min:95},sleep:{min:452},wt:181.4}});
      lmaLoad().apps=[{pkg:'com.example.maps',label:'Maps & more',icon:${JSON.stringify(ICON)}},{pkg:'com.example.two',label:'Two'}]`,
  'health-goal': `Object.assign(lmaHealthCfg(),{on:true,goal:5000,src:'',last:now-26*36e5,today:{date:getToday(),count:5000},snap:{steps:[{date:getToday(),count:5000}],ex:{count:0,min:0}}})`,
  'browser': `lmaNative=()=>false`,
  'luck': `const dice=[6,3,5,2];state.luck={date:getToday(),dice:dice,score:luckCompute(dice).score,ver:LUCK_VER}`,
  'over-pace': `state.activities=state.activities.concat([Object.assign({},state.activities[state.activities.length-1],{id:'ov1',name:'Long ride',date:new Date(now-20e3).toISOString(),xp:620,duration:180})])`,
  'tip': `state.tipsSeen={}`,
};
const FAKE = `(() => { const ls = {}; window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeOverviewCards', getPlatform: () => 'android',
  registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativeOverviewCards: { addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: ['overview.cards.v1', 'overview.cat.live.v1', 'overview.cat.coat.v1'] }), open: async () => {}, update: async () => {}, close: async () => { (ls.closed || []).forEach(f => f({})) } } } } })()`;

module.exports = async function overview({ b, out, write, directorDay }) {
  const fs = require('fs'), path = require('path');
  const golden = (n) => JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'saves', n + '.json'), 'utf8'));
  const visit = async (base) => {
    const g = golden(base.name), p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await p.clock.setFixedTime(new Date(base.clock));
    await p.addInitScript(([STORE, save, extras]) => {
      localStorage.setItem(STORE, save); for (const k in extras) localStorage.setItem(k, extras[k]);
      localStorage.setItem('lifemax_android_prefs_v1', JSON.stringify({ nativeOverviewCards: true, nativeOverview: false, apps: [] }));
    }, [STORE, JSON.stringify({ ...g.state, tutorialDone: true, tipsSeen: { ...(g.state.tipsSeen || {}), dashboard: true } }), g.extras || {}]);
    await p.addInitScript(FAKE);
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } if (window.AppsShortcuts) AppsShortcuts.loadIcons = () => Promise.resolve({}) });
    await p.waitForFunction(() => NativeOverviewCards.active(), null, { timeout: 30000 });
    await p.evaluate(() => { window.__base = { state: JSON.stringify(state), prefs: JSON.stringify(lmaLoad()), health: JSON.stringify(lmaHealthCfg()), native: lmaNative } });
    return p;
  };
  // the toast comes and goes on its own timer, so a fixture carries it only when asked to
  const shoot = (p, name, setup, withToast = false) => p.evaluate(async ([setup, withToast]) => {
    const B = window.__base, s = JSON.parse(B.state), pr = JSON.parse(B.prefs), P = lmaLoad();
    Object.keys(state).forEach((k) => delete state[k]); Object.assign(state, s);
    Object.keys(P).forEach((k) => delete P[k]); Object.assign(P, pr); Object.assign(lmaHealthCfg(), JSON.parse(B.health));
    lmaNative = B.native; OV_SEL = 6;
    (0, eval)('(function(){const now=Date.now();' + (setup || '') + '})()');
    renderOverview(); renderPetPeek();
    // a living cat's portrait comes once the room's art has loaded (petPortrait answers null until then)
    for (let t = 0; t < 8000 && state.pet && state.pet.alive && !petPortrait(state.pet); t += 100) await new Promise((r) => setTimeout(r, 100));
    if (withToast) toast('Saved · +12 XP'); else document.getElementById('toast').classList.remove('show');
    return Object.assign(NativeOverviewCardsProjection.snapshot(), { webSessionId: 'overview-fixture', sequence: 1 });
  }, [setup, withToast]).then((s) => write('fixtures/overview-cards-' + name + '.json', s));

  for (const base of BASES) {
    const p = await visit(base);
    await shoot(p, base.name, '');
    if (base.name === 'established') {
      for (const [name, setup] of Object.entries(STATES)) await shoot(p, name, setup);
      await shoot(p, 'notice', '', true);
      // a celebration handed over while the cards show (a health sync's XP, or Native Log's held orbs): the page draws nothing
      await p.evaluate(() => { lastTap = { x: 206, y: 740 }; celebrateXP(25, [{ id: 'physique', xp: 20 }, { id: 'intelligence', xp: 5 }]) });
      await shoot(p, 'celebration', '');
      write('fixtures/overview-cards-replies.json', await p.evaluate(replies));
      write('fixtures/overview-hapscale.json', await p.evaluate(() => Object.keys(HAP_LEVELS).map((k) => {
        const L = HAP_LEVELS[k];
        return { level: k, dm: L.dm, add: L.add, amp: L.amp, min: L.min, max: HAP_MAX,
          samples: [1, 4, 5, 6, 8, 10, 12, 18, 30, 60, 160, 400].map((ms) => ({ in: ms, out: hapScale(ms, L) })).concat([{ in: [8, 30, 8], out: hapScale([8, 30, 8], L) }, { in: [18, 30, 18], out: hapScale([18, 30, 18], L) }]) };
      })));
    }
    await p.context().close();
  }

  /* Today's plan: LifeDirector.native.glance() before set-up, on the lived-in day, and with a timer running */
  const glance = async (planned, name, timer) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    await directorDay.seed(p, { planned });
    await p.goto('file://' + out, { timeout: 120000 });
    await p.evaluate(() => { lmaThumbWarm = async () => { } });
    if (planned) await directorDay.play(p); else await p.clock.runFor(3000);
    if (timer) {
      await p.clock.pauseAt(directorDay.at('17:45'));
      await p.evaluate(() => { const s = LifeDirector.native.today(0).dial.arcs.find((a) => a.act === 'inst' && a.state === 'plan' && a.s > Date.now() / 1000); LifeDirector.native.todayRun('timer', { act: 'start', id: s.id }) });
      await p.clock.runFor(65000);
    }
    write('fixtures/overview-glance-' + name + '.json', await p.evaluate(() => LifeDirector.native.glance()));
    await p.context().close();
  };
  await glance(false, 'setup');
  await glance(true, 'day');
  await glance(true, 'timer', true);
};

/* in the page: each command as the screen sends it, answered in the kit's envelope ({command, requestId, ok, ...}); what it opened
   over the page is closed again before the next */
function replies() {
  const STORE_KEY = 'lifemax_rpg_prototype_v1', C = NativeOverviewCardsProjection.commands, out = {};
  const run = (key, command, payload) => {
    let r;
    try { r = C[command] ? C[command](payload) || {} : { ok: false, message: 'Unknown command' } } catch (e) { r = { ok: false, message: 'Something went wrong. Nothing was changed.' } }
    r = Object.assign({ command, requestId: 'req-' + key }, r);
    out[key] = { command, payload, reply: r };
    document.querySelectorAll('.hsheet,.apps-sheet').forEach((e) => e.remove());
    if (typeof shAttr !== 'undefined' && shAttr) closeAttr();
    if (typeof LK !== 'undefined' && LK.ov && LK.ov.classList.contains('on')) luckClose();
    if (!document.getElementById('ovEdit').hidden) ovEditClose();
    if (lmaView() !== 'dashboard') navigate('dashboard');
  };
  state.tipsSeen = {}; delete state.luck;
  window.AppsShortcuts.launch = () => {}; lmaLoad().apps = [{ pkg: 'com.example.maps', label: 'Maps' }];
  run('tip', 'tip', {});
  run('tipAgain', 'tip', {});
  run('luck', 'luck', {});
  run('luckAgain', 'luck', {});
  run('attr', 'attr', { id: 'physique', tile: 'attrs' });
  run('attrUnknown', 'attr', { id: 'nope', tile: 'attrs' });
  run('shop', 'shop', {});
  run('health', 'health', {});
  run('appLaunch', 'appLaunch', { pkg: 'com.example.maps' });
  run('appLaunchUnknown', 'appLaunch', { pkg: 'com.not.added' });
  run('appAdd', 'appAdd', {});
  run('appsEdit', 'appsEdit', {});
  run('edit', 'edit', {});
  // the native editor (phase 2): a layout stored, the same again, one the save refuses (storage full), and Reset
  const L = { order: OV_DEFAULT.slice().reverse(), hidden: ['luck'] };
  run('layout', 'layout', L);
  run('layoutAgain', 'layout', L);
  run('layoutBad', 'layout', { order: 'plan' });
  const real = Storage.prototype.setItem;
  Storage.prototype.setItem = function (k) { if (k === STORE_KEY) throw new Error('QuotaExceededError'); return real.apply(this, arguments) };
  try { run('layoutFull', 'layout', { order: OV_DEFAULT.slice(), hidden: [] }) } finally { Storage.prototype.setItem = real }
  run('layoutReset', 'layoutReset', {});
  run('planOpen', 'plan', { act: '', id: '' });
  run('planGone', 'plan', { act: 'inst', id: 'no-such-session' });
  run('unknown', 'nope', {});
  return out;
}
