/* The Life Director ledger's golden cases (WP-LD6): what tools/gen_ld_ledger_golden.js records from the page and the native code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector, Ledger*.kt) must reproduce: the completion ledger, quest and
   goal progress, misses and their consequences, and the lifecycle transitions (engine.js:1904-2085). Rules are
   docs/specs/LIFE_DIRECTOR_LEDGER_SPEC.md's (LED-n).

   Every case is {id, family, rule, fn, args}; the recorder adds out. As in tools/ld_schedule_cases.js the definitions use no Intl: instants
   are built from the WP-LD1 table of clock changes (localInstant), and the plans whose sessions the streams complete are made by the engine
   in node when the cases are defined (they only choose the inputs). The only zone-dependent step is the stream's day key, which the
   recorder computes with the page's own Intl, as the page's afterEvent does (logic.js:287).

   Streams (fn 'stream') replay a sequence of host-like steps against one ledger, its quests, goals and planned sessions; the recorder writes
   what each step gave and the ledger after it (without the event bodies, which never change once written: every new one is in its step's
   result) and, at the end, everything in full. The step kinds are listed at runStream in tools/gen_ld_ledger_golden.js. */
'use strict';
const { MATRIX, CHANGES } = require('./ld_kernel_cases.js');
const P = require('./ld_placement_cases.js');

const FAMILIES = ['empty', 'event', 'transition', 'record', 'revoke', 'has', 'compact', 'reconcile', 'misses', 'miss', 'stream', 'dst', 'fuzz', 'malformed'];
const ZONES = MATRIX;
const { localInstant, random, copy, scenarioAt } = P;

const STATES = ['PLANNED', 'IN_PROGRESS', 'COMPLETED_EARLY', 'COMPLETED_ON_TIME', 'COMPLETED_LATE', 'PARTIALLY_COMPLETED', 'SKIPPED_VOLUNTARY',
  'MISSED_UNEXPLAINED', 'EXCUSED_DISPLACED', 'EXCUSED_BY_TOKEN', 'CANCELLED_BY_QUEST'];
/* Object.prototype's own members: a lookup by one of these names finds an inherited value (LG03, LG04). */
const PROTO = ['constructor', '__defineGetter__', '__defineSetter__', 'hasOwnProperty', '__lookupGetter__', '__lookupSetter__', 'isPrototypeOf',
  'propertyIsEnumerable', 'toString', 'valueOf', '__proto__', 'toLocaleString'];
const PLAYERS = ['lm_3k9x0a7b2c1d', 'player', '', 'Zoë', 'p😀', '\ud800x', '0', 'lm_' + 'z'.repeat(40)];
const ABSENT = '$absent';
/* The arguments without the members marked absent (JSON cannot hold undefined: a call with fewer arguments stands for it). */
const args = (...a) => { while (a.length && a[a.length - 1] === ABSENT) a.pop(); if (a.includes(ABSENT)) throw new Error('absent in the middle'); return a; };

const T0 = Date.UTC(2026, 10, 2, 12) / 1000; // Mon 2026-11-02 12:00 UTC
const SESSION_KEYS = ['instanceId', 'taskId', 'templateId', 'title', 'tag', 'goalId', 'linkedGoalIds', 'linkedQuestRequirementIds', 'startEpoch',
  'endEpoch', 'durationSlots', 'tier', 'lifecycleState', 'isUserLocked', 'bwcId', 'IP', 'IC', 'spacingKey', 'source'];
const SOURCE_KEYS = ['id', 'kind', 'title', 'templateId', 'tag', 'goalId', 'linkedGoalIds', 'linkedQuestRequirementIds', 'tier', 'tierReason', 'dOpt',
  'dMin', 'dMax', 'IP', 'IC', 'spacingKey', 'windowStart', 'windowEnd', 'deadlineEpoch'];
const pick = (o, keys) => Object.fromEntries(keys.filter(k => o[k] !== undefined).map(k => [k, copy(o[k])]));
/* A planned session as the store keeps it, with the members the ledger reads (and its task snapshot, trimmed). */
const slim = inst => Object.assign(pick(inst, SESSION_KEYS), inst.source ? { source: pick(inst.source, SOURCE_KEYS) } : {});

/* A hand-made planned session. */
function session(id, start, minutes, more) {
  return Object.assign({ instanceId: id, taskId: 't' + id.slice(1), title: 'Session ' + id, tag: 'fitness.strength', linkedGoalIds: ['g1'],
    linkedQuestRequirementIds: [], startEpoch: start, endEpoch: start + minutes * 60, durationSlots: Math.ceil(minutes / 15), tier: 3,
    lifecycleState: 'PLANNED', isUserLocked: false, bwcId: 'bwc-' + start, IP: 3, IC: 1, spacingKey: 'g1:strength',
    source: { id: 't' + id.slice(1), kind: 'GOAL', title: 'Session ' + id, tag: 'fitness.strength', linkedGoalIds: ['g1'], linkedQuestRequirementIds: [], tier: 3, dOpt: minutes } }, more || {});
}
const ledgerWith = (more, player) => Object.assign({ schemaVersion: 1, playerId: player === undefined ? 'lm_3k9x0a7b2c1d' : player, events: {}, order: [], bounties: {},
  penalties: {}, restTokens: 1, completionsSinceToken: 0, consecutiveMisses: 0, consequencesByBwc: {}, regroupActive: false, streaks: {} }, more || {});
/* A stored event as recordCompletion writes it (only the members later steps read differ between them). */
const storedEvent = (id, at, more) => Object.assign({ eventId: id, playerId: 'lm_3k9x0a7b2c1d', instanceId: null, clientEventId: 'c' + id, activityTypeTag: 'reading',
  templateId: null, spacingKey: null, title: 'Read', linkedGoalIds: [], linkedQuestRequirementIds: [], actualStartEpoch: at - 1800, actualDurationMinutes: 30,
  scheduledDurationMinutes: 30, completionRatio: 1, lifecycleState: 'COMPLETED_ON_TIME', intensityRating: 2, recordedAtEpoch: at, source: 'ADHOC',
  suggestedEffort: { minutes: 30, IP: 1, IC: 2, xpHint: 33 } }, more || {});
const quest = (id, tag, more) => Object.assign({ id, title: 'Quest ' + id, tier: 'CORE', tag, requiredCount: 3 }, more || {});

/* Plans made by the engine in node: [label, zone, input, the plan's sessions]. */
function plans(engine) {
  const out = [];
  const add = (label, zone, input) => {
    const o = engine.generateSchedule(input);
    out.push({ label, zone, input, sessions: o.scheduledInstances.map(slim) });
  };
  for (const [name, zone] of [['deadlines', 'UTC'], ['office', 'America/New_York'], ['manygoals', 'Asia/Kolkata'], ['student', 'Europe/London'],
    ['limited', 'Australia/Sydney'], ['abundant', 'America/Los_Angeles'], ['misses', 'Pacific/Apia'], ['night', 'America/Santiago'],
    ['rotating', 'Pacific/Kiritimati'], ['workchange', 'Etc/GMT+12'], ['deadlines', 'America/Phoenix'], ['manygoals', 'UTC']])
    add(name + '@' + zone, zone, scenarioAt(engine, name, 1, zone, localInstant(zone, 2026, 10, 26, 360)));
  return out;
}
/* Plans that start the morning before a 2026-2027 clock change of the strict zones (the sessions span it). */
function dstPlans(engine) {
  const out = [];
  for (const c of CHANGES.filter(c => ZONES.includes(c.zone))) {
    const local = new Date((c.at + c.before * 60) * 1000), y = local.getUTCFullYear(), mo = local.getUTCMonth() + 1, d = local.getUTCDate();
    const input = scenarioAt(engine, c.zone.includes('Santiago') || c.zone.includes('Sydney') ? 'deadlines' : 'manygoals', 1, c.zone, localInstant(c.zone, y, mo, d - 1, 360));
    const o = engine.generateSchedule(input);
    out.push({ label: c.zone + '@' + new Date(c.at * 1000).toISOString().slice(0, 10), zone: c.zone, change: c, input, sessions: o.scheduledInstances.map(slim) });
  }
  return out;
}

