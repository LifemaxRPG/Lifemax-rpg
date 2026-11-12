/* The three rooms the cat house grows by (docs/specs/CAT_HOUSE_EXPANSION_PLAN.md WP-H4): the Sunroom, the Study and the Bathroom.
   They are PLACEHOLDER art, drawn by this script in the style of the three hand-painted rooms (same 192 x 144 size, the wall
   meeting the floor on row 117, the same wall, wainscot, skirting and floor colour families, no black outlines: each piece is
   outlined in a darker shade of its own colour). An artist replaces a picture by dropping a file of the same name and size in
   place; nothing in the code changes unless a piece's box changes, and then only its row in layers_ext.json.

   Written to android-wrapper/native/src/main/resources/pet/room/:
     sun.png, study.png, bath.png      the finished backdrops: each room's base with its pieces drawn over it in manifest order
     layers/<room>_base.png            wall and floor only; a window's glass is left clear (the sky shows through)
     layers/<piece>.png                one picture per piece, tight box, clear where empty
     volume/<piece>.png                the depth map of each standing piece (red = front * 8, green = back * 8, alpha where painted)
     layers_ext.json                   the manifest rows of these pieces, in the format of layers.json (rooms and pieces)
   Raised places (WP-H2): a piece may carry a standable surface (`stand`: the key of its SurfaceId, the feet row and the room columns a
   cat walks on, and how many floor rows behind the piece's front plane it stands). The script checks each one lies on painted pixels
   of its piece and writes it to layers_ext.json (`surfaces`, `volume.surfaces`, and `stand` for the tests, which compare it with
   body/World.kt's table).
   house.json (which rooms there are, where they stand, when they open) is written by hand; this script checks that it lists
   the three rooms in the places the art is drawn for.

   Run:   node tools/gen_pet_room_new.js           writes the files (deterministic; running it twice changes nothing)
          node tools/gen_pet_room_new.js --check   fails if the committed files differ from what this script draws (CI) */
const fs = require('fs'), path = require('path');
const png = require('./pet_png.js');

const root = path.resolve(__dirname, '..');
const target = path.resolve(root, '../android-wrapper/native/src/main/resources/pet/room');
const W = 192, H = 144;
const WALL_ROW = 117; /* the row the back wall meets the floor on, in every room */

/* ---- drawing -------------------------------------------------------------------------------------------------------- */
const rgb = (hex) => [parseInt(hex.slice(0, 2), 16), parseInt(hex.slice(2, 4), 16), parseInt(hex.slice(4, 6), 16)];
class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.data = Buffer.alloc(w * h * 4) }
  px(x, y, hex) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const o = (y * this.w + x) * 4, c = rgb(hex);
    this.data[o] = c[0]; this.data[o + 1] = c[1]; this.data[o + 2] = c[2]; this.data[o + 3] = 255;
  }
  clear(x, y) { if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.data.fill(0, (y * this.w + x) * 4, (y * this.w + x) * 4 + 4) }
  rect(x, y, w, h, hex) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.px(x + i, y + j, hex) }
  hole(x, y, w, h) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.clear(x + i, y + j) }
  opaque(x, y) { return this.data[(y * this.w + x) * 4 + 3] !== 0 }
  /* a rounded ellipse blob, for plants */
  blob(cx, cy, rx, ry, hex) {
    for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
      const u = (x + 0.5 - cx) / rx, v = (y + 0.5 - cy) / ry;
      if (u * u + v * v <= 1) this.px(x, y, hex);
    }
  }
  /* lay another canvas over this one at (x, y) */
  over(o, x, y) {
    for (let j = 0; j < o.h; j++) for (let i = 0; i < o.w; i++) {
      const s = (j * o.w + i) * 4;
      if (o.data[s + 3] === 0) continue;
      const tx = x + i, ty = y + j;
      if (tx < 0 || ty < 0 || tx >= this.w || ty >= this.h) continue;
      o.data.copy(this.data, (ty * this.w + tx) * 4, s, s + 4);
    }
  }
  image() { return png.encode({ width: this.w, height: this.h, channels: 4, data: this.data }) }
}

