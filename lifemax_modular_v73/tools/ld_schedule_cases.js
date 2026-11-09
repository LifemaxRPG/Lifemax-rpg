/* The Life Director engine's schedule golden cases (WP-LD5): what tools/gen_ld_schedule_golden.js records from the page and the native
   code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce: everything generateSchedule does after the
   construction (the repair with its snapshots and roll-backs, the fill pass, the rejections, the explanations, segments, capacity, the
   goal allocation, quest coverage, the output and its inputHash) and validateSchedule. Rules are LIFE_DIRECTOR_ENGINE_SPEC.md part 5
   (LDX repair, LDO output, LDV validation).

   Every case is {id, family, rule, fn, args}; the recorder adds out. As in tools/ld_placement_cases.js (whose input builders this file
   reuses) the definitions use no Intl, only the strict zones of the WP-LD1 matrix, and plans that serve as inputs (re-plans, validation
   inputs) are made by the engine in node when the cases are defined: they only choose the inputs, so node's last-bit differences in exp
   (LD20) cannot change a recorded answer. */
'use strict';
const { MATRIX, CHANGES } = require('./ld_kernel_cases.js');
const P = require('./ld_placement_cases.js');

const FAMILIES = ['scenario', 'seeds', 'dst', 'rules', 'fuzz', 'small', 'tight', 'replan', 'repair', 'validate', 'hash'];
const ZONES = MATRIX;
const { SCENARIO_NAMES, ARCH_IDS, localInstant, random, copy, scenarioAt, stored, small, goal } = P;

const REPAIR = 'LDX-1 LDX-2 LDX-3 LDX-4 LDX-5 LDX-6';
const OUTPUT = 'LDO-1 LDO-2 LDO-3 LDO-4 LDO-5 LDO-6 LDO-7 LDO-8 LDO-9';
const VALID = 'LDV-1 LDV-2 LDV-3 LDV-4 LDV-5 LDV-6';
const GEN = REPAIR + ' ' + OUTPUT + ' ' + VALID;

function defineCases(engine) {
  const cases = [], ids = new Set();
  const add = (family, rule, fn, args, tag) => {
    let id = family + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args: copy(args) });
  };
  const monday = zone => localInstant(zone, 2026, 10, 26, 360);

  /* scenario: the whole plan of the ten scenarios in every strict zone */
  for (const name of SCENARIO_NAMES) for (const zone of ZONES) add('scenario', GEN, 'generate', [scenarioAt(engine, name, 1, zone, monday(zone))], name + '@' + zone);
  /* seeds: the ten scenarios at seeds 2 to 8 in two zones */
  for (const name of SCENARIO_NAMES) for (let seed = 2; seed <= 8; seed++) for (const zone of ['UTC', 'Asia/Kolkata'])
    add('seeds', GEN, 'generate', [scenarioAt(engine, name, seed, zone, monday(zone))], name + '#' + seed + '@' + zone);
  /* dst: three scenarios starting the morning before every 2026-2027 clock change of the strict zones, and the morning of it */
  for (const c of CHANGES.filter(c => ZONES.includes(c.zone))) {
    const local = new Date((c.at + c.before * 60) * 1000), y = local.getUTCFullYear(), mo = local.getUTCMonth() + 1, d = local.getUTCDate();
    for (const name of ['office', 'limited', 'manygoals']) for (const back of [1, 0]) {
      const start = localInstant(c.zone, y, mo, d - back, 360);
      add('dst', GEN + ' LDO-3 LDO-4', 'generate', [scenarioAt(engine, name, 1, c.zone, start)], name + '@' + c.zone + '@' + new Date(c.at * 1000).toISOString().slice(0, 10) + '-' + back);
    }
  }
  /* rules: placement's one-behaviour plans (tools/ld_placement_cases.js) and this part's own */
  for (const c of P.handPlace(engine, monday)) add('rules', GEN, 'generate', [c.input], c.tag);
  for (const c of handSchedule(engine, monday)) add('rules', c.rule, 'generate', [c.input], c.tag);
  /* fuzz, small: placement's seeded inputs, planned to the end */
  for (let s = 1; s <= 500; s++) add('fuzz', GEN, 'generate', [P.fuzzInput(s, engine, false)], 'seed' + s);
  for (let s = 1; s <= 900; s++) add('small', GEN, 'generate', [P.fuzzInput(100000 + s, engine, false, true)], 'seed' + s);
  /* tight: plans with more urgent work than time, so the repair and the fill pass run */
  for (let s = 1; s <= 350; s++) add('tight', GEN, 'generate', [tightInput(s, engine)], 'seed' + s);
  /* chains: quest chains with a prerequisite of a lower tier (LD40) and goals the repair can move */
  for (let s = 1; s <= 80; s++) add('tight', GEN + ' LDW-5', 'generate', [chainInput(s)], 'chain' + s);
  /* replan: the engine's own plan re-planned with the clock moved on, sessions done, missed and moved, and the availability changed */
  for (let s = 1; s <= 250; s++) add('replan', GEN + ' LDR-3', 'generate', [replanInput(s, engine)], 'seed' + s);
  /* repair: the state at every repair target, after the repair and after the fill pass */
  for (const name of ['limited', 'office', 'abundant', 'manygoals']) for (const zone of ZONES)
    add('repair', REPAIR, 'repair', [scenarioAt(engine, name, 1, zone, monday(zone))], name + '@' + zone);
  for (let s = 1; s <= 220; s++) add('repair', REPAIR, 'repair', [tightInput(5000 + s, engine)], 'seed' + s);
  for (const c of handSchedule(engine, monday).filter(c => /^repair-/.test(c.tag))) add('repair', REPAIR, 'repair', [c.input], c.tag);
  /* validate: validateSchedule on plans broken one way at a time, and on malformed arguments */
  for (const c of handValidate(engine, monday)) add('validate', c.rule || VALID, 'validate', [c.v], c.tag);
  /* hash: inputHash on inputs whose nested members do and do not share the top-level names */
  for (const c of handHash(engine, monday)) add('hash', 'LDO-9', 'hash', [c.input], c.tag);
  return cases;
}

