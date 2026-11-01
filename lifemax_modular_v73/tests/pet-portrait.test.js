/* The cat portrait (plan OVERVIEW_BODY §4 phase 3): petPortrait(p), one still idle frame for the native Overview's cat card.
   Checked, for an adult legacy coat with HD, a genetic coat with an extreme body shape (HD on and off), and a kitten:
   - the room itself, stopped and put in the portrait's pose (idle sit, frame 0, facing right, eyes open, neutral face, ears and
     tail at rest, at noon so there is no tint), is drawn twice into its own 4x canvas, with the cat and without it; the pixels the
     cat changed are compared with petPortraitCanvas(p, PET_OS): every solid pixel equal, every clear pixel untouched in the room,
     the shadow the same blend, and the two outlines the same size (so nothing is cut off);
   - the data URL petPortrait returns is the 2x drawing, which is the 4x drawing at half size;
   - it is cached per look (a second ask draws nothing; a new body shape draws again), never moves the room's cat, answers the
     second cat as the second cat, and is null for a cat that has passed, a cat that is not one of the two, or art still loading.
   SHOT=<dir> writes a side-by-side picture per case (the room's cat, left; the portrait, right) to look at.
   Run: NODE_PATH=$(npm root -g) node tests/pet-portrait.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };

/* in the page: the room frame against the portrait, for the current cat */
const COMPARE = () => {
  const rt = PET_RT, p = petData(), c = rt.c;
  const d = new Date(petNow()); d.setHours(12, 0, 0, 0); rt.shift = (rt.shift || 0) + d.getTime() - petNow();
  Object.assign(c, { x: 92, y: PET_SURF.floor.y, z: PET_DEPTH.ART_Z, dir: 1, surf: 'floor', anim: petAnimName(rt, 'idle_sit'), t: 0, plan: [], step: null, hop: null, lock: null, wx: 0, boxSink: 0, fpsMul: 1 });
  rt.t = 0; rt.eye = { next: 0, at: -1 }; rt.hdMood = { expr: 'neutral', until: Infinity }; rt.parts = []; rt.bubble = null; rt.fog = null; rt.boxSq = 0; rt.gift = null; rt.dangle = null; rt.ball.on = false;
  const br = petBR(rt); br.som = Object.assign({}, br.som, { ear: 'neutral', tail: 'still' }); br.remUntil = 0; br.carry = null; br.stim = null;
  const W = rt.off.width, H = rt.off.height, grab = () => { petDraw(rt); return rt.octx.getImageData(0, 0, W, H).data };
  const withCat = grab(), real = window.petBoxDrawCat; window.petBoxDrawCat = function () { }; let bare; try { bare = grab() } finally { window.petBoxDrawCat = real }
  let x0 = W, y0 = H, x1 = -1, y1 = -1;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = (y * W + x) * 4; if (withCat[i] !== bare[i] || withCat[i + 1] !== bare[i + 1] || withCat[i + 2] !== bare[i + 2]) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y) } }
  const pc = petPortraitCanvas(p, PET_OS); if (!pc) return { error: 'portrait not ready' };
  const pd = pc.getContext('2d').getImageData(0, 0, pc.width, pc.height).data, PW = pc.width, PH = pc.height;
  let a0 = PW, b0 = PH, a1 = -1, b1 = -1;
  for (let y = 0; y < PH; y++) for (let x = 0; x < PW; x++) if (pd[(y * PW + x) * 4 + 3]) { a0 = Math.min(a0, x); a1 = Math.max(a1, x); b0 = Math.min(b0, y); b1 = Math.max(b1, y) }
  const out = { room: [x1 - x0 + 1, y1 - y0 + 1], portrait: [a1 - a0 + 1, b1 - b0 + 1], inside: a0 > 0 && b0 > 0 && a1 < PW - 1 && b1 < PH - 1, solid: 0, solidBad: 0, clearBad: 0, shade: 0, shadeBad: 0 };
  for (let y = 0; y < PH; y++) for (let x = 0; x < PW; x++) {
    const rx = x - a0 + x0, ry = y - b0 + y0; if (rx < 0 || ry < 0 || rx >= W || ry >= H) { if (pd[(y * PW + x) * 4 + 3]) out.solidBad++; continue }
    const i = (y * PW + x) * 4, j = (ry * W + rx) * 4, al = pd[i + 3];
    if (al === 255) { out.solid++; if (withCat[j] !== pd[i] || withCat[j + 1] !== pd[i + 1] || withCat[j + 2] !== pd[i + 2]) out.solidBad++ }
    else if (al === 0) { if (withCat[j] !== bare[j] || withCat[j + 1] !== bare[j + 1] || withCat[j + 2] !== bare[j + 2]) out.clearBad++ }
    else { out.shade++; const f = al / 255; for (let k = 0; k < 3; k++) if (Math.abs(withCat[j + k] - (pd[i + k] * f + bare[j + k] * (1 - f))) > 3) { out.shadeBad++; break } }
  }
  /* the picture to look at: the room around the cat (left) and the portrait on a dark card (right), both at 4x */
  const m = 24, sw = x1 - x0 + 1 + 2 * m, sh = y1 - y0 + 1 + 2 * m, shot = document.createElement('canvas'); shot.width = sw + PW + 16; shot.height = Math.max(sh, PH);
  petDraw(rt); const sx = shot.getContext('2d'); sx.fillStyle = '#151a24'; sx.fillRect(0, 0, shot.width, shot.height); sx.drawImage(rt.off, x0 - m, y0 - m, sw, sh, 0, 0, sw, sh); sx.drawImage(pc, sw + 16, 0);
  out.shot = shot.toDataURL('image/png');
  /* the 2x data URL is the 4x drawing at half size */
  const two = petPortraitCanvas(p, 2), td = two.getContext('2d').getImageData(0, 0, two.width, two.height).data; let halfBad = 0;
  for (let y = 0; y < two.height; y++) for (let x = 0; x < two.width; x++) { const i = (y * two.width + x) * 4, j = ((2 * y) * PW + 2 * x) * 4; for (let k = 0; k < 4; k++) if (td[i + k] !== pd[j + k]) { halfBad++; break } }
  out.halfBad = halfBad; out.size2 = [two.width, two.height];
  return out;
};
/* in the page: the cached data URL decodes to the 2x drawing (solid pixels exact; PNG round trips the shadow's alpha) */
const DECODE = () => new Promise(done => {
  const p = petData(), url = petPortrait(p), img = new Image();
  img.onload = () => {
    const cv = document.createElement('canvas'); cv.width = img.width; cv.height = img.height; const x = cv.getContext('2d'); x.drawImage(img, 0, 0);
    const a = x.getImageData(0, 0, cv.width, cv.height).data, two = petPortraitCanvas(p, 2), b = two.getContext('2d').getImageData(0, 0, two.width, two.height).data;
    let bad = 0; for (let i = 0; i < a.length; i += 4) { if (Math.abs(a[i + 3] - b[i + 3]) > 1) { bad++; continue } if (b[i + 3] === 255 && (a[i] !== b[i] || a[i + 1] !== b[i + 1] || a[i + 2] !== b[i + 2])) bad++ }
    done({ png: /^data:image\/png;base64,/.test(url), w: img.width, h: img.height, bad, bytes: url.length });
  };
  img.src = url;
});
/* in the page: waits until the portrait can be drawn (the room's art for this cat has loaded) */
const READY = () => new Promise(done => { const until = performance.now() + 15000; const look = () => { const u = petPortrait(petData()); if (u || performance.now() > until) return done(!!u); setTimeout(look, 150) }; look() });