/* colours: the families of the painted rooms (pet_room_cut_spec.js) */
const C = {
  wallCream: 'f1e6d4', wallCream2: 'ecdec4', rail: 'cdbfa8', railHi: 'f8f2e9', skirt: 'f1e6d4', skirtLow: 'cdbfa8',
  wallYellow: 'ecd896', wallYellow2: 'e4ce8a',
  frame: 'f7f0e2', frameHi: 'ffffff', frameLo: 'cfc2aa', lintel: 'ece2ce', lintelLo: 'cec0a8', sill: 'ece2ce', sillShade: 'f1e6d4',
  plank: ['b9814f', 'ac7646', '946238', 'c58e5a', 'a27348', '99693f'],
  tile: ['f0f2ec', 'c6d0cc'], tileLine: 'c4d2c0',
  sage: ['dee8d6', 'c4d2c0'], sageFloor: ['92aa96', 'e8dec6'], sageDark: '718475',
  blue: ['d8ecf2', 'bcd6e0'], blueLine: 'a9c6d2', blueDark: '7fa3b4', blueFloor: ['dfeef2', 'a9c8d4'],
  wood: ['8e5e35', '885a33', '684225', '5b3a20'], woodLight: 'b37e4e', woodHi: 'c58e5a',
  green: ['4fa36a', '347a50', '82c882', '2f7049'], pot: ['c4683a', '9c532e', 'd7723f'],
  wicker: ['d8b070', 'a98040', 'f0cf94', '8a6a34'],
  cushionYellow: ['f2c14e', 'd9a032', 'ffe08a'], cushionGreen: ['5e9a62', '3f7544', '86bf86'],
  deskGreen: ['3f6e4e', '2c5238', '5f9070'], white: ['f8f4ec', 'dcd6c8', 'b8b2a4'], metal: ['c9d2d6', '8d9a9f', 'eef3f4'],
  paper: 'f4ecd8', book: ['d9534f', '4f8ad9', 'e98a3a', '59b36b', 'a36028'],
};

/* ---- room bases ----------------------------------------------------------------------------------------------------- */
function plankFloor(c) {
  const bands = [[117, 1], [118, 1], [119, 1], [120, 1], [121, 2], [123, 1], [124, 1], [125, 1], [126, 5], [131, 1], [132, 1], [133, 1], [134, 1], [135, 1], [136, 1], [137, 2], [139, 1], [140, 4]];
  const shade = [2, 4, 0, 5, 0, 0, 1, 3, 0, 2, 3, 0, 1, 0, 1, 2, 3, 0];
  bands.forEach(([y, h], i) => c.rect(0, y, W, h, C.plank[shade[i] % C.plank.length]));
  /* the boards' ends, staggered, wider toward the glass */
  for (const [y0, y1, step] of [[117, 125, 22], [126, 134, 30], [135, 143, 42]]) {
    for (let row = y0, k = 0; row <= y1; row += 3, k++) for (let x = (k * 11) % step; x < W; x += step) c.px(x, row, C.plank[2]);
  }
}
function tileFloor(c, pal) {
  const rows = [[117, 5, 16], [122, 6, 20], [128, 7, 26], [135, 9, 34]]; /* y, height, tile width: the nearer, the bigger */
  rows.forEach(([y, h, w], r) => {
    for (let x = -((r % 2) * w / 2), k = 0; x < W; x += w, k++) c.rect(Math.round(x), y, w, h, pal[(k + r) % 2]);
    c.rect(0, y, W, 1, pal === C.blueFloor ? C.blueDark : C.sageDark);
  });
}
function wainscot(c, paper, line, top, rail) {
  c.rect(0, 84, W, 2, rail); /* chair rail */
  c.rect(0, 86, W, 25, paper);
  c.rect(0, 110, W, 1, C.railHi);
  c.rect(0, 111, W, 5, C.skirt);
  c.rect(0, 116, W, 1, C.skirtLow);
  for (let x = 6; x < W; x += 32) { c.rect(x, 90, 24, 17, line); c.rect(x + 1, 91, 22, 15, paper) } /* panel mouldings */
}
/* a window: the glass (64 x 56 at x + 22, y 14) left clear so the engine paints the sky there; the frame around it */
function window(c, ox, bars) {
  c.rect(ox + 16, 10, 76, 4, C.lintel); c.rect(ox + 16, 10, 76, 1, C.lintelLo);
  c.rect(ox + 16, 14, 6, 56, C.frame); c.rect(ox + 17, 14, 1, 56, C.frameHi); c.rect(ox + 20, 14, 2, 56, C.frameLo);
  c.rect(ox + 86, 14, 6, 56, C.frame); c.rect(ox + 88, 14, 2, 56, C.frame); c.rect(ox + 90, 14, 2, 56, C.frameLo);
  c.rect(ox + 16, 70, 76, 4, C.sill); c.rect(ox + 16, 74, 76, 3, C.sillShade); c.rect(ox + 18, 77, 72, 2, C.rail);
  c.hole(ox + 22, 14, 64, 56);
  if (bars) { c.rect(ox + 53, 14, 2, 56, C.frameHi); c.rect(ox + 54, 14, 1, 56, C.frameLo) } /* a mullion that stays in front of the sky */
  if (bars > 1) { c.rect(ox + 22, 41, 64, 2, C.frameHi); c.rect(ox + 22, 42, 64, 1, C.frameLo) }
}

