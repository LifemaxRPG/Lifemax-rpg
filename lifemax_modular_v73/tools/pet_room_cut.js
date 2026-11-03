/* Cuts the painted furniture out of a room backdrop into sprites and rebuilds the wall and floor behind it.
   Pure functions on RGBA images ({ width, height, data }); no files are read or written here (gen_pet_native_assets.js does that).

     cutRoom(image, room)  ->  { base, pieces }
       base    the backdrop with every piece removed and the wall/floor behind them rebuilt (an RGBA image of the same size)
       pieces  [{ name, x, y, width, height, data, ...flags }] in back-to-front order; each is the tight bounding box of the
               piece, opaque where the piece is, clear elsewhere
     compose(base, pieces) draws the pieces over the base in order (what the room does with no camera sway).

   The promise, checked by gen_pet_room_layers.js and the Kotlin RoomLayersTest: compose(base, pieces) equals the original
   backdrop exactly, pixel for pixel. It holds by construction: a piece keeps the original pixels it claims, and wherever a later
   piece covers part of an earlier one (or a piece covers the wall), the invented pixels lie only under pixels that are drawn
   over them again.

   Which pixels are which piece, and how the wall and floor are rebuilt, is data: pet_room_cut_spec.js. */
const { ROOMS, ROOM_W, ROOM_H } = require('./pet_room_cut_spec.js');

const key = (d, o) => (d[o] << 16 | d[o + 1] << 8 | d[o + 2]).toString(16).padStart(6, '0');
const inRect = (r, x, y) => x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];

/* which piece claims each pixel: an Int16Array of piece index or -1 */
function claim(img, room) {
  const { width: W, data } = img, owner = new Int16Array(W * img.height).fill(-1);
  room.pieces.forEach((p, i) => {
    for (const e of p.entries) {
      const only = e.only && new Set(e.only), not = e.not && new Set(e.not);
      for (let y = e.rect[1]; y <= e.rect[3]; y++) for (let x = e.rect[0]; x <= e.rect[2]; x++) {
        const o = (y * W + x) * 4;
        if (data[o + 3] !== 255) continue;
        const c = key(data, o);
        if ((only && !only.has(c)) || (not && not.has(c))) continue;
        owner[y * W + x] = i;
      }
    }
  });
  return owner;
}

/* The holes a later piece leaves inside a piece's box: whole[at] = -2 - i marks a pixel of piece i that is drawn over by a later
   piece, and colour holds what piece i shows there. The hole takes the colour of the nearest pixel of its own piece on the same
   row (left first), or on the same column when the piece says holes: 'column' (a post), so a seam or a shelf board runs on. */
function completePieces(img, room, owner) {
  const { width: W, height: H, data } = img;
  const whole = owner.slice(), colour = new Uint8Array(W * H * 4);
  room.pieces.forEach((p, i) => {
    if (!p.box) return;
    for (let y = p.box[1]; y <= p.box[3]; y++) for (let x = p.box[0]; x <= p.box[2]; x++) {
      const at = y * W + x;
      if (owner[at] <= i) continue;
      let src = -1;
      for (let d = 1; src < 0 && d < Math.max(W, H); d++) {
        const near = p.holes === 'column' ? [[0, -d], [0, d]] : [[-d, 0], [d, 0]];
        for (const [dx, dy] of near) {
          const sx = x + dx, sy = y + dy;
          if (sx >= 0 && sy >= 0 && sx < W && sy < H && owner[sy * W + sx] === i) { src = sy * W + sx; break }
        }
      }
      if (src < 0) throw new Error(`${p.name}: no pixel to fill the hole at ${x},${y}`);
      whole[at] = -2 - i;
      colour.set(data.subarray(src * 4, src * 4 + 4), at * 4);
    }
  });
  return { whole, colour };
}

/* The base: the backdrop with every claimed or filled pixel taken out and rebuilt.
   A fill region is { rect, avoid?, repeat }. repeat is a list of [px, py] pattern repeats tried in order (the first vector that has a
   source anywhere wins, at its smallest multiple k, minus side first), or 'auto': the wall or floor repeats along the row with a
   period found from the row's own live pixels. A source must be live (not removed, opaque), inside the region's rect and outside
   its avoid rectangles. With no source the pixel takes the nearest live one on its row, then on its column. */
function rebuildBase(img, room, owner, whole) {
  const { width: W, height: H, data } = img, out = Uint8Array.from(data);
  const gone = new Uint8Array(W * H);
  for (let i = 0; i < W * H; i++) if (owner[i] >= 0 || whole[i] <= -2) gone[i] = 1;
  const fills = room.fills, everything = { rect: [0, 0, W - 1, H - 1], repeat: [] };
  const live = (x, y, r) => x >= 0 && y >= 0 && x < W && y < H && inRect(r.rect, x, y) && !gone[y * W + x] &&
    data[(y * W + x) * 4 + 3] === 255 && !(r.avoid && r.avoid.some((a) => inRect(a, x, y)));
  const same = (a, b) => data[a * 4] === data[b * 4] && data[a * 4 + 1] === data[b * 4 + 1] && data[a * 4 + 2] === data[b * 4 + 2];
  const periods = new Map();
  const periodOf = (r, y) => {
    const id = fills.indexOf(r) + ':' + y;
    if (periods.has(id)) return periods.get(id);
    let best = 0;
    for (let p = 4; p <= 96 && !best; p++) {
      let n = 0, m = 0;
      for (let x = r.rect[0]; x + p <= r.rect[2]; x++) if (live(x, y, r) && live(x + p, y, r)) { n++; if (same(y * W + x, y * W + x + p)) m++ }
      if (n >= 8 && m / n >= 0.97) best = p;
    }
    periods.set(id, best);
    return best;
  };
  const find = (x, y, r) => {
    const vectors = r.repeat === 'auto' ? (periodOf(r, y) ? [[periodOf(r, y), 0]] : []) : r.repeat;
    for (const [px, py] of vectors) for (let k = 1; k < 200; k++) for (const s of [-1, 1]) {
      const sx = x + s * k * px, sy = y + s * k * py;
      if (live(sx, sy, r)) return sy * W + sx;
    }
    for (let d = 1; d < W; d++) { if (live(x - d, y, r)) return y * W + x - d; if (live(x + d, y, r)) return y * W + x + d }
    for (let d = 1; d < H; d++) { if (live(x, y - d, r)) return (y - d) * W + x; if (live(x, y + d, r)) return (y + d) * W + x }
    return -1;
  };
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const at = y * W + x;
    if (!gone[at]) continue;
    const src = find(x, y, fills.find((f) => inRect(f.rect, x, y)) || everything);
    if (src < 0) throw new Error(`${room.source}: nothing to rebuild ${x},${y} from`);
    out.set(data.subarray(src * 4, src * 4 + 4), at * 4);
  }
  return { width: W, height: H, data: out };
}