/* SchedulePerfTest's input (android-wrapper/native/src/test/java/com/lifemax/rpg/core/lifedirector): four weeks, every archetype at
   the hardcore intensity, eight quests mostly due in the first week. */
const FOUR_WEEKS = {
  "timezone": "Europe/London",
  "currentTimeEpoch": 1793001600,
  "horizonStartEpoch": 1793001600,
  "horizonDays": 28,
  "playerAvailability": {"timezone": "Europe/London", "sleepRequirementMinutes": 480, "rules": [
    {"id": "sleep", "kind": "SLEEP", "startMin": 1380, "durationMin": 480},
    {"id": "work", "kind": "WORK", "days": [1, 2, 3, 4, 5], "startMin": 540, "durationMin": 540, "commuteMin": 45, "load": "MIXED_MODERATE"},
    {"id": "weekend", "kind": "WORK", "days": [6], "startMin": 600, "durationMin": 360, "load": "PHYSICAL_HEAVY"},
    {"id": "gym-class", "kind": "CLASS", "days": [2, 4], "startMin": 1110, "durationMin": 60},
    {"id": "pref", "kind": "PREFERRED", "startMin": 1080, "durationMin": 120}] },
  "fixedCommitments": [
    {"id": "trip", "title": "Trip", "startEpoch": 1794200400, "endEpoch": 1794229200, "kind": "APPOINTMENT"}],
  "goals": [
    {"id": "g-1", "title": "Lift", "archetypeId": "FITNESS_HYPERTROPHY", "priority": 5, "level": "ADVANCED"},
    {"id": "g-2", "title": "Spanish", "archetypeId": "SKILL_LANGUAGE", "subject": "spanish", "priority": 4, "level": "ADVANCED"},
    {"id": "g-3", "title": "Read", "archetypeId": "HABIT_READING", "priority": 3, "level": "ADVANCED", "createdEpoch": 1790000000, "target": {"units": 3600, "completed": 200, "unit": "pages", "unitsPerHour": 30, "deadlineEpoch": 1800000000}},
    {"id": "g-4", "title": "Money", "archetypeId": "ADMIN_FINANCE", "priority": 3, "level": "ADVANCED"},
    {"id": "g-5", "title": "Run", "archetypeId": "FITNESS_CARDIO", "priority": 4, "level": "ADVANCED"},
    {"id": "g-6", "title": "Exam", "archetypeId": "STUDY_GENERAL", "priority": 5, "level": "ADVANCED"},
    {"id": "g-7", "title": "Guitar", "archetypeId": "CREATIVE_PRACTICE", "priority": 2, "level": "ADVANCED"},
    {"id": "g-8", "title": "Calm", "archetypeId": "MINDFULNESS", "priority": 2, "level": "ADVANCED"}],
  "activeQuests": [
    {"id": "q-1", "title": "Lift 3", "tier": "CORE", "tag": "fitness.strength", "requiredCount": 3, "deadlineEpoch": 1793606400},
    {"id": "q-2", "title": "Daily vocab", "tier": "CORE", "tag": "language.spanish.vocab", "requiredCount": 1, "recurrence": "DAILY"},
    {"id": "q-3", "title": "Tax", "tier": "MANDATORY", "tag": "admin.finance", "requiredCount": 4, "sessionMinutes": 90, "deadlineEpoch": 1793400000},
    {"id": "q-4", "title": "Chapter", "tier": "CORE", "tag": "study.deep", "requiredCount": 4, "prerequisiteQuestIds": ["q-3"], "deadlineEpoch": 1794000000},
    {"id": "q-5", "title": "Jam", "tier": "OPTIONAL", "tag": "creative.practice", "requiredMinutes": 240, "deadlineEpoch": 1795000000},
    {"id": "q-6", "title": "Essay", "tier": "MANDATORY", "tag": "study.deep", "requiredCount": 5, "sessionMinutes": 120, "minSessionMinutes": 60, "deadlineEpoch": 1793500000},
    {"id": "q-7", "title": "Books", "tier": "MANDATORY", "tag": "reading.focused", "requiredCount": 6, "sessionMinutes": 60, "deadlineEpoch": 1793450000},
    {"id": "q-8", "title": "Budget", "tier": "CORE", "tag": "admin.finance", "requiredCount": 3, "sessionMinutes": 60, "deadlineEpoch": 1793300000}],
  "existingSchedule": [],
  "completionHistory": [
    {"startEpoch": 1792900000, "templateId": "g-1:strength", "spacingKey": "g-1:strength"}],
  "energyProfile": {"checkin": {"epoch": 1793008800, "level": "NORMAL"}},
  "playerPreferences": {"intensityLevel": "HARDCORE", "maxDailyGoalMinutes": 240, "preferredRestDays": [0]}
};

/* A seeded plan with more urgent work than time: one to five days, long shifts, two to five goals at a high intensity, three to eight
   quests with deadlines inside the plan, sometimes a low daily cap; a quarter re-plan the engine's own plan. */
