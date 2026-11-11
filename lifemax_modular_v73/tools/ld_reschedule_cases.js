/* The Life Director engine's rescheduling golden cases (WP-LD5b): what tools/gen_ld_reschedule_golden.js records from the page and the native
   code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce: rescheduleActivity for every trigger
   (MISSED, SKIPPED, RESIDUAL, MOVE, NEW_COMMITMENT, BLOCK_HOURS, BLACKOUT_DAY, SICK, QUEST_CHANGED, TIME_CHANGED), its hold placement
   (prepareStateWithHold), the placement by radius (placeByRadius), the refusals of a move and the throws, the change record and the
   rebuilt output with its validation. Rules are LIFE_DIRECTOR_ENGINE_SPEC.md part 6 (LDM triggers, LDB the hold, LDY the radius placement,
   LDZ the change record and the output).

   Every case is {id, family, rule, fn, args}; the recorder adds out. fn is always 'reschedule' and args is [input, steps]: the runner plans
   input with generateSchedule (that plan is not recorded: tools/gen_ld_schedule_golden.js covers it), then plays the steps in order. A step
   is {trigger, set?, store?}: set replaces top-level members of the engine input first (the clock moved on, quests or availability
   changed), store makes the engine input's existingSchedule the current plan's sessions (as the page's buildInput does), then
   rescheduleActivity({engineInput, currentOutput, trigger}) runs and its engineInput and updatedSchedule are the next step's.

   As in tools/ld_schedule_cases.js the definitions use no Intl and only the strict zones of the WP-LD1 matrix. Triggers that name a session
   or a time are chosen here by playing the steps in node (play below); they only choose the inputs, so node's last-bit differences in exp
   (LD20) cannot change a recorded answer (at worst a named session does not exist in the page's plan and the step records the throw). */
'use strict';
const { CHANGES } = require('./ld_kernel_cases.js');
const P = require('./ld_placement_cases.js');
const S = require('./ld_schedule_cases.js');

const FAMILIES = ['scenario', 'seeds', 'dst', 'miss', 'move', 'residual', 'disrupt', 'quest', 'time', 'refuse', 'hold', 'radius', 'sequence'];
const ZONES = S.ZONES;
const { SCENARIO_NAMES, ARCH_IDS, localInstant, random, copy, scenarioAt, small, goal } = P;

const TRIG = 'LDM-1 LDM-2 LDM-3 LDM-4 LDM-5 LDM-6 LDM-7';
const HOLD = 'LDB-1 LDB-2 LDB-3 LDB-4';
const RADIUS = 'LDY-1 LDY-2 LDY-3 LDY-4 LDY-5';
const OUT = 'LDZ-1 LDZ-2 LDZ-3 LDZ-4 LDZ-5';
const ALL = TRIG + ' ' + HOLD + ' ' + RADIUS + ' ' + OUT;

/* One step on the state {input, out} as the recorder plays it (gen_ld_reschedule_golden.js runCases): the next state, and what the step
   gave (the result, or the error). */
function step(engine, st, s) {
  let input = st.input;
  if (s.set) input = Object.assign({}, input, copy(s.set));
  if (s.store) input = Object.assign({}, input, { existingSchedule: copy(st.out.scheduledInstances) });
  let r;
  try { r = engine.rescheduleActivity({ engineInput: input, currentOutput: st.out, trigger: copy(s.trigger) }); } catch (e) { return { st: { input, out: st.out }, threw: true }; }
  return { st: { input: r.engineInput, out: r.updatedSchedule }, r };
}

