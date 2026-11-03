/* The depth maps of the room furniture: each cut-out piece (pet_room_cut.js) as a small solid, one column of solid per painted
   pixel, from its front f to its back b (floor rows behind the piece's front plane; the profiles are data, pet_room_volume_spec.js).
   Pure functions; gen_pet_native_assets.js and gen_pet_room_layers.js write the files through pet_room_cut.js's layerFiles.

     volumeMaps(pieces)  ->  Map name -> { f: Float32Array, b: Float32Array, pushed }   for the pieces of one room, back to front
     encodeMap(piece, map) -> an RGBA image: red = f * 8, green = b * 8 (so 1/8 of a unit, at most 31 7/8), alpha 255 where the
                              piece is painted and 0 elsewhere

   The promise the native room relies on: with the camera at rest the room drawn by depth is the painted room. The room draws the
   pieces' pixels in bands of one unit of depth, nearest last, each band keyed by `sortFront - (floor(f) + 0.5)` where sortFront is
   the piece's base row minus its `front` (ties keep the manifest order). So wherever a later piece covers an earlier one in the
   painted room, the earlier piece's pixel must not be in a nearer band: volumeMaps pushes such a hidden pixel back (it is never
   seen at rest, and a camera sway only uncovers its edge). `pushed` counts them; checkRestOrder proves the result. */
const { VOLUMES } = require('./pet_room_volume_spec.js');

const UNIT = 8;
const MAX_DEPTH = 255 / UNIT;
/* the thinnest a column is: a round body's edge, a pushed pixel */
const MIN_THICKNESS = 0.25;

const key = (d, o) => (d[o] << 16 | d[o + 1] << 8 | d[o + 2]).toString(16).padStart(6, '0');
const quantise = (v) => Math.round(Math.max(0, Math.min(MAX_DEPTH, v)) * UNIT) / UNIT;

/* the sprite pixels a shape applies to: opaque, inside its rect, passing its colour rule (sprite-relative indices) */
function matched(piece, shape) {
  const { x: px, y: py, width: w, height: h, data } = piece, r = shape.rect, out = [];
  const only = shape.only && new Set(shape.only), not = shape.not && new Set(shape.not);
  for (let y = Math.max(r[1], py); y <= Math.min(r[3], py + h - 1); y++) for (let x = Math.max(r[0], px); x <= Math.min(r[2], px + w - 1); x++) {
    const i = (y - py) * w + (x - px), o = i * 4;
    if (data[o + 3] === 0) continue;
    const c = key(data, o);
    if ((only && !only.has(c)) || (not && not.has(c))) continue;
    out.push(i);
  }
  return out;
}

/* A round body: in each row the matched span is its width; the middle is nearest. */
function applyRound(piece, list, { centre, depth }, f, b) {
  const rows = new Map();
  for (const i of list) { const y = Math.floor(i / piece.width), x = i % piece.width; const s = rows.get(y) || [x, x]; rows.set(y, [Math.min(s[0], x), Math.max(s[1], x)]) }
  for (const i of list) {
    const y = Math.floor(i / piece.width), x = i % piece.width, [a, z] = rows.get(y);
    const cx = (a + z + 1) / 2, r = (z - a + 1) / 2, u = (x + 0.5 - cx) / r, s = Math.sqrt(Math.max(0, 1 - u * u));
    f[i] = centre - depth * s;
    b[i] = Math.max(centre + depth * s, f[i] + MIN_THICKNESS);
  }
}

/* A soft cushion: nearest in the middle, `edge` farther at the rim, by the distance to the edge of the matched area. */
function applyBulge(piece, list, { f: middle, edge }, f) {
  const w = piece.width, inside = new Set(list), dist = new Map();
  let frontier = list.filter((i) => [-1, 1, -w, w].some((d) => !inside.has(i + d) || (d === -1 && i % w === 0) || (d === 1 && i % w === w - 1)));
  frontier.forEach((i) => dist.set(i, 1));
  for (let n = 2; frontier.length; n++) {
    const next = [];
    for (const i of frontier) for (const d of [-1, 1, -w, w]) { const j = i + d; if (inside.has(j) && !dist.has(j)) { dist.set(j, n); next.push(j) } }
    frontier = next;
  }
  const reach = Math.min(3, Math.max(1, ...dist.values()));
  for (const i of list) { const t = 1 - (Math.min(dist.get(i), reach) - 1) / reach; f[i] = middle + edge * t * t }
}

