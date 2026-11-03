/* The cat animation lab: builds the new animation rows from the page's own frames and writes them into the page's art data.
   docs/specs/PET_ART_LAB.md explains the method, the pose-spec format and the quality checklist.
     node tools/pet_art/build.js              rebuilds every lab row from tools/pet_art/poses and writes src/data/petAssets.html
                                              (the four coat sheets grow by the lab rows; anims gains the rows after the page's 28;
                                              petAssets.lab holds their metadata) and src/data/petGrowth.html (the kitten stage
                                              sheets and bottom tables). Deterministic: a second run changes nothing.
     node tools/pet_art/build.js --check      fails if the committed data differs from a rebuild, or if any of the page's own frames
                                              differs from its fingerprint (android-wrapper/native/src/test/resources/pet-lab/
                                              base-frames.json). CI runs this.
     node tools/pet_art/build.js --sheets DIR writes contact sheets to DIR: <row>.png (the adult in all four coats, then every
                                              kitten stage in ginger, 6x, foot line green, mouth anchor red) and flipbook.png
                                              (every lab row of the adult ginger cat), plus qa.txt. Look at them before committing.
     node tools/pet_art/build.js --qa         prints the QA findings (frames that touch the edge, new open outline, head cuts that
                                              do not reassemble), exit 0.
   After a write run, regenerate the native assets: node tools/gen_pet_native_assets.js */
'use strict';
const fs = require('fs'), path = require('path');
const data = require('./lib/data.js');
const { buildLab, SIDE_KINDS } = require('./lib/lab.js');
const hashes = require('./lib/hashes.js');
const { montage } = require('./lib/render.js');
const { COATS, Plate } = require('./lib/plate.js');

const args = process.argv.slice(2);
const D = data.load();
const L = buildLab(D);
const LAB_NAMES = Object.keys(L.anims);

/**
 * The page tables with the lab block in place. The page's own tables (anims, the stages' bottom tables) are left exactly as they
 * are, so the web room, its HD layer and its tests see the same 28 rows; the lab rows' table lives in petAssets.lab (anims: the
 * row, frame count, fps and adult foot row; stageBottom: the foot row per kitten stage) and only the native assets read it.
 */
function nextTables() {
  const pageOnly = (t) => { const o = {}; for (const n of Object.keys(t)) if (data.BASE_ROWS.includes(n)) o[n] = t[n]; return o };
  const A = {}; for (const k of Object.keys(D.A)) if (k !== 'lab') A[k] = D.A[k];
  A.anims = pageOnly(D.A.anims);
  A.lab = { v: L.lab.v, anims: L.anims, stageBottom: L.stageBottoms, rows: L.lab.rows, pairs: L.lab.pairs };
  const G = { days: D.G.days, stages: D.G.stages.map((s) => ({ a: s.a, sx: s.sx, coats: s.coats, bottom: pageOnly(s.bottom) })) };
  return { A, G };
}

function sheetDiffers(have, want) {
  if (have.w !== want.w || have.h !== want.h) return true;
  for (let i = 0; i < have.px.length; i++) if (have.px[i] !== want.px[i]) return true;
  return false;
}

function check() {
  const bad = hashes.compareRecord(D.kinds).map((f) => 'page frame changed: ' + f);
  const { A, G } = nextTables();
  const strip = (o) => JSON.stringify(o, (k, v) => (k === 'coats' ? undefined : v));
  if (strip(A) !== strip(D.A)) bad.push('petAssets tables (anims / lab) differ from a rebuild');
  if (strip(G) !== strip(D.G)) bad.push('petGrowth bottom tables differ from a rebuild');
  for (const kn of SIDE_KINDS) if (sheetDiffers(D.kinds[kn].sheet, L.sheets[kn])) bad.push(kn + ' sheets differ from a rebuild');
  if (bad.length) { console.error('FAIL: the cat lab rows are out of date:\n  ' + bad.join('\n  ') + '\n  run: node lifemax_modular_v73/tools/pet_art/build.js'); process.exit(1) }
  console.log(`ok: ${LAB_NAMES.length} lab rows current for ${SIDE_KINDS.length} kinds; ${Object.keys(L.lab.pairs).length} pair poses; page frames untouched`);
}

