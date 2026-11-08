/* The cases tools/gen_quests_board_golden.js records (see its header for the case format). Hand-written families pin each rule of QUESTS_SPEC
   sections 3.3, 4.1, 5 and 6 and their hazards; the fuzz family draws random but seeded saves and call sequences, so the page and the native
   board are compared on inputs nobody chose. Everything here is deterministic: the same file comes out on every run. */
'use strict';
const path = require('path');
const content = require(path.join(__dirname, '..', 'tests', 'fixtures', 'quest-content.json'));
const { CLOCKS } = require('./quests_golden_cases.js');

const ATTRS = content.attributes;
const ZERO = Object.fromEntries(ATTRS.map(a => [a, 0]));
const CLASS_IDS = Object.keys(content.classQuests);

/* ---------- clocks ---------- */
/* the UTC instant at which a zone's wall clock reads `wall` (YYYY-MM-DDTHH:MM:SS); a time the zone skips lands just after the gap */
function localIso(zone, wall) {
  const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: zone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const asWall = ms => { const p = Object.fromEntries(fmt.formatToParts(new Date(ms)).map(x => [x.type, x.value])); return Date.parse(`${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}Z`) };
  const want = Date.parse(wall + 'Z');
  let t = want;
  for (let i = 0; i < 3; i++) t += want - asWall(t);
  return new Date(t).toISOString();
}
const WEEK_ZONES = ['UTC', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'America/Los_Angeles', 'Asia/Kolkata', 'Australia/Sydney', 'Europe/London', 'America/Santiago'];
/* every UTC weekday (2026-11-02 is a Monday), noon UTC, in zones that put it on the same or the next or the previous local day */
const WEEK_CLOCKS = [];
for (const z of WEEK_ZONES) for (let d = 2; d <= 8; d++) WEEK_CLOCKS.push([z, `2026-11-0${d}T12:00:00.000Z`, `UTC noon, weekday ${d - 1}`]);
const EDGE_ZONES = ['UTC', 'America/New_York', 'Australia/Sydney', 'Asia/Tokyo', 'Pacific/Kiritimati'];
/* wall times around the week, month, year, leap-day and clock-change boundaries */
const EDGE_WALLS = ['2026-12-28T00:00:30', '2027-01-03T23:59:30', '2027-01-01T00:00:30', '2026-12-31T23:59:30', '2028-02-28T00:00:30', '2028-02-29T12:00:00', '2028-03-05T23:59:30',
  '2028-03-06T00:00:30', '2026-02-01T00:00:30', '2026-03-01T12:00:00', '2026-03-02T00:00:30', '2026-08-31T00:00:30', '2026-03-08T01:59:30', '2026-03-08T03:00:30', '2026-03-09T00:00:30',
  '2026-11-01T00:30:00', '2026-11-01T01:30:00', '2026-11-02T00:00:30', '2026-04-05T02:30:00', '2026-10-25T01:30:00', '2026-09-06T00:30:00', '2026-03-29T01:30:00'];
const EDGE_CLOCKS = [];
EDGE_WALLS.forEach((w, i) => { for (const z of new Set([EDGE_ZONES[i % 5], EDGE_ZONES[(i + 1) % 5], 'UTC'])) EDGE_CLOCKS.push([z, localIso(z, w), w]); });
const SUNDAY = ['UTC', '2026-11-08T12:00:00.000Z', 'a Sunday'], MONDAY = ['UTC', '2026-11-02T12:00:00.000Z', 'a Monday'], WEDNESDAY = ['UTC', '2026-11-04T12:00:00.000Z', 'a Wednesday'];

/* ---------- seeded randomness (not the page's) ---------- */
function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s + 0x6D2B79F5) >>> 0; let t = Math.imul(s ^ (s >>> 15), s | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 };
}
const pickOf = (r, list) => list[Math.floor(r() * list.length)];
const intIn = (r, lo, hi) => lo + Math.floor(r() * (hi - lo + 1));

/* ---------- save pieces ---------- */
const cum = L => { let t = 0; for (let i = 1; i < L; i++) t += 200 + 75 * (i - 1); return t }; // the XP that reaches character level L
const xpAt = (L, off = 0) => ({ ...ZERO, physique: cum(L) + off });
const defaults = () => JSON.parse(JSON.stringify(content.defaultQuests));
const pack = (cls, all = true) => (content.classQuests[cls] || []).filter(q => all || !(q.cadence === 'daily' && q.kind !== 'nutrition' && q.kind !== 'hydration')).map(q => ({ ...q }));
const Q = (id, cadence, extra = {}) => ({ id, name: 'Quest ' + id, desc: 'About ' + id, cadence, xp: 20, kind: 'test', ...extra });
const custom = (n, cadence = 'daily', xp = 25) => ({ id: 'custom_17000000000' + n, name: 'My quest ' + n, desc: 'Custom quest created by you.', cadence, xp, kind: 'custom' });
const handQ = (n, slot) => ({ id: 'dh_{D0}_' + slot + '_' + n, hand: true, slot, tpl: 't1', name: 'Card ' + slot, desc: 'A card', cadence: 'daily', xp: 10, kind: 'hand', attribute: 'discipline', verify: 'self', tier: 1 });
const base = (extra = {}) => ({ questDone: {}, classId: null, quests: [], attrXP: { ...ZERO }, ...extra });
const done = (...pairs) => Object.fromEntries(pairs);

const C = (fn, args = [], at) => ({ fn, args, ...(at ? { at } : {}) });
const BOARD = C('renderQuests'), LEDGER = C('renderLedger'), APPLY = C('applyClassContent');

