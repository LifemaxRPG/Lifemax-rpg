/* Native Overview cards (overview.cards.v1), web half (plans/OVERVIEW_BODY.md, phase 1):
   - the setting (off by default, "(beta)"), the boot open after the theme, and the composite: the live shell never opens while
     the cards are wanted, and the undecided drawing never flushes the hand-over;
   - the projection against the page's own cards: moves, the ring's arcs, the week's bars, the gems' dots, luck, recent, the head,
     the cat, the tile order (ovLayout(), before any save);
   - every command and action, with the screen stepping aside and coming back: the attribute sheet (its tile in view first),
     Luck (no update between the roll's save and its overlay; a second roll changes nothing), the shop, Health, the app picker,
     the Apps tab, an app launched and an unknown one refused, the tip (twice), the web editor, the glance, a {view} action;
   - each covering rule: the voice HUD (through lmvHudOpen), a level-up, a celebration the page plays, the thumbnail pass, the
     tutorial, a resize past 860 and back; the pet loop stopped while the cards cover the page;
   - celebrations with real timers: a native Log save hands its held orbs to the cards (no .fx-xp, the cue 120 ms later); with the
     HUD open the page plays them and the cards come back after; a level-up steps them aside; XP earned while they show is theirs;
   - notices; an app icon arriving refreshes the screen;
   - an old APK (no plugin) keeps the live shell; giving up (a missing capability, an open or an update rejected) hands the view to
     the live shell for the session, with no retry, until the switch is turned off and on; the cross-close both ways.
   Fake NativeOverviewCards and NativeOverview plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-overview-cards.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', 'established.json'), 'utf8'));
const ICON = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

/* the fakes: window.__cards configures the cards plugin ({caps, openFails, updateFails}); window.__calls records every call */
const FAKES = (cardsAvailable) => `
  // every toast the page writes, from the first (a boot open answers before the page has finished loading)
  window.__toasts = []; new MutationObserver(rs => rs.forEach(r => { if (r.target.id === 'toast') r.addedNodes.forEach(n => __toasts.push(n.textContent)) })).observe(document, { childList: true, subtree: true });
  window.__calls = []; window.__cards = { caps: ['overview.cards.v1'], openFails: false, updateFails: false };
  const mk = (name, cards) => { const ls = {}; return {
    __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a, performance.now()]); if (cards && window.__cards.openFails) throw Error('decoder rejected') },
    update: async a => { window.__calls.push([name, 'update', a, performance.now()]); if (cards && window.__cards.updateFails) throw Error('decoder rejected') },
    close: async a => { window.__calls.push([name, 'close', a, performance.now()]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => { window.__calls.push([name, 'handshake']); return { protocol: 1, capabilities: cards ? window.__cards.caps : [] } } } };
  const P = { NativeOverview: mk('overview', false) };
  if (${cardsAvailable}) P.NativeOverviewCards = mk('cards', true);
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => !!P[n], getPlatform: () => 'android', Plugins: P, registerPlugin: () => ({}), convertFileSrc: u => u };`;

