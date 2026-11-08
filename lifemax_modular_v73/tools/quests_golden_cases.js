/* The cases tools/gen_quests_golden.js records (see its header for the case format). Hand-written families pin each rule of
   QUESTS_SPEC sections 7 to 9 and their hazards; the fuzz family draws random but seeded saves and call sequences, so the page and the
   native draw are compared on inputs nobody chose. Everything here is deterministic: the same file comes out on every run. */
'use strict';
const path = require('path');
const content = require(path.join(__dirname, '..', 'tests', 'fixtures', 'quest-content.json'));

const ATTRS = content.attributes, TPL = content.hand.templates;
const CLASSES = [null, 'athlete', 'scholar', 'entrepreneur', 'explorer', 'disciple', 'diplomat'];
/* frozen clocks: [zone, now, what is special] */
const CLOCKS = [
  ['UTC', '2026-11-08T12:00:00.000Z', 'a Sunday'],
  ['America/New_York', '2026-11-01T05:30:00.000Z', 'the day daylight saving ends, 01:30 before the repeat'],
  ['America/New_York', '2026-03-08T07:30:00.000Z', 'the day daylight saving starts, 03:30'],
  ['Europe/London', '2026-03-29T00:30:00.000Z', 'just before the clocks go forward'],
  ['Australia/Sydney', '2026-04-04T14:30:00.000Z', '01:30 on the day daylight saving ends'],
  ['America/Santiago', '2026-09-06T04:30:00.000Z', 'a day whose local midnight does not exist'],
  ['Pacific/Kiritimati', '2026-12-31T10:30:00.000Z', 'already 2027-01-01 at UTC+14'],
  ['Pacific/Pago_Pago', '2027-01-01T10:30:00.000Z', 'still 2026-12-31 at UTC-11'],
  ['Asia/Kolkata', '2028-02-29T18:00:00.000Z', '23:30 on a leap day'],
  ['UTC', '2026-12-31T23:59:30.000Z', 'thirty seconds before the new year'],
  ['America/Los_Angeles', '2026-02-01T07:30:00.000Z', '23:30 on the last day of January'],
  ['Asia/Tokyo', '2026-06-30T15:30:00.000Z', '00:30 on the first of July'],
];
const plusMs = (iso, ms) => new Date(Date.parse(iso) + ms).toISOString();
const H = 3600000;

/* ---------- seeded randomness for the generated cases (not the page's) ---------- */
function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s + 0x6D2B79F5) >>> 0; let t = Math.imul(s ^ (s >>> 15), s | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296 };
}
const pickOf = (r, list) => list[Math.floor(r() * list.length)];
const intIn = (r, lo, hi) => lo + Math.floor(r() * (hi - lo + 1));

/* ---------- save pieces ---------- */
const tpl = id => TPL.find(t => t.id === id);
/* a card's quest exactly as dhMakeQuest builds it, for a save written by an earlier day */
const handQuest = (id, day, slot, n) => { const t = tpl(id); return { id: 'dh_' + day + '_' + slot + '_' + n, hand: true, slot, tpl: t.id, name: t.name, desc: t.desc, cadence: 'daily', xp: t.xp, kind: 'hand', attribute: t.attr, verify: t.verify, tier: t.tier } };
const card = (id, day, slot, n) => ({ tpl: id, id: 'dh_' + day + '_' + slot + '_' + n, n });
/* a dealt day: its draw and the quests of its cards */
function dealt(day, tpls, extra = {}) {
  return { dailyDraw: { date: day, nonce: 0, rerollsUsed: 0, cards: tpls.map((t, i) => card(t, day, i, 0)), ...extra }, quests: tpls.map((t, i) => handQuest(t, day, i, 0)) };
}
const luck = score => ({ date: '{D0}', dice: [3, 4, 5, 6], score, ver: 2 });
const staleLuck = score => ({ date: '{D-1}', dice: [3, 4, 5, 6], score, ver: 2 });
let actSeq = 0;
const act = (type, primary, xp, date, extra = {}) => ({ id: 'act_' + (++actSeq), type, name: type, primary, xp, duration: 30, date, ...extra });
const defaults = () => JSON.parse(JSON.stringify(content.defaultQuests));
const classPack = (cls, all) => (content.classQuests[cls] || []).filter(q => all || !(q.cadence === 'daily' && q.kind !== 'nutrition' && q.kind !== 'hydration')).map(q => ({ ...q }));
const custom = (n, cadence = 'daily') => ({ id: 'custom_17000000000' + n, name: 'My quest ' + n, desc: 'Custom quest created by you.', cadence, xp: 25, kind: 'custom' });
const base = (extra = {}) => ({ activities: [], questDone: {}, classId: null, dhMig: 1, quests: [], sparks: 0, ...extra });

const C = (name, args = [], at) => ({ fn: name, args, ...(at ? { at } : {}) });
const ENSURE = C('dhEnsure'), CARDS = C('dhCards'), COST = C('dhRerollCost'), WEAK = C('dhWeakestAttr');
const reroll = slot => C('dhReroll', [slot]);

