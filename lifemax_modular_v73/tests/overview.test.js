/* The Overview's tiles and their editor, on the page (all Overviews: Classic, and the live Overview's web body).
   - The tiles are in the editor's order from the first frame: the three tiles later segments add (Rewards, Apps, Health) take their
     saved place as they are added, not at the first save.
   - Back closes the open editor, after any popup over it, and leaves the live Overview's bars as they were (Edit, Done and Back).
   - A layout change that could not be stored is taken back (the tiles and the editor's rows keep the stored layout), and storage
     full says so once; a read-only save keeps its one alert. dismissFeatureTip answers whether it was stored.
   - The cards' data functions decide what a drawing shows: Next moves' ranking and its cut at four, the pace, the ring's arcs,
     the week chart's scale, and the cat card's name for two cats (tests/overview-classic.test.js proves the HTML drawn from them).
   Run: NODE_PATH=$(npm root -g) node tests/overview.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const STORE = 'lifemax_rpg_prototype_v1', fixture = n => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', n + '.json'), 'utf8'));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

/* An established save whose layout was edited before Rewards, Apps and Health existed: they go after their default neighbour (hero) */
const SAVED = { order: ['recent', 'luck', 'plan', 'hero', 'next', 'attrs', 'week', 'cat'], hidden: ['week'] };
const EXPECT = ['recent', 'luck', 'plan', 'hero', 'rewards', 'apps', 'health', 'next', 'attrs', 'week', 'cat'];
/* A fake NativeOverview plugin (the live Overview's native bars) that records its calls */
const FAKE = `(() => { window.__calls = []; const ls = {};
  const NativeOverview = { addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    capabilities: async () => ({ liveWeb: true }), open: async a => { __calls.push('open') }, update: async () => { __calls.push('update') },
    close: async a => { __calls.push('close'); (ls.closed || []).forEach(f => f({ session: a && a.session, action: '' })) } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeOverview', getPlatform: () => 'android', Plugins: { NativeOverview },
    registerPlugin: () => ({}), convertFileSrc: u => u }; })();`;

