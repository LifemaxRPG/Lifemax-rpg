/* Native Play (pet.play.v1), web half (docs/specs/CAT_BRAIN_PLAN.md CB4 part B, first slice), through the real page:
   - gating: a Cat screen without pet.play.v1 gets exactly what it got (no tray; Play hands the visit to the page's own tray, as
     before); with it the projection carries the tray and Play never hands the visit over;
   - the tray is the page's: only the toys petToyUnlocked allows (unlocked, or bought early), with petToysData's words, "Let <name>
     pick", and petPlayClick's refusal for a cat too hungry to play;
   - the `play` command {op, cat, toy, played}: refused for a screen without the capability, every malformed shape refused; a game
     played to its end is the page's own petPlayDone (two a day, the bond) and petLikesPlayed (the toy's liking); a sniff credits
     the liking only; ok only once stored, a refused save taken back; a repeat answered "Already recorded." even after a reload
     (the op is kept with the save); the second cat's game is the second cat's; the Overview cards take the same command.
   Fake NativePet and NativeOverviewCards plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-play.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');
const PLAY = 'pet.play.v1';
const PET_CAPS = ['pet.projection.v1', 'pet.coat.v1', 'pet.touch.v1', 'pet.world.v1'];

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
  window.game = (o) => Object.assign({ op: 'c:play:' + Math.random().toString(36).slice(2), cat: petAlbumCatId(state.pet), toy: 'mouse', played: true }, o);
  window.stored = () => JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1'));
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { petCaps = [...PET_CAPS, PLAY], cardCaps = ['overview.cards.v1', 'pet.world.v1'], cards = false, pet = CAT('Miso'), pet2 = '' } = {}) {
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
  await p.evaluate(async ([pet, pet2]) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } if (pet) await setCats('state.pet=' + pet + (pet2 ? ';state.pet2=' + pet2 : '')); await petInit() }, [pet, pet2]);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return showing('NativePet') });

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. gating: an older screen is as it was; Play still hands over to the page's tray */
    let p = await start(b, { petCaps: PET_CAPS });
    await toPet(p);
    let r = await p.evaluate(async () => {
      const keys = Object.keys(latest('NativePet')), refused = await command('NativePet', 'play', game());
      let opened = 0; const orig = window.petPlayClick; window.petPlayClick = function () { opened++; return orig.apply(this, arguments) };
      await Capacitor.Plugins.NativePet.__emit('action', { action: 'play', id: '' }); await wait(200);
      return { keys, refused, opened, gone: !showing('NativePet'), plays: state.pet.playsToday };
    });
    ok('a Cat screen without pet.play.v1: no tray in its projection', !r.keys.includes('play'), r.keys);
    ok('a Cat screen without pet.play.v1: the play command is refused and nothing counted', r.refused && !r.refused.ok && r.refused.why === 'shape' && r.plays === 0, r);
    ok('a Cat screen without pet.play.v1: Play still opens the page\'s own tray', r.opened === 1 && r.gone, r);
    await p.context().close();

    /* 2. the tray is the page's, and Play stays on the screen */
    p = await start(b, { pet: CAT('Miso', '{born:now-864e5,bondPts:0}') });
    ok('a Cat screen with pet.play.v1 opens', await toPet(p));
    r = await p.evaluate(async () => {
      const t = latest('NativePet').play, want = petToysData(state.pet).filter(x => x.unlocked).map(x => ({ id: x.id, name: x.name, word: x.word }));
      let opened = 0; const orig = window.petPlayClick; window.petPlayClick = function () { opened++; return orig.apply(this, arguments) };
      await Capacitor.Plugins.NativePet.__emit('action', { action: 'play', id: '' }); await until(() => showing('NativePet'), 3000);
      return { t, want, locked: Object.keys(PET_TOYS).filter(id => !petToyUnlocked(state.pet, id)), opened, still: showing('NativePet') };
    });
    ok('the tray holds only the toys the cat may play with, in the page\'s words', JSON.stringify(r.t.toys) === JSON.stringify(r.want) && r.locked.length > 0 && r.t.toys.every(t => !r.locked.includes(t.id)), r);
    ok('the tray\'s words: "Let Miso pick", no refusal for a fed cat', r.t.pick === 'Let Miso pick' && r.t.tired === '' && /Miso/.test(r.t.title), r.t);
    ok('Play with pet.play.v1 never hands the visit to the page', r.opened === 0 && r.still, r);
    r = await p.evaluate(async () => { state.toysBought = { wand: 1 }; save(); renderPetView(true); await wait(300); return latest('NativePet').play.toys.map(t => t.id) });
    ok('a toy bought early in the Rewards shop is in the tray', r.includes('wand'), r);
    r = await p.evaluate(async () => { await setCats('state.pet.lastFed=now-30*36e5'); await wait(200); return latest('NativePet').play.tired });
    ok('a cat too hungry to play: the page\'s own refusal', r === 'Miso is too tired to play. A meal first.', r);
    await p.context().close();

    /* 3. the play command */
    p = await start(b);
    await toPet(p);
    const bad = [
      ['a key missing', 'delete g.played'], ['a key more', 'g.extra = 1'], ['an unknown toy', 'g.toy = "laser"'], ['played as a word', 'g.played = "yes"'],
      ['an op with spaces', 'g.op = "a b"'], ['no op', 'g.op = ""'], ['a cat as a number', 'g.cat = 0'],
    ];
    r = await p.evaluate(async (bad) => {
      const out = {};
      for (const [name, js] of bad) { const g = game(); (0, eval)('(function(g){' + js + '})')(g); const a = await command('NativePet', 'play', g); out[name] = a && !a.ok && a.why === 'shape' }
      out.stranger = await command('NativePet', 'play', game({ cat: 'cnobody0' }));
      out.plays = state.pet.playsToday;
      return out;
    }, bad);
    for (const [name] of bad) ok('a malformed game is refused: ' + name, r[name], r);
    ok('a game of a cat that is not here is refused', r.stranger && !r.stranger.ok && r.stranger.why === 'refused', r.stranger);
    ok('nothing malformed counted', r.plays === 0, r.plays);
    r = await p.evaluate(async () => {
      const g = game(), likesBefore = (petLikes(state.pet).plays.mouse || 0), bond = state.pet.bondPts;
      const a = await command('NativePet', 'play', g), s = stored();
      const again = await command('NativePet', 'play', g);
      return { a, again, plays: state.pet.playsToday, likes: petLikes(state.pet).plays.mouse - likesBefore, bondUp: state.pet.bondPts > bond, savedPlays: s.pet.playsToday, ops: s.petPlayOps, op: g.op };
    });
    ok('a game played to its end: the page counts it (petPlayDone) and credits the toy (petLikesPlayed)', r.a.ok && r.a.changed && r.plays === 1 && r.likes === 1 && r.bondUp, r);
    ok('the game is in the save, with its op', r.savedPlays === 1 && r.ops.includes(r.op), r);
    ok('the same game again: "Already recorded." and no second count', r.again.ok && !r.again.changed && r.again.message === 'Already recorded.' && r.plays === 1, r.again);
    r = await p.evaluate(async () => {
      const sniff = await command('NativePet', 'play', game({ toy: 'yarn', played: false }));
      const second = await command('NativePet', 'play', game()), third = await command('NativePet', 'play', game());
      return { sniff, second, third, plays: state.pet.playsToday, yarn: petLikes(state.pet).plays.yarn || 0 };
    });
    ok('a sniff credits the toy\'s liking only', r.sniff.ok && r.yarn === 1, r);
    ok('two games a day count (the page\'s rule); a third is stored, not counted', r.second.ok && r.third.ok && r.plays === 2, r);
    r = await p.evaluate(async () => {
      const g = game({ toy: 'feather' }), before = JSON.stringify([state.pet, state.petPlayOps]);
      const write = writeSave; writeSave = () => false; let a; try { a = await command('NativePet', 'play', g) } finally { writeSave = write }
      return { a, same: JSON.stringify([state.pet, state.petPlayOps]) === before, opKept: (state.petPlayOps || []).includes(g.op) };
    });
    ok('a game the browser could not store: not ok, taken back (no count, no op kept)', r.a && !r.a.ok && r.a.why === 'unsaved' && r.same && !r.opKept, r);
    const op = await p.evaluate(async () => { const g = game({ op: 'c:play:kept' }); await command('NativePet', 'play', g); return g.op });
    await p.reload(); await p.evaluate(HELPERS);
    r = await p.evaluate(async (op) => { lmaThumbWarm = async () => { }; await petInit(); navigate('pet'); await until(() => showing('NativePet'), 6000);
      const plays = state.pet.playsToday; const a = await command('NativePet', 'play', game({ op })); return { a, same: state.pet.playsToday === plays } }, op);
    ok('a game replayed after a reload is answered "Already recorded." (the op is kept with the save)', r.a && r.a.ok && r.a.message === 'Already recorded.' && r.same, r);
    await p.context().close();

    /* 4. two cats: the second cat's game is the second cat's; the Overview cards take it too */
    p = await start(b, { cards: true, pet: CAT('Ted'), pet2: CAT('Lily', "{coat:'gray',born:now-20*864e5}") });
    await toPet(p);
    r = await p.evaluate(async () => {
      const a = await command('NativePet', 'play', game({ cat: petAlbumCatId(state.pet2) }));
      navigate('dashboard'); await until(() => showing('NativeOverviewCards'), 6000);
      const c = await command('NativeOverviewCards', 'play', game());
      return { a, c, ted: state.pet.playsToday, lily: state.pet2.playsToday };
    });
    ok('the second cat\'s game counts for the second cat', r.a && r.a.ok && r.lily === 1, r);
    ok('the Overview cards take a game the world reports while they show', r.c && r.c.ok && r.ted === 1, r);
    ok('no page errors', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