function baseSun() {
  const c = new Canvas(W, H);
  c.rect(0, 0, W, 84, C.wallYellow); for (let y = 0; y < 84; y += 6) c.rect(0, y, W, 1, C.wallYellow2);
  wainscot(c, C.wallCream, 'e4d8bc', 86, C.rail);
  window(c, 40, 2);
  plankFloor(c);
  return c;
}
function baseStudy() {
  const c = new Canvas(W, H);
  const green = '4f7a5c', green2 = '466e53'; /* deep green: the room's one accent */
  c.rect(0, 0, W, 84, green); for (let x = 0; x < W; x += 12) c.rect(x, 0, 1, 84, green2);
  wainscot(c, 'e2d5bc', 'd2c4a8', 86, C.rail);
  window(c, 36, 1);
  plankFloor(c);
  return c;
}
function baseBath() {
  const c = new Canvas(W, H);
  c.rect(0, 0, W, 84, C.blue[0]);
  for (let y = 0; y < 84; y += 12) c.rect(0, y, W, 1, C.blueLine);
  for (let x = 0; x < W; x += 12) c.rect(x, 0, 1, 84, C.blueLine);
  c.rect(0, 84, W, 2, C.blueDark);
  c.rect(0, 86, W, 25, C.blue[1]);
  for (let y = 86; y < 111; y += 8) c.rect(0, y, W, 1, C.blueLine);
  for (let x = 0; x < W; x += 12) c.rect(x, 86, 1, 25, C.blueLine);
  c.rect(0, 110, W, 1, C.railHi); c.rect(0, 111, W, 5, C.white[0]); c.rect(0, 116, W, 1, C.white[1]);
  /* a frosted pane: painted, no sky (the room has no window in the map) */
  c.rect(56, 22, 56, 44, C.frame); c.rect(58, 24, 52, 40, 'e6f2f6');
  for (let y = 24; y < 64; y += 6) c.rect(58, y, 52, 1, 'd6e8ee');
  c.rect(56, 66, 56, 3, C.sill);
  tileFloor(c, C.blueFloor);
  return c;
}