/* The front and back of every pixel of one piece from its profile, before the push. */
function profile(piece, spec) {
  const n = piece.width * piece.height, f = new Float32Array(n), b = new Float32Array(n).fill(spec.depth);
  for (const shape of spec.shapes) {
    const list = matched(piece, shape);
    if (!list.length) throw new Error(`${piece.name}: a shape at ${shape.rect} matches no pixel`);
    if (shape.round) { applyRound(piece, list, shape.round, f, b); continue }
    if (shape.bulge) { applyBulge(piece, list, shape.bulge, f); continue }
    const y0 = Math.max(shape.rect[1], piece.y), y1 = Math.min(shape.rect[3], piece.y + piece.height - 1);
    for (const i of list) {
      if (Array.isArray(shape.f)) {
        const y = piece.y + Math.floor(i / piece.width), t = y1 > y0 ? (y - y0) / (y1 - y0) : 0;
        f[i] = shape.f[0] + (shape.f[1] - shape.f[0]) * t;
      } else if (shape.f != null) f[i] = shape.f;
      if (shape.b != null) b[i] = shape.b;
    }
  }
  for (let i = 0; i < n; i++) {
    if (piece.data[i * 4 + 3] === 0) { f[i] = 0; b[i] = 0; continue }
    f[i] = quantise(f[i]);
    b[i] = quantise(Math.max(b[i], f[i] + MIN_THICKNESS));
  }
  return { f, b };
}

const sortFront = (piece) => piece.base - (VOLUMES[piece.name].front || 0);
const band = (f) => Math.floor(f);

/* Every room pixel covered by more than one solid piece: [the pieces' indices back to front, their sprite indices]. */
function overlaps(pieces) {
  const cover = new Map();
  pieces.forEach((p, k) => {
    if (!VOLUMES[p.name]) return;
    for (let y = 0; y < p.height; y++) for (let x = 0; x < p.width; x++) {
      const i = y * p.width + x;
      if (p.data[i * 4 + 3] === 0) continue;
      const at = (p.y + y) * 1000 + p.x + x, list = cover.get(at) || [];
      list.push([k, i]);
      cover.set(at, list);
    }
  });
  return [...cover.values()].filter((l) => l.length > 1);
}

/* Push back every pixel a later piece covers at rest, so its band is never nearer than the covering piece's. */
function volumeMaps(pieces) {
  const maps = new Map();
  for (const p of pieces) {
    const spec = VOLUMES[p.name];
    if (!spec) continue;
    if (p.base == null) throw new Error(p.name + ': a flat piece has no volume');
    for (const s of Object.keys(spec.surfaces || {})) {
      if (!p.surfaces.includes(s)) throw new Error(`${p.name}: surface ${s} is not one of the piece's`);
      if (!(spec.surfaces[s] >= 0 && spec.surfaces[s] <= spec.depth)) throw new Error(`${p.name}: surface ${s} is outside the piece`);
    }
    maps.set(p.name, { ...profile(p, spec), pushed: 0 });
  }
  for (const list of overlaps(pieces)) {
    const [top, ti] = list[list.length - 1], tp = pieces[top], tf = maps.get(tp.name).f[ti];
    for (const [k, i] of list.slice(0, -1)) {
      const p = pieces[k], m = maps.get(p.name), need = band(tf) + sortFront(p) - sortFront(tp);
      if (band(m.f[i]) >= need) continue;
      if (need > MAX_DEPTH - MIN_THICKNESS) throw new Error(`${p.name}: cannot push a hidden pixel behind ${tp.name}`);
      m.f[i] = need;
      m.b[i] = Math.max(m.b[i], need + MIN_THICKNESS);
      m.pushed++;
    }
  }
  checkRestOrder(pieces, maps);
  return maps;
}

/* The promise: at every covered pixel the last piece in band order is the last piece in manifest order. */
function checkRestOrder(pieces, maps) {
  for (const list of overlaps(pieces)) {
    const keyOf = ([k, i]) => sortFront(pieces[k]) - (band(maps.get(pieces[k].name).f[i]) + 0.5);
    const last = list.reduce((best, e) => (keyOf(e) >= keyOf(best) ? e : best));
    if (last !== list[list.length - 1]) throw new Error(`${pieces[list[0][0]].name}: the bands would not redraw the painted room at rest`);
  }
}

function encodeMap(piece, map) {
  const n = piece.width * piece.height, data = Buffer.alloc(n * 4);
  for (let i = 0; i < n; i++) {
    if (piece.data[i * 4 + 3] === 0) continue;
    data[i * 4] = Math.round(map.f[i] * UNIT);
    data[i * 4 + 1] = Math.round(map.b[i] * UNIT);
    data[i * 4 + 3] = 255;
  }
  return { width: piece.width, height: piece.height, channels: 4, data };
}

/* The manifest's volume entry of a piece, or null for a flat one. */
function volumeEntry(name) {
  const spec = VOLUMES[name];
  if (!spec) return null;
  for (const [s, piece] of Object.entries(spec.covered || {})) {
    if (!spec.surfaces || !(s in spec.surfaces)) throw new Error(`${name}: covered ${s} is not one of its surfaces`);
    if (!VOLUMES[piece]) throw new Error(`${name}: ${piece} covering ${s} is not a solid piece`);
  }
  return { file: `volume/${name}.png`, front: spec.front || 0, depth: spec.depth, wall: !!spec.wall, surfaces: spec.surfaces || {},
    covered: spec.covered || {} };
}

module.exports = { VOLUMES, UNIT, volumeMaps, encodeMap, volumeEntry, checkRestOrder };
