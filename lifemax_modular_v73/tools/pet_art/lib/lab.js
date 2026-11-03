/* Builds every lab row from the pose specs (tools/pet_art/poses), for the adult and the six kitten stages, with its metadata.
   Pure: reads the loaded data (data.load()), returns new sheets and tables; writing and checking live in build.js.
   Per row the metadata the engines need (petAssets.lab.rows.<name>):
     fps, loop, contacts (frames with paws planted), chain {from, to} (rows it hands over from / to), flipAtEnd (turn), bite
     (pickup: the frame the toy is taken), release (toss: the frame it leaves), touch (bump, nose_touch: the contact frame),
     mouth {kind: [[x, y] per frame]} (the toy / mouth anchor in frame pixels, side view facing +x; null where no face point)
   The foot row (bottom) of a lab row is one below the lowest pixel of its first frame, per kind: the rule walk and idle_sit
   follow (walk: paws on row 45, bottom 46), so a lab row stands exactly where walk and idle_sit stand. A few page rows break it
   (stretch has bottom 47 with paws on 45; the kitten tables vary more), which is why the lab does not copy their bottoms. */
'use strict';
const fs = require('fs'), path = require('path');
const { W, H, BASE_ROWS, baseSheet, assemble, frameOf } = require('./data.js');
const { buildFrame, source } = require('./spec.js');
const parts = require('./parts.js');
const { audit } = require('./repair.js');
const { lowestRow } = require('./geom.js');
const { isFill, channel, WHISKER } = require('./plate.js');

const POSES = path.join(__dirname, '../poses');
const SIDE_KINDS = ['adult', 'st0', 'st1', 'st2', 'st3', 'st4', 'st5'];
const ROW_KEYS = ['fps', 'loop', 'contacts', 'chain', 'flipAtEnd', 'bite', 'release', 'touch', 'stride'];

function readSpecs() {
  const index = JSON.parse(fs.readFileSync(path.join(POSES, 'index.json'), 'utf8'));
  const rows = index.rows.map((n) => {
    const s = JSON.parse(fs.readFileSync(path.join(POSES, n + '.json'), 'utf8'));
    if (s.name !== n) throw new Error(`poses/${n}.json names itself ${s.name}`);
    if (BASE_ROWS.includes(n)) throw new Error(n + ' is one of the page rows');
    if (!s.frames.length || s.frames.length > 8) throw new Error(n + ': 1 to 8 frames');
    return s;
  });
  return { rows, pairs: index.pairs };
}

/** QA of one built frame against its source: problems a reviewer must look at (empty = clean) */
function frameQa(built, kindName) {
  const out = [], p = built.plate, s = built.src.plate;
  const before = new Set(audit(s)), now = audit(p).filter((m) => !before.has(m));
  if (now.length) out.push(`${now.length} new open-outline or lone pixels (${now.slice(0, 3).join('; ')})`);
  /* fill on the frame's edge means the form runs on past it (an outline there still closes the form) */
  const edge = (q) => { let n = 0; for (let x = 0; x < W; x++) { if (isFill(q.get(x, 0))) n++ } for (let y = 0; y < H; y++) { if (isFill(q.get(0, y))) n++; if (isFill(q.get(W - 1, y))) n++ } return n };
  if (edge(p) > edge(s)) out.push('runs past the frame edge (clipped)');
  if (kindName && built.src.rig && built.src.rig.h) {
    try { if (!parts.partsCheck(s, parts.cut(s, built.src.rig))) out.push('head cut does not reassemble the source exactly') } catch (e) { /* no head: nothing to check */ }
  }
  return out;
}

/** the frontmost non-whisker pixel of the face, on the rows around the mouth anchor */
function muzzleFront(fr) {
  let x1 = 0; const y0 = Math.floor(fr.mouth[1]) - 4;
  for (let y = y0; y <= y0 + 4; y++) for (let x = 0; x < W; x++) { const v = fr.plate.get(x, y); if (v && channel(v, 2) !== WHISKER && channel(v, 0) !== WHISKER) x1 = Math.max(x1, x) }
  return x1;
}

