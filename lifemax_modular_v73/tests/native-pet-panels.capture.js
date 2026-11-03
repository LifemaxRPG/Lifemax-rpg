/* The page's own Cat view put through the cat states of native-pet-panels.scenarios.js, and what its renderers write: the HTML of
   the mind row, the inner-life panel, the likes bars, the genetics panel, the Together card and the toy tray. The golden file
   (fixtures/pet-panels-golden.json) was captured from the renderers BEFORE they were split into data functions and HTML
   functions (CT6 part A), so a test comparing with it proves the DOM did not change by a byte. Also used to read what the DOM
   shows, as text, against the projection. */
const { CLOCK, SCENARIOS } = require('./native-pet-panels.scenarios');
const fs = require('fs'), path = require('path');
const GOLDEN = path.join(__dirname, 'fixtures', 'pet-panels-golden.json');

/* a minimal fake NativePet: only the handshake, so the page can answer which capabilities the screen has */
const FAKE = (caps) => `
  window.__calls = []; const ls = {};
  const plugin = { __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))), addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    handshake: async () => ({ protocol: 1, capabilities: window.__caps }), open: async a => { window.__calls.push(['open', a]) },
    update: async a => { window.__calls.push(['update', a]) }, close: async () => { window.__calls.push(['close']); (ls.closed || []).forEach(f => f({})) } };
  window.__caps = ${JSON.stringify(caps)};
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativePet', getPlatform: () => 'android', registerPlugin: () => ({}), convertFileSrc: u => u, Plugins: { NativePet: plugin } };`;

const HELPERS = `
  window.wait = ms => new Promise(r => setTimeout(r, ms));
  window.count = k => __calls.filter(c => c[0] === k).length;
  window.showing = () => { const o = __calls.map(c => c[0]).lastIndexOf('open'), c = __calls.map(c => c[0]).lastIndexOf('close'); return o >= 0 && o > c };
  window.until = async (f, ms = 5000) => { for (let t = 0; t < ms && !f(); t += 25) await wait(25); return !!f() };
  window.latest = () => { const c = [...__calls].reverse().find(c => c[0] === 'open' || c[0] === 'update'); return c && c[1].snapshot };
  window.command = async (command, payload) => { const n = __calls.length, id = 'r-' + n; await Capacitor.Plugins.NativePet.__emit('command', { command, payload, requestId: id });
    await until(() => __calls.slice(n).some(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id), 3000);
    const u = __calls.slice(n).find(c => c[0] === 'update' && c[1].reply && c[1].reply.requestId === id); return u ? u[1].reply : null };
  window.setCats = js => { (0, eval)('(function(){const now=Date.now();' + js + '})()'); save(); renderPetView(true); return wait(250) };
  NativeOverviewSnapshot.snapshot = async () => ({ width: 320, header: { nodes: [], hits: [], height: 64, width: 320 }, nav: { nodes: [], hits: [], height: 64, width: 320 } });`;

/** a page at the fixed clock on the Cat view, with the screen's plugin faked (capabilities `caps`); the scenario is not set yet */
async function start(b, file, { caps = ['pet.projection.v1', 'pet.coat.v1'], native = true } = {}) {
  const golden = require('./fixtures/saves/established.json');
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.setFixedTime(new Date(CLOCK));
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true } })), golden.extras || {},
    JSON.stringify({ nativeOverview: false, nativeOverviewCards: false, apps: [], nativePet: native })]);
  await p.addInitScript(FAKE(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(HELPERS);
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } });
  return p;
}

/** sets the scenario up in the page and returns the HTML each renderer wrote for the cat the Cat view shows (and the focused second cat) */
async function capture(p, js, { focus = false } = {}) {
  return p.evaluate(async ([js, focus]) => {
    await setCats(js);
    const rt = await petInit(); if (state.pet2) { petPairEnsure(rt); await until(() => petPairOn(), 5000) }
    const hash = s => { let h = 5381; for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0; return (h >>> 0).toString(36) };
    const read = () => {
      const br = petBR(PET_RT); br.awayFresh = Date.now();
      petRenderStatus(true); petToyTrayRender();
      const html = id => { const e = document.getElementById(id); return e ? e.innerHTML.replace(/src="data:[^"]*"/g, m => 'src="data:#' + hash(m) + '"') : null };
      return { mind: html('petMind'), inner: html('petInner'), likes: html('petLikes'), dna: html('petDna'), pair: html('petPair'), toys: html('petToyGrid'), favs: html('petFavs') };
    };
    const out = { first: read() };
    if (focus && petPairOn()) { petPairFocus(1); await wait(200); out.second = read(); petPairFocus(0); await wait(100) }
    return out;
  }, [js, focus]);
}

module.exports = { start, capture, FAKE, HELPERS, GOLDEN, SCENARIOS, CLOCK,
  readGolden: () => JSON.parse(fs.readFileSync(GOLDEN, 'utf8')) };

/* `node tests/native-pet-panels.capture.js <page>` writes the golden file from the page given: run it against a build of the code
   before the refactor, never against the current one */
if (require.main === module) {
  const { chromium } = require('playwright');
  const file = require('./current-page')(process.argv[2]);
  (async () => {
    const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
    const out = {};
    try {
      for (const [name, js] of Object.entries(SCENARIOS)) {
        const p = await start(b, file);
        out[name] = await capture(p, js, { focus: name === 'twoCats' });
        if (p.errors.length) console.log(name, 'page errors', p.errors);
        await p.context().close();
        console.log('captured', name);
      }
    } finally { await b.close() }
    fs.writeFileSync(GOLDEN, JSON.stringify(out, null, 1) + '\n');
  })();
}
