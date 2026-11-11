/* The timer golden cases for Life Director (WP-LD10): Today.timer (start, pause, resume, reset, replace, finish, cancel, autoFinish), the
   timer's once-a-second check (focusTick) that finishes it on its own, the clock as the page reads it (Date.now: wall time in
   milliseconds, set back, jumped forward, across a clock change), the app closed for hours and opened again, a timer on another session,
   and the instants the page's tick and timer change something (the wake family, for the native wake calculator). Run by
   tools/ld_host_vm.js runHostCases on logic.js and recorded by tools/gen_ld_timer_golden.js. Rules are LIFE_DIRECTOR_TIMER_SPEC.md's (TM-n).

   A case is {id, family, rule, start, steps} as in tools/ld_host_cases.js. Most cases start from a plan made by hand here (handPlan:
   four sessions at known local times, today 10:00 and 18:00, tomorrow 10:00 and in three days), so every instant a case steps to is
   known without the engine; a few make their plan with a first Re-plan. A step names the running timer's start with {$ref: 'focus'}
   (milliseconds). No Intl: instants come from Date.UTC and the clock-change table (tools/ld_kernel_cases.js). */
'use strict';
const { localInstant, random } = require('./ld_placement_cases.js');
const { base, ZONES } = require('./ld_host_cases.js');

const FAMILIES = ['start', 'pause', 'reset', 'replace', 'finish', 'cancel', 'auto', 'tick', 'clock', 'wake', 'fuzz'];
const MIN = 60, HOUR = 3600, DAY = 86400;
/* the local instant (epoch seconds) of a day of November 2026 (or another month) at minute `min` */
const local = (zone, d, min, mo = 11, y = 2026) => localInstant(zone, y, mo, d, min);
const P = (n, field, plus) => Object.assign({ $ref: 'planned', n }, field ? { field } : {}, plus ? { plus } : {});
const F = plus => Object.assign({ $ref: 'focus', n: 0 }, plus ? { plus } : {});
const TM = (payload, at) => ({ at: at === undefined ? { plus: 60 } : at, op: 'today', name: 'timer', payload });
const T = (name, payload, at) => ({ at: at === undefined ? { plus: 60 } : at, op: 'today', name, payload });
const FT = at => ({ at, op: 'focusTick' });
const focus = (focusValue, at) => ({ at: at === undefined ? { plus: 1 } : at, op: 'focus', focus: focusValue });
const storage = (mode, at) => ({ at: at === undefined ? { plus: 1 } : at, op: 'storage', mode });

/* A session made by hand: id, local day and minute, minutes long; with an explanation, as every session the engine places has one (the
   native repair explains a carried session again only when it has none, and cannot for a task the plan does not hold). */
function session(zone, id, d, min, minutes, mo, y, more) {
  const start = local(zone, d, min, mo, y);
  return Object.assign({ instanceId: id, taskId: 't-' + id, templateId: 'g-0:strength', title: 'Session ' + id, tag: 'fitness.strength', kind: 'GOAL', goalId: 'g-0',
    linkedGoalIds: ['g-0'], linkedQuestRequirementIds: [], startEpoch: start, endEpoch: start + minutes * MIN, durationSlots: minutes / 15, tier: 3, IP: 4, IC: 1,
    spacingKey: 'g-0:strength', lifecycleState: 'PLANNED', isUserLocked: false, revision: 0,
    explanation: { tier: 3, summary: 'Scheduled because it serves your goals.', factors: [] } }, more || {});
}
/* A plan made by hand for day d: s-a today 10:00 (45 min), s-b today 18:00 (30), s-c tomorrow 10:00 (60), s-d in three days 10:00
   (60); the horizon from today's midnight, `days` long; extra sessions appended. */
