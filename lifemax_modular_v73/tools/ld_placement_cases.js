/* The Life Director engine's placement golden cases (WP-LD4): what tools/gen_ld_placement_golden.js records from the page and the native
   placement code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce. Rules are
   LIFE_DIRECTOR_ENGINE_SPEC.md's part 4 (LDR preparation and energy, LDK feasibility, LDU scoring, LDW construction, LDJ rejections).

   Every case is {id, family, rule, fn, args}; the recorder adds out. As in tools/ld_kernel_cases.js the definitions use no Intl: every
   instant comes from Date.UTC and the clock-change table of that file, and only the strict zones of the WP-LD1 matrix are used. The seeded
   inputs that re-plan an earlier plan take it from the engine itself, run here in node when the cases are defined: the plan only supplies
   inputs (its sessions become the existing schedule and the history), so node's last-bit differences in exp (LD20) cannot change a
   recorded answer, only which input is asked. */
'use strict';
const { MATRIX, FIXED, CHANGES } = require('./ld_kernel_cases.js');

const FAMILIES = ['prepare', 'anchors', 'scenario', 'seeds', 'dst', 'rules', 'fuzz', 'small'];
const ZONES = MATRIX;
const SCENARIO_NAMES = ['office', 'night', 'rotating', 'student', 'abundant', 'limited', 'manygoals', 'deadlines', 'misses', 'workchange'];
const ARCH_IDS = ['FITNESS_HYPERTROPHY', 'SKILL_LANGUAGE', 'HABIT_READING', 'ADMIN_FINANCE', 'FITNESS_CARDIO', 'STUDY_GENERAL', 'CREATIVE_PRACTICE', 'MINDFULNESS'];
const STATES = ['PLANNED', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED_VOLUNTARY', 'MISSED', 'PARTIAL'];

/* ---------- small helpers (no Intl) ---------- */
const utc = (y, mo, d, h = 0, mi = 0, s = 0) => Date.UTC(y, mo - 1, d, h, mi, s) / 1000;
const changes = CHANGES;
function offsetAt(zone, t) {
  if (zone in FIXED) return FIXED[zone];
  let off = null;
  for (const c of changes.filter(c => c.zone === zone)) { if (off === null) off = c.before; if (t >= c.at) off = c.after; }
  return off;
}
const localInstant = (zone, y, mo, d, min = 0) => { const w = utc(y, mo, d, 0, min); return w - offsetAt(zone, w - offsetAt(zone, w) * 60) * 60; };
/* Seeded generator (xorshift32; not the engine's mulberry32). */
function random(seed) {
  let x = seed >>> 0 || 1;
  return () => { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; };
}
const copy = v => JSON.parse(JSON.stringify(v));
/* Moves every member named ...Epoch by shift seconds, at any depth. */
function moveEpochs(v, shift) {
  if (Array.isArray(v)) v.forEach(x => moveEpochs(x, shift));
  else if (v && typeof v === 'object') for (const k of Object.keys(v)) {
    if (/Epoch$/.test(k) && typeof v[k] === 'number') v[k] += shift; else moveEpochs(v[k], shift);
  }
  return v;
}
/* A built-in scenario moved to zone, its clock at start (every epoch shifted with it, the zone set on the input and its availability). */
function scenarioAt(engine, name, seed, zone, start) {
  const inp = copy(engine.buildScenario(name, seed).input), shift = start - inp.currentTimeEpoch;
  moveEpochs(inp, shift);
  inp.timezone = zone; inp.playerAvailability.timezone = zone;
  return inp;
}
/* A session of an earlier plan as a store keeps it: the members placement reads, and the state it is in. */
const SESSION_KEYS = ['instanceId', 'taskId', 'templateId', 'title', 'tag', 'kind', 'goalId', 'linkedGoalIds', 'linkedQuestRequirementIds', 'startEpoch', 'endEpoch',
  'durationSlots', 'tier', 'IP', 'IC', 'spacingKey'];
const stored = (inst, state, locked) => Object.assign(Object.fromEntries(SESSION_KEYS.filter(k => inst[k] !== undefined).map(k => [k, copy(inst[k])])),
  { lifecycleState: state, isUserLocked: !!locked });

function defineCases(engine) {
  const cases = [], ids = new Set();
  const add = (family, rule, fn, args, tag) => {
    let id = family + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args: copy(args) });
  };
  const monday = zone => localInstant(zone, 2026, 10, 26, 360);
  const PREP_RULES = 'LDR-1 LDR-2 LDR-3 LDR-4 LDR-5 LDR-6';
  const PLACE_RULES = 'LDR-1 LDR-4 LDR-5 LDK-1 LDK-2 LDU-1 LDU-2 LDW-1 LDW-2 LDW-3 LDW-4 LDW-7 LDJ-1';

  /* prepare: prepareState on the scenarios in five strict zones, and on re-plans of their plans (sessions kept, started, displaced) */
  for (const name of SCENARIO_NAMES) for (const zone of ['UTC', 'America/New_York', 'Asia/Kolkata', 'Australia/Sydney', 'Pacific/Kiritimati'])
    add('prepare', PREP_RULES, 'prepare', [scenarioAt(engine, name, 1, zone, monday(zone))], name + '@' + zone);
  for (let s = 1; s <= 80; s++) add('prepare', PREP_RULES, 'prepare', [fuzzInput(s, engine, true)], 'seed' + s);
  for (const c of handPrepare(engine, monday)) add('prepare', c.rule, 'prepare', [c.input], c.tag);

  /* anchors: anchorsDependents on small task lists */
  const T = (id, placed, deps, cov) => Object.assign({ id, isPlaced: placed }, deps === undefined ? {} : { dependsOn: deps }, cov ? { coveredBy: cov } : {});
  const ANCH = [
    ['none', [], 'tA'], ['placed-dependent', [T('tB', true, ['tA'])], 'tA'], ['unplaced-dependent', [T('tB', false, ['tA'])], 'tA'],
    ['covered-dependent', [T('tB', true, ['tA'], 'iX')], 'tA'], ['other-prerequisite', [T('tB', true, ['tC'])], 'tA'],
    ['no-list', [T('tB', true)], 'tA'], ['null-list', [T('tB', true, null)], 'tA'], ['empty-list', [T('tB', true, [])], 'tA'],
    ['second-of-two', [T('tB', false, ['tA']), T('tC', true, ['tD', 'tA'])], 'tA'], ['self', [T('tA', true, ['tA'])], 'tA'],
    ['prefix-only', [T('tB', true, ['tAA'])], 'tA'], ['text-list', [T('tB', true, 'xtAx')], 'tA'], ['text-list-miss', [T('tB', true, 'tB')], 'tA'],
    ['covered-empty-text', [Object.assign(T('tB', true, ['tA']), { coveredBy: '' })], 'tA'], ['undefined-task', [T('tB', true, ['undefined'])], undefined]];
  for (const [tag, tasks, taskId] of ANCH) add('anchors', 'LDW-5', 'anchors', [tasks, taskId === undefined ? null : taskId], tag);

  /* scenario: placement (generateSchedule up to its repair) on the ten scenarios in every strict zone */
  for (const name of SCENARIO_NAMES) for (const zone of ZONES) add('scenario', PLACE_RULES, 'place', [scenarioAt(engine, name, 1, zone, monday(zone)), 'all'], name + '@' + zone);
  /* seeds: the ten scenarios at seeds 2 to 12 (their work hours move with the seed) in two zones */
  for (const name of SCENARIO_NAMES) for (let seed = 2; seed <= 12; seed++) for (const zone of ['UTC', 'Asia/Kolkata'])
    add('seeds', PLACE_RULES, 'place', [scenarioAt(engine, name, seed, zone, monday(zone)), 'few'], name + '#' + seed + '@' + zone);
  /* dst: three scenarios starting the morning before every 2026-2027 clock change of the strict zones */
  for (const c of changes.filter(c => ZONES.includes(c.zone))) {
    const local = new Date((c.at + c.before * 60) * 1000), y = local.getUTCFullYear(), mo = local.getUTCMonth() + 1, d = local.getUTCDate();
    for (const name of ['office', 'night', 'manygoals']) for (const back of [1, 0]) {
      const start = localInstant(c.zone, y, mo, d - back, 360);
      add('dst', PLACE_RULES + ' LDR-2', 'place', [scenarioAt(engine, name, 1, c.zone, start), 'few'], name + '@' + c.zone + '@' + new Date(c.at * 1000).toISOString().slice(0, 10) + '-' + back);
    }
  }
  /* rules: one behaviour each, on a small plan */
  for (const c of handPlace(engine, monday)) add('rules', c.rule, 'place', [c.input, 'all'], c.tag);
  /* fuzz: seeded inputs, a third of them re-plans of the engine's own earlier plan */
  for (let s = 1; s <= 700; s++) add('fuzz', PLACE_RULES + ' LDR-2 LDR-3', 'place', [fuzzInput(s, engine, false), 'few'], 'seed' + s);
  /* small: seeded short plans (one to three days, few goals and quests), many of them, one task probed */
  for (let s = 1; s <= 1600; s++) add('small', PLACE_RULES + ' LDR-2 LDR-3', 'place', [fuzzInput(100000 + s, engine, false, true), 'one'], 'seed' + s);
  return cases;
}

