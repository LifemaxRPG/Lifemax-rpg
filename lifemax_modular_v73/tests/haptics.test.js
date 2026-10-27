/* Haptic strength tests. Run:  NODE_PATH=$(npm root -g) node tests/haptics.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("window.__v=[];Object.defineProperty(navigator,'vibrate',{value:function(x){window.__v.push(x);return true},configurable:true});");
  await p.goto('file://' + file); await p.waitForTimeout(800);
  await p.evaluate(() => { const o = document.getElementById('onboarding'); if (o) { o.classList.remove('show'); o.style.display = 'none' } });
  const buzz = async (level, pat) => p.evaluate(([l, pat]) => { state.fx = Object.assign({}, fxOpt(), { haptics: true, hapticLevel: l }); window.__v = []; haptic(pat); return window.__v }, [level, pat]);
  const one = async l => (await buzz(l, 10))[0];
  const L = await one('light'), N = await one('normal'), S = await one('strong'), M = await one('max');
  ok('each step is stronger than the one before (browser: longer buzzes)', L < N && N < S && S < M && N === 10, [L, N, S, M]);
  const pat = await buzz('strong', [18, 30, 18]);
  ok('a pattern: the buzzes grow, the pauses stay', pat[0].length === 3 && pat[0][0] > 18 && pat[0][1] === 30 && pat[0][2] > 18, pat);
  ok('the longest buzz is capped', (await one('max')) <= 260 && (await buzz('max', [400]))[0][0] <= 260);
  await p.evaluate(() => { state.fx = Object.assign({}, fxOpt(), { haptics: false }); window.__v = []; haptic(10) });
  ok('off means no buzz at all', (await p.evaluate(() => window.__v)).length === 0);
  // native motor
  await p.evaluate(() => {
    window.__n = []; LifeMaxHaptics.reset(); window.lmaNative = () => true;
    const orig = window.lmaPlugin; window.lmaPlugin = n => n === 'NavHaptics' ? { pattern: o => { window.__n.push(['pattern', o.timings, o.amp]); return Promise.resolve() }, pulse: o => { window.__n.push(['pulse', o]); return Promise.resolve() }, tick: () => { window.__n.push(['tick']); return Promise.resolve() }, start: () => Promise.resolve(), end: () => Promise.resolve() } : orig(n);
  });
  const nat = async l => p.evaluate(async l => { state.fx = Object.assign({}, fxOpt(), { haptics: true, hapticLevel: l }); window.__n = []; window.__v = []; haptic([12]); await new Promise(r => setTimeout(r, 30)); return { n: window.__n, v: window.__v } }, l);
  const amps = []; for (const l of ['light', 'normal', 'strong', 'max']) { const r = await nat(l); amps.push(r.n[0] && r.n[0][2]); if (l === 'strong') ok('native: goes to the motor, not the browser', r.n.length === 1 && r.n[0][0] === 'pattern' && r.v.length === 0, r) }
  ok('native: amplitude rises with every step, Max is full', amps[0] < amps[1] && amps[1] < amps[2] && amps[2] < amps[3] && amps[3] === 255, amps);
  // an older app build without the new plugin methods falls back to the browser buzz
  await p.evaluate(() => { LifeMaxHaptics.reset(); const orig = window.lmaPlugin; window.lmaPlugin = n => n === 'NavHaptics' ? { pattern: () => Promise.reject(new Error('not implemented')), pulse: () => Promise.reject(new Error('x')) } : orig(n); window.__v = []; state.fx = Object.assign({}, fxOpt(), { haptics: true, hapticLevel: 'strong' }); haptic([12]) });
  await p.waitForTimeout(80);
  ok('an older app build falls back to the longer browser buzz', (await p.evaluate(() => window.__v)).length >= 1 && (await p.evaluate(() => LifeMaxHaptics.native())) === false);
  // settings card
  await p.evaluate(() => { LifeMaxHaptics.reset(); navigate('settings') }); await p.waitForTimeout(500);
  const ui = await p.evaluate(() => ({ title: !!document.getElementById('hapCard'), seg: [...document.querySelectorAll('#hapBody [aria-label="Haptic strength"] button')].map(x => x.textContent), tries: [...document.querySelectorAll('#hapBody button')].filter(x => /^Try:/.test(x.textContent)).length }));
  ok('Settings has a Haptics card with four strengths and test buttons', ui.title && ui.seg.join() === 'Light,Normal,Strong,Max' && ui.tries === 4, ui);
  await p.evaluate(() => { window.__v = []; [...document.querySelectorAll('#hapBody [aria-label="Haptic strength"] button')].find(x => x.textContent === 'Max').click() });
  ok('picking Max saves it and gives a sample buzz', (await p.evaluate(() => state.fx.hapticLevel)) === 'max' && (await p.evaluate(() => window.__v.length)) >= 1);
  ok('the chosen strength is shown as selected', await p.evaluate(() => [...document.querySelectorAll('#hapBody [aria-label="Haptic strength"] button')].find(x => x.textContent === 'Max').getAttribute('aria-pressed') === 'true'));
  await p.evaluate(() => { const c = document.querySelector('#hapBody input[type=checkbox]'); c.checked = false; c.dispatchEvent(new Event('change', { bubbles: true })) });
  ok('the switch turns it off and greys out the strengths', (await p.evaluate(() => state.fx.haptics)) === false && await p.evaluate(() => [...document.querySelectorAll('#hapBody [aria-label="Haptic strength"] button')].every(x => x.disabled)));
  ok('the choice survives a reload', await (async () => { await p.reload(); await p.waitForTimeout(800); return p.evaluate(() => fxOpt().hapticLevel === 'max' && fxOpt().haptics === false) })());
  const mani = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'patch_manifest.py'), 'utf8'), pl = fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'NavHapticsPlugin.java'), 'utf8');
  ok('the app asks for the Vibrate permission', /android\.permission\.VIBRATE/.test(mani));
  ok('the native plugin has pulse and pattern with an amplitude', /public void pulse/.test(pl) && /public void pattern/.test(pl) && /createWaveform/.test(pl));
  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
