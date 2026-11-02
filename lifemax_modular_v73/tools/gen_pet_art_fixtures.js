/* Records what the page draws from the cat's art, for the native recolour to be tested against (feature/pet/art, PetArtFidelityTest).
   The page is run, not read: petInit() recolours the four coats' sheets and the depth sheets (rt.sheets, rt.dsheets), petLoadStage()
   the kitten stages (rt.gs), petDepthStageSheet() the depth kitten stages (rt.dsg); the pixels those canvases hold are written as RGBA
   PNGs, exact (a clear pixel is 0,0,0,0). The page's own tables are recorded beside them.
   Output in android-wrapper/native/src/test/resources/pet-art/:
     adult-<coat>.png     all 28 rows x 8 frames of each of the four coats (576 x 1344)
     depth-<coat>.png     walk_front, walk_back, sniff, bonk (432 x 192)
     stage<i>-<coat>.png  the kitten art for stages 0..5, one coat per stage sampled: ginger 0, gray 2, black 4, tuxedo 5
     dstage<i>-<coat>.png every depth kitten stage of every coat (432 x 192)
     room-items.png       the room item atlas as the page's canvas holds it (256 x 128)
     web-tables.json      anims, depth anims, palettes, growth tables, room atlas meta, and the stage the page picks for sample ages
   Run: NODE_PATH=$(npm root -g) node tools/gen_pet_art_fixtures.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const png = require('./pet_png.js');
const file = require('../tests/current-page')(process.argv[2]);
const target = path.resolve(__dirname, '../../android-wrapper/native/src/test/resources/pet-art');
const COATS = ['ginger', 'gray', 'black', 'tuxedo'];
const KITTEN_SAMPLE = { ginger: 0, gray: 2, black: 4, tuxedo: 5 };
const AGES = [0, 0.5, 4.99, 5, 9.99, 10, 14.9, 15, 19.99, 20, 24.99, 25, 29.99, 30, 45];

/* in the page: the recoloured sheets as base64 RGBA, and the tables */
const RECORD = async ({ COATS, KITTEN_SAMPLE, AGES }) => {
  const rt = await petInit(), A = rt.A, G = petGrowthJson(), out = { sheets: {}, tables: {} };
  const grab = (cv) => {
    const d = cv.getContext('2d').getImageData(0, 0, cv.width, cv.height).data; let s = '';
    for (let i = 0; i < d.length; i += 0x8000) s += String.fromCharCode.apply(null, d.subarray(i, i + 0x8000));
    return { w: cv.width, h: cv.height, b64: btoa(s) };
  };
  for (const coat of COATS) {
    out.sheets['adult-' + coat] = grab(rt.sheets[coat]); out.sheets['depth-' + coat] = grab(rt.dsheets[coat]);
  }
  const wait = (f) => new Promise((ok) => { const t0 = performance.now(); const look = () => { const v = f(); if (v || performance.now() - t0 > 10000) ok(v); else setTimeout(look, 30) }; look() });
  for (const coat of COATS) {
    const p = petAdopt(coat, 'Mo', 'playful'); p.born = petNow() - 2 * 864e5; save();
    const i = KITTEN_SAMPLE[coat]; await petLoadStage(rt, i); out.sheets['stage' + i + '-' + coat] = grab(rt.gs[i + '|' + coat].cv);
    for (let s = 0; s < 6; s++) { petDepthStageSheet(rt, p, s); const cv = await wait(() => rt.dsg[s + '|' + coat].cv); out.sheets['dstage' + s + '-' + coat] = grab(cv) }
  }
  {
    const cv = document.createElement('canvas'); cv.width = rt.items.width; cv.height = rt.items.height;
    cv.getContext('2d').drawImage(rt.items, 0, 0); out.sheets['room-items'] = grab(cv);
  }
  const ageIdx = AGES.map((d) => { const p = petData(); p.born = petNow() - d * 864e5; return [d, petGrowIdx(p)] });
  /* the kitten row and foot row the draw (17-runtime.js petDrawCat) picks for an anim at a stage: the stage's own bottom, else idle_sit's */
  const stageRows = G.stages.map((g, i) => Object.fromEntries(Object.keys(A.anims).map((n) => {
    const meta = g.bottom[n] == null && g.bottom.idle_sit != null ? A.anims.idle_sit : A.anims[n];
    return [n, { row: meta.row, n: meta.n, fps: meta.fps, bottom: g.bottom[n] != null ? g.bottom[n] : g.bottom.idle_sit }];
  })));
  out.tables = {
    W: A.W, H: A.H, cols: A.cols, anims: A.anims, animOrder: Object.keys(A.anims), depth: { W: A.depth.W, H: A.depth.H, anims: A.depth.anims, animOrder: Object.keys(A.depth.anims) },
    pal: Object.fromEntries(COATS.map((c) => [c, A.pal[c]])),
    growth: { days: G.days, stages: G.stages.map((s) => ({ a: s.a, sx: s.sx, bottom: s.bottom })) }, depthStageSx: A.depth.stages.map((s) => s.sx),
    growDays: PET_GROW_DAYS, roomMeta: A.room.meta, ageIdx, stageRows,
  };
  return out;
};

(async () => {
  const b = await chromium.launch(), p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(), errs = [];
  p.on('pageerror', (e) => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const rec = await p.evaluate(RECORD, { COATS, KITTEN_SAMPLE, AGES });
  await b.close();
  if (errs.length) { console.error(errs.join('\n')); process.exit(1) }
  fs.rmSync(target, { recursive: true, force: true }); fs.mkdirSync(target, { recursive: true });
  let total = 0;
  for (const [name, s] of Object.entries(rec.sheets)) {
    const buf = png.encode({ width: s.w, height: s.h, channels: 4, data: Buffer.from(s.b64, 'base64') });
    fs.writeFileSync(path.join(target, name + '.png'), buf); total += buf.length; console.log('wrote ' + name + '.png', buf.length);
  }
  const want = COATS.map((c) => 'adult-' + c).concat(COATS.map((c) => 'depth-' + c), COATS.map((c) => 'stage' + KITTEN_SAMPLE[c] + '-' + c));
  for (const n of want) if (!rec.sheets[n]) throw new Error('missing ' + n);
  fs.writeFileSync(path.join(target, 'web-tables.json'), JSON.stringify(rec.tables, null, 1) + '\n');
  console.log('total png bytes', total);
})();
