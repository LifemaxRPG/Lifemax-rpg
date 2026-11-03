/* The room furniture layers of the native cat room (CT9): the painted backdrops cut into a base and one sprite per furniture piece.
   The work is done by pet_room_cut.js (the algorithm) and pet_room_cut_spec.js (which pixels are which piece, and how the wall and
   floor behind each piece are rebuilt); gen_pet_native_assets.js writes the files with all the other native pet art, so its --check
   in CI covers these too. This script is the same step on its own, with a way to look at the result:
     node tools/gen_pet_room_layers.js             write android-wrapper/native/src/main/resources/pet/room/layers*
     node tools/gen_pet_room_layers.js --check     fail if the committed layers differ from what the page's backdrops give, or if
                                                   a room's pieces drawn over its base are not the backdrop exactly
     node tools/gen_pet_room_layers.js --preview DIR
                                                   DIR/<room>.png: the backdrop | the base | the pieces, 4x, and the report below
   The report lists, per room, each piece's box and the number of pixels it rebuilt behind it. */
const fs = require('fs'), path = require('path');
const png = require('./pet_png.js');
const { ROOMS, cutRoom, compose, layerFiles } = require('./pet_room_cut.js');

const root = path.resolve(__dirname, '..');
const target = path.resolve(root, '../android-wrapper/native/src/main/resources/pet');
const SCALE = 4;

function backdrops() {
  const html = fs.readFileSync(path.join(root, 'src/data/petAssets.html'), 'utf8');
  const room = JSON.parse(/<script[^>]*>([\s\S]*)<\/script>/.exec(html)[1]).room;
  const read = (u) => png.decode(Buffer.from(u.slice(u.indexOf(',') + 1), 'base64'));
  return { living: read(room.living), bedroom: read(room.bg), kitchen: read(room.kitchen) };
}

/* the backdrop, the base and the pieces side by side on a checkerboard, SCALE times */
function sheet(img, base, pieces) {
  const W = img.width, H = img.height, gap = 6, OW = (W * 3) * SCALE + gap * 2, out = Buffer.alloc(OW * H * SCALE * 4);
  const only = { width: W, height: H, data: Buffer.alloc(W * H * 4) };
  for (const p of pieces) for (let y = 0; y < p.height; y++) for (let x = 0; x < p.width; x++) {
    const s = (y * p.width + x) * 4;
    if (p.data[s + 3]) only.data.set(p.data.subarray(s, s + 4), ((p.y + y) * W + p.x + x) * 4);
  }
  [img, base, only].forEach((im, n) => {
    for (let y = 0; y < H * SCALE; y++) for (let x = 0; x < W * SCALE; x++) {
      const s = ((y / SCALE | 0) * W + (x / SCALE | 0)) * 4, a = im.data[s + 3] / 255, o = (y * OW + n * (W * SCALE + gap) + x) * 4;
      const check = ((x >> 3) + (y >> 3)) & 1 ? 200 : 235;
      for (let c = 0; c < 3; c++) out[o + c] = Math.round(im.data[s + c] * a + check * (1 - a));
      out[o + 3] = 255;
    }
  });
  return png.encode({ width: OW, height: H * SCALE, channels: 4, data: out });
}

function report(name, img, base, pieces) {
  let rebuilt = 0;
  for (let i = 0; i < img.data.length; i += 4) if (img.data[i] !== base.data[i] || img.data[i + 1] !== base.data[i + 1] || img.data[i + 2] !== base.data[i + 2]) rebuilt++;
  console.log(`${name}: ${rebuilt} pixels differ between the backdrop and its base`);
  for (const p of pieces) console.log(`  ${p.name.padEnd(22)} ${p.width}x${p.height} at ${p.x},${p.y}  base ${p.base}`);
}

const imgs = backdrops(), files = layerFiles(imgs, png), arg = process.argv[2];
const same = (a, b) => { try { const p = png.decode(a), q = png.decode(b); return p.width === q.width && p.height === q.height && p.data.equals(q.data) } catch (e) { return false } };
if (arg === '--check') {
  const stale = [];
  for (const [name, want] of files) {
    const file = path.join(target, name), have = fs.existsSync(file) ? fs.readFileSync(file) : null;
    if (!have || !(name.endsWith('.png') ? same(have, want) : have.equals(want))) stale.push(name);
  }
  if (stale.length) { console.error('FAIL: the room layers are out of date: ' + stale.join(', ') + '\n  run: node lifemax_modular_v73/tools/gen_pet_room_layers.js'); process.exit(1) }
  console.log('ok: ' + files.size + ' room layer files are current, and every room reassembles exactly');
} else if (arg === '--preview') {
  const dir = path.resolve(process.argv[3] || '.');
  fs.mkdirSync(dir, { recursive: true });
  for (const name of Object.keys(ROOMS)) {
    const { base, pieces } = cutRoom(imgs[name], ROOMS[name]);
    report(name, imgs[name], base, pieces);
    fs.writeFileSync(path.join(dir, name + '.png'), sheet(imgs[name], base, pieces));
    const back = compose(base, pieces);
    if (!back.data.equals(Buffer.from(imgs[name].data))) throw new Error(name + ' does not reassemble');
  }
} else {
  for (const [name, buf] of files) {
    fs.mkdirSync(path.dirname(path.join(target, name)), { recursive: true });
    fs.writeFileSync(path.join(target, name), buf);
  }
  console.log('wrote ' + files.size + ' room layer files to ' + path.relative(process.cwd(), target));
}