/* ---------- the families ---------- */
const CASES = [];
function add(family, id, rule, spec) {
  const full = family + '/' + id;
  if (CASES.some(c => c.id === full)) throw new Error('two cases are called ' + full);
  const [zone, now] = spec.clock || CLOCKS[0];
  CASES.push({ id: full, family, rule, zone, now, state: spec.state || {}, calls: spec.calls });
}

function seeds() {
  const texts = ['', 'a', 'abc', 'Daily Hand', '2026-11-08|5500|0|0', '2026-11-08|1000|0|1', '2026-11-08|9999|3|2', '2027-01-01|7342|12|0',
    '2026-11-08|5500.5|1|2', '2026-11-08|-200|0|0', '2026-11-08|5500|NaN|1', '2026-11-08|5500|undefined|0', '|||', 'é', 'é', '日本語',
    '🃏', '\ud800', '\udfff', 'x\u0000y', '\uffff', 'z'.repeat(997), '0123456789'.repeat(31), 'Ω≈ç√∫', '\t\n\r', 'NaN', 'null'];
  texts.forEach((t, i) => add('seed', 's' + i, 'QST-32', { calls: [C('dhSeed', [t])] }));
}

function generators() {
  const s = [0, 1, 2, 42, 7, 2166136261, 4294967295, 4294967296, -1, -4294967297, 2147483647, 2147483648, 3.7, -3.7, 1e10, 1e21, 0x6D2B79F5,
    0x92D4860B, 123456789, 987654321, 16777619, 0.5];
  s.forEach((x, i) => add('rng', 'r' + i, 'QST-33', { calls: [C('dhRng', [x, 8])] }));
  const r = rng(99);
  for (let i = 0; i < 10; i++) add('rng', 'random' + i, 'QST-33', { calls: [C('dhRng', [Math.floor(r() * 4294967296), 5])] });
}

function makeQuests() {
  [['t1', '2026-11-08', 0, 0], ['t53', '2026-11-08', 2, 7], ['t20', '2027-01-01', 1, '3'], ['t31', '2026-02-28', 2, null], ['t9', '2026-11-08', 0, 1.5],
    ['t44', 'x', 1, 1e21], ['t12', '2026-11-08', 2, -0], ['t5', '', 0, 'n']].forEach((a, i) => add('quest', 'q' + i, 'QST-40', { calls: [C('dhMakeQuest', a)] }));
}

/* recent attribute XP and the weakest attribute: windows, exclusions, the tie order and the number hazards */
function recent() {
  const window = [C('dhRecentAttrXP', [7]), C('dhRecentAttrXP', [1]), C('dhRecentAttrXP', [2]), C('dhRecentAttrXP', [30]), C('dhRecentAttrXP', [0]), WEAK];
  const histories = {
    empty: { rule: 'QST-35 QST-36', acts: [] },
    boundary: { rule: 'QST-35', acts: [act('strength', 'physique', 40, '{I-6 00:00:01}'), act('cardio', 'endurance', 30, '{I-7 23:59:59}'), act('study', 'intelligence', 20, '{I0 00:00:00}')] },
    questExcluded: { rule: 'QST-35', acts: [act('quest', 'physique', 500, '{I0 08:00:00}'), act('strength', 'endurance', 5, '{I-1 08:00:00}')] },
    passive: { rule: 'QST-35', acts: [act('steps', 'endurance', 12, '{I0 07:00:00}', { passive: true }), act('sleep', 'wellbeing', 8, '{I-2 07:00:00}', { passive: true })] },
    emptySplit: { rule: 'QST-35 QD14', acts: [act('strength', 'physique', 50, '{I0 09:00:00}', { xpSplit: [] }), act('study', 'intelligence', 1, '{I-3 09:00:00}')] },
    future: { rule: 'QST-35 QD13', acts: [act('strength', 'physique', 70, '{I2 10:00:00}'), act('meditation', 'wellbeing', 3, '{I0 10:00:00}')] },
    ties: { rule: 'QST-36', acts: ATTRS.slice(0, 7).map((a, i) => act('x', a, 10, '{I-' + i + ' 12:00:00}')) },
    tiesLast: { rule: 'QST-36', acts: ATTRS.slice(1).map((a, i) => act('x', a, 10, '{I-' + (i % 6) + ' 12:00:00}')) },
    numbers: { rule: 'QST-1 QST-35', acts: [act('a', 'physique', '15', '{I0 01:00:00}'), act('b', 'endurance', -5, '{I0 01:00:00}'), act('c', 'social', 'abc', '{I0 01:00:00}'),
      act('d', 'business', null, '{I0 01:00:00}'), act('e', 'discipline', 0.1, '{I0 01:00:00}'), act('f', 'discipline', 0.2, '{I0 02:00:00}'), act('g', 'adventure', 1e308, '{I0 01:00:00}'),
      act('h', 'adventure', 1e308, '{I-1 01:00:00}'), act('i', 'wellbeing', ' 7 ', '{I0 01:00:00}'), act('j', 'intelligence', '0x10', '{I0 01:00:00}'), act('k', 'intelligence', true, '{I0 01:00:00}'),
      act('l', 'physique', [4], '{I0 01:00:00}'), act('m', 'physique', '1e400', '{I-1 01:00:00}')] },
    splits: { rule: 'QST-35', acts: [act('strength', 'physique', 99, '{I0 05:00:00}', { xpSplit: [{ attr: 'physique', xp: 6 }, null, { attr: 'nope', xp: 50 }, { attr: ['social'], xp: 4 }, { xp: 9 }, 3, { attr: 'endurance', xp: '2' }] }),
      act('study', 'intelligence', 9, '{I-1 05:00:00}', { xpSplit: [{ attr: 'intelligence', xp: 2.5 }, { attr: 'intelligence', xp: 2.5 }] }), act('x', 'business', 9, '{I0 05:00:00}', { xpSplit: 0 })] },
    dates: { rule: 'QST-35 TIME-4 QD20', acts: [act('a', 'physique', 5, 'not a date'), act('b', 'endurance', 6, null), act('c', 'social', 7, '{D-6}'), act('d', 'business', 8, '{D-6}T00:30:00'),
      act('e', 'wellbeing', 9, '{D-7}T23:30:00'), act('f', 'discipline', 4, '{D0}'), { id: 'act_nodate', type: 'g', primary: 'adventure', xp: 3 }] },
  };
  const clocks = [CLOCKS[0], CLOCKS[1], CLOCKS[2], CLOCKS[5], CLOCKS[6], CLOCKS[7], CLOCKS[8]];
  for (const [name, h] of Object.entries(histories))
    clocks.forEach((clock, i) => add('recent', name + '_' + i, h.rule, { clock, state: base({ activities: h.acts }), calls: window }));
}