function tightInput(seed, engine) {
  const r = random(seed * 7919 + 13), pick = a => a[Math.floor(r() * a.length)], chance = p => r() < p, int = (a, b) => a + Math.floor(r() * (b - a + 1));
  const zone = pick(ZONES), days = pick([1, 2, 2, 3, 3, 4, 5]);
  const start = localInstant(zone, pick([2026, 2027]), pick([1, 3, 4, 6, 9, 10, 11]), int(1, 28), int(20, 40) * 15);
  const now = start + (chance(0.2) ? int(0, days * 30) * 900 : 0);
  const rules = [{ id: 'sleep', kind: 'SLEEP', startMin: pick([1320, 1380, 0]), durationMin: pick([420, 480, 540]) }];
  if (chance(0.85)) rules.push({ id: 'work', kind: 'WORK', days: pick([[0, 1, 2, 3, 4, 5, 6], [1, 2, 3, 4, 5]]), startMin: pick([420, 480, 540]),
    durationMin: pick([480, 540, 600, 660]), commuteMin: pick([0, 30, 60]), load: pick(['MIXED_MODERATE', 'PHYSICAL_HEAVY', 'COGNITIVE_HEAVY', 'LIGHT']) });
  if (chance(0.3)) rules.push({ id: 'class', kind: 'CLASS', startMin: 1140, durationMin: 90 });
  const goals = [];
  for (let i = 0, n = int(2, 5); i < n; i++) {
    const g = goal('g-' + seed + '-' + i, pick(ARCH_IDS), pick([2, 3, 4, 5]), pick(['INTERMEDIATE', 'ADVANCED', 'ADVANCED']));
    g.title = pick(['Gym', 'Spanish', 'Books', 'Money', 'Run', 'Exam', 'Guitar', 'Calm']) + ' ' + i;
    if (g.archetypeId === 'SKILL_LANGUAGE') g.subject = pick(['spanish', 'french']);
    goals.push(g);
  }
  const quests = [], qn = int(3, 8), qids = Array.from({ length: qn }, (_, i) => 'q-' + i);
  for (let i = 0; i < qn; i++) {
    const q = { id: qids[i], title: 'Quest ' + i, tier: pick(['MANDATORY', 'MANDATORY', 'CORE', 'CORE', 'OPTIONAL']),
      tag: pick(['fitness.strength', 'language.spanish.vocab', 'reading.focused', 'study.deep', 'admin.finance', 'creative.practice', 'wellbeing']),
      requiredCount: pick([1, 1, 2, 3]), deadlineEpoch: now + int(2, (days + 1) * 24) * 3600 - (chance(0.5) ? 60 : 0) };
    if (chance(0.3)) q.sessionMinutes = pick([45, 60, 90, 120]);
    if (chance(0.3)) q.minSessionMinutes = pick([15, 30]);
    if (chance(0.15)) q.recurrence = 'DAILY';
    if (chance(0.2) && i > 0) q.prerequisiteQuestIds = [pick(qids.slice(0, i))];
    quests.push(q);
  }
  const prefs = { intensityLevel: pick(['AGGRESSIVE', 'HARDCORE', 'HARDCORE', 'BALANCED']) };
  if (chance(0.4)) prefs.maxDailyGoalMinutes = pick([60, 90, 120, 180]);
  if (chance(0.3)) prefs.minDailyRecoveryMinutes = pick([30, 60, 120]);
  if (chance(0.2)) prefs.maxHighIntensityPerDay = pick([1, 2]);
  if (chance(0.2)) prefs.mealsEnabled = false;
  const input = { timezone: zone, currentTimeEpoch: now, horizonStartEpoch: start, horizonDays: days,
    playerAvailability: { timezone: zone, sleepRequirementMinutes: 480, rules, blackouts: [] },
    fixedCommitments: [], goals, activeQuests: quests, existingSchedule: [], completionHistory: [], playerPreferences: prefs };
  if (!chance(0.25)) return input;
  let plan;
  try { plan = engine.generateSchedule(input).scheduledInstances; } catch { return input; }
  const later = now + int(0, days * 6) * 900;
  input.existingSchedule = plan.map(i => stored(i, i.endEpoch <= later ? pick(['COMPLETED', 'MISSED']) : 'PLANNED', chance(0.2)));
  input.currentTimeEpoch = later;
  return input;
}

/* A seeded plan of quest chains whose prerequisites are of a lower tier than their dependents (the construction places a dependent's
   tier before its prerequisite exists, so only the fill pass can place it, LD40), with goals whose sessions the repair can move. */
function chainInput(seed) {
  const r = random(seed * 4241 + 11), pick = a => a[Math.floor(r() * a.length)], int = (a, b) => a + Math.floor(r() * (b - a + 1));
  const zone = pick(['UTC', 'Europe/London', 'Asia/Kolkata', 'America/New_York']), days = int(2, 5);
  const start = localInstant(zone, 2026, pick([3, 6, 10, 11]), int(1, 27), 360), quests = [];
  for (let i = 0, n = int(1, 4); i < n; i++) {
    const deadline = start + int(20, days * 24) * 3600 - 60;
    quests.push({ id: 'q-' + i + 'a', title: 'First ' + i, tier: pick(['OPTIONAL', 'SIDE', 'CORE']), tag: pick(['admin.finance', 'chores', 'reading.focused']),
      requiredCount: int(1, 2), sessionMinutes: pick([30, 45, 60]), deadlineEpoch: deadline });
    quests.push({ id: 'q-' + i + 'b', title: 'Then ' + i, tier: pick(['MANDATORY', 'CORE']), tag: pick(['study.deep', 'creative.practice', 'wellbeing']),
      requiredCount: int(1, 3), sessionMinutes: pick([30, 60, 90]), minSessionMinutes: pick([15, 30]), prerequisiteQuestIds: ['q-' + i + 'a'],
      deadlineEpoch: deadline + int(2, 30) * 3600 });
  }
  const goals = [];
  for (let i = 0, n = int(0, 4); i < n; i++) goals.push(goal('g-' + seed + '-' + i, pick(ARCH_IDS), pick([1, 2, 3, 4, 5]), pick(['NOVICE', 'INTERMEDIATE', 'ADVANCED']), { subject: 'spanish' }));
  return small(zone, start, { horizonDays: days, goals, activeQuests: quests, playerPreferences: { intensityLevel: pick(['BALANCED', 'AGGRESSIVE', 'HARDCORE']),
    maxDailyGoalMinutes: pick([90, 120, 240, 480]) } });
}

/* A seeded re-plan: placement's seeded input planned by the engine, then the clock moved on (sometimes off the quarter hour), sessions
   done, missed, skipped, started, pinned, moved by up to a day, and the availability changed (a new commitment over a session, the
   work hours moved, a blackout, a check-in). */