/* ---------- the families ---------- */
const CASES = [];
function add(family, id, rule, spec) {
  const full = family + '/' + id;
  if (CASES.some(c => c.id === full)) throw new Error('two cases are called ' + full);
  const [zone, now] = spec.clock || SUNDAY;
  CASES.push({ id: full, family, rule, zone, now, state: spec.state || {}, calls: spec.calls });
}
const tag = ([zone, now]) => zone.replace(/\W/g, '') + '_' + now.slice(0, 19).replace(/\D/g, '');

/* questToday: every weekday in several zones, every spelling of `day` */
function weekdays() {
  const named = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
  const A = [undefined, 'any', ...named, '', 'any ', 'ANY'];
  const B = ['Mon', 'MON', 'monday', ' mon', 'mon ', null, 0, 1, true, false, ['mon'], { day: 'mon' }, [], 'sun,mon', 'all'];
  const calls = list => list.map((v, i) => C('questToday', [v === undefined ? { id: 'w' + i } : { id: 'w' + i, day: v }]));
  WEEK_CLOCKS.forEach(clock => {
    add('weekday', 'names_' + tag(clock), 'QST-11 QST-2', { clock, calls: calls(A) });
    add('weekday', 'odd_' + tag(clock), 'QST-11', { clock, calls: calls(B) });
  });
  EDGE_CLOCKS.slice(0, 24).forEach(clock => add('weekday', 'edge_' + tag(clock), 'QST-11 QST-2', { clock, calls: calls(named.concat(['any'])) }));
}

/* questKey and isQuestDone: the three period kinds, boundaries, and how keys of earlier periods age */
function keys() {
  const quests = [Q('a', 'daily'), Q('b', 'weekly'), Q('e', 'epic'), Q('m', 'monthly'), { id: 'n' }, { id: 'u', cadence: 'Daily' }, Q('', 'daily'), { id: 7, cadence: 'daily' }, { id: ['x', 1], cadence: 'weekly' }, { id: null, cadence: 'epic' }, Q('z', 'daily', { day: 'mon' })];
  const calls = quests.flatMap(q => [C('questKey', [q]), C('isQuestDone', [q])]);
  const doneKeys = done(['a_{D0}', 'act_1'], ['a_{D-1}', 'act_0'], ['b_{W}', 'act_2'], ['b_{W-7}', 'act_8'], ['e_once', 'act_3'], ['m_once', 'act_4'], ['n_once', 'act_5'], ['_{D0}', 'act_6'], ['7_{D0}', 'act_7'],
    ['x,1_{W}', 'act_9'], ['null_once', 'act_10'], ['u_once', 'act_11'], ['z_{D0}', '']);
  EDGE_CLOCKS.forEach(clock => add('key', 'edge_' + tag(clock), 'QST-12 QST-13 TIME-2 TIME-8', { clock, state: base({ questDone: doneKeys }), calls }));
  WEEK_CLOCKS.filter((_, i) => i % 3 === 0).forEach(clock => add('key', 'week_' + tag(clock), 'QST-12 QST-13', { clock, state: base({ questDone: doneKeys }), calls: calls.slice(0, 6) }));
  /* truthiness of what a key holds, and a questDone that is not an object */
  const holds = [['s', 'act'], ['t', true], ['f', false], ['n0', 0], ['n1', 1], ['nu', null], ['em', ''], ['ar', []], ['ob', {}], ['nan', 'NaN']];
  add('key', 'truthiness', 'QST-12 QST-13', { state: base({ questDone: Object.fromEntries(holds.map(([k, v]) => [k + '_{D0}', v])) }), calls: holds.map(([k]) => C('isQuestDone', [Q(k, 'daily')])) });
  for (const [name, qd] of [['array', ['a']], ['string', 'a_x'], ['number', 5], ['true', true]])
    add('key', 'questDone_' + name, 'QST-12', { state: base({ questDone: qd }), calls: [C('isQuestDone', [Q('a', 'daily')]), C('isQuestDone', [Q('a', 'weekly')])] });
  /* the same quest checked either side of local midnight, of Sunday into Monday and of a clock change */
  const moves = [
    ['UTC', '2026-11-04T23:59:30.000Z', '2026-11-05T00:00:30.000Z'], ['UTC', '2026-11-08T23:59:30.000Z', '2026-11-09T00:00:30.000Z'], ['UTC', '2026-12-31T23:59:30.000Z', '2027-01-01T00:00:30.000Z'],
    ['UTC', '2028-02-28T23:59:30.000Z', '2028-02-29T00:00:30.000Z'], ['UTC', '2028-02-29T23:59:30.000Z', '2028-03-01T00:00:30.000Z'], ['Asia/Tokyo', '2026-11-08T14:59:30.000Z', '2026-11-08T15:00:30.000Z'],
    ['America/New_York', '2026-11-01T05:59:30.000Z', '2026-11-01T06:00:30.000Z'], ['America/New_York', '2026-03-08T04:59:30.000Z', '2026-03-08T05:00:30.000Z'],
    ['Pacific/Kiritimati', '2026-12-30T09:59:30.000Z', '2026-12-30T10:00:30.000Z'], ['America/Santiago', '2026-09-06T03:59:30.000Z', '2026-09-06T04:00:30.000Z'],
    ['Europe/London', '2026-03-29T00:59:30.000Z', '2026-03-29T01:00:30.000Z'], ['Australia/Sydney', '2026-04-04T13:59:30.000Z', '2026-04-04T14:00:30.000Z'],
  ];
  const probe = [Q('a', 'daily'), Q('b', 'weekly'), Q('e', 'epic')].flatMap(q => [C('questKey', [q]), C('isQuestDone', [q])]);
  moves.forEach(([zone, before, after], i) => add('key', 'cross' + i + '_' + zone.replace(/\W/g, ''), 'QST-12 QST-13 TIME-8', {
    clock: [zone, before], state: base({ questDone: done(['a_{D0}', 'act_1'], ['b_{W}', 'act_2'], ['e_once', 'act_3']) }),
    calls: probe.concat(probe.map(c => ({ ...c, at: after }))),
  }));
}

