/* The cat world contract (pet.world.v1), web half (docs/specs/CAT_BRAIN_PLAN.md CB0), through the real page:
   - capability gating: the Cat tab's and the Overview cards' projections carry the `world` block only for a screen that advertised
     pet.world.v1; an older screen's projection is exactly what it was, and the world command is refused for it;
   - the block's contents equal the page's own data (identity, DNA personality, bond, hunger, counters, care caps, likes, toys, the
     pair, the fed cue) for a kitten, a bonded adult, a hungry cat, two cats, vacation and a genetic coat; seq rises with a change
     only; building the block writes nothing and changes nothing;
   - the world command: stored in the save (ok only then, the reply's epoch and rev), back in the next projection, a repeat by op
     "Already recorded.", a stale revision and a wrong epoch refused, an oversize blob refused, a blob for other cats refused, a
     newer version refused, a save the browser refuses taken back; a backup round-trip keeps the world and raises the epoch, so
     the world native held from before is refused as stale;
   - the one-time import (petWorldLegacy): a rich web brain, a v77 save with bond points only, a damaged brain and a stray with no
     brain, read without creating anything; and a stored world for a cat leaves its legacy out.
   The blocks and imports are also kept as goldens in tests/fixtures/cat-world-golden.json, which the native decoders read
   (WorldFixtureTest); RECORD=1 rewrites them. Fake NativePet and NativeOverviewCards plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-cat-world.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');
const GOLDEN = path.join(__dirname, 'fixtures', 'cat-world-golden.json');
const CLOCK = '2026-10-30T12:00:00Z';
const WORLD = 'pet.world.v1';
const PET_CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1'];

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
  window.latest = (n) => { const c = [...__calls].reverse().find(c => c[0] === n && (c[1] === 'open' || c[1] === 'update')); return c && c[2].snapshot };
  window.command = async (n, command, payload) => { const k = __calls.length, id = 'r-' + k; await Capacitor.Plugins[n].__emit('command', { command, payload, requestId: id });
    await until(() => __calls.slice(k).some(c => c[0] === n && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id), 3000);
    const u = __calls.slice(k).find(c => c[0] === n && c[1] === 'update' && c[2].reply && c[2].reply.requestId === id); return u ? u[2].reply : null };
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(200) };
  window.world = (blob, o) => Object.assign({ op: 'w-' + Math.random().toString(36).slice(2), epoch: petWorldBlock(Date.now()).epoch, rev: ((state.petWorld && state.petWorld.rev) | 0) + 1, blob }, o);
  window.blobFor = (extra) => { const cats = {}; [state.pet, state.pet2].filter(p => p && p.alive).forEach(p => cats[petAlbumCatId(p)] = { act: 'loaf' }); return Object.assign({ v: 1, cats }, extra) };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { petCaps = [...PET_CAPS, WORLD], cardCaps = ['overview.cards.v1', WORLD], cards = false, pet = CAT('Miso'), pet2 = '', fixed = false } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  if (fixed) await p.clock.setFixedTime(new Date(CLOCK));
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify({ nativePet: true, nativeOverview: false, nativeOverviewCards: cards, apps: [] })]);
  await p.addInitScript(FAKE);
  await p.addInitScript(([a, c]) => { window.__caps.NativePet = a; window.__caps.NativeOverviewCards = c }, [petCaps, cardCaps]);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async ([pet, pet2]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } if (pet) await setCats('state.pet=' + pet + (pet2 ? ';state.pet2=' + pet2 : '')); await petInit() }, [pet, pet2]);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return showing('NativePet') });

/* the brains the import is tried on: a rich one (Ted's kind), a v77 save (bond points, no brain), a damaged one, and a stray */
const RICH = `{v:1,af:{v:0.42,a:-0.2,s:0.1,t:now},at:{fam:0.8,tru:0.74,sec:0.6,aut:0.55,dg:0,day:'',style:'secure'},
  mem:{w:{bed:0.21,couch:0.16,tree:0.12,sill:0.08,box:0.07,pouf:0.05,rug:0.04,front_living:0.1,front_bedroom:0.06,lrug:0.03,kitchen:0.02},ep:[],core:[],fx:{first_glass:1,first_blink:1,first_vigil:0,bogus:1}},
  rt:{h:[0,0,0,0,0,0,0.2,0.7,0.6,0.1,0,0,0.3,0.2,0,0,0,0.1,0.5,0.8,0.9,0.6,0.3,0.1],last:now,night:''},
  qk:{inn:['dramatic_flopper','chirp_greeter'],acq:['lap_barnacle','not_a_quirk'],c:{n:3,t:7,g:21,k:2.6}},zm:{e:0.71,at:now},vo:{p:1.043,t:0.5},
  items:[['ball_a',140.4,62.25,1],['nope',1,1,0],['mouse',400,130,0]],off:{at:now,acts:[],scene:''},sl:{st:'none',pose:''},hu:{gifts:2},co:{min:3}}`;
