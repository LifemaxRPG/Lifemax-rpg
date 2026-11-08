/* Developer parkour demo (pet.demo.v1), web half, through the real page:
   - gating: the projection carries `demo` {label, hint} only for a Cat screen that advertised pet.demo.v1 AND only while the
     page's developer mode (state.devMode) is on; an older screen (no capability) or developer mode off gets exactly what it got;
   - toggling developer mode in Settings (toggleDevMode) re-projects the Cat tab at once, both ways;
   - the demo has no data effects on the page: no command is registered for it (the tap goes to the native brain only) and
     projecting it changes nothing in the cat's save.
   Fake NativePet and NativeOverviewCards plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-pet-demo.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = require('./fixtures/saves/established.json');
const DEMO = 'pet.demo.v1';
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
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(200) };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

async function start(b, { petCaps = [...PET_CAPS, DEMO], pet = CAT('Miso') } = {}) {
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.addInitScript(([save, extras, prefs]) => {
    if (!localStorage.getItem('lifemax_rpg_prototype_v1')) { localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]) }
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {}, JSON.stringify({ nativePet: true, nativeOverview: false, nativeOverviewCards: false, apps: [] })]);
  await p.addInitScript(FAKE);
  await p.addInitScript(([a, c]) => { window.__caps.NativePet = a; window.__caps.NativeOverviewCards = c }, [petCaps, ['overview.cards.v1', 'pet.world.v1']]);
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(async (pet) => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } await setCats('state.pet=' + pet); await petInit() }, pet);
  return p;
}
const toPet = (p) => p.evaluate(async () => { navigate('pet'); await until(() => showing('NativePet'), 6000); return showing('NativePet') });

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. developer mode off: no button, even for a screen with pet.demo.v1 */
    let p = await start(b);
    ok('a Cat screen with pet.demo.v1 opens', await toPet(p));
    let r = await p.evaluate(async () => ({ keys: Object.keys(latest('NativePet')), dev: !!state.devMode }));
    ok('developer mode off: the projection has no demo field', !r.keys.includes('demo') && !r.dev, r);
    ok('no demo command exists on the page (the tap goes to the native brain only)', await p.evaluate(() => !('demo' in NativePet.commands)));

    /* 2. developer mode on (as Settings toggles it): the button is projected at once, and goes again */
    r = await p.evaluate(async () => {
      toggleDevMode(true); await until(() => latest('NativePet').demo, 3000);
      const d = latest('NativePet').demo;
      return { d, keys: d ? Object.keys(d).sort().join() : '' };
    });
    ok('developer mode on: the projection carries the demo button in the page\'s words', r.d && r.d.label === 'Demo parkour' && /Developer demo/.test(r.d.hint) && r.keys === 'hint,label', r);
    r = await p.evaluate(async () => { toggleDevMode(false); await until(() => !latest('NativePet').demo, 3000); return { gone: !latest('NativePet').demo, dev: state.devMode } });
    ok('developer mode off again: the button goes at once', r.gone && r.dev === false, r);
    ok('no page errors (screen with the capability)', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 3. an older screen (no pet.demo.v1): never a demo field, even with developer mode on */
    p = await start(b, { petCaps: PET_CAPS });
    await toPet(p);
    r = await p.evaluate(async () => { toggleDevMode(true); await wait(400); return { keys: Object.keys(latest('NativePet')), dev: state.devMode } });
    ok('an older screen without pet.demo.v1: no demo field with developer mode on', r.dev === true && !r.keys.includes('demo'), r);
    await p.context().close();

    /* 4. developer mode saved on: shown on open; projecting the demo changes nothing in the cat's save */
    p = await start(b);
    r = await p.evaluate(async () => {
      state.devMode = true; save(); renderPetView(true); await wait(300);
      navigate('dashboard'); await wait(200); navigate('pet'); await until(() => showing('NativePet'), 6000);
      const mine = () => JSON.stringify([state.pet, state.pet2 || null, state.petPlayOps || null, state.petPair || null]);
      const before = mine();
      renderPetView(true); await wait(300);
      return { shown: !!latest('NativePet').demo, same: before === mine() };
    });
    ok('developer mode on at open: the button shows, and projecting it changes nothing in the cat\'s save', r.shown && r.same, r);
    ok('no page errors (saved developer mode)', p.errors.length === 0, p.errors);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL threw ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
