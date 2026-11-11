/* Golden cases for the Life Director store (WP-LD7: LifeDirectorStore, engine.js:2234-2340: validate, migrate, defaultState, the load
   with its .bak fall-back and quarantine, the save with its .bak rotation and read-back check, clear, and the backup text both ways),
   recorded from the real page in Chromium, so that the native store (android-wrapper/native/src/main/java/com/lifemax/rpg/core/directorstore)
   is accepted only when it does exactly what the page does, byte for byte, for every case.
   Regenerate when the store changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_ld_store_golden.js [built-page.html]
   Output: tests/fixtures/ld-store-golden.json.gz (gzip of one case per line; the text is what serialize() writes).
   tests/ld-store-golden.test.js records every case again and fails when the page disagrees with the file.

   How: the seed texts are what the page itself wrote in Chromium (tools/ld_store_seeds.js). The cases (tools/ld_store_cases.js) are run
   by runCases below inside the built page, on the store module's own text (tools/ld_engine_vm.js storeSource), each against a scripted
   storage that logs every call, can fail any call and caps its size as Chromium does; the browser family runs on a clean origin's real
   localStorage instead (logged the same way), which checks that cap. node runs every case but the browser family a second time, from the
   same text in a vm, and must agree.

   File: {header, seeds, cases}. seeds: {name: text}. A case is {id, family, rule, fn, args, out}; family, rule and args as
   tools/ld_store_cases.js says. A text in out is given as itself when 2,000 units or shorter, as {seed: name} when it is a seed's text,
   and otherwise as {len, fnv} (its length and FNV-1a 32 over its UTF-16 units, 8 hex digits). A state in out is the text of
   JSON.stringify of the state with NaN, -0 and the infinities written {"$num": ...}. ops is the storage log, in order: ['get', key,
   text | null | {threw: name}], ['set', key, text, 'ok' | 'dropped' | {kept: units} | {threw: name}], ['remove', key] or ['remove', key,
   {threw: name}]; after is the storage at the end as [key, text] pairs sorted by key. fn and out:
     load      {state, report, available} or {threw: {name, message}}, ops, after: create(storage) then load(opts).
     save      {result, ops, after}: create(storage) then save(state).  clear: the same with clear(opts).
     export    {text}: exportJson(state).   import: {state} or {threw: {name, message}} (no message for a SyntaxError, whose words are the
               JavaScript engine's): importJson(text).
     validate  {reason}.   migrate: {state} or {threw}.   defaultState: {state}.
     sequence  {steps, ops, after}: steps run in order on one storage: create (a new store object on the same storage, as a reload
               does), load, save (the current state), edit (one top-level member of the current state set), clear, import (importJson,
               then save, the current state replaced only when saved, as logic.js Do.importBackup does), export, storage (the storage's
               items, faults or quota changed from outside). */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib'), http = require('http'), vm = require('vm');