async function start(b, { cardsAvailable = true, cardsOn = true, cards = {} } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  const now = Date.now();
  const save = Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: {}, name: 'Ada',
    pet: { v: 1, name: 'Miso', coat: 'ginger', trait: 'curious', born: now - 40 * 864e5, lastFed: now - 3 * 36e5, meals: [], day: '', fedToday: 0, petsToday: 0, playsToday: 0, bondPts: 0, care: 0, vacation: { on: false, since: 0 }, gifts: 0, lastGiftDay: '', alive: true, passed: 0, seen: {}, greeted: 0 } });
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(save), golden.extras || {}, JSON.stringify({ nativeOverviewCards: cardsOn, apps: [{ pkg: 'com.example.maps', label: 'Maps' }] })]);
  await p.addInitScript(FAKES(cardsAvailable));
  await p.addInitScript(c => { Object.assign(window.__cards, c) }, cards);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } });
  return p;
}
/* in the page: helpers every check uses */
const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.count = (n, k) => __calls.filter(c => c[0] === n && c[1] === k).length;
  window.last = (n, k) => [...__calls].reverse().find(c => c[0] === n && c[1] === k);
  window.showing = (n = 'cards') => { const o = last(n, 'open'), c = last(n, 'close'); return !!o && (!c || __calls.lastIndexOf(o) > __calls.lastIndexOf(c)) };
  window.latest = () => { const c = [...__calls].reverse().find(c => c[0] === 'cards' && (c[1] === 'open' || c[1] === 'update')); return c && c[2].snapshot };
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  window.command = async (command, payload) => { const n = __calls.length; await Capacitor.Plugins.NativeOverviewCards.__emit('command', { command, payload, requestId: 'r-' + command }); await wait(250);
    const u = __calls.slice(n).find(c => c[0] === 'cards' && c[1] === 'update' && c[2].reply); return u ? u[2].reply : null };
  window.asideAndBack = async (cover, uncover, ms = 4000) => { await cover(); const aside = await until(() => !showing(), 2000); await uncover(); const back = await until(() => showing(), ms); return { aside, back } };`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the switch is on: the boot open, the composite, the projection against the page */
    let p = await start(b);
    await p.evaluate(HELPERS);
    const boot = await p.evaluate(async () => {
      await until(() => showing(), 6000);
      const o = last('cards', 'open');
      return { opened: !!o, at: o && o[3], hybrid: count('overview', 'open'), accent: o && o[2].snapshot.tokens.accent, now: NativeTabKit.tokens().accent, view: lmaView() };
    });
    ok('boot: the cards open on the Overview after the theme pass (900 ms), the live shell never', boot.opened && boot.at >= 880 && boot.hybrid === 0 && boot.view === 'dashboard', boot);
    ok('boot: the first projection has the page\'s accent', boot.accent === boot.now, boot);

    const proj = await p.evaluate(() => {
      const s = latest(), T = s.tiles, txt = e => e ? e.textContent.replace(/\s+/g, ' ').trim() : null, all = q => [...document.querySelectorAll(q)];
      const out = {};
      out.shape = s.protocol === 1 && typeof s.webSessionId === 'string' && typeof s.sequence === 'number' && typeof s.tokens.accent === 'number' && typeof s.light === 'boolean'
        && ['plan', 'next', 'hero', 'rewards', 'apps', 'health', 'attrs', 'week', 'cat', 'luck', 'recent'].every(k => k in T) && Array.isArray(s.celebrations) && s.motion && typeof s.motion.reduced === 'boolean'
        && s.celebrationTarget && typeof s.celebrationTarget.x === 'number' && 'notice' in s && typeof s.images === 'object';
      out.head = s.head.date === txt(document.getElementById('ovDate')) && s.head.hello === txt(document.getElementById('ovHello')) && s.head.edit.label === 'Edit' && s.head.edit.a11y === document.getElementById('ovEditBtn').getAttribute('aria-label');
      out.tip = !!s.tip && s.tip.key === 'dashboard' && s.tip.title === FEATURE_TIPS.dashboard.title && s.tip.dismiss === 'Dismiss tip';
      out.order = JSON.stringify(s.layout) === JSON.stringify(ovLayout()) && JSON.stringify(all('#ovTiles > .ov-tile').map(t => t.dataset.tile)) === JSON.stringify(ovLayout().order)
        && JSON.stringify(s.tileInfo.map(t => t.id)) === JSON.stringify(OV_DEFAULT) && s.tileInfo.every(t => typeof t.color === 'number' && typeof t.note === 'string')
        && s.tileInfo.find(t => t.id === 'cat').sub === 'Miso’s room';
      out.moves = JSON.stringify(T.next.moves.map(m => m.title)) === JSON.stringify(all('#ovNext .ov-move b').map(txt)) && T.next.moves.every(m => typeof m.color === 'number' && m.go && ('view' in m.go || 'attr' in m.go || 'luck' in m.go));
      out.arcs = JSON.stringify(T.hero.ring.arcs.map(a => a.length.toFixed(2) + ' ' + (100 - a.length).toFixed(2) + '|' + a.offset.toFixed(2)))
        === JSON.stringify(all('#ovHero .ov-seg').map(c => c.getAttribute('stroke-dasharray') + '|' + c.getAttribute('stroke-dashoffset'))) && T.hero.ring.r === 62;
      out.hero = String(T.hero.ring.today) === document.getElementById('ovToday').dataset.v && txt(document.querySelector('#ovHero .ov-title')).startsWith(T.hero.title)
        && T.hero.level === +txt(document.querySelector('#ovHero .ov-lvbadge')) && T.hero.xp.text === txt(document.querySelector('#ovHero .ov-xpt span'));
      out.bars = JSON.stringify(T.week.bars.map(x => x.a11y)) === JSON.stringify(all('#ovWeek .ov-bar').map(x => x.getAttribute('aria-label'))) && T.week.selected === OV_SEL;
      out.gems = JSON.stringify(T.attrs.gems.map(g => 's-' + g.dot.kind)) === JSON.stringify(all('#ovAttrs .ov-gems .ov-dot').map(d => d.className.split(' ').find(c => c.startsWith('s-'))));
      out.luck = T.luck.text === txt(document.querySelector('#ovLuck .ov-luck-t')) && T.luck.score === txt(document.querySelector('#ovLuck .ov-luck-n'));
      out.recent = JSON.stringify(T.recent.items.map(x => x.name)) === JSON.stringify(all('#ovRecent .ov-fi b').map(txt));
      out.cat = T.cat && T.cat.mode === 'alive' && T.cat.name === txt(document.querySelector('#petPeek strong')) && typeof T.cat.hearts === 'number';
      out.rewards = JSON.stringify(T.rewards.chips) === JSON.stringify(all('#ovRewards .ovh-chip').map(c => ({ label: txt(c.querySelector('span')), value: txt(c.querySelector('b')) })));
      out.apps = T.apps && T.apps.items.length === 1 && T.apps.items[0].pkg === 'com.example.maps' && T.apps.items[0].image === null && T.apps.add === 'Add app';
      out.health = T.health && T.health.on === false && T.health.button === 'Set up health sync';
      out.haptics = s.haptics === null || (typeof s.haptics.dm === 'number' && s.haptics.max === 260);
      out.plan = T.plan === null || ['setup', 'none', 'day'].includes(T.plan.state);
      return out;
    });
    for (const [k, v] of Object.entries(proj)) ok('projection: ' + k + ' says what the page draws', v);

    /* every command and action */
    const cmd = await p.evaluate(async () => {
      const out = {};
      const r1 = await command('tip', {}); const r2 = await command('tip', {});
      out.tip = r1 && r1.ok && r1.changed === true && latest().tip === null && state.tipsSeen.dashboard === true && r2 && r2.ok && r2.changed === false && r2.message === 'Already recorded.';
      out.tipDetail = [r1, r2];
      const bad = await command('appLaunch', { pkg: 'com.evil' });
      out.appUnknown = bad && bad.ok === false && typeof bad.message === 'string';
      let launched = null; AppsShortcuts.launch = pkg => { launched = pkg }; const good = await command('appLaunch', { pkg: 'com.example.maps' });
      out.appLaunch = good && good.ok && launched === 'com.example.maps' && showing();
      out.unknown = (await command('nope', {})).ok === false;

      // Luck: no update carries the roll before the overlay steps the screen aside; it comes back on close; a second roll changes nothing
      const n = __calls.length;
      const luck = await asideAndBack(() => command('luck', {}), async () => { await wait(300); luckClose() });
      const leaked = __calls.slice(n).filter(c => c[0] === 'cards' && c[1] === 'update' && c[2].snapshot.tiles.luck.rolled && __calls.indexOf(c) < __calls.findIndex((x, i) => i >= n && x[0] === 'cards' && x[1] === 'close'));
      out.luck = luck.aside && luck.back && !!state.luck && leaked.length === 0 && latest().tiles.luck.rolled;
      const score = state.luck.score, log = JSON.stringify(state.luckLog);
      const luck2 = await asideAndBack(() => command('luck', {}), async () => { await wait(300); luckClose() });
      out.luckAgain = luck2.aside && luck2.back && state.luck.score === score && JSON.stringify(state.luckLog) === log;
      out.luckDetail = { luck, luck2, leaked: leaked.length };

      // the attribute sheet: the tapped tile in view behind it
      const attr = await asideAndBack(async () => { scrollTo(0, 0); await command('attr', { id: 'physique', tile: 'week' }) }, async () => {
        const r = document.querySelector('#ovTiles > .ov-tile[data-tile="week"]').getBoundingClientRect(); out.tileInView = r.top < innerHeight && r.bottom > 0 && scrollY > 0;
        out.sheet = !!shAttr && document.body.classList.contains('noscroll'); closeAttr() });
      out.attr = attr.aside && attr.back && out.tileInView && out.sheet;
      out.attrBad = (await command('attr', { id: 'nope', tile: 'attrs' })).ok === false;
      const shop = await asideAndBack(() => command('shop', {}), async () => { out.shopSheet = !!document.querySelector('.hsheet'); document.querySelector('.hsheet').remove() });
      out.shop = shop.aside && shop.back && out.shopSheet;
      const health = await asideAndBack(() => command('health', {}), async () => { out.healthSheet = !!document.querySelector('.hsheet'); document.querySelector('.hsheet').remove() });
      out.health = health.aside && health.back && out.healthSheet;
      const picker = await asideAndBack(() => command('appAdd', {}), async () => { out.pickerSheet = !!document.querySelector('.apps-sheet'); document.querySelectorAll('.apps-sheet').forEach(e => e.remove()) });
      out.appAdd = picker.aside && picker.back && out.pickerSheet;
      const edit = await asideAndBack(() => command('edit', {}), async () => { out.editorShown = !document.getElementById('ovEdit').hidden; ovEditClose() });
      out.edit = edit.aside && edit.back && out.editorShown;
      const back = await asideAndBack(() => command('edit', {}), async () => { lmaBack() });
      out.editBack = back.aside && back.back && lmaView() === 'dashboard';
      return out;
    });
    for (const k of ['tip', 'appUnknown', 'appLaunch', 'unknown', 'luck', 'luckAgain', 'attr', 'attrBad', 'shop', 'health', 'appAdd', 'edit', 'editBack'])
      ok('command ' + k + ': runs on the page, the screen steps aside and comes back as it should', cmd[k], cmd);

    /* covering rules */
    const cover = await p.evaluate(async () => {
      const out = {};
      if (!lmvUI) lmvBuildUI();
      const hud = await asideAndBack(() => lmvHudOpen(true), () => lmvHudOpen(false));
      out.hud = hud.aside && hud.back;
      const thumb = await asideAndBack(() => { lmaThumbWarmBusy = true; NativeOverviewCards.diagnostics(); dispatchEvent(new Event('resize')) }, () => { lmaThumbWarmBusy = false; dispatchEvent(new Event('resize')) });
      out.thumb = thumb.aside && thumb.back;
      out.petWhileCovering = showing() && NativeOverviewCards.covering() && petWantRun() === false;
      const lu = await asideAndBack(() => showLevelUp({ charLevel: null, attrs: [{ id: 'physique', short: 'Physique', from: 3, level: 4 }] }), async () => {
        out.petWhileAside = petWantRun() === true; await wait(800); document.querySelector('.luov').click() }, 3000);
      out.levelUp = lu.aside && lu.back;
      const tut = await asideAndBack(() => { document.getElementById('tutorial').classList.add('show'); document.body.classList.add('x-tut') }, () => { document.getElementById('tutorial').classList.remove('show'); document.body.classList.remove('x-tut') });
      out.tutorial = tut.aside && tut.back;
      return out;
    });
    for (const [k, v] of Object.entries(cover)) ok('covering: ' + k, v, cover);
    await p.setViewportSize({ width: 900, height: 860 });
    ok('covering: wider than 860 steps the cards aside', await p.evaluate(() => until(() => !showing(), 2000)));
    await p.setViewportSize({ width: 412, height: 860 });
    ok('covering: back under 860 brings them back', await p.evaluate(() => until(() => showing(), 3000)));

    /* notices; an app icon arriving */
    const misc = await p.evaluate(async (ICON) => {
      const out = {};
      toast('Saved your thing'); await until(() => latest().notice && latest().notice.text === 'Saved your thing', 2000);
      const nt = latest().notice; out.notice = !!nt && typeof nt.id === 'number' && nt.text === 'Saved your thing';
      lmaLoad().apps[0].icon = ICON; ovAppsRender(); await until(() => latest().tiles.apps.items[0].image, 2000);
      const s = latest(), key = s.tiles.apps.items[0].image; out.icon = !!key && s.images[key] === ICON;
      return out;
    }, ICON);
    ok('notices: the page\'s toast reaches the screen', misc.notice, misc);
    ok('an app icon arriving refreshes the open screen, by key', misc.icon, misc);

    /* celebrations, real timers */
    const fx = await p.evaluate(async () => {
      const out = {}, cues = []; const cue = xpCue; xpCue = d => { cues.push([d, performance.now()]); return cue(d) };
      // XP earned while the cards show (a health sync's): drawn by the cards
      const n0 = __calls.length, t0 = performance.now(); lastTap = { x: 200, y: 700 }; celebrateXP(15, [{ id: 'physique', xp: 15 }]);
      await wait(250);
      const c0 = latest().celebrations; out.sink = c0.length === 1 && c0[0].xp === 15 && c0[0].gains[0].attribute === 'physique' && typeof c0[0].gains[0].color === 'number'
        && c0[0].origin.x === 200 && !document.querySelector('.fx-xp') && showing() && cues.length === 1 && cues[0][1] - t0 >= 100;
      out.sinkDetail = { c0, cues: cues.length };
      await wait(2200);
      await NativeOverviewCards.close().then(() => NativeOverviewCards.open()); await until(() => showing());
      out.kept = latest().celebrations.length === 0; // kept 2 s only
      // a native Log save: the cards open before Log's held orbs play, and draw them
      lmaNavTo('log', { fast: true }); await until(() => !showing(), 2000);
      const d = NativeLogProjection.draftFor('reading'), opens = count('cards', 'open');
      const res = NativeLogProjection.commands.submit({ draft: d, operationId: 'op-' + Date.now() }); const t1 = performance.now();
      lmaNavTo('dashboard', { fast: true });
      await wait(800);
      const open = __calls.filter(c => c[0] === 'cards' && c[1] === 'open').slice(opens)[0];
      const got = latest().celebrations;
      out.log = res.ok && !!open && open[3] - t1 < 450 && got.length >= 1 && !document.querySelector('.fx-xp') && showing();
      out.logDetail = { res: res.ok, openAfter: open && open[3] - t1, got: got.length, fx: !!document.querySelector('.fx-xp') };
      await wait(2200);
      // the same save with the HUD open: the page plays the orbs, the HUD closes at 1 s, the cards come back once they are done
      lmaNavTo('log', { fast: true }); await until(() => !showing(), 2000);
      const d2 = NativeLogProjection.draftFor('reading'), res2 = NativeLogProjection.commands.submit({ draft: d2, operationId: 'op2-' + Date.now() }); const t2 = performance.now();
      lmvHudOpen(true); lmaNavTo('dashboard', { fast: true });
      let pagePlayed = false; const watchFx = setInterval(() => { if (document.querySelector('.fx-xp')) pagePlayed = true }, 20);
      await wait(1000 - (performance.now() - t2)); const awayAt1s = !showing(); lmvHudOpen(false);
      await wait(500); const stillAway = !showing();
      await until(() => showing(), 3000); const backAt = performance.now() - t2; clearInterval(watchFx);
      out.hud = res2.ok && pagePlayed && awayAt1s && stillAway && backAt > 1900 && backAt < 3000;
      out.hudDetail = { pagePlayed, awayAt1s, stillAway, backAt };
      xpCue = cue;
      return out;
    });
    ok('celebration: XP earned while the cards show is drawn by them (no .fx-xp; the cue 120 ms later)', fx.sink, fx.sinkDetail);
    ok('celebration: a handed-over celebration stays 2 s in the projection', fx.kept);
    ok('celebration: a native Log save — the cards open before 450 ms and draw its held orbs', fx.log, fx.logDetail);
    ok('celebration: the same save with the HUD open — the page plays it, the cards come back by about 2.1 s', fx.hud, fx.hudDetail);

    /* actions and navigating commands */
    const nav = await p.evaluate(async () => {
      const out = {};
      const apps = await command('appsEdit', {}); await wait(300);
      out.appsEdit = apps && apps.ok && apps.navigate === 'apps' && lmaView() === 'apps' && !showing();
      lmaNavTo('dashboard', { fast: true }); await until(() => showing());
      await Capacitor.Plugins.NativeOverviewCards.__emit('action', { view: 'character' }); await wait(300);
      out.view = lmaView() === 'character' && !showing();
      lmaNavTo('dashboard', { fast: true }); await until(() => showing());
      // the hand-over: no flush from the live shell while the cards are wanted, and it never opens
      const flushes = []; const f = NativeTabs.flush; NativeTabs.flush = function (id) { flushes.push(id); return f.apply(this, arguments) };
      lmaNavTo('quests', { fast: true }); await wait(200); lmaNavTo('dashboard', { fast: true }); await until(() => showing()); await wait(300);
      NativeTabs.flush = f; out.noFlush = !flushes.includes('overview') && count('overview', 'open') === 0;
      // native tab swipe sees the Overview as a native neighbour while the cards are wanted (the composite's body and wouldOpen)
      out.swipeNative = NativeTabs.webBody('dashboard') === false && NativeTabs.wouldOpen('dashboard') === true && NativeTabs.covered('dashboard') === true;
      await command('plan', { act: '', id: '' }); await wait(300);
      out.plan = lmaView() === 'life-director' && !showing();
      return out;
    });
    for (const [k, v] of Object.entries(nav)) ok('navigation: ' + k, v, nav);
    ok('no page errors (cards on)', !p.errors.length, p.errors);
    await p.context().close();

    /* 2. the setting, an old APK, and the cross-close */
    p = await start(b, { cardsOn: false });
    await p.evaluate(HELPERS);
    const set = await p.evaluate(async () => {
      const out = {};
      await until(() => showing('overview'), 6000);
      out.shellByDefault = showing('overview') && count('cards', 'open') === 0;
      out.swipeWall = NativeTabs.webBody('dashboard') === true; // the live shell draws the body on the page: a wall to the native swipe
      navigate('settings'); await wait(50);
      const t = document.getElementById('nativeOverviewCardsToggle');
      out.row = !!t && !t.checked && !t.disabled && t.closest('.settingsline').textContent.includes('Native Overview cards (beta)');
      // the cross-close: the shell is open; the cards open over it and close it
      lmaNavTo('dashboard', { fast: true }); await until(() => showing('overview'), 4000);
      lmaLoad().nativeOverviewCards = true; await NativeOverviewCards.open(); await wait(200);
      out.cardsCloseShell = showing('cards') && !showing('overview');
      // and the other way: the shell's own open closes open cards
      lmaLoad().nativeOverviewCards = false; await openNativeOverview(); await wait(300);
      out.shellClosesCards = showing('overview') && !showing('cards');
      return out;
    });
    for (const [k, v] of Object.entries(set)) ok('setting: ' + k, v, set);
    await p.context().close();

    p = await start(b, { cardsAvailable: false, cardsOn: true });
    await p.evaluate(HELPERS);
    const old = await p.evaluate(async () => {
      await until(() => showing('overview'), 6000);
      navigate('settings'); await wait(50);
      const t = document.getElementById('nativeOverviewCardsToggle');
      return { shell: count('overview', 'open') > 0, disabled: !!t && t.disabled, wanted: NativeOverviewCards.wanted() };
    });
    ok('an old APK (no NativeOverviewCards) keeps the live shell, and the switch is disabled', old.shell && old.disabled && !old.wanted, old);
    await p.context().close();

    /* 3. giving up */
    for (const [mode, cards] of [['capability', { caps: [] }], ['open', { openFails: true }], ['update', { updateFails: true }]]) {
      p = await start(b, { cards });
      await p.evaluate(HELPERS);
      const g = await p.evaluate(async (mode) => {
        const out = {};
        if (mode === 'update') { await until(() => count('cards', 'open') > 0, 6000); renderOverview(); }
        await until(() => NativeOverviewCards.diagnostics().gaveUp, 6000);
        out.gaveUp = NativeOverviewCards.diagnostics().gaveUp && NativeOverviewCards.diagnostics().failReason === mode && !NativeOverviewCards.wanted();
        out.shell = await until(() => showing('overview'), 4000);
        out.toast = __toasts.filter(t => /native Overview cards could not open/.test(t)).length === 1;        const asks = count('cards', 'open') + count('cards', 'handshake');
        document.body.appendChild(document.createElement('div')); document.body.classList.add('x'); document.body.classList.remove('x');
        lmaNavTo('quests', { fast: true }); await wait(200); lmaNavTo('dashboard', { fast: true }); await wait(800);
        out.noRetry = count('cards', 'open') + count('cards', 'handshake') === asks && showing('overview');
        // the switch, off and on, tries once more
        __cards.caps = ['overview.cards.v1']; __cards.openFails = false; __cards.updateFails = false;
        navigate('settings'); await setNativeOverviewCardsEnabled(false); await setNativeOverviewCardsEnabled(true);
        out.reset = !NativeOverviewCards.diagnostics().gaveUp;
        if (mode !== 'capability') { lmaNavTo('dashboard', { fast: true }); out.retried = await until(() => showing('cards'), 4000) && !showing('overview') } else out.retried = true;
        return out;
      }, mode);
      for (const [k, v] of Object.entries(g)) ok('gave up (' + mode + '): ' + k, v, g);
      await p.context().close();
    }
  } finally { await b.close() }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})();