/* stageLvl and questLocked at every threshold, from every source of character XP */
function stages() {
  const values = [0, 1, 2, 3, 4, 5, 6, 7, -1, 1.5, 2.5, '1', '3', '03', ' 3', '5', 'length', '2.0', [3], [5, 1], [], {}, true, false, '', null, 'abc', 1e21, 12, -0];
  for (let i = 0; i < values.length; i += 6) add('stage', 'stageLvl' + i, 'QST-15', { calls: values.slice(i, i + 6).map(v => C('stageLvl', [v])) });
  add('stage', 'stageLvl_literals', 'QST-15', { calls: ['undefined', 'NaN', '-0', 'Infinity'].map(v => C('stageLvl', [{ $: v }])) });
  add('stage', 'stageOf', 'QST-15', { calls: [{ id: 'a' }, { id: 'a', stage: 3 }, { id: 'a', stage: null }, { id: 'a', stage: '4' }, { id: 'a', stage: 'length' }].map(q => C('stageOf', [q])) });
  const at = [3, 5, 8, 12];
  at.forEach((need, si) => {
    const stage = si + 2;
    for (const level of [need - 1, need, need + 1]) for (const [src, attrs] of [['attr', { attrXP: xpAt(level) }], ['split', { attrXP: { ...ZERO, physique: cum(level) - 7, social: 7 } }],
      ['bonus', { attrXP: { ...ZERO }, legacyCharXPBonus: cum(level) }], ['ascended', { attrXP: { ...ZERO }, ascended: { physique: cum(level) - 10, social: 10 } }]])
      add('stage', `lock_s${stage}_l${level}_${src}`, 'QST-15 CURVE-9', { state: base({ ...attrs }), calls: [C('questLocked', [Q('e', 'epic', { stage })]), C('questLocked', [Q('d', 'daily', { stage })]), C('questLocked', [Q('x', 'weekly')])] });
    /* one XP under and exactly on the threshold */
    add('stage', `lock_edge_${need}`, 'QST-15 CURVE-4', { state: base({ attrXP: xpAt(need, -1) }), calls: [C('questLocked', [Q('e', 'epic', { stage })])] });
    add('stage', `lock_edge_${need}_on`, 'QST-15 CURVE-4', { state: base({ attrXP: xpAt(need) }), calls: [C('questLocked', [Q('e', 'epic', { stage })])] });
  });
  const odd = [undefined, 0, 1, '2', '5', [3], 2.5, -3, true, 'abc', null, 'length', 7, 1e9];
  for (const level of [1, 3, 6, 12, 40]) add('stage', 'oddStage_l' + level, 'QST-15', { state: base({ attrXP: xpAt(level) }), calls: odd.map(s => C('questLocked', [s === undefined ? Q('o', 'epic') : Q('o', 'epic', { stage: s })])) });
  add('stage', 'oddQuest', 'QST-15', { state: base({ attrXP: xpAt(5) }), calls: [{ id: 'a' }, 5, 'str', [], { stage: 3 }].map(q => C('questLocked', [q])) });
  add('stage', 'level_curve', 'CURVE-3 QST-15', { calls: [1, 2, 3, 4, 5, 7, 8, 9, 11, 12, 13, 30].flatMap(L => [C('assign', ['attrXP', xpAt(L)]), C('questLocked', [Q('e', 'epic', { stage: 5 })])]) });
  /* with no attrXP the page cannot compute the level: it throws, but only for a quest that asks */
  add('stage', 'noAttrXP', 'QST-15', { state: { quests: [] }, calls: [C('questLocked', [Q('e', 'epic', { stage: 3 })]), C('questLocked', [Q('e', 'epic', { stage: 1 })]), C('questLocked', [Q('e', 'epic')])] });
  add('stage', 'nonNumberXP', 'QST-15', { state: base({ attrXP: { physique: '300', social: 10 } }), calls: [C('questLocked', [Q('e', 'epic', { stage: 3 })])] });
}