/* ---- pieces --------------------------------------------------------------------------------------------------------- */
/* a lit box: the top face (th rows) lighter, the front face with a shaded lower edge, a darker outline in its own colour */
function box(c, x, y, w, h, th, pal) {
  const [front, side, hi] = pal;
  c.rect(x, y, w, h, front);
  c.rect(x, y, w, th, hi);
  c.rect(x, y + th, w, 1, side);
  c.rect(x, y + h - 2, w, 2, side);
  c.rect(x, y, 1, h, side); c.rect(x + w - 1, y, 1, h, side);
}
function plant(c, cx, baseY, h, rw) {
  const g = C.green;
  c.rect(cx - 5, baseY - 11, 10, 11, C.pot[0]); c.rect(cx - 5, baseY - 11, 10, 2, C.pot[2]); c.rect(cx - 5, baseY - 2, 10, 2, C.pot[1]);
  c.rect(cx - 1, baseY - 11 - 12, 2, 12, g[3]);
  c.blob(cx, baseY - 11 - h * 0.55, rw, h * 0.45, g[1]);
  c.blob(cx - rw * 0.35, baseY - 11 - h * 0.62, rw * 0.62, h * 0.32, g[0]);
  c.blob(cx + rw * 0.4, baseY - 11 - h * 0.7, rw * 0.5, h * 0.27, g[2]);
}
function cushion(c, x, y, w, h, pal) { c.rect(x, y, w, h, pal[0]); c.rect(x, y, w, 2, pal[2]); c.rect(x, y + h - 1, w, 1, pal[1]); c.rect(x, y, 1, h, pal[1]); c.rect(x + w - 1, y, 1, h, pal[1]) }

function sunBench() { /* 104 x 28: a window bench with two cushions */
  const c = new Canvas(104, 28), t = C.wood;
  box(c, 0, 8, 104, 20, 3, [C.woodLight, t[0], C.woodHi]);
  c.rect(4, 14, 96, 1, t[0]); for (let x = 6; x < 100; x += 24) c.rect(x, 16, 20, 8, t[1]); /* drawer fronts */
  c.rect(3, 26, 4, 2, t[2]); c.rect(97, 26, 4, 2, t[2]);
  cushion(c, 8, 2, 40, 7, C.cushionYellow); cushion(c, 48, 2, 42, 7, C.cushionYellow); cushion(c, 90, 2, 10, 7, C.cushionGreen); /* one unbroken seat */
  return c;
}
function wickerChair() { /* 42 x 46 */
  const c = new Canvas(42, 46), w = C.wicker;
  c.rect(4, 0, 34, 26, w[0]); c.rect(4, 0, 34, 2, w[2]); c.rect(4, 0, 2, 26, w[1]); c.rect(36, 0, 2, 26, w[1]);
  for (let y = 4; y < 24; y += 4) for (let x = 7; x < 35; x += 4) c.px(x + ((y / 4) % 2) * 2, y, w[3]); /* the weave */
  c.rect(0, 24, 42, 8, w[0]); c.rect(0, 24, 42, 2, w[2]); c.rect(0, 30, 42, 2, w[1]);
  cushion(c, 3, 21, 36, 6, C.cushionGreen);
  c.rect(3, 32, 3, 14, w[1]); c.rect(36, 32, 3, 14, w[1]); c.rect(7, 32, 28, 6, w[0]); c.rect(7, 37, 28, 1, w[1]);
  return c;
}
function bigPlant() { /* 30 x 70 */
  const c = new Canvas(30, 70);
  plant(c, 15, 70, 58, 14);
  c.blob(8, 24, 7, 6, C.green[0]); c.blob(22, 18, 6, 6, C.green[2]); c.blob(14, 8, 8, 7, C.green[0]);
  return c;
}
function doorFrame(left) { /* 14 x 100: two pillars' worth of frame seen from the side, drawn under every cat */
  const c = new Canvas(14, 100);
  c.rect(0, 0, 14, 6, C.lintel); c.rect(0, 0, 14, 1, C.lintelLo); c.rect(0, 5, 14, 1, C.frameLo);
  const x = left ? 0 : 8;
  c.rect(x, 6, 6, 94, C.frame); c.rect(x + (left ? 0 : 5), 6, 1, 94, C.frameLo); c.rect(x + (left ? 5 : 0), 6, 1, 94, C.frameHi);
  return c;
}

