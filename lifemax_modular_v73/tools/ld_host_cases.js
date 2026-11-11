/* The host-level golden cases for Life Director (WP-LD9): Today's and Do's commands, completion, the tick (host sync, misses, rollover),
   the dial's applyDial, the plan refusal and the one-time early-done repair, run by tools/ld_host_vm.js runHostCases on logic.js and
   recorded by tools/gen_ld_host_golden.js. Rules are LIFE_DIRECTOR_HOST_SPEC.md's (HC-n).

   A case is {id, family, rule, start, steps} (tools/ld_host_vm.js says what each part is). Every case starts from a saved state built
   here (base below: sleep, work, goals, in a zone of the WP-LD1 strict matrix) and most make their plan with a first Re-plan step, so the
   sessions they act on are the engine's own; a step names a session, rule or goal by its place in the state ({$ref}), and may run at a
   time read from the state. No Intl is used: instants come from Date.UTC and the clock-change table (tools/ld_kernel_cases.js). */
'use strict';
const { localInstant, random } = require('./ld_placement_cases.js');

const FAMILIES = ['done', 'log', 'skip', 'pin', 'disrupt', 'replan', 'regroup', 'goals', 'prefs', 'time', 'activity', 'backup', 'sync', 'misses',
  'rollover', 'dial', 'output', 'early', 'boot', 'fuzz'];
const ZONES = ['UTC', 'America/New_York', 'Europe/London', 'Australia/Sydney', 'Asia/Kolkata', 'America/Los_Angeles', 'Pacific/Kiritimati', 'America/Santiago'];
/* Wednesday 4 November 2026, 06:30 in a zone, in milliseconds */
const morning = (zone, d = 4, min = 390, mo = 11, y = 2026) => localInstant(zone, y, mo, d, min) * 1000;
const P = (n, field, plus) => Object.assign({ $ref: 'planned', n }, field ? { field } : {}, plus ? { plus } : {});
const Hist = (n, field) => Object.assign({ $ref: 'history', n }, field ? { field } : {});
const ref = ($ref, n) => ({ $ref, n });

/* A saved state: sleep 23:00-07:00, weekday work 09:00-17:00 with 30 minutes' travel, and goals (one or two of the engine's kinds). */
function base(zone, o = {}) {
  const since = 1790000000;
  const goals = (o.goals || ['FITNESS_HYPERTROPHY']).map((a, i) => ({ id: 'g-' + i, title: ['Build muscle', 'Read more', 'Learn Spanish', 'Run'][i] || 'Goal ' + i,
    archetypeId: a, priority: 3 + (i % 2), level: 'NOVICE', status: 'ACTIVE', createdEpoch: since }));
  return Object.assign({
    schemaVersion: 1, playerId: 'player', createdEpoch: since, updatedEpoch: since, setupComplete: o.setup !== false,
    availability: { timezone: zone, sleepRequirementMinutes: 480, blackouts: [], rules: o.rules || [
      { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480 },
      { id: 'work', kind: 'WORK', title: 'Work', days: [1, 2, 3, 4, 5], startMin: 9 * 60, durationMin: 480, commuteMin: 30, load: 'COGNITIVE_HEAVY' }] },
    goals, fixedCommitments: [], prefs: o.prefs || {}, schedule: null, ledger: o.ledger || null, changeLog: [], energyCheckin: null, questOverlay: {}, labQuests: [],
    bridge: { enabled: false, source: null, mapping: null }, lastGeneratedWeekKey: null, ui: Object.assign({ tab: 'today', tours: { setup: true, today: true } }, o.ui || {}),
  }, o.more || {});
}
const quest = (id, tag, more) => Object.assign({ id, title: 'Quest ' + id, tier: 'CORE', tag, status: 'ACTIVE', requiredCount: 2, progressCount: 0, schedulingMode: 'SESSIONS',
  sessionMinutes: 30, minSessionMinutes: 30, bounty: { xp: 20 }, source: 'lifemax', hostRef: { questId: id, key: id + '_w', cadence: 'weekly', done: false } }, more || {});

