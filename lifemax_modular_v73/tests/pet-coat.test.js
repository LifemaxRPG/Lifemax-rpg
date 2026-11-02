/* A coat's sheets for the native live cat (50-pet-coat.js, command `coat`; overview.cat.coat.v1; pet engine phase P3b), on the real page:
   - for genetic coats (a tortoiseshell, a colour-point, a long-haired white-spotted cat, a classic tabby) and every sheet kind (adult,
     depth, the six kitten stages and the six kitten depth stages) the answer's PNG decodes, through the same codec the native
     fixtures use (tools/pet_png.js), to the same grey-index pixels the room draws that coat from (petLoadImg("dna:<kind>:<key>"));
   - the answer's palette, applied to those indices the way petTintSheet applies it, gives the room's own recoloured canvas
     (rt.sheets / petTintSheet), every pixel, alpha included;
   - an unknown key, a legacy coat, a coat the page does not hold, a malformed key, an unknown kind (including the 2x "hd" sheets) and a
     non-string are refused with a message and no pixels; an answer is cached (a second ask is the same bytes);
   - the PNG writer (filters, DEFLATE with matches) round-trips arbitrary index data through zlib, small, odd-sized and run-heavy sheets
     among them, and the sheets stay small enough to hand over.
   Run: NODE_PATH=$(npm root -g) node tests/pet-coat.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const png = require('../tools/pet_png.js');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const KINDS = ['adult', 'depth'].concat([0, 1, 2, 3, 4, 5].map((i) => 'st' + i), [0, 1, 2, 3, 4, 5].map((i) => 'dst' + i));

/* in the page: pick one coat per wanted trait by drawing genomes, and hold it as the first cat's */
const PICK = (wanted) => {
  const out = {};
  for (const [name, test] of Object.entries(wanted)) {
    const f = new Function('rec', 'return ' + test);
    for (let t = 0; t < 60000 && !out[name]; t++) { const ph = petDnaPheno(petDnaRandom(null, {})); if (ph.key.indexOf('dna_') === 0 && f(petDnaRecipe(ph.key))) out[name] = ph.key }
  }
  return out;
};
/* in the page: what the room draws that coat from, and what it tints it to */
const ROOM = async ([key, kind]) => {
  const rec = petDnaRecipe(key), cv = await petLoadImg('dna:' + kind + ':' + key), d = cv.getContext('2d').getImageData(0, 0, cv.width, cv.height).data;
  const grey = new Uint8Array(cv.width * cv.height); for (let i = 0; i < grey.length; i++) grey[i] = d[i * 4];
  const tint = await petTintSheet('dna:' + kind + ':' + key, petDnaPalette(rec)), t = tint.getContext('2d').getImageData(0, 0, tint.width, tint.height).data;
  const b64 = (u8) => { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s) };
  return { w: cv.width, h: cv.height, grey: b64(grey), rgba: b64(new Uint8Array(t.buffer, t.byteOffset, t.length)) };
};

