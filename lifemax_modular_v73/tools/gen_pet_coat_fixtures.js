/* Records what the page hands native for genetic coats and what the room draws from it, for the native recolour to be tested against
   (feature/pet/art, CoatFidelityTest; pet engine phase P3b). The page is run, not read, for coats chosen to cover the composer's
   branches: a tortoiseshell with white spotting (calico), a colour-point, a long-haired white-spotted red tabby, a classic silver tabby.
   For each coat and sheet kind (adult, depth, st1: a kitten's side view) it writes
     <label>-<kind>.reply.json   the page's answer to the cards' `coat {key, kind}` command, exactly (what native decodes)
     <label>-<kind>.png          the room's own recoloured canvas for that sheet (rt.sheets / rt.dsheets / rt.gs after petDnaEnsureSheets /
                                 petLoadStage: the pixels the room draws the cat from), RGBA, a clear pixel 0,0,0,0
   plus coats.json (label -> key). Output in android-wrapper/native/src/test/resources/pet-coat/.
   Run: NODE_PATH=$(npm root -g) node tools/gen_pet_coat_fixtures.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const png = require('./pet_png.js');
const file = require('../tests/current-page')(process.argv[2]);
const target = path.resolve(__dirname, '../../android-wrapper/native/src/test/resources/pet-coat');
/* dna_<base>_<tortie>_<tab m|c|t|n>_<white 0-4>_<silver>_<point n|cs|mk|sp>_<long>_<eye> */
const COATS = { calico: 'dna_black_1_n_2_0_n_0_3', point: 'dna_black_0_n_0_0_cs_0_4', longwhite: 'dna_red_0_m_3_0_n_1_2', silver: 'dna_blue_0_c_0_1_n_0_1' };
const KINDS = ['adult', 'depth', 'st1'];

const RECORD = async ({ COATS, KINDS }) => {
  const rt = await petInit(); petAdopt('ginger', 'Mo', 'playful');
  const grab = (cv) => {
    const d = cv.getContext('2d').getImageData(0, 0, cv.width, cv.height).data; let s = '';
    for (let i = 0; i < d.length; i += 0x8000) s += String.fromCharCode.apply(null, d.subarray(i, i + 0x8000));
    return { w: cv.width, h: cv.height, b64: btoa(s) };
  };
  const wait = (f) => new Promise((ok) => { const t0 = performance.now(); const look = () => { const v = f(); if (v || performance.now() - t0 > 10000) ok(v); else setTimeout(look, 30) }; look() });
  const out = {};
  for (const [label, key] of Object.entries(COATS)) {
    petDnaRegister(key); state.pet.coat = key;
    await petDnaEnsureSheets(rt, key); await wait(() => rt.sheets[key] && rt.dsheets[key]);
    await petLoadStage(rt, 1); await wait(() => rt.gs['1|' + key]);
    for (const kind of KINDS) {
      const reply = await NativeOverviewCardsProjection.commands.coat({ key, kind });
      const cv = kind === 'adult' ? rt.sheets[key] : kind === 'depth' ? rt.dsheets[key] : rt.gs['1|' + key].cv;
      out[label + '-' + kind] = { reply: Object.assign({ command: 'coat', requestId: 'req-1' }, reply), room: grab(cv) };
    }
  }
  return out;
};

(async () => {
  const b = await chromium.launch(), p = await (await b.newContext({ viewport: { width: 393, height: 844 } })).newPage(), errs = [];
  p.on('pageerror', (e) => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file); await p.waitForTimeout(900);
  const rec = await p.evaluate(RECORD, { COATS, KINDS });
  await b.close();
  if (errs.length) { console.error(errs.join('\n')); process.exit(1) }
  fs.rmSync(target, { recursive: true, force: true }); fs.mkdirSync(target, { recursive: true });
  let total = 0;
  for (const [name, r] of Object.entries(rec)) {
    if (!r.reply.ok) throw new Error('the page refused ' + name + ': ' + r.reply.message);
    const reply = JSON.stringify(r.reply), rgba = png.encode({ width: r.room.w, height: r.room.h, channels: 4, data: Buffer.from(r.room.b64, 'base64') });
    fs.writeFileSync(path.join(target, name + '.reply.json'), reply + '\n'); fs.writeFileSync(path.join(target, name + '.png'), rgba);
    total += reply.length + rgba.length; console.log('wrote ' + name, reply.length, rgba.length);
  }
  fs.writeFileSync(path.join(target, 'coats.json'), JSON.stringify(COATS, null, 1) + '\n');
  console.log('total bytes', total);
})();