/* dhDraw for every slot and nonces 0 to 3, each class and none, the luck values, rest and active days */
function draws() {
  const lucks = [['fallback', undefined], ['l1000', 1000], ['l9999', 9999], ['l5500', 5500], ['frac', 7321.5], ['zero', 0], ['low', -9000], ['high', 40000], ['stale', 'stale']];
  const r = rng(7);
  let k = 0;
  for (const cls of CLASSES.concat(['wizard'])) for (const [ln, lv] of lucks) for (const rest of [false, true]) {
    if (k++ % 2 && (ln === 'zero' || ln === 'low' || ln === 'high' || ln === 'stale')) continue;
    const clock = CLOCKS[k % CLOCKS.length];
    const acts = Array.from({ length: intIn(r, 0, 6) }, () => act('x', pickOf(r, ATTRS), intIn(r, 1, 40), '{I-' + intIn(r, 0, 6) + ' 1' + intIn(r, 0, 9) + ':00:00}'));
    const state = base({ classId: cls, activities: acts, ...(lv === undefined ? {} : { luck: lv === 'stale' ? staleLuck(8000) : luck(lv) }), ...(rest ? { restDays: { '{D0}': true } } : {}) });
    const calls = [];
    for (let slot = 0; slot < 3; slot++) for (let nonce = 0; nonce < 4; nonce++) calls.push(C('dhDraw', ['{D0}', nonce, slot, []]));
    calls.push(C('dhDraw', ['{D0}', 0, 1, (content.hand.classAttrs[cls] ? TPL.filter(t => content.hand.classAttrs[cls].includes(t.attr)).map(t => t.id) : ['t1', 't2'])]));
    calls.push(C('dhDraw', ['{D-1}', 2, 2, ['t5', 't9', 't40']]));
    add('draw', [cls || 'none', ln, rest ? 'rest' : 'active'].join('_'), 'QST-38 QST-39' + (rest ? ' REST-DAY-1' : '') + (lv === undefined || lv === 0 || lv === 'stale' ? ' QST-34' : ''), { clock, state, calls });
  }
  /* the pool runs dry: every template of the weakest attribute taken, every template taken, rest days without tier 1 and 2 left */
  const all = TPL.map(t => t.id);
  add('draw', 'pool_weakestTaken', 'QST-38', { state: base(), calls: [C('dhDraw', ['{D0}', 0, 0, TPL.filter(t => t.attr === 'physique').map(t => t.id)])] });
  add('draw', 'pool_allTaken', 'QST-38', { state: base(), calls: [0, 1, 2].map(s => C('dhDraw', ['{D0}', 0, s, all])) });
  add('draw', 'pool_restOnlyTier3', 'QST-38 REST-DAY-1', { state: base({ restDays: { '{D0}': true } }), calls: [C('dhDraw', ['{D0}', 1, 0, TPL.filter(t => t.attr === 'physique' && t.tier < 3).map(t => t.id)])] });
  add('draw', 'pool_takenOdd', 'QST-38', { state: base({ classId: 'scholar' }), calls: [C('dhDraw', ['{D0}', 0, 1, [null, 5, 't30', ['t31']]]), C('dhDraw', ['{D0}', 'x', 2, []]), C('dhDraw', ['{D0}', null, 0, []])] });
  /* the rest day of another date than today: a recovery session, a plan-free rest day, a passive recovery that does not count */
  add('draw', 'rest_recovery', 'QST-38 REST-DAY-1', { state: base({ activities: [act('recovery', 'wellbeing', 10, '{I0 06:00:00}', { duration: 25 })] }), calls: [0, 1, 2].map(s => C('dhDraw', ['{D0}', 0, s, []])) });
  add('draw', 'rest_shortRecovery', 'QST-38 REST-DAY-1', { state: base({ activities: [act('recovery', 'wellbeing', 10, '{I0 06:00:00}', { duration: 15 })] }), calls: [0, 1, 2].map(s => C('dhDraw', ['{D0}', 0, s, []])) });
  add('draw', 'rest_passiveRecovery', 'QST-38 REST-DAY-1', { state: base({ activities: [act('recovery', 'wellbeing', 10, '{I0 06:00:00}', { duration: 40, passive: true })] }), calls: [0, 1, 2].map(s => C('dhDraw', ['{D0}', 0, s, []])) });
  add('draw', 'rest_otherDay', 'QST-38 REST-DAY-1', { state: base({ restDays: { '{D-1}': true } }), calls: [C('dhDraw', ['{D-1}', 0, 0, []]), C('dhDraw', ['{D0}', 0, 0, []])] });
}

