/* Records what the page's rig composer draws, for the native port to be tested against pixel for pixel
   (feature/pet/art, RigComposeFidelityTest). The page is run, not read: for each case the real recoloured sheet of the
   page, the real rig data and the real petRigCompose give a 224 x 100 picture (the frame plus the rig's margins).
   Output in android-wrapper/native/src/test/resources/pet-rig/:
     atlas.png    every composed picture, RGBA, 20 to a row, in the order of cases.json
     cases.json   per case: kind (adult, st2, depth, dst2), coat, anim, frame, the pose the web uses {ear0, ear1, tail, flat},
                  the morph {em, ew, tl, lg, bl, wk, bob} the compose got (tl is already the bobtail-limited value), and the cell
   The sample: every adult animation of the ginger coat at two frames in five poses (neutral, ears flat, ears swivelled,
   tail turned each way) with extreme proportions, and at frame 0 with small and bobtail proportions; the other three
   legacy coats, a kitten stage, the depth rows and a depth kitten stage on fewer cases (the composer does not look at
   colours, so the coats only prove the recolour and the margins agree).
   Run: NODE_PATH=$(npm root -g) node tools/gen_pet_rig_fixtures.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const png = require('./pet_png.js');
const file = require('../tests/current-page')(process.argv[2]);
const target = path.resolve(__dirname, '../../android-wrapper/native/src/test/resources/pet-rig');
const COLUMNS = 20;

const POSES = {
  neutral: null,
  flat: { ear0: -36, ear1: 36, flat: 1, tail: 0, tw: 0 },
  swivel: { ear0: -20, ear1: 12, flat: 0, tail: 0, tw: 0 },
  tailUp: { ear0: 0, ear1: 0, flat: 0, tail: 7, tw: 2 },
  tailDown: { ear0: 0, ear1: 0, flat: 0, tail: -7, tw: 0 },
  mixed: { ear0: -36, ear1: 36, flat: 1, tail: 7, tw: 2 },
};
const MORPHS = {
  extreme: { em: 2.2, ew: 1.5, tl: 1.8, lg: 1.4, bl: 1.5, wk: 1.8 },
  small: { em: 0.5, ew: 0.7, tl: 0.45, lg: 0.7, bl: 0.7, wk: 0.5 },
  bob: { em: 1.1, ew: 1, tl: 1.3, lg: 1, bl: 1, wk: 1, bob: true },
  plain: { em: 1, ew: 1, tl: 1, lg: 1, bl: 1, wk: 1 },
};

const RECORD = async ({ POSES, MORPHS, COLUMNS }) => {
  const rt = await petInit(), A = rt.A, cases = [], cells = [];
  const wait = (f) => new Promise((ok) => { const t0 = performance.now(); const look = () => { const v = f(); if (v || performance.now() - t0 > 10000) ok(v); else setTimeout(look, 30) }; look() });
  const sheets = {};
  for (const coat of ['ginger', 'gray', 'black', 'tuxedo']) { sheets['adult|' + coat] = rt.sheets[coat]; sheets['depth|' + coat] = rt.dsheets[coat] }
  const p = petAdopt('gray', 'Mo', 'playful'); p.born = petNow() - 2 * 864e5; save();
  await petLoadStage(rt, 2); sheets['st2|gray'] = rt.gs['2|gray'].cv;
  petDepthStageSheet(rt, p, 2); sheets['dst2|gray'] = (await wait(() => rt.dsg['2|gray'].cv));
  const run = (kind, coat, anim, frame, poseName, morphName) => {
    const dep = kind.charAt(0) === 'd', meta = (dep ? A.depth.anims : A.anims)[anim], W = dep ? A.depth.W : A.W, H = dep ? A.depth.H : A.H;
    const fr = petRigFrameData(kind, anim, frame), sh = sheets[kind + '|' + coat];
    const morph = MORPHS[morphName], pose = POSES[poseName];
    const m = { em: morph.em, ew: morph.ew, tl: morph.bob ? Math.min(morph.tl, 0.3) : morph.tl, lg: morph.lg, bl: morph.bl, wk: morph.wk };
    const r = petRigCompose(petRigSrcPixels(sh, meta.row, frame, W, H), fr, m, pose, W, H);
    if (r.CW * r.CH !== 224 * 100) throw new Error('unexpected canvas ' + r.CW + 'x' + r.CH);
    cells.push(r.px);
    cases.push({ kind, coat, anim, frame, pose: pose || { ear0: 0, ear1: 0, flat: 0, tail: 0, tw: 0 }, morph: { em: morph.em, ew: morph.ew, tl: morph.tl, lg: morph.lg, bl: morph.bl, wk: morph.wk, bob: !!morph.bob }, cell: cells.length - 1 });
  };
  const adult = Object.keys(A.anims), five = ['neutral', 'flat', 'swivel', 'tailUp', 'tailDown'];
  for (const anim of adult) {
    const n = A.anims[anim].n;
    for (const f of [0, Math.floor(n / 2)]) for (const pn of five) run('adult', 'ginger', anim, f, pn, 'extreme');
    for (const mn of ['small', 'bob']) for (const pn of ['neutral', 'mixed']) run('adult', 'ginger', anim, 0, pn, mn);
  }
  for (const coat of ['gray', 'black', 'tuxedo']) for (const anim of ['idle_sit', 'walk', 'loaf', 'sleep', 'groom']) for (const pn of ['neutral', 'mixed']) run('adult', coat, anim, 0, pn, 'extreme');
  for (const anim of ['idle_sit', 'walk', 'loaf', 'sleep', 'groom']) for (const pn of ['neutral', 'mixed']) { run('st2', 'gray', anim, 0, pn, 'extreme'); run('st2', 'gray', anim, 1, pn, 'small') }
  for (const anim of Object.keys(A.depth.anims)) {
    for (const pn of ['neutral', 'flat', 'tailUp']) { run('depth', 'ginger', anim, 0, pn, 'extreme'); run('dst2', 'gray', anim, 0, pn, 'extreme') }
    run('depth', 'tuxedo', anim, 1, 'mixed', 'small');
  }
  const out = [];
  for (const px of cells) {
    const u8 = new Uint8Array(px.buffer, px.byteOffset, px.byteLength); let s = '';
    for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000));
    out.push(btoa(s));
  }
  return { cases, cells: out };
};

(async () => {
  const b = await chromium.launch(), pg = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(), errs = [];
  pg.on('pageerror', (e) => errs.push(String(e)));
  await pg.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await pg.goto('file://' + file); await pg.waitForTimeout(900);
  const rec = await pg.evaluate(RECORD, { POSES, MORPHS, COLUMNS });
  await b.close();
  if (errs.length) { console.error(errs.join('\n')); process.exit(1) }
  const CW = 224, CH = 100, rows = Math.ceil(rec.cells.length / COLUMNS), W = CW * COLUMNS, H = CH * rows, data = Buffer.alloc(W * H * 4);
  rec.cells.forEach((b64, i) => {
    const cell = Buffer.from(b64, 'base64'), ox = (i % COLUMNS) * CW, oy = Math.floor(i / COLUMNS) * CH;
    for (let y = 0; y < CH; y++) cell.copy(data, ((oy + y) * W + ox) * 4, y * CW * 4, (y + 1) * CW * 4);
  });
  fs.rmSync(target, { recursive: true, force: true }); fs.mkdirSync(target, { recursive: true });
  const buf = png.encode({ width: W, height: H, channels: 4, data });
  fs.writeFileSync(path.join(target, 'atlas.png'), buf);
  fs.writeFileSync(path.join(target, 'cases.json'), JSON.stringify({ columns: COLUMNS, cellWidth: CW, cellHeight: CH, cases: rec.cases }) + '\n');
  console.log('wrote', rec.cases.length, 'cases,', buf.length, 'png bytes');
})();
