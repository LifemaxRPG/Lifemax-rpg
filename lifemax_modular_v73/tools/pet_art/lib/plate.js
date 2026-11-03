/* A plate is one piece of cat art for all four legacy coats at once.
   Every pixel holds the four coats' palette indices packed into one 32-bit word: ginger in bits 0..7, gray 8..15, black 16..23,
   tuxedo 24..31 (COATS order). The four coat sheets are drawn on the same grid (petDnaCompose reads them pixel by pixel), so a
   geometric operation that moves packed words moves every coat the same way and the coats stay registered: a new frame made
   from a plate is the same shape in every coat and DNA coats composed from them stay exact.
   0 is a clear pixel in every coat. OUTLINE (index 10) is the dark outline every form is drawn with. */
'use strict';

const COATS = ['ginger', 'gray', 'black', 'tuxedo'];
const OUTLINE = 10;
const WHISKER = 190;
const PACKED_OUTLINE = (OUTLINE | (OUTLINE << 8) | (OUTLINE << 16) | (OUTLINE << 24)) >>> 0;

/** the palette index of coat c (0..3) in packed word v */
const channel = (v, c) => (v >>> (8 * c)) & 255;
/** a packed word with the same index in every coat */
const uniform = (i) => (i | (i << 8) | (i << 16) | (i << 24)) >>> 0;
/** true if any coat draws an outline at v (the coats agree on the outline everywhere except a few whisker tips) */
const isOutline = (v) => channel(v, 2) === OUTLINE || channel(v, 0) === OUTLINE;
/** true if v is fur, face or any filled pixel that is not outline */
const isFill = (v) => v !== 0 && !isOutline(v);

class Plate {
  /** w x h, all clear, or wrapping px (a Uint32Array of w*h packed words) */
  constructor(w, h, px) {
    this.w = w; this.h = h; this.px = px || new Uint32Array(w * h);
    if (this.px.length !== w * h) throw new Error('plate size mismatch');
  }
  get(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h ? this.px[y * this.w + x] : 0 }
  set(x, y, v) { if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.px[y * this.w + x] = v >>> 0 }
  clone() { return new Plate(this.w, this.h, this.px.slice()) }
  /** the w x h piece at (x, y) */
  crop(x, y, w, h) {
    const out = new Plate(w, h);
    for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) out.px[yy * w + xx] = this.get(x + xx, y + yy);
    return out;
  }
  /** draws src over this plate with its top-left at (x, y); clear pixels of src leave this plate as it is */
  blit(src, x = 0, y = 0) {
    for (let yy = 0; yy < src.h; yy++) for (let xx = 0; xx < src.w; xx++) {
      const v = src.px[yy * src.w + xx]; if (v) this.set(x + xx, y + yy, v);
    }
    return this;
  }
  /** left-right mirror */
  mirror() {
    const out = new Plate(this.w, this.h);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) out.px[y * this.w + (this.w - 1 - x)] = this.px[y * this.w + x];
    return out;
  }
  /** moved by whole pixels (pixels pushed outside are lost) */
  shift(dx, dy) {
    const out = new Plate(this.w, this.h);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) { const v = this.px[y * this.w + x]; if (v) out.set(x + dx, y + dy, v) }
    return out;
  }
  /** {x0, y0, x1, y1} of the opaque pixels (inclusive), or null when empty */
  bbox() {
    let x0 = this.w, y0 = this.h, x1 = -1, y1 = -1;
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) if (this.px[y * this.w + x]) {
      if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
    }
    return x1 < 0 ? null : { x0, y0, x1, y1 };
  }
  count() { let n = 0; for (const v of this.px) if (v) n++; return n }
  equals(o) { if (o.w !== this.w || o.h !== this.h) return false; for (let i = 0; i < this.px.length; i++) if (o.px[i] !== this.px[i]) return false; return true }
  /** the palette-index image of one coat (Uint8Array w*h) */
  coat(c) { const out = new Uint8Array(this.w * this.h); for (let i = 0; i < out.length; i++) out[i] = channel(this.px[i], c); return out }
  /** a plate keeping only the pixels where keep(x, y, v) is true */
  filter(keep) {
    const out = new Plate(this.w, this.h);
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) { const v = this.px[y * this.w + x]; if (v && keep(x, y, v)) out.px[y * this.w + x] = v }
    return out;
  }
}

/** packs four index images (COATS order) of the same size into a plate */
function packCoats(w, h, images) {
  const p = new Plate(w, h);
  for (let i = 0; i < w * h; i++) p.px[i] = (images[0][i] | (images[1][i] << 8) | (images[2][i] << 16) | (images[3][i] << 24)) >>> 0;
  return p;
}

module.exports = { Plate, COATS, OUTLINE, WHISKER, PACKED_OUTLINE, channel, uniform, isOutline, isFill, packCoats };