function replanInput(seed, engine) {
  const r = random(seed * 6151 + 3), pick = a => a[Math.floor(r() * a.length)], chance = p => r() < p, int = (a, b) => a + Math.floor(r() * (b - a + 1));
  const input = P.fuzzInput(200000 + seed, engine, false);
  input.existingSchedule = []; input.currentTimeEpoch = input.horizonStartEpoch;
  let plan;
  try { plan = engine.generateSchedule(input).scheduledInstances; } catch { return input; }
  const later = input.currentTimeEpoch + int(0, input.horizonDays * 30) * 900 + (chance(0.3) ? int(1, 899) : 0);
  input.existingSchedule = plan.map(i => {
    const state = i.endEpoch <= later ? pick(['COMPLETED', 'MISSED_UNEXPLAINED', 'SKIPPED_VOLUNTARY', 'PLANNED', 'PARTIAL'])
      : i.startEpoch <= later ? pick(['IN_PROGRESS', 'PLANNED']) : pick(['PLANNED', 'PLANNED', 'PLANNED', 'SKIPPED_VOLUNTARY', 'CANCELLED_BY_QUEST']);
    const s = stored(i, state, chance(0.2));
    if (chance(0.15)) { const d = pick([-3600, -900, 900, 1800, 7200, 86400]); s.startEpoch += d; s.endEpoch += d; }
    if (chance(0.1)) s.explanation = { tier: i.tier, summary: 'kept words', factors: [], locked: true };
    return s;
  });
  for (const i of input.existingSchedule) if (i.lifecycleState === 'COMPLETED' && i.spacingKey) input.completionHistory.push({ startEpoch: i.startEpoch, templateId: i.templateId, spacingKey: i.spacingKey });
  input.currentTimeEpoch = later;
  if (chance(0.5)) input.horizonStartEpoch = later;
  const victim = input.existingSchedule.length ? pick(input.existingSchedule) : null;
  if (victim && chance(0.4)) input.fixedCommitments.push({ id: 'new', title: 'Came up', startEpoch: victim.startEpoch - 900, endEpoch: victim.endEpoch + 900, kind: pick(['APPOINTMENT', 'EMERGENCY', 'WORK']) });
  const work = input.playerAvailability.rules.find(x => x.kind === 'WORK');
  if (work && chance(0.3)) work.startMin = (work.startMin + pick([-120, -60, 60, 120])) % 1440;
  if (chance(0.15)) input.playerAvailability.blackouts.push({ id: 'off', startEpoch: later + int(1, 48) * 1800, endEpoch: later + int(49, 96) * 1800, kind: pick(['SICK', 'BLACKOUT']) });
  if (chance(0.2)) input.energyProfile = { checkin: { epoch: later, level: pick(['LOW', 'NORMAL', 'HIGH']) } };
  return input;
}

/* One-behaviour plans of this part: the repair's moves and its budget, the fill pass, the words of an explanation, segments and capacity
   over odd days, goal allocation, quest coverage and the status. */
