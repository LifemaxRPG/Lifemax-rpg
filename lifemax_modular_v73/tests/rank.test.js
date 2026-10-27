/* Pecking order for two cats: the odds follow the stats, the underdog can still win and gains more for it, rank shows up in the Together card.
   Run: NODE_PATH=$(npm root -g) node tests/rank.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/v(\d+)/)[1] - +b.match(/v(\d+)/)[1]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 300) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Mo', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = Date.now(); save(); navigate('pet') }); await p.waitForTimeout(2500);
  await p.evaluate(() => { petDevPair('adopt') }); await p.waitForFunction(() => petPairOn(), null, { timeout: 15000 }); await p.waitForTimeout(800);
  await p.evaluate(() => { state.pet2.born = Date.now() - 40 * 864e5; const d = petPairData(); d.met = Date.now(); d.rk = null; petRankData() });
  ok('each cat starts with a confidence from its DNA', await p.evaluate(() => { const r = petRankData(); return r.cf.every(v => v >= 0.15 && v <= 0.85) }));
  const odds = await p.evaluate(() => { const r = petRankData(); r.cf = [0.8, 0.3]; const a = petRankProb(0), b = petRankProb(1); r.cf = [0.5, 0.5]; const o = petRankPs; petRankPs = () => 0.5; const c = petRankProb(0); petRankPs = o; return { a, b, c } });
  ok('a much more confident cat is the strong favourite', odds.a > 0.75, odds);
  ok('the weaker cat still has a real, low chance', odds.b > 0.02 && odds.b < 0.4, odds);
  ok('cats with equal stats are close to a coin flip (the starter has a small edge)', Math.abs(odds.c - 0.5) < 0.12, odds);
  const sim = await p.evaluate(() => { const r = petRankData(); let w = [0, 0], ups = 0, gain = [], lose = []; for (let i = 0; i < 600; i++) { r.cf = [0.8, 0.3]; r.hist = []; r.n = 0; r.settled = false; r.lead = -1; const before = r.cf.slice(), res = petRankContest(0); w[res.w]++; const d = r.cf[res.w] - before[res.w]; (res.upset ? gain : lose).push(d) } const av = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0; return { w, upGain: av(gain), normGain: av(lose), nUp: gain.length } });
  ok('the underdog wins some contests (a low but real share)', sim.w[1] > 6 && sim.w[1] < 300, sim);
  ok('an upset lifts confidence more than an expected win', sim.upGain > sim.normGain + 0.02, sim);
  const flip = await p.evaluate(() => { const r = petRankData(); r.cf = [0.5, 0.5]; r.n = 0; r.hist = []; r.lead = -1; r.settled = false; for (let i = 0; i < 12; i++) { const res = petRankContest(1) } return { cf: r.cf, lead: r.lead, w: r.w } });
  ok('confidence stays inside 0.05 to 0.95', flip.cf.every(v => v >= 0.05 && v <= 0.95), flip);
  await p.evaluate(() => { const r = petRankData(); r.cf = [0.5, 0.5]; r.n = 0; r.w = [0, 0]; r.hist = []; r.lead = -1; r.settled = false; petPairStatus(true) });
  await p.evaluate(() => { petDevPair('standoff') });
  await p.waitForFunction(() => petRankData().n >= 1, null, { timeout: 60000 });
  ok('a standoff runs to a result', true);
  await p.waitForTimeout(500);
  ok('the Together card shows the pecking order', await p.evaluate(() => { petPairStatus(true); const c = document.getElementById('petPair'); return !!c && /PECKING ORDER/.test(c.textContent) && c.querySelectorAll('.pet-rank .pet-thread').length === 2 }));
  ok('chase and grooming consult rank when there is no age gap', await p.evaluate(() => { const r = petRankData(); r.cf = [0.9, 0.2]; let c = 0, g = 0; for (let i = 0; i < 400; i++) { if (petRankBias('chase', 1) === 0) c++; if (petRankBias('groom', 0) === 1) g++ } return c > 220 && g > 220 }));
  ok('no script errors', errs.length === 0, errs);
  console.log(pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
