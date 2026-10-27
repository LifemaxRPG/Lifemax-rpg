/* Quest and dice sounds. Run: NODE_PATH=$(npm root -g) node tests/sfx.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const mk = async (mode, init) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); await p.addInitScript((init || '') + "localStorage.setItem('lma_qdeal','" + (mode || 'quick') + "');if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));"); await p.goto('file://' + file); await p.waitForTimeout(800); return p };
  const p = await mk(); const ev = (f, a) => p.evaluate(f, a);
  ok('sound groups for cards, dice, die and chips are packed', await ev(() => ['ui-bell', 'card-place', 'card-slide', 'card-shuffle', 'card-fan', 'card-shove', 'dice-throw', 'dice-shake', 'dice-grab', 'die-throw', 'chips-handle', 'chips-stack', 'cards-pack-take-out'].every(g => QSFX.has(g))));
  ok('the packed sounds add well under 1 MB', await ev(() => JSON.stringify(SFX_DATA).length < 700000));
  const dec = await ev(async () => { const out = []; for (const g of QSFX.groups()) { const ac = new (window.AudioContext || window.webkitAudioContext)(); const k = Object.keys(SFX_DATA).find(x => x.replace(/-\d+$/, '') === g); const bin = atob(SFX_DATA[k]), u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); try { const buf = await ac.decodeAudioData(u.buffer); out.push([g, +buf.duration.toFixed(2), buf.numberOfChannels]) } catch (e) { out.push([g, 'ERR']) } ac.close() } return out });
  ok('every sound group decodes in the browser audio engine', dec.length >= 10 && dec.every(x => x[1] !== 'ERR' && x[1] > 0.1), dec);
  // play log
  await ev(() => { window.__s = []; const o = QSFX.play; QSFX.play = function (n, opt) { window.__s.push(n); return o.apply(this, arguments) } });
  ok('the volume setting scales and can switch sounds off', await ev(() => { QSFX.setVol(0); const a = QSFX.on(); QSFX.setVol(.7); return !a && QSFX.on() }));
  ok('the main Sound switch silences them too', await ev(() => { state.fx = { sound: false }; const a = QSFX.on(); state.fx = { sound: true }; return !a }));
  ok('Settings has a volume choice', await ev(() => { setPickCat('look'); const s = document.getElementById('qsfxVol'); return !!s && s.options.length === 4 }));
  // luck dice
  await ev(() => { window.__s.length = 0; luckImpact(.6); luckImpact(.2) });
  ok('the Luck dice impacts use the recorded die sounds', await ev(() => window.__s.includes('die-throw') && window.__s.includes('dice-throw')), await ev(() => window.__s));
  await ev(() => { window.__s.length = 0; const rec = { d: [3, 4, 5, 2] }; const tok = ++LK.run; LK.k = 1; try { LK.dice || 0 } catch (e) { } });
  // old page sounds
  await ev(() => { document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); state.dailyDraw.dealt = true; window.__s.length = 0; navigate('quests'); dhReveal(0) }); await p.waitForTimeout(400);
  ok('turning a card over on the flat page plays a card sound', await ev(() => window.__s.includes('card-slide')));
  // 3D scene sequence
  const q = await mk('table'); await q.evaluate(() => { window.__s = []; const o = QSFX.play; QSFX.play = function (n) { window.__s.push(n); return o.apply(this, arguments) }; document.querySelectorAll('.lu-card').forEach(e => e.parentElement.remove()); navigate('quests') });
  await q.waitForSelector('.gate3-btn', { timeout: 8000 }); await q.waitForTimeout(500); await q.click('.gate3-btn');
  await q.waitForSelector('#fateScene[data-await="flip"]', { timeout: 40000 });
  const names = await q.evaluate(() => [...new Set(window.__s)]);
  ok('the reading plays deck, shuffle, deal and slide sounds', ['card-fan', 'cards-pack-take-out', 'card-slide', 'card-shuffle', 'card-place'].every(n => names.includes(n)), names);
  await q.mouse.move(206, 400); await q.mouse.down(); for (let i = 1; i <= 8; i++) { await q.mouse.move(206 + i * 30, 400); await q.waitForTimeout(16) } await q.mouse.up(); await q.waitForTimeout(1500);
  ok('the shuffle button rings the bell', await q.evaluate(() => window.__s.includes('ui-bell')));
  ok('turning a card face-up plays a reveal sound', await q.evaluate(() => window.__s.includes('chips-handle') || window.__s.includes('chips-stack')));
  ok('no page errors', p.errs.length === 0 && q.errs.length === 0, [p.errs, q.errs]);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