function write() {
  const bad = hashes.compareRecord(D.kinds);
  if (bad.length) throw new Error('the page frames differ from their fingerprints before writing: ' + bad.slice(0, 5).join(', '));
  const { A, G } = nextTables();
  A.coats = data.unpackSheet(L.sheets.adult);
  G.stages.forEach((s, i) => { s.coats = data.unpackSheet(L.sheets['st' + i]) });
  data.writeBlock(D.assets, A);
  data.writeBlock(D.growth, G);
  console.log(`wrote ${LAB_NAMES.length} lab rows (${LAB_NAMES.join(', ')}) for ${SIDE_KINDS.length} kinds`);
  qaReport(true);
}

function qaLines() { return L.qa.map((q) => `${q.kind} ${q.row}#${q.frame}: ${q.problems.join('; ')}`) }
function qaReport(brief) {
  const lines = qaLines();
  if (brief) console.log(`qa: ${lines.length} frames to look at (node tools/pet_art/build.js --qa)`);
  else console.log(lines.length ? lines.join('\n') : 'qa: clean');
}

const WRAP = 4; /* frames per contact-sheet line, so a sheet stays readable at full size */
/** montage lines for one row of one kind, wrapped every WRAP frames */
function lines(label, kn, name) {
  const fr = L.frames[kn][name], foot = (kn === 'adult' ? L.anims[name].bottom : L.stageBottoms[+kn.slice(2)][name]) - 1, out = [];
  for (let i = 0; i < fr.length; i += WRAP) {
    const part = fr.slice(i, i + WRAP);
    out.push({ label: `${label} ${i}-${i + part.length - 1}`, frames: part.map((b) => b.plate), foot, marks: part.map((b) => b.mouth) });
  }
  return out;
}

/** the pair poses composed as the engine places them (adult ginger and gray as A and B, kitten st2 ginger as A and B) */
function pairLines() {
  const PW = 168, PH = 52, out = [];
  const frameOfKind = (kn, row, f) => {
    if (L.frames[kn][row]) return L.frames[kn][row][f % L.frames[kn][row].length].plate;
    const k = D.kinds[kn], a = k.anims[row]; return data.frameOf(k.sheet, a.row, f % a.n);
  };
  for (const [name, p] of Object.entries(L.lab.pairs)) for (const kn of ['adult', 'st2']) {
    const { dx, dy } = p.offset[kn], frames = [];
    const n = Math.max(L.anims[p.a] ? L.anims[p.a].n : D.A.anims[p.a].n, 4);
    for (let f = 0; f < n; f++) {
      const cv = new Plate(PW, PH), ax = Math.round(PW / 2 - 36 - dx / 2), ay = PH - 48;
      const A = frameOfKind(kn, p.a, f), B0 = frameOfKind(kn, p.b, name === 'snuggle' ? f + 2 : f), B = p.mirrorB ? B0.mirror() : B0;
      if (!p.bFront) cv.blit(B, ax + dx, ay + dy);
      cv.blit(A, ax, ay);
      if (p.bFront) cv.blit(B, ax + dx, ay + dy);
      frames.push(cv);
    }
    out.push({ label: `${name} ${kn}`, frames: frames.slice(0, 4), foot: PH - 48 + D.A.anims.idle_sit.bottom - 1 });
  }
  return out;
}

function sheets(dir) {
  fs.mkdirSync(dir, { recursive: true });
  const pal = (c) => D.A.pal[COATS[c]];
  for (const name of LAB_NAMES) {
    /* the four coats need their own palettes: one file per coat, then one with every kitten stage in ginger */
    COATS.forEach((c, ci) => fs.writeFileSync(path.join(dir, `${name}-${c}.png`), montage(lines(`${name} adult ${c}`, 'adult', name), ci, pal(ci), 6)));
    const stages = SIDE_KINDS.slice(1).flatMap((kn) => lines(`${name} ${kn}`, kn, name));
    fs.writeFileSync(path.join(dir, `${name}-kittens.png`), montage(stages, 0, pal(0), 6));
  }
  const flip = LAB_NAMES.flatMap((name) => lines(name, 'adult', name));
  fs.writeFileSync(path.join(dir, 'flipbook.png'), montage(flip, 0, pal(0), 6));
  fs.writeFileSync(path.join(dir, 'pairs.png'), montage(pairLines(), 0, pal(0), 5));
  fs.writeFileSync(path.join(dir, 'qa.txt'), qaLines().join('\n') + '\n');
  console.log(`contact sheets for ${LAB_NAMES.length} rows written to ${dir}`);
}

if (args.includes('--check')) check();
else if (args.includes('--qa')) qaReport(false);
else if (args.includes('--sheets')) sheets(path.resolve(args[args.indexOf('--sheets') + 1] || 'pet_art_sheets'));
else write();