function handSchedule(engine, monday) {
  const z = 'UTC', t = monday(z), out = [];
  const g = (tag, input, rule) => out.push({ tag, input, rule: rule || GEN });
  const busy = (minutes, more) => ({ timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'work', kind: 'WORK', days: [0, 1, 2, 3, 4, 5, 6], startMin: 480, durationMin: minutes, load: 'MIXED_MODERATE' }].concat(more || []) });
  const quest = (id, tier, tag, count, hours, more) => Object.assign({ id, title: 'Q ' + id, tier, tag, requiredCount: count, deadlineEpoch: t + hours * 3600 - 60 }, more || {});
  /* the repair: compression (move A), swap and eviction (move B), budget, roll-backs */
  g('repair-compress', small(z, t, { horizonDays: 1, playerAvailability: busy(720), goals: [goal('g-a', 'STUDY_GENERAL', 3, 'ADVANCED'), goal('g-b', 'HABIT_READING', 3, 'ADVANCED')],
    activeQuests: [quest('q-1', 'MANDATORY', 'admin.finance', 1, 20, { sessionMinutes: 60, minSessionMinutes: 45 })], playerPreferences: { intensityLevel: 'HARDCORE' } }), REPAIR + ' LDO-2');
  g('repair-swap', small(z, t, { horizonDays: 2, playerAvailability: busy(660), goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 2, 'ADVANCED'), goal('g-b', 'MINDFULNESS', 2)],
    activeQuests: [quest('q-1', 'MANDATORY', 'study.deep', 2, 40, { sessionMinutes: 90 }), quest('q-2', 'CORE', 'reading.focused', 2, 44, { sessionMinutes: 60 })],
    playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 180 } }), REPAIR + ' LDO-2');
  g('repair-evict', small(z, t, { horizonDays: 1, playerAvailability: busy(780), goals: [goal('g-a', 'HABIT_READING', 1, 'ADVANCED'), goal('g-b', 'MINDFULNESS', 1, 'ADVANCED')],
    activeQuests: [quest('q-1', 'MANDATORY', 'admin.finance', 2, 20, { sessionMinutes: 60 }), quest('q-2', 'MANDATORY', 'study.deep', 1, 20, { sessionMinutes: 90 })],
    playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 150 } }), REPAIR + ' LDO-1');
  for (const n of [4, 6, 8]) g('repair-budget-' + n, small(z, t, { horizonDays: n, playerAvailability: busy(720),
    goals: ARCH_IDS.map((a, i) => goal('g-' + i, a, 1 + (i % 5), 'ADVANCED', a === 'SKILL_LANGUAGE' ? { subject: 'spanish' } : {})),
    activeQuests: Array.from({ length: 12 }, (_, i) => quest('q-' + i, i % 3 ? 'CORE' : 'MANDATORY', ['admin.finance', 'study.deep', 'reading.focused', 'creative.practice'][i % 4], 3, 24 * n, { sessionMinutes: 60, minSessionMinutes: 30 })),
    playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 240 } }), REPAIR + ' LDO-8');
  g('repair-chain', small(z, t, { horizonDays: 2, playerAvailability: busy(660), goals: [goal('g-a', 'STUDY_GENERAL', 3, 'ADVANCED')],
    activeQuests: [quest('q-1', 'MANDATORY', 'study.deep', 1, 30, { sessionMinutes: 60 }), quest('q-2', 'MANDATORY', 'admin.finance', 1, 46, { sessionMinutes: 60, prerequisiteQuestIds: ['q-1'] })],
    playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 120 } }), REPAIR);
  g('repair-locked-full', small(z, t, { horizonDays: 1, playerAvailability: busy(720), goals: [goal('g-a', 'STUDY_GENERAL', 3, 'ADVANCED')],
    activeQuests: [quest('q-1', 'MANDATORY', 'admin.finance', 1, 20, { sessionMinutes: 120 })], playerPreferences: { intensityLevel: 'HARDCORE' },
    existingSchedule: [{ instanceId: 'i_pin', taskId: 't_pin', templateId: 'x', title: 'Pinned', tag: 'chores', kind: 'GOAL', goalId: null, linkedGoalIds: [], linkedQuestRequirementIds: [],
      startEpoch: t + 14 * 3600 + 1800, endEpoch: t + 15 * 3600 + 1800, durationSlots: 4, tier: 2, IP: 1, IC: 1, spacingKey: 'x', lifecycleState: 'PLANNED', isUserLocked: true }] }), REPAIR + ' LDO-2');
  /* the four-week plan SchedulePerfTest times (its repair spends the whole budget) */
  g('repair-four-weeks', copy(FOUR_WEEKS), REPAIR + ' LDO-8');
  /* a prerequisite of a lower tier than its dependent: the dependent fits but only a fill pass would place it (LD40) */
  for (const [ta, tb, dl] of [['OPTIONAL', 'MANDATORY', 30], ['SIDE', 'CORE', 46], ['OPTIONAL', 'CORE', 70], ['CORE', 'MANDATORY', 46]])
    g('chain-lower-tier-' + ta + '-' + tb + '-' + dl, small(z, t, { horizonDays: 3, activeQuests: [
      { id: 'q-a', title: 'First', tier: ta, tag: 'admin.finance', requiredCount: 1, sessionMinutes: 30, minSessionMinutes: 30, deadlineEpoch: t + 60 * 3600 },
      { id: 'q-b', title: 'Then', tier: tb, tag: 'study.deep', requiredCount: 1, sessionMinutes: 30, minSessionMinutes: 30, prerequisiteQuestIds: ['q-a'],
        deadlineEpoch: t + dl * 3600 }] }), REPAIR + ' LDO-1');
  /* explanations */
  const one = (arch, more) => small(z, t, Object.assign({ goals: [goal('g-x', arch, 4, 'INTERMEDIATE')] }, more || {}));
  g('explain-dollar-title', small(z, t, { goals: [goal('g-x', 'HABIT_READING', 4, 'NOVICE', { title: 'Save $& and $$ and $1 and $\' and $`' })] }), 'LDO-2');
  g('explain-quote-title', small(z, t, { goals: [goal('g-x', 'STUDY_GENERAL', 4, 'NOVICE', { title: 'the goal "x" — ünïcode 🙂' })] }), 'LDO-2');
  g('explain-empty-title', small(z, t, { goals: [goal('g-x', 'STUDY_GENERAL', 4, 'NOVICE', { title: '' })] }), 'LDO-2');
  g('explain-quest-and-goal', small(z, t, { horizonDays: 3, goals: [goal('g-x', 'FITNESS_HYPERTROPHY', 4)],
    activeQuests: [{ id: 'q-d', title: 'Daily lift', tier: 'CORE', tag: 'fitness.strength', requiredCount: 1, recurrence: 'DAILY' }, quest('q-2', 'CORE', 'fitness.strength', 2, 70)] }), 'LDO-2 LDO-7');
  g('explain-two-quests', small(z, t, { horizonDays: 2, activeQuests: [quest('q-1', 'CORE', 'study.deep', 1, 40), quest('q-2', 'MANDATORY', 'study.deep', 1, 40)] }), 'LDO-2 LDO-7');
  g('explain-rest-day', one('FITNESS_HYPERTROPHY', { horizonDays: 2, playerPreferences: { preferredRestDays: [1, 2], intensityLevel: 'HARDCORE' } }), 'LDO-2');
  g('explain-spacing', one('FITNESS_HYPERTROPHY', { horizonDays: 7 }), 'LDO-2');
  g('explain-scarce', one('STUDY_GENERAL', { horizonDays: 3, playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'w', kind: 'WORK', days: [1, 2], startMin: 420, durationMin: 840 }] } }), 'LDO-2');
  g('explain-checkin', one('STUDY_GENERAL', { energyProfile: { checkin: { epoch: t, level: 'LOW' } } }), 'LDO-2');
  g('explain-deadline-text', small(z, t, { activeQuests: [{ id: 'q-t', title: 'Text deadline', tier: 'CORE', tag: 'admin.finance', requiredCount: 1, deadlineEpoch: 'soon' }] }), 'LDO-2 LDE-2');
  /* the stored sessions: kept words, pinned, started, history, displaced */
  const base = small(z, t, { horizonDays: 2, goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 4), goal('g-b', 'SKILL_LANGUAGE', 3, 'NOVICE', { subject: 'spanish' })],
    activeQuests: [{ id: 'q-d', title: 'Daily vocab', tier: 'CORE', tag: 'language.spanish.vocab', requiredCount: 1, recurrence: 'DAILY' }] });
  const plan = engine.generateSchedule(base).scheduledInstances;
  const re = (sched, more) => Object.assign(copy(base), { existingSchedule: sched }, more || {});
  g('kept-explanations', re(plan.map((i, k) => Object.assign(stored(i, k % 2 ? 'IN_PROGRESS' : 'PLANNED', k % 3 === 0), k % 4 ? {} : { explanation: { summary: 'old' } })),
    { currentTimeEpoch: plan[1].startEpoch + 600 }), 'LDO-2 LDO-6 LDO-7 LDV-5');
  g('kept-full-copies', re(plan.map(i => Object.assign(copy(i), { lifecycleState: 'PLANNED', isUserLocked: true }))), 'LDO-2 LDO-6 LDV-5');
  g('kept-displaced', re(plan.map(i => stored(i, 'PLANNED', true)), { fixedCommitments: [{ id: 'c1', title: 'Doctor', startEpoch: plan[0].startEpoch, endEpoch: plan[0].endEpoch + 3600, kind: 'APPOINTMENT' }] }), 'LDO-1 LDV-5');
  g('history-kept', re(plan.map((i, k) => stored(i, ['COMPLETED', 'MISSED_UNEXPLAINED', 'SKIPPED_VOLUNTARY', 'PARTIAL'][k % 4]))), 'LDO-8');
  g('kept-no-quest-links', re([(() => { const s = stored(plan[0], 'PLANNED', true); delete s.linkedQuestRequirementIds; return s; })()]), 'LDO-7');
  g('kept-same-start', re([Object.assign(stored(plan[0], 'PLANNED', true), { instanceId: 'i_b' }),
    Object.assign(stored(plan[0], 'PLANNED', true), { instanceId: 'i_a', endEpoch: plan[0].startEpoch, durationSlots: 0 })]), 'LDO-2 LDO-8');
  g('kept-text-duration', re([Object.assign(stored(plan[0], 'PLANNED', true), { durationSlots: '4', tier: '3' })]), 'LDO-6 LDV-4');
  /* segments and capacity over odd days and blocks */
  for (const [tag, input] of [['segments-no-sleep', small(z, t, { playerAvailability: { timezone: z, rules: [] }, goals: [goal('g-x', 'MINDFULNESS', 3)] })],
    ['segments-blackout', small(z, t, { goals: [goal('g-x', 'MINDFULNESS', 3)], playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }],
      blackouts: [{ id: 'b', startEpoch: t + 3600, endEpoch: t + 7 * 3600, kind: 'SICK' }] } })],
    ['segments-commitments', small(z, t, { goals: [goal('g-x', 'MINDFULNESS', 3)], fixedCommitments: [{ id: 'c1', title: 'Dentist', startEpoch: t + 3 * 3600, endEpoch: t + 4 * 3600, kind: 'APPOINTMENT', commuteMin: 20 },
      { id: 'c2', startEpoch: t + 30 * 3600, endEpoch: t + 31 * 3600, kind: 'EMERGENCY' }, { commitmentId: 'c3', title: 7, startEpoch: t + 32 * 3600, endEpoch: t + 33 * 3600 }] })],
    ['capacity-half-day', small(z, t + 9 * 3600 + 900, { horizonDays: 2, goals: [goal('g-x', 'STUDY_GENERAL', 3)] })],
    ['capacity-now-late', small(z, t, { currentTimeEpoch: t + 40 * 3600, goals: [goal('g-x', 'STUDY_GENERAL', 3)] })]]) g(tag, input, 'LDO-3 LDO-4 LDO-5');
  /* allocation, coverage, status */
  g('alloc-paused-and-behind', small(z, t, { horizonDays: 3, goals: [goal('g-a', 'HABIT_READING', 5, 'NOVICE', { target: { units: 600, completed: 10, unit: 'pages', unitsPerHour: 30, deadlineEpoch: t + 20 * 86400 }, createdEpoch: t - 30 * 86400 }),
    goal('g-b', 'MINDFULNESS', 1, 'NOVICE', { status: 'PAUSED' }), goal('g-c', 'ADMIN_FINANCE', 2)] }), 'LDO-6');
  g('alloc-same-template-two-goals', small(z, t, { goals: ['g-2', 'g-1'].map(id => ({ id, title: 'Same', priority: 3, level: 'NOVICE', customTemplates: [{ title: 'Same', tag: 'chores', minutes: 30, perWeek: 4 }] })) }), 'LDO-6 LDV-6');
  g('alloc-driver-second', small(z, t, { goals: [{ id: 'g-d', title: 'Drive', priority: 4, level: 'NOVICE', createdEpoch: t - 86400,
    target: { units: 100000, completed: 0, unit: 'u', unitsPerHour: 1, deadlineEpoch: t + 7 * 86400 },
    customTemplates: [{ title: 'Plain', tag: 'chores', minutes: 30, perWeek: 3 }, { title: 'Driver', tag: 'study.deep', minutes: 45, perWeek: 3, volumeDriver: true }] }] }), 'LDO-6');
  g('alloc-numeric-ids-three', small(z, t, { goals: [goal('100', 'MINDFULNESS', 3), goal('9', 'HABIT_READING', 2), goal('10', 'STUDY_GENERAL', 5), goal('g-a', 'ADMIN_FINANCE', 1)] }), 'LDO-6 LDV-6');
  g('alloc-numeric-ids', small(z, t, { goals: [goal('10', 'MINDFULNESS', 3), goal('9', 'HABIT_READING', 2), goal('g-a', 'STUDY_GENERAL', 4)] }), 'LDO-6 LDV-6');
  g('coverage-passive', small(z, t, { activeQuests: [{ id: 'q-p', title: 'Drink water', tier: 'CORE', kind: 'hydration', tag: 'wellbeing', requiredCount: 8 }, quest('q-a', 'CORE', 'admin.finance', 2, 40)] }), 'LDO-7');
  g('status-warning', small(z, t, { playerAvailability: { timezone: z, sleepRequirementMinutes: 600, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 300 }] }, goals: [goal('g-x', 'MINDFULNESS', 3)] }), 'LDO-8 LDV-1');
  g('status-shortfall', small(z, t, { playerAvailability: busy(840), playerPreferences: { minDailyRecoveryMinutes: 240 }, goals: [goal('g-x', 'MINDFULNESS', 3)] }), 'LDO-8 LDV-4');
  g('empty-plan', small(z, t, {}), 'LDO-8 LDV-6');
  g('clock-field', Object.assign(small(z, t, { goals: [goal('g-x', 'MINDFULNESS', 3)] }), { clock: { offset: 5 } }), 'LDO-9');
  return out;
}

