/* Native Overview cards, phase 2: the native editor's page half (plans/OVERVIEW_BODY.md §3.4, §4 phase 2, §5 "Phase 2, web").
   - the page's own editor draws the same HTML after its words moved into OV_EDIT_WORDS (the words the screen's editor draws too):
     tests/fixtures/overview-editor-golden.json, captured before the move (RECORD=1 rewrites it, only ever from code known to be right);
   - the projection carries those words (`editor`) and every tile as the editor names it (`tileInfo`);
   - `layout {order, hidden}` goes through the page's own layout save (ovLayoutSave): stored and shown, the reply carrying the page's
     layout; the same layout twice is one state and one write ("Already recorded."); unknown and duplicate ids are dropped and a
     missing tile goes back to its default place, as ovLayout() has it; a payload that is not a layout changes nothing;
   - `layoutReset {}` gives the default layout, twice;
   - a write that is not stored (storage full, a read-only save) answers ok:false with the page's message (unsavedMessage()), the
     layout is taken back (the state, the page's tiles and the reply's projection all keep the stored one), storage full's toast is
     not also sent as a notice (the screen says the reply's message), and a read-only save keeps its one alert;
   - the screen stays open through all of it.
   Fake NativeOverviewCards and NativeOverview plugins stand in for Android, as in tests/native-overview-cards.test.js.
   Run: NODE_PATH=$(npm root -g) node tests/native-overview-editor.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const STORE = 'lifemax_rpg_prototype_v1', GOLDEN = path.join(__dirname, 'fixtures', 'overview-editor-golden.json'), RECORD = process.env.RECORD === '1';
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const golden = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', 'established.json'), 'utf8'));

const FAKES = (cardsAvailable) => `
  window.__toasts = []; new MutationObserver(rs => rs.forEach(r => { if (r.target.id === 'toast') r.addedNodes.forEach(n => __toasts.push(n.textContent)) })).observe(document, { childList: true, subtree: true });
  window.__calls = [];
  const mk = (name, cards) => { const ls = {}; return {
    __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a]) },
    update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => ({ protocol: 1, capabilities: cards ? ['overview.cards.v1'] : [] }) } };
  const P = { NativeOverview: mk('overview', false) };
  if (${cardsAvailable}) P.NativeOverviewCards = mk('cards', true);
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => !!P[n], getPlatform: () => 'android', Plugins: P, registerPlugin: () => ({}), convertFileSrc: u => u };`;

async function start(b, { cardsOn = true, pet = true, layout = null } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.dialogs = [];
  p.on('pageerror', e => p.errors.push(String(e))); p.on('dialog', d => { p.dialogs.push(d.message()); d.dismiss().catch(() => { }) });
  const now = Date.now();
  const save = Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: {}, name: 'Ada' },
    pet && { pet: { v: 1, name: 'Miso', coat: 'ginger', trait: 'curious', born: now - 40 * 864e5, lastFed: now - 3 * 36e5, meals: [], day: '', fedToday: 0, petsToday: 0, playsToday: 0, bondPts: 0, care: 0, vacation: { on: false, since: 0 }, gifts: 0, lastGiftDay: '', alive: true, passed: 0, seen: {}, greeted: 0 } },
    layout && { ovLayout: layout });
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(save), golden.extras || {}, JSON.stringify({ nativeOverviewCards: cardsOn })]);
  await p.addInitScript(FAKES(true));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.waitForFunction(() => window.__ovReady && typeof ovEditOpen === 'function', null, { timeout: 60000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } });
  return p;
}
const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.last = (n, k) => [...__calls].reverse().find(c => c[0] === n && c[1] === k);
  window.showing = (n = 'cards') => { const o = last(n, 'open'), c = last(n, 'close'); return !!o && (!c || __calls.lastIndexOf(o) > __calls.lastIndexOf(c)) };
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  /* every write of the core save, counted */
  window.__writes = 0; { const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === '${STORE}') { __writes++; if (window.__full) throw new Error('QuotaExceededError') } return real.apply(this, arguments) } }
  /* a command from the screen: its reply, and the projection sent with it */
  window.command = async (command, payload) => { const n = __calls.length; await Capacitor.Plugins.NativeOverviewCards.__emit('command', { command, payload, requestId: 'r-' + command }); await wait(250);
    const u = __calls.slice(n).find(c => c[0] === 'cards' && c[1] === 'update' && c[2].reply); return u ? { reply: u[2].reply, snapshot: u[2].snapshot } : null };
  window.look = () => ({ state: state.ovLayout ? JSON.stringify({ order: state.ovLayout.order, hidden: state.ovLayout.hidden }) : null,
    stored: JSON.stringify(JSON.parse(localStorage.getItem('${STORE}')).ovLayout || null), layout: ovLayout(),
    tiles: [...document.querySelectorAll('#ovTiles > .ov-tile')].map(t => t.dataset.tile), off: [...document.querySelectorAll('#ovTiles > .ov-tile.ov-off')].map(t => t.dataset.tile) });`;

/* the page's editor, drawn in each state: what OV_EDIT_WORDS must not change */
async function editorHtml(b) {
  const out = {};
  const states = [
    ['default', { pet: false }, null],
    ['pet-custom', { pet: true, layout: { order: ['luck', 'hero', 'plan', 'next', 'attrs', 'week', 'cat', 'recent'], hidden: ['cat', 'week'] } }, null],
    ['toggled', { pet: true }, `() => { document.querySelector('#ovEditList .ov-er[data-tile="week"] .ov-er-eye').click(); document.querySelector('#ovEditList .ov-er[data-tile="cat"] .ov-er-eye').click(); document.querySelector('#ovEditList .ov-er[data-tile="week"] .ov-er-eye').click() }`],
    ['reset', { pet: true, layout: { order: ['recent', 'luck'], hidden: ['plan', 'luck'] } }, `() => document.querySelector('#ovEdit .ov-edit-reset').click()`],
  ];
  for (const [name, opts, act] of states) {
    const p = await start(b, Object.assign({ cardsOn: false }, opts));
    out[name] = await p.evaluate(async (act) => {
      ovEditOpen();
      if (act) (0, eval)('(' + act + ')')();
      return document.getElementById('ovEdit').innerHTML;
    }, act);
    await p.context().close();
  }
  return out;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the page's editor is drawn as before */
    const html = await editorHtml(b);
    if (RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const want = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    for (const k of Object.keys(want)) ok('the page\'s editor draws byte for byte as before: ' + k, html[k] === want[k], { got: html[k], want: want[k] });
    if (process.env.ONLY === 'golden') return;

    /* 2. the projection: the editor's words and the tiles' names */
    let p = await start(b);
    await p.evaluate(HELPERS);
    let r = await p.evaluate(async () => {
      await until(() => showing(), 6000);
      const s = last('cards', 'open')[2].snapshot, ed = document.getElementById('ovEdit');
      ovEditOpen();
      const page = { title: ed.querySelector('h3').textContent, hint: ed.querySelector('.ov-edit-h small').textContent, done: ed.querySelector('.ov-edit-done').textContent,
        reset: ed.querySelector('.ov-edit-reset').textContent, eye: ed.querySelector('.ov-er-eye').getAttribute('aria-label') };
      ovEditClose(true);
      await until(() => showing(), 4000);
      return { editor: s.editor, words: OV_EDIT_WORDS, page, tileInfo: s.tileInfo, layout: s.layout, first: ovLayout().order[0], firstName: OV_TILES.find(t => t.id === ovLayout().order[0]).name };
    });
    ok('the projection carries the page\'s editor words (OV_EDIT_WORDS), every one a string', same(r.editor, r.words) && Object.values(r.editor).every(v => typeof v === 'string' && v.length) &&
      same(Object.keys(r.editor).sort(), ['done', 'hidden', 'hide', 'hint', 'moveBottom', 'moveDown', 'moveTop', 'moveUp', 'reset', 'show', 'title']), r.editor);
    ok('...the same words the page\'s own editor shows', r.page.title === r.editor.title && r.page.hint === r.editor.hint && r.page.done === r.editor.done && r.page.reset === r.editor.reset && r.page.eye === r.editor.hide + r.firstName, r.page);
    ok('tileInfo names every tile the editor lists, in the default order; the cat\'s room is named after the cat', r.tileInfo.length === r.layout.order.length && r.tileInfo.find(t => t.id === 'cat').sub === 'Miso’s room' && r.tileInfo.every(t => typeof t.color === 'number'), r.tileInfo);

    /* 3. layout and layoutReset, stored */
    r = await p.evaluate(async () => {
      const out = {}, L = ovLayout(), order = L.order.slice().reverse(), hidden = ['luck', 'week'];
      let w = __writes; const a = await command('layout', { order, hidden });
      out.first = { reply: a.reply, writes: __writes - w, sent: a.snapshot.layout, look: look(), want: { order, hidden } };
      w = __writes; const b = await command('layout', { order, hidden });
      out.again = { reply: b.reply, writes: __writes - w, sent: b.snapshot.layout, look: look() };
      // unknown and twice-named ids are dropped; a tile left out goes back after its default neighbour, as ovLayout() places it
      const partial = order.filter(id => id !== 'next'), messy = ['bogus', partial[0]].concat(partial);
      const c = await command('layout', { order: messy, hidden: ['bogus', 'recent'] });
      out.messy = { reply: c.reply, sent: c.snapshot.layout, look: look() };
      w = __writes; const bad = [await command('layout', { order: 'plan', hidden: [] }), await command('layout', { order: [3], hidden: [] }), await command('layout', {})];
      out.bad = { replies: bad.map(x => x.reply), writes: __writes - w, look: look() };
      w = __writes; const r1 = await command('layoutReset', {}); const r2 = await command('layoutReset', {});
      out.reset = { r1: r1.reply, r2: r2.reply, writes: __writes - w, sent: r2.snapshot.layout, look: look(), def: OV_DEFAULT.slice() };
      out.open = showing() && !__calls.some(c => c[0] === 'cards' && c[1] === 'close');
      return out;
    });
    let f = r.first;
    ok('layout: stored through the page\'s layout save, one write, and the page\'s tiles show it', f.reply.ok === true && f.reply.changed === true && f.writes === 1 && same(f.look.layout, f.want) &&
      f.look.stored === JSON.stringify(f.want) && same(f.look.tiles, f.want.order) && same(f.look.off.sort(), f.want.hidden.slice().sort()), f);
    ok('...the reply comes with the page\'s layout in its projection', same(f.sent, f.want), f.sent);
    f = r.again;
    ok('the same layout twice: one state and one write, the second recognised ("Already recorded.")', f.reply.ok === true && f.reply.changed === false && f.reply.message === 'Already recorded.' && f.writes === 0 && same(f.sent, r.first.want), f);
    f = r.messy;
    ok('layout drops unknown and twice-named ids and puts a tile left out back after its default neighbour (ovLayout())', f.reply.ok && same(f.sent, f.look.layout) && f.sent.order.length === r.first.want.order.length &&
      !f.sent.order.includes('bogus') && f.sent.order.includes('next') && same(f.sent.hidden, ['recent']), f);
    f = r.bad;
    ok('a payload that is not a layout is refused and changes nothing', f.replies.every(x => x.ok === false && x.message === 'Something went wrong. Nothing was changed.') && f.writes === 0 && same(f.look.layout, r.messy.sent), f);
    f = r.reset;
    ok('layoutReset gives the default layout; twice gives it still, with one write', f.r1.ok && f.r1.changed === true && f.r2.ok && f.r2.changed === false && f.writes === 1 &&
      same(f.sent, { order: f.def, hidden: [] }) && same(f.look.layout, { order: f.def, hidden: [] }) && same(f.look.tiles, f.def) && f.look.off.length === 0, f);
    ok('the screen stays open through every layout command', r.open, r);

    /* 4. writes the page could not store */
    r = await p.evaluate(async () => {
      const out = {};
      await command('layout', { order: OV_DEFAULT.slice(), hidden: ['cat'] });
      const base = look(); __toasts = [];
      window.__full = true;
      try {
        const a = await command('layout', { order: OV_DEFAULT.slice().reverse(), hidden: [] });
        out.full = { reply: a.reply, sent: a.snapshot.layout, notice: a.snapshot.notice, look: look(), toasts: __toasts.slice() };
        const b = await command('layoutReset', {});
        out.fullReset = { reply: b.reply, sent: b.snapshot.layout, look: look() };
        await wait(300); // a refresh after the toast: still no notice of it
        out.later = last('cards', 'update')[2].snapshot.notice;
      } finally { window.__full = false }
      out.base = base;
      saveReadOnly = 'Read-only for this check'; saveReadOnlyTold = false; __toasts = [];
      try {
        const c = await command('layout', { order: OV_DEFAULT.slice().reverse(), hidden: [] });
        const d = await command('layoutReset', {});
        out.readOnly = { reply: c.reply, reset: d.reply, sent: c.snapshot.layout, look: look(), toasts: __toasts.slice() };
      } finally { saveReadOnly = '' }
      out.open = showing();
      return out;
    });
    await p.waitForTimeout(3000); // writeSave's alert comes 2.5 s later
    const kept = (x, base) => x.state === base.stored && x.stored === base.stored && same(x.layout, base.layout) && same(x.tiles, base.layout.order) && same(x.off, base.layout.hidden);
    f = r.full;
    ok('storage full: layout answers ok:false with the page\'s message', f.reply.ok === false && f.reply.message === 'Could not save to this browser (storage full?)', f.reply);
    ok('...the layout is taken back: the state, the stored save and the page\'s tiles keep the stored one, and so does the reply\'s projection', kept(f.look, r.base) && same(f.sent, r.base.layout), { f, base: r.base });
    ok('...the page\'s own toast is not also sent as a notice (the screen says the reply\'s message)', f.toasts.length === 1 && f.notice === null && r.later === null, { toasts: f.toasts, notice: f.notice, later: r.later });
    ok('...layoutReset with storage full is refused the same way and keeps the hidden tile hidden', r.fullReset.reply.ok === false && r.fullReset.reply.message === 'Could not save to this browser (storage full?)' && kept(r.fullReset.look, r.base) && same(r.fullReset.sent, r.base.layout), r.fullReset);
    f = r.readOnly;
    ok('a read-only save: layout and layoutReset answer ok:false with the page\'s read-only words, and the layout is taken back', f.reply.ok === false && f.reply.message === 'Not saved: this version is read-only for your save' &&
      f.reset.ok === false && f.reset.message === f.reply.message && kept(f.look, r.base) && same(f.sent, r.base.layout), f);
    ok('...with its one alert and no toast', f.toasts.length === 0 && p.dialogs.length === 1 && /Read-only for this check/.test(p.dialogs[0]), { toasts: f.toasts, dialogs: p.dialogs });
    ok('the screen stays open after the refusals', r.open, r);
    ok('no script errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } finally { await b.close() }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})();
