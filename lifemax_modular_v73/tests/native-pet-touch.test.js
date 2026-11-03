/* Native Cat tab, touch (pet.touch.v1), web half (docs/specs/CAT_TAB_PLAN.md, CT2). The screen's cats answer a finger by themselves;
   the page stays the only writer and applies its own petting rules to what the screen reports:
   - the projection: the touch fields exist only for a screen that said pet.touch.v1 (an older screen refuses unknown keys), carry the
     page's numbers (trust level, the trait's share of saturation, trust, saturation worn off by the clock), the day's counts, the
     owner's haptics and the page's words, and still write nothing;
   - the `stroke` command through the real petPetDone: a tap counts nothing, a hold of 1.1 s or more is a pet (3 counted a day, +2 bond),
     a cat that left (had enough, or shy) is not counted, a repeat of the same operation is answered and counted once, a level-up
     comes back with the page's words, refusals (no operation, an unknown cat, bad fields) change nothing, a save the browser refuses
     is taken back (the count, the bond and the brain), and the same operation counts once it can be stored;
   - the brain's saturation after a landing and a hold, from the page's PET_BRAIN numbers, and its events (overstimulation, spam);
   - the second cat of a house of two is petted on its own;
   - the `purr` command: the page's loop is fed while the screen covers the room, and stops with the screen;
   - the full-screen room is the screen's own with the capability (the page's stays shut), and the page's with an older screen.
   A fake NativePet plugin stands in for Android. Run: NODE_PATH=$(npm root -g) node tests/native-pet-touch.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');

const FAKE = (caps) => `
  window.__calls = []; const ls = {};
  const plugin = { __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))), addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: window.__caps }), open: async a => { window.__calls.push(['open', a]) },
    update: async a => { window.__calls.push(['update', a]) }, close: async () => { window.__calls.push(['close']); (ls.closed || []).forEach(f => f({})) } };
  window.__caps = ${JSON.stringify(caps)};
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativePet', getPlatform: () => 'android', registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativePet: plugin } };`;
const CAT = (name, o = '{}') => `Object.assign({v:1,name:${JSON.stringify(name)},coat:'ginger',trait:'curious',born:now-40*864e5,lastFed:now-5*36e5,meals:[],day:getToday(),fedToday:0,petsToday:0,playsToday:0,bondPts:PET_BOND_AT[3],care:0,vacation:{on:false,since:0},gifts:0,lastGiftDay:'',alive:true,passed:0,seen:{},greeted:0},${o})`;
const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.count = k => __calls.filter(c => c[0] === k).length;
  window.showing = () => { const o = __calls.map(c => c[0]).lastIndexOf('open'), c = __calls.map(c => c[0]).lastIndexOf('close'); return o >= 0 && o > c };
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  window.latest = () => { const c = [...__calls].reverse().find(c => c[0] === 'open' || c[0] === 'update'); return c && c[1].snapshot };
  window.command = async (command, payload) => { const n = __calls.length, id = 'r-' + n; await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
    await until(() => __calls.slice(n).some(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id), 3000);
    const u = __calls.slice(n).find(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); return u ? u[1].reply : null };
  window.action = async a => { await Capacitor.Plugins.NativePet.__emit('action', a); await wait(150) };
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(250) };
  window.stroke = (o) => command('touch', Object.assign({ kind: 'stroke', op: 'op-' + Math.random(), cat: 0, heldMs: 1500, left: '', ignored: false, spam: false, keepSleep: false }, o));
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;
const CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1'];

async function start(b, { caps = CAPS, pet = CAT('Miso'), pet2 = '' } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify({ nativeOverview: false, nativeOverviewCards: false, apps: [], nativePet: true })]);
  await p.addInitScript(FAKE(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async ([pet, pet2]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } await setCats('state.pet=' + pet + (pet2 ? ';state.pet2=' + pet2 : '')); await petInit() }, [pet, pet2]);
  await p.evaluate(async () => { navigate('pet'); await until(() => showing(), 5000) });
  return p;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the projection */
    let p = await start(b, { caps: ['pet.projection.v1', 'pet.coat.v1'] });
    let r = await p.evaluate(() => { const s = NativePetProjection.snapshot(); return { keys: ['touch', 'counts', 'haptics', 'touchWords'].filter(k => k in s) } });
    ok('an older screen (no pet.touch.v1) is sent none of the touch fields', r.keys.length === 0, r);
    await p.context().close();

    p = await start(b, { pet: CAT('Miso', "{trait:'shy',petsToday:2,playsToday:1,brain:null}") });
    r = await p.evaluate(async () => {
      const s = NativePetProjection.snapshot(), before = JSON.stringify(state), writes = [];
      const set = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { writes.push(k); return set.apply(this, arguments) };
      NativePetProjection.snapshot(); Storage.prototype.setItem = set;
      return { s, same: JSON.stringify(state) === before, writes: writes.length } });
    const t = r.s.touch && r.s.touch[0];
    ok('the touch fields carry the page\'s numbers: trust level, the shy trait\'s 1.3 share, trust and saturation', t && t.level === 3 && t.satMul === 1.3 && t.trust > 0 && t.trust <= 1 && t.sat === 0, r.s.touch);
    ok('counts and words: pets 2 of 3, plays 1 of 2, the line the card shows, the close chip', JSON.stringify(r.s.counts) === '{"pets":2,"petsMax":3,"plays":1,"playsMax":2}' && r.s.touchWords.countsLine === 'Petted 2 of 3 · Played 1 of 2 today' && r.s.touchWords.closeRoom === 'Close room', r.s);
    ok('the projection with touch writes nothing and changes nothing (no brain is made for it)', r.same && r.writes === 0, r);
    r = await p.evaluate(() => { const on = NativePetProjection.snapshot().haptics; const was = state.fx; state.fx = { haptics: false }; const off = NativePetProjection.snapshot().haptics; state.fx = was; return { on, off, hapMax: HAP_MAX } });
    ok('haptics: the owner\'s strength when on (the same scale the Overview cards use), null when off', r.on && r.on.amp > 0 && r.on.max === r.hapMax && r.off === null, r);
    await p.context().close();

    /* 2. a tap and a pet, the day's three, the bond */
    p = await start(b);
    r = await p.evaluate(async () => {
      const out = {}; const s0 = state.pet.bondPts;
      out.tap = await stroke({ op: 'tap-1', heldMs: 200 }); out.afterTap = { pets: state.pet.petsToday, bond: state.pet.bondPts - s0 };
      out.sat = state.pet.brain.af.s;
      out.pet = await stroke({ op: 'pet-1', heldMs: 1500 }); out.afterPet = { pets: state.pet.petsToday, bond: state.pet.bondPts - s0, saved: JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1')).pet.petsToday };
      out.again = await stroke({ op: 'pet-1', heldMs: 1500 }); out.afterAgain = state.pet.petsToday;
      await stroke({ op: 'pet-2', heldMs: 2000 }); await stroke({ op: 'pet-3', heldMs: 2000 });
      out.fourth = await stroke({ op: 'pet-4', heldMs: 2000 }); out.cap = { pets: state.pet.petsToday, bond: state.pet.bondPts - s0 };
      out.counts = latest().counts; return out });
    ok('a tap counts nothing: ok, not changed, no pet, no bond', r.tap.ok && !r.tap.changed && r.afterTap.pets === 0 && r.afterTap.bond === 0 && r.tap.levelUp === 0, r);
    ok('a landing raises the saturation by the 0.12 tap share of the curious cat (0.12 + the hold of 0.2 s)', Math.abs(r.sat - (0.12 + 0.09 * 0.2 * (1.25 - 0.5 * 0.5))) < 0.2 && r.sat > 0.1, r.sat);
    ok('a hold of 1.5 s is a pet: counted, +2 bond, saved', r.pet.ok && r.pet.changed && r.afterPet.pets === 1 && r.afterPet.bond === 2 && r.afterPet.saved === 1, r);
    ok('the same operation again is answered "Already recorded." and counts once', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.afterAgain === 1, r.again);
    ok('three a day: the fourth pet is ok, not changed, and adds no bond', r.fourth.ok && !r.fourth.changed && r.cap.pets === 3 && r.cap.bond === 6, r);
    ok('the projection\'s counts follow: 3 of 3', r.counts.pets === 3, r.counts);
    await p.context().close();

    /* 3. a cat that left is not a pet; refusals */
    p = await start(b);
    r = await p.evaluate(async () => {
      const out = {}; const s0 = state.pet.bondPts;
      out.over = await stroke({ op: 'o-1', heldMs: 2500, left: 'over', ignored: true }); out.shy = await stroke({ op: 'o-2', heldMs: 2700, left: 'shy' });
      out.pets = state.pet.petsToday; out.bond = state.pet.bondPts - s0;
      out.noOp = await stroke({ op: '' }); out.badCat = await stroke({ op: 'x', cat: 1 }); out.badLeft = await stroke({ op: 'x2', left: 'gone' }); out.neg = await stroke({ op: 'x3', heldMs: -5 });
      out.str = await stroke({ op: 'x4', heldMs: '5' }); out.flag = await stroke({ op: 'x5', spam: 'yes' }); out.kind = await command('touch', { kind: 'tickle', cat: 0 }); out.noKind = await command('touch', {});
      out.after = { pets: state.pet.petsToday, bond: state.pet.bondPts - s0 }; return out });
    ok('a cat that had enough or got up shy is not a pet: ok, nothing counted, no bond', r.over.ok && !r.over.changed && r.shy.ok && !r.shy.changed && r.pets === 0 && r.bond === 0, r);
    ok('refused, nothing changed: no operation, an unknown cat, bad left, negative or textual time, a non-boolean, an unknown kind, no kind',
      [r.noOp, r.badCat, r.badLeft, r.neg, r.str, r.flag, r.kind, r.noKind].every(x => x && !x.ok && /Nothing was changed|no cat/i.test(x.message)) && r.after.pets === 0 && r.after.bond === 0, r);
    await p.context().close();

    /* 4. a level-up, a save the browser refuses */
    p = await start(b, { pet: CAT('Miso', '{bondPts:PET_BOND_AT[3]-1}') });
    r = await p.evaluate(async () => {
      const out = {}; const lv0 = petLevel(state.pet);
      const real = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
      const before = JSON.stringify([state.pet.petsToday, state.pet.bondPts, state.pet.brain && state.pet.brain.af.s]);
      try { out.refused = await stroke({ op: 'q-1', heldMs: 1500 }) } finally { Storage.prototype.setItem = real }
      out.same = before === JSON.stringify([state.pet.petsToday, state.pet.bondPts, state.pet.brain && state.pet.brain.af.s]);
      out.retry = await stroke({ op: 'q-1', heldMs: 1500 }); out.lv = [lv0, petLevel(state.pet)]; out.pets = state.pet.petsToday; return out });
    ok('a save the browser refuses is taken back: not ok, the count, the bond and the brain as they were', !r.refused.ok && !r.refused.changed && /Could not save/.test(r.refused.message) && r.same, r);
    ok('the same operation then counts once, and the level it won comes back with the page\'s words', r.retry.ok && r.retry.changed && r.pets === 1 && r.lv[1] === r.lv[0] + 1 && r.retry.levelUp === r.lv[1] && /Miso trusts you more now/.test(r.retry.message), r);
    ok('the level-up leaves the page quiet: its words are the reply\'s, not also the screen\'s notice', await p.evaluate(() => latest().notice === null));
    await p.context().close();

    /* 5. the saturation and the brain's events */
    p = await start(b);
    r = await p.evaluate(async () => {
      const out = {}; const T = PET_TRAIT_BASE.curious; await stroke({ op: 'a', heldMs: 0 });
      const landing = state.pet.brain.af.s; out.landing = [landing, 0.12 * T.sat];
      const tru = state.pet.brain.at.tru; await stroke({ op: 'b', heldMs: 4000 });
      out.hold = [state.pet.brain.af.s - landing, 0.12 * T.sat + 0.09 * 4 * T.sat * (1.25 - 0.5 * tru)];
      const seen = []; const be = window.petBrainEvent; window.petBrainEvent = function (k, o) { seen.push(k + (o && o.ignored ? ':ignored' : '')); return be.apply(this, arguments) };
      await stroke({ op: 'c', heldMs: 3000, left: 'over', ignored: true }); await stroke({ op: 'd', heldMs: 100, spam: true }); await stroke({ op: 'e', heldMs: 1000 }); window.petBrainEvent = be;
      out.seen = seen; out.kept = state.pet.brain.af.s <= 1.2; return out });
    ok('a landing adds SAT_TAP of the trait', Math.abs(r.landing[0] - r.landing[1]) < 0.002, r.landing);
    ok('a 4 s hold adds a landing and the hold\'s share (0.09 a second, less for a trusting cat)', Math.abs(r.hold[0] - r.hold[1]) < 0.01, r.hold);
    ok('the brain hears what happened: overstimulation (ignored), spam, and pet beats for a stroke that was gentle', r.seen.includes('overstim:ignored') && r.seen.includes('spam') && r.kept, r.seen);
    await p.context().close();

    /* the second cat is petted on its own */
    p = await start(b, { pet: CAT('Ted'), pet2: CAT('Lily', "{coat:'gray',trait:'lazy'}") });
    r = await p.evaluate(async () => {
      const s = latest(); await stroke({ op: 'two-1', cat: 1, heldMs: 1500 });
      return { touch: s.touch.map(t => [t.level, t.satMul]), pets: [state.pet.petsToday, state.pet2.petsToday], bond: [state.pet.bondPts - PET_BOND_AT[3], state.pet2.bondPts - PET_BOND_AT[3]], bad: await stroke({ op: 'two-2', cat: 2 }) } });
    ok('two cats: each has its own touch profile (the lazy one takes 0.8), and a stroke on the second counts for the second only', JSON.stringify(r.touch) === '[[3,1],[3,0.8]]' && r.pets.join() === '0,1' && r.bond.join() === '0,2' && !r.bad.ok, r);
    await p.context().close();

    /* 6. purr: the page's loop is fed while the screen covers the room, and stops with the screen */
    p = await start(b);
    r = await p.evaluate(async () => {
      const out = {}; const calls = []; const pp = window.petPurr, pt = window.petPurrTick;
      window.petPurr = function (rt, v) { calls.push(['purr', v]); return pp.apply(this, arguments) }; window.petPurrTick = function () { calls.push(['tick']); return pt.apply(this, arguments) };
      out.on = await command('touch', { kind: 'purr', cat: 0, on: true, level: 0.75 }); await wait(450); const ticks = calls.filter(c => c[0] === 'tick').length;
      out.ticks = ticks; out.started = calls.some(c => c[0] === 'purr' && c[1] === 0.75);
      out.bad = await command('touch', { kind: 'purr', cat: 0, on: true, level: 2 }); out.bad2 = await command('touch', { kind: 'purr', cat: 0, on: 'yes', level: 1 });
      out.off = await command('touch', { kind: 'purr', cat: 0, on: false, level: 0 }); const n = calls.filter(c => c[0] === 'tick').length; await wait(400); out.stopped = calls.filter(c => c[0] === 'tick').length === n;
      await command('touch', { kind: 'purr', cat: 0, on: true, level: 0.5 }); await Capacitor.Plugins.NativePet.close(); await wait(100); const m = calls.filter(c => c[0] === 'tick').length; await wait(400);
      out.closedStops = !(PET_RT.purr && PET_RT.purr.on) && calls.filter(c => c[0] === 'purr').pop()[1] === 0 && m >= 0;
      window.petPurr = pp; window.petPurrTick = pt; return out });
    ok('purr on: the page starts its purr and feeds the loop every 100 ms while the room loop is stopped', r.on.ok && r.started && r.ticks >= 3, r);
    ok('purr: a level outside 0..1 or a non-boolean is refused; off stops the feeding; closing the screen stops a purr too', !r.bad.ok && !r.bad2.ok && r.off.ok && r.stopped && r.closedStops, r);
    await p.context().close();

    /* 7. the full-screen room */
    p = await start(b);
    /* the action closes and reopens the screen: under load the reopen can outlast a fixed pause, so wait for the screen itself (up to 5 s), then let the page settle */
    r = await p.evaluate(async () => { await action({ action: 'room', id: '' }); for (let i = 0; i < 50 && !showing(); i++) await wait(100); await wait(200); return { page: document.body.classList.contains('pet-full-open'), showing: showing() } });
    ok('View room with pet.touch.v1: the room is the screen\'s own, the page\'s full-screen room stays shut and the screen stays', !r.page && r.showing, r);
    await p.context().close();
    p = await start(b, { caps: ['pet.projection.v1', 'pet.coat.v1'] });
    r = await p.evaluate(async () => { await action({ action: 'room', id: '' }); await wait(300); return { page: document.body.classList.contains('pet-full-open'), showing: showing() } });
    ok('View room with an older screen: the page\'s full-screen room opens, as in CT1', r.page && !r.showing, r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } finally { await b.close() }
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})();
