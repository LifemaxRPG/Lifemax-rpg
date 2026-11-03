/* Reading and writing the cat art the page embeds (src/data/petAssets.html, petGrowth.html, petRig.html).
   The sheets are 8-bit grey PNG data URLs whose pixel value is the palette index; every frame is 72 x 48, eight frames a row.
   A "kind" is one family of sheets in the same layout: adult (the grown cat's side view), st0..st5 (the kitten stages' side
   views, own art), depth (front and back views), dst0..dst5. Each kind has one sheet per legacy coat; loadKind packs the four into
   one Plate (see plate.js).
   The first 28 adult rows (BASE_ROWS, the page's own animations) are the source art and are never written. The lab rows the
   toolchain makes are appended after them, in the same sheets, so every coat (and every DNA coat the page composes from these
   sheets) has them. */
'use strict';
const fs = require('fs'), path = require('path');
const png = require('../../pet_png.js');
const { Plate, COATS, packCoats } = require('./plate.js');

const ROOT = path.resolve(__dirname, '../../..');
const DATA = path.join(ROOT, 'src/data');
const W = 72, H = 48, COLS = 8;
/** the page's own rows, in sheet order; the toolchain reads them and never writes them */
const BASE_ROWS = ['idle_sit', 'walk', 'run', 'sleep', 'loaf', 'groom', 'eat', 'play', 'stretch', 'lookup', 'slowblink', 'purr', 'rub',
  'knead', 'meow', 'leap', 'idle_sit_w', 'walk_w', 'loaf_w', 'sleep_w', 'crouch', 'scratch', 'scratch_in', 'scratch_out', 'roll', 'kick',
  'bat', 'swat'];
const STAGES = 6;

function readBlock(name) {
  const file = path.join(DATA, name + '.html'), text = fs.readFileSync(file, 'utf8');
  const m = /^(<script[^>]*>)([\s\S]*)(<\/script>\s*)$/.exec(text);
  if (!m) throw new Error(name + ': not a single embedded JSON script');
  return { file, head: m[1], body: m[2], tail: m[3], json: JSON.parse(m[2]) };
}
const dataUrl = (u) => Buffer.from(u.slice(u.indexOf(',') + 1), 'base64');
const toDataUrl = (buf) => 'data:image/png;base64,' + buf.toString('base64');

function decodeIndex(url, label) {
  const img = png.decode(dataUrl(url));
  if (img.channels !== 1) throw new Error(label + ': not a grey-index sheet');
  return img;
}
function encodeIndex(w, h, idx) { return toDataUrl(png.encode({ width: w, height: h, channels: 1, data: Buffer.from(idx) }, { filter: 0 })) }

/** the four coats' sheets of one kind, packed: { sheet: Plate, rows: n } */
function packSheet(coats, label) {
  const imgs = COATS.map((c) => decodeIndex(coats[c], label + ' ' + c));
  const { width, height } = imgs[0];
  if (imgs.some((i) => i.width !== width || i.height !== height)) throw new Error(label + ': the coats differ in size');
  return packCoats(width, height, imgs.map((i) => i.data));
}

/** everything the toolchain reads, decoded */
function load() {
  const assets = readBlock('petAssets'), growth = readBlock('petGrowth'), rig = readBlock('petRig');
  const A = assets.json, G = growth.json, R = rig.json;
  if (A.W !== W || A.H !== H || A.cols !== COLS) throw new Error('petAssets frame layout changed');
  BASE_ROWS.forEach((n, i) => { if (!A.anims[n] || A.anims[n].row !== i) throw new Error('base row ' + n + ' is not at ' + i) });
  const kinds = { adult: { sheet: packSheet(A.coats, 'adult'), anims: A.anims, bottom: null, sx: 1, rig: R.adult } };
  G.stages.forEach((s, i) => { kinds['st' + i] = { sheet: packSheet(s.coats, 'st' + i), anims: A.anims, bottom: s.bottom, sx: s.sx, rig: R['st' + i] } });
  kinds.depth = { sheet: packSheet(A.depth.coats, 'depth'), anims: A.depth.anims, bottom: null, sx: 1, rig: R.depth };
  A.depth.stages.forEach((s, i) => { kinds['dst' + i] = { sheet: packSheet(s.coats, 'dst' + i), anims: A.depth.anims, bottom: null, sx: s.sx, rig: R['dst' + i] } });
  return { assets, growth, A, G, R, kinds };
}

/** frame f of row r of a packed sheet */
function frameOf(sheet, row, f) { return sheet.crop(f * W, row * H, W, H) }

/** the base sheet (rows 0..27 only) of a kind, so a rebuild never reads its own earlier output */
function baseSheet(kind) { return kind.sheet.crop(0, 0, kind.sheet.w, BASE_ROWS.length * H) }

/** a new sheet: the base rows, then each lab row's frames (a list of { name, frames: [Plate] }) */
function assemble(base, labRows) {
  const out = new Plate(base.w, base.h + labRows.length * H);
  out.blit(base, 0, 0);
  labRows.forEach((r, i) => r.frames.forEach((fr, f) => out.blit(fr, f * W, base.h + i * H)));
  return out;
}

/** a packed sheet back to the four coats' data URLs */
function unpackSheet(sheet) {
  const out = {};
  COATS.forEach((c, i) => { out[c] = encodeIndex(sheet.w, sheet.h, sheet.coat(i)) });
  return out;
}

function writeBlock(block, json) { fs.writeFileSync(block.file, block.head + JSON.stringify(json) + block.tail) }

module.exports = { ROOT, DATA, W, H, COLS, BASE_ROWS, STAGES, load, frameOf, baseSheet, assemble, unpackSheet, writeBlock, readBlock, decodeIndex };
