/* Face edits on a side-view frame (facing +x), by palette index: the face is drawn with its own indices (eyes 160/170 with a
   180 highlight and 150 pupil, nose and mouth pinks 120..140, the mouth line 200 and the tongue 210, muzzle white 90..110), so
   an edit can find its pixels exactly.
     { "face": "jaw", "by": k }       opens the mouth k pixels: the muzzle below the mouth line moves down and the gap becomes
                                      mouth (200) with the tongue (210) at its middle; the chin's outline moves with it
     { "face": "eyesFrom", "src": "row:f" }  copies the eye pixels (and the lids around them) from another frame of the same kind
                                      whose face point lines up, used to close or open eyes on a borrowed pose
   Lengths scale with the stage like every spec length. */
'use strict';
const { channel, isOutline, uniform } = require('./plate.js');

const MOUTH_LINE = 200, TONGUE = 210, EYE = [150, 160, 170, 180];

/** the face box around the rig's face point (where face edits may act) */
function faceBox(rf, sx) {
  const [hx, hy] = rf.h, r = Math.max(4, Math.round(7 * sx));
  return { x0: Math.floor(hx - r), x1: Math.ceil(hx + r), y0: Math.floor(hy - r), y1: Math.ceil(hy + r + 2) };
}

/** opens the jaw by k pixels below the lowest mouth-line row */
function jaw(p, rf, k, sx) {
  const b = faceBox(rf, sx), out = p.clone();
  let my = -1, mx0 = p.w, mx1 = -1;
  for (let y = b.y0; y <= b.y1; y++) for (let x = b.x0; x <= b.x1; x++) {
    const v = channel(p.get(x, y), 2);
    if (v === MOUTH_LINE || v === TONGUE) { my = Math.max(my, y); mx0 = Math.min(mx0, x); mx1 = Math.max(mx1, x) }
  }
  if (my < 0) throw new Error('jaw: this frame draws no mouth');
  /* the jaw: the columns of the mouth (plus the outline beside it), from the mouth line down to the head's lower edge */
  const xa = mx0 - 1, xb = mx1 + 1;
  for (let x = xa; x <= xb; x++) {
    let bottom = my; while (bottom + 1 < p.h && p.get(x, bottom + 1) && bottom < my + 4) bottom++;
    for (let y = bottom; y > my; y--) out.set(x, y + k, p.get(x, y));
    for (let y = my + 1; y <= my + k; y++) {
      const edge = x === xa || x === xb;
      out.set(x, y, edge ? p.get(x, my) : uniform(Math.abs(x - (mx0 + mx1) / 2) < 1 && y === my + k ? TONGUE : MOUTH_LINE));
    }
  }
  return out;
}

/** copies the eye pixels of frame q onto p (same kind, both frames' face points within a pixel) */
function eyesFrom(p, q, rf, sx) {
  const b = faceBox(rf, sx), out = p.clone();
  for (let y = b.y0; y < rf.h[1] + 1; y++) for (let x = b.x0; x <= b.x1; x++) {
    const a = channel(p.get(x, y), 2), c = channel(q.get(x, y), 2);
    if (EYE.includes(a) || EYE.includes(c) || (isOutline(p.get(x, y)) !== isOutline(q.get(x, y)) && y >= rf.h[1] - 4)) out.set(x, y, q.get(x, y));
  }
  return out;
}

/** dispatches one face op; ctx.source(ref) resolves another frame of the same kind */
function edit(p, op, rf, sx, ctx) {
  if (!rf || !rf.h) throw new Error('face edit on a frame without a face point');
  if (op.face === 'jaw') return jaw(p, rf, Math.max(1, Math.round(op.by * sx)), sx);
  if (op.face === 'eyesFrom') return eyesFrom(p, ctx.source(op.src).plate, rf, sx);
  throw new Error('unknown face edit ' + op.face);
}

module.exports = { edit, jaw, eyesFrom, faceBox };