/** pair placement: how far (frame pixels, adult or stage) cat B's frame sits from cat A's, by the fit rule */
function pairOffset(pair, kindName, kinds, built) {
  const k = kinds[kindName], sx = k.sx;
  const frameOfRow = (row, f) => {
    if (built[row]) return { plate: built[row][f].plate, mouth: built[row][f].mouth, rig: built[row][f].src.rig };
    const a = k.anims[row]; return { plate: frameOf(k.sheet, a.row, f), mouth: parts.mouthPoint(frameOf(k.sheet, a.row, f), k.rig[row].f[f]), rig: k.rig[row].f[f] };
  };
  const cf = pair.contactFrame || 0, A = frameOfRow(pair.a, cf), B = frameOfRow(pair.b, 0);
  let dx, dy = 0;
  if (pair.fit === 'spoon') { const b = A.plate.bbox(); dx = -Math.round(pair.lead * (b.x1 - b.x0 + 1)); dy = -Math.max(1, Math.round(pair.behind * sx)) }
  else if (pair.fit === 'noseToNose') dx = 2 * muzzleFront(A) + 1 - W; /* the two muzzles meet, whiskers may cross */
  else if (pair.fit === 'mouthToHead') { const e = parts.headEllipse(B.rig); dx = Math.round(A.mouth[0] - (W - e.cx - e.rx)) }
  else throw new Error('unknown pair fit ' + pair.fit);
  return { dx, dy };
}

/**
 * Everything a build writes: { sheets: {kind: Plate}, anims: {name: {row, n, fps, bottom}}, stageBottoms: [{name: bottom}],
 * lab: {v, rows, pairs}, qa: [{kind, row, frame, problems}], frames: {kind: {row: [built]}} }
 */
function buildLab(D) {
  const { rows, pairs } = readSpecs(), kinds = D.kinds;
  const out = { sheets: {}, anims: {}, stageBottoms: [], lab: { v: 1, rows: {}, pairs: {} }, qa: [], frames: {} };
  for (const kn of SIDE_KINDS) {
    const k = kinds[kn], built = {}, labRows = [], bottoms = {};
    for (const spec of rows) {
      /* an airborne row names a grounded frame to measure its floor from ("standsOn"), so it keeps the height it is drawn at */
      const floor = lowestRow(source(spec.standsOn || spec.frames[0].src, kn, kinds).plate);
      const frames = spec.frames.map((fs_, i) => {
        let b;
        try { b = buildFrame(fs_, { kindName: kn, kinds, sx: k.sx, floor }) } catch (e) { throw new Error(`${spec.name}#${i} (${kn}): ${e.message}`) }
        const problems = frameQa(b, kn); if (problems.length) out.qa.push({ kind: kn, row: spec.name, frame: i, problems });
        return b;
      });
      built[spec.name] = frames;
      labRows.push({ name: spec.name, frames: frames.map((f) => f.plate) });
      bottoms[spec.name] = floor + 1; /* walk and idle_sit stand on the row under their lowest pixel; so does every lab row */
    }
    out.frames[kn] = built;
    out.sheets[kn] = assemble(baseSheet(k), labRows);
    if (kn === 'adult') rows.forEach((s, i) => { out.anims[s.name] = { row: BASE_ROWS.length + i, n: s.frames.length, fps: s.fps, bottom: bottoms[s.name] } });
    else out.stageBottoms[+kn.slice(2)] = bottoms;
  }
  for (const s of rows) {
    const r = {}; for (const key of ROW_KEYS) if (s[key] !== undefined) r[key] = s[key];
    r.mouth = {}; for (const kn of SIDE_KINDS) r.mouth[kn] = out.frames[kn][s.name].map((b) => b.mouth);
    out.lab.rows[s.name] = r;
  }
  for (const [name, p] of Object.entries(pairs)) {
    const o = { a: p.a, b: p.b, mirrorB: !!p.mirrorB, bFront: !!p.bFront, contactFrame: p.contactFrame || 0, offset: {} };
    for (const kn of SIDE_KINDS) o.offset[kn] = pairOffset(p, kn, kinds, out.frames[kn]);
    out.lab.pairs[name] = o;
  }
  return out;
}

module.exports = { buildLab, readSpecs, SIDE_KINDS, POSES };