/* Picks a trigger for the state, by kind, with r (a seeded generator). */
function picker(r) {
  const pick = a => a[Math.floor(r() * a.length)], int = (a, b) => a + Math.floor(r() * (b - a + 1));
  const future = st => st.out.scheduledInstances.filter(i => i.startEpoch > st.input.currentTimeEpoch);
  const anyInst = st => pick(future(st).length ? future(st) : st.out.scheduledInstances.length ? st.out.scheduledInstances : [{ instanceId: 'i_none' }]);
  const segs = (st, codes) => st.out.segments.filter(g => codes.includes(g.code) && g.startEpoch > st.input.currentTimeEpoch);
  const at = (st, codes, dur) => {
    const ok = segs(st, codes).filter(g => g.endEpoch - g.startEpoch >= (dur || 0));
    if (!ok.length) return st.input.currentTimeEpoch + 3600 * int(1, 30);
    const g = pick(ok), room = Math.max(0, Math.floor((g.endEpoch - g.startEpoch - (dur || 0)) / 900));
    return g.startEpoch + 900 * int(0, room);
  };
  const window = (st, hours) => { const s0 = st.input.currentTimeEpoch + 900 * int(0, 96); return { startEpoch: s0, endEpoch: s0 + Math.round(hours * 3600) }; };
  const kinds = {
    MISSED: st => ({ type: 'MISSED', instanceId: anyInst(st).instanceId }),
    SKIPPED: st => ({ type: 'SKIPPED', instanceId: anyInst(st).instanceId }),
    MOVE: st => {
      const i = anyInst(st), dur = (i.endEpoch - i.startEpoch) || 900;
      const where = pick(['free', 'free', 'free', 'busy', 'hard', 'recovery', 'past', 'outside', 'shift', 'back', 'odd']);
      let to;
      if (where === 'free') to = at(st, ['FREE'], dur);
      else if (where === 'busy') to = at(st, ['SCHEDULED_TASK'], 0);
      else if (where === 'hard') to = at(st, ['PROTECTED_SLEEP', 'HARD_COMMITMENT', 'COMMUTE_AND_TRANSITION', 'ESSENTIAL_MAINTENANCE'], 0);
      else if (where === 'recovery') to = at(st, ['RECOVERY_PROTECTED'], 0);
      else if (where === 'past') to = st.input.currentTimeEpoch - 900 * int(1, 12);
      else if (where === 'outside') to = pick([st.out.horizonEndEpoch + 3600, st.out.horizonStartEpoch - 7200 - dur, st.out.horizonEndEpoch - 900]);
      else if (where === 'shift') to = (i.startEpoch || st.input.currentTimeEpoch) + pick([86400, -86400, 2 * 86400, 3600, -3600]);
      else if (where === 'back') to = i.origStartEpoch || (i.startEpoch || 0) - 900;
      else to = at(st, ['FREE'], 0) + int(1, 899);
      return { type: 'MOVE', instanceId: i.instanceId, newStartEpoch: to };
    },
    RESIDUAL: st => {
      const i = anyInst(st), src = i.source || LOOSE;
      const rem = 15 * int(1, 4);
      return { type: 'RESIDUAL', parentInstanceId: i.instanceId, task: Object.assign({}, src, { id: 't_residual_' + int(1, 99999), title: (i.title || 'Session') + ' (finish)', dOpt: rem, dMin: rem, dMax: rem,
        tierReason: 'RESIDUAL', windowStart: st.input.currentTimeEpoch, parentInstanceId: i.instanceId }, r() < 0.3 ? { tier: pick([1, 2, 3, 4]) } : {}) };
    },
    NEW_COMMITMENT: st => {
      const i = r() < 0.6 ? anyInst(st) : null, w = i && i.startEpoch ? { startEpoch: i.startEpoch - 900 * int(0, 4), endEpoch: i.endEpoch + 900 * int(0, 8) } : window(st, pick([1, 2, 3, 9]));
      return { type: 'NEW_COMMITMENT', commitment: Object.assign({ id: 'c-' + int(1, 9999), title: pick(['Extra shift', 'Dentist', 'Meeting']), kind: pick(['WORK', 'APPOINTMENT', 'EMERGENCY']), commuteMin: pick([0, 0, 15, 30]) }, w) };
    },
    BLOCK_HOURS: st => ({ type: 'BLOCK_HOURS', commitment: Object.assign({ id: 'b-' + int(1, 9999), title: 'Unavailable' }, r() < 0.5 ? { startEpoch: st.input.currentTimeEpoch, endEpoch: st.input.currentTimeEpoch + 3600 * int(1, 8) } : window(st, int(1, 30))) }),
    BLACKOUT_DAY: st => {
      const now = st.input.currentTimeEpoch;
      return { type: 'BLACKOUT_DAY', commitment: Object.assign({ id: 'd-' + int(1, 9999), title: pick(['Day off the plan', 'Paused']) }, r() < 0.5 ? { startEpoch: now, endEpoch: now + 3600 * int(6, 48) } : window(st, 24)) };
    },
    SICK: st => ({ type: 'SICK', commitment: Object.assign({ id: 's-' + int(1, 9999), title: 'Sick' }, r() < 0.7 ? { startEpoch: st.input.currentTimeEpoch, endEpoch: st.input.currentTimeEpoch + 86400 * int(1, 3) } : window(st, 30)) }),
    QUEST_CHANGED: st => ({ type: 'QUEST_CHANGED' }),
    TIME_CHANGED: st => ({ type: 'TIME_CHANGED' }),
  };
  /* the input change that makes QUEST_CHANGED and TIME_CHANGED mean something */
  const questEdit = st => {
    const qs = copy(st.input.activeQuests || []);
    for (const q of qs) {
      const d = r();
      if (d < 0.25) q.status = pick(['COMPLETED', 'FAILED', 'ARCHIVED']);
      else if (d < 0.6 && q.deadlineEpoch) q.deadlineEpoch = st.input.currentTimeEpoch + 3600 * int(2, 60) - 60;
      else if (d < 0.7) q.deadlineEpoch = st.input.currentTimeEpoch + 3600 * int(1, 20);
    }
    if (r() < 0.2 && qs.length) qs.splice(int(0, qs.length - 1), 1);
    return { activeQuests: qs };
  };
  const timeEdit = st => {
    const av = copy(st.input.playerAvailability);
    const work = (av.rules || []).find(x => x.kind === 'WORK');
    const d = r();
    if (work && d < 0.4) work.startMin = (work.startMin + pick([-120, -60, 60, 120, 180])) % 1440;
    else if (work && d < 0.6) av.rules = av.rules.filter(x => x !== work);
    else if (d < 0.8) av.rules = (av.rules || []).concat([{ id: 'extra', kind: 'WORK', days: [0, 1, 2, 3, 4, 5, 6], startMin: pick([600, 780, 1020]), durationMin: pick([60, 120, 240]) }]);
    else av.blackouts = (av.blackouts || []).concat([Object.assign({ id: 'off', kind: pick(['SICK', 'BLACKOUT']) }, window(st, int(2, 20)))]);
    return { playerAvailability: av };
  };
  return { pick, int, kinds, questEdit, timeEdit, anyInst, at };
}

