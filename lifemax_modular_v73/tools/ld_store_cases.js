/* The Life Director store golden cases (WP-LD7): what tools/gen_ld_store_golden.js records from the page's LifeDirectorStore
   (engine.js:2234-2340). Every case starts from the seed texts the real page wrote (tools/ld_store_seeds.js) or from seeded, deterministic
   changes to them; the rule IDs are those of docs/specs/LIFE_DIRECTOR_STORE_SPEC.md.

   Arguments (what the runner and the Kotlin replay both resolve, literally):
     text      a string; {seed: name} (a seed text); {repeat: {unit, count}} (unit repeated); {grow: {from, field, length}} (the text
               `from` parsed, `field` set to a string of x's so that JSON.stringify of the whole is `length` units long).
     state     {text} (parsed with JSON.parse, so number texts and key order are the text's) or {tree} (a JSON tree in which
               {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'} stands for that number).
     storage   {items: [[key, text]...], quota?: units, faults?: [{op, key, nth, error}], lossy?: [{key, nth, keep}]}: a storage that
               starts with the items in that order; `quota` caps the sum of key and value lengths (UTF-16 units) as Chromium does
               (5,242,880 for a real origin); a fault makes the nth matching call (0: every call; key '*': any key) of op get, set or
               remove throw `error` ({name, message}, or 'quota' for Chromium's QuotaExceededError); a lossy write keeps only the first
               `keep` units of the nth matching set (keep -1: the write is silently not done).
     opts      {playerId, timezone, nowEpoch} as logic.js passes them to load and clear, or absent. */
'use strict';

const KEY = 'lifemax_director_state_v1', BAK = KEY + '.bak', PROBE = KEY + '.probe';
const QUARANTINE = 'lifemax_director_quarantine_lifemax_director_state_v1', QUARANTINE_BAK = QUARANTINE + '_bak';
const CHROMIUM_QUOTA = 5242880;
const FAMILIES = ['seeds', 'validate', 'migrate', 'defaults', 'load', 'save', 'sequence', 'backup', 'numbers', 'unknown', 'older', 'depth', 'fuzz', 'browser'];
const SMALL_SEEDS = ['fresh', 'unfinished'], BIG_SEEDS = ['planned', 'lived', 'lived-bak', 'imported', 'v0'];

/* A seeded random source (mulberry32), so the cases are the same on every run. */
function random(seed) {
  let a = seed >>> 0;
  const next = () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  return { next, int: n => Math.floor(next() * n), pick: list => list[Math.floor(next() * list.length)] };
}