function defineCases(engine) {
  engineStable = engine.util.stableId;
  const cases = [], ids = new Set();
  const add = (family, rule, fn, a, tag) => {
    let id = family + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args: copy(a) });
  };

  /* empty: emptyLedger's player id */
  for (const p of [ABSENT, null, '', 0, false, true, 42, -0.5, 'player', 'lm_3k9x0a7b2c1d', 'Zoë', 'p😀', '\ud800', ' ', 'a'.repeat(300), [1, 2], [], {}, { a: 1 }])
    add('empty', 'LED-1', 'emptyLedger', args(p), JSON.stringify(p === ABSENT ? 'absent' : p));

  /* event: completionEventId over player ids, instance ids, client ids and revisions */
  const insts = [null, '', 'i_abc', '0', 0, 7, 'i😀', ['i', 'x'], { a: 1 }, true];
  const clients = [ABSENT, null, '', 'host:a1', 'ld-today:op-1', 0, 5];
  const revs = [ABSENT, null, 0, 1, 2, 17, '2', '', true, 1.5, -1, 'x', [3]];
  for (const p of ['lm_3k9x0a7b2c1d', '', 'Zoë', 'p😀', null, 12, 'player'])
    for (const i of insts) for (const c of [null, 'host:a1']) for (const r of [ABSENT, 1])
      add('event', 'LED-2', 'completionEventId', args(p, i === ABSENT ? null : i, c, r), JSON.stringify([p, i, c, r]).replace(/"\$absent"/g, 'absent'));
  for (const r of revs) for (const i of ['i_abc', '']) add('event', 'LED-2', 'completionEventId', args('lm_x', i, 'host:1', r), 'rev' + JSON.stringify([i, r]));
  for (const c of clients) add('event', 'LED-2', 'completionEventId', args('lm_x', null, c, ABSENT), 'client' + JSON.stringify(c));

  /* transition: every pair of states, and names that are not states */
  const froms = STATES.concat(['COMPLETED', 'planned', '', 'undefined', '5', ...PROTO]);
  for (const f of froms) for (const t of PROTO.includes(f) ? ['PLANNED', 'IN_PROGRESS'] : STATES.concat(['COMPLETED', 'IN_PROGRESS ', 'toString']))
    add('transition', 'LED-20', 'canTransition', [f, t], f + '>' + t);
  for (const [f, t] of [[ABSENT, ABSENT], [null, 'IN_PROGRESS'], [['PLANNED'], 'IN_PROGRESS'], [['PLANNED', 'X'], 'IN_PROGRESS'], [5, 'PLANNED'], [{}, 'X'],
    ['PLANNED', ['IN_PROGRESS']], ['MISSED_UNEXPLAINED', null], [true, 'X']])
    add('transition', 'LED-20', 'canTransition', args(f, t === ABSENT ? ABSENT : t), 'odd' + JSON.stringify([f, t]));

  /* record: recordCompletion, one behaviour at a time */
  const base = session('i_r1', T0, 60);
  const rec = (tag, ci, rule) => add('record', rule || 'LED-3 LED-4 LED-5 LED-6 LED-7 LED-8', 'recordCompletion', [ci], tag);
  const ci = more => Object.assign({ ledger: ledgerWith(), playerId: 'ignored', instance: base, actualStartEpoch: T0, actualDurationMinutes: 60, nowEpoch: T0 + 3600 }, more);
  rec('plain', ci());
  rec('no-ledger', ci({ ledger: null, playerId: 'lm_fresh' }));
  rec('no-ledger-no-player', ci({ ledger: undefined, playerId: undefined }));
  for (const [k, v] of [['ledger', false], ['ledger', 0], ['playerId', 7], ['playerId', '']]) rec('no-ledger-' + k + '=' + JSON.stringify(v), ci({ ledger: k === 'ledger' ? v : null, playerId: v }));
  for (const p of PLAYERS) rec('player:' + p, ci({ ledger: ledgerWith({}, p) }));
  for (const m of [0, 0.4, 0.5, 1, 1.5, 2.5, 14, 15, 30, 44, 44.5, 45, 45.5, 59, 60, 61, 90, 600, -3, -0.5, '30', '', null, ' 45 ', '1e2', 'x', [20], true])
    rec('minutes:' + JSON.stringify(m), ci({ actualDurationMinutes: m }), 'LED-5 LED-6');
  rec('minutes:absent', ci({ actualDurationMinutes: undefined }), 'LED-5 LED-6');
  for (const r of [0, 0.5, 0.6, 0.74, 0.75, 0.76, 1, 1.2, '0.9', null, -1, 'x'])
    for (const m of [30, 45, 50, 59]) rec('ratio:' + JSON.stringify(r) + '@' + m, ci({ satisfiedRatio: r, actualDurationMinutes: m }), 'LED-5 LED-6 LED-9');
  for (const [len, m] of [[0, 30], [1, 1], [-30, 10], [7, 7], [22, 15], [0.5 * 60, 30]])
    rec('scheduled:' + len + '@' + m, ci({ instance: Object.assign({}, base, { endEpoch: base.startEpoch + len * 60 }), actualDurationMinutes: m }), 'LED-5 LED-6');
  rec('scheduled-fraction', ci({ instance: Object.assign({}, base, { endEpoch: base.startEpoch + 90 + 0.5 }) }), 'LED-5');
  for (const [off, m] of [[-3600, 60], [-3601, 60], [-3599, 60], [-7200, 30], [0, 60], [1, 59], [3600, 60], [3599, 60], [3601, 10], [-60, 60], [-1800, 30], [-1800, 20]])
    rec('start:' + off + '+' + m, ci({ actualStartEpoch: T0 + off, actualDurationMinutes: m }), 'LED-6');
  for (const s of [0, null, '', false, undefined]) rec('start-falsy:' + JSON.stringify(s), ci({ actualStartEpoch: s }), 'LED-6');
  rec('start-falsy-adhoc', { ledger: ledgerWith(), adhoc: { tag: 'reading', title: 'Read' }, clientEventId: 'c1', actualStartEpoch: 0, actualDurationMinutes: 20, nowEpoch: T0 }, 'LED-6');
  for (const st of STATES.concat(['COMPLETED', '', 'planned'])) rec('state:' + st, ci({ instance: Object.assign({}, base, { lifecycleState: st }) }), 'LED-4');
  rec('state:absent', ci({ instance: Object.assign({}, base, { lifecycleState: undefined }) }), 'LED-4');
  rec('state:absent-no-ledger', ci({ ledger: null, instance: Object.assign({}, base, { lifecycleState: 'SKIPPED_VOLUNTARY' }) }), 'LED-4');
  const evId = engine.completionEventId('lm_3k9x0a7b2c1d', 'i_r1', null, undefined);
  rec('duplicate', ci({ ledger: ledgerWith({ events: { [evId]: storedEvent(evId, T0) }, order: [evId] }) }), 'LED-4');
  rec('duplicate-before-state', ci({ ledger: ledgerWith({ events: { [evId]: storedEvent(evId, T0) }, order: [evId] }), instance: Object.assign({}, base, { lifecycleState: 'COMPLETED_LATE' }) }), 'LED-4');
  rec('duplicate-falsy-body', ci({ ledger: ledgerWith({ events: { [evId]: 0 }, order: [evId] }) }), 'LED-4');
  rec('duplicate-archived-not-seen', ci({ ledger: ledgerWith({ archivedIds: [evId] }) }), 'LED-4 LED-11');
  for (const rv of [1, 2, '3', 0, null, true]) rec('revision:' + JSON.stringify(rv), ci({ instance: Object.assign({}, base, { revision: rv }), ledger: ledgerWith({ events: { [evId]: storedEvent(evId, T0) }, order: [evId] }) }), 'LED-2 LED-4');
  for (const id of ['', null, 0, 5, 'i 2']) rec('instance-id:' + JSON.stringify(id), ci({ instance: Object.assign({}, base, { instanceId: id }), clientEventId: 'c-9' }), 'LED-2 LED-7');
  rec('instance-id-absent-no-client', ci({ instance: Object.assign({}, base, { instanceId: undefined }) }), 'LED-2');
  for (const [tag, more] of [['adhoc', {}], ['adhoc-tag', { adhoc: { tag: 'language.spanish.vocab', title: 'Vocab' } }], ['adhoc-none', { adhoc: null }],
    ['adhoc-empty', { adhoc: {} }], ['adhoc-ip', { adhoc: { tag: 'study', IP: 5, IC: 0 } }], ['adhoc-links', { adhoc: { tag: 'x', linkedGoalIds: ['g1', 2], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0', extra: 1 }, { questId: 'q2' }, 5, 'x', []] } }],
    ['adhoc-template', { adhoc: { tag: 'admin', templateId: 'g1:a', spacingKey: 'g1:a', title: '' } }], ['adhoc-tag-proto-free', { adhoc: { tag: 'unknown.root' } }],
    ['adhoc-tag-number', { adhoc: { tag: 7 } }], ['adhoc-tag-dotted', { adhoc: { tag: '.fitness' } }]])
    rec(tag, Object.assign({ ledger: ledgerWith(), playerId: 'p', clientEventId: 'host:a1', actualStartEpoch: T0, actualDurationMinutes: 25, nowEpoch: T0 + 60 }, more), 'LED-7');
  rec('adhoc-no-client', { ledger: ledgerWith(), adhoc: { tag: 'reading' }, actualDurationMinutes: 25, nowEpoch: T0 }, 'LED-2');
  rec('adhoc-client-empty', { ledger: ledgerWith(), adhoc: { tag: 'reading' }, clientEventId: '', actualDurationMinutes: 25, nowEpoch: T0 }, 'LED-2');
  for (const [ip, ic] of [[0, 0], [5, 1], [1, 5], [2, 2], ['4', 1], [null, 3], [6, 0], [-2, 0], [2.5, 1]])
    rec('effort:' + JSON.stringify([ip, ic]), ci({ instance: Object.assign({}, base, { IP: ip, IC: ic }) }), 'LED-7');
  for (const tag of ['reading', 'study.deep', 'nutrition', 'wellbeing', 'nope', '', null, 'fitness'])
    rec('profile:' + JSON.stringify(tag), ci({ instance: Object.assign({}, base, { IP: 0, IC: 0, tag }) }), 'LED-7');
  for (const rpe of [0, 1, 2.5, 3.49, 5, 6, -1, '4', null, 'x']) rec('rpe:' + JSON.stringify(rpe), ci({ rpe }), 'LED-7');
  for (const m of [1, 7, 45, 61, 119]) for (const ipc of [1, 3, 5]) rec('xp:' + m + '@' + ipc, ci({ instance: Object.assign({}, base, { IP: ipc, IC: 1, endEpoch: T0 + 7200 }), actualDurationMinutes: m }), 'LED-7');
  for (const [n, tokens] of [[0, 1], [3, 1], [4, 1], [4, 2], [4, 3], [4, 0], [5, 1], [9, 1], [4, '2'], [4, 2.5], ['4', 1]])
    rec('tokens:' + n + '/' + tokens, ci({ ledger: ledgerWith({ completionsSinceToken: n, restTokens: tokens, consecutiveMisses: 2, regroupActive: true }) }), 'LED-8');
  for (const tokens of [0, 1, 2, 3, 4, '1', '2'])
    for (const st of ['EXCUSED_BY_TOKEN', 'MISSED_UNEXPLAINED']) for (const n of [0, 4])
      rec('refund:' + st + '/' + JSON.stringify(tokens) + '/' + n, ci({ instance: Object.assign({}, base, { lifecycleState: st }), ledger: ledgerWith({ restTokens: tokens, completionsSinceToken: n }), actualStartEpoch: T0 + 7200, nowEpoch: T0 + 9000 }), 'LED-8');
  for (const [key, m, streaks] of [['g1:s', 60, {}], ['g1:s', 60, { 'g1:s': 4 }], ['g1:s', 20, { 'g1:s': 4 }], [null, 60, {}], ['', 60, {}], [5, 60, { 5: 1 }], ['g1:s', 60, { 'g1:s': '2' }], ['k', 60, { k: null }]])
    rec('streak:' + JSON.stringify([key, m, streaks]), ci({ instance: Object.assign({}, base, { spacingKey: key }), actualDurationMinutes: m, ledger: ledgerWith({ streaks }) }), 'LED-9');
  const resid = (tag, inst, m, more) => rec('residual:' + tag, Object.assign(ci({ instance: Object.assign({}, base, inst), actualDurationMinutes: m }), more || {}), 'LED-10');
  for (const m of [1, 10, 14, 15, 16, 29, 30, 44]) resid('tier3-' + m, {}, m);
  resid('tier2', { tier: 2 }, 20);
  resid('tier2-quest', { tier: 2, linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }, 20);
  resid('tier1', { tier: 1, linkedQuestRequirementIds: [] }, 20);
  resid('tier-text', { tier: '3' }, 20);
  resid('no-source', { source: null }, 20);
  resid('source-text', { source: 'abc' }, 20);
  resid('source-list', { source: ['a', 'b'] }, 20);
  resid('title-absent', { title: undefined }, 20);
  resid('long', { endEpoch: T0 + 4 * 3600 }, 61);
  resid('now-absent', {}, 20, { nowEpoch: undefined });
  resid('source-overwritten', { source: { id: 'x', title: 'old', dOpt: 90, windowStart: 1, parentInstanceId: 'y', tierReason: 'GOAL', extra: [1] } }, 20);
  resid('ratio-0.5', {}, 25, { satisfiedRatio: 0.5 });
  rec('compacts-under', ci({ ledger: ledgerWith(bigLedger(1998, T0 - 500 * 86400)) }), 'LED-11');
  rec('compacts-at', ci({ ledger: ledgerWith(bigLedger(1999, T0 - 500 * 86400)) }), 'LED-11');
  rec('compacts-again', ci({ ledger: ledgerWith(Object.assign(bigLedger(2400, i => T0 - 410 * 86400 + 3600 * i), { archivedIds: ['old1', 'old2'] })) }), 'LED-11');
  /* the second award (LG01): the session's event was archived by an earlier compaction; recordCompletion looks only at the bodies */
  rec('archived-then-again', ci({ ledger: ledgerWith(Object.assign(bigLedger(2000, i => T0 - 300 * 86400 + i), { archivedIds: ['ev_old', evId] })), nowEpoch: T0 }), 'LED-4 LED-11 LED-12');

  /* revoke */
  const L1 = ledgerWith({ events: { [evId]: storedEvent(evId, T0), ev_b: storedEvent('ev_b', T0) }, order: [evId, 'ev_b'] });
  const rv = (tag, ledger, ...a) => add('revoke', 'LED-13', 'revokeEvent', [ledger, ...args(...a)], tag);
  rv('plain', L1, evId, 'UNDONE_IN_LIFEMAX_LOG', T0 + 50);
  rv('unknown', L1, 'ev_zzz', 'X', T0);
  rv('no-reason', L1, evId, ABSENT);
  for (const r of ['', null, 0, 'HOST_UNDO', 7]) for (const n of [0, null, '', T0, '17']) rv('reason:' + JSON.stringify([r, n]), L1, evId, r, n);
  rv('already', Object.assign(copy(L1), { revoked: { [evId]: { reason: 'A', atEpoch: 1 } } }), evId, 'B', T0);
  rv('already-falsy', Object.assign(copy(L1), { revoked: { [evId]: 0 } }), evId, 'B', T0);
  rv('second', Object.assign(copy(L1), { revoked: { [evId]: { reason: 'A', atEpoch: 1 } } }), 'ev_b', 'B', T0);
  rv('revoked-null', Object.assign(copy(L1), { revoked: null }), evId, 'B', T0);
  rv('body-falsy', ledgerWith({ events: { ev_0: 0, ev_e: '' }, order: [] }), 'ev_0', 'B', T0);
  rv('archived', ledgerWith({ archivedIds: [evId] }), evId, 'B', T0);
  for (const p of PROTO) rv('proto:' + p, L1, p, 'B', T0);
  rv('proto-own', ledgerWith({ events: { toString: storedEvent('x', 1) }, order: [] }), 'toString', 'B', T0);
  rv('id-number', ledgerWith({ events: { 5: storedEvent('x', 1) }, order: [] }), 5, 'B', T0);
  rv('id-absent', ledgerWith({ events: { undefined: storedEvent('x', 1) }, order: [] }));

  /* has */
  const hs = (tag, ledger, id) => add('has', 'LED-12', 'ledgerHas', args(ledger, id), tag);
  hs('event', L1, evId); hs('missing', L1, 'ev_q'); hs('archived', ledgerWith({ archivedIds: ['ev_a', 'ev_q'] }), 'ev_q');
  hs('archived-absent', ledgerWith({ archivedIds: ['ev_a'] }), 'ev_q'); hs('archived-null', ledgerWith({ archivedIds: null }), 'ev_q');
  hs('archived-text', ledgerWith({ archivedIds: 'xxev_qxx' }), 'ev_q'); hs('archived-text-miss', ledgerWith({ archivedIds: 'ev_' }), 'ev_q');
  hs('body-falsy', ledgerWith({ events: { ev_0: 0 } }), 'ev_0'); hs('body-falsy-archived', ledgerWith({ events: { ev_0: 0 }, archivedIds: ['ev_0'] }), 'ev_0');
  hs('id-absent', L1, ABSENT); hs('id-number', ledgerWith({ archivedIds: [5] }), 5); hs('id-number-key', ledgerWith({ events: { 5: 1 } }), 5);
  hs('id-number-text', ledgerWith({ archivedIds: ['5'] }), 5);
  for (const p of PROTO) hs('proto:' + p, ledgerWith(), p);

  /* compact (engine.js:1979-1984, private; recordCompletion calls it after every event) */
  const cp = (tag, ledger, now) => add('compact', 'LED-11', 'compactLedger', [ledger, now], tag);
  const N = T0 + 1000 * 86400, cut = N - 400 * 86400;
  for (const n of [0, 1999, 2000, 2001, 2100]) cp('size' + n, ledgerWith(bigLedger(n, cut - 1)), N);
  cp('boundary', ledgerWith(bigLedger(2000, i => cut - 1 + (i % 3))), N);
  cp('none-old', ledgerWith(bigLedger(2000, cut)), N);
  const dupes = bigLedger(2000, cut - 10); dupes.order.splice(5, 0, dupes.order[3], 'ev_gone', dupes.order[3]);
  cp('duplicates', ledgerWith(dupes), N);
  cp('archived-kept', ledgerWith(Object.assign(bigLedger(2000, cut - 10), { archivedIds: ['a', 'b'] })), N);
  cp('archived-null', ledgerWith(Object.assign(bigLedger(2000, cut - 10), { archivedIds: null })), N);
  cp('times-text', ledgerWith(bigLedger(2000, String(cut - 10))), N);
  cp('times-mixed', ledgerWith(bigLedger(2000, i => (i % 3 ? cut - 10 + (i % 2) * 20 : String(cut - 10)))), N);
  cp('now-text', ledgerWith(bigLedger(2000, cut - 10)), String(N));
  cp('now-absent', ledgerWith(bigLedger(2000, cut - 10)), null);
  cp('order-text-short', ledgerWith({ order: 'abc' }), N);

  /* reconcile */
  reconcileCases(engine, (tag, a, rule) => add('reconcile', rule || 'LED-14 LED-15 LED-16', 'reconcileQuestProgress', a, tag));

  /* misses */
  const ms = (tag, a) => add('misses', 'LED-17', 'detectMisses', a, tag);
  const pl = [session('i_m1', T0, 60), session('i_m2', T0 + 3600, 30), session('i_m3', T0 + 7200, 30, { lifecycleState: 'IN_PROGRESS' }), session('i_m4', T0, 15, { lifecycleState: 'MISSED_UNEXPLAINED' })];
  for (const now of [T0, T0 + 3600 + 3600, T0 + 3600 + 3601, T0 + 7200 + 1800 + 3601, T0 + 86400]) for (const g of [ABSENT, 0, 30, 60, 90, '15', null, -60, 'x'])
    ms(now - T0 + '@' + JSON.stringify(g), args(pl, now, g));
  ms('none', args(null, T0)); ms('empty', args([], T0)); ms('no-now', args(pl));
  ms('end-text', [[session('i_t', T0, 60, { endEpoch: String(T0 + 3600) })], T0 + 3 * 3600, 60]);
  ms('end-text-concat', [[session('i_t', T0, 60, { endEpoch: '1' })], 1e9, 60]);
  ms('now-text', [pl, String(T0 + 86400), 60]);

  /* miss */
  missCases((tag, a, rule) => add('miss', rule || 'LED-18 LED-19', 'applyMissConsequence', a, tag));

  /* stream, dst, fuzz: event streams */
  const ps = plans(engine);
  for (const s of handStreams(ps)) add('stream', s.rule, 'stream', [s.spec], s.tag);
  /* a quest done releases its other sessions: quest-only ones leave the plan, a pinned one stays as an orphan, a goal one keeps its goal */
  for (const zone of ['UTC', 'America/New_York', 'Asia/Kolkata']) {
    const at = k => localInstant(zone, 2026, 11, 2, 540 + k * 120);
    const only = { linkedGoalIds: [], tag: 'admin.finance', spacingKey: null, linkedQuestRequirementIds: [{ questId: 'q-tax', reqId: 'r0' }] };
    const planned = [0, 1, 2, 3, 4].map(k => session('i_q' + k, at(k), 30, Object.assign({}, only, k === 3 ? { isUserLocked: true } : {}, k === 4 ? { linkedGoalIds: ['g1'] } : {})));
    const steps = [0, 1].map(k => ({ op: 'complete', id: 'i_q' + k, start: at(k), minutes: 30, now: at(k) + 1800 })).concat([{ op: 'tick', now: at(6) }]);
    add('stream', 'LED-16 LED-14 LED-22', 'stream', [{ tz: zone, playerId: 'lm_q', prefs: { consequenceMode: 'MODERATE' }, ledger: null, planned,
      quests: [quest('q-tax', 'admin', { requiredCount: 2, tier: 'MANDATORY', bounty: { xp: 40 } })], goals: [{ id: 'g1', title: 'Tax', archetypeId: 'ADMIN_FINANCE' }], steps }], 'release@' + zone);
  }
  const dps = dstPlans(engine);
  for (const p of dps) for (let k = 0; k < 4; k++) add('dst', 'LED-22 LED-3 LED-5 LED-6 LED-14 LED-17', 'stream', [fuzzStream(7000 + k * 97 + dps.indexOf(p), p, k)], p.label + '#' + k);
  for (const p of dps.slice(0, 6)) add('dst', 'LED-5 LED-6 LED-22', 'stream', [gapStream(p)], 'gap@' + p.label);
  for (let s = 1; s <= 400; s++) add('fuzz', 'LED-3 LED-4 LED-5 LED-6 LED-7 LED-8 LED-9 LED-10 LED-13 LED-14 LED-15 LED-16 LED-17 LED-18 LED-19 LED-22', 'stream',
    [fuzzStream(s, ps[s % ps.length], s % 3)], 'seed' + s);

  /* malformed: shapes the page's own writers never produce */
  malformedCases(engine, (tag, fn, a) => add('malformed', 'LED-21', fn, a, tag));
  return cases;
}

/* A ledger of n tiny events; event i was recorded at at(i) (a number gives every event that time). */
function bigLedger(n, at) {
  const events = {}, order = [];
  for (let i = 0; i < n; i++) {
    const id = 'ev_' + i.toString(36);
    events[id] = { eventId: id, recordedAtEpoch: typeof at === 'function' ? at(i) : at };
    order.push(id);
  }
  return { events, order };
}

function reconcileCases(engine, add) {
  const ev = (more) => Object.assign({ eventId: 'ev_1', instanceId: 'i_1', activityTypeTag: 'fitness.strength', linkedGoalIds: ['g1'], linkedQuestRequirementIds: [],
    actualDurationMinutes: 40, completionRatio: 1, source: 'SCHEDULE' }, more || {});
  const goals = [{ id: 'g1', title: 'Lift', archetypeId: 'FITNESS_HYPERTROPHY', priority: 4, level: 'NOVICE' }];
  const r = (tag, a, rule) => add(tag, a, rule);
  const Q = (tag, quests, event, opts, rule) => r(tag, args(event || ev(), quests, [], [], opts === undefined ? ABSENT : opts), rule || 'LED-14');
  Q('count', [quest('q1', 'fitness')]);
  Q('count-exact', [quest('q1', 'fitness.strength')]);
  Q('count-wild', [quest('q1', 'fitness.*')]);
  Q('count-other', [quest('q1', 'reading')]);
  Q('count-done', [quest('q1', 'fitness', { progressCount: 2 })]);
  Q('count-done-daily', [quest('q1', 'fitness', { progressCount: 2, recurrence: 'DAILY' })], null, { dayKey: '2026-11-02' });
  Q('count-done-daily-nokey', [quest('q1', 'fitness', { progressCount: 2, recurrence: 'DAILY' })]);
  Q('count-over', [quest('q1', 'fitness', { progressCount: 5 })]);
  Q('count-bounty', [quest('q1', 'fitness', { progressCount: 2, bounty: { xp: 50 } })]);
  Q('count-bounty-zero', [quest('q1', 'fitness', { progressCount: 2, bounty: 0 })]);
  Q('minutes', [quest('q1', 'fitness', { requiredCount: 0, requiredMinutes: 100, progressMinutes: 30 })]);
  Q('minutes-done', [quest('q1', 'fitness', { requiredCount: 0, requiredMinutes: 60, progressMinutes: 30 })]);
  Q('minutes-text', [quest('q1', 'fitness', { requiredCount: 0, requiredMinutes: 100, progressMinutes: 30 })], ev({ actualDurationMinutes: '40' }));
  Q('both', [quest('q1', 'fitness', { requiredCount: 2, requiredMinutes: 60 })]);
  Q('min-session', [quest('q1', 'fitness', { requirements: [{ requirementId: 'a', activityTypeTag: 'fitness', targetCount: 2, minSessionMinutes: 45 }] })]);
  Q('min-session-ok', [quest('q1', 'fitness', { requirements: [{ requirementId: 'a', activityTypeTag: 'fitness', targetCount: 2, minSessionMinutes: 40 }] })]);
  Q('min-session-quest', [quest('q1', 'fitness', { minSessionMinutes: 41 })]);
  for (const cr of [0.74, 0.75, 0.5, 1]) for (const sr of [ABSENT, 0.5, 0, 0.8]) Q('satisfied:' + cr + '/' + JSON.stringify(sr), [quest('q1', 'fitness', { requiredMinutes: 100 })], ev({ completionRatio: cr }), sr === ABSENT ? {} : { satisfiedRatio: sr });
  Q('linked', [quest('q1', 'reading', { exclusive: true })], ev({ linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }));
  Q('linked-wrong-req', [quest('q1', 'reading', { exclusive: true })], ev({ linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r1' }] }));
  Q('exclusive-tag', [quest('q1', 'fitness', { exclusive: true })]);
  Q('exclusive-dedicated', [quest('q1', 'fitness', { requiresDedicatedExclusiveTime: true })]);
  Q('reqs', [quest('q1', 'x', { requirements: [{ requirementId: 'a', activityTypeTag: 'fitness', targetCount: 2, currentCount: 1, extra: 'k' }, { id: 'b', tag: 'reading', targetCount: 1 }, { activityTypeTag: 'fitness.*', targetMinutes: 50, currentMinutes: 20 }] })]);
  Q('reqs-done', [quest('q1', 'x', { requirements: [{ requirementId: 'a', activityTypeTag: 'fitness', targetCount: 2, currentCount: 1 }, { activityTypeTag: 'fitness', targetMinutes: 50, currentMinutes: 20 }] })]);
  Q('reqs-linked-one', [quest('q1', 'x', { exclusive: true, requirements: [{ requirementId: 'a', activityTypeTag: 'reading', targetCount: 2 }, { requirementId: 'b', activityTypeTag: 'study', targetCount: 1 }] })], ev({ linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'b' }] }));
  Q('reqs-empty', [quest('q1', 'fitness', { requirements: [] })]);
  Q('no-target', [quest('q1', 'fitness', { requiredCount: 0 })]);
  Q('status', [quest('q1', 'fitness', { status: 'COMPLETED' }), quest('q2', 'fitness', { status: 'PAUSED' }), quest('q3', 'fitness', { status: '' })]);
  Q('applied', [quest('q1', 'fitness', { appliedEventIds: ['ev_1'] })]);
  Q('applied-text', [quest('q1', 'fitness', { appliedEventIds: 'xxev_1' })]);
  Q('applied-text-miss', [quest('q1', 'fitness', { appliedEventIds: 'ev_' })]);
  for (const n of [499, 500, 501]) Q('applied-cap' + n, [quest('q1', 'fitness', { appliedEventIds: Array.from({ length: n }, (_, i) => 'e' + i) })]);
  Q('applied-text-cap', [quest('q1', 'fitness', { appliedEventIds: 'x'.repeat(520) })]);
  Q('legacy-ids', [{ questId: 'q9', title: 'Q', activityTypeTag: 'fitness', targetCount: 2, currentCount: 1 }]);
  Q('legacy-ids-number', [{ id: 9, tag: 'fitness', requiredCount: 2 }], ev({ linkedQuestRequirementIds: [{ questId: '9', reqId: 'r0' }] }));
  Q('counts-text', [quest('q1', 'fitness', { requiredCount: '3', progressCount: '1' })]);
  Q('counts-bad', [quest('q1', 'fitness', { requiredCount: 'x', progressCount: 1 })]);
  Q('several', [quest('q1', 'fitness'), quest('q2', 'reading'), quest('q3', 'fitness', { progressCount: 2 }), quest('q4', 'fitness.cardio')]);
  Q('none', null); Q('empty', []);
  Q('title-number', [quest('q1', 'fitness', { title: 5, progressCount: 2 })]);
  Q('event-id-absent', [quest('q1', 'fitness', { progressCount: 2 })], ev({ eventId: undefined }));
  Q('dur-absent', [quest('q1', 'fitness', { requiredMinutes: 90 })], ev({ actualDurationMinutes: undefined }));
  /* goals */
  const G = (tag, gs, event, rule) => r(tag, [event || ev(), [], gs, []], rule || 'LED-15');
  G('linked', goals);
  G('linked-goalId', [{ goalId: 'g1', title: 'Lift', archetypeId: 'FITNESS_HYPERTROPHY' }]);
  G('linked-number', [{ id: 1, title: 'x' }], ev({ linkedGoalIds: ['1'] }));
  G('not-linked', goals, ev({ linkedGoalIds: [] }));
  G('adhoc-tag', goals, ev({ linkedGoalIds: [], source: 'ADHOC' }));
  G('adhoc-tag-broad', goals, ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'fitness' }));
  G('adhoc-tag-deeper', goals, ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'fitness.strength.upper' }));
  G('adhoc-other', goals, ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'reading' }));
  G('adhoc-language', [{ id: 'g2', title: 'Learn Spanish', archetypeId: 'SKILL_LANGUAGE', subject: 'spanish' }], ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'language.spanish.vocab' }));
  G('adhoc-language-generic', [{ id: 'g2', title: 'Learn Spanish', archetypeId: 'SKILL_LANGUAGE', subject: 'spanish' }], ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'language' }));
  G('adhoc-custom', [{ id: 'g3', title: 'Pottery', customTemplates: [{ key: 'wheel', tag: 'creative.pottery', minutes: 60 }] }], ev({ linkedGoalIds: [], source: 'ADHOC', activityTypeTag: 'creative.pottery' }));
  G('adhoc-disabled', [{ id: 'g1', title: 'Lift', archetypeId: 'FITNESS_HYPERTROPHY', disabledTemplates: ['strength', 'conditioning', 'mobility', 'nutrition'] }], ev({ linkedGoalIds: [], source: 'ADHOC' }));
  G('applied', [Object.assign({ appliedEventIds: ['ev_1'] }, goals[0])]);
  G('applied-text', [Object.assign({ appliedEventIds: 'ev_1' }, goals[0])]);
  G('applied-cap', [Object.assign({ appliedEventIds: Array.from({ length: 500 }, (_, i) => 'e' + i) }, goals[0])]);
  G('counters', [Object.assign({ sessionsCompleted: 4, minutesCompleted: 100 }, goals[0])]);
  G('counters-text', [Object.assign({ sessionsCompleted: '4', minutesCompleted: '100' }, goals[0])]);
  G('counters-text-dur', [Object.assign({ minutesCompleted: 100 }, goals[0])], ev({ actualDurationMinutes: '40' }));
  G('partial', goals, ev({ completionRatio: 0.5 }));
  G('target', [Object.assign({ target: { units: 10, unitsPerHour: 3, completed: 2 } }, goals[0])]);
  G('target-cap', [Object.assign({ target: { units: 3, unitsPerHour: 3, completed: 2 } }, goals[0])]);
  G('target-no-rate', [Object.assign({ target: { units: 10, completed: 2 } }, goals[0])]);
  G('target-none', [Object.assign({ target: null }, goals[0])]);
  G('target-text', [Object.assign({ target: { units: '10', unitsPerHour: '3', completed: '2' } }, goals[0])]);
  G('several', [goals[0], { id: 'g2', title: 'Read', archetypeId: 'HABIT_READING' }, { id: 'g3', title: 'x' }], ev({ linkedGoalIds: ['g1', 'g3'] }));
  G('none', null);
  /* pruning */
  const fut = [session('i_1', T0, 30, { linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }),
    session('i_2', T0 + 3600, 30, { linkedGoalIds: [], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }),
    session('i_3', T0 + 7200, 30, { linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }, { questId: 'q2', reqId: 'r0' }] }),
    session('i_4', T0 + 9000, 30, { linkedGoalIds: [], isUserLocked: true, linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }),
    session('i_5', T0 + 9900, 30, { lifecycleState: 'IN_PROGRESS', linkedGoalIds: [], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }),
    session('i_6', T0 + 12600, 30, { linkedGoalIds: [], source: null, isUserLocked: 1, linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] }),
    session('i_7', T0 + 14400, 30, { linkedQuestRequirementIds: [] }),
    session('i_8', T0 + 15400, 30, { linkedGoalIds: [], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }, { questId: 'q1', reqId: 'r1' }] })];
  const done = quest('q1', 'fitness', { progressCount: 2 });
  r('prune', [ev({ instanceId: 'i_9' }), [done], [], fut], 'LED-16');
  r('prune-own', [ev(), [done], [], fut], 'LED-16');
  r('prune-already-completed', [ev({ instanceId: 'i_9', activityTypeTag: 'reading' }), [quest('q1', 'fitness', { status: 'COMPLETED' })], [], fut], 'LED-16');
  r('prune-legacy-id', [ev({ instanceId: 'i_9', activityTypeTag: 'reading' }), [{ questId: 'q1', status: 'COMPLETED' }], [], fut], 'LED-16');
  r('prune-daily', [ev({ instanceId: 'i_9' }), [quest('q1', 'fitness', { progressCount: 2, recurrence: 'DAILY' })], [], fut, { dayKey: '2026-11-02' }], 'LED-16');
  r('prune-none', [ev({ instanceId: 'i_9' }), [quest('q1', 'fitness')], [], fut], 'LED-16');
  r('prune-no-future', [ev({ instanceId: 'i_9' }), [done], [], null], 'LED-16');
  r('prune-adhoc-event', [ev({ instanceId: null, source: 'ADHOC' }), [done], [], fut], 'LED-16');
  r('prune-source-list', [ev({ instanceId: 'i_9' }), [done], [], [session('i_a', T0, 30, { linkedGoalIds: [], isUserLocked: true, source: ['x'], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] })]], 'LED-16');
  r('prune-goal-ids-text', [ev({ instanceId: 'i_9' }), [done], [], [session('i_a', T0, 30, { linkedGoalIds: 'g', linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] })]], 'LED-16');
  r('prune-goal-ids-empty-text', [ev({ instanceId: 'i_9' }), [done], [], [session('i_a', T0, 30, { linkedGoalIds: '', linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] })]], 'LED-16');
  r('prune-link-number', [ev({ instanceId: 'i_9' }), [{ id: 1, tag: 'fitness', status: 'COMPLETED' }], [], [session('i_a', T0, 30, { linkedGoalIds: [], linkedQuestRequirementIds: [{ questId: 1, reqId: 'r0' }, { questId: '1' }] })]], 'LED-16');
}