/* validateSchedule on a real plan broken one way at a time (every violation and warning code), and on malformed arguments. */
function handValidate(engine, monday) {
  const z = 'UTC', t = monday(z), out = [];
  const v = (tag, value, rule) => out.push({ tag, v: value, rule });
  const input = small(z, t, { horizonDays: 3, goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 4), goal('g-b', 'SKILL_LANGUAGE', 3, 'NOVICE', { subject: 'spanish' })],
    activeQuests: [{ id: 'q-1', title: 'Q1', tier: 'CORE', tag: 'admin.finance', requiredCount: 1, deadlineEpoch: t + 2 * 86400 }] });
  const res = engine.generateSchedule(input), plan = res.scheduledInstances, rej = res.unscheduledDiagnostics;
  const P0 = () => copy(plan), base = (planned, more) => Object.assign({ engineInput: copy(input), planned, rejected: copy(rej) }, more || {});
  const edit = (k, f) => { const p = P0(); f(p[k], p); return p; };
  v('as-planned', base(P0()));
  v('zero-length', base(edit(0, i => { i.endEpoch = i.startEpoch; })), 'LDV-1');
  v('negative-length', base(edit(0, i => { i.endEpoch = i.startEpoch - 900; })), 'LDV-1');
  v('on-sleep', base(edit(0, i => { i.startEpoch = t + 18 * 3600; i.endEpoch = t + 19 * 3600; })), 'LDV-1');
  v('on-work', base(edit(0, i => { i.startEpoch = t + 4 * 3600; i.endEpoch = t + 5 * 3600; })), 'LDV-1');
  v('overlap', base(edit(1, (i, p) => { i.startEpoch = p[0].startEpoch; i.endEpoch = p[0].endEpoch; })), 'LDV-1');
  v('overlap-locked', base(edit(1, (i, p) => { p[0].isUserLocked = true; i.startEpoch = p[0].startEpoch; i.endEpoch = p[0].endEpoch; })), 'LDV-1 LDV-5');
  v('cross-sleep', base(edit(0, i => { i.startEpoch = t + 16 * 3600; i.endEpoch = t + 26 * 3600; })), 'LDV-1');
  v('before-horizon', base(edit(0, i => { i.startEpoch = t - 7200; i.endEpoch = t - 3600; })), 'LDV-1');
  v('after-horizon', base(edit(0, i => { i.startEpoch = t + 9 * 86400; i.endEpoch = t + 9 * 86400 + 3600; })), 'LDV-1');
  v('started-past', Object.assign(base(P0()), { engineInput: Object.assign(copy(input), { currentTimeEpoch: plan[0].startEpoch + 600 }) }), 'LDV-1 LDV-4');
  v('no-explanation', base(edit(0, i => { delete i.explanation; })), 'LDV-5');
  v('empty-summary', base(edit(1, i => { i.explanation = { summary: '' }; })), 'LDV-5');
  v('deadline', base(edit(0, i => { i.source = Object.assign({}, i.source, { deadlineEpoch: i.endEpoch - 60 }); })), 'LDV-2');
  v('deadline-locked', base(edit(0, i => { i.isUserLocked = true; i.source = Object.assign({}, i.source, { deadlineEpoch: i.endEpoch - 60 }); })), 'LDV-2');
  v('window', base(edit(0, i => { i.source = Object.assign({}, i.source, { windowEnd: i.startEpoch }); })), 'LDV-2');
  v('prereq-missing', base(edit(0, i => { i.source = Object.assign({}, i.source, { dependsOn: ['t_nothing'] }); })), 'LDV-2');
  v('prereq-late', base(edit(0, (i, p) => { i.source = Object.assign({}, i.source, { dependsOn: [p[1].taskId], minLagMin: 30 }); })), 'LDV-2');
  v('prereq-by-instance', base(edit(1, (i, p) => { i.source = Object.assign({}, i.source, { dependsOn: ['t' + p[0].instanceId.slice(1)], minLagMin: 0 }); })), 'LDV-2');
  v('spacing', base(edit(1, (i, p) => { i.spacingKey = p[0].spacingKey; i.source = Object.assign({}, i.source, { spacingMinH: 400 }); })), 'LDV-3');
  v('spacing-locked', base(edit(1, (i, p) => { i.isUserLocked = true; i.spacingKey = p[0].spacingKey; i.source = Object.assign({}, i.source, { spacingMinH: 400 }); })), 'LDV-3');
  v('spacing-kept', base(edit(1, (i, p) => { i.kept = true; i.spacingKey = p[0].spacingKey; i.source = Object.assign({}, i.source, { spacingMinH: 400 }); })), 'LDV-3');
  const fill = () => { const p = P0(), day = plan[0]; const free = []; for (let s = t + 7 * 3600; s < t + 17 * 3600; s += 3600) free.push(s);
    return p.concat(free.map((s, k) => Object.assign(copy(day), { instanceId: 'i_fill' + k, startEpoch: s, endEpoch: s + 3600, durationSlots: 4 }))); };
  v('downtime-and-cap', base(fill()), 'LDV-1 LDV-4');
  v('downtime-cap-limit', Object.assign(base(P0()), { engineInput: Object.assign(copy(input), { playerPreferences: { maxDailyGoalMinutes: 15 } }) }), 'LDV-4');
  v('shortfall', Object.assign(base(P0()), { engineInput: Object.assign(copy(input), { playerPreferences: { minDailyRecoveryMinutes: 900 } }) }), 'LDV-4');
  v('sleep-warnings', Object.assign(base(P0()), { engineInput: Object.assign(copy(input), { playerAvailability: { timezone: z, sleepRequirementMinutes: 600,
    rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 300 }, { id: 'w', kind: 'WORK', days: [1, 2, 3], startMin: 1320, durationMin: 180 }] } }) }), 'LDV-1');
  const pinned = Object.assign(copy(plan[0]), { isUserLocked: true, lifecycleState: 'PLANNED' }), withPin = Object.assign(copy(input), { existingSchedule: [pinned] });
  v('pin-kept', Object.assign(base(P0()), { engineInput: withPin }), 'LDV-5');
  v('pin-moved', Object.assign(base(edit(0, i => { i.startEpoch += 900; i.endEpoch += 900; })), { engineInput: withPin }), 'LDV-5');
  v('pin-missing', Object.assign(base(P0().slice(1)), { engineInput: withPin }), 'LDV-5');
  v('pin-excused-id', Object.assign(base(P0().slice(1)), { engineInput: withPin, displacedIds: [pinned.instanceId] }), 'LDV-5');
  v('pin-excused-rejection', Object.assign(base(P0().slice(1), { rejected: [{ instanceId: pinned.instanceId, humanExplanation: 'x' }] }), { engineInput: withPin }), 'LDV-5');
  v('pin-history-state', Object.assign(base(P0().slice(1)), { engineInput: Object.assign(copy(input), { existingSchedule: [Object.assign({}, pinned, { lifecycleState: 'COMPLETED' })] }) }), 'LDV-5');
  v('rejected-no-words', base(P0(), { rejected: [{ taskId: 't_x', tier: 3 }, { taskId: 't_y', tier: 1, humanExplanation: '' }, { taskId: 't_z', humanExplanation: 'ok' }] }), 'LDV-5 LDV-6');
  v('metrics-compressed', base(edit(0, i => { i.compressed = true; i.tier = 4; })), 'LDV-6');
  v('metrics-no-plan', base([], { rejected: [{ tier: 4, humanExplanation: 'a' }, { tier: 2, humanExplanation: 'b' }, { tier: 0, humanExplanation: 'c' }] }), 'LDV-6');
  v('metrics-text-tiers', base(edit(0, i => { i.tier = '3'; }), { rejected: [{ tier: '4', humanExplanation: 'a' }] }), 'LDV-6');
  v('planned-null', base(null), 'LDV-6');
  v('rejected-zero', base(P0(), { rejected: 0 }), 'LDV-6');
  v('planned-null-item', base([null]), 'LDV-1');
  v('planned-text', base('abc'), 'LDV-1');
  v('source-text', base(edit(0, i => { i.source = 'text'; })), 'LDV-2');
  v('source-null-deps', base(edit(0, i => { i.source = Object.assign({}, i.source, { dependsOn: null }); })), 'LDV-2');
  v('off-grid', base(edit(0, i => { i.startEpoch += 420; i.endEpoch += 120; })), 'LDV-1');
  v('text-epochs', base(edit(0, i => { i.startEpoch = String(i.startEpoch); i.endEpoch = String(i.endEpoch); })), 'LDV-1');
  v('no-input', { planned: [] }, 'LDV-1');
  v('bad-zone', Object.assign(base(P0()), { engineInput: Object.assign(copy(input), { timezone: 'Mars/Base' }) }), 'LDV-1');
  /* a waking day that ends in free time no day owns (a short seeded input, 1510, has one at slot 39) */
  const open = P.fuzzInput(1510, engine, false, true), H0 = engine.deriveAvailability(open).grid.H0;
  open.currentTimeEpoch = H0;
  for (const [a, b] of [[38, 41], [36, 39], [39, 42]]) v('cross-day-' + a, { engineInput: open, planned: [{ instanceId: 'i_x', title: 'Across', startEpoch: H0 + a * 900, endEpoch: H0 + b * 900,
    explanation: { summary: 'x' }, durationSlots: b - a }], rejected: [] }, 'LDV-1');
  for (const zone of ['America/New_York', 'Asia/Kolkata', 'Australia/Sydney']) {
    const zi = scenarioAt(engine, 'office', 1, zone, localInstant(zone, 2026, 10, 26, 360)), zo = engine.generateSchedule(zi);
    v('scenario-' + zone, { engineInput: zi, planned: zo.scheduledInstances, rejected: zo.unscheduledDiagnostics });
    v('scenario-shifted-' + zone, { engineInput: zi, planned: zo.scheduledInstances.map(i => Object.assign({}, i, { startEpoch: i.startEpoch + 1800, endEpoch: i.endEpoch + 1800 })), rejected: [] });
  }
  return out;
}

