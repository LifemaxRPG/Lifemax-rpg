/* The Life Director availability layer's golden cases (WP-LD2): what tools/gen_ld_availability_golden.js records from the page and the
   native availability code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce. Rules are
   LIFE_DIRECTOR_ENGINE_SPEC.md's part 2 (LDA, LDP, LDS, LDG).

   Every case is {id, family, rule, fn, args}; the recorder adds out. As in tools/ld_kernel_cases.js the definitions use no Intl: every
   instant comes from Date.UTC and the clock-change table of that file, and only the strict zones of the WP-LD1 matrix are used (no
   tz-fragile zone), so the same cases come out of every runner. Inputs are JSON values, as the page's store holds them; a number JSON
   cannot hold is written {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'} (only the helper family needs one). */
'use strict';
const { MATRIX, FIXED, CHANGES } = require('./ld_kernel_cases.js');

const FAMILIES = ['helper', 'rule', 'applies', 'ruleon', 'materialize', 'prefs', 'sleep', 'paint', 'avail', 'scenario', 'dst', 'fuzz'];
const ZONES = MATRIX;
/* The ten built-in scenarios (engine.js SCENARIOS): their availability rules, sleep need and preferences are taken from the engine's
   own buildScenario (they depend on the seed, never on a zone) and planned again in every zone of the matrix. */
const SCENARIO_NAMES = ['office', 'night', 'rotating', 'student', 'abundant', 'limited', 'manygoals', 'deadlines', 'misses', 'workchange'];
/* The scenarios that plan the office scenario's availability (the same rules, sleep need and, for availability, preferences). */
const SAME_AS_OFFICE = ['manygoals', 'deadlines', 'misses', 'workchange'];