function missCases(add) {
  const inst = (more) => session('i_x', T0, 60, Object.assign({ bwcId: 'bwc-a' }, more || {}));
  const L = more => ledgerWith(more);
  const prefs = mode => (mode === undefined ? undefined : { consequenceMode: mode });
  const M = (tag, ledger, i, p, now, kind, rule) => add(tag, args(ledger, i, p === undefined ? null : p, now, kind), rule);
  for (const mode of ['LENIENT', 'MODERATE', 'HARDCORE_RPG', 'harsh', '']) for (const kind of ['MISS', 'SKIP', 'other', ABSENT])
    for (const tier of [1, 2, 3, 4, '3', '2', null]) for (const tokens of [0, 1])
      M([mode, kind, JSON.stringify(tier), tokens].join('/'), L({ restTokens: tokens }), inst({ tier }), prefs(mode), T0 + 7200, kind);
  for (const mode of ['MODERATE', 'HARDCORE_RPG']) {
    M(mode + '/tier-absent', L(), inst({ tier: undefined }), prefs(mode), T0, 'MISS');
    M(mode + '/already', L({ penalties: { [engineStable('pen', 'MISS_PENALTY:i_x')]: { instanceId: 'i_x', atEpoch: 1, consequence: null } } }), inst(), prefs(mode), T0, 'MISS');
    M(mode + '/already-falsy', L({ penalties: { [engineStable('pen', 'MISS_PENALTY:i_x')]: 0 } }), inst(), prefs(mode), T0, 'MISS');
    for (const c of [0, 1, 2, 3, 5, '2']) for (const g of [false, true]) for (const kind of ['MISS', 'SKIP'])
      M(mode + '/regroup:' + JSON.stringify(c) + '/' + g + '/' + kind, L({ consecutiveMisses: c, regroupActive: g, restTokens: 0 }), inst(), prefs(mode), T0, kind);
    M(mode + '/one-per-day', L({ consequencesByBwc: { 'bwc-a': 'pen_other' } }), inst(), prefs(mode), T0, 'MISS');
    M(mode + '/one-per-day-other', L({ consequencesByBwc: { 'bwc-b': 'pen_other' }, restTokens: 0 }), inst(), prefs(mode), T0, 'MISS');
    M(mode + '/one-per-day-falsy', L({ consequencesByBwc: { 'bwc-a': '' }, restTokens: 0 }), inst(), prefs(mode), T0, 'MISS');
    M(mode + '/bwc-absent', L({ restTokens: 0 }), inst({ bwcId: undefined }), prefs(mode), T0, 'MISS');
    M(mode + '/bwc-absent-taken', L({ restTokens: 0, consequencesByBwc: { undefined: 'pen_q' } }), inst({ bwcId: undefined }), prefs(mode), T0, 'MISS');
    for (const t of [1, 2, 3, '2', 0.5, -1]) M(mode + '/tokens:' + JSON.stringify(t), L({ restTokens: t }), inst(), prefs(mode), T0, 'MISS');
    for (const [key, streaks] of [['g1:strength', { 'g1:strength': 3 }], ['g1:strength', {}], ['g1:strength', { 'g1:strength': 0 }], ['g1:strength', { 'g1:strength': '3' }],
      [null, {}], ['', {}], [7, { 7: 2 }], ['k', { k: 0.5 }]])
      M(mode + '/streak:' + JSON.stringify([key, streaks]), L({ restTokens: 0, streaks }), inst({ spacingKey: key }), prefs(mode), T0, 'MISS');
    for (const now of [0, null, '5']) M(mode + '/now:' + JSON.stringify(now), L({ restTokens: 0 }), inst(), prefs(mode), now, 'MISS');
    for (const id of ['', 0, 5, null]) M(mode + '/id:' + JSON.stringify(id), L(), inst({ instanceId: id }), prefs(mode), T0, 'MISS');
  }
  M('prefs-absent', L(), inst(), undefined, T0, 'MISS');
  M('prefs-text', L(), inst(), 'MODERATE', T0, 'MISS');
  M('prefs-full', L({ restTokens: 0 }), inst(), { consequenceMode: 'MODERATE', intensityLevel: 'LIGHT', missGraceMinutes: 5 }, T0, 'MISS');
  /* three misses in a row: the third opens the regroup, and still pays (LG07) */
  let ledger = L({ restTokens: 0 });
  for (let k = 0; k < 5; k++) {
    M('row' + k, ledger, inst({ instanceId: 'i_row' + k, bwcId: 'bwc-' + k }), prefs('MODERATE'), T0 + k, 'MISS');
    ledger.consecutiveMisses++; if (ledger.consecutiveMisses >= 3) ledger.regroupActive = true;
  }
}
/* The engine's stableId, for building inputs (the engine loaded in node). */
let engineStable = null;

