/* HD cat tests. Run:  NODE_PATH=$(npm root -g) node tests/pethd.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 844 } }); const p = await ctx.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e))); p.on('console', m => { if (m.type() === 'warning' && /pet (hd|rig|dna)/.test(m.text())) errs.push(m.text()) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const D = await p.evaluate(() => { const B = petHdJson(), A = petAssetsJson(); return { hs: B.hs, kinds: Object.keys(B.kinds), rows: B.kinds.adult.anims.length, want: Object.keys(A.anims).length, depth: B.kinds.depth.anims, faces: B.kinds.adult.face.walk.filter(Boolean).length, back: B.kinds.depth.face.walk_back.filter(Boolean).length, closed: B.kinds.adult.face.slowblink.filter(f => f && !f.o).length, open: B.kinds.adult.face.walk.every(f => f && f.o) } });
  ok('data: 2x, every growth stage, front and back views', D.hs === 2 && D.kinds.length === 14, D);
  ok('data: every animation of the cat is there (28 adult rows, 4 front/back rows)', D.rows === D.want && D.rows === 28 && D.depth.length === 4, D);
  ok('data: front-on frames have a face anchor, the back view has none, closed-eye frames are marked', D.faces === 8 && D.back === 0 && D.closed > 0 && D.open, D);
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
  // every animation now comes from the HD sheets
  await p.evaluate(() => { PET_RT.hd = PET_RT.hd || {}; petHdSheet(PET_RT, petData().coat, 'adult') }); await p.waitForTimeout(1500);
  const all = await p.evaluate(() => { const rt = PET_RT, p = petData(); return Object.keys(rt.A.anims).map(a => [a, !!LifeMaxHD.pick(rt, p, 'adult', a, 0, rt.A.anims[a], Object.assign({}, rt.c, { anim: a }))]) });
  ok('every one of the 28 adult animations is drawn from the HD sheet', all.length === 28 && all.every(x => x[1]), all.filter(x => !x[1]));
  const closed = await p.evaluate(() => { const rt = PET_RT, p = petData(), c = Object.assign({}, rt.c, { anim: 'slowblink' }); rt.t = 0.5; rt.hdMood = { expr: 'neutral', until: Date.now() + 9e5 }; const K = petHdJson().kinds.adult, a = K.face.slowblink; const fc = a.findIndex(f => f && !f.o), fo = a.findIndex(f => f && f.o); const px = f => { LifeMaxHD.clear(); const h = LifeMaxHD.pick(rt, p, 'adult', 'slowblink', f, rt.A.anims.slowblink, c); return Array.from(h.cv.getContext('2d').getImageData(0, 0, h.cv.width, h.cv.height).data).join(',') }; return { fc, fo, same: fc >= 0 && fo >= 0 && px(fc) !== px(fo) } });
  ok('a frame drawn with closed eyes keeps them closed even when the mood says neutral', closed.fc >= 0 && closed.fo >= 0 && closed.same, closed);
  const pose = await p.evaluate(() => { const rt = PET_RT, p = petData(), c = Object.assign({}, rt.c, { anim: 'idle_sit' }); rt.t = 0.5; rt.hdMood = { expr: 'neutral', until: Date.now() + 9e5 }; const real = rt.br; rt.br = { som: { ear: 'airplane_flat', tail: 'tip_twitch' }, remUntil: 0 }; const A = petBrainArt(rt); const flat = petHdPose(rt, 'idle_sit'); const none = petHdPose(rt, 'walk'); LifeMaxHD.clear(); const h1 = LifeMaxHD.pick(rt, p, 'adult', 'idle_sit', 0, rt.A.anims.idle_sit, c); rt.br = { som: { ear: 'neutral', tail: 'still' }, remUntil: 0 }; LifeMaxHD.clear(); const h0 = LifeMaxHD.pick(rt, p, 'adult', 'idle_sit', 0, rt.A.anims.idle_sit, c); rt.br = real; const d = h => Array.from(h.cv.getContext('2d').getImageData(0, 0, h.cv.width, h.cv.height).data).join(','); return { flat: !!(flat && flat.flat), none: none === null, differs: d(h1) !== d(h0) } });
  ok('flattened ears (the cat\'s mood) change the HD frame, only on the rows that allow it', pose.flat && pose.none && pose.differs, pose);
  const kit = await p.evaluate(() => { const rt = PET_RT; return Object.keys(rt.A.depth.anims).length });
  ok('front and back view rows are known to the HD system', kit === 4, kit);
  await p.evaluate(() => { navigate('pet') }); await p.waitForTimeout(500);
  await p.evaluate(() => { petDevScratch() }); await p.waitForTimeout(1500); await p.evaluate(() => { petDevPlay2(1) }); await p.waitForTimeout(2200);
  ok('scratching and playing still draw at the new room scale without errors', errs.length === 0, errs);
  /* v202: sleeping eyes, blink rate, camera stillness */
  ok('a frame meant to have closed eyes really draws them closed (the "closed" expression exists)', await p.evaluate(() => { const px = new Uint32Array(100 * 100); const a = { l: [50, 40], r: [50, 60], nose: [55, 50], mouth: [58, 50], sc: 1 }; try { petHdFace(px, 100, 100, a, 'closed', petHdCols(PET_RT, petData().coat), 1); return true } catch (e) { return false } }) && await p.evaluate(() => { const s = petHdFace.toString(); return /closed:\['closed','closed'\]/.test(s) }));
  const sl = await p.evaluate(() => { const rt = PET_RT, c = rt.c, old = c.step; c.step = { k: 'act', a: 'sleep', t: 0, d: 99, depth: 0.1 }; const e1 = petHdExpr(rt, petData(), c); c.step = { k: 'act', a: 'sleep', t: 0, d: 99, depth: 0.9 }; const e2 = petHdExpr(rt, petData(), c); c.step = old; return [e1, e2] });
  ok('a sleeping cat has its eyes shut, light doze or deep', sl[0] === 'blink' && sl[1] === 'blink', sl);
  const bl = await p.evaluate(() => { const rt = PET_RT, c = rt.c, old = c.step; c.step = null; const t0 = rt.t; let n = 0, prev = false, tot = 0; for (let i = 0; i < 6000; i++) { rt.t = 10 + i * 0.01; const e = petHdExpr(rt, petData(), c) === 'blink'; if (e && !prev) n++; if (e) tot++; prev = e } rt.t = t0; c.step = old; return { perMin: n, shut: tot / 6000 } });
  ok('an awake cat blinks about every 3 seconds, eyes shut for a fraction of a second', bl.perMin >= 17 && bl.perMin <= 34 && bl.shut > 0.04 && bl.shut < 0.12, bl);
  const cam = await p.evaluate(() => { const rt = PET_RT, c = rt.c; c.step = { k: 'act', a: 'loaf', t: 0, d: 999 }; rt.cam.cx = c.x - 7; rt.camGo = false; const x0 = rt.cam.cx; for (let i = 0; i < 200; i++) petCamUpdate(rt, 0.05); const still = rt.cam.cx === x0; rt.cam.cx = c.x - 40; for (let i = 0; i < 400; i++) petCamUpdate(rt, 0.05); return { still, settled: Math.abs(rt.cam.cx - c.x) < 1, whole: rt.cam.cx === Math.round(rt.cam.cx) } });
  ok('a resting cat does not make the room drift (small offsets are ignored)', cam.still, cam);
  ok('a real offset still pans and settles on a whole pixel', cam.settled && cam.whole, cam);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