const { defineCases, FAMILIES } = require('./ld_store_cases.js');
const engineVm = require('./ld_engine_vm.js');
const seedsModule = require('./ld_store_seeds.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'ld-store-golden.json.gz');

/* Runs cases on Store (the page's LifeDirectorStore); real is the browser's localStorage for the browser family (or null). Self-contained:
   its text is evaluated in the realm the store lives in. Takes and gives JSON texts. */
function runCases(Store, casesText, seedsText, real) {
  const cases = JSON.parse(casesText), seeds = JSON.parse(seedsText);
  const fnv = s => { let h = 0x811c9dc5; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; } return ('0000000' + h.toString(16)).slice(-8); };
  const seedOf = new Map();
  for (const k of Object.keys(seeds)) if (!seedOf.has(seeds[k])) seedOf.set(seeds[k], k);
  const ref = s => (s == null ? null : seedOf.has(s) ? { seed: seedOf.get(s) } : s.length <= 2000 ? s : { len: s.length, fnv: fnv(s) });
  const put = (o, k, v) => Object.defineProperty(o, k, { value: v, enumerable: true, writable: true, configurable: true });
  const NUMS = { NaN: NaN, Infinity: Infinity, '-Infinity': -Infinity, '-0': -0 };
  const canon = v => {
    if (typeof v === 'number') return Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' } : Object.is(v, -0) ? { $num: '-0' } : v;
    if (Array.isArray(v)) return v.map(canon);
    if (v && typeof v === 'object') { const o = {}; for (const k of Object.keys(v)) if (v[k] !== undefined) put(o, k, canon(v[k])); return o; }
    return v === undefined ? null : v;
  };
  const uncanon = v => {
    if (Array.isArray(v)) return v.map(uncanon);
    if (v && typeof v === 'object') {
      const keys = Object.keys(v);
      if (keys.length === 1 && keys[0] === '$num') return NUMS[v.$num];
      const o = {}; for (const k of keys) put(o, k, uncanon(v[k])); return o;
    }
    return v;
  };
  const stateText = s => ref(JSON.stringify(canon(s)));
  const text = a => {
    if (typeof a === 'string') return a;
    if (a.seed != null) return seeds[a.seed];
    if (a.repeat) return a.repeat.unit.repeat(a.repeat.count);
    const o = JSON.parse(text(a.grow.from));
    put(o, a.grow.field, '');
    put(o, a.grow.field, 'x'.repeat(a.grow.length - JSON.stringify(o).length));
    return JSON.stringify(o);
  };
  const stateOf = a => (a.tree !== undefined ? uncanon(a.tree) : JSON.parse(text(a.text)));
  const errorOf = (e, key) => {
    const x = new Error(e === 'quota' ? "Failed to execute 'setItem' on 'Storage': Setting the value of '" + key + "' exceeded the quota." : e.message);
    x.name = e === 'quota' ? 'QuotaExceededError' : e.name;
    return x;
  };
  const threw = e => (e && e.name === 'SyntaxError' ? { name: e.name } : { name: e && e.name, message: e && e.message });
  /* the scripted storage: the case's items, faults, lossy writes and quota (module comment of tools/ld_store_cases.js) */
  function scripted(spec, log) {
    const items = new Map();
    for (const [k, v] of spec.items || []) items.set(k, text(v));
    let faults = [], lossy = [], quota = Infinity;
    const configure = s => {
      if (s.faults) faults = s.faults.map(f => Object.assign({ seen: 0 }, f));
      if (s.lossy) lossy = s.lossy.map(l => Object.assign({ seen: 0 }, l));
      if (s.quota !== undefined) quota = s.quota == null ? Infinity : s.quota;
      for (const [k, v] of s.itemsChange || []) { if (v == null) items.delete(k); else items.set(k, text(v)); }
    };
    configure(spec);
    const fault = (op, key) => {
      let e = null;
      for (const f of faults) if (f.op === op && (f.key === '*' || f.key === key)) { f.seen++; if (!e && (f.nth === 0 || f.seen === f.nth)) e = errorOf(f.error, key); }
      return e;
    };
    const used = () => { let n = 0; for (const [k, v] of items) n += k.length + v.length; return n; };
    return {
      configure, items,
      getItem(key) {
        const e = fault('get', key);
        if (e) { log.push(['get', key, { threw: e.name }]); throw e; }
        const v = items.has(key) ? items.get(key) : null;
        log.push(['get', key, ref(v)]);
        return v;
      },
      setItem(key, value) {
        value = String(value);
        const e = fault('set', key);
        if (e) { log.push(['set', key, ref(value), { threw: e.name }]); throw e; }
        if (used() - (items.has(key) ? key.length + items.get(key).length : 0) + key.length + value.length > quota) {
          const q = errorOf('quota', key); log.push(['set', key, ref(value), { threw: q.name }]); throw q;
        }
        let keep = null;
        for (const l of lossy) if (l.key === '*' || l.key === key) { l.seen++; if (keep === null && (l.nth === 0 || l.seen === l.nth)) keep = l.keep; }
        if (keep !== null && keep < 0) { log.push(['set', key, ref(value), 'dropped']); return; }
        items.set(key, keep === null ? value : value.slice(0, keep));
        log.push(['set', key, ref(value), keep === null ? 'ok' : { kept: keep }]);
      },
      removeItem(key) {
        const e = fault('remove', key);
        if (e) { log.push(['remove', key, { threw: e.name }]); throw e; }
        items.delete(key); log.push(['remove', key]);
      },
      after: () => [...items].map(([k, v]) => [k, ref(v)]).sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0)),
    };
  }
  /* the browser's own localStorage behind the same log (the browser family: no faults; the origin is cleared first) */
  function browser(spec, log) {
    real.clear();
    for (const [k, v] of spec.items || []) real.setItem(k, text(v));
    const keys = () => { const out = []; for (let i = 0; i < real.length; i++) out.push(real.key(i)); return out.sort(); };
    return {
      configure() { throw new Error('the browser family cannot change its storage'); },
      getItem(key) { let v; try { v = real.getItem(key); } catch (e) { log.push(['get', key, { threw: e.name }]); throw e; } log.push(['get', key, ref(v)]); return v; },
      setItem(key, value) { try { real.setItem(key, value); } catch (e) { log.push(['set', key, ref(String(value)), { threw: e.name }]); throw e; } log.push(['set', key, ref(String(value)), 'ok']); },
      removeItem(key) { real.removeItem(key); log.push(['remove', key]); },
      after: () => keys().map(k => [k, ref(real.getItem(k))]),
    };
  }
  const plain = () => scripted({}, []);
  const FN = {
    load(a, st) { const store = Store.create(st); const res = store.load(a.opts); return { state: stateText(res.state), report: canon(res.report), available: store.available }; },
    save(a, st) { return { result: canon(Store.create(st).save(stateOf(a.state))) }; },
    clear(a, st) { return { result: canon(Store.create(st).clear(a.opts)) }; },
    export(a) { return { text: ref(Store.create(plain()).exportJson(stateOf(a.state))) }; },
    import(a) { return { state: stateText(Store.create(plain()).importJson(text(a.text))) }; },
    validate(a) { return { reason: Store.validate(stateOf(a.state)) }; },
    migrate(a) { return { state: stateText(Store.migrate(stateOf(a.state))) }; },
    defaultState(a) { return { state: stateText(Store.defaultState(a.opts)) }; },
    sequence(a, st) {
      let store = Store.create(st), current = null;
      const steps = a.steps.map(s => {
        try {
          if (s.do === 'create') { store = Store.create(st); return { available: store.available }; }
          if (s.do === 'load') { const res = store.load(s.opts); current = res.state; return { state: stateText(current), report: canon(res.report) }; }
          if (s.do === 'save') return { result: canon(store.save(current)) };
          if (s.do === 'edit') { if (current && typeof current === 'object') put(current, s.set[0], uncanon(s.set[1])); return {}; }
          if (s.do === 'clear') return { result: canon(store.clear(s.opts)) };
          if (s.do === 'export') return { text: ref(store.exportJson(current)) };
          if (s.do === 'import') {
            let next;
            try { next = store.importJson(text(s.text)); } catch (e) { return { refused: threw(e) }; }
            const saved = store.save(next);
            if (saved.ok) current = next;
            return { saved: canon(saved) };
          }
          if (s.do === 'storage') { st.configure({ faults: s.faults, quota: s.quota, itemsChange: s.items }); return {}; }
          throw new Error('unknown step ' + s.do);
        } catch (e) { return { threw: threw(e) }; }
      });
      return { steps };
    },
  };
  return cases.map(c => {
    const once = () => {
      const log = [], st = c.family === 'browser' ? browser(c.args.storage, log) : scripted(c.args.storage || {}, log);
      let out;
      try { out = FN[c.fn](c.args, st); } catch (e) { out = { threw: threw(e) }; }
      if (c.args.storage) { out.ops = log; out.after = st.after(); }
      return JSON.stringify(out);
    };
    if (!FN[c.fn]) throw new Error('no adaptor for ' + c.fn);
    const out = once();
    if (once() !== out) throw new Error('not deterministic: ' + c.id);
    return JSON.stringify(Object.assign({}, c, { out: JSON.parse(out) }));
  });
}