/* Hand streams: one host flow at a time on a plan. */
function handStreams(ps) {
  const out = [];
  const S = (tag, plan, steps, more, rule) => out.push({ tag, rule: rule || 'LED-22 LED-3 LED-4 LED-8 LED-13 LED-14 LED-18', spec: Object.assign({ tz: plan.zone, playerId: 'lm_3k9x0a7b2c1d',
    prefs: { consequenceMode: 'MODERATE' }, ledger: null, planned: plan.sessions, quests: plan.input.activeQuests || [], goals: plan.input.goals || [], steps }, more || {}) });
  for (const plan of ps) {
    const s = plan.sessions, at = i => s[i % s.length], id = i => at(i).instanceId, len = i => Math.round((at(i).endEpoch - at(i).startEpoch) / 60);
    const done = i => ({ op: 'complete', id: id(i), start: at(i).startEpoch, minutes: len(i), now: at(i).endEpoch + 60 });
    /* complete, repeat (a double tap), revoke, repeat (still refused), reopen and complete again (a second award by design) */
    S('repeat@' + plan.label, plan, [done(0), done(0), { op: 'revoke', ref: 0, reason: 'UNDONE_IN_LIFEMAX_LOG', now: at(0).endEpoch + 120 }, done(0), { op: 'reopen', id: id(0) },
      Object.assign(done(0), { now: at(0).endEpoch + 300 }), { op: 'has', ref: 0 }, { op: 'has', ref: 1 }]);
    /* five completions earn a token, a miss spends it, a late completion of the excused session refunds it */
    const five = [1, 2, 3, 4, 5].map(done);
    S('tokens@' + plan.label, plan, [{ op: 'tick', now: at(0).endEpoch + 3601 }, ...five, { op: 'complete', id: id(0), start: at(0).endEpoch + 7200, minutes: len(0), now: at(0).endEpoch + 9000 }]);
    S('tokens-spent@' + plan.label, plan, [...five, { op: 'tick', now: at(6).endEpoch + 3601 }, { op: 'complete', id: id(6), start: at(6).endEpoch + 4000, minutes: len(6), now: at(6).endEpoch + 9000 }], { prefs: { consequenceMode: 'HARDCORE_RPG' } });
    /* skips: voluntary, the same one twice, then misses in a row (regroup) */
    S('skips@' + plan.label, plan, [{ op: 'skip', id: id(1), now: at(1).startEpoch - 600 }, { op: 'skip', id: id(1), now: at(1).startEpoch - 500 }, { op: 'skip', id: id(2), now: at(2).startEpoch },
      { op: 'tick', now: at(8).endEpoch + 4000 }, done(9), { op: 'tick', now: at(12).endEpoch + 4000 }]);
    /* partial completions: residual tasks, streaks untouched, quest counts untouched */
    S('partial@' + plan.label, plan, [0, 1, 2, 3].map(i => ({ op: 'complete', id: id(i), start: at(i).startEpoch, minutes: Math.max(1, Math.floor(len(i) / 2)), now: at(i).endEpoch })));
    S('partial-ratio@' + plan.label, plan, [0, 1, 2, 3].map(i => ({ op: 'complete', id: id(i), start: at(i).startEpoch, minutes: Math.ceil(len(i) * 0.6), now: at(i).endEpoch })), { prefs: { consequenceMode: 'MODERATE', partialSatisfiedRatio: 0.5 } }, 'LED-5 LED-9 LED-14 LED-15 LED-22');
    /* ad-hoc logs: host activities and today's logs, repeated */
    S('adhoc@' + plan.label, plan, [{ op: 'adhoc', client: 'host:a1', tag: at(0).tag, title: 'Logged', minutes: 30, start: at(0).startEpoch, now: at(0).startEpoch + 1800 },
      { op: 'adhoc', client: 'host:a1', tag: 'reading', title: 'Again', minutes: 30, start: at(0).startEpoch, now: at(0).startEpoch + 1900 },
      { op: 'adhoc', client: 'ld-today:op-7', tag: at(3).tag, title: 'Today', minutes: 45, start: at(3).startEpoch, now: at(3).startEpoch + 3000 },
      { op: 'revoke', ref: 0, reason: 'UNDONE_IN_LIFEMAX_LOG', now: at(3).startEpoch + 3100 }, { op: 'adhoc', client: 'host:a1', tag: at(0).tag, title: 'Logged', minutes: 30, start: at(0).startEpoch, now: at(3).startEpoch + 3200 }]);
    /* the whole plan done in order, then the clock moved a week on */
    S('all@' + plan.label, plan, s.map((_, i) => done(i)).concat([{ op: 'tick', now: s[s.length - 1].endEpoch + 7 * 86400 }]));
    /* nothing done: every miss at once (the page takes 12 per tick) */
    S('all-missed@' + plan.label, plan, [{ op: 'tick', now: s[s.length - 1].endEpoch + 86400 }, { op: 'tick', now: s[s.length - 1].endEpoch + 2 * 86400 }, done(0), done(1)], { prefs: { consequenceMode: 'HARDCORE_RPG' } });
    /* the player id varies (event ids differ, the ledger's own player id wins over the host's) */
    S('players@' + plan.label, plan, [done(0), done(1)], { playerId: PLAYERS[ps.indexOf(plan) % PLAYERS.length] }, 'LED-1 LED-2 LED-3 LED-22');
    S('ledger-player@' + plan.label, plan, [done(0), done(1)], { playerId: 'host-now', ledger: ledgerWith({}, 'lm_original') }, 'LED-2 LED-3 LED-22');
  }
  return out;
}

