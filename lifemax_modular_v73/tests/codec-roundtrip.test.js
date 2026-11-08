/* The save codec's round-trip family (fixtures/codec-roundtrip.json, written by tools/gen_codec_roundtrip.js) still describes the
   page: it is recorded again from the current page, in memory, and must come out byte for byte the same, so the native codec is
   always checked against what the page really writes. Also checked without the page: the file is well formed and every number's
   text reads back as its bits.
   Run: NODE_PATH=$(npm root -g) node tests/codec-roundtrip.test.js [built-page.html]
   Regenerate after an intended change: NODE_PATH=$(npm root -g) node tools/gen_codec_roundtrip.js */
'use strict';
const fs = require('fs');
const gen = require('../tools/gen_codec_roundtrip.js');

let pass = 0, fail = 0;
const ok = (name, cond, extra) => {
  if (cond) { pass++; console.log('PASS ' + name) } else { fail++; console.log('FAIL ' + name + (extra !== undefined ? '\n     ' + JSON.stringify(extra).slice(0, 1500) : '')) }
};
const bits = d => { const v = new DataView(new ArrayBuffer(8)); v.setFloat64(0, d); let h = ''; for (let i = 0; i < 8; i++) h += v.getUint8(i).toString(16).padStart(2, '0'); return h };
const fromBits = h => { const v = new DataView(new ArrayBuffer(8)); for (let i = 0; i < 8; i++) v.setUint8(i, parseInt(h.slice(2 * i, 2 * i + 2), 16)); return v.getFloat64(0) };

(async () => {
  const raw = fs.readFileSync(gen.FILE, 'utf8'), file = JSON.parse(raw);
  ok('the file is in its own one-entry-per-line form', gen.serialize(file) === raw);
  ok('every number text reads back as its double (NaN and the infinities as null; -0 as 0)', file.numbers.every(([h, t]) => {
    const d = fromBits(h);
    return Number.isFinite(d) ? bits(JSON.parse(t)) === bits(d === 0 ? 0 : d) : t === 'null';
  }));
  ok('every string text reads back as its code units', file.strings.every(([u, t]) => JSON.stringify(JSON.parse(t).split('').map(c => c.charCodeAt(0))) === JSON.stringify(u)));
  ok('every save is stored JSON of an object', file.saves.every(s => typeof JSON.parse(s.stored) === 'object'));

  const page = require('./current-page.js')(process.argv[2]);
  const again = await gen.generate(page);
  ok('the page writes the same numbers', JSON.stringify(again.numbers) === JSON.stringify(file.numbers));
  ok('the page writes the same strings', JSON.stringify(again.strings) === JSON.stringify(file.strings));
  for (const s of file.saves) {
    const now = again.saves.find(x => x.name === s.name);
    ok('the page stores the ' + s.name + ' save byte for byte as recorded', now && now.stored === s.stored,
      now && { at: [...now.stored].findIndex((c, i) => c !== s.stored[i]) });
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
