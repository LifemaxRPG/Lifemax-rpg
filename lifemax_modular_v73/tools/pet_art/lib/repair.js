/* Clean-up passes after a frame was recomposed, so it obeys the same rules as the hand-drawn frames (measured over all 154
   adult frames: every fill pixel except a whisker has its four side neighbours opaque, i.e. the dark outline closes every form;
   no pixel floats alone).
   Every pass works only inside a zone (a Uint8Array mask; null = the whole frame): the pixels a transform touched, grown by two.
   Untouched art is never "repaired", because the original deliberately has a few thick outline corners and toe gaps.
     dropSpecks    removes opaque islands of at most MAX_SPECK pixels (8-connected) that a turn or a cut can leave behind
     fillPinholes  a clear pixel with fill on both sides (left and right, or above and below), or on three sides, takes the
                   most common neighbouring fill: closes the hairline cracks a turn opens between two parts
     closeOutline  a clear pixel beside a fill pixel becomes outline: re-draws the outline where a turn or a cut exposed fur
     dropSpurs     an outline pixel with no fill around it that sticks out (one outline neighbour) or sits doubled on the edge
                   goes clear: removes outline left dangling where a part moved away
     dropInnerDots an outline pixel with fill on all four sides and at most one outline neighbour becomes the surrounding fill:
                   removes the specks of an old seam inside a form */
'use strict';
const { isOutline, isFill, channel, PACKED_OUTLINE, WHISKER } = require('./plate.js');

const MAX_SPECK = 3;
const PUPIL = 150; /* kitten eyes reach the head's edge without an outline; a pupil may touch clear */
const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const N8 = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]];

const inZone = (zone, p, x, y) => !zone || zone[y * p.w + x];
/** a fill pixel that must not touch clear on a side: everything but whiskers and pupils */
const needsOutline = (v) => isFill(v) && channel(v, 2) !== WHISKER && channel(v, 0) !== WHISKER && channel(v, 2) !== PUPIL;

/** zone grown by r pixels (square) */
function grow(zone, w, h, r) {
  const out = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) if (zone[y * w + x])
    for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
      const xx = x + dx, yy = y + dy; if (xx >= 0 && yy >= 0 && xx < w && yy < h) out[yy * w + xx] = 1;
    }
  return out;
}

function dropSpecks(p, zone) {
  const seen = new Uint8Array(p.w * p.h);
  for (let s = 0; s < p.px.length; s++) {
    if (!p.px[s] || seen[s]) continue;
    const comp = [s]; seen[s] = 1;
    for (let i = 0; i < comp.length; i++) {
      const x = comp[i] % p.w, y = (comp[i] / p.w) | 0;
      for (const [dx, dy] of N8) {
        const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= p.w || yy >= p.h) continue;
        const k = yy * p.w + xx; if (p.px[k] && !seen[k]) { seen[k] = 1; comp.push(k) }
      }
    }
    if (comp.length <= MAX_SPECK && comp.every((k) => inZone(zone, p, k % p.w, (k / p.w) | 0))) for (const k of comp) p.px[k] = 0;
  }
}

/** the most common fill value among the 8 neighbours (side neighbours count double), or 0 */
function commonFill(p, x, y) {
  const votes = new Map(); let best = 0, bestN = 0;
  for (const [dx, dy] of N8) {
    const v = p.get(x + dx, y + dy); if (!isFill(v) || !needsOutline(v)) continue;
    const n = (votes.get(v) || 0) + (dx === 0 || dy === 0 ? 2 : 1); votes.set(v, n);
    if (n > bestN || (n === bestN && v < best)) { best = v; bestN = n }
  }
  return best;
}

function fillPinholes(p, zone) {
  for (let pass = 0; pass < 2; pass++) {
    const fix = [];
    for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
      if (p.get(x, y) || !inZone(zone, p, x, y)) continue;
      const l = p.get(x - 1, y), r = p.get(x + 1, y), u = p.get(x, y - 1), d = p.get(x, y + 1);
      const sides = [l, r, u, d].filter((v) => v).length;
      if (!((l && r) || (u && d) || sides >= 3)) continue;
      const v = commonFill(p, x, y); if (v) fix.push([x, y, v]);
    }
    for (const [x, y, v] of fix) p.set(x, y, v);
    if (!fix.length) break;
  }
}

function closeOutline(p, zone) {
  const add = [];
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    if (!needsOutline(p.get(x, y))) continue;
    const here = inZone(zone, p, x, y);
    for (const [dx, dy] of N4) {
      const xx = x + dx, yy = y + dy;
      if (xx >= 0 && yy >= 0 && xx < p.w && yy < p.h && !p.get(xx, yy) && (here || inZone(zone, p, xx, yy))) add.push([xx, yy]);
    }
  }
  for (const [x, y] of add) p.set(x, y, PACKED_OUTLINE);
}

function dropSpurs(p, zone) {
  for (let pass = 0; pass < 3; pass++) {
    const kill = [];
    for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
      const v = p.get(x, y); if (!isOutline(v) || !inZone(zone, p, x, y)) continue;
      let fill8 = 0, out8 = 0, clear4 = 0;
      for (const [dx, dy] of N8) { const q = p.get(x + dx, y + dy); if (isFill(q)) fill8++; else if (q) out8++ }
      for (const [dx, dy] of N4) if (!p.get(x + dx, y + dy)) clear4++;
      /* no fill anywhere around: a dangling end (at most one outline neighbour) or an outer doubled outline (clear on a side) */
      if (fill8 === 0 && (out8 <= 1 || clear4 >= 1)) kill.push([x, y]);
    }
    for (const [x, y] of kill) p.set(x, y, 0);
    if (!kill.length) break;
  }
}

function dropInnerDots(p, zone) {
  const fix = [];
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    const v = p.get(x, y); if (!isOutline(v) || !inZone(zone, p, x, y)) continue;
    if (!N4.every(([dx, dy]) => isFill(p.get(x + dx, y + dy)))) continue;
    let out8 = 0; for (const [dx, dy] of N8) if (isOutline(p.get(x + dx, y + dy))) out8++;
    if (out8 <= 1) { const f = commonFill(p, x, y); if (f) fix.push([x, y, f]) }
  }
  for (const [x, y, f] of fix) p.set(x, y, f);
}

/** all passes in order, in place; zone null = whole frame. Returns p. */
function repair(p, zone) {
  dropSpecks(p, zone);
  fillPinholes(p, zone);
  dropInnerDots(p, zone);
  closeOutline(p, zone);
  dropSpurs(p, zone);
  dropSpecks(p, zone);
  closeOutline(p, zone);
  return p;
}

/** the art rules a finished frame must meet, as a list of problems (empty = clean); used by the QA report and the tests */
function audit(p) {
  const bad = [];
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    const v = p.get(x, y); if (!v) continue;
    if (needsOutline(v) && N4.some(([dx, dy]) => !p.get(x + dx, y + dy))) bad.push(`open outline at ${x},${y}`);
    if (!N8.some(([dx, dy]) => p.get(x + dx, y + dy))) bad.push(`lone pixel at ${x},${y}`);
  }
  return bad;
}

module.exports = { repair, audit, grow, dropSpecks, fillPinholes, closeOutline, dropSpurs, dropInnerDots };