/* class packs: applyClassContent over classes, stored packs and starting quest lists */
function packs() {
  const lists = {
    fresh: defaults(), nodaily: defaults().filter(q => q.cadence !== 'daily'), empty: [], mixed: defaults().concat([custom(1), custom(2, 'weekly'), handQ(0, 0), handQ(1, 1)]),
    athletePack: pack('athlete'), scholarPack: pack('scholar'), leftovers: [Q('q1', 'daily'), Q('q6', 'epic'), Q('cc_old', 'weekly'), Q('cc_', 'daily'), custom(3), Q('other', 'daily')],
    idEdges: ['q0', 'q7', 'Q1', 'q1 ', 'q1\n', 'q11', 'cc', 'cc_', 'cc_a\nb', 'cc_ ', 'xcc_1', 'cc_ok', 'q6', 'q4', ' q2'].map(id => Q(id, 'daily')),
    ids: [{ id: 5 }, { name: 'no id' }, { id: ['q1'] }, { id: null }, { id: 'q2', cadence: 'daily' }, Q('q4', 'weekly', { xp: 99 })],
  };
  const classIds = [undefined, null, 'athlete', 'scholar', 'entrepreneur', 'explorer', 'disciple', 'diplomat', 'wizard', '', 'toString', '__proto__', ['athlete'], 5];
  const stored = [undefined, null, 'athlete', 'scholar', '', 'toString', 'wizard', 5];
  const names = Object.keys(lists), small = names.filter(k => !/Pack$/.test(k));
  let n = 0;
  classIds.forEach((cid, i) => stored.forEach((st, j) => {
    const list = small[(i * 3 + j * 5 + n++) % small.length];
    const state = { quests: lists[list] };
    if (cid !== undefined) state.classId = cid;
    if (st !== undefined) state.classContent = st;
    add('pack', `c${i}_s${j}_${list}`, 'QST-9 QD01', { state, calls: [APPLY, APPLY] });
  }));
  /* each class from every starting list: the migration, then again (it must not change a thing) */
  for (const cls of ['athlete', 'scholar', null, 'disciple']) for (const list of names)
    add('pack', `from_${cls}_${list}`, 'QST-9 QD01', { state: { quests: lists[list], classId: cls, ...(cls === 'athlete' && list === 'athletePack' ? { classContent: 'athlete' } : {}) }, calls: [APPLY, APPLY] });
  /* switching class (the owner picks another): the old pack goes and the new one comes in, custom quests and Hand cards stay */
  const order = ['athlete', 'scholar', 'entrepreneur', 'explorer', null, 'athlete'];
  for (let i = 0; i < order.length; i++) add('pack', 'switch_' + i, 'QST-9 QST-10 QD02', {
    state: { quests: lists.mixed, classId: order[i] },
    calls: order.slice(i + 1).concat(order.slice(0, i)).flatMap(c => [APPLY, C('assign', ['classId', c]), APPLY]),
  });
  /* the page's throws: a null entry, quests that is not a list, a class named like an Object member (the quests are already stripped by then) */
  add('pack', 'throw_nullEntry', 'QST-9', { state: { quests: [Q('q1', 'daily'), null], classId: 'athlete' }, calls: [APPLY] });
  add('pack', 'throw_string', 'QST-9', { state: { quests: 'abc', classId: 'athlete' }, calls: [APPLY] });
  add('pack', 'throw_number', 'QST-9', { state: { quests: 7, classId: 'scholar' }, calls: [APPLY] });
  add('pack', 'throw_object', 'QST-9', { state: { quests: { a: 1 }, classId: null, classContent: 'athlete' }, calls: [APPLY] });
  add('pack', 'throw_proto', 'QST-9 QD21', { state: { quests: lists.mixed, classId: 'constructor' }, calls: [APPLY, APPLY] });
  add('pack', 'falsyQuests', 'QST-9', { state: { quests: 0, classId: 'explorer' }, calls: [APPLY] });
  add('pack', 'noQuestsKey', 'QST-9', { state: { classId: 'explorer' }, calls: [APPLY] });
  add('pack', 'sameKeyNoStrip', 'QST-9 QD01', { state: { quests: [Q('q1', 'daily'), Q('cc_x', 'daily')] }, calls: [APPLY] });
  add('pack', 'nullStoredAndNoClass', 'QST-9 QD01', { state: { quests: [Q('q1', 'daily')], classContent: null }, calls: [APPLY] });
  add('pack', 'dupDefault', 'QST-9', { state: { quests: [Q('q4', 'weekly', { name: 'mine' }), Q('q5', 'daily')], classId: 'disciple', classContent: 'athlete' }, calls: [APPLY] });
}

/* custom quests: refusal, defaults and clamping */
function creates() {
  const create = (n, c = 'daily', x = '25') => C('createQuest', [n, c, x]);
  const xps = ['', '0', '25', '1', '0.5', '-5', '-0', '12.7', '12.70', '499.99', '500', '501', '1e3', '1e2', 'abc', '  7 ', '+9', '0x10', '1e-1', '3.', '.5', '99999999999', '1e400', '-1e400', '0.0000001', '7e0', '1,5', 'Infinity', 'NaN'];
  xps.forEach((x, i) => add('create', 'xp' + i, 'QST-21 QST-23 QD07', { state: { quests: [] }, calls: [create('Read', 'daily', x)] }));
  const nameList = ['Read', '', '   ', '\t\n', ' padded ', 'a'.repeat(300), 'é日本語', '😀 party', '<b>bold</b>', 'quote " and \' mix', '&amp;', ' nbsp ', '﻿bom', ' ls ', '\u001cfs\u001d', 'line\nbreak', 'tab\there', '0', 'null', '​zw'];
  nameList.forEach((nm, i) => add('create', 'name' + i, 'QST-21 QST-22', { state: { quests: [] }, calls: [create(nm)] }));
  for (const cad of ['daily', 'weekly', 'epic', 'monthly', '', 'DAILY', 'nutrition']) add('create', 'cadence_' + (cad || 'empty'), 'QST-21 QST-23', { state: base({ quests: [] }), calls: [create('Quest', cad), BOARD] });
  /* where it lands in the list, and the ids */
  add('create', 'afterDefaults', 'QST-21', { state: base({ quests: defaults() }), calls: [create('One'), create('Two', 'weekly', '40'), BOARD] });
  add('create', 'sameMillisecond', 'QST-23 QD09', { state: base({ quests: [] }), calls: [create('One'), create('Two'), BOARD] });
  add('create', 'laterMillisecond', 'QST-23 QD09', { state: base({ quests: [] }), calls: [create('One'), { ...create('Two'), at: '2026-11-08T12:00:00.001Z' }] });
  add('create', 'doneAndUndone', 'QST-23 QD08', { state: base({ quests: [] }), calls: [create('One'), C('isQuestDone', [{ id: 'custom_' + Date.parse(SUNDAY[1]), cadence: 'daily' }])] });
  for (const clock of CLOCKS.concat([MONDAY, WEDNESDAY])) add('create', 'clock_' + tag(clock), 'QST-21', { clock, state: base({ quests: [] }), calls: [create('Timed', 'weekly', '30'), BOARD] });
  /* a refusal changes and saves nothing; a save with no quests list makes the page throw */
  add('create', 'refused_keepsList', 'QST-21', { state: base({ quests: defaults() }), calls: [create(''), create(' '), create('ok')] });
  for (const [name, quests] of [['missing', undefined], ['null', null], ['string', 'abc'], ['number', 3], ['object', { push: 1 }]])
    add('create', 'noList_' + name, 'QST-21', { state: quests === undefined ? { questDone: {} } : { quests }, calls: [create('Nobody'), create('')] });
  add('create', 'afterThrow', 'QST-21', { state: { quests: 'abc' }, calls: [create('x'), C('assign', ['quests', []]), create('y')] });
}