/* Steps of the given kinds, each chosen on the state the steps before it leave (played in node). Options: advance (move the clock on
   before some steps), store (the page's existingSchedule), horizon (the horizon start moves with the clock). */
function steps(engine, input, r, kinds, opt) {
  const o = opt || {}, pk = picker(r);
  let st;
  try { st = { input, out: engine.generateSchedule(input) }; } catch { return kinds.map(k => ({ trigger: { type: k } })); }
  const out = [];
  for (const kind of kinds) {
    const s = {};
    let view = st;
    if (o.advance && r() < o.advance) {
      const now = st.input.currentTimeEpoch + 900 * pk.int(1, 40) + (r() < 0.2 ? pk.int(1, 899) : 0);
      s.set = { currentTimeEpoch: now };
      if (o.horizon && r() < 0.5) s.set.horizonStartEpoch = now;
    }
    if (kind === 'QUEST_CHANGED') s.set = Object.assign(s.set || {}, pk.questEdit(st));
    if (kind === 'TIME_CHANGED') s.set = Object.assign(s.set || {}, pk.timeEdit(st));
    if (o.store && r() < o.store) s.store = true;
    if (s.set) view = { input: Object.assign({}, st.input, s.set), out: st.out };
    s.trigger = pk.kinds[kind](view);
    out.push(s);
    st = step(engine, st, s).st;
  }
  return out;
}

/* A residual task's snapshot when the plan has no session to take one from: every member a snapshot has (LDB-2). */
const LOOSE = { id: 't_loose', kind: 'GOAL', title: 'Loose ends', templateId: 'x:loose', tag: 'general', goalId: null, linkedGoalIds: [], linkedQuestRequirementIds: [],
  priority: 3, questWeight: 0, tier: 3, tierReason: 'RESIDUAL', dOpt: 30, dMin: 15, dMax: 30, IP: 2, IC: 2, spacingKey: 'x:loose', spacingMinH: 0, spacingIdealH: 24,
  maxPerBwc: 2, avoidBeforeSleepMin: 0, phases: ['ANY'], windowStart: 0, windowEnd: 0, deadlineEpoch: null, week: 0, sessionIndex: 0, dependsOn: [], minLagMin: 0,
  exclusive: false, requiresPrior: null };

const KINDS = ['MISSED', 'SKIPPED', 'MOVE', 'MOVE', 'RESIDUAL', 'NEW_COMMITMENT', 'BLOCK_HOURS', 'BLACKOUT_DAY', 'SICK', 'QUEST_CHANGED', 'TIME_CHANGED'];