function defineCases() {
  const cases = [], ids = new Set();
  const add = (family, rule, tag, start, steps) => {
    let id = family + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, start: Object.assign({ storage: 'ok', host: {} }, start), steps });
  };
  /* a case on a fresh plan: boot at `at`, Re-plan a second later, then the steps */
  const planned = (family, rule, tag, zone, steps, o = {}) => add(family, rule, tag, { at: o.at || morning(zone), saved: JSON.stringify(base(zone, o)), storage: o.storage,
    host: o.host, banner: o.banner }, [{ at: { plus: 1 }, op: 'today', name: 'replan', payload: {} }].concat(steps));
  const T = (name, payload, at) => ({ at: at || { plus: 60 }, op: 'today', name, payload });
  const D = (name, payload, at) => ({ at: at || { plus: 60 }, op: 'do', name, payload });

  /* ---- done (HC-10 to HC-14, finishInstance HC-20 to HC-26) ---- */
  const minutesVariants = [undefined, 0, -5, 0.4, 1, 30, 30.6, '45', 'abc', 480, 1000, null, true];
  ZONES.forEach((z, zi) => {
    planned('done', 'HC-10 HC-20', 'at-start@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), T('done', { id: Hist(-1) })]);
    minutesVariants.forEach((m, k) => {
      if ((k + zi) % 3) return;
      planned('done', 'HC-11 HC-20', 'minutes-' + JSON.stringify(m) + '@' + z, z, [T('done', { id: P(k % 3), minutes: m }, P(k % 3, 'startEpoch', 600))]);
    });
    planned('done', 'HC-12', 'too-early@' + z, z, [T('done', { id: P(-1) })]);
    planned('done', 'HC-12', 'six-hours-ahead@' + z, z, [T('done', { id: P(1) }, P(1, 'startEpoch', -6 * 3600)), T('done', { id: P(1) }, P(1, 'startEpoch', -6 * 3600 - 1))]);
    planned('done', 'HC-10', 'gone@' + z, z, [T('done', { id: 'no-such' }), T('done', {}), T('done', { id: 7 })]);
    planned('done', 'HC-13', 'after-skip@' + z, z, [T('skip', { id: P(0) }, P(0, 'startEpoch', -1800)), T('done', { id: Hist(-1) })]);
    ['failed', 'readonly', 'offline', 'already'].forEach(mode =>
      planned('done', 'HC-21', 'award-' + mode + '@' + z, z, [T('done', { id: P(0), minutes: 20 }, P(0, 'startEpoch', 1200)), T('done', { id: Hist(-1) })], { host: { award: mode } }));
    planned('done', 'HC-22', 'partial@' + z, z, [T('done', { id: P(0), minutes: 10 }, P(0, 'startEpoch', 600))], { goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING'] });
    planned('done', 'HC-14', 'not-saved@' + z, z, [{ at: { plus: 5 }, op: 'storage', mode: 'quota' }, T('done', { id: P(0) }, P(0, 'startEpoch', 300)),
      T('done', { id: Hist(-1) }), { at: { plus: 5 }, op: 'storage', mode: 'ok' }, T('done', { id: Hist(-1) })]);
    planned('done', 'HC-23', 'quest@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), T('done', { id: P(0) }, P(0, 'startEpoch', 300))],
      { host: { quests: [quest('q-lift', 'fitness.strength', { requiredCount: 1 }), quest('q-read', 'reading')] }, goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING'] });
    planned('done', 'HC-23', 'quest-no-bounty@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300))],
      { host: { quests: [quest('q-lift', 'fitness.strength', { requiredCount: 1 }), quest('q-read', 'reading')], award: 'failed' }, goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING'] });
    planned('done', 'HC-23', 'quest-shared-key@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300))],
      { host: { quests: [quest('q-lift', 'fitness.strength', { requiredCount: 3 }), quest('q-lift2', 'fitness.strength', { requiredCount: 3,
        hostRef: { questId: 'q-lift2', key: 'q-lift_w', cadence: 'weekly', done: false } })] }, goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING'] });
    planned('done', 'HC-24', 'focus-cleared@' + z, z, [{ at: { plus: 5 }, op: 'host', host: {} }, T('done', { id: P(0) }, P(0, 'startEpoch', 300))],
      { ui: { focus: { id: 'later', start: 0, paused: null, pausedMs: 0, plannedMs: 60000 } } });
    planned('done', 'HC-20', 'finish-direct@' + z, z, [{ at: P(0, 'startEpoch', 900), op: 'finish', id: P(0), minutes: 45, opt: {} },
      { at: { plus: 1 }, op: 'finish', id: Hist(-1), minutes: 45, opt: {} }, { at: { plus: 1 }, op: 'finish', id: P(0), minutes: 5, opt: { silent: true, awardXp: false, startEpoch: 1 } }]);
  });

  /* the 6-hour edge across midnight: a hand-made session at 02:00, done from 20:00 the evening before (HC-12) */
  ZONES.forEach(z => {
    const start = morning(z, 5, 120) / 1000;
    const inst = { instanceId: 'night-1', taskId: 't-night', templateId: 'g-0:strength', title: 'Night lift', tag: 'fitness.strength', kind: 'GOAL', goalId: 'g-0',
      linkedGoalIds: ['g-0'], linkedQuestRequirementIds: [], startEpoch: start, endEpoch: start + 3600, durationSlots: 4, tier: 3, IP: 4, IC: 1,
      spacingKey: 'g-0:strength', lifecycleState: 'PLANNED', isUserLocked: false, revision: 0 };
    const sched = { horizonStartEpoch: start - 86400, horizonEndEpoch: start + 6 * 86400, timezone: z, scheduledInstances: [inst], historyInstances: [] };
    add('done', 'HC-12', 'boundary@' + z, { at: (start - 8 * 3600) * 1000, saved: JSON.stringify(base(z, { more: { schedule: sched } })) },
      [T('done', { id: 'night-1' }, (start - 6 * 3600 - 1) * 1000), T('done', { id: 'night-1' }, (start - 6 * 3600) * 1000)]);
  });

  /* ---- logToday (HC-15, HC-27) ---- */
  ZONES.forEach(z => {
    planned('log', 'HC-15 HC-27', 'later-day@' + z, z, [T('logToday', { id: P(-1), op: 'op-1', minutes: 25 }), T('logToday', { id: P(-1), op: 'op-1', minutes: 25 }),
      T('logToday', { id: P(-1), op: 'op-2' })]);
    planned('log', 'HC-15', 'refusals@' + z, z, [T('logToday', { id: 'nope', op: 'x' }), T('logToday', { id: P(0) }), T('logToday', { id: P(0), op: '' })]);
    ['failed', 'offline'].forEach(mode => planned('log', 'HC-27', 'award-' + mode + '@' + z, z, [T('logToday', { id: P(2), op: 'o', minutes: 600 })], { host: { award: mode } }));
    planned('log', 'HC-27', 'direct@' + z, z, [{ at: { plus: 60 }, op: 'logToday', id: P(1), minutes: 15, opId: 'd1' }, { at: { plus: 60 }, op: 'logToday', id: P(1), minutes: 15, opId: 'd1' }]);
  });

  /* ---- skip and its take-back (HC-16, HC-17) ---- */
  ZONES.forEach(z => {
    planned('skip', 'HC-16 HC-17', 'skip-undo@' + z, z, [T('skip', { id: P(0) }), T('skip', { id: Hist(-1) }), T('unskip', { id: Hist(-1), op: 'u1' }),
      T('unskip', { id: P(0), op: 'u1' }), T('unskip', { id: P(0), op: 'u2' })]);
    planned('skip', 'HC-17', 'too-late@' + z, z, [T('skip', { id: P(1) }), T('unskip', { id: Hist(-1) }, { plus: 121 })]);
    planned('skip', 'HC-17', 'just-in-time@' + z, z, [T('skip', { id: P(1) }), T('unskip', { id: Hist(-1) }, { plus: 120 })]);
    planned('skip', 'HC-17', 'plan-changed@' + z, z, [T('skip', { id: P(1) }), T('pin', { id: P(0), on: true }), T('unskip', { id: Hist(-1) })]);
    planned('skip', 'HC-17', 'other-id@' + z, z, [T('skip', { id: P(1) }), T('unskip', { id: P(0) }), T('unskip', { id: 'zz' }), T('unskip', {})]);
    planned('skip', 'HC-16', 'refusals@' + z, z, [T('skip', { id: 'nope' }), T('done', { id: P(0) }, P(0, 'startEpoch', 60)), T('skip', { id: Hist(-1) }),
      T('skip', { id: P(0) })], { ui: { focus: { id: 'x', start: 1, paused: null, pausedMs: 0, plannedMs: 1 } } });
    planned('skip', 'HC-16', 'tokens@' + z, z, [T('skip', { id: P(0) }), T('skip', { id: P(0) }), T('skip', { id: P(0) }), T('skip', { id: P(0) })],
      { ledger: { schemaVersion: 1, playerId: 'player', events: {}, order: [], bounties: {}, penalties: {}, restTokens: 1, completionsSinceToken: 0, consecutiveMisses: 0,
        consequencesByBwc: {}, regroupActive: false, streaks: {} }, prefs: { consequenceMode: 'MODERATE' } });
  });
  ZONES.slice(0, 3).forEach(z => planned('skip', 'HC-16 HC-24', 'running@' + z, z, [{ at: { plus: 5 }, op: 'focus', focus: { id: P(0), start: 1, paused: null, pausedMs: 0, plannedMs: 60000 } },
    T('skip', { id: P(0) }), T('done', { id: P(0) }, P(0, 'startEpoch', 60)), T('skip', { id: P(0) })]));

  /* ---- pin and move (HC-18, HC-19) ---- */
  ZONES.forEach(z => {
    planned('pin', 'HC-18', 'pin@' + z, z, [T('pin', { id: P(0), on: true }), T('pin', { id: P(0), on: true }), T('pin', { id: P(0), on: false }), T('pin', { id: P(0), on: 0 }),
      T('pin', { id: 'nope', on: true })]);
    planned('pin', 'HC-19', 'move@' + z, z, [T('moveTo', { id: P(1), start: P(1, 'startEpoch', 3600) }), T('moveTo', { id: P(0), start: 'x' }), T('moveTo', { id: 'nope', start: 1 }),
      T('moveTo', { id: P(0), start: P(0, 'startEpoch', 900) })]);
    planned('pin', 'HC-19', 'move-refused@' + z, z, [T('moveTo', { id: P(0), start: 1000 }), T('pin', { id: P(1), on: true }), T('moveTo', { id: P(1), start: P(1, 'startEpoch') }),
      T('moveTo', { id: P(1), start: P(1, 'startEpoch', 2 * 86400 + 1800) })]);
  });

  /* ---- Something came up (HC-30, HC-31) ---- */
  ZONES.forEach(z => {
    planned('disrupt', 'HC-30', 'work@' + z, z, [T('disrupt', { kind: 'work', op: 'w1', date: '2026-11-05', from: 17 * 60, until: 21 * 60, commute: 20 }),
      T('disrupt', { kind: 'work', op: 'w1', date: '2026-11-05', from: 17 * 60, until: 21 * 60 }),
      T('disrupt', { kind: 'work', op: 'w2', date: '2026-11-06', from: 22 * 60, until: 2 * 60 }), T('disrupt', { kind: 'work', op: 'w3', date: '5 Nov', from: 1, until: 2 }),
      T('disrupt', { kind: 'work', op: 'w4', date: '2026-11-05', from: 1440, until: 2 }), T('disrupt', { kind: 'work', op: 'w5', date: '2026-11-06', from: 18 * 60, until: 18 * 60 })]);
    planned('disrupt', 'HC-30', 'block@' + z, z, [T('disrupt', { kind: 'block', op: 'b1', hours: 2 }), T('disrupt', { kind: 'block', op: 'b1', hours: 2 }),
      T('disrupt', { kind: 'block', op: 'b2', hours: 5 }), T('disrupt', { kind: 'block', op: 'b3', hours: '3' })]);
    planned('disrupt', 'HC-30', 'day@' + z, z, [T('disrupt', { kind: 'day', op: 'd1' }), T('disrupt', { kind: 'day', op: 'd1' })]);
    planned('disrupt', 'HC-30', 'sick@' + z, z, [T('disrupt', { kind: 'sick', op: 's1', days: 2 }), T('disrupt', { kind: 'sick', op: 's2', days: 4 })]);
    planned('disrupt', 'HC-31', 'refusals@' + z, z, [T('disrupt', { kind: 'energy', op: 'e' }), T('disrupt', { kind: 'nope', op: 'e' }), T('disrupt', { kind: 'day' }),
      T('disrupt', {})]);
    planned('disrupt', 'HC-32', 'energy@' + z, z, [T('energy', { level: 'LOW' }), T('energy', { level: 'LOW' }), T('energy', { level: 'BAD' }), T('energy', {})]);
  });
  /* the DST days: an extra shift across the night the clocks change */
  [['America/New_York', 2026, 10, 31], ['America/New_York', 2027, 3, 13], ['Europe/London', 2026, 10, 24], ['Australia/Sydney', 2026, 10, 3], ['America/Santiago', 2026, 9, 5]]
    .forEach(([z, y, mo, d]) => {
      const date = y + '-' + String(mo).padStart(2, '0') + '-' + String(d + 1).padStart(2, '0');
      planned('disrupt', 'HC-30', 'dst-shift-' + date + '@' + z, z, [T('disrupt', { kind: 'work', op: 'n1', date: y + '-' + String(mo).padStart(2, '0') + '-' + String(d).padStart(2, '0'), from: 20 * 60, until: 4 * 60 }),
        T('disrupt', { kind: 'work', op: 'n2', date, from: 60, until: 3 * 60 + 30 }), T('disrupt', { kind: 'day', op: 'n3' })], { at: localInstant(z, y, mo, d, 6 * 60) * 1000 });
    });

  /* ---- Re-plan and the plan refusal (HC-33, HC-40) ---- */
  ZONES.forEach(z => {
    planned('replan', 'HC-33', 'twice@' + z, z, [T('replan', {}), T('replan', {}, { plus: 86400 })]);
    planned('replan', 'HC-7 HC-33', 'long-log@' + z, z, [T('replan', {})], { more: { changeLog: Array.from({ length: 60 }, (_, k) => ({ at: 1790000000 + k, label: 'old ' + k })) } });
    planned('replan', 'HC-33', 'bad-banner@' + z, z, [{ at: { plus: 1 }, op: 'banner', banner: { kind: 'bad', text: 'Old' } }, T('replan', {})]);
  });
  planned('replan', 'HC-33', 'no-setup@UTC', 'UTC', [T('replan', {})], { setup: false });

  /* ---- regroup and the banner (HC-34, HC-35) ---- */
  const REG = { kind: 'warn', regroup: true, text: 'Looks like life got hectic. Want to pause for 48 hours, switch to light mode for this week, or just carry on? Missed-task consequences are paused either way.' };
  ZONES.forEach(z => {
    ['pause', 'light', 'go'].forEach(v => planned('regroup', 'HC-34', v + '@' + z, z, [{ at: { plus: 1 }, op: 'banner', banner: REG }, T('regroup', { v, op: 'r1' }),
      T('regroup', { v, op: 'r1' }), T('regroup', { v: v === 'go' ? 'pause' : 'go', op: 'r2' })]));
    planned('regroup', 'HC-34', 'refusals@' + z, z, [T('regroup', { v: 'nope' }), T('regroup', { v: 'pause' }), { at: { plus: 1 }, op: 'banner', banner: REG },
      T('regroup', { v: 'pause' }), T('regroup', {}), T('dismissBanner', { text: REG.text }), T('dismissBanner', { text: 'other' })]);
    planned('regroup', 'HC-35', 'dismiss@' + z, z, [{ at: { plus: 1 }, op: 'banner', banner: { kind: 'warn', text: 'Hello' } }, T('dismissBanner', { text: 'Hi' }),
      T('dismissBanner', { text: 'Hello' }), T('dismissBanner', { text: 'Hello' }), T('dismissBanner', {})]);
  });
  planned('regroup', 'HC-34', 'light-expired@UTC', 'UTC', [{ at: { plus: 1 }, op: 'banner', banner: REG }, T('regroup', { v: 'light' }), T('regroup', { v: 'light' }, { plus: 8 * 86400 }),
    { at: { plus: 1 }, op: 'maybeRollover' }]);

  /* ---- goals (HC-50 to HC-55) ---- */
  ZONES.slice(0, 5).forEach((z, zi) => {
    planned('goals', 'HC-50', 'priority@' + z, z, [D('goalPriority', { id: ref('goal', 0), value: 5 }), D('goalPriority', { id: ref('goal', 0), value: '2' }),
      D('goalPriority', { id: ref('goal', 0), value: 6 }), D('goalPriority', { id: 'nope', value: 1 }), D('goalPriority', { id: ref('goal', 0), value: 2.5 })]);
    planned('goals', 'HC-51', 'pause@' + z, z, [D('goalPause', { id: ref('goal', 0), paused: true }), D('goalPause', { id: ref('goal', 0), paused: true }),
      D('goalPause', { id: ref('goal', 0) }), D('goalPause', { id: ref('goal', 0), paused: 'yes' }), D('goalPause', { id: 'x' })]);
    planned('goals', 'HC-52', 'remove@' + z, z, [D('goalRemove', { id: ref('goal', 1) }), D('goalRemove', { id: ref('goal', 1) }), D('clarify', { id: ref('goal', 0), arch: 'HABIT_READING' }),
      D('clarify', { id: ref('goal', 0), arch: 'NOPE' }), D('clarify', { id: 'x', arch: 'HABIT_READING' })], { goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING'] });
    planned('goals', 'HC-53', 'add@' + z, z, [D('goalAdd', { op: 'op-' + zi + '-123456789', arch: 'HABIT_READING', title: ' Read ', priority: 4, level: 'INTERMEDIATE' }),
      D('goalAdd', { op: 'op-' + zi + '-123456789', arch: 'HABIT_READING', title: 'Read' }),
      D('goalAdd', { op: 'c-' + zi, arch: 'CUSTOM', title: 'Guitar', tag: 'creative.practice', minutes: 45, perWeek: '2', physical: 1, mental: 4 }),
      D('goalAdd', { op: 'l-' + zi, arch: 'SKILL_LANGUAGE', title: 'French', subject: ' french ' }),
      D('goalAdd', { op: 'd-' + zi, arch: 'CUSTOM', title: 'Draw', tag: 'creative.practice' }),
      D('goalAdd', { op: 'v-' + zi, arch: 'HABIT_READING', title: 'Books', units: 600, unit: 'pages', rate: 30, deadline: '2026-12-31' })]);
    planned('goals', 'HC-53', 'add-refusals@' + z, z, [D('goalAdd', { arch: 'NOPE', title: 'x', op: '1' }), D('goalAdd', { arch: 'HABIT_READING', title: '  ', op: '1' }),
      D('goalAdd', { arch: 'HABIT_READING', title: 'x', priority: 9, op: '1' }), D('goalAdd', { arch: 'HABIT_READING', title: 'x', level: 'GOD', op: '1' }),
      D('goalAdd', { arch: 'CUSTOM', title: 'x', tag: 'nope', op: '1' }), D('goalAdd', { arch: 'HABIT_READING', title: 'x' }), D('goalAdd', {})]);
  });
  add('goals', 'HC-53', 'add-before-setup@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false, goals: [] })) },
    [D('goalAdd', { op: 'abcdefghijklmnop', arch: 'FITNESS_CARDIO', title: 'Run' }), D('goalAdd', { op: 'abcdefghijklXYZ', arch: 'FITNESS_CARDIO', title: 'Run again' })]);

  /* ---- preferences (HC-56 to HC-58) ---- */
  ZONES.slice(0, 4).forEach(z => {
    planned('prefs', 'HC-56', 'pref@' + z, z, [D('pref', { key: 'intensityLevel', value: 'AGGRESSIVE' }), D('pref', { key: 'planningHorizonDays', value: '14' }),
      D('pref', { key: 'consequenceMode', value: 'HARDCORE_RPG' }), D('pref', { key: 'intensityLevel', value: 'MAX' }), D('pref', { key: 'nope', value: 1 }),
      D('pref', { key: 'planningHorizonDays', value: 10 })]);
    planned('prefs', 'HC-56', 'range@' + z, z, [D('prefRange', { key: 'maxDailyGoalMinutes', value: 120 }), D('prefRange', { key: 'maxDailyGoalMinutes', value: 125 }),
      D('prefRange', { key: 'missGraceMinutes', value: '30' }), D('prefRange', { key: 'missGraceMinutes', value: 0 }), D('prefRange', { key: 'minDailyRecoveryMinutes', value: 361 }),
      D('prefRange', { key: 'nope', value: 30 }), D('prefRange', { key: 'maxConsecutiveHighLoadMinutes', value: 'x' })]);
    planned('prefs', 'HC-57', 'rest-day@' + z, z, [D('restDay', { day: 3, on: true }), D('restDay', { day: 3, on: true }), D('restDay', { day: 3 }), D('restDay', { day: '0' }),
      D('restDay', { day: 7 }), D('restDay', { day: 1.5 })]);
    planned('prefs', 'HC-58', 'routine@' + z, z, [D('routine', { key: 'windDownMinutes', value: 45 }), D('routine', { key: 'windDownMinutes', value: 44.6 }),
      D('routine', { key: 'morningRoutineMinutes', value: 500 }), D('routine', { key: 'mealMinutes', value: 3 }), D('routine', { key: 'nope', value: 1 }),
      D('routine', { key: 'mealMinutes', value: 'x' }), D('meals', { on: false }), D('meals', { on: false }), D('meals', {}), D('meals', { on: 'yes' })]);
    planned('prefs', 'HC-59', 'ui@' + z, z, [D('labToggle', { on: 1 }), D('labToggle', {}), D('tourReset', {}), D('nope', {})]);
  });
  add('prefs', 'HC-56 HC-59', 'before-setup@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false })) },
    [D('pref', { key: 'intensityLevel', value: 'LIGHT' }), D('routine', { key: 'windDownMinutes', value: 30 }), D('meals', { on: true }), D('tourReset', {})]);

  /* ---- My time: sleep, commitments, set-up (HC-60 to HC-64) ---- */
  ZONES.forEach((z, zi) => {
    planned('time', 'HC-60', 'sleep@' + z, z, [D('sleepSave', { bed: 22 * 60 + 30, wake: 6 * 60 + 30 }), D('sleepSave', { bed: 22 * 60 + 30, wake: 6 * 60 + 30 }),
      D('sleepSave', { bed: 23 * 60, wake: 7 * 60, weekend: { bed: 60, wake: 9 * 60 } }), D('sleepSave', { bed: 600, wake: 600 }), D('sleepSave', { bed: 0, wake: 240 }),
      D('sleepSave', { bed: 1440, wake: 1 }), D('sleepSave', { bed: 60, wake: 420, weekend: { bed: 60 } }), D('sleepSave', {}),
      D('sleepSave', { bed: 1380, wake: 420, weekend: { bed: 60, wake: 120 } })]);
    planned('time', 'HC-61', 'rule@' + z, z, [D('ruleSave', { op: 'k' + zi, title: ' Gym class ', kind: 'CLASS', days: [2, 4, 9], start: 18 * 60, end: 19 * 60, load: 'LIGHT', commute: 300, unwind: -5 }),
      D('ruleSave', { op: 'k' + zi, title: 'Gym class', kind: 'CLASS', days: [2, 4], start: 18 * 60, end: 19 * 60, load: 'LIGHT' }),
      D('ruleSave', { id: ref('rule', -1), title: 'Gym class', kind: 'CLASS', days: [2, 4], start: 18 * 60, end: 19 * 60, load: 'LIGHT', commute: 240, unwind: 0 }),
      D('ruleSave', { id: ref('rule', -1), title: 'Gym class', kind: 'CLASS', days: [2, 4], start: 18 * 60, end: 19 * 60, load: 'LIGHT', commute: 240, unwind: 0 }),
      D('ruleSave', { op: 'rot' + zi, title: '', kind: 'WORK', rotating: true, anchor: '2026-11-02', on: 3, off: '2', start: 19 * 60, end: 7 * 60, load: 'PHYSICAL_HEAVY' }),
      D('ruleRemove', { id: ref('rule', -1) }), D('ruleRemove', { id: ref('rule', -1) }), D('ruleRemove', { id: 'gone' }), D('ruleRemove', { id: 'sleep' })]);
    planned('time', 'HC-61', 'rule-refusals@' + z, z, [D('ruleSave', { op: 'a', kind: 'WORK', days: [1], start: 60, load: 'LIGHT' }),
      D('ruleSave', { op: 'a', kind: 'WORK', days: [], start: 60, end: 120, load: 'LIGHT' }), D('ruleSave', { op: 'a', kind: 'WORK', rotating: true, anchor: 'x', start: 60, end: 120, load: 'LIGHT' }),
      D('ruleSave', { op: 'a', kind: 'SLEEP', days: [1], start: 60, end: 120, load: 'LIGHT' }), D('ruleSave', { op: 'a', kind: 'WORK', days: [1], start: 60, end: 120, load: 'X' }),
      D('ruleSave', { id: 'sleep', kind: 'WORK', days: [1], start: 60, end: 120, load: 'LIGHT' }), D('ruleSave', { id: 'gone', kind: 'WORK', days: [1], start: 60, end: 120, load: 'LIGHT' }),
      D('ruleSave', { kind: 'WORK', days: [1], start: 60, end: 120, load: 'LIGHT' }), D('ruleSave', { op: 'same', kind: 'APPOINTMENT', days: [1], start: 60, end: 60, load: 'MIXED_MODERATE' })]);
  });
  ZONES.slice(0, 3).forEach(z => {
    const fresh = base(z, { setup: false, rules: [] });
    add('time', 'HC-62 HC-60', 'setup@' + z, { at: morning(z), saved: JSON.stringify(fresh) }, [D('finishSetup', {}), D('sleepSave', { bed: 23 * 60, wake: 7 * 60 }),
      D('ruleSave', { op: 'w', title: 'Job', kind: 'WORK', days: [1, 2, 3, 4, 5], start: 540, end: 1020, load: 'MIXED_MODERATE', commute: 15 }),
      D('finishSetup', {}), D('finishSetup', {}, { plus: 3600 })]);
  });
  add('time', 'HC-62', 'setup-store-fails@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false })) },
    [{ at: { plus: 1 }, op: 'storage', mode: 'broken' }, D('finishSetup', {}), D('sleepSave', { bed: 60, wake: 600 })]);

  /* ---- a pinned activity at a time (HC-63) ---- */
  ZONES.forEach((z, zi) => {
    planned('activity', 'HC-63', 'add@' + z, z, [D('activityAtAdd', { op: 'a' + zi, title: ' Piano ', tag: 'creative.practice', date: '2026-11-07', start: 15 * 60, minutes: 60 }),
      D('activityAtAdd', { op: 'a' + zi, title: 'Piano', tag: 'creative.practice', date: '2026-11-07', start: 15 * 60, minutes: 60 }),
      D('activityAtAdd', { op: 'b' + zi, title: 'Over work', tag: 'reading', date: '2026-11-05', start: 10 * 60, minutes: 30 }),
      D('activityAtAdd', { op: 'c' + zi, title: 'Past', tag: 'reading', date: '2026-11-03', start: 10 * 60, minutes: 30 }),
      D('activityAtAdd', { op: 'd' + zi, title: 'Far', tag: 'reading', date: '2026-12-20', start: 10 * 60, minutes: 30 }),
      D('activityAtAdd', { op: 'e' + zi, title: 'Sleep time', tag: 'reading', date: '2026-11-06', start: 2 * 60, minutes: 30 }),
      D('activityAtAdd', { op: 'f' + zi, title: 'On top', tag: 'reading', date: '2026-11-07', start: 15 * 60 + 30, minutes: 15 })]);
    planned('activity', 'HC-63', 'refusals@' + z, z, [D('activityAtAdd', {}), D('activityAtAdd', { op: 'x'.repeat(129) }), D('activityAtAdd', { op: 7 }),
      D('activityAtAdd', { op: 'g', title: 'x'.repeat(81), tag: 'reading', date: '2026-11-07', start: 600, minutes: 30 }),
      D('activityAtAdd', { op: 'g', title: 'x', tag: 'nope', date: '2026-11-07', start: 600, minutes: 30 }),
      D('activityAtAdd', { op: 'g', title: 'x', tag: 'reading', date: '2026-11-07', start: 610, minutes: 30 }),
      D('activityAtAdd', { op: 'g', title: 'x', tag: 'reading', date: '2026-11-07', start: 600, minutes: 200 }),
      D('activityAtAdd', { op: 'g', title: 'x', tag: 'reading', date: '7/11/2026', start: 600, minutes: 30 }),
      D('activityAtAdd', { op: 'g', title: 'x', tag: 'reading', date: '2026-11-07', start: 600, minutes: '30' })]);
  });
  planned('activity', 'HC-63', 'not-saved@UTC', 'UTC', [{ at: { plus: 1 }, op: 'storage', mode: 'quota' },
    D('activityAtAdd', { op: 'q', title: 'Piano', tag: 'creative.practice', date: '2026-11-07', start: 15 * 60 + 30, minutes: 15 }),
    { at: { plus: 1 }, op: 'storage', mode: 'ok' }, D('activityAtAdd', { op: 'q', title: 'Piano', tag: 'creative.practice', date: '2026-11-07', start: 15 * 60 + 30, minutes: 15 })]);
  add('activity', 'HC-63', 'no-plan@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC')) }, [D('activityAtAdd', { op: 'n', title: 'x', tag: 'reading', date: '2026-11-07', start: 600, minutes: 30 })]);
  [['America/New_York', 2027, 3, 14, 150], ['Europe/London', 2027, 3, 28, 75], ['Australia/Sydney', 2026, 10, 4, 135], ['America/New_York', 2026, 11, 1, 75], ['Europe/London', 2026, 10, 25, 75]]
    .forEach(([z, y, mo, d, m]) => {
      const date = y + '-' + String(mo).padStart(2, '0') + '-' + String(d).padStart(2, '0');
      planned('activity', 'HC-63', 'dst-' + date + '@' + z, z, [D('activityAtAdd', { op: 'g1', title: 'Clock', tag: 'reading', date, start: m, minutes: 30 }),
        D('activityAtAdd', { op: 'g2', title: 'Clock', tag: 'reading', date, start: 13 * 60, minutes: 45 })], { at: localInstant(z, y, mo, d - 1, 8 * 60) * 1000, rules: [
        { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 4 * 60, durationMin: 480 }] });
    });

  /* ---- backup and reset (HC-65, HC-66) ---- */
  ZONES.slice(0, 3).forEach(z => {
    const other = base(z, { goals: ['HABIT_READING'] }); other.setupComplete = true;
    planned('backup', 'HC-65', 'roundtrip@' + z, z, [D('exportBackup', {}), D('importBackup', { text: JSON.stringify({ format: 'lifemax-director-backup', version: 1, state: other }) }),
      D('importBackup', { text: '{' }), D('importBackup', { text: JSON.stringify({ schemaVersion: 2 }) }), D('importBackup', {}),
      D('importBackup', { text: JSON.stringify({ sleep: { bed: 1380, wake: 420 }, work: [{ startMin: 540, durationMin: 480, days: [1, 2] }], goals: [] }) })]);
    planned('backup', 'HC-66', 'reset@' + z, z, [D('reset', {}), T('replan', {}), D('reset', {})]);
  });
  planned('backup', 'HC-65 HC-66', 'store-fails@UTC', 'UTC', [{ at: { plus: 1 }, op: 'storage', mode: 'quota' },
    D('importBackup', { text: JSON.stringify({ format: 'lifemax-director-backup', version: 1, state: base('UTC') }) }), D('reset', {})]);

  /* ---- host sync (HC-70 to HC-74) ---- */
  const act = (id, iso, tag, minutes, more) => Object.assign({ id, type: 'strength', name: 'Act ' + id, minutes, iso, tag, planDate: null }, more || {});
  ZONES.forEach(z => {
    const at = morning(z), iso = ms => new Date(ms).toISOString();
    planned('sync', 'HC-70', 'match-and-adhoc@' + z, z, [{ at: { plus: 3 * 3600 }, op: 'host', host: { activities: [act('a1', iso(at + 2 * 3600e3), 'fitness.strength', 40),
      act('a2', iso(at + 2.5 * 3600e3), 'chores', 0), act('a3', iso(at - 30 * 3600e3), 'reading', 20)] } }, { at: { plus: 1 }, op: 'syncHost' }, { at: { plus: 1 }, op: 'syncHost' },
    { at: { plus: 60 }, op: 'host', host: { activities: [act('a1', iso(at + 2 * 3600e3), 'fitness.strength', 40), act('a4', iso(at + 4 * 3600e3), 'fitness', 25)] } },
    { at: { plus: 1 }, op: 'syncHost' }]);
    planned('sync', 'HC-71', 'undone-in-log@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), T('done', { id: P(0) }, P(0, 'startEpoch', 300)),
      { at: { plus: 60 }, op: 'host', host: { existing: [] } }, { at: { plus: 1 }, op: 'syncHost' }, { at: { plus: 1 }, op: 'syncHost' }]);
    planned('sync', 'HC-70', 'tag-directions@' + z, z, [{ at: { plus: 3 * 3600 }, op: 'host', host: { activities: [act('t1', iso(at + 2 * 3600e3), 'fitness.strength.free', 30),
      act('t2', iso(at + 2.2 * 3600e3), 'fitness', 30)] } }, { at: { plus: 1 }, op: 'syncHost' }]);
    planned('sync', 'HC-71', 'undone-later@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), { at: { plus: 60 }, op: 'host', host: { existing: [] } },
      { at: Hist(-1, 'endEpoch'), op: 'syncHost' }]);
    planned('sync', 'HC-72', 'quest-done@' + z, z, [T('pin', { id: P(0), on: true }), { at: { plus: 60 }, op: 'host', host: { quests: [quest('q-lift', 'fitness.strength', { status: 'COMPLETED' })], milestones: [{ id: 'm1' }] } },
      { at: { plus: 1 }, op: 'syncHost' }], { host: { quests: [quest('q-lift', 'fitness.strength', { requiredCount: 3 })] } });
    planned('sync', 'HC-73', 'untimed@' + z, z, [{ at: { plus: 60 }, op: 'host', host: { quests: [quest('q-water', 'wellbeing', { schedulingMode: 'NONE', anchor: 'MEAL', requiredCount: 1 })] } },
      { at: { plus: 1 }, op: 'syncHost' }, { at: { plus: 1 }, op: 'syncHost' }], { host: { quests: [quest('q-water', 'wellbeing', { requiredCount: 1, recurrence: 'DAILY' })] }, goals: [] });
    planned('sync', 'HC-73', 'retired@' + z, z, [{ at: { plus: 60 }, op: 'host', host: { quests: [], retired: { 'dh_1': { id: 'dh_1', title: 'Drink', schedulingMode: 'NONE', anchor: 'WAKE' } } } },
      { at: { plus: 1 }, op: 'syncHost' }], { host: { quests: [quest('dh_1', 'wellbeing', { requiredCount: 1, recurrence: 'DAILY' })], retired: {} }, goals: [] });
  });
  planned('sync', 'HC-74', 'offline@UTC', 'UTC', [{ at: { plus: 60 }, op: 'host', host: { connected: false, activities: [act('a1', '2026-11-04T08:00:00.000Z', 'reading', 20)] } },
    { at: { plus: 1 }, op: 'syncHost' }, { at: { plus: 1 }, op: 'tick' }]);
  add('sync', 'HC-74', 'no-plan@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC')), host: { activities: [act('a1', '2026-11-04T08:00:00.000Z', 'reading', 20)] } },
    [{ at: { plus: 1 }, op: 'syncHost' }]);
  planned('sync', 'HC-70', 'plan-day@UTC', 'UTC', [{ at: { plus: 60 }, op: 'host', host: { activities: [act('p1', '2026-11-04T10:00:00.000Z', 'fitness', 50, { planDate: '2026-11-04' })] } },
    { at: { plus: 1 }, op: 'syncHost' }], { host: { planSessions: [{ id: 'plan:2026-11-04', title: 'Plan: Legs', tier: 'CORE', tag: 'fitness', status: 'ACTIVE', requiredCount: 1, progressCount: 0,
    sessionMinutes: 45, exclusive: true, windowStartEpoch: 1793750400, deadlineEpoch: 1793836800, hostRef: { plan: '2026-11-04', done: false }, source: 'lifemax-plan' }] } });

  /* ---- misses (HC-75 to HC-77) ---- */
  ZONES.forEach((z, zi) => {
    ['LENIENT', 'MODERATE', 'HARDCORE_RPG'].forEach((mode, k) => {
      if ((zi + k) % 2) return;
      planned('misses', 'HC-75 HC-76', mode + '@' + z, z, [{ at: { plus: 30 * 3600 }, op: 'processMisses' }, { at: { plus: 1 }, op: 'processMisses' }, { at: { plus: 2 * 86400 }, op: 'tick' }],
        { prefs: { consequenceMode: mode } });
    });
    planned('misses', 'HC-75', 'grace@' + z, z, [{ at: P(0, 'endEpoch', 3600), op: 'processMisses' }, { at: { plus: 1 }, op: 'processMisses' }], { prefs: { missGraceMinutes: 60 } });
    planned('misses', 'HC-77', 'regroup@' + z, z, [{ at: { plus: 4 * 86400 }, op: 'processMisses' }, T('regroup', { v: 'go' }), T('regroup', { v: 'go' })],
      { goals: ['FITNESS_HYPERTROPHY', 'HABIT_READING', 'SKILL_LANGUAGE'], prefs: { consequenceMode: 'MODERATE', intensityLevel: 'HARDCORE' } });
  });
  planned('misses', 'HC-75', 'none@UTC', 'UTC', [{ at: { plus: 60 }, op: 'processMisses' }]);
  const worn = { schemaVersion: 1, playerId: 'player', events: {}, order: [], bounties: {}, penalties: {}, restTokens: 0, completionsSinceToken: 4, consecutiveMisses: 2,
    consequencesByBwc: {}, regroupActive: false, streaks: { 'g-0:strength': 3 } };
  ZONES.slice(0, 4).forEach(z => {
    planned('misses', 'HC-76 HC-77', 'worn-ledger@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), { at: { plus: 3 * 86400 }, op: 'tick' }, T('regroup', { v: 'light' })],
      { ledger: worn, prefs: { consequenceMode: 'MODERATE' } });
    planned('done', 'HC-25', 'token-earned@' + z, z, [T('done', { id: P(0) }, P(0, 'startEpoch', 300)), T('done', { id: P(1) }, P(1, 'startEpoch', 300))], { ledger: worn });
  });
  add('misses', 'HC-75', 'no-plan@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC')) }, [{ at: { plus: 60 }, op: 'processMisses' }]);

  /* ---- rollover and the tick (HC-78, HC-79) ---- */
  ZONES.forEach(z => {
    planned('rollover', 'HC-78', 'short-horizon@' + z, z, [{ at: { plus: 12 * 3600 }, op: 'maybeRollover' }], { prefs: { planningHorizonDays: 2 } });
    planned('rollover', 'HC-78', 'next-week@' + z, z, [{ at: { plus: 60 }, op: 'maybeRollover' }, { at: { plus: 5 * 86400 }, op: 'maybeRollover' }, { at: { plus: 60 }, op: 'maybeRollover' }]);
    planned('rollover', 'HC-78', 'light-ends@' + z, z, [{ at: { plus: 60 }, op: 'maybeRollover' }], { more: { prefsLightUntil: 1700000000, prefsLightRestore: 'AGGRESSIVE' } });
    planned('rollover', 'HC-79', 'tick@' + z, z, [{ at: { plus: 3600 }, op: 'tick' }, { at: { plus: 26 * 3600 }, op: 'tick' }, { at: { plus: 6 * 86400 }, op: 'tick' }]);
  });
  planned('rollover', 'HC-78', 'zone-moved@UTC', 'UTC', [{ at: { plus: 60 }, op: 'do', name: 'pref', payload: { key: 'intensityLevel', value: 'LIGHT' } }]);
  add('rollover', 'HC-78', 'zone-change@London', { at: morning('Europe/London'), saved: JSON.stringify(base('Europe/London')) }, [T('replan', {}),
    { at: { plus: 60 }, op: 'do', name: 'importBackup', payload: { text: JSON.stringify(base('Asia/Kolkata', { more: { lastGeneratedWeekKey: '2026-11-02' } })) } },
    { at: { plus: 60 }, op: 'maybeRollover' }, { at: { plus: 60 }, op: 'tick' }]);
  add('rollover', 'HC-78', 'not-set-up@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false, more: { prefsLightUntil: 1, prefsLightRestore: null } })) },
    [{ at: { plus: 60 }, op: 'maybeRollover' }, { at: { plus: 60 }, op: 'tick' }]);
  add('rollover', 'HC-78', 'first-plan@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC')) }, [{ at: { plus: 60 }, op: 'maybeRollover' }]);

  /* ---- the dial (HC-80 to HC-82) ---- */
  const dial = (ruleId, end, startMin, durationMin, from, to) => ({ ruleId, end, startMin, durationMin, from, to });
  ZONES.forEach(z => {
    planned('dial', 'HC-80', 'bedtime@' + z, z, [{ at: { plus: 60 }, op: 'applyDial', P: dial('sleep', 'start', 22 * 60, 540, 1380, 1320) },
      { at: { plus: 60 }, op: 'applyDial', P: dial('sleep', 'start', 22 * 60, 540, 1380, 1320) }, { at: { plus: 60 }, op: 'applyDial', P: null }]);
    planned('dial', 'HC-80', 'work-ends@' + z, z, [{ at: { plus: 60 }, op: 'applyDial', P: dial('work', 'end', 540, 600, 1020, 1140) },
      { at: { plus: 60 }, op: 'applyDial', P: dial('nope', 'end', 540, 600, 1020, 1140) }]);
    planned('dial', 'HC-81', 'kept@' + z, z, [{ at: { plus: 60 }, op: 'applyDial', P: dial('sleep', 'end', 23 * 60, 495, 420, 435) }]);
  });
  add('dial', 'HC-80', 'before-setup@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false })) },
    [{ at: { plus: 60 }, op: 'applyDial', P: dial('sleep', 'start', 1320, 540, 1380, 1320) }]);
  planned('dial', 'HC-82', 'not-saved@UTC', 'UTC', [{ at: { plus: 1 }, op: 'storage', mode: 'quota' }, { at: { plus: 60 }, op: 'applyDial', P: dial('work', 'start', 600, 420, 540, 600) }]);
  add('dial', 'HC-82', 'not-saved-before-setup@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false })) },
    [{ at: { plus: 1 }, op: 'storage', mode: 'broken' }, { at: { plus: 60 }, op: 'applyDial', P: dial('sleep', 'start', 1320, 540, 1380, 1320) }]);

  /* ---- applyOutput and its refusal (HC-40, HC-41) ---- */
  ZONES.slice(0, 4).forEach(z => {
    const bad = { status: 'INVARIANT_VIOLATION', validation: { violatedInvariants: Array.from({ length: 12 }, (_, k) => ({ code: 'INV_' + k, detail: 'd' + k })) } };
    planned('output', 'HC-40', 'refused@' + z, z, [{ at: { plus: 60 }, op: 'applyOutput', out: bad, label: 'x' }, { at: { plus: 60 }, op: 'applyOutput', label: 'Kept', mode: 'repair' },
      { at: { plus: 60 }, op: 'applyOutput' }, T('replan', {})]);
  });

  /* ---- early-done repair (HC-42) ---- */
  ZONES.slice(0, 4).forEach(z => {
    const at = morning(z);
    const h = (id, start, resolved, state, more) => Object.assign({ instanceId: id, taskId: id, title: 'S ' + id, tag: 'reading', linkedGoalIds: [], linkedQuestRequirementIds: [],
      startEpoch: start, endEpoch: start + 3600, durationSlots: 4, lifecycleState: state || 'COMPLETED_ON_TIME', completionEventId: 'ev_' + id, hostActivityId: 'ld_ev_' + id,
      actualMinutes: 50, resolvedAtEpoch: resolved, revision: 1 }, more || {});
    const s = at / 1000;
    const hist = [h('e1', s + 3 * 86400, s - 3600), h('e2', s + 2 * 86400, s - 3600, 'PARTIALLY_COMPLETED', { actualMinutes: null }), h('e3', s + 3600, s - 600),
      h('e4', s + 3 * 86400, s - 3600, 'SKIPPED_VOLUNTARY'), h('e5', s + 3 * 86400, s - 3600, 'COMPLETED_EARLY', { adhoc: true }), h('e6', s + 86400 * 4, null),
      h('e7', s - 86400, s - 3 * 86400), h('e8', s + 86400 * 5, s - 7200), h('e9', s + 13 * 3600, s), h('old', s - 20.5 * 86400, s - 21 * 86400)];
    const sched = { horizonStartEpoch: s - 86400, horizonEndEpoch: s + 6 * 86400, timezone: z, scheduledInstances: [h('p1', s + 86400 * 5 + 1800, null, 'PLANNED')], historyInstances: hist };
    add('early', 'HC-42', 'repair@' + z, { at, saved: JSON.stringify(base(z, { more: { schedule: sched } })) }, [{ at: { plus: 1 }, op: 'repairEarlyDone' }, { at: { plus: 1 }, op: 'repairEarlyDone' }]);
    add('early', 'HC-42', 'one@' + z, { at, saved: JSON.stringify(base(z, { more: { schedule: Object.assign({}, sched, { historyInstances: [hist[0]] }) } })) }, [{ at: { plus: 1 }, op: 'syncHost' }]);
  });
  add('early', 'HC-42', 'no-schedule@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { more: { fixes: { other: 1 } } })) }, [{ at: { plus: 1 }, op: 'repairEarlyDone' }]);
  add('early', 'HC-42', 'done-already@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { more: { fixes: { earlyDone: 1 } } })) }, [{ at: { plus: 1 }, op: 'syncHost' }]);

  /* ---- boot (HC-01 to HC-03) ---- */
  add('boot', 'HC-1', 'new@UTC', { at: morning('UTC'), saved: null, host: { playerId: 'lm_abc' } }, [T('replan', {})]);
  add('boot', 'HC-1', 'blocked@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC')), storage: 'blocked' }, [T('replan', {})]);
  add('boot', 'HC-1', 'migrated@UTC', { at: morning('UTC'), saved: JSON.stringify({ sleep: { bed: 1380, wake: 420 }, work: [{ startMin: 540, durationMin: 480, days: [1, 2, 3] }], goals: [] }) },
    [T('replan', {})]);
  add('boot', 'HC-1', 'unreadable@UTC', { at: morning('UTC'), saved: '{oops' }, [D('finishSetup', {})]);
  add('boot', 'HC-1', 'recovered@UTC', { at: morning('UTC'), saved: '{oops', bak: JSON.stringify(base('UTC')) }, [T('replan', {})]);
  add('boot', 'HC-1', 'invalid-main@UTC', { at: morning('UTC'), saved: JSON.stringify({ schemaVersion: 3 }), bak: JSON.stringify(base('UTC')) }, [T('replan', {})]);
  add('boot', 'HC-1', 'offline-lab@UTC', { at: morning('UTC'), saved: JSON.stringify(base('UTC', { setup: false })), host: { connected: false } }, [D('labToggle', { on: false })]);

  /* ---- fuzz: seeded sequences of commands (HC-90) ---- */
  const OPS = [
    r => T('done', { id: P(Math.floor(r() * 4)), minutes: [undefined, 10, 30, 90][Math.floor(r() * 4)] }, P(Math.floor(r() * 2), 'startEpoch', Math.floor(r() * 7200) - 3600)),
    r => T('skip', { id: P(Math.floor(r() * 4)) }), r => T('unskip', { id: Hist(-1), op: 'u' + Math.floor(r() * 2) }, { plus: Math.floor(r() * 200) }),
    r => T('pin', { id: P(Math.floor(r() * 4)), on: r() < 0.5 }), r => T('moveTo', { id: P(Math.floor(r() * 4)), start: P(Math.floor(r() * 4), 'startEpoch', 900 * Math.floor(r() * 9 - 4)) }),
    r => T('logToday', { id: P(-1 - Math.floor(r() * 3)), op: 'f' + Math.floor(r() * 3), minutes: 15 + Math.floor(r() * 60) }),
    r => T('disrupt', { kind: 'block', op: 'fb' + Math.floor(r() * 3), hours: [1, 2, 3][Math.floor(r() * 3)] }),
    r => ({ at: { plus: Math.floor(r() * 30 * 3600) }, op: 'tick' }), r => T('replan', {}), r => T('energy', { level: ['LOW', 'NORMAL', 'HIGH'][Math.floor(r() * 3)] }),
    r => D('restDay', { day: Math.floor(r() * 7) }), r => D('goalPriority', { id: ref('goal', 0), value: 1 + Math.floor(r() * 5) }),
    r => ({ at: { plus: 60 }, op: 'host', host: { award: ['ok', 'failed', 'offline'][Math.floor(r() * 3)] } }),
  ];
  for (let seed = 1; seed <= 160; seed++) {
    const r = random(seed * 7919), z = ZONES[seed % ZONES.length], steps = [];
    const n = 3 + Math.floor(r() * 6);
    for (let k = 0; k < n; k++) steps.push(OPS[Math.floor(r() * OPS.length)](r));
    const goals = [['FITNESS_HYPERTROPHY'], ['HABIT_READING', 'FITNESS_CARDIO'], ['SKILL_LANGUAGE']][seed % 3];
    const prefs = [{}, { consequenceMode: 'MODERATE' }, { consequenceMode: 'HARDCORE_RPG', missGraceMinutes: 15 }][seed % 3];
    planned('fuzz', 'HC-90', 'seed-' + seed + '@' + z, z, steps, { goals, prefs, at: morning(z, 2 + (seed % 5), 300 + (seed % 12) * 60) });
  }
  return cases;
}

module.exports = { defineCases, FAMILIES, ZONES, base };
