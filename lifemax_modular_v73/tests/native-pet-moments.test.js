/* Native cat reports to the album and the likes (docs/specs/CAT_ENGINE_GAPS.md WP-C1), web half, through the real page
   (50-native-pet-moments.js): the `moment`, `gift` and `dwell` commands.
   - gating: a screen without pet.world.v1 gets every one refused; every malformed shape is refused;
   - moment: each first moment sets the brain's fx flag once and the album gets its entry once; blink and respect also raise the album's
     event; a quirk is appended once (three at most) and only a known one; a repeat op is "Already recorded.";
   - gift: refused until the cat is Devoted and again on the same day; counts p.gifts, sets lastGiftDay, and the album keeps the gift;
   - dwell: the likes are credited the way the page's daily credit does (a favourite place and toy appear, old habits fade once a day),
     a day too old or in the future is refused, a repeat is "Already recorded.";
   - ok only once stored; a refused save is taken back and says nothing; a repeat after a reload is "Already recorded.";
     the Overview cards take the commands too.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-moments.test.js [built html] */
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
/* Served over http, not file://: a reload of a file:// page in this Chromium sometimes comes back without the page's last
   localStorage writes (tests/current-page.js), which is what made the after-a-reload step fail now and then. */
let site = null;
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');
const WORLD = 'pet.world.v1';
const PET_CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1', 'pet.panels.v1', 'pet.play.v1'];

const FAKE = `
  window.__calls = []; window.__caps = { NativePet: [], NativeOverviewCards: [] };
  const mk = (name) => { const ls = {}; return { __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: window.__caps[name] }), capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    open: async a => { window.__calls.push([name, 'open', a]) }, update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close']); (ls.closed || []).forEach(f => f({ session: a && a.session })) } } };
  const P = { NativePet: mk('NativePet'), NativeOverviewCards: mk('NativeOverviewCards'), NativeOverview: mk('NativeOverview') };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => !!P[n], getPlatform: () => 'android', registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: P };`;