function defineCases(engine) {
  const cases = [], ids = new Set();
  const add = (family, rule, input, st, tag) => {
    let id = family + '/reschedule/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/reschedule/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn: 'reschedule', args: copy([input, st]) });
  };
  const monday = zone => localInstant(zone, 2026, 10, 26, 360);
  /* scenario: the ten scenarios in every strict zone; a miss, a move and a skip (and in some, a fourth trigger of any kind) */
  for (const name of SCENARIO_NAMES) ZONES.forEach((zone, zi) => {
    const r = random(9001 + zi * 31 + name.length * 7), input = scenarioAt(engine, name, 1, zone, monday(zone));
    add('scenario', ALL, input, steps(engine, input, r, ['MISSED', 'MOVE', 'SKIPPED'].concat(zi % 3 ? [] : [KINDS[(zi + name.length) % KINDS.length]]), { advance: 0.3 }), name + '@' + zone);
  });
  /* seeds: the scenarios at seeds 2 to 4 in two zones, two triggers of any kind */
  for (const name of SCENARIO_NAMES) for (let seed = 2; seed <= 4; seed++) for (const zone of ['UTC', 'Asia/Kolkata']) {
    const r = random(seed * 1000 + name.length), input = scenarioAt(engine, name, seed, zone, monday(zone));
    add('seeds', ALL, input, steps(engine, input, r, [r() < 0.5 ? 'MISSED' : 'MOVE', KINDS[Math.floor(r() * KINDS.length)]], { advance: 0.4, store: 0.3 }), name + '#' + seed + '@' + zone);
  }
  /* dst: plans that start the day before and the day of every 2026-2027 clock change of the strict zones; moves across the change and
     across midnight, and a block of hours over it */
  for (const c of CHANGES.filter(c => ZONES.includes(c.zone))) {
    const local = new Date((c.at + c.before * 60) * 1000), y = local.getUTCFullYear(), mo = local.getUTCMonth() + 1, d = local.getUTCDate();
    for (const back of [1, 0]) {
      const start = localInstant(c.zone, y, mo, d - back, 360), input = small(c.zone, start, { horizonDays: 3, goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 4), goal('g-b', 'STUDY_GENERAL', 3)],
        activeQuests: [{ id: 'q-1', title: 'Q1', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 2, deadlineEpoch: start + 50 * 3600 }] });
      const r = random(c.at % 100000 + back), tag = c.zone + '@' + new Date(c.at * 1000).toISOString().slice(0, 10) + '-' + back;
      add('dst', 'LDM-3 LDB-1 LDZ-4 ' + OUT, input, steps(engine, input, r, ['MOVE', 'MOVE', 'BLOCK_HOURS', 'MISSED']), tag);
      const st2 = steps(engine, input, r, ['MOVE']);
      st2[0].trigger.newStartEpoch = c.at - 1800 + 900 * Math.floor(r() * 5);
      add('dst', 'LDM-3 LDB-1', input, st2, tag + '-onchange');
      add('dst', 'LDM-6 LDZ-1', input, [{ trigger: { type: 'BLOCK_HOURS', commitment: { id: 'b', title: 'Unavailable', startEpoch: c.at - 3600, endEpoch: c.at + 3600 } } }], tag + '-block');
    }
  }
  /* one family per trigger, on seeded short plans (placement's short inputs, tight ones and quest chains) */
  const base = s => (s % 3 === 0 ? S.tightInput(7000 + s, engine) : s % 3 === 1 ? P.fuzzInput(400000 + s, engine, false, true) : S.chainInput(300 + s));
  const fam = (family, rule, kinds, n, opt) => {
    for (let s = 1; s <= n; s++) { const r = random(s * 7717 + family.length), input = base(s + family.length * 1000); add(family, rule, input, steps(engine, input, r, kinds(r, s), opt), 'seed' + s); }
  };
  fam('miss', 'LDM-1 LDM-2 ' + HOLD + ' ' + RADIUS + ' ' + OUT, r => [r() < 0.5 ? 'MISSED' : 'SKIPPED'], 400, { advance: 0.5, store: 0.3 });
  fam('move', 'LDM-3 ' + HOLD + ' ' + RADIUS + ' ' + OUT, r => (r() < 0.3 ? ['MOVE', 'MOVE'] : ['MOVE']), 450, { advance: 0.3, store: 0.3 });
  fam('residual', 'LDM-4 ' + RADIUS + ' ' + OUT, () => ['RESIDUAL'], 150, { advance: 0.5 });
  fam('disrupt', 'LDM-5 LDM-6 ' + HOLD + ' ' + RADIUS + ' ' + OUT, (r, s) => [['NEW_COMMITMENT', 'BLOCK_HOURS', 'BLACKOUT_DAY', 'SICK'][s % 4]], 400, { advance: 0.3, store: 0.3 });
  fam('quest', 'LDM-7 ' + HOLD + ' ' + RADIUS + ' ' + OUT, () => ['QUEST_CHANGED'], 200, { advance: 0.3 });
  fam('time', 'LDM-7 ' + HOLD + ' ' + RADIUS + ' ' + OUT, () => ['TIME_CHANGED'], 150, { advance: 0.3, store: 0.5 });
  /* sequence: a plan, then ten changes of any kind, each on what the one before left (undo-style moves back included) */
  fam('sequence', ALL, r => Array.from({ length: 10 }, () => KINDS[Math.floor(r() * KINDS.length)]), 160, { advance: 0.4, store: 0.3, horizon: true });
  for (const c of handCases(engine, monday)) add(c.family, c.rule, c.input, c.steps, c.tag);
  return cases;
}

