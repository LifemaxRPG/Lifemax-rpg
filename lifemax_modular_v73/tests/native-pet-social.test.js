/* Native pair moments (pet.social.v1), web half (docs/specs/CAT_BRAIN_PLAN.md CB5), through the real page:
   - gating: a screen without pet.social.v1 gets exactly the world block it got (no `met` in its pair) and the social command is
     refused; with it the pair says whether the two have met;
   - the `social` command {op, kind, cats, winner?, at}: every malformed shape refused; each kind recorded EXACTLY as the page's own
     pair director records the end of that moment (a differential test: the same save, once through the command and once through
     the director's own end function, 29-pet-pair.js / 29-pet-rank.js, with the director's coin forced the same way, give the same
     state.petPair: friendship, the day's gain and cap, counts, the last line, the meeting, the pecking order);
   - ok only once stored, a refused save taken back; a repeat answered "Already recorded." even after a reload; the Together card
     of the native Cat tab shows the new counts and last line; the Overview cards take the command too.
   RECORD_FIXTURE=1 writes the recorded outcomes for the native FakePage's check (android-wrapper/native/src/test/resources/fixtures/
   pet-social.json, SocialFixtureTest).
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-social.test.js [built html] */
const { chromium } = require('playwright');
const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');
const SOCIAL = 'pet.social.v1';
const PET_CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1', 'pet.world.v1', 'pet.panels.v1', 'pet.play.v1'];

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
  window.moment = (o) => Object.assign({ op: 'c:social:' + Math.random().toString(36).slice(2), kind: 'chase', cats: ids(), at: Date.now() }, o);
  window.stored = () => JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1'));
  /* the friendship record as compared: times dropped (the director dates its line with the clock, native with the moment's time),
     the meeting as a flag */
  window.pairView = () => { const d = JSON.parse(JSON.stringify(state.petPair)); delete d.seen; if (d.rk && !d.rk.n) delete d.rk; if (d.last) delete d.last.at; d.met = !!d.met; d.lastGreet = !!d.lastGreet; return d };
  window.withRandom = (v, f) => { const r = Math.random; Math.random = () => v; try { return f() } finally { Math.random = r } };
  window.pairAt = (rel, extra) => { petPairData(); Object.assign(state.petPair, { rel, met: Date.now() - 864e5, day: '', gain: 0, n: { greet: 0, hiss: 0, chase: 0, groom: 0, nap: 0 }, last: null, lastGreet: 0 }, extra || {}); delete state.petPair.rk; };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { petCaps = [...PET_CAPS, SOCIAL], cardCaps = ['overview.cards.v1', 'pet.world.v1', SOCIAL], cards = false } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript(([save, extras, prefs]) => {
    if (!localStorage.getItem('lifemax_rpg_prototype_v1')) { localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]) }
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify({ nativePet: true, nativeOverview: false, nativeOverviewCards: cards, apps: [] })]);
  await p.addInitScript(FAKE);
  await p.addInitScript(([a, c]) => { window.__caps.NativePet = a; window.__caps.NativeOverviewCards = c }, [petCaps, cardCaps]);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async ([a, b]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { }
    await setCats('state.pet=' + a + ';state.pet2=' + b + ';state.petPair=null'); const rt = await petInit(); petPairEnsure(rt) },
  [CAT('Ted', "{dna:null}"), CAT('Lily', "{coat:'gray',born:now-35*864e5,trait:'shy'}")]);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return showing('NativePet') });

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const fixture = { v: 1, steps: [] };
  try {
    /* 1. gating */
    let p = await start(b, { petCaps: PET_CAPS });
    await toPet(p);
    let r = await p.evaluate(async () => { const w = latest('NativePet').world; return { keys: Object.keys(w.pair).sort(), refused: await command('NativePet', 'social', moment()), n: state.petPair && state.petPair.n.chase } });
    ok('a screen without pet.social.v1: its pair is as it was (no met)', JSON.stringify(r.keys) === JSON.stringify(['gainLeft', 'lead', 'level', 'rel', 'settled']), r.keys);
    ok('a screen without pet.social.v1: the social command is refused, nothing counted', r.refused && !r.refused.ok && r.refused.why === 'shape' && !r.n, r);
    await p.context().close();

    p = await start(b);
    ok('a Cat screen with pet.social.v1 opens', await toPet(p));
    r = await p.evaluate(async () => { renderPetView(true); await wait(150); return latest('NativePet').world.pair });
    ok('with pet.social.v1 the pair says whether the two have met', r && r.met === false, r);

    /* 2. malformed shapes */
    r = await p.evaluate(async () => {
      const bad = {
        extraKey: moment({ x: 1 }), kind: moment({ kind: 'nap2' }), sameCats: moment({ cats: [ids()[0], ids()[0]] }), oneCat: moment({ cats: [ids()[0]] }),
        badId: moment({ cats: ['Ted', ids()[1]] }), at: moment({ at: 1.5 }), op: moment({ op: 'bad op!' }), winnerNotStandoff: moment({ winner: ids()[0] }),
        standoffNoWinner: moment({ kind: 'standoff' }), winnerStranger: moment({ kind: 'standoff', winner: 'cabc' }), array: [1],
      };
      const out = {};
      for (const k in bad) { const a = await command('NativePet', 'social', bad[k]); out[k] = !!a && !a.ok && a.why === 'shape' }
      out.stranger = await command('NativePet', 'social', moment({ cats: ['czzzzzz', ids()[1]] }));
      out.n = JSON.stringify(state.petPair ? state.petPair.n : null);
      return out;
    });
    for (const k of ['extraKey', 'kind', 'sameCats', 'oneCat', 'badId', 'at', 'op', 'winnerNotStandoff', 'standoffNoWinner', 'winnerStranger', 'array']) ok('a malformed moment is refused: ' + k, r[k], r);
    ok('a moment of a cat that is not here is refused', r.stranger && !r.stranger.ok && r.stranger.why === 'refused', r.stranger);

    /* 3. the differential: the command and the page's own director record the same */
    r = await p.evaluate(async () => {
      const I = (a, b, o) => Object.assign({ a, b, opt: {}, rounds: 0, queue: [] }, o || {});
      const cases = [];
      const run = async (name, rel, extra, commands, director) => {
        pairAt(rel, extra); const start = JSON.stringify(state.petPair);
        for (const c of commands) { const a = await command('NativePet', 'social', moment(c)); if (!a || !a.ok) return cases.push({ name, refused: a }) }
        const viaNative = pairView();
        state.petPair = JSON.parse(start); director(); save(); const viaPage = pairView();
        cases.push({ name, same: JSON.stringify(viaNative) === JSON.stringify(viaPage), viaNative, viaPage, start: JSON.parse(start), commands });
      };
      await run('first meeting, a hiss', 0.1, { met: 0 }, [{ kind: 'hiss', cats: [ids()[0], ids()[1]] }], () => withRandom(0, () => petPairGreetEnd(I(0, 1))));
      await run('a nose touch', 0.3, {}, [{ kind: 'greet' }], () => withRandom(0.99, () => petPairGreetEnd(I(0, 1))));
      await run('a cheek rub', 0.62, {}, [{ kind: 'greet', cats: [ids()[1], ids()[0]] }], () => withRandom(0.99, () => petPairGreetEnd(I(1, 0))));
      await run('a chase', 0.4, {}, [{ kind: 'chase' }], () => petPairChaseMore(I(0, 1, { rounds: 2 })));
      await run('grooming', 0.5, {}, [{ kind: 'groom', cats: [ids()[1], ids()[0]] }], () => withRandom(0.99, () => petPairGroomEnd(I(1, 0))));
      await run('grooming, then a nap together', 0.7, {}, [{ kind: 'groom' }, { kind: 'nap' }], () => petPairGroomEnd(I(0, 1, { opt: { nap: 1 } })));
      await run('the daily cap', 0.5, { day: petDay(), gain: 0.15 }, [{ kind: 'groom' }], () => withRandom(0.99, () => petPairGroomEnd(I(0, 1))));
      // a standoff: the director's coin decides; the command carries the same winner
      for (const v of [0, 0.999]) {
        pairAt(0.5); const s0 = JSON.stringify(state.petPair);
        const res = withRandom(v, () => { petPairStandoffEnd(I(0, 1)); return state.petPair.rk.hist[state.petPair.rk.hist.length - 1] });
        state.petPair = JSON.parse(s0);
        await run('a standoff won by cat ' + res, 0.5, {}, [{ kind: 'standoff', winner: ids()[res] }], () => withRandom(v, () => petPairStandoffEnd(I(0, 1))));
      }
      return cases;
    });
    for (const c of r) ok('the command records as the page\'s director: ' + c.name, c.same, c);
    for (const c of r) if (c.same) fixture.steps.push({ name: c.name, start: { rel: c.start.rel, gain: c.start.gain, sameDay: !!c.start.day }, kinds: c.commands.map(k => k.kind), rel: c.viaNative.rel, gain: Math.round(c.viaNative.gain * 1000) / 1000, n: c.viaNative.n, met: c.viaNative.met, last: c.viaNative.last && c.viaNative.last.k });
    r = await p.evaluate(() => state.petPair.rk);
    ok('a standoff moved the pecking order (petRankContest, petRankUpdate)', r && r.n === 1 && r.hist.length === 1, r);

    /* 4. stored once, refused saves taken back, repeats, the card */
    r = await p.evaluate(async () => {
      pairAt(0.5); save(); await wait(100);
      const m = moment({ kind: 'chase', at: Date.now() - 20 * 60000 }), a = await command('NativePet', 'social', m), s = stored();
      const again = await command('NativePet', 'social', m);
      renderPetView(true); await wait(250);
      const card = latest('NativePet').panels && latest('NativePet').panels.pair && latest('NativePet').panels.pair.card;
      return { a, again, n: state.petPair.n.chase, savedN: s.petPair.n.chase, ops: s.petSocialOps, op: m.op, card };
    });
    ok('a moment: ok, changed, counted once and stored with its op', r.a.ok && r.a.changed && r.n === 1 && r.savedN === 1 && r.ops.includes(r.op), r);
    ok('the same moment again: "Already recorded." and no second count', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.n === 1, r.again);
    ok('the Together card shows the count and the last line, dated by the moment', r.card && /1 chase/.test(r.card.counts) && /played chase, 20 min/.test(r.card.last), r.card);
    r = await p.evaluate(async () => {
      const m = moment({ kind: 'groom' }), before = JSON.stringify([state.petPair, state.petSocialOps]);
      const write = writeSave; writeSave = () => false; let a; try { a = await command('NativePet', 'social', m) } finally { writeSave = write }
      return { a, same: JSON.stringify([state.petPair, state.petSocialOps]) === before };
    });
    ok('a moment the browser could not store: not ok, taken back', r.a && !r.a.ok && r.a.why === 'unsaved' && r.same, r);
    const op = await p.evaluate(async () => { const m = moment({ op: 'c:social:kept', kind: 'nap' }); await command('NativePet', 'social', m); return m.op });
    await p.reload(); await p.evaluate(HELPERS);
    r = await p.evaluate(async (op) => { lmaThumbWarm = async () => { }; await petInit(); navigate('pet'); await until(() => showing('NativePet'), 6000);
      await until(() => !!state.petPair, 3000);
      const n = state.petPair.n.nap; const a = await command('NativePet', 'social', moment({ op, kind: 'nap' })); return { a, same: state.petPair.n.nap === n, n } }, op);
    ok('a moment replayed after a reload: "Already recorded." (the op is kept with the save)', r.a && r.a.ok && r.a.message === 'Already recorded.' && r.same && r.n === 1, r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 5. the Overview cards take it too */
    p = await start(b, { cards: true });
    r = await p.evaluate(async () => {
      navigate('dashboard'); await until(() => showing('NativeOverviewCards'), 6000);
      const c = await command('NativeOverviewCards', 'social', moment({ kind: 'greet' }));
      return { c, n: state.petPair.n.greet, met: !!state.petPair.met };
    });
    ok('the Overview cards take a moment the world reports while they show', r.c && r.c.ok && r.n === 1 && r.met, r);
    ok('no page errors (cards)', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  if (process.env.RECORD_FIXTURE && !fail) {
    const out = path.join(__dirname, '../../android-wrapper/native/src/test/resources/fixtures/pet-social.json');
    fs.writeFileSync(out, JSON.stringify(fixture, null, 1) + '\n');
    console.log('wrote ' + out);
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
