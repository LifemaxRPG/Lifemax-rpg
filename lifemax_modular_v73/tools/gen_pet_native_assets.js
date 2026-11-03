/* The native cat's art, cut out of the page's embedded data (src/data/petAssets.html and petGrowth.html), so the native engine
   draws the same pixels the web room does. Nothing here is drawn or recoloured: the sheets stay palette-index images, the
   palettes and tables are copied across, and the Kotlin side (feature/pet/art) recolours them exactly as petInit does.
   Written to android-wrapper/native/src/main/resources/pet/:
     anims.json          the 28 adult rows and the 4 depth rows: name, sheet row, frame count, fps, foot row (bottom); frame size, columns
     growth.json         the six kitten stages: first day, age fraction, scale sx, the bottom table of each (a stage has no 'scratch' row)
     palettes.json       index -> RGB per legacy coat (ginger, gray, black, tuxedo); index 0 and any index not listed is transparent
     room.json           the room item atlas: name -> [x, y, w, h]
     sheets/adult-<coat>.png, sheets/depth-<coat>.png, sheets/stage<i>-<coat>.png, sheets/dstage<i>-<coat>.png
                         grey-index sheets (8-bit grey, the index is the pixel value), re-encoded losslessly and smaller
     room/items.png      the room item atlas, RGBA, byte-for-byte pixels of the page's
     room/living.png, room/bedroom.png, room/kitchen.png
                         the three painted backdrops, RGBA 192 x 144: the living room (house x 0), the bedroom (192), the kitchen (384)
     room/layers.json, room/layers/<room>_base.png, room/layers/<piece>.png
                         the furniture cut out of the three backdrops (tools/pet_room_cut.js, spec pet_room_cut_spec.js): each room's
                         backdrop with the furniture removed and the wall and floor rebuilt, one sprite per piece, and the manifest
                         (place, depth, flags). The pieces drawn over the base give the backdrop back exactly; the build checks it.
     room/props/<name>.png  the free-standing depth props the room draws between the backdrop and the cats: pouf, cushion, and the
                         two halves of the box (box_back, box_front)
   Out of scope (later phases): HD art (petHd.html), the rig (petRig.html), toys other than the box, DNA coats.
   Run:   node tools/gen_pet_native_assets.js           writes the files (deterministic; running it twice changes nothing)
          node tools/gen_pet_native_assets.js --check   fails if the committed files differ from what the page data gives (CI) */
const fs = require('fs'), path = require('path');
const png = require('./pet_png.js');
const { layerFiles } = require('./pet_room_cut.js');

const root = path.resolve(__dirname, '..');
const target = path.resolve(root, '../android-wrapper/native/src/main/resources/pet');
const COATS = ['ginger', 'gray', 'black', 'tuxedo'];
const ROOM_W = 192, ROOM_H = 144; /* each painted backdrop: three of them side by side make the 576 x 144 house */
const PROPS = ['pouf', 'cushion', 'box_back', 'box_front'];
const GROW_DAYS = 30; /* PET_GROW_DAYS in src/runtime/segments/16-runtime.js: a kitten is grown at 30 days; stage i covers days 5i..5i+4 */

function embedded(name) {
  const html = fs.readFileSync(path.join(root, 'src/data', name + '.html'), 'utf8');
  const m = /<script[^>]*>([\s\S]*)<\/script>/.exec(html);
  if (!m) throw new Error(name + ': no script element');
  return JSON.parse(m[1]);
}
const dataUrl = (u) => Buffer.from(u.slice(u.indexOf(',') + 1), 'base64');
const json = (v) => Buffer.from(JSON.stringify(v, null, 1) + '\n');

function checkedSheet(label, url, width, height, palette) {
  const img = png.decode(dataUrl(url));
  if (img.channels !== 1) throw new Error(label + ': not a grey-index sheet');
  if (img.width !== width || img.height !== height) throw new Error(`${label}: ${img.width}x${img.height}, expected ${width}x${height}`);
  for (const v of img.data) if (v !== 0 && !palette[String(v)]) throw new Error(`${label}: index ${v} has no colour (the page draws it clear)`);
  return png.encode(img);
}