function handPlan(zone, o = {}) {
  const d = o.d || 4, mo = o.mo || 11, y = o.y || 2026;
  const list = [session(zone, 's-a', d, 600, 45, mo, y), session(zone, 's-b', d, 1080, 30, mo, y), session(zone, 's-c', d + 1, 600, 60, mo, y),
    session(zone, 's-d', d + 3, 600, 60, mo, y)].concat(o.extra || []);
  list.sort((a, b) => a.startEpoch - b.startEpoch);
  const h0 = local(zone, d, 0, mo, y);
  return { horizonStartEpoch: h0, horizonEndEpoch: o.horizonEnd || h0 + (o.days || 7) * DAY, timezone: zone, scheduledInstances: list, historyInstances: [] };
}
/* the saved state over a hand plan; the week key is the plan's (Monday 2 November 2026 unless given) */
const handState = (zone, o = {}) => JSON.stringify(base(zone, { prefs: o.prefs, ledger: o.ledger, ui: o.ui,
  more: Object.assign({ schedule: handPlan(zone, o), lastGeneratedWeekKey: o.weekKey || '2026-11-02' }, o.more || {}) }));
const A = (zone, plus = 0) => (local(zone, 4, 600) + plus) * 1000; // s-a's start (ms), plus seconds
const B = (zone, plus = 0) => (local(zone, 4, 1080) + plus) * 1000; // s-b's start
const C = (zone, plus = 0) => (local(zone, 5, 600) + plus) * 1000; // s-c's start

