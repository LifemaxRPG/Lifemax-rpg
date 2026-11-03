/* Contact sheets: frames drawn big enough to judge by eye (and by the Read tool) at full resolution.
   A strip is a row of frames; a montage stacks strips with a small label above each. Every frame cell shows the foot row
   (a green line where the paws stand), optional marks (the mouth anchor as a red cross) and a faint checker so clear pixels
   read as clear. Colours come from one legacy coat's palette (petAssets.pal). Output: RGBA PNG through pet_png.js. */
'use strict';
const png = require('../../pet_png.js');
const { channel } = require('./plate.js');

const BG = [52, 58, 72], CHECK = [[60, 66, 82], [56, 62, 77]], FOOT = [96, 200, 120], MARK = [255, 64, 64], TEXT = [230, 232, 240], GAP = 3;

/* a 3 x 5 pixel font for labels: lower case, digits and a few signs; each glyph is 5 rows of 3 bits */
const FONT = {
  a: [2, 5, 7, 5, 5], b: [6, 5, 6, 5, 6], c: [3, 4, 4, 4, 3], d: [6, 5, 5, 5, 6], e: [7, 4, 6, 4, 7], f: [7, 4, 6, 4, 4], g: [3, 4, 5, 5, 3],
  h: [5, 5, 7, 5, 5], i: [7, 2, 2, 2, 7], j: [1, 1, 1, 5, 2], k: [5, 5, 6, 5, 5], l: [4, 4, 4, 4, 7], m: [5, 7, 7, 5, 5], n: [6, 5, 5, 5, 5],
  o: [2, 5, 5, 5, 2], p: [6, 5, 6, 4, 4], q: [2, 5, 5, 6, 3], r: [6, 5, 6, 5, 5], s: [3, 4, 2, 1, 6], t: [7, 2, 2, 2, 2], u: [5, 5, 5, 5, 7],
  v: [5, 5, 5, 5, 2], w: [5, 5, 7, 7, 5], x: [5, 5, 2, 5, 5], y: [5, 5, 2, 2, 2], z: [7, 1, 2, 4, 7], 0: [7, 5, 5, 5, 7], 1: [2, 6, 2, 2, 7],
  2: [6, 1, 2, 4, 7], 3: [6, 1, 2, 1, 6], 4: [5, 5, 7, 1, 1], 5: [7, 4, 6, 1, 6], 6: [3, 4, 7, 5, 7], 7: [7, 1, 2, 2, 2], 8: [7, 5, 7, 5, 7],
  9: [7, 5, 7, 1, 6], _: [0, 0, 0, 0, 7], '-': [0, 0, 7, 0, 0], '#': [5, 7, 5, 7, 5], '.': [0, 0, 0, 0, 2], ' ': [0, 0, 0, 0, 0], '/': [1, 1, 2, 4, 4],
};

class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.d = Buffer.alloc(w * h * 4); this.fill(0, 0, w, h, BG) }
  dot(x, y, c) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.d[i] = c[0]; this.d[i + 1] = c[1]; this.d[i + 2] = c[2]; this.d[i + 3] = 255 }
  fill(x, y, w, h, c) { for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) this.dot(xx, yy, c) }
  text(x, y, s, k = 2) {
    for (const ch of String(s).toLowerCase()) {
      const g = FONT[ch] || FONT[' '];
      g.forEach((bits, r) => { for (let b = 0; b < 3; b++) if (bits & (4 >> b)) this.fill(x + b * k, y + r * k, k, k, TEXT) });
      x += 4 * k;
    }
  }
  png() { return png.encode({ width: this.w, height: this.h, channels: 4, data: this.d }) }
}

/** draws one frame (a Plate) of coat c with palette pal at (ox, oy), each art pixel s x s; opts.foot, opts.marks [[x,y]] */
function drawFrame(cv, fr, c, pal, ox, oy, s, opts = {}) {
  for (let y = 0; y < fr.h; y++) for (let x = 0; x < fr.w; x++) {
    const v = channel(fr.px[y * fr.w + x], c);
    const col = v ? (pal[String(v)] || [255, 0, 255]) : CHECK[(x + y) & 1];
    cv.fill(ox + x * s, oy + y * s, s, s, col);
  }
  if (opts.foot != null) cv.fill(ox, oy + opts.foot * s + s - 1, fr.w * s, 1, FOOT);
  for (const [mx, my] of opts.marks || []) {
    const cx = ox + Math.round(mx * s + s / 2), cy = oy + Math.round(my * s + s / 2);
    for (let k = -s; k <= s; k++) { cv.dot(cx + k, cy, MARK); cv.dot(cx, cy + k, MARK) }
  }
}

/**
 * A montage: rows = [{ label, frames: [Plate], foot, marks: [[x,y] | null per frame] }], drawn for coat index c.
 * Returns a PNG buffer. scale is art pixels to image pixels (6 for review).
 */
function montage(rows, c, pal, scale = 6) {
  const cols = Math.max(1, ...rows.map((r) => r.frames.length));
  const fw = rows[0] ? rows[0].frames[0].w : 72, fh = rows[0] ? rows[0].frames[0].h : 48, lab = 14;
  const cv = new Canvas(GAP + cols * (fw * scale + GAP), GAP + rows.length * (fh * scale + GAP + lab));
  rows.forEach((r, ri) => {
    const oy = GAP + ri * (fh * scale + GAP + lab);
    cv.text(GAP + 2, oy + 2, r.label || '');
    r.frames.forEach((fr, fi) => {
      const m = r.marks && r.marks[fi];
      drawFrame(cv, fr, c, pal, GAP + fi * (fw * scale + GAP), oy + lab, scale, { foot: r.foot, marks: m ? [m] : [] });
    });
  });
  return cv.png();
}

module.exports = { Canvas, drawFrame, montage };