(async () => {
  const b = await chromium.launch(), p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(), errs = [];
  p.on('pageerror', (e) => errs.push(String(e)));
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true}));");
  await p.goto('file://' + file, { timeout: 120000 }); await p.waitForTimeout(900);
  await p.evaluate(async () => { await petInit(); petAdopt('ginger', 'Mo', 'playful') });
  const coats = await p.evaluate(PICK, {
    tortie: 'rec.tortie && !rec.point && !rec.long',
    point: "rec.point === 'cs' && !rec.long && !rec.white",
    longWhite: 'rec.long && rec.white >= 2 && !rec.point && !rec.tortie',
    classic: "rec.tab === 'classic' && !rec.tortie && !rec.white && !rec.point && !rec.long",
  });
  ok('four distinct genetic coats found', Object.keys(coats).length === 4 && new Set(Object.values(coats)).size === 4, coats);

  for (const [name, key] of Object.entries(coats)) {
    await p.evaluate((k) => { petDnaRegister(k); state.pet.coat = k }, key);
    let greyBad = 0, rgbaBad = 0, sheets = 0, bytes = 0, shape = true, answers = 0, errMsg = null;
    for (const kind of KINDS) {
      const ans = await p.evaluate(([k, kd]) => petCoatAnswer({ key: k, kind: kd }), [key, kind]);
      if (!ans.ok) { errMsg = [kind, ans]; break }
      answers++;
      const room = await p.evaluate(ROOM, [key, kind]), img = png.decode(Buffer.from(ans.png, 'base64')), grey = Buffer.from(room.grey, 'base64'), rgba = Buffer.from(room.rgba, 'base64');
      bytes += ans.png.length; sheets++;
      if (img.channels !== 1 || img.width !== room.w || img.height !== room.h) { shape = false; continue }
      for (let i = 0; i < grey.length; i++) if (img.data[i] !== grey[i]) greyBad++;
      /* the palette the way petTintSheet applies it: index 0 clear, an index with no colour clear, else the colour at full alpha */
      for (let i = 0; i < grey.length; i++) {
        const g = img.data[i], hex = g ? ans.palette[String(g)] : null, j = i * 4;
        const want = hex ? [parseInt(hex.slice(0, 2), 16), parseInt(hex.slice(2, 4), 16), parseInt(hex.slice(4, 6), 16), 255] : [rgba[j], rgba[j + 1], rgba[j + 2], 0];
        if (rgba[j + 3] !== want[3] || (want[3] && (rgba[j] !== want[0] || rgba[j + 1] !== want[1] || rgba[j + 2] !== want[2]))) rgbaBad++;
      }
    }
    ok(name + ': all 14 sheet kinds answered', answers === 14 && !errMsg, errMsg);
    ok(name + ': every sheet is an 8-bit grey PNG of the size the room draws', shape);
    ok(name + ': the decoded index pixels equal the pixels the room draws the coat from (all kinds)', greyBad === 0, greyBad);
    ok(name + ': the answer\'s palette recolours them to the room\'s own tinted canvas, every pixel (all kinds)', rgbaBad === 0, rgbaBad);
    ok(name + ': the 14 sheets stay small (' + Math.round(bytes / 1024) + ' KB of base64)', bytes < 14 * 300000 && bytes > 0, bytes);
  }

  /* palette: index -> rrggbb, the room's colours, with the eye colour and the fixed outline */
  const pal = await p.evaluate((k) => petCoatAnswer({ key: k, kind: 'depth' }).then((a) => ({ pal: a.palette, want: petDnaPalette(petDnaRecipe(k)) })), coats.tortie);
  ok('the palette is exactly the room\'s (same indices, same colours), 6 hex digits each',
    JSON.stringify(Object.keys(pal.pal).sort()) === JSON.stringify(Object.keys(pal.want).sort()) && Object.keys(pal.pal).every((i) => pal.pal[i] === pal.want[i].map((v) => v.toString(16).padStart(2, '0')).join('')) && Object.values(pal.pal).every((h) => /^[0-9a-f]{6}$/.test(h)), pal);

  /* refusals */
  const heldNot = await p.evaluate(() => { const ph = petDnaPheno(petDnaRandom(null, {})); return ph.key.indexOf('dna_') === 0 ? ph.key : 'dna_black_0_n_0_0_n_0_3' });
  const k0 = coats.tortie;
  const REFUSE = {
    unknownKey: { key: 'dna_zzz', kind: 'adult' }, shortKey: { key: 'dna_a1b2', kind: 'adult' }, legacy: { key: 'ginger', kind: 'adult' }, legacyTuxedo: { key: 'tuxedo', kind: 'st2' },
    notHeld: { key: heldNot, kind: 'adult' }, upper: { key: k0.toUpperCase(), kind: 'adult' }, longKey: { key: k0 + '_' + 'x'.repeat(80), kind: 'adult' }, pathKey: { key: '../' + k0, kind: 'adult' },
    emptyKey: { key: '', kind: 'adult' }, numberKey: { key: 7, kind: 'adult' }, noKey: { kind: 'adult' }, nullBody: null,
    badKind: { key: k0, kind: 'nope' }, hdKind: { key: k0, kind: 'hdadult' }, st6: { key: k0, kind: 'st6' }, st9: { key: k0, kind: 'st9' }, dst9: { key: k0, kind: 'dst9' }, spaced: { key: k0, kind: 'adult ' }, stNeg: { key: k0, kind: 'st-1' },
    noKind: { key: k0 }, kindNumber: { key: k0, kind: 3 }, kindArray: { key: k0, kind: ['adult'] },
  };
  const refused = await p.evaluate(async (R) => { const o = {}; for (const [n, q] of Object.entries(R)) { try { o[n] = await petCoatAnswer(q) } catch (e) { o[n] = { threw: String(e) } } } return o }, REFUSE);
  for (const [n, r] of Object.entries(refused)) ok('refused: ' + n, r.ok === false && typeof r.message === 'string' && r.message.length > 0 && !r.png && !r.palette && Object.keys(r).sort().join() === 'message,ok', r);
  // a held coat that is not a cat's: refused until the room registers it, then answered
  const reg = await p.evaluate(async (k) => { const before = await petCoatAnswer({ key: k, kind: 'depth' }); petDnaRegister(k); const after = await petCoatAnswer({ key: k, kind: 'depth' }); petDnaUnregister(k); return { before: before.ok, after: after.ok } }, heldNot);
  ok('a coat the page has not registered is refused; once registered it is answered', reg.before === false && reg.after === true, reg);

  /* the command, through the overview cards' own command table, and the cache */
  const viaCmd = await p.evaluate(async (k) => {
    const C = NativeOverviewCardsProjection.commands, a = await C.coat({ key: k, kind: 'adult' }), b = await C.coat({ key: k, kind: 'adult' }), r = await C.coat({ key: 'ginger', kind: 'adult' });
    return { a: a.ok, same: a.png === b.png && JSON.stringify(a.palette) === JSON.stringify(b.palette), keys: Object.keys(a).sort().join(), refused: r.ok === false, cache: PET_COAT.cache.size };
  }, k0);
  ok('the cards\' command table answers coat, a second ask is the same bytes, a legacy coat is refused', viaCmd.a && viaCmd.same && viaCmd.refused, viaCmd);
  ok('an answer is exactly {ok, coat, kind, png, palette}', viaCmd.keys === 'coat,kind,ok,palette,png', viaCmd);
  ok('the page keeps at most 6 answers', viaCmd.cache <= 6, viaCmd);

  /* the PNG writer on its own */
  const enc = await p.evaluate(() => {
    const sizes = [[1, 1], [2, 3], [7, 5], [72, 48], [576, 40], [300, 3], [1000, 2]], out = [];
    let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) >> 8;
    const b64 = (u8) => { let s = ''; for (let i = 0; i < u8.length; i += 0x8000) s += String.fromCharCode.apply(null, u8.subarray(i, i + 0x8000)); return btoa(s) };
    for (const [w, h] of sizes) for (const mode of ['noise', 'runs', 'zero', 'ramp', 'mixed']) {
      const a = new Uint8Array(w * h);
      for (let i = 0; i < a.length; i++) a[i] = mode === 'noise' ? rnd() & 255 : mode === 'zero' ? 0 : mode === 'ramp' ? i & 255 : mode === 'runs' ? ((i / 300) | 0) % 5 * 50 : (rnd() % 7 ? 0 : (rnd() % 21) * 10);
      out.push({ w, h, mode, data: b64(a), png: b64(petCoatPng(w, h, a)) });
    }
    return out;
  });
  let encBad = 0; for (const e of enc) { const d = png.decode(Buffer.from(e.png, 'base64')); if (d.width !== e.w || d.height !== e.h || d.channels !== 1 || !d.data.equals(Buffer.from(e.data, 'base64'))) { encBad++; console.log('  bad', e.w, e.h, e.mode) } }
  ok('the PNG writer round-trips ' + enc.length + ' sheets (noise, runs, zeros, ramps, sparse) through zlib', encBad === 0, encBad);
  const zeros = enc.find((e) => e.mode === 'zero' && e.w === 1000);
  ok('a sheet of clear pixels is tiny (runs match)', Buffer.from(zeros.png, 'base64').length < 200, Buffer.from(zeros.png, 'base64').length);

  ok('no page errors', errs.length === 0, errs);
  await b.close();
  console.log(pass + ' passed, ' + fail + ' failed'); process.exit(fail ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1) });