function cutRoom(img, room) {
  if (img.width !== ROOM_W || img.height !== ROOM_H) throw new Error('a backdrop is ' + ROOM_W + 'x' + ROOM_H);
  const owner = claim(img, room), { whole, colour } = completePieces(img, room, owner);
  const base = rebuildBase(img, room, owner, whole);
  const { width: W, data } = img;
  const pieces = room.pieces.map((p, i) => {
    let x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
    for (let at = 0; at < W * ROOM_H; at++) {
      if (owner[at] !== i && whole[at] !== -2 - i) continue;
      const x = at % W, y = (at - x) / W;
      x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
    }
    if (x1 < 0) throw new Error(p.name + ' claims no pixel');
    const w = x1 - x0 + 1, h = y1 - y0 + 1, out = new Uint8Array(w * h * 4);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const at = y * W + x, o = ((y - y0) * w + x - x0) * 4;
      if (owner[at] === i) out.set(data.subarray(at * 4, at * 4 + 4), o);
      else if (whole[at] === -2 - i) out.set(colour.subarray(at * 4, at * 4 + 4), o);
    }
    return { name: p.name, x: x0, y: y0, width: w, height: h, data: out, plane: p.plane, base: p.base, occludes: p.occludes,
      thick: p.thick, surfaces: p.surfaces };
  });
  return { base, pieces };
}

/* draw each piece over a copy of base (the pieces are opaque or clear, so source over is a copy of the opaque pixels) */
function compose(base, pieces) {
  const out = Uint8Array.from(base.data), W = base.width;
  for (const p of pieces) for (let y = 0; y < p.height; y++) for (let x = 0; x < p.width; x++) {
    const s = (y * p.width + x) * 4;
    if (p.data[s + 3] === 0) continue;
    out.set(p.data.subarray(s, s + 4), ((p.y + y) * W + p.x + x) * 4);
  }
  return { width: base.width, height: base.height, channels: 4, data: Buffer.from(out) };
}

/* The page's depth maths (24-pet-depth.js): the depth z at which feet stand on row y. Pieces sort against cats by it. */
const HORIZON_Y = 98, FLOOR_Y_ART = 132, SCALE_AT_Z_MIN_PCT = 55, SCALE_Z_RANGE_PCT = 130;
const zFromY = (y) => Math.max(0, Math.min(100, ((y - HORIZON_Y) / (FLOOR_Y_ART - HORIZON_Y) * 100 - SCALE_AT_Z_MIN_PCT) * 100 / SCALE_Z_RANGE_PCT));
const ROOM_ORDER = ['living', 'bedroom', 'kitchen'];

/* The files of one build: room/layers/<room>_base.png, room/layers/<piece>.png and room/layers.json (the manifest: every piece in
   draw order with its place, depth and flags). backdrops: { living, bedroom, kitchen } decoded RGBA images; png: pet_png.js.
   Throws if a room does not reassemble exactly (the promise at the top of this file). */
function layerFiles(backdrops, png) {
  const files = new Map(), rooms = {}, pieces = [];
  ROOM_ORDER.forEach((name, index) => {
    const img = backdrops[name], { base, pieces: cut } = cutRoom(img, ROOMS[name]);
    const back = compose(base, cut);
    if (!back.data.equals(Buffer.from(img.data))) throw new Error(name + ': the pieces over the base are not the backdrop');
    files.set(`room/layers/${name}_base.png`, png.encode({ width: base.width, height: base.height, channels: 4, data: Buffer.from(base.data) }));
    rooms[name] = { base: `${name}_base.png`, houseX: index * ROOM_W };
    for (const p of cut) {
      files.set(`room/layers/${p.name}.png`, png.encode({ width: p.width, height: p.height, channels: 4, data: Buffer.from(p.data) }));
      pieces.push({ name: p.name, room: name, file: p.name + '.png', x: p.x, y: p.y, w: p.width, h: p.height, plane: p.plane,
        baseY: p.base, z: p.base == null ? null : Math.round(zFromY(p.base) * 100) / 100, occludes: p.occludes, thick: p.thick, surfaces: p.surfaces });
    }
  });
  const rows = pieces.map((p) => ' ' + JSON.stringify(p)).join(',\n');
  files.set('room/layers.json', Buffer.from(`{"roomWidth":${ROOM_W},"roomHeight":${ROOM_H},"rooms":${JSON.stringify(rooms)},\n"pieces":[\n${rows}\n]}\n`));
  return files;
}

module.exports = { ROOMS, cutRoom, compose, claim, layerFiles, zFromY };