function desk() { /* 96 x 44: a green-topped desk with a drawer pedestal and a lamp */
  const c = new Canvas(96, 44), t = C.wood;
  box(c, 0, 12, 96, 6, 2, [C.deskGreen[0], C.deskGreen[1], C.deskGreen[2]]);
  c.rect(4, 18, 4, 26, t[0]); c.rect(4, 18, 1, 26, t[2]); c.rect(88, 18, 4, 26, t[0]); c.rect(91, 18, 1, 26, t[2]);
  box(c, 58, 18, 28, 26, 1, [t[0], t[2], C.woodLight]);
  for (const y of [21, 29, 37]) { c.rect(60, y, 24, 6, t[1]); c.rect(70, y + 2, 4, 1, C.woodHi) }
  c.rect(10, 6, 14, 6, C.book[1]); c.rect(10, 6, 14, 1, C.paper); c.rect(24, 8, 10, 4, C.book[0]); /* books */
  c.rect(70, 10, 10, 2, C.metal[1]); c.rect(74, 3, 2, 8, C.metal[1]); c.rect(66, 0, 12, 4, C.cushionYellow[0]); c.rect(66, 0, 12, 1, C.cushionYellow[2]); /* the lamp */
  return c;
}
function deskChair() { /* 28 x 44 */
  const c = new Canvas(28, 44), t = C.wood;
  c.rect(4, 0, 20, 22, C.deskGreen[0]); c.rect(4, 0, 20, 2, C.deskGreen[2]); c.rect(4, 0, 2, 22, C.deskGreen[1]); c.rect(22, 0, 2, 22, C.deskGreen[1]);
  c.rect(0, 22, 28, 6, C.deskGreen[0]); c.rect(0, 22, 28, 1, C.deskGreen[2]); c.rect(0, 27, 28, 1, C.deskGreen[1]);
  c.rect(3, 28, 3, 16, t[0]); c.rect(22, 28, 3, 16, t[0]); c.rect(6, 34, 16, 2, t[1]);
  return c;
}
function studyShelf() { /* 36 x 94: a bookshelf a cat reaches from the desk */
  const c = new Canvas(36, 94), t = C.wood;
  c.rect(0, 0, 36, 94, t[0]); c.rect(0, 0, 36, 3, C.woodLight); c.rect(0, 0, 2, 94, t[2]); c.rect(34, 0, 2, 94, t[2]);
  for (let i = 0; i < 4; i++) {
    const y = 6 + i * 21; c.rect(2, y, 32, 17, t[3]);
    let x = 3;
    for (let b = 0; x < 32; b++) { const bw = 3 + (b * 7 + i) % 4, bh = 11 + (b * 5 + i * 3) % 6; c.rect(x, y + 17 - bh, bw, bh, C.book[(b + i) % C.book.length]); x += bw + 1 }
    c.rect(2, y + 17, 32, 2, t[1]);
  }
  c.rect(0, 92, 36, 2, t[2]);
  return c;
}
function paperBasket() { /* 30 x 24: a wicker bin with crumpled paper */
  const c = new Canvas(30, 24), w = C.wicker;
  c.rect(2, 4, 26, 20, w[0]); c.rect(2, 4, 26, 2, w[2]); c.rect(2, 22, 26, 2, w[1]); c.rect(2, 4, 2, 20, w[1]); c.rect(26, 4, 2, 20, w[1]);
  for (let y = 9; y < 21; y += 4) for (let x = 5; x < 26; x += 4) c.px(x + ((y / 4) % 2) * 2, y, w[3]);
  c.blob(10, 3, 5, 3, C.paper); c.blob(19, 2, 4, 2, 'e8e0d0'); c.px(9, 2, w[1]);
  return c;
}
function studyRug() { /* 100 x 12, flat */
  const c = new Canvas(100, 12);
  c.rect(0, 0, 100, 12, '6a8a70'); c.rect(3, 3, 94, 6, '86a88b'); c.rect(8, 5, 84, 2, 'e2d5bc');
  for (let x = 0; x < 100; x += 4) { c.px(x, 0, 'e2d5bc'); c.px(x, 11, 'e2d5bc') }
  return c;
}

