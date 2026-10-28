/* Gemini understanding for the voice engine, with fetch stubbed (no network).
   Run: NODE_PATH=$(npm root -g) node tests/gemini.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(2500);
  const gibberish = 'blorp the snarfle thing';
  ok('off by default: nothing is sent', await p.evaluate(g => { window.__f = []; window.fetch = () => { __f.push(1); return Promise.reject(new Error('x')) }; LifeMaxVoice.simulateUtterance(g); return __f.length === 0 }, gibberish));
  await p.evaluate(() => { localStorage.setItem('lifemax_gemini_key_v1', 'KEY123'); localStorage.setItem('lifemax_gemini_on_v1', 'true') });
  ok('utterance builder', await p.evaluate(() => LifeMaxGemini.utterance({ name: 'add_to_schedule', args: { title: 'Dentist', date: '2026-11-03', time: '15:00', duration_minutes: 45 } }) === 'Put Dentist on my schedule on November 3 at 3:00 PM for 45 minutes'));
  /* a sentence the offline engine understands never goes to Gemini */
  ok('understood offline: no call', await p.evaluate(() => { __f = []; LifeMaxVoice.simulateUtterance('how many steps did I walk yesterday'); return __f.length === 0 }));
  /* stub: returns an add_to_schedule call */
  await p.evaluate(() => { window.__req = null; window.fetch = (u, o) => { __req = { u, o }; return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ candidates: [{ content: { parts: [{ functionCall: { name: 'add_to_schedule', args: { title: 'Team meeting', date: (() => { const d = new Date(Date.now() + 864e5); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') })(), time: '15:00', duration_minutes: 60 } } }] } }] }) }) } });
  await p.evaluate(g => { LifeMaxVoice.simulateUtterance(g) }, gibberish);
  await p.waitForFunction(() => { const s = LifeMaxVoice.snapshot(); return s && s.intentId && s.intentId !== 'unknown_query' && s.title !== 'Asking Gemini…' }, null, { timeout: 8000 }).catch(() => { });
  const snap = await p.evaluate(() => LifeMaxVoice.snapshot());
  const req = await p.evaluate(() => ({ u: __req.u, k: __req.o.headers['x-goog-api-key'], b: JSON.parse(__req.o.body) }));
  ok('request goes to the Gemini API with the key in a header, not the URL', /generativelanguage\.googleapis\.com/.test(req.u) && req.k === 'KEY123' && req.u.indexOf('KEY123') < 0, req.u);
  ok('request sends only the sentence and tools', req.b.contents[0].parts[0].text === gibberish && req.b.tools[0].functionDeclarations.length === 3);
  ok('the call becomes a normal schedule preview (awaiting confirm)', snap && /PREVIEW|AWAIT/.test(snap.state || '') && /Team meeting/i.test(JSON.stringify(snap)), snap);
  ok('feed_cats call feeds', await p.evaluate(async () => { const pt = petAdopt('ginger', 'Mo', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = Date.now() - 6 * 36e5; fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ candidates: [{ content: { parts: [{ functionCall: { name: 'feed_cats', args: {} } }] } }] }) }); lmvReset && lmvReset(); LifeMaxVoice.simulateUtterance('zorp glimmer feed thingy'); await new Promise(r => setTimeout(r, 600)); return Date.now() - state.pet.lastFed < 5000 }));
  ok('an API error is shown, not thrown', await p.evaluate(async () => { fetch = () => Promise.resolve({ ok: false, status: 400, json: () => Promise.resolve({ error: { message: 'API key not valid' } }) }); lmvReset && lmvReset(); LifeMaxVoice.simulateUtterance('qwerty zxcv plugh'); await new Promise(r => setTimeout(r, 500)); const s = LifeMaxVoice.snapshot(); return /Could not reach Gemini/.test(s.title || '') }));
  ok('settings show the Gemini rows', await p.evaluate(() => { localStorage.setItem('lifemax_gemini_ui', '1'); navigate('settings'); lmvSettingsRender(); return /Gemini understanding/.test(document.getElementById('lmvSettingsBody').textContent) && !!document.querySelector('#lmvSettingsBody input[type=password]') }));
  ok('no script errors', errs.length === 0, errs);
  console.log(`\n${pass} passed, ${fail} failed`); await b.close(); process.exit(fail ? 1 : 0);
})();