/* In node: the store text in a vm, the runner in the same context (so every object is the store's realm's). */
function recordInNode(cases, seeds) {
  const ctx = vm.createContext({ self: {} });
  vm.runInContext(engineVm.storeSource(), ctx, { filename: 'engine.js#store' });
  const run = vm.runInContext('(' + runCases.toString() + ')', ctx);
  return run(ctx.self.LifeDirectorStore, JSON.stringify(cases), JSON.stringify(seeds), null);
}

const launch = () => require('playwright').chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

/* In Chromium: the seeds from the page, then the cases inside the built page (on the store module's text, which the page must carry
   unchanged) and the browser family on a clean origin's localStorage. Cases go in parts and come back as text. */
async function recordInChromium(file, onPart) {
  const browser = await launch();
  const server = http.createServer((q, s) => { s.writeHead(200, { 'content-type': 'text/html; charset=utf-8' }); s.end('<!doctype html><meta charset="utf-8"><title>store</title>'); });
  try {
    const seeds = await seedsModule.capture(browser, file), cases = defineCases(seeds);
    const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
    await p.goto('file://' + file);
    const install = ({ src, runner }) => {
      const holder = {};
      new Function('self', src)(holder);
      window.__ldStore = { S: holder.LifeDirectorStore, run: new Function('return ' + runner)() };
    };
    await p.evaluate(install, { src: engineVm.storeSource(), runner: runCases.toString() });
    await new Promise(r => server.listen(0, '127.0.0.1', r));
    const clean = await (await browser.newContext()).newPage();
    await clean.goto('http://127.0.0.1:' + server.address().port + '/');
    await clean.evaluate(install, { src: engineVm.storeSource(), runner: runCases.toString() });
    const lines = [], seedsText = JSON.stringify(seeds);
    for (let i = 0; i < cases.length; i += 100) {
      const part = cases.slice(i, i + 100), inPage = part.filter(c => c.family !== 'browser'), real = part.filter(c => c.family === 'browser');
      const a = inPage.length ? await p.evaluate(([cs, sd]) => window.__ldStore.run(window.__ldStore.S, cs, sd, null), [JSON.stringify(inPage), seedsText]) : [];
      const b = real.length ? await clean.evaluate(([cs, sd]) => window.__ldStore.run(window.__ldStore.S, cs, sd, localStorage), [JSON.stringify(real), seedsText]) : [];
      const byId = new Map(a.concat(b).map(l => [JSON.parse(l).id, l]));
      lines.push(...part.map(c => byId.get(c.id)));
      if (onPart) onPart(i + part.length);
    }
    if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
    return { seeds, cases, lines, browser: browser.version() };
  } finally { server.close(); await browser.close(); }
}