/* A small plan: two UTC days from Monday 06:00, sleep 23:00-07:00, office hours on weekdays, the given goals, quests and preferences. */
function small(zone, start, extra) {
  return Object.assign({
    timezone: zone, currentTimeEpoch: start, horizonStartEpoch: start, horizonDays: 2,
    playerAvailability: { timezone: zone, sleepRequirementMinutes: 480, rules: [
      { id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
      { id: 'work', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480, commuteMin: 30, load: 'MIXED_MODERATE' }] },
    fixedCommitments: [], goals: [], activeQuests: [], existingSchedule: [], completionHistory: [], playerPreferences: {}
  }, extra || {});
}
const goal = (id, arch, priority, level, more) => Object.assign({ id, title: id, archetypeId: arch, priority, level: level || 'NOVICE' }, more || {});

function handPrepare(engine, monday) {
  const z = 'UTC', t = monday(z), out = [];
  const base = small(z, t, { goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 4), goal('g-b', 'SKILL_LANGUAGE', 3, 'NOVICE', { subject: 'spanish' })] });
  const plan = engine.generateSchedule(base).scheduledInstances;
  const at = (i, d) => Object.assign({}, plan[i], { startEpoch: plan[i].startEpoch + d, endEpoch: plan[i].endEpoch + d });
  const re = (sched, more) => Object.assign(copy(base), { existingSchedule: sched }, more || {});
  const p = (tag, input, rule) => out.push({ tag, input, rule: rule || 'LDR-1 LDR-3 LDR-4' });
  p('existing-planned', re(plan.map(i => stored(i, 'PLANNED'))));
  p('existing-locked', re(plan.map((i, k) => stored(i, 'PLANNED', k % 2 === 0))));
  p('existing-started', re(plan.map(i => stored(i, 'IN_PROGRESS')), { currentTimeEpoch: plan[1].startEpoch + 900 }));
  p('existing-history', re(plan.map((i, k) => stored(i, STATES[3 + (k % 4)]))));
  p('existing-before-horizon', re([stored(at(0, -3 * 86400), 'PLANNED'), stored(at(1, -3 * 86400), 'PLANNED', true)]));
  p('existing-after-horizon', re([stored(at(0, 9 * 86400), 'PLANNED', true)]));
  p('locked-on-work', re([stored(Object.assign({}, plan[0], { startEpoch: t + 4 * 3600, endEpoch: t + 5 * 3600 }), 'PLANNED', true)]));
  p('locked-on-sleep', re([stored(Object.assign({}, plan[0], { startEpoch: t + 18 * 3600, endEpoch: t + 19 * 3600 }), 'PLANNED', true)]));
  p('started-on-sleep', re([stored(Object.assign({}, plan[0], { startEpoch: t - 1800, endEpoch: t + 1800 }), 'IN_PROGRESS')]));
  p('started-ended', re([stored(Object.assign({}, plan[0], { startEpoch: t - 7200, endEpoch: t - 3600 }), 'IN_PROGRESS')]));
  p('locked-overlapping', re([stored(at(0, 0), 'PLANNED', true), stored(at(0, 900), 'PLANNED', true)]));
  p('locked-same-start', re([Object.assign(stored(at(0, 0), 'PLANNED', true), { instanceId: 'i_b' }), Object.assign(stored(at(0, 0), 'PLANNED', true), { instanceId: 'i_a' })]));
  p('locked-off-grid', re([stored(Object.assign({}, plan[0], { startEpoch: plan[0].startEpoch + 420, endEpoch: plan[0].endEpoch + 120 }), 'PLANNED', true)]));
  p('locked-no-goal-links', re([Object.assign(stored(at(0, 0), 'PLANNED', true), { linkedGoalIds: [] })]));
  p('locked-two-goal-links', re([Object.assign(stored(at(0, 0), 'PLANNED', true), { linkedGoalIds: ['g-a', 'g-b'] })]));
  p('locked-no-links', re([(() => { const s = stored(at(0, 0), 'PLANNED', true); delete s.linkedGoalIds; return s; })()]));
  p('locked-intensity-text', re([Object.assign(stored(at(0, 0), 'PLANNED', true), { IP: '5', IC: 300 })]));
  p('locked-no-intensity', re([(() => { const s = stored(at(0, 0), 'PLANNED', true); delete s.IP; delete s.IC; delete s.spacingKey; return s; })()]));
  p('locked-no-bwc', re([stored(Object.assign({}, plan[0], { startEpoch: t + 20 * 3600, endEpoch: t + 21 * 3600 }), 'PLANNED', true)]));
  for (const [tag, ck] of [['checkin-low', { epoch: t, level: 'LOW' }], ['checkin-normal', { epoch: t + 3600, level: 'NORMAL' }], ['checkin-high', { epoch: t - 7200, level: 'HIGH' }],
    ['checkin-late', { epoch: t + 30 * 3600, level: 'LOW' }], ['checkin-odd-level', { epoch: t, level: 'TIRED' }], ['checkin-zero-epoch', { epoch: 0, level: 'HIGH' }],
    ['checkin-off-grid', { epoch: t + 1234, level: 'HIGH' }], ['checkin-text-epoch', { epoch: String(t), level: 'LOW' }]])
    p(tag, Object.assign(copy(base), { energyProfile: { checkin: ck } }), 'LDR-2 LDR-5');
  for (const need of [0, 360, 420, 600, 720, '540', 1e9]) p('sleep-need-' + need, Object.assign(copy(base), { playerAvailability: Object.assign(copy(base.playerAvailability), { sleepRequirementMinutes: need }) }), 'LDR-2');
  for (const rec of [30, 37, 100, 120, 240, 360]) p('recovery-' + rec, Object.assign(copy(base), { playerPreferences: { minDailyRecoveryMinutes: rec } }), 'LDR-4');
  for (const lead of [0, 7, 60, 600, -30]) p('lead-' + lead, Object.assign(copy(base), { currentTimeEpoch: t + 3 * 3600 + 120, playerPreferences: { leadMinutes: lead } }), 'LDR-1 LDR-4');
  p('now-mid-horizon', Object.assign(copy(base), { currentTimeEpoch: t + 30 * 3600 }), 'LDR-1 LDR-4');
  p('now-past-horizon', Object.assign(copy(base), { currentTimeEpoch: t + 5 * 86400 }), 'LDR-1 LDR-4');
  p('history-spacing', Object.assign(copy(base), { completionHistory: [{ startEpoch: t - 86400, templateId: 'g-a:strength', spacingKey: 'g-a:strength' },
    { startEpoch: t - 3600, spacingKey: 'g-b:vocab' }, { startEpoch: t - 7200, templateId: 'x' }, { startEpoch: t - 99 * 86400, spacingKey: 'g-a:strength' }] }), 'LDR-1');
  return out;
}

