/* Cutting a side-view frame into parts and moving one of them, the head first (the rig has no head part: rig_build.py records
   the ears, the tail, the whiskers, the leg and torso bands and a face point `h`, the centroid of the eye, nose and mouth pixels).
   The head cut, guided by the rig:
     - its size comes from the ear bases (their distance is the head's width at every growth stage), its middle from them and `h`;
     - the head is the fill reachable from the face without crossing an outline pixel and without leaving an ellipse around
       that middle: where the art draws a line (the chin, the back of the head) the cut follows the line, and only across the
       open fur of the neck does the ellipse decide;
     - the outline pixels around that fill, the ears (rig `e` runs) and the whiskers (rig `w`) belong to the head too.
   Every opaque pixel belongs to exactly one part (head or body), so drawing the parts back in place gives the source frame
   exactly; partsCheck() proves it for every frame a spec uses.
   Moving the head: the body is drawn, then the turned / moved head over it; where the head left a gap at the neck the body's fur
   is grown into it (only between the body and the new head), then repair.js re-draws the outline. */
'use strict';
const { Plate, isOutline, isFill, channel } = require('./plate.js');
const xf = require('./xform.js');
const { grow } = require('./repair.js');

const MOUTH = [200, 210], NOSE = [120, 130, 140];
const N4 = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const N8 = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [-1, -1], [1, -1], [-1, 1]];
/* head ellipse, in units of the ear-base distance (tuned by eye on the adult and kitten contact sheets) */
const HEAD = { RX: 0.66, RX_PAD: 1.5, CHIN: 0.34, RY_PAD: 1.5 };
const NECK_REACH = 3; /* how far (pixels) beyond the moved head the neck fur may be grown */

function markRuns(mask, w, r) { for (let i = 0; i < r.length; i += 3) for (let x = r[i + 1]; x <= r[i + 2]; x++) mask[r[i] * w + x] = 1 }

/** the head's ellipse { cx, cy, rx, ry } for a frame with rig entry rf, or null when the rig has no face point */
function headEllipse(rf) {
  if (!rf || !rf.h) return null;
  const [hx, hy] = rf.h, ears = (rf.e || []).map((e) => e.b);
  let sep, top, cx;
  if (ears.length >= 2) { sep = Math.abs(ears[1][0] - ears[0][0]); top = Math.min(ears[0][1], ears[1][1]); cx = (ears[0][0] + ears[1][0]) / 2 }
  else { sep = 15; top = hy - 9; cx = hx - 2 }
  const chin = hy + HEAD.CHIN * sep;
  return { cx: cx + 0.5, cy: (top + chin) / 2 + 0.5, rx: HEAD.RX * sep + HEAD.RX_PAD, ry: (chin - top) / 2 + HEAD.RY_PAD, sep };
}
const inEllipse = (e, x, y) => ((x + 0.5 - e.cx) / e.rx) ** 2 + ((y + 0.5 - e.cy) / e.ry) ** 2 <= 1;

/**
 * Cuts frame p (rig entry rf) into { head: mask, body: mask, neck: [x, y], ellipse } (masks are Uint8Array w*h).
 * Throws when the frame has no face point.
 */