/* dhEnsure: the migration, stale cards, the day roll, cards already dealt, cards without their quest */
function ensures() {
  const T = ['t4', 't20', 't40'];
  const E = (id, rule, state, calls = [ENSURE, CARDS, ENSURE], clock) => add('ensure', id, rule, { state, calls, clock });
  E('fresh_noClass', 'QST-41 QST-42 QST-8', { activities: [], questDone: {}, classId: null, quests: defaults(), sparks: 0 });
  CLASSES.slice(1).forEach(cls => E('preHand_' + cls, 'QST-41 QST-42', { activities: [], questDone: {}, classId: cls, classContent: cls, quests: classPack(cls, true).concat([custom(1)]), sparks: 0 }));
  E('migrated_keepsQ1', 'QST-42', { ...base({ quests: defaults() }) });
  for (const [n, v] of [['zero', 0], ['empty', ''], ['false', false], ['null', null]]) E('mig_' + n, 'QST-42', { ...base({ quests: defaults() }), dhMig: v });
  const ids = ['q1', 'q2', 'q3', 'q4', 'q10', 'Q1', 'q', 'cc_', 'cc_x', 'cc_x\ny', 'cc_x\u2028', 'cc_x\u0085', 'cc_x\r', 'xcc_a', ' cc_a', 'q1 ', 'cc_\u2029'];
  E('mig_patterns', 'QST-42 QD01', { ...base({ quests: ids.map((id, i) => ({ id, name: 'Q' + i, cadence: 'daily', xp: 10 })) }), dhMig: undefined });
  E('mig_kinds', 'QST-42', { ...base({ quests: [{ id: 'cc_n', name: 'N', cadence: 'daily', xp: 10, kind: 'nutrition' }, { id: 'cc_h', name: 'H', cadence: 'daily', xp: 10, kind: 'hydration' },
    { id: 'cc_w', name: 'W', cadence: 'weekly', xp: 10 }, { id: 'q2', name: 'E', cadence: 'epic', xp: 10 }, { id: 'cc_d', name: 'D', cadence: 'Daily', xp: 10 }, { id: 'q3', name: 'K', cadence: 'daily', xp: 10, kind: 'Nutrition' }] }), dhMig: undefined });
  E('mig_oddIds', 'QST-42', { ...base({ quests: [{ id: ['q1'], name: 'A', cadence: 'daily' }, { id: 12, name: 'B', cadence: 'daily' }, { name: 'C', cadence: 'daily' }, { id: null, name: 'D', cadence: 'daily' }, { id: { a: 1 }, name: 'E', cadence: 'daily' }] }), dhMig: undefined });
  E('customKept', 'QST-41', base({ quests: [custom(1), custom(2, 'weekly'), custom(3, 'epic')] }));
  E('dealtToday', 'QST-41', base(dealt('{D0}', T)), [ENSURE, ENSURE, CARDS]);
  E('dealtToday_flags', 'QST-41 QST-49', base(dealt('{D0}', T, { dealt: true, up: [true, false, true], turned: [false, true, false], rerollsUsed: 2, nonce: 5 })));
  const missing = base(dealt('{D0}', T)); missing.quests.splice(1, 1);
  E('dealtToday_questMissing', 'QST-41', missing);
  const allMissing = base(dealt('{D0}', T)); allMissing.quests = [custom(4)];
  E('dealtToday_questsMissing', 'QST-41', allMissing);
  const nMismatch = base(dealt('{D0}', T)); nMismatch.dailyDraw.cards[2].n = 1; nMismatch.quests.pop();
  E('cardNonceMismatch_duplicates', 'QST-41 QD19', nMismatch, [ENSURE, ENSURE, ENSURE, CARDS]);
  const unknownTpl = base(dealt('{D0}', T)); unknownTpl.dailyDraw.cards[0].tpl = 't99'; unknownTpl.quests.shift();
  E('cardUnknownTemplate', 'QST-41 QD19', unknownTpl);
  const noN = base(dealt('{D0}', T)); delete noN.dailyDraw.cards[1].n; noN.quests.splice(1, 1);
  E('cardWithoutN', 'QST-41 QD19', noN);
  const yesterday = base(dealt('{D-1}', ['t1', 't18', 't44'], { dealt: true, up: [true, true, true], turned: [true, false, false], rerollsUsed: 1, nonce: 1 }));
  yesterday.questDone = { ['dh_{D-1}_0_0_{D-1}']: 'quest_1' }; yesterday.quests.push(custom(5));
  E('dayRoll_fromYesterday', 'QST-41 QST-43', yesterday);
  const staleQuests = base(dealt('{D0}', T)); staleQuests.quests.push(handQuest('t2', '{D-2}', 0, 0), handQuest('t3', '{D-1}', 1, 4));
  E('staleCardsRemoved', 'QST-43', staleQuests);
  const oddHand = base(dealt('{D0}', T)); oddHand.quests.push({ ...custom(6), hand: true }, { ...handQuest('t7', '{D0}', 0, 9), id: 'dh_{D0}x_0_9' }, { ...custom(7), hand: 'yes' }, { ...custom(8), hand: 0 }, { ...handQuest('t8', '{D0}', 2, 3) });
  E('handFlagOnOtherIds', 'QST-43 QD21', oddHand);
  for (const n of [2, 4]) { const s = base(dealt('{D0}', ['t1', 't2', 't3', 't4'].slice(0, n))); E('cards' + n, 'QST-41', s) }
  E('drawNull', 'QST-41', base({ dailyDraw: null }));
  E('drawYesterdayWithFlags', 'QST-41', base({ dailyDraw: { date: '{D-1}', nonce: 3, rerollsUsed: 3, cards: [], dealt: true } }));
  E('questsMissing', 'QST-41', { activities: [], questDone: {}, classId: 'athlete', dhMig: 1 });
  E('questsNull', 'QST-41', { ...base(), quests: null });
  E('questsMissing_noMig', 'QST-41 QST-42', { activities: [], questDone: {}, classId: 'explorer' });
  /* the day rolls between two calls; luck goes stale at midnight (luckToday is only today's roll) */
  CLOCKS.forEach(([zone, now], i) => {
    add('ensure', 'roll_' + i, 'QST-41 QST-43 QST-34', { clock: [zone, now], state: base({ classId: CLASSES[i % CLASSES.length], luck: luck(1000 + i * 811), quests: [custom(9)] }),
      calls: [ENSURE, C('dhEnsure', [], plusMs(now, 30000)), C('dhCards', [], plusMs(now, 30000)), C('dhEnsure', [], plusMs(now, 24 * H)), C('dhEnsure', [], plusMs(now, 49 * H)), C('dhCards', [], plusMs(now, 49 * H))] });
  });
  /* QD02: a class switch keeps today's Class card until a reroll */
  add('ensure', 'classSwitch', 'QST-41 QD02', { state: base({ classId: 'athlete', sparks: 500 }), calls: [ENSURE, C('assign', ['classId', 'scholar']), ENSURE, reroll(1), CARDS] });
  add('ensure', 'classSwitch_toNone', 'QST-41 QD02', { state: base({ classId: 'explorer', sparks: 500 }), calls: [ENSURE, C('assign', ['classId', null]), reroll(1), reroll(1)] });
  add('ensure', 'luckRolledLater', 'QST-41 QST-34', { state: base(), calls: [ENSURE, C('assign', ['luck', luck(9999)]), ENSURE, reroll(2)] });
}