const header = browser => ({
  about: 'Life Director store golden cases; see tools/gen_ld_store_golden.js',
  recordedIn: 'Chromium ' + browser,
  nodeCheck: 'node ' + process.versions.node,
});
/* one case per line, so a change shows up in a diff of the unzipped text as the cases it changed; the seeds on one line each first */
const serialize = (head, seeds, lines) => '{"header":' + JSON.stringify(head) + ',"seeds":{\n' + Object.keys(seeds).map(k => JSON.stringify(k) + ':' + JSON.stringify(seeds[k])).join(',\n')
  + '\n},"cases":[\n' + lines.join(',\n') + '\n]}\n';
const compress = text => zlib.gzipSync(Buffer.from(text, 'utf8'), { level: 9 });
const readFile = () => zlib.gunzipSync(fs.readFileSync(FILE)).toString('utf8');
/* The file's parts without parsing it whole: header, seeds, and each case's JSON text. */
function readParts() {
  const text = readFile(), rows = text.split('\n');
  const head = JSON.parse(rows[0].replace(/,"seeds":\{$/, '}')).header, seeds = {};
  let i = 1;
  for (; rows[i] !== '},"cases":['; i++) { const l = rows[i].endsWith(',') ? rows[i].slice(0, -1) : rows[i]; Object.assign(seeds, JSON.parse('{' + l + '}')); }
  const lines = rows.slice(i + 1).filter(l => l.startsWith('{"id"')).map(l => (l.endsWith(',') ? l.slice(0, -1) : l));
  return { text, head, seeds, lines };
}

module.exports = { FAMILIES, runCases, recordInNode, recordInChromium, serialize, header, readFile, readParts, compress, FILE };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  recordInChromium(file, n => { if (n % 1000 === 0) console.log('recorded ' + n); }).then(({ seeds, cases, lines, browser }) => {
    const text = serialize(header(browser), seeds, lines);
    fs.writeFileSync(FILE, compress(text));
    const by = {};
    for (const c of cases) by[c.family] = (by[c.family] || 0) + 1;
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ' (' + text.length + ' bytes unzipped): ' + lines.length + ' cases ' + JSON.stringify(by));
    const inNode = cases.filter(c => c.family !== 'browser'), node = recordInNode(inNode, seeds), want = new Map(lines.map(l => [JSON.parse(l).id, l]));
    const differ = inNode.filter((c, i) => node[i] !== want.get(c.id)).map(c => c.id);
    console.log('node disagrees with Chromium on ' + differ.length + ' cases' + (differ.length ? ', e.g. ' + differ.slice(0, 5).join(', ') : ''));
  }).catch(e => { console.error(e); process.exit(1); });
}
