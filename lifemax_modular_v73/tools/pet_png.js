/* A small PNG codec for the pet art tools (gen_pet_native_assets.js, gen_pet_art_fixtures.js): 8-bit grey (colour type 0) and
   8-bit RGBA (colour type 6), not interlaced, which is all the cat's sheets and the room atlas use.
     decode(buf) -> { width, height, channels, data }   data: Buffer of height * width * channels bytes
     encode({ width, height, channels, data }, { filter }?) -> Buffer
   encode is deterministic: one filter per row chosen by the smallest sum of absolute residuals (ties go to the lower filter
   number), zlib level 9, no ancillary chunks. */
const zlib = require('zlib');

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const COLOUR_TYPE = { 1: 0, 4: 6 }; /* channels -> PNG colour type */
const CHANNELS = { 0: 1, 6: 4 };

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0 }
  return t;
})();
function crc32(buf) { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
function chunk(type, body) {
  const head = Buffer.alloc(8); head.writeUInt32BE(body.length, 0); head.write(type, 4, 'latin1');
  const tail = Buffer.alloc(4); tail.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), body])), 0);
  return Buffer.concat([head, body, tail]);
}

function paeth(a, b, c) {
  const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}
/* the byte a filter predicts at position i of a row; left = bytes[i-bpp], up = same position in the row above, upLeft both */
function predict(type, left, up, upLeft) {
  return type === 0 ? 0 : type === 1 ? left : type === 2 ? up : type === 3 ? (left + up) >> 1 : paeth(left, up, upLeft);
}

function decode(buf) {
  if (!buf.subarray(0, 8).equals(SIGNATURE)) throw new Error('not a PNG');
  let pos = 8, width = 0, height = 0, channels = 0; const idat = [];
  while (pos < buf.length) {
    const len = buf.readUInt32BE(pos), type = buf.toString('latin1', pos + 4, pos + 8), body = buf.subarray(pos + 8, pos + 8 + len);
    if (type === 'IHDR') {
      width = body.readUInt32BE(0); height = body.readUInt32BE(4);
      channels = CHANNELS[body[9]];
      if (body[8] !== 8 || !channels || body[12] !== 0) throw new Error('unsupported PNG (need 8-bit grey or RGBA, not interlaced)');
    } else if (type === 'IDAT') idat.push(body);
    pos += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(idat)), stride = width * channels, data = Buffer.alloc(height * stride);
  for (let y = 0; y < height; y++) {
    const type = raw[y * (stride + 1)], src = y * (stride + 1) + 1, dst = y * stride;
    for (let i = 0; i < stride; i++) {
      const left = i >= channels ? data[dst + i - channels] : 0, up = y ? data[dst - stride + i] : 0;
      const upLeft = y && i >= channels ? data[dst - stride + i - channels] : 0;
      data[dst + i] = (raw[src + i] + predict(type, left, up, upLeft)) & 255;
    }
  }
  return { width, height, channels, data };
}

function encode({ width, height, channels, data }, opts = {}) {
  const stride = width * channels, rows = [];
  /* opts.filter fixes one filter for every row: 0 (None) packs palette-index sheets about a quarter smaller than the heuristic */
  const types = opts.filter != null ? [opts.filter] : [0, 1, 2, 3, 4];
  for (let y = 0; y < height; y++) {
    let best = null, bestCost = Infinity;
    for (const type of types) {
      const out = Buffer.alloc(stride + 1); out[0] = type; let cost = 0;
      for (let i = 0; i < stride; i++) {
        const at = y * stride + i, left = i >= channels ? data[at - channels] : 0, up = y ? data[at - stride] : 0;
        const upLeft = y && i >= channels ? data[at - stride - channels] : 0;
        const v = (data[at] - predict(type, left, up, upLeft)) & 255; out[i + 1] = v; cost += v < 128 ? v : 256 - v;
      }
      if (cost < bestCost) { bestCost = cost; best = out }
    }
    rows.push(best);
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = COLOUR_TYPE[channels];
  return Buffer.concat([SIGNATURE, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(Buffer.concat(rows), { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

module.exports = { decode, encode };