const CAT = (name, o = '{}') => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-5*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:PET_BOND_AT[3],care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o})`;
const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  window.showing = (n) => { const k = __calls.filter(c => c[0] === n).map(c => c[1]); return k.lastIndexOf('open') >= 0 && k.lastIndexOf('open') > k.lastIndexOf('close') };
  window.latest = (n) => { const c = [...__calls].reverse().find(c => c[0] === n && (c[1] === 'open' || c[1] === 'update') && c[2].snapshot); return c && c[2].snapshot };
  window.command = async (n, command, payload) => { const k = __calls.length, id = 'r-' + k; await Capacitor.Plugins[n].__emit('command', { command, payload, requestId: id });
    await until(() => __calls.slice(k).some(c => c[0] === n && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id), 3000);
    const u = __calls.slice(k).find(c => c[0] === n && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id); return u ? u[2].reply : null };
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(200) };
  window.ids = () => [petAlbumCatId(state.pet), petAlbumCatId(state.pet2)];
  window.rid = () => 'c:t:' + Math.random().toString(36).slice(2);
  window.mom = (kind, o) => Object.assign({ op: rid(), cat: ids()[0], kind, at: Date.now() }, o);
  window.stored = () => JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1'));
  /* the friendship record as compared: times dropped (the director dates its line with the clock, native with the moment's time),
     the meeting as a flag */
  window.pairView = () => { const d = JSON.parse(JSON.stringify(state.petPair)); delete d.seen; if (d.rk && !d.rk.n) delete d.rk; if (d.last) delete d.last.at; d.met = !!d.met; d.lastGreet = !!d.lastGreet; return d };
  window.withRandom = (v, f) => { const r = Math.random; Math.random = () => v; try { return f() } finally { Math.random = r } };
  window.pairAt = (rel, extra) => { petPairData(); Object.assign(state.petPair, { rel, met: Date.now() - 864e5, day: '', gain: 0, n: { greet: 0, hiss: 0, chase: 0, groom: 0, nap: 0 }, last: null, lastGreet: 0 }, extra || {}); delete state.petPair.rk; };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { petCaps = [...PET_CAPS, WORLD], cardCaps = ['overview.cards.v1', WORLD], cards = false } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript(([save, extras, prefs]) => {
    if (!localStorage.getItem('lifemax_rpg_prototype_v1')) { localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]) }
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify({ nativePet: true, nativeOverview: false, nativeOverviewCards: cards, apps: [] })]);
  await p.addInitScript(FAKE);
  await p.addInitScript(([a, c]) => { window.__caps.NativePet = a; window.__caps.NativeOverviewCards = c }, [petCaps, cardCaps]);
  await p.goto(site.url, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async ([a, b]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    await setCats('state.pet=' + a + ';state.pet2=' + b + ';state.petPair=null'); const rt = await petInit(); petPairEnsure(rt) },
  [CAT('Ted', "{dna:null}"), CAT('Lily', "{coat:'gray',born:now-35*864e5,trait:'shy'}")]);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return showing('NativePet') });

(async () => {
  site = await require('./current-page').serve(file);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. gating */
    let p = await start(b, { petCaps: PET_CAPS });
    await toPet(p);
    let r = await p.evaluate(async () => ({
      moment: await command('NativePet', 'moment', mom('first_glass')),
      gift: await command('NativePet', 'gift', { op: rid(), cat: ids()[0] }),
      dwell: await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day: petDay(), spots: { bed: 60 }, toys: {} }),
      fx: !!(state.pet.brain && state.pet.brain.mem && state.pet.brain.mem.fx.first_glass),
    }));
    for (const k of ['moment', 'gift', 'dwell']) ok('a screen without pet.world.v1: ' + k + ' is refused', r[k] && !r[k].ok && r[k].why === 'shape', r[k]);
    ok('nothing was recorded for it', !r.fx, r);
    await p.context().close();

    p = await start(b);
    ok('a Cat screen with pet.world.v1 opens', await toPet(p));
    await p.evaluate(() => { PET_ALBUM.WEEK_CAP = 99 }); // the album's own weekly limit of three would hold the later moments back

    /* 2. malformed shapes */
    r = await p.evaluate(async () => {
      const c = ids()[0], day = petDay();
      const bad = {
        momentExtra: mom('first_glass', { x: 1 }), momentKind: mom('first_nap'), momentAt: mom('first_glass', { at: 1.5 }), momentOp: mom('first_glass', { op: 'bad op!' }),
        momentCat: mom('first_glass', { cat: 'Ted' }), quirkNone: mom('quirk'), quirkUnknown: mom('quirk', { quirk: 'paw_tuck_sleeper' }), quirkOnOther: mom('blink', { quirk: 'toy_hoarder' }),
        giftExtra: { op: rid(), cat: c, n: 1 }, giftNoCat: { op: rid() },
        dwellDay: { op: rid(), cat: c, day: '2026-13-40', spots: {}, toys: {} }, dwellDayForm: { op: rid(), cat: c, day: '5/1/2026', spots: {}, toys: {} },
        dwellSpot: { op: rid(), cat: c, day, spots: { nowhere: 5 }, toys: {} }, dwellToy: { op: rid(), cat: c, day, spots: {}, toys: { rock: 5 } },
        dwellFraction: { op: rid(), cat: c, day, spots: { bed: 1.5 }, toys: {} }, dwellNegative: { op: rid(), cat: c, day, spots: { bed: -1 }, toys: {} },
        dwellMoreThanADay: { op: rid(), cat: c, day, spots: { bed: 80000, rug: 80000 }, toys: {} }, dwellMissing: { op: rid(), cat: c, day, spots: {} },
        array: [1],
      };
      const out = {};
      for (const k in bad) {
        const kind = k.startsWith('gift') ? 'gift' : k.startsWith('dwell') ? 'dwell' : 'moment';
        const a = await command('NativePet', kind, bad[k]); out[k] = !!a && !a.ok && a.why === 'shape';
      }
      out.stranger = await command('NativePet', 'moment', mom('first_glass', { cat: 'czzzzzz' }));
      out.fx = JSON.stringify(state.pet.brain && state.pet.brain.mem && state.pet.brain.mem.fx);
      return out;
    });
    for (const k of Object.keys(r)) if (!['stranger', 'fx'].includes(k)) ok('a malformed report is refused: ' + k, r[k], r);
    ok('a report of a cat that is not here is refused', r.stranger && !r.stranger.ok && r.stranger.why === 'refused', r.stranger);
    ok('nothing was recorded by any refusal', !/first_glass/.test(r.fx || ''), r.fx);

    /* 3. moments */
    r = await p.evaluate(async () => {
      const out = {}, fx = (i = 0) => (([state.pet, state.pet2][i].brain || {}).mem || { fx: {} }).fx, album = (i = 0) => [state.pet, state.pet2][i].album ? [state.pet, state.pet2][i].album.entries.map(e => e.kind) : [];
      const m = mom('first_glass');
      out.a = await command('NativePet', 'moment', m);
      out.glassFx = fx().first_glass === 1;
      out.glassAlbum = album().includes('glass');
      out.again = await command('NativePet', 'moment', m);
      out.other = await command('NativePet', 'moment', mom('first_glass'));
      out.glassCount = album().filter(k => k === 'glass').length;
      for (const [kind, key, entry] of [['first_co', 'first_co', 'comfort'], ['first_zoomies', 'first_zoomies', 'zoomies'], ['first_belly', 'first_belly', 'belly'], ['first_vigil', 'first_vigil', 'vigil'], ['first_trophy', 'first_trophy', 'trophy'], ['blink', 'first_blink', 'blink'], ['respect', 'first_respect', 'respect']]) {
        await command('NativePet', 'moment', mom(kind));
        out[kind] = fx()[key] === 1 && album().includes(entry);
      }
      out.lily = await command('NativePet', 'moment', mom('first_glass', { cat: ids()[1] }));
      out.lilyFx = fx(1).first_glass === 1;
      const q1 = await command('NativePet', 'moment', mom('quirk', { quirk: 'toy_hoarder' }));
      const q1again = await command('NativePet', 'moment', mom('quirk', { quirk: 'toy_hoarder' }));
      out.q1 = q1.ok && q1.changed && q1again.ok && !q1again.changed;
      await command('NativePet', 'moment', mom('quirk', { quirk: 'lap_barnacle' }));
      await command('NativePet', 'moment', mom('quirk', { quirk: 'hydro_inspector' }));
      const q4 = await command('NativePet', 'moment', mom('quirk', { quirk: 'night_owl_sentinel' }));
      out.acq = state.pet.brain.qk.acq.slice();
      out.q4 = q4.ok && !q4.changed;
      out.quirkAlbum = album().includes('quirk');
      out.ops = (stored().petMomentOps || []).length;
      return out;
    });
    ok('a first moment: ok and changed', r.a && r.a.ok && r.a.changed, r.a);
    ok('it sets the brain\'s flag and the album keeps it', r.glassFx && r.glassAlbum, r);
    ok('the same op again: "Already recorded." and nothing twice', r.again && r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.glassCount === 1, r);
    ok('the same moment under a new op: ok, no change, still one album entry', r.other && r.other.ok && !r.other.changed && r.glassCount === 1, r);
    for (const k of ['first_co', 'first_zoomies', 'first_belly', 'first_vigil', 'first_trophy', 'blink', 'respect']) ok('moment ' + k + ': the flag is set and the album has the entry', r[k], r);
    ok('a moment is the named cat\'s own: the second cat has its own flag', r.lily && r.lily.changed && r.lilyFx, r);
    ok('a quirk is appended once', r.q1, r);
    ok('three quirks at most, the fourth is not added', r.acq.length === 3 && !r.acq.includes('night_owl_sentinel') && r.q4, r);
    ok('the quirk reaches the album', r.quirkAlbum, r);
    ok('the ops are kept with the saved game, at most 40', r.ops > 0 && r.ops <= 40, r.ops);

    /* 4. the gift */
    r = await p.evaluate(async () => {
      const out = {};
      out.low = await command('NativePet', 'gift', { op: rid(), cat: ids()[0] });
      state.pet.bondPts = PET_BOND_AT[5]; state.pet.lastGiftDay = ''; state.pet.gifts = 0; save();
      const op = rid();
      out.a = await command('NativePet', 'gift', { op, cat: ids()[0] });
      out.gifts = state.pet.gifts; out.day = state.pet.lastGiftDay === petDay();
      out.again = await command('NativePet', 'gift', { op, cat: ids()[0] });
      out.second = await command('NativePet', 'gift', { op: rid(), cat: ids()[0] });
      out.counted = state.pet.gifts;
      out.album = state.pet.album.entries.some(e => e.kind === 'gift');
      out.saved = stored().pet.gifts;
      return out;
    });
    ok('a gift from a cat that is not Devoted is refused', r.low && !r.low.ok && r.low.why === 'refused', r.low);
    ok('a gift from a Devoted cat: ok, counted once, today recorded', r.a && r.a.ok && r.a.changed && r.gifts === 1 && r.day && r.saved === 1, r);
    ok('the same gift again: "Already recorded."', r.again && r.again.ok && !r.again.changed && r.again.message === 'Already recorded.', r.again);
    ok('a second gift on the same day is refused', r.second && !r.second.ok && r.second.why === 'refused' && r.counted === 1, r);
    ok('the album keeps the gift', r.album, r);

    /* 5. dwell */
    r = await p.evaluate(async () => {
      const out = {}, lk = () => state.pet.likes, day = petDay();
      state.pet.likes = null; petLikes(state.pet); save();
      const op = rid();
      out.a = await command('NativePet', 'dwell', { op, cat: ids()[0], day, spots: { bed: 3 * 3600, rug: 600 }, toys: { mouse: 600 } });
      out.bed = Math.round(lk().spots.bed * 100) / 100; out.rug = Math.round(lk().spots.rug * 100) / 100; out.mouse = lk().toys.mouse;
      out.dayT = lk().dayT === new Date(...day.split('-').map((v, i) => i === 1 ? v - 1 : +v)).getTime();
      out.again = await command('NativePet', 'dwell', { op, cat: ids()[0], day, spots: { bed: 3 * 3600 }, toys: {} });
      out.bedAfter = Math.round(lk().spots.bed * 100) / 100;
      await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day, spots: { bed: 10 * 3600 }, toys: { mouse: 1200 } });
      out.favSpot = lk().favSpot; out.favToy = lk().favToy;
      out.album = state.pet.album.entries.map(e => e.kind).filter(k => /fav_/.test(k)).sort();
      const d = new Date(); d.setDate(d.getDate() + 1); const tomorrow = d.toLocaleDateString('en-CA');
      const before = lk().spots.bed;
      out.tomorrow = await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day: tomorrow, spots: { rug: 60 }, toys: {} });
      out.faded = Math.abs(lk().spots.bed - before * 0.95) < 1e-9;
      const y = new Date(); y.setDate(y.getDate() - 30);
      out.old = await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day: y.toLocaleDateString('en-CA'), spots: { bed: 600 }, toys: {} });
      const f = new Date(); f.setDate(f.getDate() + 5);
      out.future = await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day: f.toLocaleDateString('en-CA'), spots: { bed: 600 }, toys: {} });
      out.empty = await command('NativePet', 'dwell', { op: rid(), cat: ids()[0], day, spots: {}, toys: {} });
      return out;
    });
    ok('a dwell: ok, changed, the place and toy are credited by the hour and the minute', r.a && r.a.ok && r.a.changed && r.bed === 2.1 && r.rug === 0.12 && r.mouse === 5, r);
    ok('the likes\' day is the report\'s day', r.dayT, r);
    ok('the same dwell again: "Already recorded." and nothing credited twice', r.again && r.again.ok && !r.again.changed && r.bedAfter === r.bed, r);
    ok('hours in a place make it the favourite place and a toy the favourite toy', r.favSpot === 'bed' && r.favToy === 'mouse', r);
    ok('the album notices both favourites', JSON.stringify(r.album) === JSON.stringify(['fav_spot', 'fav_toy']), r.album);
    ok('a new day fades the old habits once, 5 %', r.tomorrow && r.tomorrow.ok && r.faded, r);
    ok('a day older than a week is refused', r.old && !r.old.ok && r.old.why === 'refused', r.old);
    ok('a day in the future is refused', r.future && !r.future.ok && r.future.why === 'refused', r.future);
    ok('a dwell of nothing is recorded as seen and changes nothing', r.empty && r.empty.ok && !r.empty.changed, r.empty);

    /* 6. refused saves are taken back and say nothing */
    r = await p.evaluate(async () => {
      const out = {}, snap = () => JSON.stringify([state.pet, state.pet2, state.petMomentOps]);
      state.pet2.bondPts = PET_BOND_AT[5]; state.pet2.lastGiftDay = ''; save();
      const shown = [], orig = window.toast; window.toast = function (m) { shown.push(String(m)); return orig.apply(this, arguments) };
      const cases = {
        moment: ['moment', mom('first_vigil', { cat: ids()[1] })],
        quirk: ['moment', mom('quirk', { cat: ids()[1], quirk: 'toy_hoarder' })],
        gift: ['gift', { op: rid(), cat: ids()[1] }],
        dwell: ['dwell', { op: rid(), cat: ids()[1], day: petDay(), spots: { bed: 3600 }, toys: { ball_a: 600 } }],
      };
      const write = writeSave;
      for (const k in cases) {
        const before = snap(); writeSave = () => false; let a; try { a = await command('NativePet', cases[k][0], cases[k][1]) } finally { writeSave = write }
        await wait(150);
        out[k] = { refused: a && !a.ok && a.why === 'unsaved', same: snap() === before, quiet: shown.length === 0 };
        const b2 = await command('NativePet', cases[k][0], cases[k][1]);
        out[k].retry = b2 && b2.ok && b2.changed;
        shown.length = 0;
        out[k].said = '';
      }
      window.toast = orig;
      return out;
    });
    for (const k of ['moment', 'quirk', 'gift', 'dwell']) {
      ok('a ' + k + ' the browser could not store: not ok, taken back, silent', r[k].refused && r[k].same && r[k].quiet, r[k]);
      ok('its retry is recorded', r[k].retry, r[k]);
    }

    /* 7. a reload keeps the ops */
    const op = await p.evaluate(async () => { const m = mom('first_belly', { cat: ids()[1], op: 'c:t:kept' }); await command('NativePet', 'moment', m); return m.op });
    await p.reload(); await p.evaluate(HELPERS);
    r = await p.evaluate(async (op) => { await until(() => typeof petInit === 'function' && typeof state === 'object' && !!state, 20000);
      lmaThumbWarm = async () => { }; await petInit(); navigate('pet'); await until(() => showing('NativePet'), 20000);
      const a = await command('NativePet', 'moment', mom('first_belly', { cat: ids()[1], op })); return { a, ops: state.petMomentOps } }, op);
    ok('a moment replayed after a reload: "Already recorded." (the op is kept with the save)', r.a && r.a.ok && !r.a.changed && r.a.message === 'Already recorded.' && r.ops.includes(op), r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 8. the Overview cards take them too */
    p = await start(b, { cards: true });
    r = await p.evaluate(async () => {
      navigate('dashboard'); await until(() => showing('NativeOverviewCards'), 6000);
      const m = await command('NativeOverviewCards', 'moment', mom('first_co'));
      const d = await command('NativeOverviewCards', 'dwell', { op: rid(), cat: ids()[0], day: petDay(), spots: { sill: 1800 }, toys: {} });
      return { m, d, fx: state.pet.brain.mem.fx.first_co, sill: state.pet.likes.spots.sill };
    });
    ok('the Overview cards take a moment and a dwell the world reports while they show', r.m && r.m.ok && r.d && r.d.ok && r.fx === 1 && r.sill === 0.35, r);
    ok('no page errors (cards)', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close(); site.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