function bathtub() { /* 100 x 40: a claw-foot tub */
  const c = new Canvas(100, 40), w = C.white;
  c.rect(0, 4, 100, 28, w[0]); c.rect(0, 4, 100, 4, 'ffffff'); c.rect(0, 8, 100, 1, w[1]);
  c.rect(0, 30, 100, 2, w[1]); c.rect(0, 4, 2, 28, w[1]); c.rect(98, 4, 2, 28, w[1]);
  c.rect(6, 12, 88, 3, 'c8e0ea'); /* the tile's blue showing in the dry tub's inner wall */
  for (const x of [6, 86]) { c.rect(x, 32, 8, 8, C.metal[1]); c.rect(x + 1, 32, 6, 2, C.metal[0]); c.rect(x - 1, 38, 10, 2, C.metal[1]) }
  c.rect(88, 0, 3, 8, C.metal[1]); c.rect(84, 0, 8, 2, C.metal[0]); c.rect(82, 2, 3, 2, C.metal[1]); /* the tap */
  return c;
}
function sinkCabinet() { /* 56 x 50: a cabinet with a basin and a tap */
  const c = new Canvas(56, 50), t = C.wood;
  c.rect(0, 14, 56, 36, 'e8e0cc'); c.rect(0, 14, 2, 36, 'c9bfa6'); c.rect(54, 14, 2, 36, 'c9bfa6'); c.rect(0, 48, 56, 2, 'c9bfa6');
  c.rect(3, 22, 24, 23, 'f4ecd8'); c.rect(29, 22, 24, 23, 'f4ecd8');
  c.rect(24, 32, 2, 5, t[2]); c.rect(30, 32, 2, 5, t[2]);
  box(c, 0, 8, 56, 8, 3, [C.white[0], C.white[1], 'ffffff']);
  c.rect(8, 9, 28, 2, 'c8e0ea');
  c.rect(40, 0, 3, 10, C.metal[1]); c.rect(36, 0, 8, 2, C.metal[0]); c.rect(36, 2, 2, 3, C.metal[1]);
  return c;
}
function laundryBasket() { /* 28 x 30 */
  const c = new Canvas(28, 30), w = C.wicker;
  c.rect(2, 8, 24, 22, w[0]); c.rect(2, 8, 24, 2, w[2]); c.rect(2, 28, 24, 2, w[1]); c.rect(2, 8, 2, 22, w[1]); c.rect(24, 8, 2, 22, w[1]);
  for (let y = 13; y < 27; y += 4) for (let x = 5; x < 24; x += 4) c.px(x + ((y / 4) % 2) * 2, y, w[3]);
  c.blob(9, 6, 7, 5, 'f4ecd8'); c.blob(18, 5, 6, 4, C.blue[1]); c.rect(6, 6, 6, 1, 'ffffff');
  return c;
}
function bathMat() { /* 60 x 10, flat */
  const c = new Canvas(60, 10);
  c.rect(0, 0, 60, 10, 'e6c24e'); c.rect(2, 2, 56, 6, 'f2d878'); c.rect(6, 4, 48, 2, 'e6c24e');
  for (let x = 0; x < 60; x += 3) { c.px(x, 0, 'f2d878'); c.px(x, 9, 'f2d878') }
  return c;
}

