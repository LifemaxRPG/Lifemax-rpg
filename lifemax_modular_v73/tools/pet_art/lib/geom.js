/* Landmarks of a side-view frame, measured from its pixels and its rig entry (petRig.html), so a pose spec can say "turn about
   the hind paw" or "squash the legs by two rows" and mean the same place on the adult and on every kitten stage.
   Side views face +x (the face is on the right). Coordinates are art pixels; pivots are pixel corners (x + 0.5 is a centre). */
'use strict';
const { isFill } = require('./plate.js');

/** the lowest opaque row */
function lowestRow(p) { for (let y = p.h - 1; y >= 0; y--) for (let x = 0; x < p.w; x++) if (p.get(x, y)) return y; return -1 }

/** runs [x0, x1] of opaque pixels on row y */
function runs(p, y, mask) {
  const out = []; let a = -1;
  for (let x = 0; x <= p.w; x++) {
    const on = x < p.w && p.get(x, y) && (!mask || !mask[y * p.w + x]);
    if (on && a < 0) a = x; if (!on && a >= 0) { out.push([a, x - 1]); a = -1 }
  }
  return out;
}

/** the paws on the floor: runs of the lowest two rows, merged; rear = leftmost, front = rightmost; each { x0, x1, y } */
function paws(p) {
  const y = lowestRow(p); if (y < 0) return null;
  const rs = runs(p, y).concat(runs(p, y - 1));
  rs.sort((a, b) => a[0] - b[0]);
  const merged = [];
  for (const r of rs) { const m = merged[merged.length - 1]; if (m && r[0] <= m[1] + 1) m[1] = Math.max(m[1], r[1]); else merged.push(r.slice()) }
  return { rear: { x0: merged[0][0], x1: merged[0][1], y }, front: { x0: merged[merged.length - 1][0], x1: merged[merged.length - 1][1], y }, all: merged, y };
}

/** a mask (Uint8Array) of the rig's tail and whisker pixels, which are not body for the band searches */
function rigMask(p, rf) {
  const m = new Uint8Array(p.w * p.h);
  const mark = (r) => { for (let i = 0; i < r.length; i += 3) for (let x = r[i + 1]; x <= r[i + 2]; x++) m[r[i] * p.w + x] = 1 };
  if (rf && rf.t) mark(rf.t.r);
  if (rf && rf.w) rf.w.forEach((w) => mark(w.r));
  return m;
}

/**
 * The legs-only band [ya, yb): rows between the belly and the paws where only legs are drawn (the rig's `l` when it has one,
 * else measured the same way rig_build.py does: going up from the paws, the first row whose longest run is at least
 * BELLY_FRACTION of the body length is the belly; the band starts two rows below it and ends three rows above the paws).
 * null when the frame has no such band (sitting, lying).
 */
const BELLY_FRACTION = 0.45;
function legBand(p, rf) {
  if (rf && rf.l) return { ya: rf.l[0], yb: rf.l[1] };
  const mask = rigMask(p, rf), bottom = lowestRow(p); if (bottom < 0) return null;
  let x0 = p.w, x1 = -1;
  for (let y = 0; y <= bottom; y++) for (const [a, b] of runs(p, y, mask)) { x0 = Math.min(x0, a); x1 = Math.max(x1, b) }
  const len = x1 - x0 + 1, need = Math.max(8, Math.round(BELLY_FRACTION * len));
  let y = bottom - 2;
  while (y > 0 && Math.max(0, ...runs(p, y, mask).map(([a, b]) => b - a + 1)) < need) y--;
  const ya = y + 2, yb = bottom - 2;
  return yb - ya >= 2 ? { ya, yb } : null;
}

/** the torso band [xa, xb) (rig `x`, else 28 % .. 52 % of the body length from the rear, as rig_build.py) */
function torsoBand(p, rf) {
  if (rf && rf.x) return { xa: rf.x[0], xb: rf.x[1] };
  const mask = rigMask(p, rf); let x0 = p.w, x1 = -1;
  for (let y = 0; y < p.h; y++) for (const [a, b] of runs(p, y, mask)) { x0 = Math.min(x0, a); x1 = Math.max(x1, b) }
  const xa = Math.floor(x0 + 0.28 * (x1 - x0)), xb = Math.floor(x0 + 0.52 * (x1 - x0));
  return xb - xa >= 3 ? { xa, xb } : null;
}

/** the centre of the opaque pixels */
function centroid(p) {
  let sx = 0, sy = 0, n = 0;
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) if (p.get(x, y)) { sx += x; sy += y; n++ }
  return n ? [sx / n + 0.5, sy / n + 0.5] : [p.w / 2, p.h / 2];
}

/** named pivot of a frame: rearPaw, frontPaw, paws (between them), centre, or a literal [x, y] */
function pivot(name, p, extra = {}) {
  if (Array.isArray(name)) return name;
  if (extra[name]) return extra[name];
  const pw = paws(p);
  switch (name) {
    case 'rearPaw': return [(pw.rear.x0 + pw.rear.x1 + 1) / 2, pw.y + 1];
    case 'frontPaw': return [(pw.front.x0 + pw.front.x1 + 1) / 2, pw.y + 1];
    case 'paws': return [(pw.rear.x0 + pw.front.x1 + 1) / 2, pw.y + 1];
    case 'centre': return centroid(p);
    default: throw new Error('unknown pivot ' + name);
  }
}

/** number of fill pixels (a cheap measure used by the checks) */
function fillCount(p) { let n = 0; for (const v of p.px) if (isFill(v)) n++; return n }

module.exports = { lowestRow, runs, paws, legBand, torsoBand, centroid, pivot, rigMask, fillCount };
