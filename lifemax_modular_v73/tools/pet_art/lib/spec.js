/* The pose-spec interpreter: turns one declarative frame description into a frame, for any kind (adult or kitten stage).
   A frame spec (see docs/specs/PET_ART_LAB.md, "Pose specs"):
     { "src": "<row>:<frame>"        the source frame: one of the page's own frames of the same kind ("depth/<row>:<f>" takes the
                                     depth sheet of the same growth stage: depth for adult, dst<i> for st<i>)
       "ops": [ ... ]                applied in order; each op is one object:
         { "legs": k }                 remove k rows from the legs-only band (k < 0: add rows): crouch / rise on the spot;
                                       "band": [a, b] gives the band as rows a..b above the floor row instead of measuring it
         { "torso": k }                remove k columns from the torso band (k < 0: add): shorter / longer body
         { "rotate": deg, "pivot": p } RotSprite turn of the whole frame, positive = clockwise (nose down for a cat facing right)
         { "shift": [dx, dy] }         move by whole pixels
         { "floor": true }             move up or down so the paws stand on the row's floor (the lowest row of its first frame)
         { "part": "head", ... }       move a part, see parts.js (rotate, shift, pivot)
         { "face": "...", ... }        a face edit, see face.js
       "mirror": true                draw it facing the other way (a turn's second half) }
   Lengths are adult art pixels; on a kitten stage they are multiplied by the stage's scale sx (0.6 .. 0.93) and rounded, keeping
   at least one pixel when the adult value is not zero. Angles are the same at every stage.
   After the ops the frame is repaired (repair.js) in the zone the ops changed. */
'use strict';
const { W, H, frameOf } = require('./data.js');
const xf = require('./xform.js');
const geom = require('./geom.js');
const { repair, grow } = require('./repair.js');
const parts = require('./parts.js');
const face = require('./face.js');

/** an adult length on a stage of scale sx: rounded, never rounded to zero */
function px(v, sx) { if (!v) return 0; const r = Math.round(v * sx); return r === 0 ? Math.sign(v) : r }

/** the source frame and its rig entry; ref "walk:3" or "depth/walk_front:0" */
function source(ref, kindName, kinds) {
  const m = /^(?:(depth)\/)?([a-z_]+):(\d+)$/.exec(ref);
  if (!m) throw new Error('bad source ' + ref);
  let kn = kindName;
  if (m[1]) kn = kindName === 'adult' ? 'depth' : 'd' + kindName;
  const k = kinds[kn], a = k.anims[m[2]];
  if (!a || +m[3] >= a.n) throw new Error(`no frame ${ref} in ${kn}`);
  const rf = k.rig && k.rig[m[2]] ? k.rig[m[2]].f[+m[3]] : null;
  return { plate: frameOf(k.sheet, a.row, +m[3]), rig: rf, row: m[2], frame: +m[3], kind: kn, bottom: k.bottom && k.bottom[m[2]] != null ? k.bottom[m[2]] : a.bottom };
}

/**
 * Builds one frame. ctx: { kindName, kinds, sx }. Returns { plate, anchors } where anchors holds the mouth point (when the
 * head is known) after every op, in frame coordinates.
 */
function buildFrame(spec, ctx) {
  const src = source(spec.src, ctx.kindName, ctx.kinds), sx = ctx.sx;
  let p = src.plate.clone();
  const changed = new Uint8Array(W * H);
  let cut = null; /* the cut-out parts, made on first use */
  let mouth = parts.mouthPoint(src.plate, src.rig);
  /* the rig's coordinates hold until a whole-frame op moves the art: part and face ops must come first */
  let moved = false;
  const rigNow = () => (moved ? null : src.rig);
  const needRig = (op) => { if (moved) throw new Error(`${spec.src}: ${JSON.stringify(op)} must come before whole-frame ops`) };
  const track = (fn) => { /* a whole-frame point transform, applied to the tracked anchors */ if (mouth) mouth = fn(mouth) };
  const fctx = { source: (ref) => source(ref, ctx.kindName, ctx.kinds) };
  for (const op of spec.ops || []) {
    const before = p;
    if (op.legs != null) {
      /* "band": [a, b] names the rows itself, counted up from the floor row (front views, where the legs touch) */
      const fl = geom.lowestRow(p);
      const b = op.band ? { ya: fl - px(op.band[0], sx), yb: fl - px(op.band[1], sx) } : geom.legBand(p, rigNow());
      if (!b) throw new Error(`${spec.src}: no legs band in ${ctx.kindName}`);
      const k = px(op.legs, sx); p = xf.legBand(p, b.ya, b.yb, k); moved = true;
      track(([x, y]) => [x, y < b.ya ? y + k : y]);
    } else if (op.torso != null) {
      const b = geom.torsoBand(p, rigNow()); if (!b) throw new Error(`${spec.src}: no torso band in ${ctx.kindName}`);
      const k = px(op.torso, sx); p = xf.torsoBand(p, b.xa, b.xb, k); moved = true;
      track(([x, y]) => [x < b.xa ? x + k : x, y]);
    } else if (op.rotate != null && !op.part) {
      const [qx, qy] = geom.pivot(op.pivot || 'paws', p); p = xf.rotate(p, op.rotate, qx, qy); moved = true;
      track(([x, y]) => rot(x, y, qx, qy, op.rotate));
    } else if (op.shift && !op.part) {
      const dx = px(op.shift[0], sx), dy = px(op.shift[1], sx); p = p.shift(dx, dy); moved = true;
      track(([x, y]) => [x + dx, y + dy]);
    } else if (op.floor) {
      if (ctx.floor == null) throw new Error('floor: no floor row for this row');
      const dy = ctx.floor - geom.lowestRow(p); p = p.shift(0, dy); moved = true;
      track(([x, y]) => [x, y + dy]);
    } else if (op.part) {
      needRig(op);
      if (!cut) cut = parts.cut(p, src.rig);
      const r = parts.movePart(p, cut, op, sx); p = r.plate; cut = r.cut; if (mouth) mouth = r.map(mouth);
    } else if (op.face) {
      needRig(op);
      p = face.edit(p, op, src.rig, sx, fctx);
    } else throw new Error('unknown op ' + JSON.stringify(op));
    for (let i = 0; i < p.px.length; i++) if (p.px[i] !== before.px[i]) changed[i] = 1;
  }
  if (changed.some((v) => v)) repair(p, grow(changed, W, H, 2));  if (spec.mirror) { p = p.mirror(); if (mouth) mouth = [W - mouth[0], mouth[1]] }
  return { plate: p, mouth: mouth ? [Math.round(mouth[0] * 10) / 10, Math.round(mouth[1] * 10) / 10] : null, src };
}

/** turns (x, y) by deg about (qx, qy), screen orientation (y down), positive clockwise */
function rot(x, y, qx, qy, deg) {
  const r = (deg * Math.PI) / 180, c = Math.cos(r), s = Math.sin(r), dx = x - qx, dy = y - qy;
  return [qx + dx * c - dy * s, qy + dx * s + dy * c];
}

module.exports = { buildFrame, source, px, rot };