/* ---- the rooms: pieces in draw order (back to front) ----------------------------------------------------------------- */
/* depth: how many floor rows deep; wall: its back is against the wall; flat: lies on the floor or is drawn under everything */
const ROOMS = {
  sun: { base: baseSun, pieces: [
    { name: 'door_frame_sun', make: () => doorFrame(true), x: 0, y: 17, flat: true },
    { name: 'sun_bench', make: sunBench, x: 40, y: 100, depth: 8, wall: true, occludes: true, stand: { key: 'bench', row: 2, x0: 10, x1: 98, depth: 3 } },
    { name: 'wicker_chair', make: wickerChair, x: 142, y: 82, depth: 8, wall: false, occludes: true },
    { name: 'big_plant', make: bigPlant, x: 6, y: 58, depth: 6, wall: false, occludes: true },
  ] },
  study: { base: baseStudy, pieces: [
    { name: 'door_frame_study', make: () => doorFrame(false), x: 178, y: 17, flat: true },
    { name: 'study_rug', make: studyRug, x: 40, y: 130, flat: true, floorRug: true },
    { name: 'study_shelf', make: studyShelf, x: 8, y: 34, depth: 7, wall: true, occludes: true, stand: { key: 'shelf', row: 0, x0: 4, x1: 32, depth: 2 } },
    { name: 'desk', make: desk, x: 52, y: 84, depth: 10, wall: true, occludes: true, stand: { key: 'desk', row: 12, x0: 4, x1: 92, depth: 4 } },
    { name: 'desk_chair', make: deskChair, x: 148, y: 84, depth: 6, wall: false, occludes: true },
    { name: 'paper_basket', make: paperBasket, x: 118, y: 104, depth: 5, wall: false, occludes: true },
  ] },
  bath: { base: baseBath, pieces: [
    { name: 'door_frame_bath', make: () => doorFrame(false), x: 178, y: 17, flat: true },
    { name: 'bath_mat', make: bathMat, x: 50, y: 132, flat: true, floorRug: true },
    { name: 'bathtub', make: bathtub, x: 4, y: 88, depth: 10, wall: true, occludes: true, stand: { key: 'tub', row: 4, x0: 6, x1: 78, depth: 4 } },
    { name: 'sink_cabinet', make: sinkCabinet, x: 122, y: 72, depth: 8, wall: true, occludes: true, stand: { key: 'sink', row: 8, x0: 4, x1: 30, depth: 3 } },
    { name: 'laundry_basket', make: laundryBasket, x: 106, y: 102, depth: 5, wall: false, occludes: true },
  ] },
};

/* z the page's depth of a floor row (zFromRow in room/Parallax.kt) */
const zFromRow = (row) => Math.max(0, Math.min(100, (((row - 98) / (132 - 98)) * 100 - 55) * 100 / 130));
const round2 = (v) => Math.round(v * 100) / 100;

/* the depth map of a standing piece: the front face one unit behind its plane, the top face receding to the back */
function volumeOf(canvas, depth, topRows) {
  const v = Buffer.alloc(canvas.w * canvas.h * 4), enc = (x) => Math.round(Math.max(0, Math.min(31.875, x)) * 8);
  for (let y = 0; y < canvas.h; y++) for (let x = 0; x < canvas.w; x++) {
    const o = (y * canvas.w + x) * 4;
    if (canvas.data[o + 3] === 0) continue;
    const ramp = y < topRows ? 1 + (depth - 2) * (1 - y / topRows) : 1;
    v[o] = enc(ramp); v[o + 1] = enc(depth); v[o + 2] = 0; v[o + 3] = 255;
  }
  return png.encode({ width: canvas.w, height: canvas.h, channels: 4, data: v });
}

