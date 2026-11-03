/* The web room holds still while the camera does (the owner's "room shake" report, docs/architecture/PET_TAB_INVENTORY.md).
   The room's layers move with the phone's tilt (deviceorientation gamma) and with a drag. A phone held in a hand sends a gamma
   that is never still (sensor noise, the hand's tremor), and near upright gamma is unstable altogether; fed straight into the
   layer offsets and rounded to whole room pixels, that made the background and every item jump a pixel back and forth with
   the camera standing still. Checked here with the real frame loop running, the cat held still and synthetic deviceorientation
   events at 60 Hz carrying realistic noise:
   - a phone held still at a tilt (+-0.3 degree noise, and a hand's +-1 degree tremor): after it settles, no layer offset
     changes from frame to frame, and every offset is back at 0 (a steady phone is no tilt);
   - a phone held near upright, where gamma swings by degrees: no layer moves at all;
   - the feature is kept: a real tilt of the phone moves the near layers more than the wall, and a held drag holds still;
   - the camera stood still through all of it (so any change measured is the room shaking, not the camera).
   Run: NODE_PATH=$(npm root -g) node tests/pet-depth-steady.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x ? '  ' + JSON.stringify(x) : '')) } };

/* in the page: hold the cat still, send deviceorientation at 60 Hz as `gamma(t)`/`beta(t)` (t in s, seeded noise), record the
   offset of each depth plane against the wall (what petDepthScene translates an item by) and the wall's own, every frame drawn */
const RUN = ([spec, ms, settleMs]) => new Promise(done => {
  const rt = PET_RT, c = rt.c, Z = [0, 35, 45, 60, 75, 95];
  Object.assign(c, { plan: [], step: petAct('loaf', 9999), x: 92, z: PET_DEPTH.ART_Z, surf: 'floor' }); c.y = petFloorY(c.z);
  if (rt.cam) rt.cam.cx = c.x; rt.camGo = false;
  /* each case starts as a phone just picked up: no tilt seen yet, the room at rest */
  PET_TILT_RAW = null; PET_TILT_F = null; PET_TILT = null; rt.par = { k: 0, target: 0 };
  const realUpdate = window.petUpdate; window.petUpdate = function () { };
  let seed = spec.seed || 7; const rnd = () => { seed = (seed * 1103515245 + 12345) % 2147483648; return seed / 2147483648 };
  const noise = a => (rnd() * 2 - 1) * a;
  const t0 = performance.now(), send = () => {
    const t = (performance.now() - t0) / 1000;
    const g = spec.g0 + (spec.ramp ? Math.min(1, t / 0.3) * spec.ramp : 0) + noise(spec.noise) + (spec.tremor ? spec.tremor * Math.sin(t * 2 * Math.PI * 7) : 0);
    const e = new Event('deviceorientation'); e.alpha = 0; e.beta = spec.beta + noise(spec.betaNoise || 0); e.gamma = g; window.dispatchEvent(e);
  };
  const iv = setInterval(send, 1000 / 60); send();
  if (spec.drag) { petDepthPointer(rt, 'down', 100, 'room'); petDepthPointer(rt, 'move', 100 + spec.drag) }
  const realDraw = window.petDraw, frames = [];
  window.petDraw = function (r) { const bg = petParBg(r); frames.push({ t: performance.now() - t0, cam: r.cam ? r.cam.cx : null, bg: bg, d: Z.map(z => petParOff(r, z) - bg), k: r.par ? r.par.k : 0 }); return realDraw.apply(this, arguments) };
  setTimeout(() => {
    clearInterval(iv); window.petDraw = realDraw; window.petUpdate = realUpdate;
    if (spec.drag) petDepthPointer(rt, 'up', 0);
    const after = frames.filter(f => f.t >= settleMs);
    let changes = 0, reversals = 0; const last = {};
    for (let i = 1; i < after.length; i++) {
      const a = after[i - 1], b = after[i];
      if (a.bg !== b.bg || a.d.some((v, j) => v !== b.d[j])) changes++;
      if (i > 1) { const p = after[i - 2]; if (p.bg === b.bg && p.bg !== a.bg) reversals++; for (let j = 0; j < Z.length; j++) if (p.d[j] === b.d[j] && p.d[j] !== a.d[j]) reversals++ }
    }
    const cams = frames.map(f => f.cam), end = after[after.length - 1] || {};
    done({ frames: frames.length, measured: after.length, changes, reversals, camSteady: cams.every(x => x === cams[0]), endBg: end.bg, endD: end.d, endK: end.k,
      maxD: frames.reduce((m, f) => Math.max(m, ...f.d.map(Math.abs)), 0), nearOverWall: frames.some(f => Math.abs(f.d[5]) > Math.abs(f.d[1]) + 2) });
  }, ms);
});

(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 393, height: 844 } }); const p = await ctx.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  await p.evaluate(() => { const pt = petAdopt('ginger', 'Mo', 'playful'); pt.born = Date.now() - 40 * 864e5; pt.lastFed = Date.now(); pt.bondPts = 300; save(); navigate('pet') });
  await p.waitForFunction(() => PET_RT && PET_RT.ready && PET_LOOP.raf, null, { timeout: 15000 });
  await p.waitForTimeout(600);

  /* a phone held still at a tilt that puts the 75 plane (12 px) right on a rounding edge: gamma 9.4 -> k 0.376 -> 4.51 px */
  const held = await p.evaluate(RUN, [{ g0: 9.4, beta: 40, noise: 0.3 }, 13000, 3000]);   /* 10 s measured */
  ok('the camera stands still while the phone is held at a tilt', held.camSteady, held);
  ok('a phone held still at a tilt (+-0.3 degree noise): no layer moves from frame to frame once it has settled', held.measured > 40 && held.changes === 0, held);
  ok('...and no layer flickers back and forth', held.reversals === 0, held);
  ok('a steady phone is no tilt: every layer is back where the room is painted', held.endBg === 0 && held.endD.every(v => v === 0) && held.endK === 0, held);

  const tremor = await p.evaluate(RUN, [{ g0: -12, beta: 35, noise: 0.3, tremor: 1, seed: 11 }, 6000, 3500]);
  ok('a hand\'s tremor (+-1 degree at 7 Hz) on top of the noise: still no layer moves once settled', tremor.changes === 0 && tremor.reversals === 0 && tremor.camSteady, tremor);

  /* held nearly upright, gamma is unstable (it swings by degrees as beta passes 90) */
  const upright = await p.evaluate(RUN, [{ g0: 0, beta: 86, noise: 8, betaNoise: 3, seed: 5 }, 4000, 0]);
  ok('a phone held upright, where gamma swings by degrees: no layer moves at all', upright.changes === 0 && upright.maxD === 0, upright);

  /* the feature is kept: a real tilt moves the near layers more than the wall */
  const tilt = await p.evaluate(RUN, [{ g0: 0, beta: 40, noise: 0.3, ramp: 18, seed: 3 }, 1500, 0]);
  ok('a real tilt of the phone still moves the near layers more than the wall', tilt.nearOverWall && tilt.maxD >= 4, tilt);

  /* a drag held still holds still */
  const drag = await p.evaluate(RUN, [{ g0: 0, beta: 40, noise: 0.3, drag: 24, seed: 9 }, 3000, 1500]);
  ok('a drag held still: the layers have moved and hold still', drag.maxD >= 4 && drag.changes === 0 && drag.reversals === 0, drag);

  ok('no page errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