/* inputHash: its two halves, the first of which keeps, at every depth, only the members named like a top-level member. */
function handHash(engine, monday) {
  const t = monday('UTC'), base = small('UTC', t, { goals: [goal('g-a', 'MINDFULNESS', 3)] }), out = [];
  const h = (tag, input) => out.push({ tag, input });
  h('small', base);
  h('clock-dropped', Object.assign(copy(base), { clock: { now: 1 } }));
  h('nested-top-name', Object.assign(copy(base), { goals: [{ id: 'g', goals: 1, timezone: 'x', other: 2 }] }));
  h('nested-only', Object.assign(copy(base), { playerPreferences: { leadMinutes: 5, intensityLevel: 'LIGHT' } }));
  h('key-order', Object.fromEntries(Object.entries(copy(base)).reverse()));
  h('numbers', Object.assign(copy(base), { extra: [0.1, 1e21, 1e-7, -5, 123456789012, 2.5e-300, 0.000001] }));
  h('unicode', Object.assign(copy(base), { note: 'ünï 🙂 "q" \\ \n\t \u0001  ' }));
  h('empty', {});
  h('only-clock', { clock: 1 });
  h('arrays', { a: [1, [2, { a: 3, b: 4 }]], b: null, c: true, d: 'x' });
  h('numeric-keys', { 2: 'b', 10: 'c', a: { 10: 1, 2: 2, a: 3 } });
  h('unsorted-names', { zeta: { alpha: 1, zeta: 2 }, alpha: { zeta: 3, beta: 4 } });
  for (let s = 1; s <= 25; s++) h('fuzz' + s, P.fuzzInput(300000 + s, engine, false, true));
  return out;
}

module.exports = { defineCases, FAMILIES, ZONES, SCENARIO_NAMES, tightInput, replanInput, chainInput };
