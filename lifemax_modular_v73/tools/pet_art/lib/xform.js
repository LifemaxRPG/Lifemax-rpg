/* Pixel-art-aware transforms on plates. None of them invents a colour: every output pixel is a copy of one source pixel (all
   four coats at once, see plate.js), so palette-index shading, the outline index and hard edges survive.
     rotate      RotSprite: the plate is scaled up 8x with Scale2x (EPX, three passes; equal packed words count as equal), the big
                 image is turned about the pivot with nearest-neighbour, and each output pixel takes the big pixel under its centre.
                 Edges stay one pixel wide and diagonal lines stay clean, which plain nearest-neighbour rotation breaks.
     scale       squash / stretch about an anchor with nearest-neighbour (index preserving). For small amounts prefer the band
                 operations below, which keep faces and paws untouched.
     legBand     removes (squash) or repeats (stretch) whole rows inside the legs-only band: the paws stay on the floor and the
                 whole upper body drops or rises, exactly how the page's rig stretches legs (petRig `l`, 38-pet-rig.js).
     torsoBand   removes or repeats whole columns inside the torso band (petRig `x`): a longer or shorter body.
     shift, mirror (plate.js) complete the set. */
'use strict';
const { Plate } = require('./plate.js');

/** one Scale2x (EPX) pass */
function scale2x(p) {
  const out = new Plate(p.w * 2, p.h * 2), W = p.w;
  for (let y = 0; y < p.h; y++) for (let x = 0; x < W; x++) {
    const P = p.get(x, y), A = p.get(x, y - 1), B = p.get(x + 1, y), C = p.get(x - 1, y), D = p.get(x, y + 1);
    const e0 = C === A && C !== D && A !== B ? A : P, e1 = A === B && A !== C && B !== D ? B : P;
    const e2 = D === C && D !== B && C !== A ? C : P, e3 = B === D && B !== A && D !== C ? D : P;
    const o = out.px, ow = out.w;
    o[2 * y * ow + 2 * x] = e0; o[2 * y * ow + 2 * x + 1] = e1; o[(2 * y + 1) * ow + 2 * x] = e2; o[(2 * y + 1) * ow + 2 * x + 1] = e3;
  }
  return out;
}

const UP = 8; /* RotSprite's working scale: three Scale2x passes */
const bigCache = new WeakMap();
function big(p) { let b = bigCache.get(p); if (!b) { b = scale2x(scale2x(scale2x(p))); bigCache.set(p, b) } return b }

/**
 * RotSprite turn of p by deg degrees (positive = clockwise on screen, y down) about (px, py) in art pixels (pixel corners:
 * (10, 20) is the top-left corner of pixel 10, 20). The result has p's size; whatever turns outside is lost.
 */
function rotate(p, deg, px, py) {
  if (!deg) return p.clone();
  const b = big(p), out = new Plate(p.w, p.h), r = (-deg * Math.PI) / 180, c = Math.cos(r), s = Math.sin(r);
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    const dx = x + 0.5 - px, dy = y + 0.5 - py;
    const sx = px + dx * c - dy * s, sy = py + dx * s + dy * c; /* the inverse turn of the output pixel centre */
    const bx = Math.floor(sx * UP), by = Math.floor(sy * UP);
    if (bx >= 0 && by >= 0 && bx < b.w && by < b.h) out.px[y * p.w + x] = b.px[by * b.w + bx];
  }
  return out;
}

/** nearest-neighbour scale by (kx, ky) about the anchor (ax, ay) in art pixels (corner coordinates) */
function scale(p, kx, ky, ax, ay) {
  const out = new Plate(p.w, p.h);
  for (let y = 0; y < p.h; y++) for (let x = 0; x < p.w; x++) {
    const sx = Math.floor(ax + (x + 0.5 - ax) / kx), sy = Math.floor(ay + (y + 0.5 - ay) / ky);
    out.px[y * p.w + x] = p.get(sx, sy);
  }
  return out;
}

/** which source rows (or columns) a band of n keeps when it becomes n - k long (k < 0 repeats): evenly spread, deterministic */
function bandMap(n, k) {
  const m = n - k, map = [];
  for (let i = 0; i < m; i++) map.push(Math.min(n - 1, Math.floor(((i + 0.5) * n) / m)));
  return map;
}

/**
 * Legs-only band [ya, yb): removes k rows from it (k < 0 adds -k), keeping everything below yb in place and moving everything
 * above the band down by k. With k > 0 the cat crouches on the spot, with k < 0 it rises on longer legs.
 */
function legBand(p, ya, yb, k) {
  if (!k) return p.clone();
  const n = yb - ya, map = bandMap(n, k), out = new Plate(p.w, p.h), top = ya + k;
  for (let y = 0; y < p.h; y++) {
    let sy;
    if (y >= yb) sy = y; else if (y >= top) sy = ya + map[y - top]; else sy = y - k;
    for (let x = 0; x < p.w; x++) out.px[y * p.w + x] = p.get(x, sy);
  }
  return out;
}

/**
 * Torso band [xa, xb): removes k columns (k < 0 adds -k), the head side (x >= xb) staying in place and the rear moving by k.
 * The cat faces +x in every side view, so this lengthens or shortens the back without moving the face.
 */
function torsoBand(p, xa, xb, k) {
  if (!k) return p.clone();
  const n = xb - xa, map = bandMap(n, k), out = new Plate(p.w, p.h), left = xa + k;
  for (let x = 0; x < p.w; x++) {
    let sx;
    if (x >= xb) sx = x; else if (x >= left) sx = xa + map[x - left]; else sx = x - k;
    for (let y = 0; y < p.h; y++) out.px[y * p.w + x] = p.get(sx, y);
  }
  return out;
}

module.exports = { scale2x, rotate, scale, legBand, torsoBand, bandMap };