/* ---------- the board ---------- */
const WEEKDAY_CLOCKS = [2, 3, 4, 5, 6, 7, 8].map(d => ['UTC', `2026-11-0${d}T12:00:00.000Z`, 'weekday ' + (d - 1)]);
function boards() {
  const levels = [1, 12];
  /* each class's pack on every weekday, with some quests done and the character at level 1 and at 12 */
  for (const cls of CLASS_IDS) WEEKDAY_CLOCKS.forEach((clock, i) => [levels[(i + CLASS_IDS.indexOf(cls)) % 2]].forEach((level, k) => {
    const quests = pack(cls);
    const dn = {};
    quests.forEach((q, j) => { if ((j + i + k) % 3 === 0) dn[q.id + (q.cadence === 'daily' ? '_{D0}' : q.cadence === 'weekly' ? '_{W}' : '_once')] = 'act_' + j });
    add('board', `pack_${cls}_d${i}_l${level}`, 'QST-26 QST-27 QST-28 QST-29 QST-30', { clock, state: base({ quests, questDone: dn, classId: cls, classContent: cls, attrXP: xpAt(level) }), calls: [BOARD] });
  }));
  /* the starter profile and custom quests */
  WEEKDAY_CLOCKS.forEach((clock, i) => add('board', 'starter_d' + i, 'QST-26 QD01', { clock, state: base({ quests: defaults().concat([custom(1), custom(2, 'weekly', 60)]), questDone: done(['q1_{D0}', 'a'], ['q4_{W}', 'b']) }), calls: [BOARD, LEDGER] }));
  /* the hazards of QUESTS_SPEC 6 */
  const hazards = {
    nutritionEpic: [Q('n1', 'epic', { kind: 'nutrition', stage: 1 }), Q('e1', 'epic', { stage: 2 })], unknownCadence: [Q('m', 'monthly'), Q('d', 'daily'), Q('n', 'monthly', { kind: 'nutrition' })],
    noCadence: [{ id: 'x', name: 'No cadence', xp: 5 }, Q('d', 'daily')], nutritionAll: [Q('nd', 'daily', { kind: 'nutrition' }), Q('nw', 'weekly', { kind: 'nutrition' }), Q('ne', 'epic', { kind: 'nutrition' }), Q('nm', 'monthly', { kind: 'nutrition' })],
    nutritionDay: [Q('nd1', 'daily', { kind: 'nutrition', day: 'mon' }), Q('nd2', 'daily', { kind: 'nutrition', day: 'any' }), Q('nd3', 'daily', { kind: 'nutrition', day: 'sun' })],
    hydration: [Q('h', 'daily', { kind: 'hydration' }), Q('h2', 'daily', { kind: 'hydration', day: 'wed' })], handOnly: [handQ(0, 0), handQ(0, 1), handQ(0, 2)], handAndDaily: [handQ(0, 0), Q('d', 'daily'), handQ(0, 1)],
    handWeekly: [Q('hw', 'weekly', { hand: true }), Q('he', 'epic', { hand: true })], dayOnWeekly: [Q('w', 'weekly', { day: 'mon' }), Q('d', 'daily', { day: 'mon' }), Q('e', 'epic', { day: 'mon' })],
    emptyBoard: [], onlyWeekly: [Q('w1', 'weekly'), Q('w2', 'weekly')], onlyEpics: [Q('e1', 'epic', { stage: 3 })], dailyAllOtherDay: [Q('d1', 'daily', { day: 'tue' }), Q('d2', 'daily', { day: 'thu' })],
    epicOrder: [Q('e5', 'epic', { stage: 5 }), Q('e1', 'epic', { stage: 1 }), Q('e0', 'epic'), Q('e3', 'epic', { stage: 3 }), Q('e3b', 'epic', { stage: 3 }), Q('e2', 'epic', { stage: 2 }), Q('e1b', 'epic', { stage: 1 }), Q('e0b', 'epic')],
    epicOrderOdd: [Q('a', 'epic', { stage: '4' }), Q('b', 'epic', { stage: 2.5 }), Q('c', 'epic', { stage: -1 }), Q('d', 'epic', { stage: null }), Q('e', 'epic', { stage: true }), Q('f', 'epic', { stage: [3] })],
    epicDesc: [Q('a', 'epic', { stage: 1, desc: '' }), Q('b', 'epic', { stage: 1, desc: undefined }), Q('c', 'epic', { stage: 1, desc: 'Has one' }), Q('d', 'epic', { stage: 4, desc: 'Locked one' })],
    descs: [Q('a', 'daily', { desc: '' }), Q('b', 'daily', { desc: 0 }), Q('c', 'daily', { desc: 12 }), Q('d', 'daily', { desc: null }), Q('e', 'daily', { desc: ['x', 'y'] }), Q('f', 'weekly', { desc: '<i>x</i> & "q"' })],
    names: [Q('a', 'daily', { name: '' }), Q('b', 'daily', { name: null }), Q('c', 'daily', { name: 7 }), Q('d', 'daily', { name: '<b>&"\'' }), Q('e', 'weekly', { name: '日本語 😀' }), { id: 'f', cadence: 'daily' }],
    ids: [Q('a b', 'daily'), Q('a&b', 'daily'), Q('a-b', 'weekly'), Q('日本', 'daily'), Q('a\\b', 'daily'), { cadence: 'daily', name: 'no id' }, Q('7', 'weekly'), { id: 8, cadence: 'daily', name: 'num id' }],
    xpKinds: [Q('a', 'daily', { xp: -5 }), Q('b', 'daily', { xp: '30' }), Q('c', 'daily', { xp: 'abc' }), Q('d', 'daily', { xp: null }), Q('e', 'daily', { xp: 12.7 }), Q('f', 'daily', { xp: true }), Q('g', 'weekly', { xp: 1e21 }), Q('h', 'weekly', { xp: ' 4 ' }), { id: 'i', cadence: 'daily', name: 'no xp' }, Q('j', 'weekly', { xp: [9] }), Q('k', 'daily', { xp: 0.1 }), Q('l', 'daily', { xp: 0.2 })],
    xpText: [Q('a', 'daily', { xp: '0b12' }), Q('b', 'weekly', { xp: '\u001c7' }), Q('c', 'weekly', { xp: '\u00a08' }), Q('d', 'daily', { xp: '\ufeff9' }), Q('e', 'daily', { xp: '0o9' }), Q('f', 'daily', { xp: '0b101' }), Q('g', 'weekly', { xp: '1_000' }), Q('h', 'weekly', { xp: '0x1F' }), Q('i', 'daily', { xp: '\u2028 3 \u2029' }), Q('j', 'daily', { xp: '1e2' }), Q('k', 'daily', { xp: '-Infinity' }), Q('l', 'daily', { xp: '.5e1' }), Q('m', 'weekly', { xp: '0x' }), Q('n', 'weekly', { xp: '+0x10' })],
    bigSum: [Q('a', 'daily', { xp: 999999 }), Q('b', 'daily', { xp: 123456.789 }), Q('c', 'weekly', { xp: 1000 }), Q('d', 'weekly', { xp: 0.0004 }), Q('e', 'weekly', { xp: 0.0005 })],
    infinite: [Q('a', 'daily', { xp: 1e308 }), Q('b', 'daily', { xp: 1e308 }), Q('c', 'weekly', { xp: 5 })],
    sums: [Q('a', 'daily', { xp: 0.1 }), Q('b', 'daily', { xp: 0.2 }), Q('c', 'weekly', { xp: 0.3 }), Q('d', 'daily', { xp: 1234.5675 }), Q('e', 'daily', { xp: 2.0005 })],
  };
  const dn = { nutritionEpic: done(['n1_once', 'x']), handAndDaily: done(['d_{D0}', 'x']), epicOrder: done(['e3_once', 'a'], ['e1_once', 'a']), hydration: done(['h_{D0}', 'a']), sums: done(['d_{D0}', 'a']), bigSum: done(['c_{W}', 'a']) };
  for (const [name, quests] of Object.entries(hazards)) for (const [lv, clock] of [[1, SUNDAY], [12, MONDAY], [4, WEDNESDAY]])
    add('board', `${name}_l${lv}_${tag(clock).slice(-8)}`, 'QST-26 QST-28 QST-29 QD11 QD12', { clock, state: base({ quests, questDone: dn[name] || {}, attrXP: xpAt(lv) }), calls: [BOARD] });
  /* toLocaleString rounds the available XP to three decimals, halves away from zero */
  [0.0005, 0.0015, 0.0025, 2.0025, 1.0005, 1.2345, 7.0004999, 1e-7, 1234567.8915, 0.1 + 0.2].forEach((xp, i) => add('board', 'localeHalf' + i, 'QST-29 QST-30 QD12', { state: base({ quests: [Q('a', 'daily', { xp })] }), calls: [BOARD] }));
  /* percentages: every count of done dailies out of every size */
  for (const total of [1, 2, 3, 4, 6, 7, 8, 9, 12]) for (const dd of [0, 1, Math.floor(total / 2), total - 1, total].filter((v, i, a) => a.indexOf(v) === i && v >= 0 && v <= total)) {
    const quests = Array.from({ length: total }, (_, i) => Q('p' + i, 'daily'));
    add('board', `pct_${dd}_of_${total}`, 'QST-29 QST-30', { state: base({ quests, questDone: Object.fromEntries(quests.slice(0, dd).map(q => [q.id + '_{D0}', 'a'])) }), calls: [BOARD] });
  }
  /* ticking and unticking through the stored keys moves rows between done and undone; periods that rolled over do not count */
  const week = [Q('d', 'daily'), Q('w', 'weekly'), Q('e', 'epic', { stage: 1 })];
  add('board', 'tickedYesterday', 'QST-12 QST-13 QD03', { clock: WEDNESDAY, state: base({ quests: week, questDone: done(['d_{D-1}', 'a'], ['w_{W-7}', 'b'], ['e_once', 'c']) }), calls: [BOARD, { ...BOARD, at: '2026-11-09T12:00:00.000Z' }] });
  add('board', 'tickedThisPeriod', 'QST-12 QST-13', { clock: WEDNESDAY, state: base({ quests: week, questDone: done(['d_{D0}', 'a'], ['w_{W}', 'b'], ['e_once', 'c']) }), calls: [BOARD, { ...BOARD, at: '2026-11-09T12:00:00.000Z' }, { ...BOARD, at: '2026-11-16T12:00:00.000Z' }] });
  EDGE_CLOCKS.slice(0, 30).forEach(clock => add('board', 'edge_' + tag(clock), 'QST-12 QST-26 TIME-8', { clock, state: base({ quests: week.concat([Q('m', 'daily', { day: 'mon' }), Q('s', 'daily', { day: 'sun' })]), questDone: done(['d_{D0}', 'a'], ['w_{W}', 'b'], ['s_{D0}', 'c'], ['m_{D0}', 'd']) }), calls: [BOARD] }));
  add('board', 'midnightAndSunday', 'QST-11 QST-12', { clock: ['UTC', '2026-11-08T23:59:30.000Z'], state: base({ quests: pack('scholar'), questDone: done(['cc_scholar_01_{D0}', 'a']) }), calls: [BOARD, { ...BOARD, at: '2026-11-09T00:00:30.000Z' }] });
}

