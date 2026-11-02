/* Records what the page draws of the empty room, for the native room painter to be tested against (feature/pet/presentation,
   RoomFidelityTest). The page is run, not read: petInit() loads the room art, then petDraw() is called with no cat, at a fixed
   hour and a fixed room clock, and the canvas it paints is written out as an RGBA PNG: the whole house, 576 x 144.
   The page's room canvas is 4x the house (PET_OS = 4, so a scaled cat stays crisp). The native room is drawn at the card's own
   whole-number scale, so the page is asked for the same picture at 1x (PET_OS = 1 for this one draw): the same code, the same
   order, the same colours, only without the finer grid. Nothing else is changed in the page.
   Output in android-wrapper/native/src/test/resources/pet-room/:
     house-<hour>.png   the empty house at that hour (6, 12, 18.75, 20.5, 23), the room clock at 12.34 s
     web-room.json      the hours, the clock, and the page's own tables: sky stops, window offsets, tint at every hour sampled, bowls
   Run: NODE_PATH=$(npm root -g) node tools/gen_pet_room_fixtures.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const png = require('./pet_png.js');
const file = require('../tests/current-page')(process.argv[2]);
const target = path.resolve(__dirname, '../../android-wrapper/native/src/test/resources/pet-room');
const HOURS = [6, 12, 18.75, 20.5, 23];
const CLOCK = 12.34;
const TINT_SAMPLES = [0, 4.9, 5, 6.25, 7.49, 7.5, 12, 16.99, 17, 18.25, 19.49, 19.5, 20.5, 21.49, 21.5, 23, 23.99];
const SKY_SAMPLES = [0, 2.5, 5, 5.5, 6.2, 7, 8, 10, 12, 14.5, 17, 18, 18.8, 19.6, 20.5, 21.25, 22, 23, 24];

/* in the page: the empty house at each hour, as base64 RGBA, and the page's tables */
const RECORD = async ({ HOURS, CLOCK, TINT_SAMPLES, SKY_SAMPLES }) => {
  delete state.pet; delete state.pet2;
  const rt = await petInit();
  await new Promise((ok) => setTimeout(ok, 600)); /* the depth props are plain images: let them finish */
  const cv = document.createElement('canvas'); cv.width = PET_HW; cv.height = 144;
  const saved = { off: rt.off, octx: rt.octx, os: PET_OS, hour: petHour, t: rt.t };
  rt.off = cv; rt.octx = cv.getContext('2d', { willReadFrequently: true }); rt.octx.imageSmoothingEnabled = false;
  PET_OS = 1; rt.t = CLOCK; rt.bowl.food = false; rt.ball.on = false; rt.gift = null; rt.parts = []; rt.bubble = null;
  const out = { houses: {}, tables: {} };
  for (const h of HOURS) {
    petHour = function () { return h };
    petDraw(rt);
    const d = rt.octx.getImageData(0, 0, PET_HW, 144).data; let s = '';
    for (let i = 0; i < d.length; i += 0x8000) s += String.fromCharCode.apply(null, d.subarray(i, i + 0x8000));
    out.houses[h] = btoa(s);
  }
  rt.off = saved.off; rt.octx = saved.octx; PET_OS = saved.os; petHour = saved.hour; rt.t = saved.t;
  const A = rt.A, P = A.depth.props;
  out.tables = {
    sky: PET_SKY, windows: PET_WINDOWS, bowl: PET_BOWL, houseX0: PET_HX0, houseWidth: PET_HW,
    tint: TINT_SAMPLES.map((h) => [h, petTint(h)]),
    skyAt: SKY_SAMPLES.map((h) => [h, petSkyAt(h)]),
    props: PET_PROPS.map((pr) => ({ k: pr.k, x: pr.x, z: pr.z, parts: pr.parts || null })),
    propSizes: Object.fromEntries(Object.keys(P).map((k) => [k, [P[k].w, P[k].h]])),
    roomMeta: A.room.meta,
  };
  return out;
};

(async () => {
  const b = await chromium.launch(), p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(), errs = [];
  p.on('pageerror', (e) => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const rec = await p.evaluate(RECORD, { HOURS, CLOCK, TINT_SAMPLES, SKY_SAMPLES });
  await b.close();
  if (errs.length) { console.error(errs.join('\n')); process.exit(1) }
  fs.rmSync(target, { recursive: true, force: true }); fs.mkdirSync(target, { recursive: true });
  for (const h of HOURS) {
    const buf = png.encode({ width: 576, height: 144, channels: 4, data: Buffer.from(rec.houses[h], 'base64') });
    fs.writeFileSync(path.join(target, 'house-' + h + '.png'), buf); console.log('wrote house-' + h + '.png', buf.length);
  }
  fs.writeFileSync(path.join(target, 'web-room.json'), JSON.stringify({ hours: HOURS, clock: CLOCK, tables: rec.tables }, null, 1) + '\n');
})();