/* A seeded stream on a plan: random host steps in time order. mode: 0 a lenient player, 1 moderate, 2 hardcore. */
function fuzzStream(seed, plan, mode) {
  const rnd = random(seed * 2654435761 + 17), pick = a => a[Math.floor(rnd() * a.length)], chance = p => rnd() < p;
  const s = plan.sessions.slice(0, 6 + Math.floor(rnd() * 30));
  if (!s.length) throw new Error('empty plan ' + plan.label);
  const prefs = { consequenceMode: ['LENIENT', 'MODERATE', 'HARDCORE_RPG'][mode], missGraceMinutes: pick([60, 60, 30, 0, 120, 15]) };
  if (chance(0.3)) prefs.partialSatisfiedRatio = pick([0.5, 0.6, 0.9, 1]);
  const steps = [], completed = [], clients = [];
  let now = s[0].startEpoch - 3600, refs = 0;
  for (let k = 0, n = 10 + Math.floor(rnd() * 30); k < n; k++) {
    now += Math.floor(rnd() * 5 * 3600);
    const i = Math.floor(rnd() * s.length), x = s[i], len = Math.round((x.endEpoch - x.startEpoch) / 60);
    const r = rnd();
    if (r < 0.35) {
      const ratio = pick([1, 1, 1, 0.8, 0.75, 0.74, 0.5, 0.2, 1.5]), minutes = Math.max(1, Math.round(len * ratio));
      const start = pick([x.startEpoch, x.startEpoch, x.startEpoch - 1800, x.startEpoch + 900, x.endEpoch + 600, x.startEpoch - minutes * 60]);
      steps.push({ op: 'complete', id: x.instanceId, start, minutes, now: Math.max(now, start + 60) }); completed.push(refs++);
    } else if (r < 0.45) {
      const client = clients.length && chance(0.3) ? pick(clients) : 'host:' + seed + '-' + k;
      clients.push(client);
      steps.push({ op: 'adhoc', client, tag: chance(0.7) ? x.tag : pick(['reading', 'fitness', 'language.spanish', 'study.deep', 'misc']), title: 'Log ' + k, minutes: pick([5, 15, 30, 45, 90]), start: now - 1800, now });
      refs++;
    } else if (r < 0.55 && completed.length) steps.push({ op: 'revoke', ref: pick(completed), reason: pick(['UNDONE_IN_LIFEMAX_LOG', '', 'HOST_UNDO']), now });
    else if (r < 0.62) steps.push({ op: 'reopen', id: x.instanceId });
    else if (r < 0.72) steps.push({ op: 'skip', id: x.instanceId, now });
    else if (r < 0.9) steps.push({ op: 'tick', now });
    else steps.push({ op: 'has', ref: Math.floor(rnd() * (refs + 2)) });
  }
  const players = PLAYERS.concat(['lm_' + seed.toString(36)]);
  return { tz: plan.zone, playerId: pick(players), prefs, ledger: chance(0.2) ? ledgerWith({ restTokens: pick([0, 1, 2, 3]), completionsSinceToken: pick([0, 3, 4]), streaks: {} }, pick(players)) : null,
    planned: s, quests: plan.input.activeQuests || [], goals: plan.input.goals || [], steps };
}