/* Text edits that keep every other byte: a member added before the last `}` of an object text, and a member's number text replaced. */
const addMember = (text, key, raw) => text.slice(0, text.lastIndexOf('}')) + (/\{\s*$/.test(text.slice(0, text.lastIndexOf('}'))) ? '' : ',') + JSON.stringify(key) + ':' + raw + text.slice(text.lastIndexOf('}'));
const setNumber = (text, key, raw) => text.replace(new RegExp('"' + key + '":-?[0-9][0-9.eE+-]*'), '"' + key + '":' + raw);

/* The JSON corner values the page's reads and JavaScript's coercions meet. */
const ODD_NUMBERS = ['0', '-0', '1', '1.0', '1e0', '10e-1', '0.1e1', '1E0', '1.5', '-1', '1e21', '1e-7', '123456789012345678901', '12345678901234567890',
  '9007199254740993', '5e-324', '1e400', '-1e400', '0.1', '2.5e-5', '1439', '1440', '-1440', '4.35', '0.30000000000000004'];
const VALUES = ['null', 'true', 'false', '0', '-0', '1', '2', '-1', '1.5', '1e400', '""', '"1"', '"0"', '" 7 "', '"0x1f"', '"0b11"', '"0o17"', '"-0x5"',
  '"1e3"', '".5"', '"5."', '"Infinity"', '"-Infinity"', '"infinity"', '"1_000"', '"\\u00a07\\u2028"', '"\\ufeff8"', '"7\\u180e"', '"abc"', '[]', '[5]',
  '["6"]', '[[7]]', '[1,2]', '[null]', '[true]', '[{}]', '{}', '{"bed":1}', '"07:00"', '1380', '420', '-30', '3000', '1e3', '"\\u0000"'];

function defineCases(seeds) {
  const cases = [], seen = new Map();
  const add = (family, rule, fn, args, tag) => {
    const base = family + '/' + tag, n = (seen.get(base) || 0) + 1;
    seen.set(base, n);
    cases.push({ id: n > 1 ? base + '#' + n : base, family, rule, fn, args });
  };
  const seedText = name => ({ seed: name });
  const fresh = seeds.fresh, unfinished = seeds.unfinished;
  const storage = (items, extra) => Object.assign({ items: items.filter(([, v]) => v != null) }, extra || {});
  const err = (name, message) => ({ name, message });
  const OPTS = { playerId: 'lm_player', timezone: 'Europe/Berlin', nowEpoch: 1793174700 };

  /* ── seeds: every text the page wrote, read, re-saved, exported and imported ── */
  for (const name of Object.keys(seeds)) {
    const t = seedText(name), isState = name !== 'lived-export';
    add('seeds', 'LST-4 LST-7', 'load', { storage: storage([[KEY, t]]), opts: OPTS }, name + '/main');
    add('seeds', 'LST-4 LST-7', 'load', { storage: storage([[BAK, t]]), opts: OPTS }, name + '/bak-only');
    add('seeds', 'LST-4 LST-6', 'load', { storage: storage([[KEY, '{'], [BAK, t]]), opts: OPTS }, name + '/behind-corrupt');
    add('seeds', 'LST-23', 'import', { text: t }, name + '/import');
    if (!isState) continue;
    add('seeds', 'LST-10', 'validate', { state: { text: t } }, name + '/validate');
    add('seeds', 'LST-11', 'migrate', { state: { text: t } }, name + '/migrate');
    add('seeds', 'LST-17 LST-18', 'save', { storage: storage([]), state: { text: t } }, name + '/into-empty');
    add('seeds', 'LST-18', 'save', { storage: storage([[KEY, t]]), state: { text: t } }, name + '/over-itself');
    add('seeds', 'LST-18', 'save', { storage: storage([[KEY, seedText('fresh')], [BAK, seedText('unfinished')]]), state: { text: t } }, name + '/rotates');
    add('seeds', 'LST-22', 'export', { state: { text: t } }, name + '/export');
  }

  /* ── validate: every check in its order, on JSON corner values ── */
  const tops = ['null', 'true', 'false', '0', '1', '""', '"x"', '[]', '[1]', '{}'];
  for (const v of tops) add('validate', 'LST-10', 'validate', { state: { text: v } }, 'top/' + v);
  const shapes = [];
  for (const sv of ['1', '1.0', '1e0', '10e-1', '0', '-0', '2', '"1"', 'null', 'true', '[1]', '{}', '1.0000000000000002', '0.9999999999999999', 'MISSING'])
    for (const av of ['{"rules":[]}', '{"rules":{}}', '{"rules":null}', '{}', 'null', '[]', '"x"', '5', '{"rules":"[]"}', 'MISSING'])
      for (const gv of ['[]', '{}', 'null', '"[]"', 'MISSING']) shapes.push([sv, av, gv]);
  const shapeText = ([sv, av, gv], extra) => '{' + [sv !== 'MISSING' && '"schemaVersion":' + sv, av !== 'MISSING' && '"availability":' + av, gv !== 'MISSING' && '"goals":' + gv, extra]
    .filter(Boolean).join(',') + '}';
  shapes.forEach((s, i) => { if (i % 2 === 0 || s[0] === '1') add('validate', 'LST-10', 'validate', { state: { text: shapeText(s) } }, 'shape/' + s.join('|')); });
  add('validate', 'LST-10', 'validate', { state: { tree: { schemaVersion: { $num: 'NaN' }, availability: { rules: [] }, goals: [] } } }, 'schema-nan');
  add('validate', 'LST-10', 'validate', { state: { tree: { schemaVersion: { $num: '-0' } } } }, 'schema-minus-zero');
  add('validate', 'LST-10', 'validate', { state: { tree: { schemaVersion: { $num: 'Infinity' } } } }, 'schema-infinity');
  for (const sv of ['1e21', '123456789012345678901', '[1,[2,[3]]]', '[null,null]', '{"a":1}', '"\\ud800"', '-1e-7', '0.000001'])
    add('validate', 'LST-10', 'validate', { state: { text: '{"schemaVersion":' + sv + '}' } }, 'message/' + sv);

  /* ── migrate: the v0 prototype shape, and what is left alone ── */
  for (const v of tops) add('migrate', 'LST-11', 'migrate', { state: { text: v } }, 'top/' + v);
  for (const sv of ['MISSING', 'null', '0', '-0', '1', '2', '"0"', 'false', '0.0'])
    add('migrate', 'LST-11', 'migrate', { state: { text: shapeText([sv, 'MISSING', '[{"id":"g"}]'], '"sleep":{"bed":1380,"wake":420},"extra":1') } }, 'schema/' + sv);
  for (const bed of VALUES) for (const wake of ['420', '"07:00"', '1380', 'MISSING', '"1e3"', '[5]', 'null', '-1500'])
    add('migrate', 'LST-12', 'migrate', { state: { text: '{"sleep":{"bed":' + bed + (wake === 'MISSING' ? '' : ',"wake":' + wake) + '}}' } }, 'sleep/' + bed + '/' + wake);
  for (const sleep of ['null', 'true', '0', '1', '"bed"', '[]', '[1380]', '{}', '{"wake":420}', '{"bed":null}', '{"bed":1380,"wake":1380}', '{"bed":1380,"wake":1381}'])
    add('migrate', 'LST-12', 'migrate', { state: { text: '{"sleep":' + sleep + '}' } }, 'sleep-shape/' + sleep);
  const works = ['null', 'false', '0', '""', 'true', '1', '"ab"', '{}', '{"entries":1}', '[]', '[null]', '[1,"x",true]', '["ab"]', '[[1,2]]', '[{}]',
    '[{"title":"Work","days":[1,2],"startMin":540,"durationMin":480}]', '[{"id":"mine","kind":"CLASS"}]', '[{"kind":"SHIFT","id":"x","z":1}]',
    '[{"b":1,"2":2,"1":1,"a":0}]', '[{"__proto__":{"days":[1]},"title":"P"}]', '[{"__proto__":5,"title":"Q"}]', '[{"__proto__":null}]',
    '[{"4294967294":1,"4294967295":2,"9007199254740991":3,"01":4,"-1":5,"1.5":6}]', '["\\ud83d\\ude00x"]', '["\\ud800"]', '[{"id":null}]', '[{"days":[],"id":"w"},{"id":"w"}]'];
  for (const w of works) add('migrate', 'LST-13', 'migrate', { state: { text: '{"work":' + w + ',"goals":[]}' } }, 'work/' + w);
  for (const w of works.slice(0, 12)) add('migrate', 'LST-13', 'migrate', { state: { text: '{"sleep":{"bed":60,"wake":480},"work":' + w + '}' } }, 'sleep-work/' + w);
  const others = { goals: ['[]', '{}', '[1,{"id":"a"}]', '"g"', 'null'], prefs: ['{}', '{"a":1}', 'null', '0', '"p"', '[]', '[1]', 'true'],
    settings: ['{"s":1}', 'null', '""'], playerId: ['"me"', '""', 'null', '0', '5', '[]', '{}', 'true'], timezone: ['"Asia/Tokyo"', '""', 'null', '7', '"Not/AZone"'] };
  for (const [field, values] of Object.entries(others)) for (const v of values) {
    add('migrate', 'LST-14', 'migrate', { state: { text: '{"' + field + '":' + v + ',"keep":"dropped"}' } }, field + '/' + v);
    if (field === 'prefs') add('migrate', 'LST-14', 'migrate', { state: { text: '{"prefs":' + v + ',"settings":{"from":"settings"}}' } }, 'prefs-settings/' + v);
  }
  add('migrate', 'LST-11', 'migrate', { state: { text: JSON.stringify(require('./ld_store_seeds.js').V0) } }, 'v0-prototype');

  /* ── defaults: defaultState and a NEW load under every kind of options ── */
  const optsList = [undefined, {}, OPTS, { playerId: '', timezone: '', nowEpoch: 0 }, { playerId: 'p', timezone: 'UTC', nowEpoch: 1.5 }, { playerId: 0, timezone: 5, nowEpoch: '12' },
    { nowEpoch: -1 }, { nowEpoch: 1e21 }, { playerId: 'x'.repeat(3), timezone: 'America/New_York' }, { playerId: '\ud800', timezone: 'é' }, { nowEpoch: null }, { playerId: [] }];
  optsList.forEach((opts, i) => {
    add('defaults', 'LST-15', 'defaultState', opts === undefined ? {} : { opts }, 'state/' + i);
    add('defaults', 'LST-8 LST-15', 'load', opts === undefined ? { storage: storage([]) } : { storage: storage([]), opts }, 'new/' + i);
    add('defaults', 'LST-21', 'clear', opts === undefined ? { storage: storage([[KEY, seedText('unfinished')]]) } : { storage: storage([[KEY, seedText('unfinished')]]), opts }, 'clear/' + i);
  });
  add('defaults', 'LST-3', 'load', { storage: storage([[KEY, seedText('fresh')]], { faults: [{ op: 'set', key: PROBE, nth: 1, error: err('SecurityError', 'The operation is insecure.') }] }), opts: OPTS }, 'memory-only');

  /* ── load: main and .bak in every combination of present, damaged, invalid, migrated and unreadable ── */
  const corrupt = (t, r) => {
    const k = 1 + r.int(Math.max(1, t.length - 1));
    return r.pick([t.slice(0, k), t.slice(0, k) + t.slice(k + 1), t.slice(0, k) + '\u0000' + t.slice(k), '﻿' + t, t + ',', t.replace(/:0([,}])/, ':NaN$1'),
      t.replace(/"/, "'"), t.slice(0, -1) + ',}', '', ' ', 'undefined', '{"a":1}{', '[' + t, t.replace(/"([a-z])/, '"\\x$1'), '/*c*/' + t, t.replace(/:1([,}])/, ':01$1')]);
  };
  const invalid = ['null', '5', '"text"', 'true', '[]', '{}', '{"schemaVersion":2,"availability":{"rules":[]},"goals":[]}', '{"schemaVersion":"1","availability":{"rules":[]},"goals":[]}',
    '{"schemaVersion":1,"goals":[]}', '{"schemaVersion":1,"availability":{"rules":{}},"goals":[]}', '{"schemaVersion":1,"availability":{"rules":[]}}',
    '{"schemaVersion":1,"availability":{"rules":[]},"goals":{}}', '{"work":"x"}', '{"schemaVersion":0,"work":5}', '{"schemaVersion":null,"sleep":{"bed":"23"}}'];
  const r = random(7);
  const choices = () => {
    const kind = r.pick(['absent', 'absent', 'seed', 'seed', 'seed', 'corrupt', 'corrupt', 'invalid', 'invalid', 'v0']);
    if (kind === 'absent') return [kind, null];
    if (kind === 'seed') return [kind, seedText(r.pick(SMALL_SEEDS.concat(r.next() < 0.1 ? BIG_SEEDS : [])))];
    if (kind === 'corrupt') return [kind, corrupt(r.pick([fresh, unfinished]), r)];
    if (kind === 'invalid') return [kind, r.pick(invalid)];
    return [kind, JSON.stringify({ sleep: { bed: r.int(1440), wake: r.int(1440) }, goals: [], prefs: r.next() < 0.5 ? { a: r.int(3) } : null })];
  };
  const loadFaults = () => {
    const f = [];
    const x = r.int(10);
    if (x === 0) f.push({ op: 'get', key: KEY, nth: 0, error: err('SecurityError', 'The operation is insecure.') });
    if (x === 1) f.push({ op: 'get', key: BAK, nth: 1, error: err('UnknownError', '') });
    if (x === 2) f.push({ op: 'set', key: r.pick([QUARANTINE, QUARANTINE_BAK, '*']), nth: 0, error: 'quota' });
    if (x === 3) f.push({ op: 'set', key: PROBE, nth: 1, error: err('Error', 'no storage') });
    if (x === 4) f.push({ op: 'remove', key: PROBE, nth: 1, error: err('InvalidStateError', 'gone') });
    if (x === 5) f.push({ op: 'get', key: '*', nth: 2, error: err('Error', 'flaky') });
    return f;
  };
  for (let i = 0; i < 1000; i++) {
    const [mk, mv] = choices(), [bk, bv] = choices();
    const extra = {}, f = loadFaults();
    if (f.length) extra.faults = f;
    if (r.int(12) === 0) extra.quota = 60 + r.int(2000);
    const items = [[KEY, mv], [BAK, bv]];
    if (r.int(6) === 0) items.push([QUARANTINE, 'older damage'], ['other', 'x']);
    if (r.int(2) === 0) items.reverse();
    add('load', 'LST-2 LST-4 LST-5 LST-6 LST-7 LST-8', 'load', Object.assign({ storage: storage(items, extra) }, r.int(5) ? { opts: OPTS } : {}), mk + '-' + bk);
  }

  /* ── save: rotation, refusals and every storage failure, on small states ── */
  const states = [fresh, unfinished, setNumber(fresh, 'updatedEpoch', '1793174701'), addMember(fresh, 'note', '"\\u00e9\\ud83d\\ude00"'),
    addMember(unfinished, 'pad', JSON.stringify('y'.repeat(300))), '{"schemaVersion":2,"availability":{"rules":[]},"goals":[]}', '{"schemaVersion":1}', '[]',
    '{"schemaVersion":1,"availability":{"rules":[]},"goals":[]}'];
  const before = [[], [[KEY, seedText('fresh')]], [[KEY, seedText('unfinished')]], [[KEY, '{broken']], [[KEY, seedText('fresh')], [BAK, seedText('unfinished')]],
    [[BAK, seedText('unfinished')]], [[KEY, 'x'.repeat(500)]]];
  const saveFaults = [[], [{ op: 'set', key: BAK, nth: 1, error: 'quota' }], [{ op: 'set', key: KEY, nth: 1, error: 'quota' }], [{ op: 'set', key: KEY, nth: 1, error: err('Error', 'quota') }],
    [{ op: 'set', key: KEY, nth: 1, error: err('InvalidStateError', 'The storage is closed.') }], [{ op: 'set', key: '*', nth: 0, error: err('Error', '') }],
    [{ op: 'get', key: KEY, nth: 1, error: err('SecurityError', 'denied') }], [{ op: 'get', key: KEY, nth: 2, error: err('Error', 'read-back') }],
    [{ op: 'set', key: PROBE, nth: 1, error: 'quota' }], [{ op: 'set', key: BAK, nth: 1, error: err('NotQuotaError', 'QUOTA in the message') }],
    [{ op: 'set', key: KEY, nth: 1, error: err('QuotaExceededError', '') }], [{ op: 'remove', key: '*', nth: 0, error: err('Error', 'no remove') }]];
  const lossy = [[], [{ key: KEY, nth: 1, keep: -1 }], [{ key: KEY, nth: 1, keep: 10 }], [{ key: KEY, nth: 1, keep: 0 }], [{ key: BAK, nth: 1, keep: 5 }]];
  const r2 = random(11);
  for (let i = 0; i < 700; i++) {
    const s = r2.pick(states), b = r2.pick(before), f = r2.pick(saveFaults), l = r2.int(3) ? [] : r2.pick(lossy), extra = {};
    if (f.length) extra.faults = f;
    if (l.length) extra.lossy = l;
    if (r2.int(6) === 0) extra.quota = 40 + r2.int(3000);
    add('save', 'LST-16 LST-17 LST-18 LST-19 LST-20', 'save', { storage: storage(b, extra), state: { text: s } }, 'case');
  }

  /* ── sequence: loads, saves, resets, restores and reloads in a row, with failures coming and going ── */
  const r3 = random(23);
  const step = () => {
    const k = r3.int(12);
    if (k < 3) return { do: 'save' };
    if (k === 3) return { do: 'edit', set: [r3.pick(['updatedEpoch', 'setupComplete', 'note', 'goals', 'schemaVersion']), r3.pick([1793174800 + r3.int(99), true, 'n', [], 2, { $num: '-0' }, 1])] };
    if (k === 4) return { do: 'load', opts: OPTS };
    if (k === 5) return { do: 'create' };
    if (k === 6) return { do: 'clear', opts: OPTS };
    if (k === 7) return { do: 'import', text: r3.pick([seedText('lived-export'), fresh, '{', '{"format":"lifemax-director-backup","version":1}', unfinished]) };
    if (k === 8) return { do: 'export' };
    if (k === 9) return { do: 'storage', faults: r3.pick(saveFaults) };
    if (k === 10) return { do: 'storage', quota: r3.int(3) ? null : 500 + r3.int(3000) };
    return { do: 'storage', items: [[r3.pick([KEY, BAK]), r3.pick([null, '{', seedText('fresh'), '{"schemaVersion":3}'])]] };
  };
  for (let i = 0; i < 400; i++) {
    const steps = [{ do: 'load', opts: OPTS }];
    for (let j = 0, n = 3 + r3.int(9); j < n; j++) steps.push(step());
    const b = r3.pick(before.slice(0, 6));
    add('sequence', 'LST-2 LST-4 LST-18 LST-19 LST-21 LST-23', 'sequence', { storage: storage(b), steps }, 'case');
  }

  /* ── backup: the text format both ways ── */
  const wrap = (s, extra) => '{"format":"lifemax-director-backup","version":1' + (extra || '') + (s === undefined ? '' : ',"state":' + s) + '}';
  const imports = [wrap(fresh), wrap(unfinished), wrap(fresh, ',"version":2'), wrap(fresh).replace('"version":1', '"version":"x"'), wrap(undefined), wrap('null'), wrap('[]'),
    wrap('{"schemaVersion":2}'), wrap('{"sleep":{"bed":1}}'), wrap('{"work":{}}'), wrap('5'), wrap(wrap(fresh)), wrap(fresh).replace('lifemax-director-backup', 'Lifemax-director-backup'),
    '{"format":"other","state":' + fresh + '}', fresh, unfinished, '{', '', 'null', '5', '"s"', '[]', '{"work":"ab"}', wrap(fresh) + ' ', ' \n' + wrap(fresh), wrap(fresh) + 'x', "{'a':1}",
    '{"format":"lifemax-director-backup","state":' + fresh + ',"state":null}', '{"state":' + fresh + ',"format":"lifemax-director-backup"}', '{"format":["lifemax-director-backup"],"state":' + fresh + '}'];
  imports.forEach((t, i) => add('backup', 'LST-23', 'import', { text: t }, 'import/' + i));
  const trees = [{ a: { $num: 'NaN' }, b: { $num: '-0' }, c: [{ $num: 'Infinity' }, { $num: '-Infinity' }] }, {}, [], { x: [] , y: {} }, { s: '\ud800 \u0007"\\/' },
    { n: [1e21, 1e-7, 5e-324, -1.5, 123456789012345680000] }, { deep: [[[[[{ a: [[]] }]]]]] }, { '2': 'b', '1': 'a', z: 0, '01': 'c' }, { '10': 'ten', '9': 'nine', '100': 'hundred' }, 'text', 5, null, true];
  trees.forEach((t, i) => add('backup', 'LST-22', 'export', { state: { tree: t } }, 'export-tree/' + i));
  for (const t of states) add('backup', 'LST-22', 'export', { state: { text: t } }, 'export');
  for (const t of states) add('backup', 'LST-22 LST-23', 'sequence', { storage: storage([]), steps: [{ do: 'import', text: t }, { do: 'export' }, { do: 'create' }, { do: 'load', opts: OPTS }] }, 'round-trip');

  /* ── numbers: number texts the page normalises when it writes ── */
  for (const n of ODD_NUMBERS) for (const field of ['updatedEpoch', 'sleepRequirementMinutes', 'createdEpoch']) {
    const t = setNumber(fresh, field, n);
    add('numbers', 'LST-17', 'save', { storage: storage([[KEY, t]]), state: { text: t } }, field + '/' + n);
    if (field === 'updatedEpoch') {
      add('numbers', 'LST-7', 'load', { storage: storage([[KEY, t]]), opts: OPTS }, field + '/' + n);
      add('numbers', 'LST-22', 'export', { state: { text: t } }, field + '/' + n);
    }
  }
  for (const n of ODD_NUMBERS) {
    add('numbers', 'LST-10', 'validate', { state: { text: setNumber(fresh, 'schemaVersion', n) } }, 'schemaVersion/' + n);
    add('numbers', 'LST-7 LST-11', 'load', { storage: storage([[KEY, setNumber(fresh, 'schemaVersion', n)]]), opts: OPTS }, 'load-schemaVersion/' + n);
  }

  /* ── unknown: fields Life Director does not know, at every level, kept through load and save ── */
  const unknowns = [['zUnknown', '{"a":[1,{"b":null}],"c":"\\u00e9"}'], ['2', '"index key"'], ['__proto__', '{"polluted":true}'], ['', '"empty key"'], ['é😀', '1'],
    ['dup', '1'], ['schedule2', '[' + '1,'.repeat(50) + '1]'], ['ui2', '{"tab":"week","x":{"y":{"z":[[[]]]}}}'], ['4294967295', '0'], ['4294967294', '0'], ['01', '"lead zero"']];
  for (const base of ['fresh', 'unfinished', 'lived']) for (const [k, v] of unknowns) {
    let t = addMember(seeds[base], k, v);
    if (k === 'dup') t = addMember(t, 'dup', '2');
    add('unknown', 'LST-7 LST-17', 'sequence', { storage: storage([[KEY, t]]), steps: [{ do: 'load', opts: OPTS }, { do: 'save' }, { do: 'edit', set: ['updatedEpoch', 1793174999] }, { do: 'save' }, { do: 'export' }] }, base + '/' + k);
  }
  for (const [k, v] of unknowns) {
    const t = fresh.replace('"availability":{', '"availability":{' + JSON.stringify(k) + ':' + v + ',').replace('"ledger":{', '"ledger":{' + JSON.stringify(k) + ':' + v + ',');
    add('unknown', 'LST-7 LST-17', 'sequence', { storage: storage([[KEY, t]]), steps: [{ do: 'load', opts: OPTS }, { do: 'save' }] }, 'nested/' + k);
  }

  /* ── older: what earlier page versions left in storage ── */
  /* Before 33eff52 (2026-11-06) clear() copied the main key to .bak and removed the main key (engine.js:2333-2336 explains why that was
     replaced): the next load found no main key and brought the old state back from .bak. */
  for (const name of ['fresh', 'unfinished', 'lived']) {
    add('older', 'LST-4 LST-7', 'load', { storage: storage([[BAK, seedText(name)]]), opts: OPTS }, 'old-clear/' + name);
    add('older', 'LST-4 LST-18', 'sequence', { storage: storage([[BAK, seedText(name)]]), steps: [{ do: 'load', opts: OPTS }, { do: 'clear', opts: OPTS }, { do: 'create' }, { do: 'load', opts: OPTS }] }, 'old-clear-then-reset/' + name);
  }
  const dropFields = (text, fields) => { const o = JSON.parse(text); for (const f of fields) delete o[f]; return JSON.stringify(o); };
  for (const fields of [['fixes'], ['hostSync', 'fixes'], ['questOverlay', 'hostSync', 'fixes'], ['labQuests', 'bridge'], ['ledger'], ['changeLog', 'energyCheckin', 'lastGeneratedWeekKey'], ['ui']]) {
    const t = dropFields(unfinished, fields);
    add('older', 'LST-7', 'sequence', { storage: storage([[KEY, t]]), steps: [{ do: 'load', opts: OPTS }, { do: 'save' }] }, 'without/' + fields.join('+'));
  }

  /* ── depth: nesting beyond what native reads (LST-26) ── */
  for (const d of [10, 100, 511, 512, 513, 600, 2000]) {
    const t = fresh.replace('"prefs":{}', '"prefs":{"deep":' + '['.repeat(d) + ']'.repeat(d) + '}');
    add('depth', 'LST-26', 'load', { storage: storage([[KEY, t], [BAK, seedText('unfinished')]]), opts: OPTS }, 'load/' + d);
    add('depth', 'LST-26', 'save', { storage: storage([]), state: { text: t } }, 'save/' + d);
  }

  /* ── fuzz: random JSON trees and mangled texts stored as the main key ── */
  const r4 = random(31);
  const tree = depth => {
    const k = r4.int(depth > 3 ? 5 : 8);
    if (k === 0) return r4.pick([null, true, false]);
    if (k === 1) return r4.pick([0, -1, 1.5, 1e21, 1793169000, 480]);
    if (k === 2) return r4.pick(['', 'a', 'é', 'x\ny', '😀']);
    if (k >= 5 && k < 7) { const o = {}; for (let i = r4.int(5); i > 0; i--) o[r4.pick(['a', 'b', 'goals', 'rules', 'sleep', 'work', 'bed', 'wake', '1', 'schemaVersion', 'prefs'])] = tree(depth + 1); return o; }
    const a = []; for (let i = r4.int(4); i > 0; i--) a.push(tree(depth + 1)); return a;
  };
  for (let i = 0; i < 500; i++) {
    const base = JSON.parse(r4.pick([fresh, unfinished]));
    const keys = Object.keys(base);
    for (let j = 1 + r4.int(3); j > 0; j--) { const k = r4.next() < 0.7 ? r4.pick(keys) : 'f' + r4.int(9); base[k] = tree(0); }
    if (r4.int(4) === 0) delete base.schemaVersion;
    let t = JSON.stringify(base);
    if (r4.int(5) === 0) t = corrupt(t, r4);
    add('fuzz', 'LST-4 LST-5 LST-6 LST-7 LST-11 LST-17', 'sequence', { storage: storage([[KEY, t], [BAK, seedText(r4.pick(SMALL_SEEDS))]]), steps: [{ do: 'load', opts: OPTS }, { do: 'save' }, { do: 'create' }, { do: 'load', opts: OPTS }] }, 'case');
  }

  /* ── browser: Chromium's own localStorage (a clean origin), checking the quota model the other cases' storage uses (LST-24) ── */
  const room = CHROMIUM_QUOTA - KEY.length;
  const grow = (from, length) => ({ grow: { from, field: 'pad', length } });
  add('browser', 'LST-18 LST-24', 'save', { storage: storage([]), state: { text: seedText('fresh') } }, 'save-fresh');
  add('browser', 'LST-24', 'save', { storage: storage([]), state: { text: grow(seedText('fresh'), room) } }, 'save-fills-exactly');
  add('browser', 'LST-19 LST-24', 'save', { storage: storage([]), state: { text: grow(seedText('fresh'), room + 1) } }, 'save-one-over');
  add('browser', 'LST-19 LST-24', 'save', { storage: storage([[KEY, grow(seedText('fresh'), 3000000)]]), state: { text: seedText('unfinished') } }, 'rotation-over-quota');
  add('browser', 'LST-18 LST-24', 'save', { storage: storage([[KEY, grow(seedText('fresh'), 2000000)]]), state: { text: seedText('unfinished') } }, 'rotation-fits');
  add('browser', 'LST-2 LST-3 LST-24', 'load', { storage: storage([[KEY, seedText('fresh')], ['filler', { repeat: { unit: 'x', count: CHROMIUM_QUOTA - KEY.length - fresh.length - 'filler'.length } }]]), opts: OPTS }, 'full-storage-probe');
  add('browser', 'LST-5 LST-24', 'load', { storage: storage([[KEY, '{"a":'], ['filler', { repeat: { unit: 'x', count: CHROMIUM_QUOTA - KEY.length - 5 - 'filler'.length - 40 } }]]), opts: OPTS }, 'quarantine-no-room');
  add('browser', 'LST-17', 'save', { storage: storage([]), state: { text: addMember(fresh, 's', '"\\ud800\\udfff\\udc00\\u2028"') } }, 'lone-surrogates');
  add('browser', 'LST-4', 'load', { storage: storage([[KEY, '{"s":"\ud800"}'], [BAK, seedText('fresh')]]), opts: OPTS }, 'raw-lone-surrogate');
  add('browser', 'LST-18', 'sequence', { storage: storage([]), steps: [{ do: 'load', opts: OPTS }, { do: 'save' }, { do: 'edit', set: ['updatedEpoch', 5] }, { do: 'save' }, { do: 'create' }, { do: 'load', opts: OPTS }] }, 'rotation');
  return cases;
}

module.exports = { defineCases, FAMILIES, KEY, BAK, PROBE, QUARANTINE, CHROMIUM_QUOTA };