/* the ledger's quest sections */
function ledgers() {
  for (const cls of CLASS_IDS) WEEKDAY_CLOCKS.filter((_, i) => i % 3 === 0).forEach((clock, i) => add('ledger', `pack_${cls}_d${i}`, 'QST-59 QST-60', { clock, state: base({ quests: pack(cls).concat([custom(1), custom(2, 'weekly')]), questDone: done(['custom_170000000001_{D0}', 'a']), attrXP: xpAt(i < 4 ? 1 : 12), classId: cls }), calls: [LEDGER] }));
  const specials = {
    empty: [], nutritionOnly: [Q('n', 'daily', { kind: 'nutrition' }), Q('nw', 'weekly', { kind: 'nutrition' }), Q('ne', 'epic', { kind: 'nutrition', stage: 2 })], hand: [handQ(0, 0), Q('d', 'daily')],
    epics: [Q('e5', 'epic', { stage: 5 }), Q('e2', 'epic', { stage: 2 }), Q('e1', 'epic'), Q('e3', 'epic', { stage: 3, desc: '' }), Q('e4', 'epic', { stage: 4, desc: 'Words' })],
    unknown: [Q('m', 'monthly'), { id: 'u' }], names: [Q('a', 'daily', { name: '<b>x</b>' }), Q('b', 'weekly', { name: 'it\'s "q" & more', desc: 'a & b' }), Q('c a', 'daily')],
    xps: [Q('a', 'daily', { xp: 12.7 }), Q('b', 'weekly', { xp: -1 }), Q('c', 'weekly', { xp: 'abc' }), Q('d', 'daily', { xp: null }), Q('e', 'weekly', { xp: 1e21 })],
    dailyDays: [Q('a', 'daily', { day: 'mon' }), Q('b', 'daily', { day: 'any' }), Q('c', 'daily', { day: 'tue' }), Q('d', 'daily', { day: 'sun' })],
  };
  for (const [name, quests] of Object.entries(specials)) for (const [lv, clock] of [[1, SUNDAY], [12, MONDAY]])
    add('ledger', `${name}_l${lv}_${tag(clock).slice(-8)}`, 'QST-59 QST-60 QD11', { clock, state: base({ quests, questDone: done(['a_{D0}', 'x'], ['e5_once', 'y'], ['e1_once', 'y'], ['n_{D0}', 'z']), attrXP: xpAt(lv) }), calls: [LEDGER] });
  add('ledger', 'afterCreate', 'QST-59 QST-22', { state: base({ quests: [] }), calls: [C('createQuest', ['Mine', 'weekly', '20']), C('createQuest', ['Daily mine', 'daily', '20']), LEDGER] });
}

