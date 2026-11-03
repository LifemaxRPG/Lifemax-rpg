/* Fingerprints of the page's own frames, so a lab build can prove it never touched them.
   frameHash: SHA-256 over the frame's palette indices for ginger, gray, black and tuxedo in that order (72 x 48 bytes each,
   row by row), first 16 hex digits. The same rule is used by the Kotlin test (feature/pet/art/LabArtTest) on the shipped sheets.
   The record lives in android-wrapper/native/src/test/resources/pet-lab/base-frames.json: { kind: { row name: [hash per frame] } }
   for every kind (adult, st0..st5, depth, dst0..dst5) and every base row. It was written once, before the first lab row existed. */
'use strict';
const crypto = require('crypto'), fs = require('fs'), path = require('path');
const { W, H, BASE_ROWS, frameOf, ROOT } = require('./data.js');

const RECORD = path.resolve(ROOT, '../android-wrapper/native/src/test/resources/pet-lab/base-frames.json');

function frameHash(fr) {
  const h = crypto.createHash('sha256');
  for (let c = 0; c < 4; c++) h.update(Buffer.from(fr.coat(c)));
  return h.digest('hex').slice(0, 16);
}

/** { kind: { row: [hash...] } } for the base rows of every kind */
function baseHashes(kinds) {
  const out = {};
  for (const [name, k] of Object.entries(kinds)) {
    const rows = name === 'depth' || name.startsWith('dst') ? Object.keys(k.anims) : BASE_ROWS;
    out[name] = {};
    for (const r of rows) {
      const a = k.anims[r]; out[name][r] = [];
      for (let f = 0; f < a.n; f++) out[name][r].push(frameHash(frameOf(k.sheet, a.row, f)));
    }
  }
  return out;
}

function writeRecord(kinds) {
  fs.mkdirSync(path.dirname(RECORD), { recursive: true });
  const h = baseHashes(kinds);
  const text = '{\n' + Object.entries(h).map(([k, rows]) => ' ' + JSON.stringify(k) + ': {\n' +
    Object.entries(rows).map(([r, l]) => '  ' + JSON.stringify(r) + ': ' + JSON.stringify(l)).join(',\n') + '\n }').join(',\n') + '\n}\n';
  fs.writeFileSync(RECORD, text);
}

/** the base frames whose fingerprint differs from the record (empty when untouched) */
function compareRecord(kinds) {
  const want = JSON.parse(fs.readFileSync(RECORD, 'utf8')), have = baseHashes(kinds), bad = [];
  for (const k of Object.keys(want)) for (const r of Object.keys(want[k])) want[k][r].forEach((h, f) => {
    if (!have[k] || !have[k][r] || have[k][r][f] !== h) bad.push(`${k} ${r}#${f}`);
  });
  return bad;
}

/* The toolchain's reference for the lab frames, for the native recolour to be checked against (LabArtTest): per kind and lab
   row, per frame, SHA-256 over the frame recoloured in ginger, gray, black and tuxedo in that order, each as R, G, B, A bytes per
   pixel row by row (a clear pixel, or an index the coat has no colour for, is 0, 0, 0, 0), first 16 hex digits. */
const LAB_REFERENCE = path.resolve(ROOT, '../android-wrapper/native/src/test/resources/pet-lab/lab-frames.json');
const COAT_NAMES = ['ginger', 'gray', 'black', 'tuxedo'];

function rgbaHash(fr, pal) {
  const h = crypto.createHash('sha256');
  COAT_NAMES.forEach((name, c) => {
    const idx = fr.coat(c), out = Buffer.alloc(idx.length * 4), p = pal[name];
    for (let i = 0; i < idx.length; i++) { const col = idx[i] && p[String(idx[i])]; if (col) { out[i * 4] = col[0]; out[i * 4 + 1] = col[1]; out[i * 4 + 2] = col[2]; out[i * 4 + 3] = 255 } }
    h.update(out);
  });
  return h.digest('hex').slice(0, 16);
}

/** the reference text for a build (lab.js buildLab output) and the page palettes */
function labReference(L, pal) {
  const kinds = Object.keys(L.frames);
  return '{\n' + kinds.map((k) => ' ' + JSON.stringify(k) + ': {\n' + Object.entries(L.frames[k]).map(([r, fr]) =>
    '  ' + JSON.stringify(r) + ': ' + JSON.stringify(fr.map((b) => rgbaHash(b.plate, pal)))).join(',\n') + '\n }').join(',\n') + '\n}\n';
}

module.exports = { frameHash, baseHashes, writeRecord, compareRecord, labReference, RECORD, LAB_REFERENCE, W, H };