(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 844 } }); const p = await ctx.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e))); p.on('console', m => { if (m.type() === 'warning' && /pet (hd|rig|dna|portrait)/.test(m.text())) errs.push(m.text()) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  ok('no cat: no portrait', await p.evaluate(() => petPortrait(null) === null && petPortrait(petData()) === null));
  /* adopt, open the room so its art loads, then leave it so the loop stops and every frame below is drawn by hand */
  const adopt = async (coat, age) => {
    await p.evaluate(([coat, age]) => { const pt = petAdopt(coat, 'Mo', 'playful'); pt.born = Date.now() - age * 864e5; pt.lastFed = Date.now(); pt.bondPts = 300; save(); navigate('pet') }, [coat, age]);
    await p.waitForTimeout(1500); await p.evaluate(() => navigate('settings')); await p.waitForTimeout(200);
  };
  const shots = process.env.SHOT; if (shots) fs.mkdirSync(shots, { recursive: true });
  const check = async (name) => {
    ok(name + ': the portrait is ready once the room\'s art has loaded', await p.evaluate(READY));
    const r = await p.evaluate(COMPARE); if (r.error) { ok(name + ': compared with the room', false, r); return }
    if (shots) fs.writeFileSync(path.join(shots, name.replace(/\W+/g, '-') + '.png'), Buffer.from(r.shot.split(',')[1], 'base64'));
    delete r.shot;
    ok(name + ': the cat the room draws and the portrait have the same outline, nothing cut off', r.room[0] === r.portrait[0] && r.room[1] === r.portrait[1] && r.inside && r.solid > 2000, r);
    ok(name + ': every solid pixel of the portrait is the room\'s pixel', r.solidBad === 0, r);
    ok(name + ': where the portrait is clear the cat changed nothing in the room, and the shadow blends the same', r.clearBad === 0 && r.shade > 0 && r.shadeBad === 0, r);
    ok(name + ': the 2x portrait (96x64 room pixels, 192x128) is the 4x drawing at half size', r.halfBad === 0 && r.size2[0] === 192 && r.size2[1] === 128, r);
    const dc = await p.evaluate(DECODE);
    ok(name + ': petPortrait is a PNG data URL of the 2x drawing', dc.png && dc.w === 192 && dc.h === 128 && dc.bad === 0 && dc.bytes < 40000, dc);
  };
  await adopt('ginger', 40);
  await check('adult ginger, HD');
  /* RECORD=1 writes the native fixture: a cat tile with this portrait and the image table carrying it (the key is FNV-1a of
     the URL, as plan §3.3 names images; align at merge with the projection's own image keys) */
  if (process.env.RECORD) {
    const fx = await p.evaluate(() => { const url = petPortrait(petData()), key = 'img' + petHash(url).toString(16); return { cat: Object.assign(petPeekData(), { portrait: key }), images: { [key]: url } } });
    const out = path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'test', 'resources', 'fixtures', 'overview-cat-portrait.json');
    fs.writeFileSync(out, JSON.stringify(fx, null, 1) + '\n'); console.log('wrote ' + out);
  }
  /* cached: a second ask draws nothing and the room's cat is where it was */
  const cache = await p.evaluate(() => {
    const real = window.petDrawCat; let n = 0; window.petDrawCat = function () { n++; return real.apply(this, arguments) };
    const c = PET_RT.c, before = JSON.stringify([c.x, c.y, c.anim, c.t, c.dir, PET_RT.t]);
    try { PET_PORTRAIT.cache.clear(); const a = petPortrait(petData()), n1 = n, a2 = petPortrait(petData()); return { drew: n1, again: n - n1, same: a === a2, still: before === JSON.stringify([c.x, c.y, c.anim, c.t, c.dir, PET_RT.t]), rt: PET_RT.c === c } }
    finally { window.petDrawCat = real }
  });
  ok('cached per look: the first ask draws once, the second draws nothing and returns the same picture', cache.drew === 1 && cache.again === 0 && cache.same, cache);
  ok('drawing the portrait never moves the room\'s cat', cache.still && cache.rt, cache);
  /* the second cat is drawn as the second cat */
  const two = await p.evaluate(() => new Promise(done => {
    const a = petPortrait(state.pet); state.pet2 = Object.assign({}, state.pet, { coat: 'black', name: 'Pip', dna: null, brain: null });
    const until = performance.now() + 15000, look = () => {
      const p2 = petPortrait(state.pet2); if (!p2 && performance.now() < until) return setTimeout(look, 150);
      const r = { differs: !!p2 && p2 !== a, first: petPortrait(state.pet) === a, stranger: petPortrait(Object.assign({}, state.pet)) === null }; state.pet2 = null; done(r);
    }; look();
  }));
  ok('the second cat\'s portrait is its own; a cat that is not one of the two has none', two.differs && two.first && two.stranger, two);
  /* a genetic coat with an extreme body shape: the colour genes, the eyes and the shape all come through */
  await p.evaluate(() => { for (let t = 0; t < 300; t++) { petDevDnaReroll(); const r = petDnaRecipe(petData().coat); if (r && r.tab && r.white > 0) break } petDevMorphSet({ em: 1, ew: 1, tl: 1, lg: 1, bl: 1, wk: 1, bob: 0 }) });
  const key = await p.evaluate(() => petPortraitKey(petData()));
  ok('the cache key carries the genetic coat and the body shape', /^dna_/.test(key) && !/\|n$/.test(key), key);
  await check('genetic coat, extreme shape, HD');
  await p.evaluate(() => { state.hdCat = false; LifeMaxHD.clear() });
  ok('HD off is its own picture', await p.evaluate(() => /\|lo\|/.test(petPortraitKey(petData()))));
  await check('genetic coat, extreme shape, HD off');
  await p.evaluate(() => { state.hdCat = true; LifeMaxHD.clear() });
  /* a kitten: its own stage sheet, never the adult art standing in while that loads */
  await p.evaluate(() => { state.pet = null; save() }); await adopt('gray', 3);
  ok('a kitten\'s portrait waits for its own art: not drawn from the adult sheet', await p.evaluate(() => { const rt = PET_RT, k = petGrowIdx(petData()) + '|' + petData().coat, g = rt.gs[k]; if (!g) return true; delete rt.gs[k]; PET_PORTRAIT.cache.clear(); const u = petPortrait(petData()); rt.gs[k] = g; return u === null }));
  await check('kitten, HD');
  /* onReady: a portrait asked for while loading says when it can be drawn */
  const told = await p.evaluate(() => new Promise(done => { const rt = PET_RT, k = petGrowIdx(petData()) + '|' + petData().coat, g = rt.gs[k]; delete rt.gs[k]; PET_PORTRAIT.cache.clear(); const u = petPortrait(petData(), () => done({ first: u, now: !!petPortrait(petData()) })); setTimeout(() => done({ timeout: true }), 8000) }));
  ok('asked while loading: null, then onReady once it can be drawn', told.first === null && told.now === true, told);
  /* a cat that has passed has no portrait */
  ok('a cat that has passed: no portrait', await p.evaluate(() => { const p = petData(); p.alive = false; try { return petPortrait(p) === null } finally { p.alive = true } }));
  ok('no page errors or pet warnings', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
