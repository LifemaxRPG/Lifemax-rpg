/* The save codec's round-trip family: what the page's own JSON.stringify writes, recorded from the real page, so the native save
   codec (android-wrapper/native/src/main/java/com/lifemax/rpg/core/save) can prove it writes the same bytes. Regenerate with
     NODE_PATH=$(npm root -g) node tools/gen_codec_roundtrip.js [built-page.html]
   Output: tests/fixtures/codec-roundtrip.json. tests/codec-roundtrip.test.js records it again in memory and fails when the page and
   the file disagree.

   The file is {numbers, strings, saves}:
     numbers  [[bits, text], ...]: a double as 16 hex digits of its IEEE-754 bits (big-endian) and JSON.stringify of it in the page:
              the edges (-0, the smallest and largest doubles, 2^53 and around it, the 1e21 and 1e-7 layout switches, NaN and the
              infinities) and 400 seeded ones (random bit patterns, cents, economy products such as x * 0.7 and x * 1.15).
     strings  [[units, text], ...]: a string as its UTF-16 code units and JSON.stringify of it: every unit below U+0020, quote,
              backslash, slash, DEL, U+2028/9, lone and paired surrogates, and 60 seeded strings mixing them.
     saves    [{name, stored}, ...]: the text the page stores (writeSave(): JSON.stringify(state)) once it has started on a save
              and written it again: the golden saves new and extras from fixtures/saves, and probe, the new save with fields the
              economy does not model holding JSON's odd corners (-0, 1e400, 1.0, 1E2, a 20-digit integer, escapes, a lone
              surrogate, U+2028, an own __proto__ key, deep nesting, null against missing).
   Held still: the clock (T0), Math.random and crypto.randomUUID (seeded), so the page writes the same text every run. */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'codec-roundtrip.json');
const SAVES = path.join(__dirname, '..', 'tests', 'fixtures', 'saves');
const STORE = 'lifemax_rpg_prototype_v1', T0 = '2026-10-28T12:00:00.000Z', SEED = 20261108;

/* The probe: the new golden save with untracked fields that hold every corner of JSON text. Written by hand, as text. */
function probeText() {
  const state = JSON.stringify(JSON.parse(fs.readFileSync(path.join(SAVES, 'new.json'), 'utf8')).state);
  const odd = '"codecProbe":{"negZero":-0,"huge":1e400,"tiny":1e-400,"oneDot":1.0,"exp":1E2,"big":12345678901234567890,' +
    '"third":0.30000000000000004,"neg":-1.5e-7,"text":"q\\"b\\\\s\\/n\\n\\t\\u0000\\u001f\\u007f\\u00e9\\u2028\\ud83d\\ude00",' +
    '"lone":"\\ud800x\\udfff","__proto__":{"kept":true},"empty":{},"none":[],"null":null,"deep":[[[[{"a":[1,[2,[3,{"b":null}]]]}]]]],' +
    '"arr":[0,-0,1,true,false,null,"",{"":""}]},"__proto__":{"own":1},"zzUnknown":[{"id":"x","extra":{"n":-0}}]';
  return state.slice(0, -1) + ',' + odd + '}';
}

/* mulberry32: the same seeded sequence in node and in the page */
function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 };
}