function build() {
  const A = embedded('petAssets'), G = embedded('petGrowth'), files = new Map();
  const { W, H, cols } = A, D = A.depth;
  const rowsOf = (anims) => Object.entries(anims).map(([name, a]) => ({ name, row: a.row, frames: a.n, fps: a.fps, bottom: a.bottom }));
  const adult = rowsOf(A.anims), depth = rowsOf(D.anims);
  const adultRows = adult.length, depthRows = depth.length, depthCols = Math.round(D.cols || 6);
  adult.forEach((a, i) => { if (a.row !== i) throw new Error('adult rows are not 0..n in order at ' + a.name) });
  depth.forEach((a, i) => { if (a.row !== i) throw new Error('depth rows are not 0..n in order at ' + a.name) });
  if (D.W !== W || D.H !== H) throw new Error('depth frame size differs from the adult one');

  const table = (rows) => '[\n' + rows.map((r) => ' ' + JSON.stringify(r)).join(',\n') + '\n]';
  files.set('anims.json', Buffer.from(`{"frameWidth":${W},"frameHeight":${H},"adultColumns":${cols},"depthColumns":${depthCols},\n"adult":${table(adult)},\n"depth":${table(depth)}}\n`));

  const palettes = {};
  for (const coat of COATS) {
    palettes[coat] = {};
    for (const [k, c] of Object.entries(A.pal[coat])) palettes[coat][k] = c.map((v) => v.toString(16).padStart(2, '0')).join('');
  }
  files.set('palettes.json', json(palettes));

  const stages = G.stages.map((s, i) => {
    if (G.days[i] !== i * 5 || Math.abs(s.a - G.days[i] / GROW_DAYS) > 1e-9) throw new Error('stage ' + i + ' is not at day 5i');
    if (D.stages[i].sx !== s.sx) throw new Error('stage ' + i + ': depth sx differs from the adult one');
    return { index: i, fromDay: G.days[i], age: s.a, scale: s.sx, bottom: s.bottom };
  });
  files.set('growth.json', json({ growDays: GROW_DAYS, stages }));
  files.set('room.json', json(A.room.meta));

  const dims = { adult: [W * cols, H * adultRows], depth: [D.W * depthCols, H * depthRows] };
  for (const coat of COATS) {
    const pal = A.pal[coat];
    files.set(`sheets/adult-${coat}.png`, checkedSheet('adult ' + coat, A.coats[coat], ...dims.adult, pal));
    files.set(`sheets/depth-${coat}.png`, checkedSheet('depth ' + coat, D.coats[coat], ...dims.depth, pal));
    G.stages.forEach((s, i) => files.set(`sheets/stage${i}-${coat}.png`, checkedSheet(`stage${i} ${coat}`, s.coats[coat], ...dims.adult, pal)));
    D.stages.forEach((s, i) => files.set(`sheets/dstage${i}-${coat}.png`, checkedSheet(`dstage${i} ${coat}`, s.coats[coat], ...dims.depth, pal)));
  }
  const items = png.decode(dataUrl(A.room.items));
  if (items.channels !== 4) throw new Error('the room atlas is not RGBA');
  for (const [name, [x, y, w, h]] of Object.entries(A.room.meta)) if (x + w > items.width || y + h > items.height) throw new Error(name + ' lies outside the atlas');
  files.set('room/items.png', png.encode(items));
  for (const [file, key] of [['living', 'living'], ['bedroom', 'bg'], ['kitchen', 'kitchen']]) {
    const img = png.decode(dataUrl(A.room[key]));
    if (img.channels !== 4 || img.width !== ROOM_W || img.height !== ROOM_H) throw new Error(`backdrop ${file}: ${img.width}x${img.height}x${img.channels}, expected ${ROOM_W}x${ROOM_H} RGBA`);
    files.set(`room/${file}.png`, png.encode(img));
  }
  const backdrops = {};
  for (const name of ['living', 'bedroom', 'kitchen']) backdrops[name] = png.decode(files.get(`room/${name}.png`));
  for (const [name, buf] of layerFiles(backdrops, png)) files.set(name, buf);
  const props = Object.assign({}, D.props, { box_back: D.toys.box_back, box_front: D.toys.box_front });
  for (const name of PROPS) {
    const p = props[name]; if (!p) throw new Error('missing depth prop ' + name);
    const img = png.decode(dataUrl(p.url));
    if (img.channels !== 4 || img.width !== p.w || img.height !== p.h) throw new Error(`prop ${name}: ${img.width}x${img.height}, the page says ${p.w}x${p.h}`);
    files.set(`room/props/${name}.png`, png.encode(img));
  }
  return files;
}

/* two PNG files are the same when they decode to the same pixels (the compressor may differ between zlib versions) */
function samePixels(a, b) {
  try { const p = png.decode(a), q = png.decode(b); return p.width === q.width && p.height === q.height && p.channels === q.channels && p.data.equals(q.data) } catch (e) { return false }
}
function listFiles(dir, rel = '') {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => e.isDirectory() ? listFiles(path.join(dir, e.name), rel + e.name + '/') : [rel + e.name]);
}

const files = build();
if (process.argv.includes('--check')) {
  const stale = [];
  for (const [name, want] of files) {
    const have = fs.existsSync(path.join(target, name)) ? fs.readFileSync(path.join(target, name)) : null;
    if (!have || !(name.endsWith('.png') ? samePixels(have, want) : have.equals(want))) stale.push(name);
  }
  for (const name of listFiles(target)) if (!files.has(name)) stale.push(name + ' (not generated any more)');
  if (stale.length) { console.error('FAIL: the native pet assets are out of date: ' + stale.join(', ') + '\n  run: node lifemax_modular_v73/tools/gen_pet_native_assets.js'); process.exit(1) }
  console.log('ok: ' + files.size + ' native pet assets are current');
} else {
  for (const name of listFiles(target)) if (!files.has(name)) fs.unlinkSync(path.join(target, name));
  let total = 0;
  for (const [name, buf] of files) {
    fs.mkdirSync(path.dirname(path.join(target, name)), { recursive: true });
    if (!fs.existsSync(path.join(target, name)) || !fs.readFileSync(path.join(target, name)).equals(buf)) fs.writeFileSync(path.join(target, name), buf);
    total += buf.length;
  }
  console.log('wrote ' + files.size + ' files, ' + total + ' bytes to ' + path.relative(process.cwd(), target));
}