/* a save that is not the shape the page writes: the board throws, or shows what it can */
function odds() {
  const lists = { nullEntry: [Q('a', 'daily'), null], numEntry: [Q('a', 'daily'), 5], strEntry: [Q('a', 'daily'), 'x', []], onlyNull: [null], nestedList: [[Q('a', 'daily')]] };
  for (const [name, quests] of Object.entries(lists)) add('odd', 'quests_' + name, 'QST-26', { state: base({ quests }), calls: [BOARD, LEDGER] });
  for (const [name, quests] of [['missing', undefined], ['null', null], ['string', 'abc'], ['number', 3], ['object', { a: 1 }], ['boolean', true]])
    add('odd', 'noList_' + name, 'QST-26', { state: quests === undefined ? { questDone: {}, attrXP: { ...ZERO } } : { quests, questDone: {}, attrXP: { ...ZERO } }, calls: [BOARD, LEDGER] });
  const withList = { quests: [Q('a', 'daily'), Q('e', 'epic', { stage: 3 })], attrXP: { ...ZERO } };
  add('odd', 'noQuestDone', 'QST-12 QST-26', { state: { ...withList }, calls: [BOARD, LEDGER] });
  add('odd', 'nullQuestDone', 'QST-12 QST-26', { state: { ...withList, questDone: null }, calls: [BOARD, LEDGER] });
  add('odd', 'noQuestDoneEmptyLists', 'QST-26', { state: { quests: [Q('m', 'monthly')], attrXP: { ...ZERO } }, calls: [BOARD, LEDGER] });
  add('odd', 'noAttrXPNoEpics', 'QST-26', { state: { quests: [Q('a', 'daily')], questDone: {} }, calls: [BOARD, LEDGER] });
  add('odd', 'noAttrXPLockedEpic', 'QST-15 QST-28', { state: { quests: [Q('e', 'epic', { stage: 3 })], questDone: {} }, calls: [BOARD, LEDGER] });
  add('odd', 'noAttrXPStageOneEpic', 'QST-15 QST-28', { state: { quests: [Q('e', 'epic', { stage: 1 })], questDone: {} }, calls: [BOARD, LEDGER] });
  add('odd', 'questDoneArray', 'QST-12', { state: { ...withList, questDone: ['a_x'] }, calls: [BOARD] });
  add('odd', 'questDoneString', 'QST-12', { state: { ...withList, questDone: 'a' }, calls: [BOARD] });
  add('odd', 'attrXPNumbers', 'QST-15', { state: { ...withList, questDone: {}, attrXP: { physique: 900 } }, calls: [BOARD] });
  add('odd', 'attrXPNull', 'QST-15', { state: { ...withList, questDone: {}, attrXP: null }, calls: [BOARD] });
  add('odd', 'ascendedAndBonus', 'QST-15 CURVE-3', { state: { ...withList, questDone: {}, attrXP: { ...ZERO }, legacyCharXPBonus: 900, ascended: { physique: 400 } }, calls: [BOARD, LEDGER] });
  add('odd', 'dailyKeepsDuplicates', 'QST-26', { state: base({ quests: [Q('a', 'daily'), Q('a', 'daily'), Q('a', 'weekly')], questDone: done(['a_{D0}', 'x']) }), calls: [BOARD] });
  add('odd', 'fractionalAndStringStage', 'QST-15 QST-28', { state: base({ quests: [Q('a', 'epic', { stage: '3' }), Q('b', 'epic', { stage: 3.5 }), Q('c', 'epic', { stage: 'x' })], attrXP: xpAt(4) }), calls: [BOARD, LEDGER] });
}