/* Runs in the page: the number and string families. */
function families(seed) {
  const rand = (s => { let a = s >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 } })(seed);
  const view = new DataView(new ArrayBuffer(8)), hex = d => { view.setFloat64(0, d); let h = ''; for (let i = 0; i < 8; i++) h += view.getUint8(i).toString(16).padStart(2, '0'); return h };
  const fromBits = () => { for (let i = 0; i < 8; i++) view.setUint8(i, Math.floor(rand() * 256)); return view.getFloat64(0) };
  const edges = [0, -0, 1, -1, 0.1, 0.2, 0.1 + 0.2, 1 / 3, 2 / 3, 5e-324, -5e-324, 1e-323, 2.2250738585072014e-308, 2.225073858507201e-308,
    1.7976931348623157e308, -1.7976931348623157e308, 2 ** 53, 2 ** 53 + 2, 2 ** 53 - 1, 2 ** 63, 2 ** 64, 1e21, 999999999999999900000, 1e21 - 65536,
    123456789012345680000, 1e-6, 1e-7, 0.000001234, 1.234e-7, 4.35, 0.5, 1.5, 2.5, 31.499999999999996, 57.49999999999999, 1724.9999999999998,
    124.99999999999999, 49.99999999999999, 8.499999999999986, 1.1499999999999999, 27.500000000000004, 100, 1e100, 1e-100, 9.5367431640625e-7,
    NaN, Infinity, -Infinity];
  const numbers = edges.slice();
  for (let i = 0; i < 400; i++) {
    const kind = i % 5, x = Math.floor(rand() * 5000);
    numbers.push(kind === 0 ? fromBits() : kind === 1 ? Math.round(rand() * 1e7) / 100 : kind === 2 ? x * 0.7 : kind === 3 ? x * 1.15 : (rand() - 0.5) * 10 ** Math.floor(rand() * 40 - 20));
  }
  const units = s => Array.from({ length: s.length }, (_, i) => s.charCodeAt(i));
  const fixed = [''];
  for (let u = 0; u < 0x20; u++) fixed.push(String.fromCharCode(u));
  fixed.push('"', '\\', '/', '\x7f', '\x80', '\xe9', ' ', ' ', '\ud800', '\udbff', '\udc00', '\udfff', '😀', '\ude00\ud83d', 'a\ud800', '\ud800𐀀', '﻿', '￿', '·★’×');
  const pool = [0x22, 0x5c, 0x2f, 0x0a, 0x00, 0x1f, 0x20, 0x41, 0x7f, 0xe9, 0x2028, 0xd800, 0xdbff, 0xdc00, 0xdfff, 0xfeff, 0x4e2d];
  for (let i = 0; i < 60; i++) {
    let s = ''; const n = 1 + Math.floor(rand() * 12);
    for (let j = 0; j < n; j++) s += String.fromCharCode(rand() < 0.7 ? pool[Math.floor(rand() * pool.length)] : Math.floor(rand() * 0x10000));
    fixed.push(s);
  }
  return { numbers: numbers.map(d => [hex(d), JSON.stringify(d)]), strings: fixed.map(s => [units(s), JSON.stringify(s)]) };
}

/* Starts the page on `text` and returns what it stores. */
async function stored(browser, file, text) {
  const context = await browser.newContext({ viewport: { width: 412, height: 860 } }), p = await context.newPage(), errors = [];
  p.on('pageerror', e => errors.push(String(e))); p.on('dialog', d => d.accept());
  try {
    await p.clock.setFixedTime(new Date(T0));
    await p.addInitScript(([STORE, text, seed]) => {
      if (sessionStorage.getItem('seeded')) return; sessionStorage.setItem('seeded', '1');
      localStorage.setItem(STORE, text);
      let a = seed >>> 0, n = 0;
      Math.random = () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 };
      if (window.crypto) crypto.randomUUID = () => '00000000-0000-4000-8000-' + String(++n).padStart(12, '0');
    }, [STORE, text, SEED]);
    await p.goto('file://' + file, { timeout: 120000 });
    await p.waitForFunction(() => typeof state === 'object' && state !== null, null, { timeout: 60000 });
    await p.waitForTimeout(3000);
    const r = await p.evaluate(STORE => { const wrote = writeSave(); return { text: localStorage.getItem(STORE), same: wrote && localStorage.getItem(STORE) === JSON.stringify(state) } }, STORE);
    if (!r.same) throw new Error('writeSave() did not store JSON.stringify(state)');
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return r.text;
  } finally { await context.close() }
}

/* Records the family in the built page `file`. */
async function generate(file) {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const context = await browser.newContext(), p = await context.newPage();
    await p.goto('about:blank');
    const out = await p.evaluate(families, SEED);
    await context.close();
    out.saves = [];
    for (const name of ['new', 'extras']) {
      const state = JSON.parse(fs.readFileSync(path.join(SAVES, name + '.json'), 'utf8')).state;
      out.saves.push({ name, stored: await stored(browser, file, JSON.stringify(state)) });
    }
    out.saves.push({ name: 'probe', stored: await stored(browser, file, probeText()) });
    return out;
  } finally { await browser.close() }
}

/* one entry per line, so a change shows up in a diff as the entries it changed */
const serialize = f => '{"numbers":[\n' + f.numbers.map(x => JSON.stringify(x)).join(',\n') + '\n],\n"strings":[\n' +
  f.strings.map(x => JSON.stringify(x)).join(',\n') + '\n],\n"saves":[\n' + f.saves.map(x => JSON.stringify(x)).join(',\n') + '\n]}\n';

module.exports = { generate, serialize, FILE, rng };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file).then(f => {
    fs.writeFileSync(FILE, serialize(f));
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + f.numbers.length + ' numbers, ' + f.strings.length + ' strings, ' + f.saves.length + ' saves');
  }).catch(e => { console.error(e); process.exit(1) });
}