/* dhReroll, dhRerollCost: free and paid, short of Sparks, finished cards, flags, odd counters */
function rerolls() {
  const T = ['t6', 't23', 't51'];
  const R = (id, rule, state, calls, clock) => add('reroll', id, rule, { state, calls, clock });
  [0, 1, 2].forEach(s => R('free_' + s, 'QST-45 QST-46', base({ classId: 'scholar' }), [ENSURE, COST, reroll(s), COST, CARDS]));
  for (const [n, v] of [['50', 50], ['49', 49], ['49_5', 49.5], ['49_4', 49.4], ['string120', '120'], ['missing', undefined], ['null', null], ['negative', -10], ['big', 1e6], ['text', 'lots']]) {
    const s = base(dealt('{D0}', T, { rerollsUsed: 1 })); if (v === undefined) delete s.sparks; else s.sparks = v;
    R('paid_' + n, 'QST-45 QST-46 SPARK-3' + (n === '49_5' ? ' QD22' : ''), s, [COST, reroll(1), COST]);
  }
  R('repeated', 'QST-46', base({ ...dealt('{D0}', T), sparks: 1000, classId: 'entrepreneur' }), [reroll(0), reroll(0), reroll(0), reroll(0), reroll(0), reroll(0), CARDS, COST]);
  R('alternating', 'QST-46', base({ ...dealt('{D0}', T), sparks: 175, classId: 'athlete' }), [reroll(0), reroll(1), reroll(2), reroll(0), reroll(1), reroll(2), CARDS]);
  const done = base(dealt('{D0}', T)); done.questDone = { ['dh_{D0}_1_0_{D0}']: 'quest_17' };
  R('doneCard', 'QST-46', done, [reroll(1), reroll(0), COST]);
  const doneFalsy = base(dealt('{D0}', T)); doneFalsy.questDone = { ['dh_{D0}_1_0_{D0}']: '' };
  R('doneFalsy', 'QST-46', doneFalsy, [reroll(1)]);
  const weekly = base(dealt('{D0}', T)); weekly.quests[0].cadence = 'weekly'; weekly.questDone = { ['dh_{D0}_0_0_{W}']: 'quest_3' };
  R('doneWeekly', 'QST-46 QST-12', weekly, [reroll(0)]);
  CLOCKS.forEach(([zone, now], i) => {
    const w = base(dealt('{D0}', T)); w.quests[2].cadence = 'weekly'; w.questDone = { ['dh_{D0}_2_0_{W}']: 'quest_4' };
    R('doneWeekly_' + i, 'QST-46 QST-12 TIME-8', w, [reroll(2), reroll(1)], [zone, now]);
  });
  const epic = base(dealt('{D0}', T)); epic.quests[1].cadence = 'epic'; epic.questDone = { ['dh_{D0}_1_0_once']: 'quest_5' };
  R('doneEpic', 'QST-46', epic, [reroll(1)]);
  const yDone = base(dealt('{D0}', T)); yDone.questDone = { ['dh_{D0}_0_0_{D-1}']: 'quest_6' };
  R('doneYesterdayKey', 'QST-46', yDone, [reroll(0)]);
  R('afterDayRoll', 'QST-45 QST-46 QST-41', base({ ...dealt('{D0}', T, { rerollsUsed: 4, nonce: 4 }), sparks: 60 }), [COST, C('dhReroll', [0], plusMs(CLOCKS[0][1], 13 * H)), C('dhRerollCost', [], plusMs(CLOCKS[0][1], 13 * H)), C('dhReroll', [0], plusMs(CLOCKS[0][1], 13 * H))]);
  for (const s of [3, -1, 1.5]) R('slot_' + String(s).replace('.', '_').replace('-', 'minus'), 'QST-46', base({ dailyDraw: null }), [reroll(s), CARDS]);
  for (const [n, v] of [['missing', undefined], ['null', null], ['string0', '0'], ['five', 5], ['minus1', -1], ['half', 0.5], ['bool', true], ['array', [0]]]) {
    const s = base(dealt('{D0}', T)); s.sparks = 100; if (v === undefined) delete s.dailyDraw.rerollsUsed; else s.dailyDraw.rerollsUsed = v;
    R('rerollsUsed_' + n, 'QST-45 QST-46' + (n === 'missing' ? ' QD23' : ''), s, [COST, reroll(2), COST]);
  }
  for (const [n, v] of [['missing', undefined], ['string2', '2'], ['half', 1.5], ['null', null], ['big', 1e21]]) {
    const s = base(dealt('{D0}', T)); if (v === undefined) delete s.dailyDraw.nonce; else s.dailyDraw.nonce = v;
    R('nonce_' + n, 'QST-46 QST-39' + (n === 'missing' ? ' QD23' : ''), s, [reroll(0), CARDS]);
  }
  for (const [n, v] of [['missing', undefined], ['full', [true, true, true]], ['short', [true]], ['string', 'xyz'], ['zero', 0], ['number', 7], ['object', { a: true, 1: true }]]) {
    const s = base(dealt('{D0}', T, { dealt: true, turned: [true, true, true] })); if (v !== undefined) s.dailyDraw.up = v;
    R('up_' + n, 'QST-46 QST-49 QD16', s, [reroll(2)]);
  }
  const lost = base(dealt('{D0}', T)); lost.quests.splice(0, 1);
  R('cardQuestLost', 'QST-46 QST-41', lost, [reroll(0), CARDS]);
  R('restDay', 'QST-46 REST-DAY-1', base({ ...dealt('{D0}', T), restDays: { '{D0}': true }, sparks: 400 }), [reroll(0), reroll(1), reroll(2), reroll(0), reroll(1), reroll(2)]);
  for (const v of [1000, 9999]) R('luck' + v, 'QST-46 QST-38', base({ ...dealt('{D0}', T), luck: luck(v), sparks: 400 }), [reroll(0), reroll(1), reroll(2), reroll(2), reroll(2)]);
  R('freshDayThenFour', 'QST-45 QST-46', base({ classId: 'explorer', sparks: 150 }), [reroll(1), reroll(1), reroll(1), reroll(1), reroll(1), COST]);
  R('costWithoutDraw', 'QST-45', base(), [COST]);
  R('costDrawPrimitive', 'QST-45', base({ dailyDraw: 7 }), [COST]);
  R('costDrawEmpty', 'QST-45', base({ dailyDraw: {} }), [COST]);
}