function handPlace(engine, monday) {
  const z = 'UTC', t = monday(z), out = [];
  const g = (tag, input, rule) => out.push({ tag, input, rule });
  const one = (arch, more, input) => small(z, t, Object.assign({ goals: [goal('g-x', arch, 4, 'INTERMEDIATE')] }, input || {}, more || {}));
  for (const a of ARCH_IDS) g('archetype-' + a, one(a), 'LDU-1 LDW-1');
  g('avoid-window', one('SKILL_LANGUAGE', { playerPreferences: { avoidWindows: [{ startMin: 420, endMin: 540 }, { startMin: 1020, endMin: 1200 }] } }), 'LDU-1');
  g('avoid-window-text', one('SKILL_LANGUAGE', { playerPreferences: { avoidWindows: [{ startMin: '420', endMin: '1200' }] } }), 'LDU-1');
  g('rest-day-high', one('FITNESS_HYPERTROPHY', { playerPreferences: { preferredRestDays: [1] } }), 'LDU-1');
  g('rest-day-text', one('FITNESS_HYPERTROPHY', { playerPreferences: { preferredRestDays: ['1'] } }), 'LDU-1');
  g('rest-day-mealprep', small(z, localInstant(z, 2026, 10, 24, 360), { horizonDays: 3, goals: [goal('g-x', 'FITNESS_HYPERTROPHY', 4)], playerPreferences: { preferredRestDays: [0, 6] } }), 'LDU-1');
  for (const level of ['LOW', 'NORMAL', 'HIGH']) g('checkin-' + level, one('STUDY_GENERAL', { energyProfile: { checkin: { epoch: t + 3600, level } } }), 'LDR-5 LDK-2 LDU-1');
  g('high-cap-1', small(z, t, { goals: [goal('g-x', 'FITNESS_HYPERTROPHY', 4), goal('g-y', 'STUDY_GENERAL', 4)], playerPreferences: { maxHighIntensityPerDay: 1 } }), 'LDK-2');
  g('high-cap-0', small(z, t, { goals: [goal('g-x', 'STUDY_GENERAL', 4)], playerPreferences: { maxHighIntensityPerDay: 0 } }), 'LDK-2 LDJ-1');
  g('consecutive-30', small(z, t, { horizonDays: 1, goals: [goal('g-x', 'STUDY_GENERAL', 4, 'ADVANCED'), goal('g-y', 'ADMIN_FINANCE', 4)], playerPreferences: { maxConsecutiveHighLoadMinutes: 30, intensityLevel: 'HARDCORE' } }), 'LDK-2');
  g('daily-cap-30', small(z, t, { goals: [goal('g-x', 'STUDY_GENERAL', 4), goal('g-y', 'HABIT_READING', 3)], playerPreferences: { maxDailyGoalMinutes: 30 } }), 'LDK-2 LDJ-1');
  g('heavy-shift', small(z, t, { horizonDays: 3, goals: [goal('g-x', 'FITNESS_HYPERTROPHY', 5, 'ADVANCED')], playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [
    { id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }, { id: 'w', kind: 'WORK', days: [1, 2, 3], startMin: 420, durationMin: 600, load: 'PHYSICAL_HEAVY' }] } }), 'LDK-2 LDJ-1');
  g('recovery-360', one('STUDY_GENERAL', { playerPreferences: { minDailyRecoveryMinutes: 360 } }), 'LDR-4 LDJ-1');
  g('lead-60', one('STUDY_GENERAL', { currentTimeEpoch: t + 7 * 3600 + 300, playerPreferences: { leadMinutes: 60 } }), 'LDR-1 LDK-1');
  g('short-sleep', one('FITNESS_CARDIO', { playerAvailability: { timezone: z, sleepRequirementMinutes: 600, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 60, durationMin: 300 }] } }), 'LDR-2');
  g('twin-goals', small(z, t, { goals: [goal('g-b', 'MINDFULNESS', 3), goal('g-a', 'MINDFULNESS', 3)] }), 'LDW-2 LDW-3');
  g('twin-custom', small(z, t, { goals: ['g-2', 'g-1', 'g-3'].map(id => ({ id, title: 'Same', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Same', tag: 'chores', minutes: 30, perWeek: 7 }] })) }), 'LDW-2 LDW-3');
  g('fair-cap', small(z, t, { horizonDays: 7, goals: [goal('g-x', 'STUDY_GENERAL', 5, 'ADVANCED'), goal('g-y', 'MINDFULNESS', 1), goal('g-z', 'HABIT_READING', 1)], playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 480 } }), 'LDW-4 LDU-2');
  g('fair-cap-only', small(z, t, { horizonDays: 2, goals: [goal('g-x', 'STUDY_GENERAL', 5, 'ADVANCED'), goal('g-y', 'MINDFULNESS', 1)], playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }] }, playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 480 } }), 'LDW-4');
  const quests = (more) => small(z, t, Object.assign({ horizonDays: 3, goals: [goal('g-x', 'SKILL_LANGUAGE', 4, 'NOVICE', { subject: 'spanish' }), goal('g-y', 'FITNESS_HYPERTROPHY', 4)] }, more));
  g('quest-chain', quests({ activeQuests: [{ id: 'q-a', title: 'A', tier: 'CORE', tag: 'study.deep', requiredCount: 2, deadlineEpoch: t + 3 * 86400 - 60 },
    { id: 'q-b', title: 'B', tier: 'CORE', tag: 'admin.finance', requiredCount: 1, prerequisiteQuestIds: ['q-a'], deadlineEpoch: t + 3 * 86400 - 60 }] }), 'LDK-1 LDW-5');
  g('quest-chain-blocked', quests({ activeQuests: [{ id: 'q-a', title: 'A', tier: 'CORE', tag: 'study.deep', requiredCount: 1, deadlineEpoch: t + 3600 },
    { id: 'q-b', title: 'B', tier: 'CORE', tag: 'admin.finance', requiredCount: 1, prerequisiteQuestIds: ['q-a'], deadlineEpoch: t + 3 * 86400 - 60 }] }), 'LDK-1 LDJ-1');
  g('quest-daily-cover', quests({ activeQuests: [{ id: 'q-d', title: 'Daily vocab', tier: 'CORE', tag: 'language.spanish.vocab', requiredCount: 1, recurrence: 'DAILY' }] }), 'LDW-1 LDW-6');
  g('quest-daily-exclusive', quests({ activeQuests: [{ id: 'q-d', title: 'Daily lift', tier: 'CORE', tag: 'fitness.strength', requiredCount: 1, recurrence: 'DAILY', exclusive: true }] }), 'LDW-6');
  g('quest-daily-wild', quests({ activeQuests: [{ id: 'q-d', title: 'Daily lang', tier: 'OPTIONAL', tag: 'language.*', requiredCount: 1, recurrence: 'DAILY' }] }), 'LDW-6');
  g('quest-critical', quests({ activeQuests: [{ id: 'q-c', title: 'Tonight', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 1, deadlineEpoch: t + 14 * 3600 }] }), 'LDU-1 LDW-1');
  g('quest-merged', quests({ activeQuests: [{ id: 'q-m', title: 'Lift 2', tier: 'CORE', tag: 'fitness.strength', requiredCount: 2, deadlineEpoch: t + 2 * 86400 }] }), 'LDW-1');
  g('quest-minutes', quests({ activeQuests: [{ id: 'q-n', title: 'Long read', tier: 'CORE', tag: 'reading.focused', requiredMinutes: 240, deadlineEpoch: t + 3 * 86400 - 60 }] }), 'LDK-1 LDU-1');
  g('quest-long-session', quests({ horizonDays: 1, activeQuests: [{ id: 'q-l', title: 'Marathon', tier: 'CORE', tag: 'creative.practice', requiredCount: 1, sessionMinutes: 300, minSessionMinutes: 120, deadlineEpoch: t + 86400 - 60 }] }), 'LDU-1 LDJ-1');
  g('quest-deadline-gone', quests({ activeQuests: [{ id: 'q-g', title: 'Soon', tier: 'CORE', tag: 'admin.finance', requiredCount: 1, deadlineEpoch: t + 1800 }] }), 'LDJ-1');
  g('prior-volume', small(z, t, { horizonDays: 7, goals: [goal('g-x', 'SKILL_LANGUAGE', 4, 'ADVANCED', { subject: 'spanish' })], completionHistory: [
    { startEpoch: t - 2 * 86400, templateId: 'g-x:vocab', spacingKey: 'g-x:vocab' }, { startEpoch: t - 8 * 86400, templateId: 'g-x:grammar', spacingKey: 'g-x:grammar' }] }), 'LDK-1');
  const plan = engine.generateSchedule(one('STUDY_GENERAL', { horizonDays: 3 })).scheduledInstances;
  const moved = (d) => one('STUDY_GENERAL', { horizonDays: 3, currentTimeEpoch: t + d, existingSchedule: plan.map(i => stored(i, 'PLANNED')) });
  for (const d of [0, 3600, 26 * 3600, 50 * 3600]) g('replan-move+' + d, moved(d), 'LDU-1 LDR-3');
  g('replan-locked', one('STUDY_GENERAL', { horizonDays: 3, existingSchedule: plan.map((i, k) => stored(i, 'PLANNED', k === 0)) }), 'LDR-3 LDK-2');
  g('history-spacing', one('FITNESS_HYPERTROPHY', { completionHistory: [{ startEpoch: t - 20 * 3600, templateId: 'g-x:strength', spacingKey: 'g-x:strength' }] }), 'LDK-2 LDU-1');
  g('history-spacing-far', one('FITNESS_HYPERTROPHY', { completionHistory: [{ startEpoch: t - 70 * 3600, templateId: 'g-x:strength', spacingKey: 'g-x:strength' }] }), 'LDU-1');
  g('no-time', one('STUDY_GENERAL', { playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'w', kind: 'WORK', startMin: 420, durationMin: 900 }] } }), 'LDJ-1');
  g('custom-odd', small(z, t, { goals: [{ id: 'g-c', title: 'Odd', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Odd', tag: 'creative.practice', minutes: 45, perWeek: 5,
    phases: 'SECONDARY', spacingMinH: '6', spacingIdealH: '30', maxPerBwc: '2', IP: 4 }] }] }), 'LDK-2 LDU-1');
  g('custom-phases-object', small(z, t, { goals: [{ id: 'g-c', title: 'Odd', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Odd', tag: 'chores', minutes: 30, perWeek: 3, phases: { PEAK: 1 } }] }] }), 'LDU-1');
  g('custom-no-spacing', small(z, t, { goals: [{ id: 'g-c', title: 'Odd', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Odd', tag: 'social', minutes: 60, perWeek: 14, spacingMinH: 0, spacingIdealH: 1, maxPerBwc: 9 }] }] }), 'LDK-2 LDU-1');
  g('custom-near-spacing', small(z, t, { horizonDays: 1, goals: [{ id: 'g-c', title: 'Often', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Often', tag: 'social', minutes: 30,
    perWeek: 14, spacingMinH: 0, spacingIdealH: 2, maxPerBwc: 9 }] }], playerPreferences: { intensityLevel: 'HARDCORE' } }), 'LDU-1');
  g('recovery-100', one('STUDY_GENERAL', { playerPreferences: { minDailyRecoveryMinutes: 100 } }), 'LDR-4 LDJ-1');
  g('preferred-window', one('CREATIVE_PRACTICE', { playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'pref', kind: 'PREFERRED', startMin: 1140, durationMin: 120 }] } }), 'LDU-1');
  g('ten-goals-one-day', small(z, t, { horizonDays: 1, goals: ARCH_IDS.map((a, i) => goal('g-' + i, a, 1 + (i % 5), 'ADVANCED', a === 'SKILL_LANGUAGE' ? { subject: 'french' } : {})),
    playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }] }, playerPreferences: { intensityLevel: 'HARDCORE', mealsEnabled: false } }), 'LDW-1 LDW-4 LDU-1');
  g('throw-no-links', one('STUDY_GENERAL', { existingSchedule: [(() => { const s = stored(plan[0], 'PLANNED', true); delete s.linkedGoalIds; return s; })()] }), 'LDR-3');
  return out;
}

/* One seeded engine input: zone, horizon, clock, rules (work with every load, classes, unavailable and preferred windows, rotations,
   validity dates), one-off commitments and blackouts, sleep, goals of every archetype and custom ones, quests of every kind with
   prerequisites, history, a check-in and preferences; with replan, or for a third of the others, the engine's own plan of that input
   becomes the existing schedule of a later clock (planned, locked, started, done, skipped, moved). short: one to three days, at most
   three goals and two quests. */
function fuzzInput(seed, engine, replan, short) {
  const r = random(seed * 104729 + 7), pick = a => a[Math.floor(r() * a.length)], chance = p => r() < p, int = (a, b) => a + Math.floor(r() * (b - a + 1));
  const zone = pick(ZONES), days = short ? pick([1, 1, 2, 2, 3]) : pick([1, 2, 3, 4, 5, 7, 7, 7, 8, 10, 14]);
  const start = localInstant(zone, pick([2026, 2027]), pick([1, 3, 4, 6, 9, 10, 11, 12]), int(1, 28), int(0, 95) * 15);
  const now = start + (chance(0.3) ? int(0, days * 40) * 900 + (chance(0.2) ? int(1, 899) : 0) : 0);
  const rules = [];
  if (chance(0.85)) rules.push({ id: 'sleep', kind: 'SLEEP', startMin: pick([1260, 1320, 1380, 1410, 0, 60, 480, 600]), durationMin: pick([360, 420, 480, 480, 540, 600]) });
  if (chance(0.1)) rules.push({ id: 'nap', kind: 'SLEEP', days: [0, 6], startMin: 840, durationMin: pick([60, 240]) });
  const LOADS = ['MIXED_MODERATE', 'PHYSICAL_HEAVY', 'COGNITIVE_HEAVY', 'LIGHT', undefined];
  if (chance(0.75)) rules.push({ id: 'work', kind: 'WORK', days: pick([[1, 2, 3, 4, 5], [1, 2, 3, 4, 5, 6], [0, 2, 4], [3]]), startMin: pick([360, 420, 480, 540, 600, 720, 1320]),
    durationMin: pick([240, 360, 480, 510, 600, 720]), commuteMin: pick([0, 0, 15, 30, 45]), load: pick(LOADS), decompressMin: chance(0.2) ? pick([0, 45]) : undefined });
  if (chance(0.2)) rules.push({ id: 'rot', kind: 'WORK', startMin: 420, durationMin: 720, load: 'PHYSICAL_HEAVY', rotation: { anchorDate: '2026-01-01', cycleLength: 8, activeDays: [0, 1, 2, 3] } });
  if (chance(0.2)) rules.push({ id: 'class', kind: 'CLASS', days: [2, 4], startMin: 1080, durationMin: 120, commuteMin: 15 });
  if (chance(0.15)) rules.push({ id: 'busy', kind: 'UNAVAILABLE', days: [0, 6], startMin: 600, durationMin: 240 });
  if (chance(0.25)) rules.push({ id: 'pref', kind: 'PREFERRED', startMin: pick([360, 1080, 1140]), durationMin: pick([60, 120, 180]) });
  if (chance(0.1)) rules.push({ id: 'late', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 780, durationMin: 300, validFrom: '2026-06-01', validTo: '2026-12-31' });
  const fixedCommitments = [], blackouts = [];
  for (let i = 0, n = chance(0.3) ? int(1, 3) : 0; i < n; i++) {
    const s = start + int(0, days * 96) * 900;
    fixedCommitments.push({ id: 'c' + i, title: 'One-off ' + i, startEpoch: s, endEpoch: s + pick([30, 60, 120, 240, 480]) * 60, kind: pick(['WORK', 'APPOINTMENT', 'EMERGENCY', 'CLASS']),
      commuteMin: pick([0, 20]), load: pick(LOADS) });
  }
  if (chance(0.1)) { const s = start + int(0, days * 24) * 3600; blackouts.push({ id: 'b0', startEpoch: s, endEpoch: s + pick([3, 8, 24]) * 3600, kind: pick(['SICK', 'BLACKOUT']) }); }
  const goals = [], nGoals = int(0, short ? 3 : 6);
  for (let i = 0; i < nGoals; i++) {
    const g = { id: 'g-' + seed + '-' + i, title: pick(['Gym', 'Spanish', 'Books', 'Money', 'Run', 'Exam', 'Guitar', 'Calm', 'Thing']), priority: pick([1, 2, 3, 4, 5, 3]),
      level: pick(['NOVICE', 'INTERMEDIATE', 'ADVANCED']), status: chance(0.92) ? 'ACTIVE' : 'PAUSED', createdEpoch: now - int(0, 90) * 86400 };
    if (chance(0.8)) g.archetypeId = pick(ARCH_IDS);
    else g.customTemplates = [{ title: g.title, tag: pick(['chores', 'social', 'fitness.cardio', 'study.deep', 'wellbeing', 'creative.practice']), minutes: pick([15, 30, 45, 60, 90]),
      perWeek: pick([1, 2, 3, 5, 7, 10]), IP: pick([1, 2, 3, 4, 5]), IC: pick([1, 2, 3, 4, 5]), volumeDriver: chance(0.3) }];
    if (g.archetypeId === 'SKILL_LANGUAGE' && chance(0.7)) g.subject = pick(['spanish', 'french']);
    if (chance(0.2)) g.disabledTemplates = [pick(['strength', 'mealprep', 'assess', 'vocab', 'check', 'checkpoint', 'review'])];
    if (chance(0.3)) g.target = { units: pick([100, 600, 3600]), completed: int(0, 400), unit: 'u', unitsPerHour: pick([5, 10, 30]), deadlineEpoch: now + int(-5, 120) * 86400 };
    goals.push(g);
  }
  const quests = [], nQuests = int(0, short ? 2 : 5), qids = Array.from({ length: nQuests }, (_, i) => 'q-' + i);
  for (let i = 0; i < nQuests; i++) {
    const q = { id: qids[i], title: 'Quest ' + i, tier: pick(['MANDATORY', 'CORE', 'CORE', 'OPTIONAL', 'SIDE']),
      tag: pick(['fitness.strength', 'fitness.*', 'language.spanish.vocab', 'language.*', 'reading.focused', 'study.deep', 'admin.finance', 'creative.practice', 'wellbeing', 'chores']),
      requiredCount: pick([1, 1, 2, 3]) };
    if (chance(0.2)) q.requiredMinutes = pick([30, 60, 120]);
    if (chance(0.3)) { q.recurrence = 'DAILY'; if (chance(0.3)) q.onlyDays = [int(0, 6), int(0, 6)]; }
    if (q.recurrence !== 'DAILY' || chance(0.2)) q.deadlineEpoch = now + int(1, (days + 1) * 24) * 3600 - (chance(0.5) ? 60 : 0);
    if (chance(0.15)) q.exclusive = true;
    if (chance(0.2)) q.sessionMinutes = pick([15, 30, 45, 60]);
    if (chance(0.1)) q.minSessionMinutes = pick([30, 45]);
    if (chance(0.3) && i > 0) q.prerequisiteQuestIds = [pick(qids.slice(0, i))];
    quests.push(q);
  }
  const templateIds = [].concat(...goals.map(g => (g.archetypeId ? engine.ARCHETYPES[g.archetypeId].templates.map(t => g.id + ':' + t.key) : [])));
  const history = [];
  for (let i = 0, n = int(0, 5); i < n && templateIds.length; i++) {
    const tid = pick(templateIds);
    history.push({ startEpoch: now - int(1, 12 * 24) * 3600, templateId: tid, spacingKey: chance(0.9) ? tid : undefined });
  }
  const prefs = { intensityLevel: pick(['LIGHT', 'BALANCED', 'BALANCED', 'AGGRESSIVE', 'HARDCORE']) };
  if (chance(0.25)) prefs.maxDailyGoalMinutes = pick([30, 60, 120, 240, 480]);
  if (chance(0.2)) prefs.minDailyRecoveryMinutes = pick([30, 60, 180, 300]);
  if (chance(0.2)) prefs.maxConsecutiveHighLoadMinutes = pick([30, 60, 120]);
  if (chance(0.2)) prefs.maxHighIntensityPerDay = pick([0, 1, 2, 5]);
  if (chance(0.2)) prefs.preferredRestDays = [int(0, 6)];
  if (chance(0.15)) prefs.avoidWindows = [{ startMin: pick([360, 720, 1080]), endMin: pick([480, 840, 1320]) }];
  if (chance(0.15)) prefs.leadMinutes = pick([0, 5, 30, 60]);
  if (chance(0.1)) prefs.mealsEnabled = false;
  if (chance(0.1)) prefs.morningRoutineMinutes = pick([0, 60]);
  const input = { timezone: zone, currentTimeEpoch: now, horizonStartEpoch: start, horizonDays: days,
    playerAvailability: { timezone: zone, sleepRequirementMinutes: pick([480, 480, 420, 540, 600, 0]), rules, blackouts },
    fixedCommitments, goals, activeQuests: quests, existingSchedule: [], completionHistory: history, playerPreferences: prefs };
  if (chance(0.25)) input.energyProfile = { checkin: { epoch: now + int(-12, 12) * 1800, level: pick(['LOW', 'NORMAL', 'HIGH', 'HIGH', 'MEH']) } };
  if (!(replan || chance(0.33))) return input;
  /* a re-plan: the engine's own plan of this input, a later clock, its sessions in every state */
  let plan;
  try { plan = engine.generateSchedule(input).scheduledInstances; } catch (e) { return input; }
  const later = now + int(0, Math.max(1, days * 8)) * 900 + (chance(0.3) ? int(1, 899) : 0);
  input.existingSchedule = plan.filter(() => chance(0.85)).map(i => {
    const s = stored(i, i.endEpoch <= later ? pick(['COMPLETED', 'MISSED', 'SKIPPED_VOLUNTARY', 'PLANNED']) : i.startEpoch <= later ? pick(['IN_PROGRESS', 'PLANNED']) : pick(STATES), chance(0.15));
    if (chance(0.1)) { const d = pick([-3600, -900, 900, 1800, 86400]); s.startEpoch += d; s.endEpoch += d; }
    return s;
  });
  for (const i of input.existingSchedule) if (i.lifecycleState === 'COMPLETED' && i.spacingKey) input.completionHistory.push({ startEpoch: i.startEpoch, templateId: i.templateId, spacingKey: i.spacingKey });
  input.currentTimeEpoch = later;
  if (chance(0.5)) input.horizonStartEpoch = later;
  return input;
}

/* The input builders are shared with the schedule cases (tools/ld_schedule_cases.js, WP-LD5). */
module.exports = { defineCases, FAMILIES, ZONES, SCENARIO_NAMES, ARCH_IDS, STATES, utc, localInstant, random, copy, scenarioAt, stored, small, goal,
  handPlace, fuzzInput };