/* ---------- small helpers (no Intl) ---------- */
const utc = (y, mo, d, h = 0, mi = 0, s = 0) => Date.UTC(y, mo - 1, d, h, mi, s) / 1000;
const num = v => (Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' } : Object.is(v, -0) ? { $num: '-0' } : v);
function offsetAt(zone, t) {
  if (zone in FIXED) return FIXED[zone];
  let off = null;
  for (const c of CHANGES.filter(c => c.zone === zone)) { if (off === null) off = c.before; if (t >= c.at) off = c.after; }
  return off;
}
/* The instant of a local wall time by the table; only used to choose interesting instants. */
const localInstant = (zone, y, mo, d, min = 0) => { const w = utc(y, mo, d, 0, min); return w - offsetAt(zone, w - offsetAt(zone, w) * 60) * 60; };
const ymdOf = t => { const x = new Date(t * 1000); return { y: x.getUTCFullYear(), mo: x.getUTCMonth() + 1, d: x.getUTCDate() }; };
const key = v => v.y + '-' + String(v.mo).padStart(2, '0') + '-' + String(v.d).padStart(2, '0');
/* Seeded generator (xorshift32; not the engine's mulberry32). */
function random(seed) {
  let x = seed >>> 0 || 1;
  return () => { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; };
}
/* The 2026 and 2027 clock changes of the matrix, each with its local window: a gap (skipped wall times) or a fold (repeated ones), as
   wall clock minutes from the local midnight of `date`. */
const CLOCK_CHANGES = CHANGES.filter(c => ZONES.includes(c.zone)).map(c => {
  const a = c.at + c.before * 60, b = c.at + c.after * 60, lo = Math.min(a, b), hi = Math.max(a, b), date = ymdOf(lo);
  return Object.assign({}, c, { kind: c.after > c.before ? 'gap' : 'fold', date, from: (lo - utc(date.y, date.mo, date.d)) / 60, to: (hi - utc(date.y, date.mo, date.d)) / 60 });
});

/* Odd JSON values a stored rule field can hold, for the normaliser. */
const ODD = [null, true, false, 0, num(-0), 1, -1, 0.5, 1.5, -0.5, 2.5, 14.9, 15.1, 1439.6, 1440, 99999, -99999, '', ' ', '60', ' 90 ', '1e2', '0x1E', 'abc',
  'Infinity', [], [30], [1, 2], {}, { a: 1 }];

function defineCases(engine) {
  const cases = [], ids = new Set();
  const add = (family, rule, fn, args, tag) => {
    let id = family + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args });
  };
  const short = v => String(JSON.stringify(v)).replace(/[^\x20-\x7e]/g, '?').slice(0, 60);

  /* helper: clamp and cmpStr, with the loose comparisons the engine relies on */
  const CL = [-1, 0, 5, 15, 1439, 1440, 2000, 0.5, -0.5, num(-0), num(NaN), num(Infinity), num(-Infinity), '30', '200', '-5', 'abc', '', null, true, false,
    [], [7], [1, 2], {}];
  CL.forEach((x, i) => { add('helper', 'LDG-1', 'clamp', [x, 0, 120], i + ':' + short(x)); add('helper', 'LDG-1', 'clamp', [x, 15, 1440], i + ':' + short(x) + '@15'); });
  for (const [a, b] of [['a', 'b'], ['b', 'a'], ['a', 'a'], ['', 'a'], ['A', 'a'], ['a', 'B'], ['rule10', 'rule2'], ['work', 'work-late'], ['é', 'f'],
    ['\u{1F600}', '￿'], ['￿', '\u{1F600}'], ['10', '9'], ['r-1', 'r_1'], ['Z', 'a'], ['sleep-on', 'sleep-off'], ['', '']])
    add('helper', 'LDG-2', 'cmpStr', [a, b], short(a) + '|' + short(b));

  /* rule: normalizeRule on every field's shapes */
  const BASE = { id: 'r1', kind: 'WORK', title: 'Shift', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480, commuteMin: 30 };
  const FIELDS = ['id', 'kind', 'title', 'days', 'startMin', 'durationMin', 'commuteMin', 'decompressMin', 'load', 'validFrom', 'validTo', 'rotation'];
  for (const f of FIELDS) {
    ODD.forEach((v, i) => add('rule', 'LDA-1', 'normalizeRule', [Object.assign({}, BASE, { [f]: v }), 0], f + '=' + i + ':' + short(v)));
    const without = Object.assign({}, BASE); delete without[f];
    add('rule', 'LDA-1', 'normalizeRule', [without, 4], f + '-missing');
  }
  for (const k of ['WORK', 'CLASS', 'APPOINTMENT', 'UNAVAILABLE', 'SLEEP', 'PREFERRED', 'work', 'Sleep', 'SLEEP_WINDOW', 'OTHER'])
    for (const rt of [undefined, 'HARD_COMMITMENT', 'SLEEP_WINDOW', 'UNAVAILABLE', 'PREFFERED_WINDOW', 'PREFERRED_WINDOW', 'WORK', 'x', ['SLEEP_WINDOW']]) {
      const r = { id: 'k', ruleType: rt }; if (k !== 'OTHER') r.kind = k;
      add('rule', 'LDA-1 LDA-2', 'normalizeRule', [r, 1], k + '|' + short(rt));
    }
  /* the legacy field names (ruleId, dayOfWeek, startMinuteOfDay, durationMinutes, shiftIntensity, weeklyRecurringRules shapes) */
  const LEGACY = [{ ruleId: 'legacy', ruleType: 'HARD_COMMITMENT', dayOfWeek: 3, startMinuteOfDay: 480, durationMinutes: 240, shiftIntensity: 'PHYSICAL_HEAVY' },
    { ruleId: 'l2', dayOfWeek: 0, startMinuteOfDay: 0, durationMinutes: 0 }, { ruleId: 'l3', dayOfWeek: '6' }, { ruleId: 'l4', dayOfWeek: 7 }, { ruleId: 'l5', dayOfWeek: null },
    { id: 'both', ruleId: 'x', days: [2], dayOfWeek: 4, startMin: 60, startMinuteOfDay: 120, durationMin: 30, durationMinutes: 90, load: 'LIGHT', shiftIntensity: 'PHYSICAL_HEAVY' },
    { id: '', ruleId: '', title: '' }, { id: 0, ruleId: 7 }, { id: 12 }, { id: [1, 2] }, { id: { a: 1 } }, { id: true }, { startMin: null, startMinuteOfDay: 90 },
    { durationMin: null, durationMinutes: 0 }, { durationMin: 0 }, { commuteMin: 241 }, { commuteMin: '15' }, {}, { days: [] }];
  LEGACY.forEach((r, i) => { add('rule', 'LDA-1 LDA-2', 'normalizeRule', [r, i], 'legacy' + i); add('rule', 'LDA-1', 'normalizeRule', [r, 10 + i], 'legacy' + i + '@' + (10 + i)); });
  const DAYS = [[0, 1, 2, 3, 4, 5, 6], [6, 5, 4, 3, 2, 1, 0], [3, 3, 1], [7, -1, 2], ['1', 2], [' 3 ', '4'], [null], [true, false], [[5]], [[1, 2]], [{}], [0.5, 6.5, 6.0001],
    [num(-0), 0], ['', 'x'], [1e-7, '1e0'], ['0x6', '0b1'], [10, 2, 1], ['10', '2', '1'], [5, 6], [], ['Infinity'], [6, 'null', null]];
  DAYS.forEach((d, i) => add('rule', 'LDA-1 LDA-3', 'normalizeRule', [{ id: 'd', days: d }, 0], 'days' + i + ':' + short(d)));
  const ROT = [{ anchorDate: '2026-10-24', cycleLength: 8, activeDays: [0, 1, 2, 3] }, { anchorDate: '2026-10-24', cycleLength: 0 }, { anchorDate: '', cycleLength: 3 },
    { anchorDate: '2026-01-05', cycleLength: -2, activeDays: [0] }, { anchorDate: '2026-01-05', cycleLength: 2.4, activeDays: [0] }, { anchorDate: '2026-01-05', cycleLength: 0.4 },
    { anchorDate: '2026-01-05', cycleLength: '3', activeDays: ['0', '2'] }, { anchorDate: '2026-01-05', cycleLength: 3, activeDays: null },
    { anchorDate: '2026-01-05', cycleLength: 3, activeDays: [] }, { anchorDate: '2026-01-05', cycleLength: 3, activeDays: [null, true, 'x', [2], {}, 1.5, -1] },
    { anchorDate: '2026-01-05', cycleLength: 3, activeDays: '012' }, { anchorDate: '2026-01-05', cycleLength: 3, activeDays: { 0: 1 } },
    { anchorDate: '2026-01-05', cycleLength: 3, activeDays: 5 }, { anchorDate: 20260105, cycleLength: 3, activeDays: [1] }, { anchorDate: ['2026-01-05'], cycleLength: 3 },
    { cycleLength: 3, activeDays: [0] }, { anchorDate: '2026-01-05' }, { anchorDate: '2026-01-05', cycleLength: '' }, { anchorDate: '2026-01-05', cycleLength: [4] },
    'rotation', true, [1, 2], 7, { anchorDate: 'x', cycleLength: 1e9, activeDays: [0] }, { anchorDate: '2026-01-05', cycleLength: 3, activeDays: [0], extra: 1 }];
  ROT.forEach((rot, i) => add('rule', 'LDA-1 LDA-4', 'normalizeRule', [{ id: 'rot', days: [1], rotation: rot }, 0], 'rotation' + i + ':' + short(rot)));
  for (const v of [null, undefined, 'x', 5, true, [], ['a'], 0, '', false]) add('rule', 'LDA-1', 'normalizeRule', v === undefined ? [] : [v, 2], 'whole:' + short(v === undefined ? 'undefined' : v));

  /* applies (engine) and ruleon (the page's own copy, logic.js ruleOn): which dates a rule applies on, as one character per day */
  const appliesCase = (rule, start, count, tag, ids) => {
    add('applies', ids, 'ruleAppliesOn', [rule, start, count], tag);
    add('ruleon', 'LDA-9', 'ruleOn', [rule, start, count], tag);
  };
  for (let mask = 0; mask < 128; mask++) {
    const days = [0, 1, 2, 3, 4, 5, 6].filter(d => mask & (1 << d));
    appliesCase({ id: 'w', days }, { y: 2026, mo: 10, d: 25 }, 14, 'week:' + days.join(''), 'LDA-5');
  }
  const CAD = [[2, [0]], [2, [1]], [3, [0]], [3, [0, 2]], [4, [0, 1]], [5, [4]], [7, [0]], [8, [0, 1, 2, 3]], [8, [4, 5, 6, 7]], [9, [0, 8]], [10, [9]], [14, [0, 7]],
    [14, [0, 1, 2, 3, 4]], [21, [20]], [28, [0, 13, 27]], [1, [0]], [6, [6]], [12, [0, 3, 6, 9]]];
  const ANCHORS = ['2026-10-24', '2026-10-25', '2026-12-31', '2026-01-01', '2025-02-28', '2024-02-29', '1970-01-01', '2099-06-30', '2026-10-31', '2027-03-01'];
  CAD.forEach(([len, act], i) => ANCHORS.forEach(a => appliesCase({ id: 'rot', days: [1, 2], rotation: { anchorDate: a, cycleLength: len, activeDays: act } },
    { y: 2026, mo: 10, d: 20 }, 42, 'cadence' + i + ':' + len + '/' + act.join('') + '@' + a, 'LDA-6')));
  const ODD_ROT = [{ anchorDate: '2026-10-24', cycleLength: 7.4, activeDays: [0, 3] }, { anchorDate: '2026-10-24', cycleLength: 7.6, activeDays: [0, 3] },
    { anchorDate: '2026-10-24', cycleLength: 2.5, activeDays: [0, 1, 2] }, { anchorDate: '2026-10-24', cycleLength: 0.4, activeDays: [0] },
    { anchorDate: '2026-10-24', cycleLength: 0.4, activeDays: ['x'] }, { anchorDate: '2026-10-24', cycleLength: '8', activeDays: [0, 1, 2, 3] },
    { anchorDate: '2026-10-24', cycleLength: '3', activeDays: [0] }, { anchorDate: '2026-10-24', cycleLength: [5], activeDays: [0] },
    { anchorDate: '2026-10-24', cycleLength: 3, activeDays: ['1', null, ' 2 '] }, { anchorDate: '2026-10-24', cycleLength: 3, activeDays: [num(-0)] },
    { anchorDate: '2026-10-24', cycleLength: 3, activeDays: [3, -1, 1.5] }, { anchorDate: '2026-1-5', cycleLength: 3, activeDays: [0] },
    { anchorDate: '0026-10-24', cycleLength: 3, activeDays: [0] }, { anchorDate: '2026-02-30', cycleLength: 3, activeDays: [0] },
    { anchorDate: '2026-13-01', cycleLength: 3, activeDays: [0] }, { anchorDate: '2026-10', cycleLength: 3, activeDays: [0] },
    { anchorDate: 'garbage', cycleLength: 3, activeDays: [0] }, { anchorDate: 'garbage', cycleLength: 3, activeDays: ['x'] },
    { anchorDate: ' 2026-10-24', cycleLength: 3, activeDays: [0] }, { anchorDate: '2026-10-24T09:00', cycleLength: 3, activeDays: [0] },
    { anchorDate: 20261024, cycleLength: 3, activeDays: [0] }, { anchorDate: ['2026-10-24'], cycleLength: 3, activeDays: [0] },
    { anchorDate: true, cycleLength: 3, activeDays: [0] }, { anchorDate: '2026-10-24', cycleLength: 3, activeDays: [0], extra: 'x' },
    { anchorDate: '2026-10-24', cycleLength: 1e6, activeDays: [0, 999999] }, { anchorDate: '+002026-10-24', cycleLength: 3, activeDays: [0] }];
  ODD_ROT.forEach((rot, i) => appliesCase({ id: 'odd', days: [1], rotation: rot }, { y: 2026, mo: 10, d: 20 }, 21, 'oddrotation' + i + ':' + short(rot), 'LDA-6'));
  const VALID = [['2026-10-26', null], [null, '2026-10-30'], ['2026-10-26', '2026-10-30'], ['2026-10-30', '2026-10-26'], ['2026-10-28', '2026-10-28'],
    ['2026-1-5', null], [null, '2026-10'], ['2026-10-26T00:00', null], ['', ''], [20261026, null], [['2026-10-27'], null], [true, null], [{}, null], [null, {}],
    ['2026-10-27 ', null], ['2026-10-27', 'x'], ['A', null], [null, '0'], ['2027-01-01', null], ['0', '9']];
  VALID.forEach(([f, t], i) => {
    const r = { id: 'v', days: [0, 1, 2, 3, 4, 5, 6] }; if (f !== null) r.validFrom = f; if (t !== null) r.validTo = t;
    appliesCase(r, { y: 2026, mo: 10, d: 24 }, 10, 'valid' + i + ':' + short([f, t]), 'LDA-7');
    appliesCase(Object.assign({}, r, { rotation: { anchorDate: '2026-10-24', cycleLength: 2, activeDays: [0] } }), { y: 2026, mo: 10, d: 24 }, 10, 'valid-rotation' + i, 'LDA-7 LDA-6');
  });
  appliesCase({ id: 'v', days: [0, 1, 2, 3, 4, 5, 6], validFrom: '2026-1-5' }, { y: 2026, mo: 9, d: 26 }, 10, 'unpadded-from', 'LDA-7');
  appliesCase({ id: 'v', days: [0, 1, 2, 3, 4, 5, 6], validTo: '2026-10' }, { y: 2026, mo: 9, d: 26 }, 10, 'month-to', 'LDA-7');
  const LEGACY_DAYS = [{ id: 'dow', dayOfWeek: 3 }, { id: 'dow', dayOfWeek: '3' }, { id: 'dow', dayOfWeek: 0, days: null }, { id: 'dow', dayOfWeek: 9 },
    { id: 'dow', days: 'weekdays' }, { id: 'dow', days: { 0: 1 } }, { id: 'dow' }, { id: 'dow', days: ['1', 2] }, { id: 'dow', days: [num(-0), 6.0] }];
  LEGACY_DAYS.forEach((r, i) => appliesCase(r, { y: 2026, mo: 10, d: 25 }, 7, 'legacy' + i + ':' + short(r), 'LDA-5'));
  for (const [y, mo, d] of [[2026, 12, 28], [2024, 2, 26], [2100, 2, 25], [2026, 2, 26], [1999, 12, 30]])
    appliesCase({ id: 'cross', days: [0, 3, 6], rotation: { anchorDate: '2026-01-01', cycleLength: 5, activeDays: [0, 2] } }, { y, mo, d }, 9, 'calendar:' + y + '-' + mo + '-' + d, 'LDA-6');

  /* materialize: a rule's intervals over a horizon (the day before through the day after), across midnights and every clock change */
  const mat = (raw, t, days, zone, tag, ids) => add('materialize', ids, 'materializeRule', [raw, t, days, zone], tag);
  const MRULES = [['nine-five', { id: 'w', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480 }],
    ['overnight', { id: 'n', kind: 'WORK', days: [1, 2, 3, 4, 5, 6], startMin: 1320, durationMin: 510, title: 'Night' }],
    ['sleep', { id: 's', kind: 'SLEEP', startMin: 1380, durationMin: 480 }], ['day-long', { id: 'a', kind: 'UNAVAILABLE', days: [6], startMin: 0, durationMin: 1440 }],
    ['late-start', { id: 'l', kind: 'CLASS', days: [2, 4], startMin: 1439, durationMin: 15 }],
    ['rotation', { id: 'r', kind: 'WORK', startMin: 420, durationMin: 720, rotation: { anchorDate: '2026-10-24', cycleLength: 8, activeDays: [0, 1, 2, 3] } }],
    ['odd-minutes', { id: 'o', kind: 'APPOINTMENT', days: [0, 3], startMin: 607.5, durationMin: 37.4 }],
    ['clamped', { id: 'c', kind: 'PREFERRED', days: [1], startMin: 5000, durationMin: 5000 }], ['nan', { id: 'x', kind: 'WORK', startMin: 'abc' }]];
  for (const zone of ZONES) for (const [name, r] of MRULES) {
    mat(r, localInstant(zone, 2026, 10, 26, 360), 7, zone, name + '@' + zone + '@week', 'LDA-8');
    mat(r, localInstant(zone, 2026, 12, 31, 600), 2, zone, name + '@' + zone + '@year-end', 'LDA-8');
  }
  for (const c of CLOCK_CHANGES) {
    const t = localInstant(c.zone, c.date.y, c.date.mo, c.date.d - 1, 360), tag = c.zone + '@' + key(c.date) + '-' + c.kind;
    const mids = [c.from, c.from + 15, c.from + 30, c.to - 15, c.to, c.from - 15].filter(m => m >= 0 && m <= 1439);
    for (const m of mids) {
      mat({ id: 'starts', kind: 'WORK', startMin: m, durationMin: 240 }, t, 3, c.zone, tag + '+start' + m, 'LDA-8 LDT-8 LDT-9');
      mat({ id: 'ends', kind: 'WORK', startMin: (m + 1440 - 480) % 1440, durationMin: 480 }, t, 3, c.zone, tag + '+end' + m, 'LDA-8 LDT-8 LDT-9');
      mat({ id: 'short', kind: 'SLEEP', startMin: m, durationMin: 15 }, t, 3, c.zone, tag + '+quarter' + m, 'LDA-8 LDT-8 LDT-9');
    }
    mat({ id: 'across', kind: 'SLEEP', startMin: 1320, durationMin: 600 }, t, 3, c.zone, tag + '+overnight', 'LDA-8 LDT-8 LDT-9');
    mat({ id: 'whole', kind: 'UNAVAILABLE', startMin: 0, durationMin: 1440 }, t, 3, c.zone, tag + '+whole-day', 'LDA-8 LDT-8');
  }
  mat({ id: 'none', kind: 'WORK' }, 0, 0, 'UTC', 'no-days', 'LDA-8');
  mat({ id: 'num-anchor', rotation: { anchorDate: 5, cycleLength: 2, activeDays: [0] } }, 1793422800, 1, 'UTC', 'number-anchor', 'LDA-8 LDA-6');

  /* prefs: the defaults and the normaliser */
  add('prefs', 'LDP-1', 'defaultPreferences', [], 'defaults');
  add('prefs', 'LDP-2', 'normalizePreferences', [], 'undefined');
  for (const p of [null, {}, 0, '', false, 'ab', 7, true, [5, 6], { 0: 'x', z: 1 }]) add('prefs', 'LDP-2', 'normalizePreferences', [p], 'whole:' + short(p));
  const PF = { maxDailyGoalMinutes: [0, 29, 30, 300, 301, 480, 481, '240', 'x', null, -5, 1e9, 100.5, true, [200]],
    minDailyRecoveryMinutes: [0, 29, 360, 361, '45', 'x', 30.5], maxConsecutiveHighLoadMinutes: [0, 29, 240, 241, ' 60 ', 'x'],
    minSleepMinutes: [0, 299, 300, 451, 720, 721, '480', 'x', 1e-9], planningHorizonDays: [0, 0.4, 0.5, 1, 1.5, 2.5, 14, 28, 29, -3, '7', 'x', 27.5, 28.49],
    intensityLevel: ['LIGHT', 'BALANCED', 'AGGRESSIVE', 'HARDCORE', 'hardcore', '', null, 5], consequenceMode: ['LENIENT', 'MODERATE', 'HARDCORE_RPG', 'STRICT', null],
    morningRoutineMinutes: [0, 45, '30', null], windDownMinutes: [0, 60, '15'], mealMinutes: [0, 20, 45, '30'], mealOffsetsMinutes: [[], [60], [30, 300, 600, 900]],
    mealsEnabled: [false, 0, 'yes'], defaultBedtimeMin: [0, 1320, 1439, '1380'], preferredRestDays: [[0, 6], 'x'], extraKey: [1, 'x', { a: [1] }] };
  for (const [k, vals] of Object.entries(PF)) vals.forEach((v, i) => add('prefs', 'LDP-2', 'normalizePreferences', [{ [k]: v }], k + '=' + i + ':' + short(v)));
  for (const [lvl, goal] of [['HARDCORE', 30], ['HARDCORE', 200], ['HARDCORE', 400], ['HARDCORE', 1000], ['HARDCORE', 'x'], ['AGGRESSIVE', 400], ['LIGHT', 1000], ['HARDCORE', 0]])
    add('prefs', 'LDP-2 LDP-3', 'normalizePreferences', [{ intensityLevel: lvl, maxDailyGoalMinutes: goal }], 'ceiling:' + lvl + '/' + goal);

  /* sleep: deriveSleep, explicit and default sleep, conflicts with commitments, merges and warnings */
  const sleepCase = (rules, t, days, zone, prefs, req, hard, tag, ids) => add('sleep', ids, 'deriveSleep', [rules, t, days, zone, prefs, req, hard], tag);
  const mon = zone => localInstant(zone, 2026, 10, 26, 360);
  const at = (zone, d, min) => localInstant(zone, 2026, 10, d, min);
  for (const zone of ZONES) {
    const t = mon(zone);
    sleepCase([], t, 3, zone, {}, 480, [], zone + '@default', 'LDS-1 LDS-2');
    sleepCase([], t, 3, zone, { defaultBedtimeMin: 1320, morningRoutineMinutes: 45 }, 450, [], zone + '@default-early', 'LDS-1 LDS-2');
    sleepCase([], t, 3, zone, {}, 480, [{ startEpoch: at(zone, 27, 360), endEpoch: at(zone, 27, 840), commuteMin: 30, title: 'Early shift' }], zone + '@conflict', 'LDS-2 LDS-3');
    sleepCase([], t, 3, zone, {}, 480, [{ startEpoch: at(zone, 27, 240), endEpoch: at(zone, 27, 600), title: 'Very early' }], zone + '@conflict-floor', 'LDS-2 LDS-3');
    sleepCase([], t, 3, zone, {}, 480, [{ startEpoch: at(zone, 27, 420), endEpoch: at(zone, 27, 900), title: 'B' }, { startEpoch: at(zone, 27, 390), endEpoch: at(zone, 27, 450), commuteMin: 15, title: 'A' }],
      zone + '@two-conflicts', 'LDS-2 LDS-3');
    sleepCase([{ id: 's', kind: 'SLEEP', startMin: 1380, durationMin: 480 }], t, 3, zone, {}, 480, [{ startEpoch: at(zone, 27, 360), endEpoch: at(zone, 27, 840), title: 'X' }], zone + '@explicit', 'LDS-1 LDS-4');
    sleepCase([{ id: 's1', kind: 'SLEEP', startMin: 1380, durationMin: 240 }, { id: 's2', kind: 'SLEEP', startMin: 210, durationMin: 240 }], t, 3, zone, {}, 480, [], zone + '@merge-30', 'LDS-4 LDS-5');
    sleepCase([{ id: 's1', kind: 'SLEEP', startMin: 1380, durationMin: 240 }, { id: 's2', kind: 'SLEEP', startMin: 211, durationMin: 240 }], t, 3, zone, {}, 480, [], zone + '@merge-31', 'LDS-4 LDS-5');
    sleepCase([{ id: 'nap', kind: 'SLEEP', startMin: 840, durationMin: 60 }, { id: 'main', kind: 'SLEEP', startMin: 0, durationMin: 400, days: [1, 2, 3] }], t, 3, zone, { minSleepMinutes: 451 }, 480, [], zone + '@nap-and-short', 'LDS-4 LDS-6');
    sleepCase([{ id: 'x', kind: 'WORK', startMin: 540, durationMin: 480 }], t, 2, zone, { defaultBedtimeMin: 1439, morningRoutineMinutes: '30' }, '420', [], zone + '@string-prefs', 'LDS-2 LDS-7');
  }
  const rs = random(20261109);
  const pick = a => a[Math.floor(rs() * a.length)], int = (lo, hi) => lo + Math.floor(rs() * (hi - lo + 1));
  for (let i = 0; i < 400; i++) {
    const zone = pick(ZONES), base = rs() < 0.3 ? pick(CLOCK_CHANGES.filter(c => c.zone === zone).map(c => localInstant(zone, c.date.y, c.date.mo, c.date.d - 1, 360)).concat([mon(zone)])) : mon(zone);
    const rules = [];
    if (rs() < 0.6) for (let k = int(1, 3); k > 0; k--) rules.push({ id: 'sl' + k, kind: 'SLEEP', startMin: pick([0, 60, 120, 1260, 1320, 1380, 1410, int(0, 95) * 15, int(0, 1439)]),
      durationMin: pick([60, 180, 240, 360, 420, 480, 540, int(1, 64) * 15]), days: rs() < 0.5 ? undefined : [0, 1, 2, 3, 4, 5, 6].filter(() => rs() < 0.6) });
    if (rs() < 0.3) rules.push({ id: 'w', kind: 'WORK', startMin: 540, durationMin: 480 });
    const hard = [];
    for (let k = int(0, 4); k > 0; k--) {
      const s = base + int(-12, 80) * 1800;
      hard.push({ startEpoch: s, endEpoch: s + int(1, 20) * 1800, commuteMin: pick([undefined, 0, 15, 30, 60, '20']), title: pick(['Shift', 'Class', 'Doctor', '', 'Lab']) });
    }
    const prefs = { defaultBedtimeMin: pick([undefined, 1200, 1320, 1380, 1439, 0, 30]), morningRoutineMinutes: pick([undefined, 0, 15, 30, 60]), minSleepMinutes: pick([undefined, 300, 450, 480, 600]) };
    sleepCase(rules.map(r => JSON.parse(JSON.stringify(r))), base, int(1, 4), zone, JSON.parse(JSON.stringify(prefs)), pick([480, 420, 360, 540, 300, 600, int(20, 50) * 15]), hard.map(h => JSON.parse(JSON.stringify(h))),
      'fuzz' + i + ':' + zone, 'LDS-1');
  }

  /* paint: precedence of slot codes, blocks, the load of hard slots, returns */
  const rp = random(77);
  for (let i = 0; i < 160; i++) {
    const zone = ZONES[Math.floor(rp() * ZONES.length)], t = rp() < 0.25 ? localInstant(zone, 2026, 3, 7, 600) : mon(zone), ops = [];
    for (let k = 1 + Math.floor(rp() * 7); k > 0; k--) {
      const s = t + Math.floor((rp() * 80 - 10) * 4) * 900 + (rp() < 0.2 ? Math.floor(rp() * 900) : 0), len = Math.floor(rp() * 40) * 900 + (rp() < 0.15 ? 450 : 0) - (rp() < 0.05 ? 3600 : 0);
      const code = Math.floor(rp() * 10), meta = rp() < 0.1 ? null : { kind: ['WORK', 'SLEEP', 'COMMUTE', 'MEAL'][Math.floor(rp() * 4)], title: 'b' + k, refId: 'r' + k };
      if (meta && rp() < 0.5) meta.load = Math.floor(rp() * 5);
      if (meta && rp() < 0.2) meta.restorative = true;
      ops.push([s, s + len, code, meta]);
    }
    add('paint', 'LDG-3 LDG-4', 'paint', [t, 1 + Math.floor(rp() * 2), zone, ops], i + ':' + zone);
  }
  add('paint', 'LDG-3', 'paint', [mon('UTC'), 1, 'UTC', [[mon('UTC') - 86400 * 3, mon('UTC') - 86400, 2, { kind: 'WORK' }], [mon('UTC') + 86400 * 5, mon('UTC') + 86400 * 6, 2, null],
    [mon('UTC') + 100, mon('UTC') + 100, 4, null]]], 'outside-and-empty');

  /* avail: deriveAvailability by hand, one feature at a time */
  const avail = (family, params, tag, ids) => add(family, ids, 'deriveAvailability', [params], tag);
  const P = (zone, rules, extra) => Object.assign({ timezone: zone, horizonStartEpoch: mon(zone), horizonDays: 3, playerAvailability: { timezone: zone, rules, sleepRequirementMinutes: 480 },
    playerPreferences: {}, fixedCommitments: [] }, extra || {});
  const WORK = { id: 'work', kind: 'WORK', title: 'Office', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480, commuteMin: 30 };
  const SLEEP = { id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 };
  for (const zone of ZONES) {
    avail('avail', P(zone, [WORK, SLEEP]), zone + '@office', 'LDA-10 LDA-11 LDA-12 LDA-13');
    avail('avail', P(zone, [WORK]), zone + '@default-sleep', 'LDA-10 LDA-13 LDS-2');
    avail('avail', P(zone, []), zone + '@empty', 'LDA-10 LDS-2 LDA-14 LDA-15');
    avail('avail', P(zone, [Object.assign({}, WORK, { load: 'PHYSICAL_HEAVY', durationMin: 600, commuteMin: 0 }), SLEEP]), zone + '@heavy', 'LDA-12 LDA-16');
    avail('avail', P(zone, [Object.assign({}, WORK, { load: 'LIGHT' }), SLEEP]), zone + '@light-no-decompress', 'LDA-12');
    avail('avail', P(zone, [Object.assign({}, WORK, { decompressMin: 45, durationMin: 120 }), SLEEP]), zone + '@decompress-given', 'LDA-12');
    avail('avail', P(zone, [WORK, SLEEP, { id: 'pref', kind: 'PREFERRED', startMin: 1080, durationMin: 120 }, { id: 'un', kind: 'UNAVAILABLE', days: [2], startMin: 1140, durationMin: 90 }]),
      zone + '@preferred-unavailable', 'LDA-10 LDA-17');
    avail('avail', P(zone, [WORK, SLEEP], { fixedCommitments: [{ id: 'c1', title: 'Dentist', startEpoch: at(zone, 27, 1080), endEpoch: at(zone, 27, 1140), commuteMin: 20, kind: 'APPOINTMENT' },
      { id: 'e1', commitmentType: 'EMERGENCY', startEpoch: at(zone, 28, 600), endEpoch: at(zone, 28, 720) }, { commitmentId: 'c2', commitmentType: 'CLASS', startEpoch: at(zone, 26, 1200), endEpoch: at(zone, 26, 1260) }] }),
    zone + '@commitments', 'LDA-10 LDA-11 LDA-12');
    avail('avail', P(zone, [WORK, SLEEP], { playerAvailability: { timezone: zone, rules: [WORK, SLEEP], blackouts: [{ id: 'b1', reason: 'Trip', startEpoch: at(zone, 27, 0), endEpoch: at(zone, 28, 0) },
      { blackoutId: 'b2', kind: 'SICK', startEpoch: at(zone, 28, 900), endEpoch: at(zone, 28, 1000) }] } }), zone + '@blackouts', 'LDA-10 LDA-11');
    avail('avail', P(zone, [WORK, SLEEP], { playerPreferences: { mealsEnabled: false, morningRoutineMinutes: 60, windDownMinutes: 45 } }), zone + '@no-meals', 'LDA-14');
    avail('avail', P(zone, [WORK, SLEEP], { playerPreferences: { mealOffsetsMinutes: [30, 240, 480, 720, 900], mealMinutes: 45 } }), zone + '@five-meals', 'LDA-14 LDA-15');
    avail('avail', P(zone, [Object.assign({}, WORK, { startMin: 600, durationMin: 600 }), SLEEP]), zone + '@meal-in-shift', 'LDA-15');
    avail('avail', P(zone, [WORK, SLEEP, { id: 'u', kind: 'UNAVAILABLE', startMin: 480, durationMin: 15 }], { playerPreferences: { mealMinutes: 15 } }), zone + '@meal-slides-later', 'LDA-15');
  }
  /* the inputs' other names and defaults: timezone from playerAvailability, weeklyRecurringRules, oneTimeBlackouts, horizonDays from the prefs */
  avail('avail', { horizonStartEpoch: mon('Asia/Kolkata'), playerAvailability: { timezone: 'Asia/Kolkata', weeklyRecurringRules: [{ ruleId: 'legacy', ruleType: 'HARD_COMMITMENT', dayOfWeek: 1, startMinuteOfDay: 480, durationMinutes: 240 }],
    oneTimeBlackouts: [{ blackoutId: 'x', title: 'Out', startEpoch: mon('Asia/Kolkata'), endEpoch: mon('Asia/Kolkata') + 7200 }] }, playerPreferences: { planningHorizonDays: 2 } }, 'legacy-names', 'LDA-10 LDA-11');
  avail('avail', { horizonStartEpoch: mon('UTC'), horizonDays: 2 }, 'no-zone', 'LDA-10');
  avail('avail', { timezone: 'Mars/Phobos', horizonStartEpoch: mon('UTC'), horizonDays: 2 }, 'bad-zone', 'LDA-10');
  avail('avail', { timezone: ['UTC'], horizonStartEpoch: mon('UTC'), horizonDays: 1 }, 'zone-array', 'LDA-10');
  avail('avail', { timezone: 'utc', horizonStartEpoch: mon('UTC'), horizonDays: '2' }, 'zone-case-days-text', 'LDA-10');
  avail('avail', { timezone: 'UTC', horizonStartEpoch: mon('UTC'), horizonDays: -1 }, 'negative-days', 'LDA-10');
  avail('avail', { timezone: 'UTC', horizonStartEpoch: mon('UTC'), horizonDays: 1.5 }, 'fractional-days', 'LDA-10');
  avail('avail', { timezone: 'UTC', horizonStartEpoch: mon('UTC'), horizonDays: 1, playerAvailability: { rules: 'x' } }, 'rules-text', 'LDA-10');
  avail('avail', { timezone: 'UTC', horizonStartEpoch: mon('UTC'), horizonDays: 1, playerAvailability: { rules: [null] } }, 'rule-null', 'LDA-10');
  avail('avail', P('UTC', [{ id: 'n', kind: 'WORK', rotation: { anchorDate: 5, cycleLength: 2, activeDays: [0] } }]), 'number-anchor', 'LDA-6 LDA-10');
  avail('avail', P('UTC', [WORK, SLEEP], { fixedCommitments: [{ id: 'z', title: { t: 1 }, startEpoch: at('UTC', 27, 600), endEpoch: at('UTC', 27, 700), kind: 'SLEEP', load: ['LIGHT'] },
    { id: 'y', title: 7, startEpoch: at('UTC', 27, 1400), endEpoch: at('UTC', 28, 120), kind: 'WORK', decompressMin: '15', commuteMin: '10' },
    { id: 'u', startEpoch: at('UTC', 26, 600), endEpoch: at('UTC', 26, 660), kind: 'UNAVAILABLE' }, { id: 'x', startEpoch: at('UTC', 26, 700), endEpoch: at('UTC', 26, 760), kind: 'EMERGENCY' }] }),
  'odd-commitments', 'LDA-11 LDA-12 LDS-8');
  avail('avail', P('UTC', [Object.assign({}, WORK, { decompressMin: 'abc' }), SLEEP]), 'decompress-text', 'LDA-12');
  avail('avail', P('UTC', [Object.assign({}, WORK, { startMin: 'abc' }), SLEEP]), 'rule-start-text', 'LDA-8 LDG-5');
  avail('avail', P('UTC', [WORK, Object.assign({}, SLEEP, { durationMin: {} })]), 'sleep-length-object', 'LDA-8 LDG-5');
  avail('avail', P('UTC', [WORK, SLEEP, { id: 'p', kind: 'PREFERRED', rotation: { anchorDate: 7, cycleLength: 2 } }]), 'preferred-number-anchor', 'LDA-17 LDG-5');
  avail('avail', P('UTC', [WORK, SLEEP], { fixedCommitments: [null] }), 'commitment-null', 'LDA-11 LDG-5');
  avail('avail', P('UTC', [WORK, SLEEP], { fixedCommitments: 'x' }), 'commitments-text', 'LDA-11 LDG-5');
  avail('avail', P('UTC', [WORK, SLEEP], { playerAvailability: { timezone: 'UTC', rules: [WORK, SLEEP], blackouts: 'ab' } }), 'blackouts-text', 'LDA-11');
  avail('avail', P('UTC', [WORK, SLEEP], { fixedCommitments: [{ id: 'k', kind: 'WORK', commitmentType: 'EMERGENCY', startEpoch: at('UTC', 27, 1200), endEpoch: at('UTC', 27, 1300) }] }),
    'emergency-type-wins', 'LDA-11');
  avail('avail', P('UTC', [Object.assign({}, WORK, { load: 'NONE' }), SLEEP]), 'load-none', 'LDA-11');
  avail('avail', P('UTC', [WORK, SLEEP], { playerPreferences: { mealOffsetsMinutes: ['x', 300], mealMinutes: 'abc' } }), 'meal-text', 'LDA-15');
  avail('avail', P('UTC', [WORK, SLEEP], { playerPreferences: { mealOffsetsMinutes: 'x' } }), 'meal-offsets-text', 'LDA-14');
  avail('avail', P('UTC', [WORK], { playerPreferences: { morningRoutineMinutes: '30' } }), 'routine-text', 'LDA-14 LDS-7');
  avail('avail', P('UTC', [WORK], { playerAvailability: { timezone: 'UTC', rules: [WORK], sleepRequirementMinutes: '450' } }), 'sleep-need-text', 'LDS-2');
  avail('avail', P('UTC', [WORK, { id: 's', kind: 'SLEEP', startMin: 1380, durationMin: 120 }, { id: 's2', kind: 'SLEEP', startMin: 840, durationMin: 60 }]), 'naps-only', 'LDA-13');
  avail('avail', P('UTC', [WORK, { id: 'early', kind: 'SLEEP', startMin: 1200, durationMin: 600 }, { id: 'nite', kind: 'WORK', days: [2], startMin: 1260, durationMin: 240 }]), 'overlap-warning', 'LDS-8');

  /* scenario: the built-in scenarios' availability in every zone of the matrix, the week from Monday 06:00. Only office and student
     jitter their rules by the seed (seeds 1 and 2); manygoals, deadlines, misses and workchange plan office's availability (the web test
     checks that for seeds 1 to 20), so they are recorded in two zones only. */
  const JITTERED = ['office', 'student'];
  for (const name of SCENARIO_NAMES) for (const seed of JITTERED.includes(name) ? [1, 2] : [1]) {
    const inp = engine.buildScenario(name, seed).input, av = inp.playerAvailability;
    for (const zone of SAME_AS_OFFICE.includes(name) ? ['UTC', 'America/New_York'] : ZONES)
      avail('scenario', { timezone: zone, horizonStartEpoch: mon(zone), horizonDays: 7, playerAvailability: { timezone: zone, rules: av.rules, sleepRequirementMinutes: av.sleepRequirementMinutes },
        playerPreferences: inp.playerPreferences, fixedCommitments: [] }, name + '#' + seed + '@' + zone, 'LDA-10 LDA-13 LDA-14 LDA-15');
  }

  /* dst: every 2026 and 2027 clock change of the matrix, with rule boundaries in the gap or fold, sleep across it, default bedtimes in it */
  for (const c of CLOCK_CHANGES) {
    const t = localInstant(c.zone, c.date.y, c.date.mo, c.date.d - 1, 360), tag = c.zone + '@' + key(c.date) + '-' + c.kind, mid = c.from + 30;
    const wd = [0, 1, 2, 3, 4, 5, 6];
    const sets = [
      ['start-in', [{ id: 'w', kind: 'WORK', days: wd, startMin: mid, durationMin: 300, commuteMin: 30 }, { id: 's', kind: 'SLEEP', startMin: (mid + 600) % 1440, durationMin: 420 }]],
      ['end-in', [{ id: 'w', kind: 'WORK', days: wd, startMin: (mid + 1440 - 480) % 1440, durationMin: 480 }, SLEEP]],
      ['sleep-through', [{ id: 's', kind: 'SLEEP', startMin: (c.from + 1440 - 180) % 1440, durationMin: 480 }]],
      ['sleep-ends-in', [{ id: 's', kind: 'SLEEP', startMin: (mid + 1440 - 420) % 1440, durationMin: 420 }, WORK]],
      ['quarters', [{ id: 'a', kind: 'APPOINTMENT', startMin: c.from, durationMin: 15 }, { id: 'b', kind: 'CLASS', startMin: c.to - 15, durationMin: 30 }, { id: 'p', kind: 'PREFERRED', startMin: c.from, durationMin: 60 }, SLEEP]],
      ['default-bedtime', [WORK], { defaultBedtimeMin: mid }],
      ['midnight', [{ id: 'm', kind: 'UNAVAILABLE', startMin: 0, durationMin: 1440, days: [c.date.d % 7] }, { id: 'n', kind: 'WORK', startMin: 1380, durationMin: 120 }, SLEEP]],
    ];
    for (const [what, rules, prefs] of sets) {
      avail('dst', { timezone: c.zone, horizonStartEpoch: t, horizonDays: 3, playerAvailability: { timezone: c.zone, rules, sleepRequirementMinutes: 480 }, playerPreferences: prefs || {}, fixedCommitments: [] },
        tag + '+' + what, 'LDA-8 LDA-10 LDA-13 LDS-1 LDT-8 LDT-9');
    }
    const s = localInstant(c.zone, c.date.y, c.date.mo, c.date.d, c.from) - 1800;
    avail('dst', { timezone: c.zone, horizonStartEpoch: t, horizonDays: 3, playerAvailability: { timezone: c.zone, rules: [SLEEP], sleepRequirementMinutes: 480 }, playerPreferences: {},
      fixedCommitments: [{ id: 'x', title: 'Across', startEpoch: s, endEpoch: s + 2 * 3600, commuteMin: 15, kind: 'WORK' }] }, tag + '+commitment-across', 'LDA-11 LDA-12 LDT-8 LDT-9');
  }

  /* fuzz: seeded random availability inputs */
  const rf = random(4242);
  const fp = a => a[Math.floor(rf() * a.length)], fi = (lo, hi) => lo + Math.floor(rf() * (hi - lo + 1));
  const KINDS = ['WORK', 'WORK', 'CLASS', 'APPOINTMENT', 'UNAVAILABLE', 'SLEEP', 'SLEEP', 'PREFERRED'];
  const NUMERIC_ODD = [null, '60', ' 90 ', [30], true, 1.5, 14.9];
  for (let i = 0; i < 600; i++) {
    const zone = fp(ZONES), dst = rf() < 0.3 ? fp(CLOCK_CHANGES.filter(c => c.zone === zone)) : null;
    const t = (dst ? localInstant(zone, dst.date.y, dst.date.mo, dst.date.d - 1, 0) : localInstant(zone, 2026, fi(1, 12), fi(1, 28), 0)) + fi(0, 95) * 900 + (rf() < 0.1 ? fi(1, 899) : 0);
    const rules = [];
    for (let k = fi(0, 5); k > 0; k--) {
      const kind = fp(KINDS), r = { id: fp(['a', 'b', 'c', 'd', 'e', 'work', 'sleep']) + k, kind, startMin: rf() < 0.5 ? fi(0, 95) * 15 : fi(0, 1439), durationMin: fp([15, 30, 60, 120, 240, 360, 480, 600, 720, fi(1, 96) * 15]) };
      if (dst && rf() < 0.4) r.startMin = Math.max(0, Math.min(1439, dst.from + fi(-2, 4) * 15));
      if (rf() < 0.6) r.days = [0, 1, 2, 3, 4, 5, 6].filter(() => rf() < 0.6);
      if (rf() < 0.15) r.rotation = { anchorDate: key(ymdOf(utc(2026, fi(1, 12), fi(1, 28)))), cycleLength: fi(2, 10), activeDays: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(() => rf() < 0.5) };
      if (rf() < 0.4) r.commuteMin = fp([0, 10, 15, 30, 45, 60, 90]);
      if (rf() < 0.2) r.decompressMin = fp([0, 15, 30, 60, 200]);
      if (rf() < 0.3) r.load = fp(['PHYSICAL_HEAVY', 'COGNITIVE_HEAVY', 'MIXED_MODERATE', 'LIGHT', 'NONE', 'x']);
      if (rf() < 0.15) r.validFrom = key(ymdOf(t + fi(-3, 3) * 86400));
      if (rf() < 0.15) r.validTo = key(ymdOf(t + fi(-3, 3) * 86400));
      if (rf() < 0.08 && kind !== 'SLEEP') r[fp(['startMin', 'durationMin', 'commuteMin'])] = fp(NUMERIC_ODD);
      if (rf() < 0.1) r.title = fp(['', 'Night shift', 'Ünïcode ★']);
      rules.push(r);
    }
    const commitments = [];
    for (let k = fi(0, 3); k > 0; k--) {
      const s = t + fi(-20, 300) * 900, c = { id: 'c' + k, title: fp(['Shift', 'Dentist', 'Exam']), startEpoch: s, endEpoch: s + fi(1, 48) * 900 };
      if (rf() < 0.7) c.kind = fp(['WORK', 'CLASS', 'APPOINTMENT', 'EMERGENCY', 'UNAVAILABLE']);
      else c.commitmentType = fp(['CLASS', 'EMERGENCY', 'OTHER']);
      if (rf() < 0.4) c.commuteMin = fp([0, 15, 30]);
      if (rf() < 0.2) c.shiftIntensity = fp(['PHYSICAL_HEAVY', 'LIGHT']);
      commitments.push(c);
    }
    const blackouts = [];
    for (let k = rf() < 0.2 ? fi(1, 2) : 0; k > 0; k--) { const s = t + fi(0, 250) * 900; blackouts.push({ id: 'b' + k, kind: fp(['SICK', 'BLACKOUT', undefined]), startEpoch: s, endEpoch: s + fi(4, 96) * 900 }); }
    const prefs = {};
    if (rf() < 0.3) prefs.mealsEnabled = rf() < 0.8;
    if (rf() < 0.3) prefs.morningRoutineMinutes = fp([0, 15, 30, 45, 90]);
    if (rf() < 0.3) prefs.windDownMinutes = fp([0, 15, 30, 60]);
    if (rf() < 0.2) prefs.mealMinutes = fp([15, 20, 30, 45, 60]);
    if (rf() < 0.2) prefs.mealOffsetsMinutes = [fi(0, 120), fi(200, 400), fi(500, 800)];
    if (rf() < 0.2) prefs.defaultBedtimeMin = fp([1200, 1320, 1380, 1439, 0, 60]);
    if (rf() < 0.2) prefs.minSleepMinutes = fp([300, 420, 480, 540]);
    const params = JSON.parse(JSON.stringify({ timezone: zone, horizonStartEpoch: t, horizonDays: fi(1, 2), playerAvailability: { timezone: zone, rules, blackouts, sleepRequirementMinutes: fp([480, 420, 540, 360, 300]) },
      playerPreferences: prefs, fixedCommitments: commitments }));
    avail('fuzz', params, i + ':' + zone + (dst ? '@' + dst.kind : ''), 'LDA-10 LDS-1');
  }
  return cases;
}

module.exports = { defineCases, FAMILIES, ZONES, SCENARIO_NAMES, SAME_AS_OFFICE, CLOCK_CHANGES };