function defineCases() {
  const cases = [], ids = new Set();
  const add = (family, rule, tag, start, steps) => {
    let id = family + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, start: Object.assign({ storage: 'ok', host: {} }, start), steps });
  };
  /* a case on the hand plan, booted a minute after s-a starts (or at o.at) */
  const hand = (family, rule, tag, zone, steps, o = {}) => add(family, rule, tag, { at: o.at || A(zone, 60), saved: handState(zone, o), host: o.host }, steps);
  /* a case on a fresh plan from the engine: boot at 06:30 on Wednesday, Re-plan a second later */
  const engine = (family, rule, tag, zone, steps, o = {}) => add(family, rule, tag, { at: o.at || local(zone, 4, 390) * 1000,
    saved: JSON.stringify(base(zone, o)), host: o.host }, [T('replan', {}, { plus: 1 })].concat(steps));
  const start = (id, at) => TM({ act: 'start', id }, at);
  const act = (a, id, s, at) => TM(Object.assign({ act: a }, id === undefined ? {} : { id }, s === undefined ? {} : { start: s }), at);

  /* ---- start (TM-10 to TM-14) ---- */
  ZONES.forEach(z => {
    hand('start', 'TM-10 TM-11', 'basic@' + z, z, [start('s-a', { plus: 1 }), start('s-a'), start('s-b'), start('s-c'), start('nope'), TM({ act: 'start' }), start(7)]);
    hand('start', 'TM-12', 'orphan@' + z, z, [focus({ id: 'ghost', start: A(z, -7200), paused: null, pausedMs: 0, plannedMs: 60000 }), start('s-a'), start('s-b')]);
    hand('start', 'TM-12', 'orphan-paused@' + z, z, [focus({ id: 'ghost', start: A(z, -7200), paused: A(z, -7000), pausedMs: 0 }), start('s-b')]);
    /* s-e at 02:00 on Friday: from 20:00 on Thursday, not a second before (the same-day rule cannot reach it) */
    const e0 = local(z, 6, 120) * 1000;
    hand('start', 'TM-11', 'six-hours@' + z, z, [start('s-e', e0 - (6 * HOUR + 1) * 1000), start('s-e', e0 - 6 * HOUR * 1000), start('s-c', C(z, -6 * HOUR - 1))],
      { extra: [session(z, 's-e', 6, 120, 60)] });
    hand('start', 'TM-13', 'not-saved@' + z, z, [storage('quota'), start('s-a'), storage('ok'), start('s-a')]);
    hand('start', 'TM-11', 'not-planned@' + z, z, [start('s-x'), start('s-a')], { extra: [session(z, 's-x', 4, 660, 30, 11, 2026, { lifecycleState: 'IN_PROGRESS' })] });
  });
  ZONES.slice(0, 4).forEach(z => {
    /* a numeric id: the plan finds it as text, the timer keeps the number, so Skip's running check and autoFinish's id check miss it (LT04) */
    hand('start', 'TM-14', 'numeric-id@' + z, z, [start(7), act('autoFinish', '7', F(), { plus: 1800 }), T('skip', { id: '7' }), FT({ plus: 1 }),
      act('autoFinish', 7, F(), { plus: 1 }), start('s-b')], { extra: [session(z, '7', 4, 600, 30)] });
    hand('start', 'TM-14', 'numeric-auto@' + z, z, [start(7), act('autoFinish', 7, F(), { plus: 1800 })], { extra: [session(z, '7', 4, 600, 30)] });
    add('start', 'TM-11', 'no-plan@' + z, { at: A(z, 60), saved: JSON.stringify(base(z)) }, [start('s-a'), act('pause'), act('finish', 's-a', 1)]);
  });

  /* ---- pause and resume (TM-20 to TM-22) ---- */
  ZONES.forEach(z => {
    hand('pause', 'TM-20 TM-21', 'pause-resume@' + z, z, [start('s-a', { plus: 1 }), act('pause', undefined, undefined, { plus: 600 }), act('pause'),
      act('resume', undefined, undefined, { plus: 300 }), act('resume'), act('finish', 's-a', F(), { plus: 600 })]);
    hand('pause', 'TM-20 TM-21', 'no-timer@' + z, z, [act('pause'), act('resume')]);
    hand('pause', 'TM-22', 'clock-back@' + z, z, [start('s-a', A(z, 120)), act('pause', undefined, undefined, A(z, 720)), act('resume', undefined, undefined, A(z, 420)),
      act('finish', 's-a', F(), A(z, 720))]);
  });
  ZONES.slice(0, 4).forEach(z => {
    hand('pause', 'TM-22', 'pause-before-start@' + z, z, [start('s-a', A(z, 300)), act('pause', undefined, undefined, A(z, 180)), FT(A(z, 240)),
      act('resume', undefined, undefined, A(z, 360)), act('finish', 's-a', F(), A(z, 960))]);
    hand('pause', 'TM-20', 'any-id@' + z, z, [start('s-a', { plus: 1 }), act('pause', 'zz', 5), act('resume', 'zz', 5)]);
    hand('pause', 'TM-20', 'not-saved@' + z, z, [start('s-a', { plus: 1 }), storage('quota'), act('pause'), act('resume'), storage('ok'), act('pause')]);
  });

  /* ---- reset (TM-25, TM-26) ---- */
  ZONES.forEach(z => {
    hand('reset', 'TM-25', 'reset@' + z, z, [start('s-a', { plus: 1 }), act('reset', 's-a', F(), { plus: 600 }), act('resume'), act('finish', 's-a', F(), { plus: 600 })]);
    hand('reset', 'TM-25', 'mismatch@' + z, z, [act('reset', 's-a', 1), start('s-a', { plus: 1 }), act('reset', 's-a', 1), act('reset', 's-b', F()), act('reset', 's-a', F(1))]);
  });
  ZONES.slice(0, 4).forEach(z => {
    hand('reset', 'TM-25', 'clock-back@' + z, z, [start('s-a', A(z, 600)), act('reset', 's-a', F(), A(z, 300)), act('resume', undefined, undefined, A(z, 400))]);
    hand('reset', 'TM-26', 'not-saved@' + z, z, [start('s-a', { plus: 1 }), storage('quota'), act('reset', 's-a', F(), { plus: 600 }), storage('ok'),
      act('reset', 's-a', F())]);
  });

  /* ---- replace (TM-30 to TM-32) ---- */
  ZONES.forEach(z => {
    hand('replace', 'TM-30', 'replace@' + z, z, [start('s-a', { plus: 1 }), act('replace', 's-b', F(), { plus: 600 }), act('replace', 's-b', A(z, 61) * 1),
      act('replace', 's-a', A(z, 61) * 1), act('finish', 's-b', F(), { plus: 900 })]);
    hand('replace', 'TM-31', 'refusals@' + z, z, [act('replace', 's-a', 5), start('s-a', { plus: 1 }), act('replace', 'nope', F()), act('replace', 's-c', F()),
      act('replace', 's-a', F()), act('replace', 's-x', F())], { extra: [session(z, 's-x', 4, 660, 30, 11, 2026, { lifecycleState: 'IN_PROGRESS' })] });
  });
  ZONES.slice(0, 4).forEach(z => {
    hand('replace', 'TM-32', 'not-saved@' + z, z, [start('s-a', { plus: 1 }), storage('quota'), act('replace', 's-b', F()), storage('ok'), act('replace', 's-b', F())]);
    hand('replace', 'TM-30', 'clock-back@' + z, z, [start('s-a', A(z, 600)), act('replace', 's-b', F(), A(z, 300))]);
    hand('replace', 'TM-30', 'orphan@' + z, z, [focus({ id: 'ghost', start: A(z, -60), paused: null, pausedMs: 0, plannedMs: 60000 }),
      act('replace', 's-a', A(z, -60))]);
  });

  /* ---- finish (TM-40 to TM-43) ---- */
  ZONES.forEach(z => {
    hand('finish', 'TM-40 TM-41', 'finish@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', F(), { plus: 1500 }), act('finish', 's-a', 1), act('finish', 's-b', 1),
      act('cancel', 's-a', 1)]);
    hand('finish', 'TM-41', 'mismatch@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', 123), act('finish'), act('finish', 's-a', F(1))]);
    hand('finish', 'TM-40', 'short@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', F(), { plus: 20 })]);
    hand('finish', 'TM-40', 'paused@' + z, z, [start('s-a', { plus: 1 }), act('pause', undefined, undefined, { plus: 600 }), act('finish', 's-a', F(), { plus: 3000 })]);
    hand('finish', 'TM-42', 'gone@' + z, z, [focus({ id: 's-gone', start: A(z, -600), paused: null, pausedMs: 0, plannedMs: 1800000 }), act('finish', 's-gone', A(z, -600))]);
  });
  ZONES.slice(0, 4).forEach(z => {
    ['failed', 'offline', 'already'].forEach(mode => hand('finish', 'TM-40', 'award-' + mode + '@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', F(), { plus: 2400 })],
      { host: { award: mode } }));
    /* a session put back on the plan after its completion was recorded: the ledger refuses the second one */
    hand('finish', 'TM-43', 'duplicate@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', F(), { plus: 600 }), { at: { plus: 5 }, op: 'applyOutput', out: handPlan(z), label: 'again' },
      start('s-a'), act('finish', 's-a', F(), { plus: 600 }), start('s-a')]);
    hand('finish', 'TM-40', 'not-saved@' + z, z, [start('s-a', { plus: 1 }), storage('quota'), act('finish', 's-a', F(), { plus: 1200 })]);
    hand('finish', 'TM-40', 'late@' + z, z, [start('s-a', A(z, 3600)), act('finish', 's-a', F(), A(z, 4800))]);
    engine('finish', 'TM-40', 'engine@' + z, z, [start(P(0), P(0, 'startEpoch', 60)), act('finish', P(0), F(), { plus: 600 })],
      { host: { quests: [{ id: 'q-lift', title: 'Quest q-lift', tier: 'CORE', tag: 'fitness.strength', status: 'ACTIVE', requiredCount: 1, progressCount: 0,
        schedulingMode: 'SESSIONS', sessionMinutes: 30, minSessionMinutes: 30, bounty: { xp: 20 }, source: 'lifemax', hostRef: { questId: 'q-lift', key: 'q-lift_w', cadence: 'weekly', done: false } }] } });
    engine('finish', 'TM-40', 'replan-while-running@' + z, z, [start(P(0), P(0, 'startEpoch', 60)), T('replan', {}, { plus: 300 }), act('finish', P(0), F(), { plus: 1200 })]);
  });

  /* ---- cancel and unknown acts (TM-4, TM-45) ---- */
  ZONES.forEach(z => {
    hand('cancel', 'TM-45', 'cancel@' + z, z, [start('s-a', { plus: 1 }), act('cancel', 's-a', F(), { plus: 300 }), act('cancel', 's-a', A(z, 61)), start('s-b'),
      act('cancel', 's-b', 99), act('cancel', 'zz', F())]);
    hand('cancel', 'TM-4', 'unknown@' + z, z, [TM({ act: 'explode', id: 's-a' }), TM({}), TM({ act: 'Start', id: 's-a' })]);
  });
  ZONES.slice(0, 4).forEach(z => hand('cancel', 'TM-45', 'not-saved@' + z, z, [start('s-a', { plus: 1 }), storage('quota'), act('cancel', 's-a', F())]));

  /* ---- autoFinish (TM-50 to TM-53) ---- */
  ZONES.forEach(z => {
    hand('auto', 'TM-50 TM-51', 'auto@' + z, z, [start('s-a', A(z, 60)), act('autoFinish', 's-a', F(), A(z, 660)), act('autoFinish', 's-a', F(), A(z, 60 + 2700) - 1),
      act('autoFinish', 's-a', A(z, 60), A(z, 60 + 2700)), act('autoFinish', 's-a', A(z, 60))]);
    hand('auto', 'TM-50', 'paused@' + z, z, [start('s-a', { plus: 1 }), act('pause'), act('autoFinish', 's-a', F(), { plus: 4 * HOUR })]);
    hand('auto', 'TM-50', 'wrong@' + z, z, [start('s-a', A(z, 60)), act('autoFinish', 's-a', 5, A(z, 3600)), act('autoFinish', 's-b', F()), act('autoFinish', 's-a', F(1))]);
    hand('auto', 'TM-52', 'gone@' + z, z, [focus({ id: 's-gone', start: A(z, -3600), paused: null, pausedMs: 0, plannedMs: 60000 }), act('autoFinish', 's-gone', A(z, -3600)),
      act('autoFinish', 's-gone', A(z, -3600))]);
    hand('auto', 'TM-51', 'hours-later@' + z, z, [start('s-a', A(z, 60)), act('autoFinish', 's-a', F(), A(z, 9 * HOUR))]);
  });
  ZONES.slice(0, 4).forEach(z => {
    hand('auto', 'TM-51', 'no-planned-ms@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: null, pausedMs: 0 }), act('autoFinish', 's-a', A(z, 0), A(z, 2700) - 1),
      act('autoFinish', 's-a', A(z, 0), A(z, 2700))]);
    hand('auto', 'TM-51', 'planned-ms-zero@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: null, pausedMs: 0, plannedMs: 0 }), act('autoFinish', 's-a', A(z, 0), A(z, 2700))]);
    hand('auto', 'TM-51', 'stale-planned-ms@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: null, pausedMs: 30000, plannedMs: 600000 }),
      act('autoFinish', 's-a', A(z, 0), A(z, 630) - 1), act('autoFinish', 's-a', A(z, 0), A(z, 630))]);
    hand('auto', 'TM-53', 'duplicate@' + z, z, [start('s-a', { plus: 1 }), act('finish', 's-a', F(), { plus: 600 }), { at: { plus: 5 }, op: 'applyOutput', out: handPlan(z), label: 'again' },
      start('s-a'), act('autoFinish', 's-a', F(), { plus: 2700 }), FT({ plus: 1 })]);
    hand('auto', 'TM-51', 'early@' + z, z, [start('s-b', B(z, -5 * HOUR - 1800)), act('autoFinish', 's-b', F(), B(z, -5 * HOUR))]);
    hand('auto', 'TM-51', 'not-saved@' + z, z, [start('s-a', A(z, 60)), storage('quota'), act('autoFinish', 's-a', F(), A(z, 60 + 2700))]);
  });

  /* ---- the once-a-second check (TM-60, TM-61) ---- */
  ZONES.forEach(z => {
    hand('tick', 'TM-60', 'tick@' + z, z, [focus({ id: 's-a', start: A(z, 30), paused: null, pausedMs: 0, plannedMs: 2700000 }), FT(A(z, 30 + 2700) - 1), FT(A(z, 30 + 2700)), FT({ plus: 1 })]);
    hand('tick', 'TM-60', 'paused@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: A(z, 30), pausedMs: 0, plannedMs: 2700000 }), FT(A(z, 4 * HOUR))]);
    hand('tick', 'TM-61', 'history-target@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: null, pausedMs: 0 }), { at: { plus: 5 }, op: 'finish', id: 's-a', minutes: 5, opt: {} },
      FT(A(z, 2700)), FT({ plus: 1 }), start('s-b', { plus: 1 })]);
  });
  ZONES.slice(0, 4).forEach(z => {
    hand('tick', 'TM-61', 'unknown@' + z, z, [focus({ id: 'ghost', start: A(z, 0), paused: null, pausedMs: 0 }), FT(A(z, 5 * HOUR)),
      focus({ id: 'ghost', start: A(z, 0), paused: null, pausedMs: 0, plannedMs: 60000 }), FT({ plus: 1 }), FT({ plus: 1 })]);
    hand('tick', 'TM-60', 'no-timer@' + z, z, [FT({ plus: 1 })]);
    hand('tick', 'TM-60', 'not-saved@' + z, z, [focus({ id: 's-a', start: A(z, 0), paused: null, pausedMs: 0, plannedMs: 2700000 }), storage('quota'), FT(A(z, 2700))]);
  });

  /* ---- the clock as the page reads it (TM-70 to TM-72) ---- */
  ZONES.forEach(z => {
    hand('clock', 'TM-70', 'back-before-start@' + z, z, [start('s-a', A(z, 600)), act('finish', 's-a', F(), A(z, -3000))]);
    hand('clock', 'TM-70', 'forward-jump@' + z, z, [start('s-a', A(z, 60)), FT(A(z, 5 * HOUR))]);
    /* the app closed while the timer runs and opened two hours after s-a ended: the page's mount ticks first (misses), then the clock */
    hand('clock', 'TM-71', 'reopen-tick-first@' + z, z, [start('s-a', A(z, 60)), { at: A(z, 2700 + 2 * HOUR), op: 'tick' }, FT({ plus: 1 }), FT({ plus: 1 }),
      start(P(0), P(0, 'startEpoch', 60))]);
    hand('clock', 'TM-71', 'reopen-glance-first@' + z, z, [start('s-a', A(z, 60)), FT(A(z, 2700 + 2 * HOUR)),
      /* the recording host does not log what it awards, so a connected tick would take the completion back as undone in LifeMax */
      { at: { plus: 1 }, op: 'host', host: { connected: false } }, { at: { plus: 1 }, op: 'tick' }]);
    hand('clock', 'TM-72', 'missed-while-running@' + z, z, [start('s-a', A(z, -1800)), { at: A(z, 2700 + HOUR + 1), op: 'tick' }, FT({ plus: 1 }), act('finish', 's-a', F())]);
  });
  /* clock-change nights: a session over the change, timed in real milliseconds (New York and London fall back, Sydney and Santiago spring forward) */
  [['America/New_York', 1, 11], ['Europe/London', 25, 10], ['Australia/Sydney', 4, 10], ['America/Santiago', 6, 9]].forEach(([z, d, mo]) => {
    const night = { d, mo, y: 2026, extra: [session(z, 's-n', d, 60, 120, mo, 2026)] };
    const s0 = local(z, d, 75, mo) * 1000;
    add('clock', 'TM-70', 'dst-night@' + z, { at: s0 - 60000, saved: handState(z, night) }, [start('s-n', s0), FT(s0 + 3600000), act('finish', 's-n', s0, s0 + 90 * 60000)]);
    add('clock', 'TM-70', 'dst-auto@' + z, { at: s0 - 60000, saved: handState(z, night) }, [start('s-n', s0), FT(s0 + 120 * 60000 - 1), FT(s0 + 120 * 60000)]);
  });

  /* ---- wake: the instants the page's timer and tick change something (TM-80 to TM-84), probed a millisecond before and at ---- */
  ZONES.forEach((z, zi) => {
    const sA = local(z, 4, 600), endA = sA + 45 * MIN;
    hand('wake', 'TM-80', 'timer@' + z, z, [focus({ id: 's-a', start: A(z, 15), paused: null, pausedMs: 120000 + zi * 1000, plannedMs: 2700000 }),
      FT(A(z, 15 + 2700 + 120 + zi) - 1), FT(A(z, 15 + 2700 + 120 + zi))]);
    const grace = [undefined, 15, 240, 30][zi % 4];
    hand('wake', 'TM-81', 'miss@' + z, z, [{ at: (endA + (grace || 60) * MIN + 1) * 1000 - 1, op: 'processMisses' }, { at: (endA + (grace || 60) * MIN + 1) * 1000, op: 'processMisses' }],
      { prefs: grace ? { missGraceMinutes: grace } : {} });
    const monday = local(z, 9, 0);
    hand('wake', 'TM-82', 'week@' + z, z, [{ at: monday * 1000 - 1, op: 'maybeRollover' }, { at: monday * 1000, op: 'maybeRollover' }], { days: 14 });
    const hEnd = local(z, 4, 0) + 3 * DAY;
    hand('wake', 'TM-83', 'horizon@' + z, z, [{ at: (hEnd - 2 * DAY + 1) * 1000 - 1, op: 'maybeRollover' }, { at: (hEnd - 2 * DAY + 1) * 1000, op: 'maybeRollover' }], { horizonEnd: hEnd });
    const lightEnd = local(z, 6, 840);
    hand('wake', 'TM-84', 'light@' + z, z, [{ at: (lightEnd + 1) * 1000 - 1, op: 'maybeRollover' }, { at: (lightEnd + 1) * 1000, op: 'maybeRollover' }],
      { days: 14, prefs: { intensityLevel: 'LIGHT' }, more: { prefsLightUntil: lightEnd, prefsLightRestore: 'AGGRESSIVE' } });
  });

  /* ---- fuzz: seeded sequences of timer acts, clock jumps both ways, ticks, saves that fail (TM-90) ---- */
  const IDS = ['s-a', 's-b', 's-c', 'nope'];
  const pick = (r, list) => list[Math.floor(r() * list.length)];
  const OPS = [
    r => start(pick(r, IDS), { plus: Math.floor(r() * 600) }),
    r => act(pick(r, ['pause', 'resume']), undefined, undefined, { plus: Math.floor(r() * 900) }),
    r => act(pick(r, ['finish', 'cancel', 'reset', 'autoFinish']), pick(r, IDS.slice(0, 3)), r() < 0.75 ? F() : 5, { plus: Math.floor(r() * 3000) }),
    r => act('replace', pick(r, IDS), r() < 0.75 ? F() : 5, { plus: Math.floor(r() * 600) }),
    r => FT({ plus: Math.floor(r() * 4000) }),
    r => FT({ plus: -Math.floor(r() * 3600) }),
    r => ({ at: { plus: Math.floor(r() * 3 * HOUR) }, op: 'tick' }),
    r => T(pick(r, ['done', 'skip']), { id: pick(r, IDS) }, { plus: Math.floor(r() * 600) }),
    r => storage(r() < 0.5 ? 'quota' : 'ok'),
    r => act(pick(r, ['start', 'finish', 'autoFinish']), pick(r, IDS.slice(0, 2)), F(), { plus: -Math.floor(r() * 1800) }),
  ];
  for (let seed = 1; seed <= 120; seed++) {
    const r = random(seed * 104729), z = ZONES[seed % ZONES.length], steps = [];
    const n = 4 + Math.floor(r() * 7);
    for (let k = 0; k < n; k++) steps.push(OPS[Math.floor(r() * OPS.length)](r));
    const prefs = [{}, { consequenceMode: 'MODERATE' }, { missGraceMinutes: 15 }][seed % 3];
    hand('fuzz', 'TM-90', 'seed-' + seed + '@' + z, z, steps, { prefs, at: A(z, (seed % 9) * 600 - 1800) });
  }
  return cases;
}

module.exports = { defineCases, FAMILIES, handPlan, local };