const DAMAGED = `{v:1,af:{v:'happy',a:null},at:{fam:7,tru:-1},mem:{w:null,fx:'x'},rt:{h:[1,2,3]},qk:{inn:'flop',c:null},zm:{e:NaN},vo:{p:9},items:'none'}`;
const STATES = {
  kitten: CAT('Pip', "{coat:'tuxedo',born:now-12*864e5,bondPts:PET_BOND_AT[1]}"),
  bonded: CAT('Ted', `{bondPts:PET_BOND_AT[5]+40,petsToday:2,playsToday:2,fedToday:1,lastFed:now-2*36e5,likes:{v:1,spots:{bed:30,couch:12.5,box:4,nowhere:9},toys:{feather:8,ball_a:2},plays:{},seen:{},dayT:0,favSpot:'bed',favToy:'feather',news:[]},brain:${RICH},bond:{posX:12,posZ:61.5,elevationY:0,at:now-60000}}`),
  hungry: CAT('Miso', '{lastFed:now-20*36e5,day:"2000-01-01",petsToday:3}'),
  vacation: CAT('Miso', '{vacation:{on:true,since:now-3*36e5},lastFed:now-9*36e5}'),
  dna: CAT('Miso', "{coat:'dna_black_1_n_2_0_n_0_3'}"),
};

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const out = { blocks: {}, legacy: {} };
  try {
    /* 1. gating: an older screen gets exactly what it got, a screen with pet.world.v1 gets the block */
    let p = await start(b, { petCaps: PET_CAPS });
    let r = await p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet')); const s = latest('NativePet');
      return { keys: Object.keys(s), refused: await command('NativePet', 'world', world(blobFor())), stored: 'petWorld' in state } });
    ok('an older Cat screen (no pet.world.v1): no world block in its projection', !r.keys.includes('world'), r.keys);
    ok('an older Cat screen: the world command is refused and nothing is stored', r.refused && !r.refused.ok && r.refused.why === 'shape' && !r.stored, r.refused);
    await p.context().close();
    p = await start(b);
    ok('a Cat screen with pet.world.v1 opens', await toPet(p));
    r = await p.evaluate(() => { const s = latest('NativePet'); return { has: !!s.world, v: s.world && s.world.v, ids: s.world && s.world.cats.map(c => c.id), id: petAlbumCatId(state.pet) } });
    ok('a Cat screen with pet.world.v1: the projection carries the world block of the living cat', r.has && r.v === 1 && r.ids.length === 1 && r.ids[0] === r.id, r);
    await p.context().close();
    for (const withWorld of [false, true]) {
      p = await start(b, { cards: true, cardCaps: withWorld ? ['overview.cards.v1', WORLD] : ['overview.cards.v1'] });
      r = await p.evaluate(async () => { navigate('dashboard'); await until(() => showing('NativeOverviewCards'), 6000); const s = NativeOverviewCardsProjection.snapshot();
        return { open: showing('NativeOverviewCards'), world: s && s.world ? s.world.cats.map(c => c.name) : null, reply: await command('NativeOverviewCards', 'world', world(blobFor())) } });
      ok('Overview cards ' + (withWorld ? 'with' : 'without') + ' pet.world.v1: the world block is ' + (withWorld ? 'sent' : 'left out'),
        r.open && (withWorld ? JSON.stringify(r.world) === '["Miso"]' : r.world === null), r);
      ok('Overview cards ' + (withWorld ? 'with' : 'without') + ' pet.world.v1: the world command is ' + (withWorld ? 'stored' : 'refused'),
        r.reply && (withWorld ? r.reply.ok && r.reply.changed && r.reply.rev === 1 : !r.reply.ok && r.reply.why === 'shape'), r.reply);
      await p.context().close();
    }

    /* 2. the block equals the page's own data, state by state; it writes nothing */
    p = await start(b, { fixed: true });
    for (const [name, set] of Object.entries(STATES)) {
      r = await p.evaluate(async ([name, set]) => {
        // set as a save would load it, without a render: the page's room would move the cat and so its saved position
        (0, eval)('(function(){const now=Date.now();state.pet=' + set + ';delete state.pet2})()');
        if (name === 'dna') petDnaRegister(state.pet.coat);
        const now = Date.now(), before = JSON.stringify(state), writes = [], put = Storage.prototype.setItem;
        Storage.prototype.setItem = function (k) { writes.push(k); return put.apply(this, arguments) };
        let w; try { w = petWorldBlock(now) } finally { Storage.prototype.setItem = put }
        const c = w.cats[0], P = state.pet, view = Object.assign({}, P, { dna: P.dna && JSON.parse(JSON.stringify(P.dna)) }), ph = petPheno(view), d = P.day === getToday();
        const want = { id: petAlbumCatId(P), slot: 0, name: P.name, born: P.born, sex: petSexOf(view), coat: P.coat, stage: petGrowIdx(P), bond: { lvl: petLevel(P), pct: petLevelPct(P) },
          zMax: petDepthMaxZ(P), hungerStage: petStage(P, now), lastFed: P.lastFed, vacation: !!P.vacation.on, fedToday: d ? P.fedToday : 0, petsToday: d ? P.petsToday : 0, playsToday: d ? P.playsToday : 0,
          feedAt: P.lastFed + 4 * 36e5, canFeed: petCanFeed(P, now) && !P.vacation.on, giftOk: petLevel(P) >= 5 && P.lastGiftDay !== getToday(), toys: Object.keys(PET_TOYS).filter(t => petToyUnlocked(P, t)),
          ps: PET_PERSONA.map(k => Math.round(ph.ps[k] * 1000) / 1000), favSpot: (P.likes && P.likes.favSpot) || null };
        const got = { id: c.id, slot: c.slot, name: c.name, born: c.born, sex: c.sex, coat: c.coat, stage: c.stage, bond: c.bond, zMax: c.zMax, hungerStage: c.hunger.stage, lastFed: c.hunger.lastFed,
          vacation: c.hunger.vacation, fedToday: c.care.fedToday, petsToday: c.care.petsToday, playsToday: c.care.playsToday, feedAt: c.care.feedAt, canFeed: now >= c.care.feedAt && !c.hunger.vacation,
          giftOk: c.care.giftOk, toys: c.toys, ps: PET_PERSONA.map(k => c.ps[k]), favSpot: c.likes.favSpot };
        return { want, got, w, same: JSON.stringify(state) === before, writes: writes.length, again: petWorldBlock(now).seq === w.seq, legacy: !!c.legacy };
      }, [name, set]);
      ok('the block equals the page\'s data: ' + name, JSON.stringify(r.want) === JSON.stringify(r.got), [r.want, r.got]);
      ok('building the block writes nothing and changes nothing: ' + name, r.same && r.writes === 0, r);
      ok('seq stays put while nothing changes, and a cat without a stored world brings its legacy: ' + name, r.again && r.legacy, r);
      out.blocks[name] = r.w;
    }
    r = await p.evaluate(async (set) => { await setCats('state.pet=' + set); const c = petWorldBlock(Date.now()).cats[0]; return { spots: c.likes.spots, toys: c.likes.toys, fav: [c.likes.favSpot, c.likes.favToy] } }, STATES.bonded);
    ok('the bonded cat\'s likes: places and toys as shares of the leader, an unknown place left out', JSON.stringify(r) === JSON.stringify({ spots: { bed: 100, couch: 42, box: 13 }, toys: { feather: 100, ball_a: 25 }, fav: ['bed', 'feather'] }), r);
    r = await p.evaluate(async () => { const h = petWorldBlock(Date.now()).cats[0].hunger; return h });
    ok('hunger: the next stage begins where the page\'s stage limits say (stage 0 ends 8 h after the meal)', r.stage === 0 && r.nextStageAt === r.lastFed + 8 * 36e5, r);
    r = await p.evaluate(async (s) => { await setCats('state.pet=' + s); return petWorldBlock(Date.now()).cats[0].hunger }, STATES.vacation);
    ok('vacation: the hunger clock is paused, so no next stage is announced', r.vacation && r.nextStageAt === null, r);
    r = await p.evaluate(async ([a, c]) => {
      (0, eval)('(function(){const now=Date.now();state.pet=' + a + ';state.pet2=' + c + ';state.petPair={v:1,a:state.pet.born,b:state.pet2.born,rel:0.62,met:3,day:getToday(),gain:0.05,n:{greet:2,hiss:0,chase:1,groom:1,nap:0},last:null,lastGreet:0,seen:now,away:null,rk:{v:1,cf:[0.6,0.4],n:5,w:[3,2],ups:0,flips:0,lead:0,settled:true,hist:[],day:""}}})()');
      const w = petWorldBlock(Date.now()); return { w, slots: w.cats.map(c => c.slot + c.name), level: PET_PAIR_LEVELS.indexOf(petPairLevel(0.62)) } }, [CAT('Ted'), CAT('Lily', "{coat:'gray',born:now-20*864e5}")]);
    ok('two cats: both, with their slots, and the pair as the page keeps it (rel, level, lead, settled, the day\'s gain left)',
      JSON.stringify(r.slots) === '["0Ted","1Lily"]' && JSON.stringify(r.w.pair) === JSON.stringify({ rel: 0.62, level: r.level, lead: 0, settled: true, gainLeft: 0.11 }), r.w.pair);
    out.blocks.twoCats = r.w;
    r = await p.evaluate(async () => { await setCats('state.pet2.alive=false;state.pet2.passed=now'); return petWorldBlock(Date.now()) });
    ok('a second cat that has passed leaves the block, and the pair with it', r.cats.length === 1 && r.pair === null, r.pair);

    /* 3. the import from the web brain: read, never created */
    const IMPORTS = {
      rich: CAT('Ted', '{brain:' + RICH + ',bond:{posX:-50,posZ:44,elevationY:3,at:now-3e5}}'),
      damaged: CAT('Miso', '{brain:' + DAMAGED + '}'),
      v77: CAT('Miso', '{bondPts:300,likes:{v:1,spots:{sill:5,tree:10},toys:{},plays:{},seen:{},dayT:0,favSpot:null,favToy:null,news:[]},bond:{posX:0,posZ:20,elevationY:0,at:now-36e5}}'),
      fresh: CAT('Miso', '{born:now-864e5,bondPts:0,trait:"shy"}'),
    };
    for (const [name, set] of Object.entries(IMPORTS)) {
      r = await p.evaluate(async ([name, set]) => {
        // set as a save would load it, without a render (the page's room would create a brain and move the cat)
        (0, eval)('(function(){const now=Date.now();state.pet=' + set + '})()');
        const before = JSON.stringify(state), l = petWorldLegacy(state.pet, Date.now());
        return { l, same: JSON.stringify(state) === before, noBrain: name === 'v77' || name === 'fresh' ? !('brain' in state.pet) : true };
      }, [name, set]);
      out.legacy[name] = r.l;
      ok('the import reads without creating anything: ' + name, r.same && r.noBrain, r);
    }
    const L = out.legacy;
    ok('rich brain: mood and the four threads carried over', JSON.stringify([L.rich.mood, L.rich.trust]) === JSON.stringify([{ v: 0.42, a: -0.2 }, { fam: 0.8, tru: 0.74, sec: 0.6, aut: 0.55 }]), L.rich);
    ok('rich brain: the six heaviest places of its memory (x100), the front zones as the glass', JSON.stringify(L.rich.spots) === JSON.stringify({ bed: 21, couch: 16, tree: 12, sill: 8, box: 7, pouf: 5 }) && L.rich.glass === 16, L.rich);
    ok('rich brain: the reported first moments only (a zero or an unknown one left out), innate quirks and counters, rhythm x100, energy, voice',
      JSON.stringify(L.rich.fx) === '["first_blink","first_glass"]' && JSON.stringify(L.rich.qk) === JSON.stringify({ inn: ['dramatic_flopper', 'chirp_greeter'], c: { n: 3, t: 7, g: 21, k: 2 } }) &&
      L.rich.rhythm.length === 24 && L.rich.rhythm[20] === 90 && L.rich.energy === 0.71 && L.rich.voice === 1.043 && L.rich.src === 'brain', L.rich);
    ok('rich brain: the toys it left on the floor (known toys only, kept in the house) and the fresh position',
      JSON.stringify(L.rich.toys) === JSON.stringify([{ id: 'ball_a', x: 140, z: 62.3 }, { id: 'mouse', x: 346, z: 100 }]) && L.rich.pos && L.rich.pos.z === 44 && L.rich.pos.elev === 3, [L.rich.toys, L.rich.pos]);
    ok('damaged brain: every bad field falls back to the page\'s default, nothing throws', L.damaged.src === 'brain' && L.damaged.mood.v === 0.2 && L.damaged.trust.fam === 1 && L.damaged.trust.tru === 0 &&
      L.damaged.fx.length === 0 && JSON.stringify(L.damaged.qk.inn) === JSON.stringify(L.v77.qk.inn) && L.damaged.rhythm.every(v => v === 5) && L.damaged.energy === 0.5 &&
      L.damaged.voice === L.v77.voice && L.damaged.toys.length === 0, L.damaged);
    r = await p.evaluate(async (set) => { await setCats('state.pet=' + set); return { inn: state.pet.brain.qk.inn, voice: state.pet.brain.vo.p } }, IMPORTS.v77);
    ok('a cat without a brain: the innate quirks and voice the page would draw for it (migratePetBrainState, the same seed)',
      JSON.stringify(r.inn) === JSON.stringify(L.v77.qk.inn) && r.voice === L.v77.voice, [r, L.v77.qk.inn, L.v77.voice]);
    ok('v77 save (bond points, no brain): the threads seeded from the bond as the page would, an old position ignored, likes as the places',
      L.v77.src === 'fresh' && L.v77.trust.fam === Math.round((0.15 + 0.75 * 300 / 580) * 1000) / 1000 && L.v77.pos === null && JSON.stringify(L.v77.spots) === '{"tree":100,"sill":50}', L.v77);
    ok('a stray with no brain: the trait\'s defaults (shy autonomy and mood)', L.fresh.src === 'fresh' && L.fresh.trust.aut === 0.75 && L.fresh.mood.v === 0.1 && L.fresh.fx.length === 0, L.fresh);
    await p.context().close();

    /* 4. the world command, end to end on the Cat tab */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => {
      const o = {}, first = world(blobFor({ imported: 'fresh' }));
      o.stored = await command('NativePet', 'world', first);
      o.saved = JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).petWorld;
      await wait(100);
      o.block = NativePetProjection.snapshot().world;
      o.again = await command('NativePet', 'world', first);
      o.stale = await command('NativePet', 'world', world(blobFor(), { rev: 1 }));
      o.epoch = await command('NativePet', 'world', world(blobFor(), { epoch: 7 }));
      o.big = await command('NativePet', 'world', world(blobFor({ pad: 'x'.repeat(9000) })));
      o.others = await command('NativePet', 'world', world({ v: 1, cats: { c123: {} } }));
      o.newer = await command('NativePet', 'world', world(blobFor({ v: 2 })));
      o.unknown = await command('NativePet', 'world', Object.assign(world(blobFor()), { extra: 1 }));
      o.noOp = await command('NativePet', 'world', world(blobFor(), { op: '' }));
      o.rev = state.petWorld.rev;
      const put = Storage.prototype.setItem; Storage.prototype.setItem = function () { throw Error('full') };
      try { o.full = await command('NativePet', 'world', world(blobFor({ toys: [] }))) } finally { Storage.prototype.setItem = put }
      o.afterFull = { rev: state.petWorld.rev, toys: 'toys' in state.petWorld.blob };
      o.next = await command('NativePet', 'world', world(blobFor({ toys: [] })));
      return o;
    });
    ok('world: stored in the save, ok and changed, the reply names the epoch and the stored revision', r.stored.ok && r.stored.changed && r.stored.epoch === 0 && r.stored.rev === 1 && r.saved && r.saved.rev === 1 && r.saved.blob.imported === 'fresh', r.stored);
    ok('world: the next projection carries the stored world, and leaves the legacy out for a cat it holds', r.block.stored && r.block.stored.rev === 1 && r.block.stored.blob.imported === 'fresh' && !('legacy' in r.block.cats[0]), r.block.stored);
    ok('world: the same op again answers "Already recorded." and changes nothing', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.', r.again);
    ok('world: a revision not newer than the stored one is refused as stale', !r.stale.ok && r.stale.why === 'stale' && r.stale.rev === 1, r.stale);
    ok('world: another epoch is refused as stale', !r.epoch.ok && r.epoch.why === 'stale' && r.epoch.epoch === 0, r.epoch);
    ok('world: a blob over 8 KB is refused', !r.big.ok && r.big.why === 'size', r.big);
    ok('world: a blob for other cats, a newer version, an unknown key and a missing op are refused as malformed', [r.others, r.newer, r.unknown, r.noOp].every(x => !x.ok && x.why === 'shape'), [r.others, r.newer, r.unknown, r.noOp]);
    ok('world: a save the browser refuses is taken back (not ok, the stored world as it was)', !r.full.ok && r.full.why === 'unsaved' && r.afterFull.rev === r.rev && !r.afterFull.toys, [r.full, r.afterFull]);
    ok('world: once the save works again the next revision is stored', r.next.ok && r.next.changed && r.next.rev === r.rev + 1, r.next);

    /* 5. a backup carries the world; restoring it raises the epoch */
    r = await p.evaluate(async () => {
      window.confirm = () => true; window.toast = () => { };
      const backup = JSON.parse(backupJson()), rev = state.petWorld.rev, held = world(blobFor());
      const imp = await applyBackup(backup);
      const o = { imp: imp.ok, epoch: petWorldBlock(Date.now()).epoch, kept: state.petWorld && state.petWorld.rev === rev, savedEpoch: localStorage.getItem('lifemax_pet_world_epoch_v1'),
        same: JSON.stringify(JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).petWorld) === JSON.stringify(backup.state.petWorld), inSave: 'petWorldEpoch' in state };
      o.stale = await command('NativePet', 'world', held);
      o.fresh = await command('NativePet', 'world', world(blobFor()));
      return o;
    });
    ok('backup round-trip: the restored save keeps the stored world as it was backed up, and the epoch (beside the save, not in it) rises', r.imp && r.kept && r.same && !r.inSave && r.epoch === 1 && r.savedEpoch === '1', r);
    ok('backup round-trip: the world native held from before the restore is refused as stale; one for the new epoch is stored', !r.stale.ok && r.stale.why === 'stale' && r.fresh.ok && r.fresh.epoch === 1, r);
    r = await p.evaluate(async () => { const a = petWorldBlock(Date.now()).seq; petFeed(); const w = petWorldBlock(Date.now()); return { a, b: w.seq, fed: w.cue.fed } });
    ok('a meal: seq rises and the fed cue rides in the block', r.b > r.a && r.fed.length === 1 && r.fed[0].cat === 0, r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 6. what the cat world host sends (CB2): its operation ids, its outbox riding inside the blob, a lost reply sent again, a refused
       save sent again once storage works, and the epoch and revision it adopts after a backup restore */
    p = await start(b);
    await toPet(p);
    r = await p.evaluate(async () => {
      const o = {}, epoch = petWorldBlock(Date.now()).epoch, id = petAlbumCatId(state.pet);
      const moment = (n) => ({ op: id + ':first_glass' + n, cmd: 'moment', args: { op: id + ':first_glass' + n, cat: id, kind: 'first_glass', at: Date.now() } });
      const full = (rev, extra) => world(blobFor(Object.assign({ outbox: Array.from({ length: 16 }, (_, i) => moment(i)) }, extra)), { epoch, rev, op: 'world:' + epoch + ':' + rev });
      o.first = await command('NativePet', 'world', full(1));
      o.size = JSON.stringify(state.petWorld.blob).length;
      o.again = await command('NativePet', 'world', full(1)); // the reply was lost, native sends the same operation again
      o.revAfterAgain = state.petWorld.rev;
      const put = Storage.prototype.setItem; Storage.prototype.setItem = function () { throw Error('full') };
      try { o.unsaved = await command('NativePet', 'world', full(2)) } finally { Storage.prototype.setItem = put }
      o.retry = await command('NativePet', 'world', full(2)); // the same operation once storage works: stored, not "Already recorded."
      o.again2 = await command('NativePet', 'world', full(2)); // the same operation again
      o.stale = await command('NativePet', 'world', Object.assign(full(2), { op: 'world:' + epoch + ':2:b' })); // another operation for a revision already stored
      o.skip = await command('NativePet', 'world', full(5)); // a gap in revisions is fine: it only has to be newer
      o.ops = state.petWorld.op;
      o.block = petWorldBlock(Date.now()).stored;
      return o;
    });
    ok('host writes: a blob with a full outbox of 16 commands is stored, well under 8 KB', r.first.ok && r.first.changed && r.size < 8192 && r.first.rev === 1, [r.first, r.size]);
    ok('host writes: a lost reply sent again with the same operation is "Already recorded." and the revision stays', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.revAfterAgain === 1, r.again);
    ok('host writes: a save the browser refuses is not remembered as landed, so the same operation is stored when it is sent again',
      !r.unsaved.ok && r.unsaved.why === 'unsaved' && r.retry.ok && r.retry.changed && r.retry.rev === 2, [r.unsaved, r.retry]);
    ok('host writes: the operation that landed is "Already recorded."; another operation for a revision already stored is stale, and the reply names the stored revision',
      r.again2.ok && !r.again2.changed && !r.stale.ok && r.stale.why === 'stale' && r.stale.rev === 2, [r.again2, r.stale]);
    ok('host writes: a gap in revisions is stored (it only has to be newer), and the block shows the stored revision and operation', r.skip.ok && r.skip.rev === 5 && r.block.rev === 5 && r.ops === 'world:0:5', [r.skip, r.block && r.block.rev, r.ops]);
    r = await p.evaluate(async () => {
      window.confirm = () => true; window.toast = () => { };
      const backup = JSON.parse(backupJson()), id = petAlbumCatId(state.pet);
      await applyBackup(backup);
      const b2 = petWorldBlock(Date.now()), rev = b2.stored.rev, epoch = b2.epoch;
      const o = { epoch, rev, old: await command('NativePet', 'world', world(blobFor(), { epoch: epoch - 1, rev: rev + 1 })) };
      o.sameRev = await command('NativePet', 'world', world(blobFor(), { epoch, rev }));
      o.next = await command('NativePet', 'world', world(blobFor(), { epoch, rev: rev + 1 }));
      return o;
    });
    ok('after a restore the block carries the backed-up world under the new epoch; native writes on from its revision', r.epoch === 1 && r.rev === 5 && !r.old.ok && r.old.why === 'stale' && !r.sameRev.ok && r.next.ok && r.next.rev === 6 && r.next.epoch === 1, r);
    await p.context().close();

    /* 7. who runs the cats of a view: the web room loop never runs under a view the native brain owns */
    p = await start(b);
    r = await p.evaluate(async () => {
      const o = { before: NativeCatWorld.owner('pet') };
      navigate('pet'); await until(() => showing('NativePet'), 6000);
      o.pet = NativeCatWorld.owner('pet'); o.petRun = petWantRun(); o.dash = NativeCatWorld.owner('dashboard'); o.other = NativeCatWorld.owner('plan');
      return o;
    });
    ok('owner: the web brain until the Cat screen shows with pet.world.v1, then the native one, and the web room loop stays off', r.before === 'web' && r.pet === 'native' && r.petRun === false && r.dash === 'web' && r.other === 'web', r);
    await p.context().close();
    p = await start(b, { petCaps: PET_CAPS });
    r = await p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return { pet: NativeCatWorld.owner('pet') } });
    ok('owner: a Cat screen without pet.world.v1 leaves the cats to the web brain', r.pet === 'web', r);
    await p.context().close();
    p = await start(b, { cards: true });
    r = await p.evaluate(async () => { const o = {}; navigate('dashboard'); await until(() => showing('NativeOverviewCards'), 6000);
      o.cards = NativeCatWorld.owner('dashboard'); o.peek = petWantRun(); return o });
    ok('owner: the Overview cards own the dashboard cats while they show, and the web peek loop stays off', r.cards === 'native' && r.peek === false, r);
    await p.context().close();

    /* the goldens the native decoders read */
    const text = JSON.stringify(out, null, 1) + '\n';
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, text); console.log('wrote ' + GOLDEN) }
    else ok('the blocks and imports equal tests/fixtures/cat-world-golden.json (RECORD=1 rewrites it)', fs.existsSync(GOLDEN) && fs.readFileSync(GOLDEN, 'utf8') === text);
  } finally { await b.close() }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
