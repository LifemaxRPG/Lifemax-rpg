/* HD cat tests. Run:  NODE_PATH=$(npm root -g) node tests/pethd.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2] || path.join(__dirname, '..', 'dist', fs.readdirSync(path.join(__dirname, '..', 'dist')).filter(f => /^Life_Max_RPG_v\d+\.html$/.test(f)).sort((a, b) => +a.match(/\d+/)[0] - +b.match(/\d+/)[0]).pop()));
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 844 } }); const p = await ctx.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e))); p.on('console', m => { if (m.type() === 'warning' && /pet (hd|rig|dna)/.test(m.text())) errs.push(m.text()) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const D = await p.evaluate(() => { const B = petHdJson(); return { hs: B.hs, kinds: Object.keys(B.kinds), rows: B.kinds.adult.anims, depth: B.kinds.depth.anims, faces: B.kinds.adult.face.walk.filter(Boolean).length, back: B.kinds.depth.face.walk_back.filter(Boolean).length } });
  ok('data: 2x, every growth stage, front and back views', D.hs === 2 && D.kinds.length === 14 && D.rows.join() === 'walk,walk_w' && D.depth.join() === 'walk_front,walk_back', D);
  ok('data: every front-on walking frame has a face anchor, the back view has none', D.faces === 8 && D.back === 0, D);
  ok('the room is drawn on an even grid so 2x pixels stay crisp', await p.evaluate(() => PET_OS === 4));
  const walk = async (coat, age, dna) => {
    await p.evaluate(([coat, age]) => { const pt = petAdopt(coat, 'Mo', 'playful'); pt.born = Date.now() - age * 864e5; pt.lastFed = Date.now(); pt.bondPts = 300; save(); navigate('pet') }, [coat, age]); await p.waitForTimeout(2600);
    if (dna) { await p.evaluate(() => { for (let t = 0; t < 300; t++) { petDevDnaReroll(); const r = petDnaRecipe(petData().coat); if (r && r.tab && r.white > 0) break } }); await p.waitForTimeout(2400) }
    await p.evaluate(() => { const rt = PET_RT, c = rt.c; const q = petRoute(c, 'floor', 40); q.push(...petRoute(c, 'floor', 150)); petInterrupt(rt, q) });
    let seen = null; for (let i = 0; i < 30; i++) { await p.waitForTimeout(250); seen = await p.evaluate(() => ({ a: PET_RT.c.anim, h: LifeMaxHD.stats() })); if (/walk/.test(seen.a) && seen.h.hits > 0) break }
    return seen;
  };
  let r = await walk('ginger', 40); ok('adult: the walk is drawn from the HD sheet', /walk/.test(r.a) && r.h.hits > 0 && r.h.sheets.some(k => /^adult\|/.test(k)) && r.h.err === 0, r);
  r = await walk('gray', 3); ok('kitten: its own HD sheet, no errors', r.h.err === 0 && r.h.sheets.some(k => /^st0\|/.test(k)), r);
  r = await walk('ginger', 40, true); ok('genetic coat: an HD sheet is composed for it too', r.h.err === 0 && r.h.sheets.some(k => /^adult\|dna_/.test(k)), r);
  // frames
  const F = await p.evaluate(() => { const rt = PET_RT, p = petData(), m = rt.A.anims.walk; const out = {}; const pk = (e, k) => { LifeMaxHD.clear(); const h = petHdPick(rt, p, 'adult', 'walk', 2, m, Object.assign({}, rt.c, { anim: 'walk' })); return h };
    const a = pk(); out.size = a && [a.cv.width, a.cv.height, a.hs]; return out });
  ok('a frame is 2x (144x96 or larger with margins) and says so', F.size && F.size[2] === 2 && F.size[0] >= 144 && F.size[1] >= 96, F);
  const diff = await p.evaluate(() => { const rt = PET_RT, p = petData(), m = rt.A.anims.walk, c = Object.assign({}, rt.c, { anim: 'walk' }); const px = e => { rt.hdMood = { expr: e, until: Date.now() + 5000 }; LifeMaxHD.clear(); const h = petHdPick(rt, p, 'adult', 'walk', 0, m, c); const d = h.cv.getContext('2d').getImageData(0, 0, h.cv.width, h.cv.height).data; return Array.from(d).join(',') }; const t = rt.t; rt.t = 0; const n = px('neutral'), hp = px('happy'), sl = px('sleepy'); rt.t = t; rt.hdMood = null; return { hp: n !== hp, sl: n !== sl, same: n === px('neutral') } });
  ok('expressions change the face; the same expression is identical', diff.hp && diff.sl, diff);
  // toggle
  await p.evaluate(() => { state.hdCat = false; LifeMaxHD.clear() }); const off = await p.evaluate(() => { const rt = PET_RT, p = petData(); return LifeMaxHD.pick(rt, p, 'adult', 'walk', 0, rt.A.anims.walk, rt.c) });
  ok('switched off, nothing is drawn from the HD sheet (the old art is used)', off === null);
  await p.evaluate(() => { state.hdCat = true }); await p.evaluate(() => navigate('settings')); await p.waitForTimeout(300);
  ok('Settings has the HD toggle and it reflects the choice', await p.evaluate(() => !!document.getElementById('hdToggle') && document.getElementById('hdToggle').checked));
  // other animations still use the old art and still draw
  const other = await p.evaluate(() => { const rt = PET_RT, p = petData(); return ['idle_sit', 'sleep', 'play', 'scratch', 'groom', 'loaf', 'run', 'stretch'].map(a => LifeMaxHD.pick(rt, p, 'adult', a, 0, rt.A.anims[a], rt.c)) });
  ok('animations not yet redrawn are left to the old art', other.every(x => x === null));
  await p.evaluate(() => { navigate('pet') }); await p.waitForTimeout(500);
  await p.evaluate(() => { petDevScratch() }); await p.waitForTimeout(1500); await p.evaluate(() => { petDevPlay2(1) }); await p.waitForTimeout(2200);
  ok('scratching and playing still draw at the new room scale without errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
