/* Rig audit: runs the real runtime compositor (petRigCompose) over every sheet / animation / frame with stress morphs and
   measures what goes wrong. Usage:  NODE_PATH=$(npm root -g) node tools/rig_audit.js <built html> [outdir]
   Writes <outdir>/report.json and contact sheets for rows that need a human look. */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = path.resolve(process.argv[2]); const out = path.resolve(process.argv[3] || path.join(__dirname, 'qa', 'audit')); fs.mkdirSync(out, { recursive: true });
(async () => {
  const b = await chromium.launch(); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage();
  p.on('pageerror', e => console.log('page error', String(e).slice(0, 200)));
  await p.goto('file://' + file); await p.waitForTimeout(1500);
  const rep = await p.evaluate(async () => {
    const rt = PET_RT, A = rt.A, G = petGrowthJson(), coat = 'ginger', pal = A.pal[coat];
    const mk = async url => { const img = await petLoadImg(url), cv = document.createElement('canvas'); cv.width = img.width; cv.height = img.height; const cx = cv.getContext('2d', { willReadFrequently: true }); cx.drawImage(img, 0, 0);
      const id = cx.getImageData(0, 0, cv.width, cv.height), d = id.data; for (let i = 0; i < d.length; i += 4) { const g = d[i]; if (g === 0) { d[i + 3] = 0; continue } const c = pal[String(g)]; if (c) { d[i] = c[0]; d[i + 1] = c[1]; d[i + 2] = c[2]; d[i + 3] = 255 } else d[i + 3] = 0 } cx.putImageData(id, 0, 0); return cv };
    const sheets = {};
    const ad = rt.sheets && rt.sheets[coat]; sheets.adult = ad && ad.cv ? ad.cv : ad;
    for (let i = 0; i < 6; i++) sheets['st' + i] = await mk(G.stages[i].coats[coat]);
    sheets.depth = rt.dsheets && rt.dsheets[coat]; sheets.depth = sheets.depth && sheets.depth.cv ? sheets.depth.cv : sheets.depth;
    for (let i = 0; i < 6; i++) sheets['dst' + i] = await mk(A.depth.stages[i].coats[coat]);
    const D = petRigJson(), OL = PET_RIG_OUTLINE, MX = PET_RIG_MX, MT = PET_RIG_MT;
    const prof = { 'ears long': { em: 2.2, ew: 1.5 }, 'ears short': { em: .45, ew: .7 }, 'tail long': { tl: 2 }, 'tail short': { tl: .5 }, 'legs long': { lg: 1.5 }, 'legs short': { lg: .7 }, 'body long': { bl: 1.5 }, 'body short': { bl: .7 }, 'whiskers': { wk: 1.8 } };
    const base = { em: 1, ew: 1, tl: 1, lg: 1, bl: 1, wk: 1 };
    const metrics = (px, CW, CH) => {
      const N = CW * CH, op = new Uint8Array(N); for (let i = 0; i < N; i++) op[i] = px[i] >>> 24 ? 1 : 0;
      /* 8-connected components of opaque pixels */
      const lab = new Int32Array(N); let comps = [], q = [];
      for (let s = 0; s < N; s++) if (op[s] && !lab[s]) { const id = comps.length + 1; let n = 0; q.length = 0; q.push(s); lab[s] = id; while (q.length) { const c = q.pop(); n++; const y = (c / CW) | 0, x = c % CW; for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) { if (!dx && !dy) continue; const yy = y + dy, xx = x + dx; if (yy < 0 || xx < 0 || yy >= CH || xx >= CW) continue; const k = yy * CW + xx; if (op[k] && !lab[k]) { lab[k] = id; q.push(k) } } } comps.push(n) }
      const floaters = comps.filter(n => n <= 4).length;
      /* enclosed transparent pixels (holes) */
      const seen = new Uint8Array(N); q.length = 0; for (let x = 0; x < CW; x++) { q.push(x, (CH - 1) * CW + x) } for (let y = 0; y < CH; y++) { q.push(y * CW, y * CW + CW - 1) }
      for (const s of q) if (!op[s]) seen[s] = 1; let st = q.filter(s => !op[s]); while (st.length) { const c = st.pop(), y = (c / CW) | 0, x = c % CW; for (const [dy, dx] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) { const yy = y + dy, xx = x + dx; if (yy < 0 || xx < 0 || yy >= CH || xx >= CW) continue; const k = yy * CW + xx; if (!op[k] && !seen[k]) { seen[k] = 1; st.push(k) } } }
      let holes = 0, exposed = 0, area = 0; for (let i = 0; i < N; i++) { if (!op[i]) { if (!seen[i]) holes++; continue } area++; const y = (i / CW) | 0, x = i % CW; if (px[i] === OL) continue; if (x === 0 || y === 0 || x === CW - 1 || y === CH - 1 || !op[i - 1] || !op[i + 1] || !op[i - CW] || !op[i + CW]) exposed++ }
      return { comps: comps.length, floaters, holes, exposed, area };
    };
    const png = (list, labels, scale) => { /* list of {px,CW,CH,ox,oy} cropped to the cat's box */
      const W = 72, H = 48, pad = 4, cvs = document.createElement('canvas'); cvs.width = list.length * (W * scale + pad); cvs.height = H * scale + 14; const cx = cvs.getContext('2d'); cx.fillStyle = '#2b3140'; cx.fillRect(0, 0, cvs.width, cvs.height); cx.imageSmoothingEnabled = false; cx.font = '10px sans-serif'; cx.fillStyle = '#9fb0c8';
      list.forEach((r, k) => { const c2 = document.createElement('canvas'); c2.width = r.CW; c2.height = r.CH; const x2 = c2.getContext('2d'), id = x2.createImageData(r.CW, r.CH); new Uint32Array(id.data.buffer).set(r.px); x2.putImageData(id, 0, 0);
        cx.drawImage(c2, r.ox - 6, r.oy - 14, W + 12, H + 14, k * (W * scale + pad), 14, (W + 12) * scale * W / (W + 12), (H + 14) * scale * H / (H + 14)); cx.fillText(labels[k], k * (W * scale + pad) + 2, 10) });
      return cvs.toDataURL('image/png') };
    const report = { sheets: {}, images: {} }, dumpLater = [];
    for (const kind of Object.keys(sheets)) {
      const sh = sheets[kind]; if (!sh) { report.sheets[kind] = { error: 'sheet not loaded' }; continue }
      const dep = kind.charAt(0) === 'd', tab = dep ? A.depth.anims : A.anims, Wd = dep ? A.depth.W : A.W, Hd = dep ? A.depth.H : A.H, rows = {};
      for (const anim of Object.keys(D[kind] || {})) {
        const row = D[kind][anim], meta = tab[anim]; if (!meta) continue;
        const info = { legs: !!row.lg, body: !!row.bd, tail: !!row.tl, frames: meta.n, earsFound: 0, tailFrames: 0, identityBad: 0, idMax: 0, worst: 0, flagged: [], prof: {} }; let best = null;
        for (let f = 0; f < meta.n; f++) {
          const fr = petRigFrameData(kind, anim, f); if (!fr) continue;
          if (fr.ears && fr.ears.length >= 2) info.earsFound++; if (fr.tail) info.tailFrames++;
          const src = petRigSrcPixels(sh, meta.row, f, Wd, Hd);
          const id = petRigCompose(src, fr, base, null, Wd, Hd);
          let diff = 0; for (let y = 0; y < Hd; y++) for (let x = 0; x < Wd; x++) { const a = src[y * Wd + x], c = id.px[(y + MT) * id.CW + x + MX]; if ((a >>> 24 ? a : 0) !== (c >>> 24 ? c : 0)) diff++ }
          if (diff > 0) info.identityBad++; info.idMax = Math.max(info.idMax, diff);
          const m0 = metrics(id.px, id.CW, id.CH);
          let worst = 0, worstName = '', renders = [{ px: src, CW: Wd, CH: Hd, ox: 0, oy: 0 }, { px: id.px, CW: id.CW, CH: id.CH, ox: MX, oy: MT }], labels = ['original', 'rig at 1.0x: ' + diff + ' px differ'];
          for (const name of Object.keys(prof)) {
            const rel = name.startsWith('ears') || name === 'whiskers' || (name.startsWith('tail') && info.tail) || (name.startsWith('legs') && info.legs) || (name.startsWith('body') && info.body);
            if (!rel) continue;
            const r = petRigCompose(src, fr, Object.assign({}, base, prof[name]), null, Wd, Hd), m = metrics(r.px, r.CW, r.CH);
            const score = Math.max(0, m.floaters - m0.floaters) * 6 + Math.max(0, m.holes - m0.holes) * 1 + Math.max(0, m.exposed - m0.exposed) * 0.5 + Math.max(0, m.comps - m0.comps) * 4;
            info.prof[name] = Math.max(info.prof[name] || 0, +score.toFixed(1));
            if (score > worst) { worst = score; worstName = name }
            if (score >= 8) { renders.push({ px: r.px, CW: r.CW, CH: r.CH, ox: MX, oy: MT, score: score }); labels.push(name + ' ' + score.toFixed(0)) }
          }
          if (worst >= 8) info.flagged.push({ frame: f, score: +worst.toFixed(1), profile: worstName });
          const rank = Math.max(worst, diff * 1.5); if (!best || rank > best.rank) { const idx = renders.map((r, i) => i).sort((a, b) => (renders[b].score || (b < 2 ? 0 : 0)) - (renders[a].score || 0)); best = { rank: rank, f: f, renders: renders.slice(0, 2).concat(renders.slice(2).sort((a, b) => b.score - a.score).slice(0, 2)), labels: null, all: labels, rr: renders } }
          info.worst = Math.max(info.worst, +worst.toFixed(1));
        }
        if (best) { const top = best.rr.slice(2).map((r, i) => ({ r: r, l: best.all[i + 2] })).sort((a, b) => b.r.score - a.r.score).slice(0, 2); const list = [best.rr[0], best.rr[1]].concat(top.map(t => t.r)), lab = [best.all[0].replace('original', 'original f' + best.f), best.all[1]].concat(top.map(t => t.l)); info.bestFrame = best.f; if (best.rank >= 12) dumpLater.push({ key: kind + '|' + anim, data: png(list, lab, 5) }) }
        rows[anim] = info;
      }
      report.sheets[kind] = rows;
    }
    dumpLater.forEach(d => { report.images[d.key] = d.data });
    return report;
  });
  const imgs = rep.images; delete rep.images; fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(rep, null, 1));
  for (const k of Object.keys(imgs)) fs.writeFileSync(path.join(out, k.replace(/\|/g, '__') + '.png'), Buffer.from(imgs[k].split(',')[1], 'base64'));
  // summary
  const lines = []; for (const kind of Object.keys(rep.sheets)) { const rows = rep.sheets[kind]; if (rows.error) { lines.push(kind + ': ' + rows.error); continue } for (const a of Object.keys(rows)) { const r = rows[a]; lines.push([kind.padEnd(6), a.padEnd(12), 'legs' + (r.legs ? 'Y' : '-'), 'body' + (r.body ? 'Y' : '-'), 'tail' + (r.tail ? 'Y' : '-'), 'ears ' + r.earsFound + '/' + r.frames, 'idBad ' + r.identityBad, 'worst ' + r.worst, 'flagged ' + r.flagged.length + '/' + r.frames].join('  ')) } }
  fs.writeFileSync(path.join(out, 'summary.txt'), lines.join('\n')); console.log(lines.length + ' rows audited; images: ' + Object.keys(imgs).length);
  await b.close();
})();