/* A plan of sessions laid out by the local clock across a clock change: lengths by the wall clock differ from the real ones. */
function gapStream(plan) {
  const c = plan.change, local = new Date((c.at + c.before * 60) * 1000), y = local.getUTCFullYear(), mo = local.getUTCMonth() + 1, d = local.getUTCDate();
  const h = local.getUTCHours() * 60 + local.getUTCMinutes(), zone = plan.zone, at = (dd, m) => localInstant(zone, y, mo, dd, m);
  const sessions = [];
  for (const [k, from, to] of [[0, h - 60, h + 60], [1, h - 30, h + 30], [2, h - 120, h - 60], [3, h + 60, h + 120], [4, 23 * 60 + 30, 24 * 60 + 30], [5, h - 15, h + 15]]) {
    const start = at(d, from), end = at(d, to);
    sessions.push(session('i_g' + k, start, 60, { endEpoch: end, bwcId: 'bwc-' + d, tier: 3 + (k % 2), spacingKey: 'g1:s' + (k % 3) }));
  }
  const steps = sessions.map((x, k) => (k % 3 === 2 ? { op: 'tick', now: x.endEpoch + 3601 } : { op: 'complete', id: x.instanceId, start: x.startEpoch, minutes: 60, now: x.endEpoch + 60 }));
  steps.push({ op: 'adhoc', client: 'host:gap', tag: 'fitness', title: 'Late', minutes: 30, start: at(d, h - 5), now: at(d, h + 90) });
  return { tz: zone, playerId: 'lm_dst', prefs: { consequenceMode: 'MODERATE' }, ledger: null, planned: sessions, quests: [quest('q1', 'fitness', { recurrence: 'DAILY', requiredCount: 2 })],
    goals: [{ id: 'g1', title: 'Lift', archetypeId: 'FITNESS_HYPERTROPHY' }], steps };
}

