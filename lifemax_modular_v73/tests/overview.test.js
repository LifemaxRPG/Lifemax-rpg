/* The Overview's tiles and their editor, on the page (all Overviews: Classic, and the live Overview's web body).
   - The tiles are in the editor's order from the first frame: the three tiles later segments add (Rewards, Apps, Health) take their
     saved place as they are added, not at the first save.
   - Back closes the open editor, after any popup over it, and leaves the live Overview's bars as they were (Edit, Done and Back).
   - A layout change that could not be stored is taken back (the tiles and the editor's rows keep the stored layout), and storage
     full says so once; a read-only save keeps its one alert. dismissFeatureTip answers whether it was stored.
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

  ok('no script errors', p.errors.length === 0 && a.errors.length === 0, [...p.errors, ...a.errors]);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