function cut(p, rf) {
  const e = headEllipse(rf); if (!e) throw new Error('this frame has no face point: no head cut');
  const w = p.w, head = new Uint8Array(w * p.h);
  /* seed: fill pixels within 2 of the face point */
  const q = [];
  for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
    const x = Math.floor(rf.h[0]) + dx, y = Math.floor(rf.h[1]) + dy;
    if (isFill(p.get(x, y)) && !head[y * w + x]) { head[y * w + x] = 1; q.push([x, y]) }
  }
  for (let i = 0; i < q.length; i++) {
    const [x, y] = q[i];
    for (const [dx, dy] of N4) {
      const xx = x + dx, yy = y + dy;
      if (xx < 0 || yy < 0 || xx >= w || yy >= p.h || head[yy * w + xx]) continue;
      const v = p.get(xx, yy); if (!isFill(v) || !inEllipse(e, xx, yy)) continue;
      head[yy * w + xx] = 1; q.push([xx, yy]);
    }
  }
  /* the outline around the head fill (inside the ellipse grown by one) */
  const ring = [];
  for (let y = 0; y < p.h; y++) for (let x = 0; x < w; x++) {
    if (head[y * w + x] || !isOutline(p.get(x, y))) continue;
    const big = { cx: e.cx, cy: e.cy, rx: e.rx + 1, ry: e.ry + 1 };
    if (inEllipse(big, x, y) && N8.some(([dx, dy]) => head[(y + dy) * w + x + dx] && x + dx >= 0 && x + dx < w)) ring.push(y * w + x);
  }
  for (const k of ring) head[k] = 1;
  for (const ear of rf.e || []) markRuns(head, w, ear.r);
  for (const wk of rf.w || []) markRuns(head, w, wk.r);
  /* fill pockets inside the head enclosed by head pixels on both sides (eye corners, nose lines) */
  for (let y = 0; y < p.h; y++) for (let x = 0; x < w; x++) {
    const k = y * w + x; if (head[k] || !p.px[k] || !inEllipse(e, x, y)) continue;
    const l = head[k - 1], r = head[k + 1], u = y > 0 && head[k - w], d = y < p.h - 1 && head[k + w];
    if ((l && r && u) || (u && d && (l || r))) head[k] = 1;
  }
  /* never below the chin: a kitten's head is so big that its ellipse reaches the front legs */
  const chinY = chinRow(p, rf, e);
  for (let y = chinY + 1; y < p.h; y++) for (let x = 0; x < w; x++) head[y * w + x] = 0;
  for (const wk of rf.w || []) markRuns(head, w, wk.r);
  for (let k = 0; k < head.length; k++) if (!p.px[k]) head[k] = 0;
  const body = new Uint8Array(w * p.h); for (let k = 0; k < body.length; k++) body[k] = p.px[k] && !head[k] ? 1 : 0;
  /* specks of body cut off by the head (a corner of the back-of-head outline) go with the head */
  for (const comp of components(body, w, p.h)) {
    if (comp.length > MAX_ORPHAN) continue;
    if (comp.some((k) => N8.some(([dx, dy]) => { const x = (k % w) + dx, y = ((k / w) | 0) + dy; return x >= 0 && y >= 0 && x < w && y < p.h && head[y * w + x] }))) for (const k of comp) { head[k] = 1; body[k] = 0 }
  }
  /* the neck: the middle of the head pixels that touch body fill */
  let nx = 0, ny = 0, n = 0;
  for (let y = 0; y < p.h; y++) for (let x = 0; x < w; x++) if (head[y * w + x] && N4.some(([dx, dy]) => body[(y + dy) * w + x + dx] && isFill(p.get(x + dx, y + dy)))) { nx += x; ny += y; n++ }
  const neck = n ? [nx / n + 0.5, ny / n + 0.5] : [e.cx - e.rx * 0.6, e.cy + e.ry * 0.5];
  return { head, body, neck, ellipse: e };
}

const MAX_ORPHAN = 6;
const FACE_INDEX = (v) => v >= 120 && v <= 210 && v !== 190;

/** the lowest row that may still be head: two rows (scaled) under the lowest eye, nose or mouth pixel near the face point */
function chinRow(p, rf, e) {
  let low = -1;
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    if (Math.abs(x + 0.5 - rf.h[0]) > e.rx || Math.abs(y + 0.5 - rf.h[1]) > 7) continue;
    if (FACE_INDEX(channel(p.get(x, y), 2)) || FACE_INDEX(channel(p.get(x, y), 0))) low = Math.max(low, y);
  }
  return low < 0 ? Math.floor(e.cy + e.ry) : low + Math.max(2, Math.round(0.14 * e.sep));
}

/** 8-connected components of a mask, as lists of indices */
function components(mask, w, h) {
  const seen = new Uint8Array(w * h), out = [];
  for (let s = 0; s < mask.length; s++) {
    if (!mask[s] || seen[s]) continue;
    const comp = [s]; seen[s] = 1;
    for (let i = 0; i < comp.length; i++) {
      const x = comp[i] % w, y = (comp[i] / w) | 0;
      for (const [dx, dy] of N8) {
        const xx = x + dx, yy = y + dy; if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const k = yy * w + xx; if (mask[k] && !seen[k]) { seen[k] = 1; comp.push(k) }
      }
    }
    out.push(comp);
  }
  return out;
}

/** the parts drawn back in place: must equal p (returns true when exact) */
function partsCheck(p, c) {
  const out = new Plate(p.w, p.h);
  for (let k = 0; k < p.px.length; k++) { if (c.body[k]) out.px[k] = p.px[k] }
  for (let k = 0; k < p.px.length; k++) { if (c.head[k]) { if (out.px[k]) return false; out.px[k] = p.px[k] } }
  return out.equals(p);
}

/** a plate of p's pixels under mask */
function pick(p, mask) { return p.filter((x, y) => mask[y * p.w + x]) }