/* saves the page would never write itself: where it throws, and where a malformed value still deals */
function odd() {
  const T = ['t2', 't21', 't47'];
  const O = (id, rule, state, calls = [ENSURE]) => add('odd', id, rule + ' QD21', { state, calls }); // every save here is malformed
  for (const [n, v] of [['object', {}], ['text', 'abc'], ['number', 5], ['true', true]]) { O('quests_' + n, 'QST-41', base({ quests: v })); O('quests_' + n + '_noMig', 'QST-41', { ...base({ quests: v }), dhMig: undefined }) }
  O('questNull_noMig', 'QST-42', { ...base({ quests: [custom(1), null] }), dhMig: undefined });
  O('questNull', 'QST-43', base({ quests: [custom(1), null] }));
  O('questPrimitives', 'QST-43', base({ quests: ['abc', 7, true, custom(2)] }), [ENSURE, CARDS]);
  O('handNumericId', 'QST-43', base({ quests: [{ id: 42, hand: true, name: 'x', cadence: 'daily' }] }));
  O('handMissingId', 'QST-43', base({ quests: [{ hand: true, name: 'x', cadence: 'daily' }] }));
  O('handArrayId', 'QST-43', base({ quests: [{ id: ['dh_{D0}_0_0'], hand: true, name: 'a', cadence: 'daily' }, { id: ['x', 'dh_{D0}_1_0'], hand: true, name: 'b', cadence: 'daily' }] }), [ENSURE, CARDS]);
  for (const [n, v] of [['number', 5], ['text', 'str'], ['array', []], ['true', true], ['zero', 0]]) O('draw_' + n, 'QST-41', base({ dailyDraw: v }));
  for (const [n, v] of [['text3', 'abc'], ['objectLength3', { length: 3 }], ['objectLength3text', { length: '3' }], ['number', 3], ['empty', '']]) {
    const s = base(dealt('{D0}', T)); s.dailyDraw.cards = v; O('cards_' + n, 'QST-41', s);
  }
  const nullCard = base(dealt('{D0}', T)); nullCard.quests = []; nullCard.dailyDraw.cards[1] = null;
  O('cardNull', 'QST-41', nullCard, [ENSURE, CARDS]);
  const primCard = base(dealt('{D0}', T)); primCard.quests = [{ name: 'no id', cadence: 'daily' }]; primCard.dailyDraw.cards[0] = 5; primCard.dailyDraw.cards[2] = 'x';
  O('cardPrimitive', 'QST-41', primCard, [ENSURE, C('dhReroll', [0]), CARDS]);
  const primCard2 = base(dealt('{D0}', T)); primCard2.dailyDraw.cards[1] = true; primCard2.sparks = 80;
  O('cardTrue_reroll', 'QST-46', primCard2, [C('dhReroll', [1]), C('dhReroll', [1]), CARDS]);
  const noId = base(dealt('{D0}', T)); delete noId.dailyDraw.cards[0].id; noId.quests.push({ name: 'no id either', cadence: 'daily' });
  O('cardNoId_questNoId', 'QST-41', noId, [ENSURE, C('dhReroll', [0])]);
  const numTpl = base(dealt('{D0}', T)); numTpl.dailyDraw.cards[2].tpl = 47; numTpl.quests.pop();
  O('cardNumericTpl', 'QST-41', numTpl, [ENSURE, C('dhReroll', [1])]);
  const dateNum = base({ dailyDraw: { date: 20261108, nonce: 0, rerollsUsed: 0, cards: [{}, {}, {}] } });
  O('drawDateNumber', 'QST-41', dateNum);
  for (const cls of ['toString', '__proto__', 'constructor', 'hasOwnProperty', 'valueOf']) O('class_' + cls, 'QST-37', base({ classId: cls }), [ENSURE, C('dhDraw', ['{D0}', 0, 0, []])]);
  O('class_array', 'QST-37', base({ classId: ['scholar'] }), [ENSURE, C('dhDraw', ['{D0}', 0, 1, []])]);
  O('class_number', 'QST-37', base({ classId: 3 }), [ENSURE]);
  O('class_wrongCase', 'QST-37', base({ classId: 'Athlete' }), [ENSURE]);
  const noDone = base(dealt('{D0}', T)); delete noDone.questDone;
  O('questDoneMissing', 'QST-46', noDone, [C('dhReroll', [0]), ENSURE]);
  const nullDone = base(dealt('{D0}', T)); nullDone.questDone = null;
  O('questDoneNull', 'QST-46', nullDone, [C('dhReroll', [0])]);
  const arrDone = base(dealt('{D0}', T)); arrDone.questDone = ['x'];
  O('questDoneArray', 'QST-46', arrDone, [C('dhReroll', [0])]);
  O('activitiesMissing', 'QST-35', { ...base(), activities: undefined }, [ENSURE, C('dhRecentAttrXP', [7])]);
  O('activitiesMissing_dealt', 'QST-41', { ...base(dealt('{D0}', T)), activities: undefined }, [ENSURE, CARDS]);
  const nullCardReroll = base(dealt('{D0}', T)); nullCardReroll.dailyDraw.cards[2] = null; nullCardReroll.quests.pop(); nullCardReroll.sparks = 99;
  O('cardNull_reroll', 'QST-46', nullCardReroll, [C('dhReroll', [0])]); // the reroll's own dhEnsure throws first
  const nullQuestAfter = base(dealt('{D0}', T)); nullQuestAfter.quests.push(null);
  O('rerollQuestNullAfterMatch', 'QST-46', nullQuestAfter, [C('dhReroll', [0])]);
  O('emptyQuestsFalsy', 'QST-41', base({ quests: 0 }), [ENSURE, CARDS]);
}