async function start(b, { save, android }) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.dialogs = []; p.on('pageerror', e => p.errors.push(String(e))); p.on('dialog', d => { p.dialogs.push(d.message()); d.accept() });
  await p.addInitScript(([STORE, save, fake]) => {
    localStorage.setItem(STORE, save);
    /* every segment and feature script is inline, so this is the order the first frame paints */
    document.addEventListener('DOMContentLoaded', () => { window.__atLoad = [...document.querySelectorAll('#ovTiles > .ov-tile')].map(t => t.dataset.tile) });
    if (fake) (0, eval)(fake);
  }, [STORE, save, android ? FAKE : '']);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.waitForFunction(() => window.__ovReady && typeof ovEditOpen === 'function', null, { timeout: 60000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } }); // the one-off walk through every tab
  await p.waitForTimeout(2500); // the boot timers: the theme and the tiles' first draw, and the live Overview's open at 1.8 s
  return p;
}
const tiles = p => p.evaluate(() => ({ order: [...document.querySelectorAll('#ovTiles > .ov-tile')].map(t => t.dataset.tile), off: [...document.querySelectorAll('#ovTiles > .ov-tile.ov-off')].map(t => t.dataset.tile) }));
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b), sameSet = (a, b) => same([...a].sort(), [...b].sort());

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* ---- the order the tiles load in ---- */
  const p = await start(b, { save: JSON.stringify({ ...fixture('established').state, ovLayout: SAVED, tipsSeen: { dashboard: true } }) });
  let r = await p.evaluate(() => ({ atLoad: __atLoad, layout: ovLayout() }));
  ok('a saved layout from before Rewards, Apps and Health existed places them after their default neighbour', same(r.layout.order, EXPECT) && same(r.layout.hidden, ['week']), r.layout);
  ok('Classic: the tiles are in the saved order from the first frame, with the three added tiles in their place', same(r.atLoad, EXPECT), r);
  r = await tiles(p);
  ok('...and stay so once the page has started, with only the hidden tile hidden', same(r.order, EXPECT) && same(r.off, ['week']), r);
  r = await p.evaluate(() => {
    const was = state.ovLayout, out = {};
    state.ovLayout = { order: ['luck', 'bogus', 'luck', 'plan'], hidden: ['bogus', 'cat'] }; out.messy = ovLayout();
    state.ovLayout = null; out.none = ovLayout();
    state.ovLayout = was; return out;
  });
  ok('ovLayout drops unknown and repeated ids and puts each missing tile after its default neighbour', same(r.messy.order, ['luck', 'recent', 'plan', 'next', 'hero', 'rewards', 'apps', 'health', 'attrs', 'week', 'cat']) && same(r.messy.hidden, ['cat']), r.messy);
  ok('with no saved layout the order is the default one', same(r.none.order, ['plan', 'next', 'hero', 'rewards', 'apps', 'health', 'attrs', 'week', 'cat', 'luck', 'recent']) && same(r.none.hidden, []), r.none);

  const a = await start(b, { save: JSON.stringify({ ...fixture('new').state, tipsSeen: { dashboard: true } }), android: true });
  await a.waitForFunction(() => __calls.includes('open'), null, { timeout: 30000 }).catch(() => { }); // the first capture of the bars is slow headless
  r = await a.evaluate(() => {
    const shown = [...document.querySelectorAll('#ovTiles > .ov-tile')].filter(t => t.offsetHeight > 0).sort((x, y) => x.getBoundingClientRect().top - y.getBoundingClientRect().top).map(t => t.dataset.tile);
    return { atLoad: __atLoad, layout: ovLayout().order, shown, open: __calls.includes('open') };
  });
  ok('Android, under the live Overview: the tiles are in the default order from the first frame', same(r.atLoad, r.layout) && same(r.layout, ['plan', 'next', 'hero', 'rewards', 'apps', 'health', 'attrs', 'week', 'cat', 'luck', 'recent']), r);
  ok('...and the tiles on screen, Apps and Health among them, run top to bottom in that order', r.open && same(r.shown, r.layout.filter(id => r.shown.includes(id))) && r.shown.includes('apps') && r.shown.includes('health'), r);

  /* ---- Back and the editor ---- */
  r = await p.evaluate(() => {
    const ed = document.getElementById('ovEdit'), out = {};
    lmaNavTo('quests', { fast: true }); lmaNavTo('dashboard', { fast: true }); // Back has somewhere to go
    document.getElementById('ovEditBtn').click();
    out.opened = !ed.hidden;
    out.back = lmaBack(); out.closed = ed.hidden && !document.getElementById('ovTiles').hidden; out.view = lmaView();
    document.getElementById('ovEditBtn').click();
    const pop = document.createElement('div'); pop.className = 'updpop'; document.body.appendChild(pop); // as the update prompt is
    lmaBack(); out.popFirst = !pop.isConnected && !ed.hidden;
    lmaBack(); out.thenEditor = ed.hidden && lmaView() === 'dashboard';
    lmaBack(); // the previous tab, in a view transition
    return out;
  });
  r.thenStack = await p.waitForFunction(() => lmaView() === 'quests', null, { timeout: 10000 }).then(() => true, () => false);
  await p.evaluate(() => lmaNavTo('dashboard', { fast: true }));
  ok('Back closes the open editor and stays on the Overview', r.opened && r.back === true && r.closed && r.view === 'dashboard', r);
  ok('a popup over the editor closes first, then the editor, then Back goes to the previous tab as before', r.popFirst && r.thenEditor && r.thenStack, r);

  /* the live Overview, installed after load as on a phone: its bars stay through the web editor (its blocked() counts lmaTopOverlay()) */
  await p.evaluate(FAKE);
  await p.evaluate(async () => { lmaLoad().nativeOverview = true; await openNativeOverview() });
  const bars = () => p.evaluate(() => ({ calls: __calls.filter(c => c !== 'update'), masked: document.querySelector('.topbar').style.opacity === '0', editing: !document.getElementById('ovEdit').hidden, view: lmaView() }));
  const shell = [];
  await p.evaluate(() => document.getElementById('ovEditBtn').click()); await p.waitForTimeout(700); shell.push(await bars());
  await p.evaluate(() => document.querySelector('#ovEdit .ov-edit-done').click()); await p.waitForTimeout(700); shell.push(await bars());
  await p.evaluate(() => document.getElementById('ovEditBtn').click()); await p.waitForTimeout(400);
  await p.evaluate(() => lmaBack()); await p.waitForTimeout(700); shell.push(await bars());
  ok('the live Overview opens with its bars masked on the page', same(shell[0].calls, ['open']) && shell[0].masked, shell[0]);
  ok('Edit, Done, then Edit and Back: the live Overview never closes, its web bars stay masked, and Back closes the editor on the Overview',
    shell.every(s => same(s.calls, ['open']) && s.masked && s.view === 'dashboard') && same(shell.map(s => s.editing), [true, false, false]), shell);

  /* ---- layout saves that are not stored ---- */
  await p.evaluate(() => { window.__toasts = []; const t = toast; toast = function (m) { __toasts.push(m); return t.apply(this, arguments) } });
  const full = fn => p.evaluate(`(() => { const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === '${STORE}') throw new Error('QuotaExceededError'); return real.apply(this, arguments) };
    try { return (${fn})() } finally { Storage.prototype.setItem = real } })()`);
  const look = () => p.evaluate(STORE => {
    const rows = [...document.querySelectorAll('#ovEditList .ov-er')];
    return { state: JSON.stringify(state.ovLayout), stored: JSON.stringify(JSON.parse(localStorage.getItem(STORE)).ovLayout), layout: ovLayout(),
      tiles: [...document.querySelectorAll('#ovTiles > .ov-tile')].map(t => t.dataset.tile), off: [...document.querySelectorAll('#ovTiles > .ov-tile.ov-off')].map(t => t.dataset.tile),
      rows: rows.map(e => e.dataset.tile), rowsOff: rows.filter(e => e.classList.contains('off')).map(e => e.dataset.tile), pressed: rows.map(e => e.querySelector('.ov-er-eye').getAttribute('aria-pressed')),
      focus: document.activeElement && document.activeElement.closest('.ov-er') ? document.activeElement.closest('.ov-er').dataset.tile : '', toasts: __toasts.slice() };
  }, STORE);
  const kept = (x, base) => x.state === base.stored && x.stored === base.stored && same(x.tiles, base.layout.order) && sameSet(x.off, base.layout.hidden) && same(x.rows, base.layout.order) && sameSet(x.rowsOff, base.layout.hidden);
  await p.evaluate(() => document.getElementById('ovEditBtn').click());
  const base = await look();
  const answer = await full(`() => ovLayoutSave({ order: ovLayout().order.slice().reverse(), hidden: [] })`);
  r = await look();
  ok('a layout the save refuses (storage full) answers false and is taken back: the state, the tiles and the open editor keep the stored layout', answer === false && kept(r, base), { answer, r, base });
  ok('...and says so, once', same(r.toasts, ['Could not save to this browser (storage full?)']), r.toasts);
  await full(`() => document.querySelector('#ovEditList .ov-er[data-tile="luck"] .ov-er-eye').click()`);
  r = await look();
  ok('hiding a tile with storage full leaves it shown: the tile, the row and its eye', kept(r, base) && r.pressed[r.rows.indexOf('luck')] === 'true' && r.toasts.length === 2, r);
  await full(`() => { const g = document.querySelector('#ovEditList .ov-er[data-tile="next"] .ov-er-grip'); g.focus(); g.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true })) }`);
  r = await look();
  ok('moving a row with storage full puts the rows back in the stored order, and the moved tile keeps the focus', kept(r, base) && r.focus === 'next' && r.toasts.length === 3, r);
  await full(`() => document.querySelector('#ovEdit .ov-edit-reset').click()`);
  r = await look();
  ok('Reset with storage full keeps the stored layout (a hidden tile stays hidden)', kept(r, base) && base.layout.hidden.length > 0 && r.toasts.length === 4, r);
  r = await p.evaluate(() => { const g = document.querySelector('#ovEditList .ov-er[data-tile="next"] .ov-er-grip'); g.focus(); g.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true })); document.querySelector('#ovEditList .ov-er[data-tile="luck"] .ov-er-eye').click(); return ovLayout() });
  const stored = await look();
  ok('with the save working the same changes are stored and shown, with no message', stored.stored === JSON.stringify(r) && same(stored.tiles, r.order) && sameSet(stored.off, r.hidden) && same(stored.rows, r.order) && r.hidden.includes('luck') && r.order.indexOf('next') === r.order.indexOf('health') - 1 && stored.focus === 'next' && stored.toasts.length === 4, { r, stored });

  const alerts = p.dialogs.length;
  r = await p.evaluate(() => {
    saveReadOnly = 'Read-only for this check'; saveReadOnlyTold = false;
    const before = JSON.stringify(state.ovLayout), answers = [];
    try { answers.push(ovLayoutSave({ order: ovLayout().order.slice().reverse(), hidden: [] })); document.querySelector('#ovEditList .ov-er[data-tile="cat"] .ov-er-eye').click() } finally { saveReadOnly = '' }
    return { answers, same: JSON.stringify(state.ovLayout) === before, toasts: __toasts.length };
  });
  await p.waitForTimeout(3000); // writeSave's alert comes 2.5 s later
  ok('a read-only save: the layout is taken back, with its one alert and no message', same(r.answers, [false]) && r.same && r.toasts === 4 && p.dialogs.length === alerts + 1 && /Read-only for this check/.test(p.dialogs[alerts]), { r, dialogs: p.dialogs });
  r = await look();
  ok('...and the editor shows the stored layout', r.state === r.stored && same(r.rows, r.layout.order) && sameSet(r.rowsOff, r.layout.hidden) && same(r.tiles, r.layout.order), r);
  await p.evaluate(() => { saveReadOnlyTold = false; document.querySelector('#ovEdit .ov-edit-done').click() });

  /* ---- the feature tip ---- */
  r = await p.evaluate(() => { const out = { stored: dismissFeatureTip('quests') }; saveReadOnly = 'Read-only for this check'; saveReadOnlyTold = true; try { out.readOnly = dismissFeatureTip('character') } finally { saveReadOnly = ''; saveReadOnlyTold = false } return out });
  r.full = await full(`() => dismissFeatureTip('history')`);
  ok('dismissFeatureTip answers whether the dismissal was stored: true, false when read-only, false with storage full', r.stored === true && r.readOnly === false && r.full === false, r);

  /* ---- the cards' data functions: the decisions a native drawing reads (tests/overview-classic.test.js proves the HTML) ---- */
  r = await a.evaluate(() => {
    const was = JSON.stringify(state), out = {}, A = ATTRS.map(x => x.id), H = 36e5;
    const day = (back, h) => { const d = new Date(); d.setDate(d.getDate() - back); d.setHours(h || 12, 0, 0, 0); return d.toISOString() };
    const act = (back, split, h) => ({ id: 'a' + Math.random(), name: 'Test', date: day(back, h), primary: split[0][0], xp: split.reduce((s, x) => s + x[1], 0), xpSplit: split.map(x => ({ attr: x[0], xp: x[1] })), duration: 0 });
    const cat = (hours, o) => Object.assign({ v: 1, name: 'Miso', coat: 'ginger', trait: 'curious', born: Date.now() - 40 * 864e5, lastFed: Date.now() - hours * H, meals: [], day: getToday(), fedToday: 0, petsToday: 0, playsToday: 0, bondPts: 0, care: 0, vacation: { on: false, since: 0 }, gifts: 0, lastGiftDay: '', alive: true, passed: 0, seen: {}, greeted: 0 }, o || {});
    const quests = n => Array.from({ length: n }, (_, i) => ({ id: 'tq' + i, name: 'Quest ' + i, cadence: 'daily', xp: 10 }));
    const moves = () => ovMoves().map(m => m.title + ' ' + JSON.stringify(m.go));
    const reset = () => { Object.keys(state).forEach(k => delete state[k]); Object.assign(state, JSON.parse(was)); state.plan = null; state.petInviteHidden = true };
    try {
      /* Next moves: a starving cat, two fading attributes and the daily quests take the four places; the workout-free luck roll and
         the attribute due a session are cut */
      reset(); state.activities = [act(6, [[A[0], 30]]), act(9, [[A[1], 30]]), act(4, [[A[2], 30]])]; state.attrXP = { [A[0]]: 500, [A[1]]: 500, [A[2]]: 500 };
      state.quests = quests(3); state.questDone = {}; state.pet = cat(30);
      out.rush = moves();
      state.pet = cat(20); out.hungry = moves();
      state.decay = { on: false }; out.calm = moves();
      state.quests.forEach(q => { state.questDone[questKey(q)] = true }); state.luck = null; state.pet = null; state.petInviteHidden = false; out.done = ovMoves().map(m => [m.title, !!m.done, m.cta || '']);
      state.activities = []; out.first = moves()[0];
      /* ovPace: last week's daily average to the nearest 10, between 60 and 400, from two active days; 100 before that. Today never counts */
      const pace = days => { const m = {}; days.forEach(([back, xp]) => { m[ovKey(back)] = xp }); return ovPace(m) };
      out.pace = [pace([[0, 900], [1, 300]]), pace([[1, 900], [3, 850]]), pace([[1, 70], [2, 70]]), pace([[2, 3000], [5, 9000]]), pace([[8, 700], [9, 700], [1, 70]]), pace([[7, 700], [1, 70]])];
      /* the ring: before any history the pace is 100, so today's XP is the fill; each arc is its share less the 2.4 gap (none for one) */
      reset(); state.activities = [act(0, [[A[0], 30]])]; out.one = ovHeroData().ring;
      state.activities = [act(0, [[A[0], 20], [A[1], 40]])]; out.two = ovHeroData().ring;
      state.activities = [act(0, [[A[0], 60], [A[1], 50], [A[2], 40]])]; out.over = ovHeroData().ring;
      /* the week: the scale is the larger of 1.2 x pace, 40 and the biggest day; bars and the pace line are fractions of it */
      state.activities = [act(0, [[A[0], 30]]), act(2, [[A[0], 20], [A[1], 10]])]; out.week = ovWeekData();
      state.activities = [act(0, [[A[0], 200], [A[1], 100]])]; out.big = ovWeekData();
      /* the cat card names both cats while both live, and the survivor alone once the second has passed, without a redraw */
      reset(); state.pet = cat(2); state.pet2 = cat(2, { name: 'Pip', coat: 'gray' });
      const peek = document.getElementById('petPeek'); peek.dataset.stamp = ''; renderPetPeek();
      out.pair = [petPeekData().name, peek.querySelector('.pet-peek-row strong').textContent];
      state.pet2 = null; renderPetPeek(); out.alone = [petPeekData().name, peek.querySelector('.pet-peek-row strong').textContent];
    } finally { reset(); state.plan = JSON.parse(was).plan; state.petInviteHidden = JSON.parse(was).petInviteHidden; const peek = document.getElementById('petPeek'); peek.dataset.stamp = ''; renderPetPeek(); renderOverview() }
    return out;
  });
  const near = (x, y) => Math.abs(x - y) < 1e-9, A0 = await a.evaluate(() => ATTRS.map(x => x.short));
  ok('Next moves: a starving cat first, then two fading attributes and the daily quests; the cut at four drops the due attribute and the luck roll',
    same(r.rush, ['Miso really needs a meal {"view":"pet"}', A0[0] + ' is fading {"attr":"' + (await a.evaluate(() => ATTRS[0].id)) + '"}', A0[1] + ' is fading {"attr":"' + (await a.evaluate(() => ATTRS[1].id)) + '"}', '3 daily quests left {"view":"quests"}']), r.rush);
  ok('...a cat that is only hungry comes after the quests', r.hungry.indexOf('Miso is hungry {"view":"pet"}') === 3 && r.hungry.length === 4, r.hungry);
  ok('...with decay off: the quests, the hungry cat, then the luck roll', same(r.calm, ['3 daily quests left {"view":"quests"}', 'Miso is hungry {"view":"pet"}', 'Roll today\'s luck {"luck":true}']), r.calm);
  ok('...all done: the luck roll, the stray kitten, then the quests as done with no call to action',
    same(r.done, [['Roll today\'s luck', false, 'Roll'], ['A stray kitten keeps stopping by', false, 'Meet'], ['Daily quests complete', true, '']]), r.done);
  ok('...and with nothing logged, the first activity first', r.first === 'Log your first activity {"view":"log"}', r.first);
  ok('ovPace: 100 with fewer than two active days, then last week\'s daily average to the nearest 10 held between 60 and 400; the last seven days are yesterday back to a week ago, so today and older days never count',
    same(r.pace, [100, 250, 60, 400, 100, 110]), r.pace);
  const lens = ring => ring.arcs.map(x => +x.length.toFixed(6)), offs = ring => ring.arcs.map(x => +(-x.offset).toFixed(6));
  ok('the ring: one attribute is one arc as long as today\'s fill, with no gap', same(lens(r.one), [30]) && same(offs(r.one), [0]) && r.one.of === 'of 100 XP' && !r.one.over, r.one);
  ok('...two are largest first, each its share less the 2.4 gap, the second starting where the first share ends', same(lens(r.two), [37.6, 17.6]) && same(offs(r.two), [0, 40]), r.two);
  ok('...over pace the shares fill the ring and never pass it, and the line says by how much', r.over.over && r.over.of === 'of 100 XP · +50%' && near(r.over.arcs.reduce((s, x) => s + x.length, 0) + 2.4 * 3, 100) && same(offs(r.over), [0, 40, 73.333333]), r.over);
  const fr = (w, i) => w.bars[i].segs.reduce((s, x) => s + x.frac, 0);
  ok('the week: the scale is 1.2 x pace when that is largest, the pace line and each day are fractions of it, each day carries its XP, and today is picked',
    near(r.week.pace.frac, 1 / 1.2) && near(fr(r.week, 6), 30 / 120) && near(fr(r.week, 4), 30 / 120) && r.week.bars[4].segs.length === 2 && r.week.bars[5].segs.length === 0 && same(r.week.bars.map(x => x.xp), [0, 0, 0, 0, 30, 0, 30]) && r.week.bars[6].today && r.week.selected === 6 && r.week.pace.label === 'pace 100' && r.week.total === '60 XP', r.week);
  ok('...a bigger day becomes the scale: it fills its bar and the pace line drops to a third', near(fr(r.big, 6), 1) && near(r.big.pace.frac, 100 / 300) && r.big.bars[6].tip.rest === '300 XP — ' + A0[0] + ' 200, ' + A0[1] + ' 100', r.big);
  ok('the cat card names both cats while both live, and the survivor alone once the second has passed (the card is not redrawn)', same(r.pair, ['Miso & Pip', 'Miso & Pip']) && same(r.alone, ['Miso', 'Miso']), { pair: r.pair, alone: r.alone });

  ok('no script errors', p.errors.length === 0 && a.errors.length === 0, [...p.errors, ...a.errors]);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