function build() {
  const files = new Map(), manifestRooms = {}, rows = [];
  for (const [id, room] of Object.entries(ROOMS)) {
    const base = room.base(), backdrop = new Canvas(W, H);
    backdrop.over(base, 0, 0);
    files.set(`layers/${id}_base.png`, base.image());
    manifestRooms[id] = { base: `${id}_base.png` };
    for (const p of room.pieces) {
      const c = p.make();
      if (p.x + c.w > W || p.y + c.h > H) throw new Error(`${p.name} leaves the room`);
      backdrop.over(c, p.x, p.y);
      files.set(`layers/${p.name}.png`, c.image());
      const baseY = p.flat ? null : p.y + c.h;
      const row = { name: p.name, room: id, file: `${p.name}.png`, x: p.x, y: p.y, w: c.w, h: c.h, baseY, z: baseY === null ? null : round2(zFromRow(baseY)),
        occludes: !!p.occludes, surfaces: p.stand ? [p.stand.key] : [], volume: null };
      if (!p.flat) {
        files.set(`volume/${p.name}.png`, volumeOf(c, p.depth, Math.min(6, c.h)));
        row.volume = { file: `volume/${p.name}.png`, front: 0, depth: p.depth, wall: !!p.wall, surfaces: p.stand ? { [p.stand.key]: p.stand.depth } : {}, covered: {} };
      }
      if (p.stand) {
        /* the feet row and the columns a cat walks on, in the room's own pixels; the whole walk must lie on painted columns of the piece */
        const s = p.stand;
        if (p.flat || s.depth > p.depth || s.row < 0 || s.row >= c.h) throw new Error(`${p.name}: bad stand`);
        for (let x = s.x0; x <= s.x1; x++) {
          let painted = false;
          for (let y = s.row - 1; y <= s.row + 3; y++) if (y >= 0 && y < c.h && c.opaque(x, y)) painted = true;
          if (!painted) throw new Error(`${p.name}: no paint under the ${s.key} surface at column ${x}`);
        }
        row.stand = { [s.key]: [p.y + s.row, p.x + s.x0, p.x + s.x1] };
      }
      rows.push(row);
    }
    files.set(`${id}.png`, backdrop.image());
  }
  const lines = rows.map((r) => ' ' + JSON.stringify(r));
  files.set('layers_ext.json', Buffer.from(`{"roomWidth":${W},"roomHeight":${H},"rooms":${JSON.stringify(manifestRooms)},\n"pieces":[\n${lines.join(',\n')}\n]}\n`));
  return files;
}

function checkHouse() {
  const house = JSON.parse(fs.readFileSync(path.join(target, 'house.json'), 'utf8'));
  const want = { bath: -576, study: -384, sun: 384 };
  for (const [id, x0] of Object.entries(want)) {
    const r = house.rooms.find((o) => o.id === id);
    if (!r || r.x0 !== x0 || r.core) throw new Error(`house.json: ${id} must stand at x ${x0} and not be a core room`);
  }
  const xs = house.rooms.map((r) => r.x0);
  if (xs.join() !== xs.slice().sort((a, b) => a - b).join()) throw new Error('house.json: rooms are not in order from the left');
}

function main() {
  checkHouse();
  const files = build();
  if (process.argv.includes('--check')) {
    /* two PNG files are the same when they decode to the same pixels (the compressor may differ between zlib versions) */
    const same = (name, a, b) => {
      if (!name.endsWith('.png')) return a.equals(b);
      try { const p = png.decode(a), q = png.decode(b); return p.width === q.width && p.height === q.height && p.channels === q.channels && p.data.equals(q.data) } catch (e) { return false }
    };
    const bad = [...files].filter(([name, buf]) => !fs.existsSync(path.join(target, name)) || !same(name, fs.readFileSync(path.join(target, name)), buf));
    if (bad.length) { console.error('gen_pet_room_new: out of date: ' + bad.map(([n]) => n).join(', ')); process.exit(1) }
    console.log(`gen_pet_room_new: ${files.size} files match`);
    return;
  }
  for (const [name, buf] of files) {
    const file = path.join(target, name);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    if (!fs.existsSync(file) || !fs.readFileSync(file).equals(buf)) fs.writeFileSync(file, buf);
  }
  console.log(`gen_pet_room_new: wrote ${files.size} files`);
}

if (require.main === module) main();
module.exports = { build, ROOMS };