/* random but seeded saves and call sequences */
function fuzz(count) {
  const r = rng(20261108);
  const sparksOf = () => pickOf(r, [0, 0, 49, 49.5, 50, 75, 120, 1000, '75', -5, undefined]);
  const lucks = () => pickOf(r, [undefined, undefined, 'stale', 1000, 9999, 5500, 'int', 'int', 'float', 0, -300, 12000]);
  for (let i = 0; i < count; i++) {
    const clock = CLOCKS[intIn(r, 0, CLOCKS.length - 1)];
    const cls = pickOf(r, CLASSES.concat(['wizard', undefined]));
    const acts = Array.from({ length: intIn(r, 0, 18) }, () => {
      const type = pickOf(r, ['strength', 'cardio', 'study', 'quest', 'recovery', 'meditation', 'steps', 'coach', 'work', 'social']);
      const extra = { duration: pickOf(r, [5, 15, 20, 25, 45, 90]) };
      if (r() < 0.15) extra.passive = true;
      if (r() < 0.25) extra.xpSplit = Array.from({ length: intIn(r, 0, 3) }, () => ({ attr: pickOf(r, ATTRS.concat(['nope'])), xp: pickOf(r, [intIn(r, 0, 60), intIn(r, 0, 60) + 0.5, '12', -3]) }));
      const when = r() < 0.05 ? pickOf(r, ['bad', null, '{D-3}', '{D0}T00:10:00']) : '{I' + intIn(r, -9, 2) + ' ' + String(intIn(r, 0, 23)).padStart(2, '0') + ':' + String(intIn(r, 0, 59)).padStart(2, '0') + ':07}';
      return act(type, pickOf(r, ATTRS), pickOf(r, [intIn(r, 0, 80), intIn(r, 0, 80), 0.1 * intIn(r, 0, 99), '30']), when, extra);
    });
    const quests = [];
    if (r() < 0.4) quests.push(...(cls && content.classQuests[cls] ? classPack(cls, r() < 0.3) : defaults()).slice(0, 8)); // a few, to keep the file small
    for (let k = intIn(r, 0, 3); k > 0; k--) quests.push(custom(i * 10 + k, pickOf(r, ['daily', 'weekly', 'epic'])));
    const state = { activities: acts, questDone: {}, quests, ...(cls === undefined ? {} : { classId: cls }), ...(r() < 0.7 ? { dhMig: 1 } : {}) };
    const sp = sparksOf(); if (sp !== undefined) state.sparks = sp;
    const lv = lucks();
    if (lv !== undefined) state.luck = lv === 'stale' ? staleLuck(6000) : luck(lv === 'int' ? intIn(r, 1000, 9999) : lv === 'float' ? 1000 + r() * 8999 : lv);
    if (r() < 0.2) state.restDays = { '{D0}': true };
    if (r() < 0.08) state.activities.push(act('recovery', 'wellbeing', 5, '{I0 07:00:00}', { duration: 30 }));
    const shape = r();
    if (shape < 0.35) {
      const picks = []; while (picks.length < 3) { const t = pickOf(r, TPL).id; if (!picks.includes(t)) picks.push(t) }
      const day = r() < 0.7 ? '{D0}' : '{D-' + intIn(r, 1, 3) + '}';
      const d = dealt(day, picks, { rerollsUsed: intIn(r, 0, 3), nonce: intIn(r, 0, 4) });
      if (r() < 0.4) d.dailyDraw.dealt = r() < 0.5;
      if (r() < 0.4) d.dailyDraw.up = [r() < 0.5, r() < 0.5, r() < 0.5];
      if (r() < 0.3) d.dailyDraw.turned = [r() < 0.5, r() < 0.5, r() < 0.5];
      state.dailyDraw = d.dailyDraw;
      state.quests.push(...d.quests.filter(() => r() < 0.85));
      if (r() < 0.4) state.questDone[d.quests[intIn(r, 0, 2)].id + '_' + day] = 'quest_' + i;
    } else if (shape < 0.45) state.quests.push(handQuest(pickOf(r, TPL).id, '{D-1}', intIn(r, 0, 2), 0));
    const calls = [];
    let at = null;
    for (let k = intIn(r, 1, 7); k > 0; k--) {
      if (r() < 0.12) at = plusMs(at || clock[1], intIn(r, 1, 30) * H);
      const what = r();
      const c = what < 0.3 ? ENSURE : what < 0.75 ? reroll(r() < 0.04 ? 3 : intIn(r, 0, 2)) : what < 0.85 ? COST : what < 0.93 ? CARDS : WEAK;
      calls.push(at ? { ...c, at } : c);
    }
    add('fuzz', 'f' + i, 'QST-38 QST-41 QST-46', { clock, state, calls });
  }
}

function defineCases() {
  if (CASES.length) return CASES;
  seeds(); generators(); makeQuests(); recent(); draws(); ensures(); rerolls(); odd(); fuzz(420);
  return CASES;
}

module.exports = { defineCases, CLOCKS };