/* One-behaviour cases: every refusal and throw, holds on pinned, started and dependent sessions, recovery protection, each radius. */
function handCases(engine, monday) {
  const z = 'UTC', t = monday(z), out = [];
  const h = (family, tag, input, st, rule) => out.push({ family, tag, input, steps: st, rule: rule || ALL });
  const plan = input => engine.generateSchedule(input);
  const base = small(z, t, { horizonDays: 3, goals: [goal('g-a', 'FITNESS_HYPERTROPHY', 4), goal('g-b', 'SKILL_LANGUAGE', 3, 'NOVICE', { subject: 'spanish' }), goal('g-c', 'HABIT_READING', 2)],
    activeQuests: [{ id: 'q-1', title: 'Tax', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 2, sessionMinutes: 60, deadlineEpoch: t + 60 * 3600 },
      { id: 'q-d', title: 'Daily vocab', tier: 'CORE', tag: 'language.spanish.vocab', requiredCount: 1, recurrence: 'DAILY' }] });
  const o = plan(base), inst = o.scheduledInstances, seg = c => o.segments.filter(g => g.code === c && g.startEpoch > t);
  const first = inst[0], mid = inst[Math.floor(inst.length / 2)], last = inst[inst.length - 1];
  const R = 'LDM-3 LDZ-5';
  /* refusals of a move, and the throws */
  h('refuse', 'move-onto-sleep', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('PROTECTED_SLEEP')[0].startEpoch } }], R);
  h('refuse', 'move-onto-work', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('HARD_COMMITMENT')[0].startEpoch } }], R);
  h('refuse', 'move-after-horizon', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: o.horizonEndEpoch + 3600 } }], R);
  h('refuse', 'move-before-horizon', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: t - 4 * 3600 } }], R);
  h('refuse', 'move-into-past', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: t - 1800 } }], R);
  h('refuse', 'move-onto-recovery', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('RECOVERY_PROTECTED')[0].startEpoch } }], R);
  const tightDay = small(z, t, { horizonDays: 2, playerPreferences: { minDailyRecoveryMinutes: 240 }, goals: [goal('g-a', 'STUDY_GENERAL', 4, 'ADVANCED')],
    playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
      { id: 'w', kind: 'WORK', days: [0, 1, 2, 3, 4, 5, 6], startMin: 420, durationMin: 720 }] } });
  const to = plan(tightDay);
  for (const [k, g] of to.segments.filter(g => (g.code === 'FREE' || g.code === 'RECOVERY_PROTECTED') && g.startEpoch > t).slice(0, 6).entries())
    h('refuse', 'move-short-day-' + k, tightDay, [{ trigger: { type: 'MOVE', instanceId: (to.scheduledInstances[0] || first).instanceId, newStartEpoch: g.startEpoch } }], R + ' LDB-3');
  h('refuse', 'move-unknown-id', base, [{ trigger: { type: 'MOVE', instanceId: 'i_nothing', newStartEpoch: t + 3600 } }], 'LDM-3');
  h('refuse', 'miss-unknown-id', base, [{ trigger: { type: 'MISSED', instanceId: 'i_nothing' } }], 'LDM-1');
  h('refuse', 'skip-unknown-id', base, [{ trigger: { type: 'SKIPPED' } }], 'LDM-1');
  h('refuse', 'unknown-trigger', base, [{ trigger: { type: 'LATE' } }], 'LDM-1');
  h('refuse', 'no-trigger', base, [{}], 'LDM-1');
  h('refuse', 'refused-then-move', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('PROTECTED_SLEEP')[0].startEpoch } },
    { trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } }], R);
  /* a move onto a session pinned before: refused when the pinned one starts first, the pinned one excused when it starts later (LD53) */
  const room = seg('FREE').find(g => g.endEpoch - g.startEpoch >= 2 * 3600) || seg('FREE')[0];
  const [pa, pb] = inst.filter(i => i.endEpoch - i.startEpoch >= 1800 && i.endEpoch - i.startEpoch <= 3600).concat(inst);
  h('refuse', 'move-onto-pinned-later', base, [{ trigger: { type: 'MOVE', instanceId: pa.instanceId, newStartEpoch: room.startEpoch } },
    { trigger: { type: 'MOVE', instanceId: pb.instanceId, newStartEpoch: room.startEpoch + 900 } }], R + ' LDB-1');
  h('refuse', 'move-onto-pinned-earlier', base, [{ trigger: { type: 'MOVE', instanceId: pa.instanceId, newStartEpoch: room.startEpoch + 900 } },
    { trigger: { type: 'MOVE', instanceId: pb.instanceId, newStartEpoch: room.startEpoch } }], R + ' LDB-1');
  /* moves: into free time, onto a session, across midnight, back again, and the same session twice */
  h('move', 'onto-session', base, [{ trigger: { type: 'MOVE', instanceId: first.instanceId, newStartEpoch: mid.startEpoch } }]);
  h('move', 'there-and-back', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: mid.startEpoch } }]);
  h('move', 'three-times', base, [0, 1, 2].map(k => ({ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[k].startEpoch } })));
  h('move', 'off-quarter', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch + 420 } }]);
  /* as the page builds the input (its existingSchedule is the current plan): a pinned session moved again, missed, or under a commitment (LD52) */
  h('move', 'again-as-the-page-does', base, [{ store: true, trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { store: true, trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[1].startEpoch } }], 'LDM-3 LDZ-4');
  h('move', 'then-miss-as-the-page-does', base, [{ store: true, trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { store: true, trigger: { type: 'MISSED', instanceId: mid.instanceId } }], 'LDM-1 LDZ-4');
  h('move', 'then-commitment-as-the-page-does', base, [{ store: true, trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { store: true, trigger: { type: 'NEW_COMMITMENT', commitment: { id: 'c', title: 'Call', kind: 'APPOINTMENT', startEpoch: seg('FREE')[0].startEpoch, endEpoch: seg('FREE')[0].startEpoch + 1800 } } }], 'LDM-5 LDB-1 LDZ-4');
  h('move', 'then-miss-it', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } }, { trigger: { type: 'MISSED', instanceId: mid.instanceId } }]);
  /* holds: pinned and started sessions stay; a session a later one depends on; the cascade to its dependents */
  const chain = small(z, t, { horizonDays: 3, activeQuests: [{ id: 'q-a', title: 'First', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 1, sessionMinutes: 60, deadlineEpoch: t + 30 * 3600 },
    { id: 'q-b', title: 'Then', tier: 'MANDATORY', tag: 'study.deep', requiredCount: 2, sessionMinutes: 60, prerequisiteQuestIds: ['q-a'], deadlineEpoch: t + 70 * 3600 }],
    goals: [goal('g-a', 'HABIT_READING', 2)] });
  const co = plan(chain), ci = co.scheduledInstances, pre = ci.find(i => i.linkedQuestRequirementIds.some(l => l.questId === 'q-a')) || ci[0];
  const dep = ci.find(i => i.linkedQuestRequirementIds.some(l => l.questId === 'q-b')) || ci[ci.length - 1];
  const cseg = c => co.segments.filter(g => g.code === c && g.startEpoch > t);
  h('hold', 'miss-prerequisite', chain, [{ trigger: { type: 'MISSED', instanceId: pre.instanceId } }], 'LDM-1 LDB-4 ' + RADIUS);
  h('hold', 'skip-prerequisite', chain, [{ trigger: { type: 'SKIPPED', instanceId: pre.instanceId } }], 'LDM-1 LDB-4 ' + RADIUS);
  h('hold', 'move-prerequisite-late', chain, [{ trigger: { type: 'MOVE', instanceId: pre.instanceId, newStartEpoch: (cseg('FREE').slice(-1)[0] || cseg('FREE')[0]).startEpoch } }], 'LDM-3 LDB-4');
  h('hold', 'commitment-over-prerequisite', chain, [{ trigger: { type: 'NEW_COMMITMENT', commitment: { id: 'c', title: 'Doctor', kind: 'APPOINTMENT', startEpoch: pre.startEpoch, endEpoch: pre.endEpoch } } }], 'LDM-5 LDB-1 LDB-4');
  h('hold', 'miss-dependent', chain, [{ trigger: { type: 'MISSED', instanceId: dep.instanceId } }], 'LDM-1 LDB-4');
  h('hold', 'pin-then-block', base, [{ trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { trigger: { type: 'BLOCK_HOURS', commitment: { id: 'b', title: 'Unavailable', startEpoch: seg('FREE')[0].startEpoch - 900, endEpoch: seg('FREE')[0].startEpoch + 7200 } } }], 'LDM-6 LDB-1 LDB-2');
  h('hold', 'started-kept', base, [{ set: { currentTimeEpoch: first.startEpoch + 600 }, trigger: { type: 'MISSED', instanceId: last.instanceId } }], 'LDB-1 LDB-2');
  h('hold', 'ended-carried', base, [{ set: { currentTimeEpoch: first.endEpoch + 600, horizonStartEpoch: first.endEpoch + 600 }, trigger: { type: 'SKIPPED', instanceId: last.instanceId } }], 'LDB-1 LDZ-3');
  h('hold', 'shorter-horizon', base, [{ set: { horizonDays: 1 }, trigger: { type: 'MISSED', instanceId: first.instanceId } }], 'LDB-1 LDZ-3');
  h('hold', 'stored-pins', base, [{ store: true, trigger: { type: 'MOVE', instanceId: mid.instanceId, newStartEpoch: seg('FREE')[0].startEpoch } },
    { store: true, trigger: { type: 'TIME_CHANGED' } }], 'LDB-1 LDZ-5');
  /* recovery protection: a commitment that eats the day's downtime evicts its lowest-tier sessions */
  const full = small(z, t, { horizonDays: 2, playerPreferences: { minDailyRecoveryMinutes: 120, intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 480 },
    goals: ARCH_IDS.slice(0, 6).map((a, i) => goal('g-' + i, a, 1 + (i % 5), 'ADVANCED', a === 'SKILL_LANGUAGE' ? { subject: 'spanish' } : {})) });
  const fo = plan(full), fseg = fo.segments.filter(g => g.code === 'RECOVERY_PROTECTED' && g.startEpoch > t).concat(fo.scheduledInstances);
  for (const [k, hours] of [[0, 2], [1, 4], [2, 6]])
    h('hold', 'recovery-protect-' + k, full, [{ trigger: { type: 'NEW_COMMITMENT', commitment: { id: 'c' + k, title: 'Shift', kind: 'WORK', startEpoch: (fseg[k] || fseg[0]).startEpoch, endEpoch: (fseg[k] || fseg[0]).startEpoch + hours * 3600 } } }], 'LDB-3 LDM-5');
  /* radius: home day, anywhere, compression, eviction, shed (tiers 1 to 4) */
  const busy = (minutes, more) => ({ timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'work', kind: 'WORK', days: [0, 1, 2, 3, 4, 5, 6], startMin: 480, durationMin: minutes, load: 'MIXED_MODERATE' }].concat(more || []) });
  for (const minutes of [600, 660, 720, 780]) {
    const packed = small(z, t, { horizonDays: 3, playerAvailability: busy(minutes), goals: [goal('g-a', 'STUDY_GENERAL', 3, 'ADVANCED'), goal('g-b', 'HABIT_READING', 1, 'ADVANCED'), goal('g-c', 'MINDFULNESS', 1, 'ADVANCED')],
      activeQuests: [{ id: 'q-1', title: 'Essay', tier: 'MANDATORY', tag: 'study.deep', requiredCount: 3, sessionMinutes: 90, minSessionMinutes: 30, deadlineEpoch: t + 70 * 3600 },
        { id: 'q-2', title: 'Tax', tier: 'MANDATORY', tag: 'admin.finance', requiredCount: 1, sessionMinutes: 60, deadlineEpoch: t + 20 * 3600 }],
      playerPreferences: { intensityLevel: 'HARDCORE', maxDailyGoalMinutes: 240 } });
    const po = plan(packed), urgent = po.scheduledInstances.filter(i => i.tier >= 3);
    for (const [j, u] of urgent.slice(0, 3).entries()) {
      h('radius', 'packed-' + minutes + '-miss-' + j, packed, [{ trigger: { type: 'MISSED', instanceId: u.instanceId } }], RADIUS);
      h('radius', 'packed-' + minutes + '-skip-' + j, packed, [{ trigger: { type: 'SKIPPED', instanceId: u.instanceId } }], RADIUS);
      h('radius', 'packed-' + minutes + '-block-' + j, packed, [{ trigger: { type: 'BLOCK_HOURS', commitment: { id: 'b', title: 'x', startEpoch: u.startEpoch, endEpoch: u.endEpoch } } }], RADIUS + ' LDM-6');
    }
    h('radius', 'packed-' + minutes + '-sick', packed, [{ trigger: { type: 'SICK', commitment: { id: 's', title: 'Sick', startEpoch: t, endEpoch: t + 26 * 3600 } } }], RADIUS + ' LDM-6');
    h('radius', 'packed-' + minutes + '-blackout', packed, [{ trigger: { type: 'BLACKOUT_DAY', commitment: { id: 'd', title: 'Off', startEpoch: t + 86400, endEpoch: t + 2 * 86400 } } }], RADIUS + ' LDM-6');
  }
  /* quests closed, moved earlier and later, a daily quest changed */
  const qinput = copy(base);
  const qset = edit => ({ activeQuests: edit(copy(qinput.activeQuests)) });
  h('quest', 'closed', qinput, [{ set: qset(qs => { qs[0].status = 'COMPLETED'; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'removed', qinput, [{ set: qset(qs => qs.slice(1)), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'deadline-earlier', qinput, [{ set: qset(qs => { qs[0].deadlineEpoch = t + 20 * 3600; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'deadline-tomorrow', qinput, [{ set: qset(qs => { qs[0].deadlineEpoch = t + 12 * 3600; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'deadline-later', qinput, [{ set: qset(qs => { qs[0].deadlineEpoch = t + 70 * 3600; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'deadline-removed', qinput, [{ set: qset(qs => { delete qs[0].deadlineEpoch; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'daily-closed', qinput, [{ set: qset(qs => { qs[1].status = 'COMPLETED'; return qs; }), trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  h('quest', 'nothing-changed', qinput, [{ trigger: { type: 'QUEST_CHANGED' } }], 'LDM-7');
  /* time changed: work moved, freed, a blackout; nothing changed */
  h('time', 'nothing-changed', base, [{ trigger: { type: 'TIME_CHANGED' } }], 'LDM-7');
  h('time', 'work-off', base, [{ set: { playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }] } }, trigger: { type: 'TIME_CHANGED' } }], 'LDM-7');
  h('time', 'work-later', base, [{ set: { playerAvailability: { timezone: z, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 },
    { id: 'work', kind: 'WORK', days: [1, 2, 3, 4, 5], startMin: 720, durationMin: 480, commuteMin: 30, load: 'MIXED_MODERATE' }] } }, trigger: { type: 'TIME_CHANGED' } }], 'LDM-7 LDB-1');
  /* residual: the rest of a session, of each tier; a residual without a parent */
  for (const tier of [1, 2, 3, 4]) h('residual', 'tier-' + tier, base, [{ trigger: { type: 'RESIDUAL', parentInstanceId: mid.instanceId, task: Object.assign({}, mid.source, { id: 't_res' + tier, title: mid.title + ' (finish)', dOpt: 30, dMin: 30, dMax: 30, tier, tierReason: 'RESIDUAL', windowStart: t }) } }], 'LDM-4 ' + RADIUS);
  /* a residual snapshot holding a member as null that a candidate never holds (goalTitle): the task's own snapshot keeps it (LDB-2) */
  h('residual', 'null-goal-title', base, [{ trigger: { type: 'RESIDUAL', parentInstanceId: mid.instanceId, task: Object.assign({}, LOOSE, { goalTitle: null, windowStart: t }) } }], 'LDM-4 LDB-2');
  h('residual', 'no-parent', base, [{ trigger: { type: 'RESIDUAL', task: Object.assign({}, mid.source, { id: 't_res', dOpt: 45, dMin: 30, dMax: 45 }) } }], 'LDM-4');
  h('residual', 'no-window', base, [{ trigger: { type: 'RESIDUAL', parentInstanceId: first.instanceId, task: Object.assign({}, first.source, { id: 't_res', windowStart: 0, windowEnd: 0 }) } }], 'LDM-4');
  /* the rest of a session, then the session missed, at the same clock and a minute later (LD49: the two would share a new id; here the
     rest finds no room, sequence/reschedule/seed57 holds the collision) */
  const urgent = inst.find(i => i.tier >= 3) || mid;
  h('residual', 'same-clock-then-miss', base, [{ trigger: { type: 'RESIDUAL', parentInstanceId: urgent.instanceId, task: Object.assign({}, urgent.source, { id: 't_res', dOpt: 30, dMin: 30, dMax: 30, tierReason: 'RESIDUAL', windowStart: t }) } },
    { trigger: { type: 'MISSED', instanceId: urgent.instanceId } }], 'LDM-1 LDM-4 LDY-1 LDZ-3');
  h('residual', 'next-minute-then-miss', base, [{ trigger: { type: 'RESIDUAL', parentInstanceId: urgent.instanceId, task: Object.assign({}, urgent.source, { id: 't_res', dOpt: 30, dMin: 30, dMax: 30, tierReason: 'RESIDUAL', windowStart: t }) } },
    { set: { currentTimeEpoch: t + 60 }, trigger: { type: 'MISSED', instanceId: urgent.instanceId } }], 'LDM-1 LDM-4 LDY-1');
  /* commitments: the four kinds over the first session, and a sick week */
  for (const type of ['NEW_COMMITMENT', 'BLOCK_HOURS', 'BLACKOUT_DAY', 'SICK'])
    h('disrupt', 'over-first-' + type, base, [{ trigger: { type, commitment: { id: 'x', title: 'X', kind: 'APPOINTMENT', startEpoch: first.startEpoch - 900, endEpoch: first.endEpoch + 900 } } }], 'LDM-5 LDM-6 LDB-1');
  h('disrupt', 'sick-week', base, [{ trigger: { type: 'SICK', commitment: { id: 's', title: 'Sick', startEpoch: t, endEpoch: t + 7 * 86400 } } }], 'LDM-6');
  h('disrupt', 'blackout-all', base, [{ trigger: { type: 'BLACKOUT_DAY', commitment: { id: 'd', title: 'Off', startEpoch: t, endEpoch: o.horizonEndEpoch } } }], 'LDM-6');
  return out;
}

module.exports = { defineCases, FAMILIES, ZONES, step };
