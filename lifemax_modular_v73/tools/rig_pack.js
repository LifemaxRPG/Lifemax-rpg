/* Packs the page's cat rig (src/data/petRig.html, JSON v2) into the compact binary the native engine reads, one file per
   kind (feature/pet/art/RigReader.kt is the reader; the two agree on the layout written here).

   File  rig/<kind>.bin, kind = adult, st0..st5, depth, dst0..dst5. Little endian throughout.
     u8  format version (1)
     u8  frame width      u8  frame height
     u8  number of animations
     per animation, in the page's row order:
       u8 name length, the name (ascii)
       u8 flags: 1 = legs can be stretched (lg), 2 = torso can be stretched (bd), 4 = the tail is a part (tl)
       u8 frame count n, then n x u32 frame offsets, counted from the first byte after the directory
     the frames, back to back. One frame:
       u8 flags: 1 leg band, 2 torso band, 4 tail, 8 head anchor
       u8 foot row (y)
       [u8 ya, u8 yb]        leg band, rows ya <= y < yb          (flag 1)
       [u8 xa, u8 xb]        torso band, columns xa <= x < xb      (flag 2)
       [i16 x, i16 y]        head anchor in tenths of a pixel      (flag 8)
       u8 ear count, then per ear: base (i16 x, i16 y tenths), tip (i16 x, i16 y tenths), pixel runs
       u8 whisker count, then per whisker: the same as an ear
       [u8 vertices, then i16 x, i16 y tenths per vertex, pixel runs]   the tail (flag 4)
     pixel runs: u16 count, then count x (u8 row, u8 x0, u8 x1): the page's `r` triples, in the page's order
   Every coordinate of the page's data has one decimal; the packer refuses anything else, so tenths lose nothing. */
const FLAG = { leg: 1, torso: 2, tail: 4, head: 8 };
const ANIM_FLAG = { lg: 1, bd: 2, tl: 4 };
const VERSION = 1;

class Out {
  constructor() { this.bytes = [] }
  u8(v) { if (!Number.isInteger(v) || v < 0 || v > 255) throw new Error('u8 out of range: ' + v); this.bytes.push(v) }
  u16(v) { if (!Number.isInteger(v) || v < 0 || v > 65535) throw new Error('u16 out of range: ' + v); this.bytes.push(v & 255, v >> 8) }
  u32(v) { for (let i = 0; i < 4; i++) this.bytes.push(Math.floor(v / 2 ** (8 * i)) & 255) }
  i16(v) { if (!Number.isInteger(v) || v < -32768 || v > 32767) throw new Error('i16 out of range: ' + v); this.u16(v & 0xffff) }
  tenths(v) {
    const t = Math.round(v * 10);
    if (Math.abs(v * 10 - t) > 1e-6) throw new Error('not a whole number of tenths: ' + v);
    this.i16(t);
  }
  buffer() { return Buffer.from(this.bytes) }
}

function runs(o, r) {
  if (r.length % 3) throw new Error('pixel runs are not triples');
  o.u16(r.length / 3);
  for (const v of r) o.u8(v);
}
function part(o, p) {
  o.tenths(p.b[0]); o.tenths(p.b[1]); o.tenths(p.t[0]); o.tenths(p.t[1]);
  runs(o, p.r);
}

function frame(o, row, fr) {
  const tail = row.tl && fr.t ? fr.t : null;
  o.u8((fr.l ? FLAG.leg : 0) | (fr.x ? FLAG.torso : 0) | (tail ? FLAG.tail : 0) | (fr.h ? FLAG.head : 0));
  o.u8(fr.y);
  if (fr.l) { o.u8(fr.l[0]); o.u8(fr.l[1]) }
  if (fr.x) { o.u8(fr.x[0]); o.u8(fr.x[1]) }
  if (fr.h) { o.tenths(fr.h[0]); o.tenths(fr.h[1]) }
  o.u8((fr.e || []).length); for (const e of fr.e || []) part(o, e);
  o.u8((fr.w || []).length); for (const w of fr.w || []) part(o, w);
  if (tail) {
    o.u8(tail.a.length);
    for (const p of tail.a) { o.tenths(p[0]); o.tenths(p[1]) }
    runs(o, tail.r);
  }
}

/** One kind (a section of the page's JSON) as a file. `frameCounts` is the art's frame count per animation, which must match. */
function packKind(kindName, section, W, H, frameCounts) {
  const names = Object.keys(section);
  const blob = new Out(), dir = new Out(), offsets = [];
  dir.u8(VERSION); dir.u8(W); dir.u8(H); dir.u8(names.length);
  for (const name of names) {
    const row = section[name];
    if (frameCounts[name] !== row.f.length) throw new Error(`${kindName}/${name}: ${row.f.length} rig frames, the art has ${frameCounts[name]}`);
    dir.u8(name.length); for (const ch of name) dir.u8(ch.charCodeAt(0));
    dir.u8(Object.entries(ANIM_FLAG).reduce((a, [k, bit]) => a | (row[k] ? bit : 0), 0));
    dir.u8(row.f.length);
    for (const fr of row.f) {
      if (!fr) throw new Error(`${kindName}/${name}: a frame is missing`);
      offsets.push(blob.bytes.length);
      dir.u32(offsets[offsets.length - 1]);
      frame(blob, row, fr);
    }
  }
  return Buffer.concat([dir.buffer(), blob.buffer()]);
}

module.exports = { packKind, VERSION };