/* Shapes the page's writers never produce: where the page throws, and where native refuses (the deviation set the native test pins). */
function malformedCases(engine, add) {
  const base = session('i_r1', T0, 60), ci = more => Object.assign({ ledger: ledgerWith(), instance: base, actualStartEpoch: T0, actualDurationMinutes: 60, nowEpoch: T0 + 3600 }, more);
  const rec = (tag, c) => add('record:' + tag, 'recordCompletion', [c]);
  rec('ci-null', null); rec('ci-text', 'x'); rec('ci-list', []);
  for (const [k, v] of [['events', null], ['events', ABSENT], ['events', 'abc'], ['events', 5], ['events', []], ['order', null], ['order', 'abc'], ['order', {}],
    ['streaks', null], ['streaks', 'x'], ['streaks', []], ['restTokens', null], ['restTokens', ABSENT], ['completionsSinceToken', ABSENT], ['completionsSinceToken', '4'],
    ['consecutiveMisses', 'x'], ['regroupActive', 'yes'], ['playerId', ABSENT], ['playerId', null], ['playerId', { a: 1 }], ['schemaVersion', 99], ['extra', { keep: [1, 2] }]]) {
    const L = ledgerWith(); if (v === ABSENT) delete L[k]; else L[k] = v;
    rec('ledger.' + k + '=' + JSON.stringify(v === ABSENT ? 'absent' : v), ci({ ledger: L, instance: Object.assign({}, base, { spacingKey: 'k' }) }));
  }
  rec('ledger-text', ci({ ledger: 'abc' })); rec('ledger-list', ci({ ledger: [1] })); rec('ledger-true', ci({ ledger: true }));
  rec('instance-text', ci({ instance: 'abc', clientEventId: 'c1' })); rec('instance-number', ci({ instance: 5 })); rec('instance-list', ci({ instance: ['a'], clientEventId: 'c' }));
  for (const [k, v] of [['startEpoch', String(T0)], ['endEpoch', String(T0 + 3600)], ['linkedGoalIds', 'g1'], ['linkedGoalIds', 5], ['linkedGoalIds', { a: 1 }],
    ['linkedQuestRequirementIds', 'q'], ['linkedQuestRequirementIds', [null]], ['linkedQuestRequirementIds', { a: 1 }], ['title', { t: 1 }], ['IP', 'x'], ['tag', ['fitness', 'x']]])
    rec('instance.' + k + '=' + JSON.stringify(v), ci({ instance: Object.assign({}, base, { [k]: v }) }));
  rec('start-text', ci({ actualStartEpoch: String(T0) }));
  rec('start-text-adhoc', { ledger: ledgerWith(), adhoc: { tag: 'x' }, clientEventId: 'c', actualStartEpoch: 'soon', actualDurationMinutes: 5, nowEpoch: T0 });
  rec('residual-links-text', ci({ instance: Object.assign({}, base, { tier: 1, linkedQuestRequirementIds: 'q' }), actualDurationMinutes: 10 }));
  rec('residual-links-absent', ci({ instance: Object.assign({}, base, { tier: 1, linkedQuestRequirementIds: undefined }), actualDurationMinutes: 10 }));
  const L1 = ledgerWith({ events: { ev_a: { recordedAtEpoch: 1 } }, order: ['ev_a'] });
  for (const [tag, ledger] of [['null', null], ['text', 'abc'], ['events-null', ledgerWith({ events: null })], ['revoked-text', Object.assign(copy(L1), { revoked: 'x' })],
    ['revoked-list', Object.assign(copy(L1), { revoked: [] })], ['revoked-number', Object.assign(copy(L1), { revoked: 3 })]])
    add('revoke:' + tag, 'revokeEvent', [ledger, 'ev_a', 'R', 5]);
  for (const [tag, ledger] of [['null', null], ['events-absent', { archivedIds: [] }], ['archived-number', ledgerWith({ archivedIds: 4 })], ['archived-object', ledgerWith({ archivedIds: {} })],
    ['archived-true', ledgerWith({ archivedIds: true })], ['events-text', ledgerWith({ events: 'ev_q' })], ['events-list', ledgerWith({ events: ['a'] })]])
    add('has:' + tag, 'ledgerHas', [ledger, 'ev_q']);
  const N = T0 + 1000 * 86400;
  for (const [tag, ledger] of [['order-absent', { events: {} }], ['order-long-text', { order: 'x'.repeat(2000), events: {} }], ['order-object', { order: { length: 5000 }, events: {} }],
    ['archived-text', Object.assign(bigLedger(2000, 1), { archivedIds: 'x' })], ['events-null', Object.assign(bigLedger(2000, 1), { events: null })]])
    add('compact:' + tag, 'compactLedger', [ledger, N]);
  const ev = { eventId: 'ev_1', instanceId: 'i_1', activityTypeTag: 'fitness', linkedGoalIds: ['g1'], linkedQuestRequirementIds: [], actualDurationMinutes: 40, completionRatio: 1, source: 'SCHEDULE' };
  const q = quest('q1', 'fitness', { progressCount: 2 }), fut = [session('i_2', T0, 30, { linkedGoalIds: [], linkedQuestRequirementIds: [{ questId: 'q1', reqId: 'r0' }] })];
  for (const [tag, a] of [['quests-text', [ev, 'q', [], []]], ['quest-null', [ev, [null], [], []]], ['quest-text', [ev, ['abc'], [], []]], ['quest-list', [ev, [['x']], [], []]],
    ['quest-applied-number', [ev, [Object.assign({ appliedEventIds: 5 }, q)], [], []]], ['quest-applied-object', [ev, [Object.assign({ appliedEventIds: { a: 1 } }, q)], [], []]],
    ['quest-req-null', [ev, [quest('q1', 'fitness', { requirements: [null] })], [], []]], ['quest-req-text', [ev, [quest('q1', 'fitness', { requirements: ['abc'] })], [], []]],
    ['event-links-text', [Object.assign({}, ev, { linkedQuestRequirementIds: 'x' }), [q], [], []]], ['event-links-null-item', [Object.assign({}, ev, { linkedQuestRequirementIds: [null] }), [q], [], []]],
    ['event-goals-text', [Object.assign({}, ev, { linkedGoalIds: 'g1' }), [], [{ id: 'g1' }], []]], ['event-goals-absent', [Object.assign({}, ev, { linkedGoalIds: undefined }), [], [{ id: 'g1' }], []]],
    ['goal-null', [ev, [], [null], []]], ['goal-text', [ev, [], ['g1'], []]], ['goal-applied-number', [ev, [], [{ id: 'g1', appliedEventIds: 3 }], []]],
    ['goals-text', [ev, [], 'g', []]], ['future-null-item', [ev, [q], [], [null]]], ['future-links-absent', [ev, [q], [], [Object.assign({}, fut[0], { linkedQuestRequirementIds: undefined })]]],
    ['future-link-null', [ev, [q], [], [Object.assign({}, fut[0], { linkedQuestRequirementIds: [null] })]]], ['future-goals-absent', [ev, [q], [], [Object.assign({}, fut[0], { linkedGoalIds: undefined })]]],
    ['future-source-text', [ev, [q], [], [Object.assign({}, fut[0], { isUserLocked: true, source: 'abc' })]]], ['future-text', [ev, [q], [], 'x']],
    ['event-null', [null, [], [], []]]])
    add('reconcile:' + tag, 'reconcileQuestProgress', a);
  for (const [tag, a] of [['null-item', [[null], T0, 60]], ['text', ['abc', T0, 60]], ['object', [{ a: 1 }, T0, 60]]]) add('misses:' + tag, 'detectMisses', a);
  const mi = session('i_x', T0, 60, { bwcId: 'b' }), mo = { consequenceMode: 'MODERATE' };
  for (const [tag, a] of [['inst-null', [ledgerWith(), null, mo, T0, 'MISS']], ['ledger-null', [null, mi, mo, T0, 'MISS']], ['penalties-absent', [Object.assign(ledgerWith(), { penalties: undefined }), mi, mo, T0, 'MISS']],
    ['penalties-text', [Object.assign(ledgerWith(), { penalties: 'p' }), mi, mo, T0, 'MISS']], ['penalties-text-lenient', [Object.assign(ledgerWith(), { penalties: 'p' }), mi, {}, T0, 'MISS']],
    ['bwc-map-absent', [Object.assign(ledgerWith(), { consequencesByBwc: undefined }), mi, mo, T0, 'MISS']], ['streaks-absent', [Object.assign(ledgerWith({ restTokens: 0 }), { streaks: undefined }), mi, mo, T0, 'MISS']],
    ['streaks-text-key', [ledgerWith({ restTokens: 0, streaks: { 'g1:strength': 'x' } }), mi, mo, T0, 'MISS']], ['tokens-text', [ledgerWith({ restTokens: 'x' }), mi, mo, T0, 'MISS']],
    ['misses-text', [ledgerWith({ consecutiveMisses: '2' }), mi, mo, T0, 'MISS']], ['bwc-object', [ledgerWith({ restTokens: 0 }), Object.assign({}, mi, { bwcId: { a: 1 } }), mo, T0, 'MISS']]])
    add('miss:' + tag, 'applyMissConsequence', a);
}

module.exports = { defineCases, FAMILIES, ZONES, STATES, PROTO };