/* seeded random saves and call sequences */
function fuzz() {
  const r = rng(2026);
  const clocks = CLOCKS.concat(WEEK_CLOCKS.filter((_, i) => i % 5 === 0), EDGE_CLOCKS.filter((_, i) => i % 4 === 0));
  const cadences = ['daily', 'daily', 'daily', 'weekly', 'weekly', 'epic', 'epic', 'monthly', undefined, 'Daily'];
  const kinds = [undefined, undefined, 'nutrition', 'hydration', 'custom', 'hand', 'fitness'];
  const xps = [5, 10, 15, 20, 25, 30, 40, 75, 100, 12.5, 0, -3, '20', 'x', null, 0.1, 1234.5];
  const days = [undefined, undefined, undefined, 'any', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
  const stages = [undefined, undefined, 1, 2, 3, 4, 5];
  for (let i = 0; i < 220; i++) {
    const n = intIn(r, 0, 10), quests = [];
    for (let k = 0; k < n; k++) {
      const q = { id: r() < 0.08 && quests.length ? quests[0].id : 'f' + i + '_' + k, name: 'Fuzz ' + k };
      const cad = pickOf(r, cadences); if (cad !== undefined) q.cadence = cad;
      if (r() < 0.8) q.desc = pickOf(r, ['', 'Some words', 'A & B <c>']);
      const kind = pickOf(r, kinds); if (kind !== undefined) q.kind = kind;
      if (kind === 'hand') q.hand = true;
      const xp = pickOf(r, xps); if (xp !== undefined) q.xp = xp;
      const day = pickOf(r, days); if (day !== undefined) q.day = day;
      const stage = pickOf(r, stages); if (stage !== undefined) q.stage = stage;
      quests.push(q);
    }
    const questDone = {};
    for (const q of quests) if (r() < 0.35) questDone[q.id + (q.cadence === 'daily' ? pickOf(r, ['_{D0}', '_{D0}', '_{D-1}']) : q.cadence === 'weekly' ? pickOf(r, ['_{W}', '_{W}', '_{W-7}']) : '_once')] = 'act_' + i;
    const state = { quests, questDone, attrXP: xpAt(intIn(r, 1, 14), intIn(r, 0, 3) === 0 ? intIn(r, -5, 5) : 0) };
    if (r() < 0.5) state.classId = pickOf(r, CLASS_IDS.concat([null, 'disciple']));
    if (r() < 0.3) state.classContent = pickOf(r, CLASS_IDS.concat([null]));
    const calls = [], steps = intIn(r, 2, 6);
    for (let s = 0; s < steps; s++) {
      const pick = r(), q = quests.length ? pickOf(r, quests) : Q('none', 'daily');
      const at = r() < 0.3 ? new Date(Date.parse(pickOf(r, clocks)[1]) + intIn(r, 0, 9) * 86400000 + intIn(r, 0, 86399) * 1000).toISOString() : undefined;
      if (pick < 0.3) calls.push(C('renderQuests', [], at));
      else if (pick < 0.45) calls.push(C('renderLedger', [], at));
      else if (pick < 0.55) calls.push(C('applyClassContent', [], at));
      else if (pick < 0.7) calls.push(C('createQuest', [pickOf(r, ['New', ' ', 'Another one', 'x'.repeat(intIn(r, 1, 80))]), pickOf(r, ['daily', 'weekly', 'epic']), pickOf(r, ['', '30', '0', '999', '12.5', '-4', 'e'])], at));
      else if (pick < 0.8) calls.push(C('assign', ['classId', pickOf(r, CLASS_IDS.concat([null]))]));
      else if (pick < 0.88) calls.push(C('questToday', [q], at));
      else if (pick < 0.94) calls.push(C('isQuestDone', [q], at), C('questKey', [q]));
      else calls.push(C('questLocked', [q]));
    }
    add('fuzz', 'f' + i, 'QST-9 QST-11 QST-12 QST-15 QST-21 QST-26', { clock: pickOf(r, clocks), state, calls });
  }
}

function defineCases() {
  if (CASES.length) return CASES.map(c => ({ ...c }));
  weekdays(); keys(); stages(); packs(); creates(); boards(); ledgers(); odds(); fuzz();
  return CASES.map(c => ({ ...c }));
}

module.exports = { defineCases };