/**
 * Moves a part. op: { part: 'head', rotate: deg, pivot: 'neck' | [x, y], shift: [dx, dy] } (lengths in adult pixels, scaled by
 * sx). Returns { plate, cut, map } where map(point) moves a point the way the head moved (for the mouth anchor).
 */
function movePart(p, c, op, sx) {
  if (op.part !== 'head') throw new Error('only the head is a movable part (got ' + op.part + ')');
  const s = (v) => { if (!v) return 0; const r = Math.round(v * sx); return r === 0 ? Math.sign(v) : r };
  const [qx, qy] = Array.isArray(op.pivot) ? op.pivot : c.neck;
  const deg = op.rotate || 0, dx = s(op.shift ? op.shift[0] : 0), dy = s(op.shift ? op.shift[1] : 0);
  let head = pick(p, c.head); const body = pick(p, c.body);
  if (deg) head = xf.rotate(head, deg, qx, qy);
  if (dx || dy) head = head.shift(dx, dy);
  const out = body.clone().blit(head);
  /* grow the body's fur into the gap the head left at the neck, but only between the body and the moved head */
  const headNow = new Uint8Array(p.w * p.h); for (let k = 0; k < headNow.length; k++) headNow[k] = head.px[k] ? 1 : 0;
  const nearHead = grow(headNow, p.w, p.h, NECK_REACH), nearBody = grow(c.body, p.w, p.h, NECK_REACH);
  for (let pass = 0; pass < NECK_REACH + 1; pass++) {
    const add = [];
    for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
      const k = y * p.w + x; if (out.px[k] || !c.head[k] || !nearHead[k] || !nearBody[k]) continue;
      const sides = N4.filter(([ex, ey]) => isFill(out.get(x + ex, y + ey))).length; if (sides < 2) continue;
      const v = neighbourFill(out, body, x, y); if (v) add.push([k, v]);
    }
    for (const [k, v] of add) out.px[k] = v;
    if (!add.length) break;
  }
  const map = ([x, y]) => { let [a, b] = [x, y]; if (deg) { const r = (deg * Math.PI) / 180, co = Math.cos(r), si = Math.sin(r), ex = a - qx, ey = b - qy; a = qx + ex * co - ey * si; b = qy + ex * si + ey * co } return [a + dx, b + dy] };
  /* the cut follows the head: a later head op moves the moved head */
  const next = { head: headNow, body: c.body.slice(), neck: map(c.neck), ellipse: c.ellipse };
  for (let k = 0; k < next.body.length; k++) if (out.px[k] && !headNow[k]) next.body[k] = 1; else next.body[k] = 0;
  return { plate: out, cut: next, map };
}

/** the body fur value nearest to (x, y) (body pixels only, not the head: the neck is body coloured) */
function neighbourFill(out, body, x, y) {
  for (let r = 1; r <= 3; r++) {
    const votes = new Map(); let best = 0, bestN = 0;
    for (let dy = -r; dy <= r; dy++) for (let dx = -r; dx <= r; dx++) {
      const v = body.get(x + dx, y + dy); if (!isFill(v)) continue;
      const n = (votes.get(v) || 0) + 1; votes.set(v, n); if (n > bestN || (n === bestN && v < best)) { best = v; bestN = n }
    }
    if (best) return best;
  }
  return 0;
}

/**
 * The mouth: where a carried toy is held, for a side view facing +x. The front of the mouth pixels (indices 200, 210) when the
 * frame draws an open mouth, else just under the nose (indices 120..140 near the face point). Null without a face point.
 */
function mouthPoint(p, rf) {
  if (!rf || !rf.h) return null;
  const [hx, hy] = rf.h, near = (x, y) => Math.abs(x + 0.5 - hx) <= 7 && Math.abs(y + 0.5 - hy) <= 6;
  const pts = (set) => { const o = []; for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) if (near(x, y) && set.includes(channel(p.get(x, y), 2))) o.push([x, y]); return o };
  const m = pts(MOUTH);
  if (m.length) { const y = Math.max(...m.map((v) => v[1])); const xs = m.filter((v) => v[1] >= y - 1).map((v) => v[0]); return [Math.max(...xs) + 1, y + 1] }
  const n = pts(NOSE);
  if (n.length) { const x = n.reduce((a, v) => a + v[0], 0) / n.length; const y = Math.max(...n.map((v) => v[1])); return [x + 1.5, y + 2.5] }
  return [hx + 2, hy + 3];
}

module.exports = { cut, movePart, partsCheck, headEllipse, mouthPoint, pick };
