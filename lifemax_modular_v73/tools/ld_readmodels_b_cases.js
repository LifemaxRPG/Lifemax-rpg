/* The Life Director read-model golden cases, part b (WP-LD8b): what tools/gen_ld_readmodels_b_golden.js records from the page and the
   native code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels) must reproduce: from a saved Life
   Director state ST, a moment, the device's zone and what LifeMax answers, the goals (nativeGoals, goalCards), the quests (nativeQuests,
   questsModel), the settings (nativeSettings), My time (nativeTime, timeModel), the forms (nativeForm: the commitment sheet, the goal
   kinds, a goal form, an activity at a held time), Something came up (nativeSheet('disrupt'), disruptParts), Move (nativeSheet('move'),
   moveTimes), the work rules and shifts the rest of LifeMax reads (workRules, shifts) and the dial's handles and stops (nativeHandles over
   dialHandles). Rules are LIFE_DIRECTOR_READMODELS_SPEC.md's (RM-n, part b from RM-21).

   States are never typed here: they are part a's (tools/ld_readmodels_cases.js defineStates: the page's own saved states, engine plans in
   the strict zones and across every 2026-2027 clock change, reschedule results). A case names one and may change it first with `set`
   ([path, value] sets, [path] removes; seeded, picking values the page itself stores).

   Every case is {id, family, rule, state, set, nowMs, deviceZone, host, load, forms, moves, shifts, handleDays}; the recorder adds out.
     host        'page' (the page's own LifeMax host, connected) or {connected, missing, milestones}: what Host.capabilities() and
                 Host.milestones() answer;
     load        the store's load report source (loadReport.source), or null for none;
     forms       [kind, id] for nativeForm (through nativeSheet, as a native screen asks);
     moves       session ids for nativeSheet('move', id);
     shifts      [from, to] epochs for shifts(from, to);
     handleDays  indexes of the days (horizonDays, or with no plan calendarDays(0)) whose handles and stops are recorded. */
'use strict';
const engineVm = require('./ld_engine_vm.js');
const { defineStates, SEEDS, STATES, DEVICE } = require('./ld_readmodels_cases.js');
const { ZONES } = require('./ld_schedule_cases.js');

const FAMILIES = ['seed', 'goals', 'quests', 'settings', 'time', 'forms', 'move', 'shifts', 'handles', 'dst', 'host', 'fuzz'];

const WORDS = 'RM-21 RM-22';
const GOALS = 'RM-23 RM-24';
const QUESTS = 'RM-25';
const SETTINGS = 'RM-26 RM-27';
const TIME = 'RM-28 RM-29 RM-30';
const FORMS = 'RM-31 RM-32 RM-33';
const DISRUPT = 'RM-34';
const MOVE = 'RM-35';
const SHIFTS = 'RM-36 RM-37';
const HANDLES = 'RM-38 RM-39 RM-40';
const ALL = [WORDS, GOALS, QUESTS, SETTINGS, TIME, FORMS, DISRUPT, MOVE, SHIFTS, HANDLES].join(' ');

/* mulberry32, the cases' own generator */
function random(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const pick = (r, list) => list[Math.floor(r() * list.length)];
const CAPS = ['state', 'save', 'rules', 'questKey', 'isQuestDone', 'toggleQuest', 'questLocked', 'navigate', 'toast', 'px', 'planDay', 'dayDone'];
const LOADS = [null, null, 'MEMORY_ONLY', 'LOCAL', 'BACKUP', 'FRESH'];

function defineCases(seeds) {
  const E = engineVm.load(), U = E.util, ARCHS = Object.keys(E.ARCHETYPES);
  const states = defineStates(seeds), cases = [], ids = new Set();
  const lived = JSON.parse(seeds.lived), LEDGER = lived.ledger;
  const add = (family, rule, state, nowMs, extra, tag) => {
    let id = family + '/' + state.replace(/^(plan|res|seed)\//, '') + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + state.replace(/^(plan|res|seed)\//, '') + '/' + tag + '#' + k;
    ids.add(id);
    const st = states.get(state), s = st.schedule;
    const base = { id, family, rule, state, set: [], nowMs, deviceZone: DEVICE, host: 'page', load: null, forms: [['rule', '']], moves: [], shifts: [], handleDays: [0] };
    if (s && Array.isArray(s.scheduledInstances)) {
      const p = s.scheduledInstances.filter(i => i.lifecycleState === 'PLANNED');
      if (p.length) base.moves = [p[Math.floor(p.length / 2)].instanceId];
    }
    Object.assign(base, extra || {});
    /* a plan made by the engine is stored with ledger null here (part a's stateOf); the page always has one, so the lived-in day's is
       put in first, unless the case is one of the few that keep none (the settings then throw, as the page would) */
    if (!st.ledger && !base.noLedger) base.set = [[['ledger'], LEDGER]].concat(base.set);
    delete base.noLedger;
    cases.push(base);
  };
  const keys = prefix => [...states.keys()].filter(k => k.startsWith(prefix));
  const sched = id => states.get(id).schedule;
  const zoneOf = id => states.get(id).availability.timezone || DEVICE;
  const span = id => { const s = sched(id); return s ? [s.horizonStartEpoch, s.horizonEndEpoch] : [D0, D0 + 7 * 86400]; };
  const ms = (r, sec) => sec * 1000 + Math.floor(r() * 1000);
  const anyMoment = (r, id) => { const [a, b] = span(id); return ms(r, Math.floor(a - 86400 + r() * (b - a + 2 * 86400))); };
  const D0 = Date.UTC(2026, 9, 28) / 1000;
  const plans = keys('plan/').concat(keys('res/'));
  const ledger = (r, set) => set.push([['ledger'], Object.assign(JSON.parse(JSON.stringify(LEDGER)), { restTokens: pick(r, [0, 1, 2, 3]) })]);

  /* the seeded changes each family makes; fuzz draws from all of them */
  const goalChanges = (r, id, set) => {
    const st = states.get(id), s = st.schedule, goals = st.goals || [];
    goals.forEach((g, k) => {
      const at = ['goals', k];
      if (r() < 0.2) set.push([at.concat('status'), pick(r, ['PAUSED', 'ACTIVE', 'PAUSED'])]);
      if (r() < 0.15) set.push([at.concat('priority'), pick(r, [1, 2, 3, 4, 5])]);
      if (r() < 0.1) set.push([at.concat('priority')]);
      if (r() < 0.15) set.push([at.concat('level'), pick(r, ['NOVICE', 'INTERMEDIATE', 'ADVANCED'])]);
      if (r() < 0.08) set.push([at.concat('level')]);
      if (r() < 0.08) set.push([at.concat('archetypeId')]);
      if (r() < 0.05) set.push([at.concat('archetypeId'), 'NOT_A_KIND']);
    });
    if (!s) return;
    (s.goalAllocation || []).forEach((a, k) => {
      const at = ['schedule', 'goalAllocation', k];
      if (r() < 0.15) set.push([at.concat('targetShare'), 0]);
      if (r() < 0.08) set.push([at.concat('actualShare'), pick(r, [1.4, -0.2, 0, 0.005, 0.995])]);
      if (r() < 0.05) set.push([at.concat('targetShare'), pick(r, [1.2, -0.1, 0.125, 0.335])]);
    });
    if (goals.length && r() < 0.35) set.push([['schedule', 'trajectoryWarnings'], goals.filter(() => r() < 0.6).map(g => ({ goalId: g.id,
      message: 'At this pace ' + g.title + ' finishes late.', projectedCompletionEpoch: s.horizonEndEpoch + Math.floor(r() * 60) * 86400 + 3600 * Math.floor(r() * 24) }))]);
    if (goals.length && r() < 0.3) set.push([['schedule', 'clarificationPrompts'], goals.filter(() => r() < 0.5).map(g => ({ goalId: g.id,
      suggestedTemplateIds: r() < 0.5 ? [] : [pick(r, ARCHS), pick(r, ARCHS)].filter((v, i, a) => a.indexOf(v) === i) }))]);
  };
  const questChanges = (r, id, set) => {
    const s = sched(id); if (!s) return;
    (s.questCoverage || []).forEach((q, k) => {
      const at = ['schedule', 'questCoverage', k];
      if (r() < 0.3) set.push([at.concat('status'), pick(r, ['PASSIVE', 'COMPLETED', 'LOCKED', 'ACTIVE', 'SCHEDULING', 'DEADLINE_PASSED'])]);
      if (r() < 0.2) set.push([at.concat('covered'), r() < 0.5]);
      if (r() < 0.2) set.push([at.concat('scheduledSessions'), pick(r, [0, 1, 2, 5])]);
      if (r() < 0.15) set.push([at.concat('recurrence'), pick(r, ['DAILY', null])]);
      if (r() < 0.15) set.push([at.concat('deadlineEpoch'), pick(r, [null, s.horizonStartEpoch + Math.floor(r() * 20) * 86400 + 3600 * Math.floor(r() * 24)])]);
      if (r() < 0.08) set.push([at.concat('questId'), String(q.questId) + '@next']);
      if (r() < 0.08) set.push([at.concat('status'), 'LOCKED'], [at.concat('covered'), false], [at.concat('questId'), 'locked-' + k]);
    });
    const cov = s.questCoverage || [];
    if (cov.length && r() < 0.5) set.push([['schedule', 'unscheduledDiagnostics'], (s.unscheduledDiagnostics || []).concat(cov.filter(() => r() < 0.4).map((q, k) => (k % 2
      ? { taskId: q.questId, title: q.title, tier: 2, reasonCode: 'NO_FEASIBLE_SLOT', humanExplanation: 'No time fits ' + q.title + '.' }
      : { taskId: 't' + k, title: q.title, tier: 3, reasonCode: 'DEADLINE_INFEASIBLE', linkedQuestIds: [q.questId], humanExplanation: q.title + ' cannot meet its deadline.' })))]);
    if (r() < 0.3) set.push([['schedule', 'passiveReminders'], ['Drink water', 'Walk 8,000 steps', 'Stretch'].filter(() => r() < 0.6).map((title, k) => ({ questId: 'p' + k, title })) ]);
  };
  const hostOf = r => {
    if (r() < 0.5) return 'page';
    const connected = r() < 0.4, missing = connected ? CAPS.filter(() => r() < 0.1).filter(c => !['state', 'save', 'rules', 'questKey', 'isQuestDone', 'toggleQuest'].includes(c))
      : CAPS.filter(() => r() < 0.4);
    if (!connected && !missing.length) missing.push(pick(r, CAPS));
    const milestones = connected ? ['First Training Block Complete', 'Aerobic Engine Builder', 'Four-Week Iron Consistency'].filter(() => r() < 0.7)
      .map((title, k) => ({ id: 'm' + k, title, desc: r() < 0.2 ? '' : 'Earn ' + title.toLowerCase() + '.', done: r() < 0.4, locked: r() < 0.25 })) : [];
    return { connected, missing, milestones };
  };
  const settingsChanges = (r, id, set, extra) => {
    const st = states.get(id);
    if (r() < 0.8 || !st.ledger) ledger(r, set);
    const prefs = Object.assign({}, st.prefs || {});
    if (r() < 0.4) prefs.intensityLevel = pick(r, ['LIGHT', 'BALANCED', 'AGGRESSIVE', 'HARDCORE', 'WILD']);
    for (const key of ['maxDailyGoalMinutes', 'minDailyRecoveryMinutes', 'maxConsecutiveHighLoadMinutes', 'missGraceMinutes'])
      if (r() < 0.25) prefs[key] = pick(r, [0, 15, 30, 45, 60, 90, 135, 240, 300, 480, 600, '90']);
    if (r() < 0.3) prefs.preferredRestDays = [0, 1, 2, 3, 4, 5, 6].filter(() => r() < 0.3);
    if (r() < 0.25) prefs.planningHorizonDays = pick(r, [7, 14, 10, 0]);
    if (r() < 0.3) prefs.consequenceMode = pick(r, ['LENIENT', 'MODERATE', 'HARDCORE_RPG', 'OTHER']);
    if (r() < 0.3) prefs.mealsEnabled = r() < 0.5;
    for (const key of ['morningRoutineMinutes', 'windDownMinutes', 'mealMinutes']) if (r() < 0.2) prefs[key] = pick(r, [0, 5, 10, 45, 60, 90, 120]);
    set.push([['prefs'], prefs]);
    if (r() < 0.2) set.push([['ui', 'lab'], true]);
    if (r() < 0.15) set.push([['lastRejected'], { at: 1790000000, violations: pick(r, [['OVERLAP'], ['SLEEP', 'CAP_DAILY'], [], [{ code: 'X', n: 1.5 }]]) }]);
    else if (r() < 0.1) set.push([['lastRejected'], null]);
    if (r() < 0.15 && st.schedule) set.push([['schedule', 'computeTimeMs'], pick(r, [0, 12.5, 1234, 0.1])]);
    extra.load = pick(r, LOADS);
  };
  const timeChanges = (r, id, set) => {
    const st = states.get(id), rules = (st.availability && st.availability.rules) || [];
    const next = rules.map(x => Object.assign({}, x));
    const sleep = next.find(x => x.kind === 'SLEEP');
    if (sleep && r() < 0.3) sleep.id = pick(r, ['sleep-wk', 'night', 'sleep-we']);
    if (sleep && r() < 0.2) { sleep.startMin = pick(r, [0, 1290, 1380, 1425, 60, 1320]); sleep.durationMin = pick(r, [360, 450, 480, 540, 1440, 15]); }
    if (r() < 0.2) next.push({ id: 'sleep-we', kind: 'SLEEP', title: 'Sleep', days: [5, 6], startMin: pick(r, [0, 1410, 60]), durationMin: pick(r, [480, 540]) });
    if (r() < 0.12) for (let k = next.length - 1; k >= 0; k--) if (next[k].kind === 'SLEEP') next.splice(k, 1);
    if (r() < 0.3) next.push({ id: 'c' + Math.floor(r() * 100), kind: pick(r, ['WORK', 'CLASS', 'APPOINTMENT', 'UNAVAILABLE', 'GYM']), title: pick(r, ['Shift', 'Lectures', 'Choir', 'Busy', '']),
      days: pick(r, [[1, 2, 3, 4, 5], [0, 6], [0, 1, 2, 3, 4, 5, 6], [6], [2, 4], [5, 6], [0, 1, 2, 3, 4], [6, 0], [5, 1, 3], [3, 2, 1, 5, 4]]), startMin: pick(r, [480, 540, 1320, 1410, 0, 750]),
      durationMin: pick(r, [60, 240, 480, 600, 720]), commuteMin: pick(r, [0, 0, 15, 45]), load: pick(r, ['MIXED_MODERATE', 'LIGHT', 'PHYSICAL_HEAVY']) });
    if (r() < 0.15) next.push({ id: 'rot' + Math.floor(r() * 10), kind: 'WORK', title: 'Rota', startMin: pick(r, [420, 1140]), durationMin: 720,
      rotation: { anchorDate: pick(r, ['2026-10-01', '2026-03-29', '2027-01-15']), cycleLength: pick(r, [8, 4, 14]), activeDays: pick(r, [[0, 1, 2, 3], [0, 1], [0, 2, 4, 6]]) } });
    if (r() < 0.15) next.push({ id: 'sleep-' + Math.floor(r() * 10), kind: 'SLEEP', title: 'Sleep', days: pick(r, [[0, 1, 2, 3, 4], [5, 6], [1, 3]]), startMin: 1380, durationMin: 480 });
    if (r() < 0.1) next.push({ id: 'pref', kind: 'PREFERRED', title: 'Evenings', days: [1, 2, 3], startMin: 1080, durationMin: 120 });
    if (st.availability) set.push([['availability', 'rules'], next]);
    if (r() < 0.15) set.push([['goals'], []]);
    if (r() < 0.15) set.push([['setupComplete'], false]);
  };
  const ruleIds = (id, set) => {
    let rules = ((states.get(id).availability || {}).rules || []);
    for (const e of set) if (e[0].join('/') === 'availability/rules' && e.length > 1) rules = e[1];
    return rules.map(x => x.id);
  };
  const formsOf = (r, id, set, now) => {
    const f = [['rule', '']];
    for (const rid of ruleIds(id, set)) if (r() < 0.6) f.push(['rule', rid]);
    if (r() < 0.3) f.push(['rule', 'gone-rule']);
    if (r() < 0.3) f.push(['goalType', '']);
    if (r() < 0.3) f.push(['goalForm', pick(r, ARCHS.concat(['CUSTOM', 'CUSTOM', 'NOPE']))]);
    if (r() < 0.4) f.push(['goalForm', 'at:' + (Math.floor(now / 1000) + Math.floor(r() * 7 * 96) * 900 + pick(r, [0, 0, 60, 7]))]);
    if (r() < 0.1) f.push(['goalForm', pick(r, ['at:0', 'at:-900', 'at:abc', 'at:1.5', 'at:', 'at:9007199254740993'])]);
    return f;
  };
  const movesOf = (r, id, set) => {
    const s = sched(id); if (!s || !Array.isArray(s.scheduledInstances)) return [];
    const list = s.scheduledInstances, out = [];
    for (let k = 0; k < 3 && list.length; k++) out.push(pick(r, list).instanceId);
    if (r() < 0.2) out.push('not-a-session');
    if ((s.historyInstances || []).length && r() < 0.3) out.push(s.historyInstances[0].instanceId);
    return out.filter((v, i, a) => a.indexOf(v) === i);
  };
  const shiftsOf = (r, id, now) => {
    const t = Math.floor(now / 1000), [a, b] = span(id);
    return [[t - 86400, t + 7 * 86400], [a, b], [t, t + Math.floor(r() * 3) * 86400 + 3600 * Math.floor(r() * 24)], [a + 86400 * Math.floor(r() * 5), a + 86400 * (5 + Math.floor(r() * 9))]].slice(0, 2 + Math.floor(r() * 3));
  };
  const shiftChanges = (r, id, set) => {
    const st = states.get(id), [a] = span(id);
    const rules = ((st.availability && st.availability.rules) || []).map(x => Object.assign({}, x));
    const work = rules.filter(x => x.kind === 'WORK');
    for (const w of work) {
      if (r() < 0.2) w.validFrom = U.ymdKey(U.localParts(a + 86400 * Math.floor(r() * 4), zoneOf(id)));
      if (r() < 0.2) w.validTo = U.ymdKey(U.localParts(a + 86400 * Math.floor(2 + r() * 5), zoneOf(id)));
      if (r() < 0.1) delete w.title;
      if (r() < 0.1) delete w.days;
    }
    if (r() < 0.3) rules.push({ id: 'night', kind: 'WORK', title: 'Night shift', days: [0, 3, 5], startMin: 1320, durationMin: 600 });
    if (st.availability) set.push([['availability', 'rules'], rules]);
    const fc = [];
    for (let k = 0, n = Math.floor(r() * 4); k < n; k++) {
      const s0 = a + Math.floor(r() * 6 * 96) * 900, len = pick(r, [1800, 3600, 4 * 3600, 9 * 3600]), c = { title: pick(r, ['Cover shift', '', 'Exam']), startEpoch: s0, endEpoch: s0 + len };
      const how = Math.floor(r() * 5);
      if (how === 0) c.kind = pick(r, ['WORK', 'CLASS', 'APPOINTMENT']); else if (how === 1) c.commitmentType = pick(r, ['CLASS', 'EMERGENCY', 'SHIFT']);
      if (r() < 0.6) c.id = 'x' + k; else if (r() < 0.5) c.commitmentId = 'cid' + k;
      if (!c.title) delete c.title;
      fc.push(c);
    }
    if (r() < 0.1) fc.push({ id: 'zero', kind: 'WORK', startEpoch: 0, endEpoch: a });
    set.push([['fixedCommitments'], fc]);
    if (r() < 0.3 && st.availability) set.push([['availability', 'blackouts'], fc.filter(() => r() < 0.5).map(c => ({ id: 'b', kind: 'SICK',
      startEpoch: c.startEpoch + pick(r, [0, 900, (c.endEpoch - c.startEpoch) / 2, (c.endEpoch - c.startEpoch) / 2 - 1]), endEpoch: c.endEpoch + pick(r, [0, 3600]) })).concat([{ kind: 'SICK' }])]);
  };

  /* seed: the page's own stored states at moments of the lived-in day and around it */
  const SEED_AT = [['0630', 6.5 * 3600], ['1200', 12 * 3600], ['1845', 18.75 * 3600], ['2359', 86399], ['d3-1200', 3 * 86400 + 12 * 3600], ['d-1-1200', -86400 + 12 * 3600]];
  for (const name of SEEDS) for (const [tag, sec] of SEED_AT) {
    const r = random(name.length * 31 + sec), now = (D0 + sec) * 1000 + 250, set = [];
    add('seed', ALL, 'seed/' + name, now, { forms: formsOf(r, 'seed/' + name, set, now), moves: movesOf(r, 'seed/' + name, set), shifts: shiftsOf(r, 'seed/' + name, now),
      load: name === 'imported' ? 'BACKUP' : null }, tag);
  }

  /* goals: allocations, repaired shares, warnings, clarifications, paused and custom goals */
  plans.filter((k, i) => i % 2 === 0).slice(0, 220).forEach((id, n) => {
    const r = random(n * 1103 + 7), set = [];
    goalChanges(r, id, set);
    add('goals', GOALS + ' ' + WORDS, id, anyMoment(r, id), { set }, 'g' + n);
  });

  /* quests: every coverage status, diagnostics that name a quest, reminders, LifeMax connected or not, milestones */
  plans.filter((k, i) => i % 2 === 1).slice(0, 200).forEach((id, n) => {
    const r = random(n * 2203 + 11), set = [];
    questChanges(r, id, set);
    add('quests', QUESTS + ' ' + WORDS, id, anyMoment(r, id), { set, host: hostOf(r) }, 'q' + n);
  });

  /* settings: every preference, out-of-range values, rest tokens, storage, the lab switch, a rejected plan, the diagnostics line */
  plans.filter((k, i) => i % 3 === 0).slice(0, 140).concat(['seed/lived', 'seed/planned', 'seed/fresh', 'seed/unfinished', 'seed/v0', 'seed/imported']).forEach((id, n) => {
    const r = random(n * 3301 + 13), set = [], extra = { set, host: hostOf(r) };
    settingsChanges(r, id, set, extra);
    add('settings', SETTINGS + ' ' + WORDS, id, anyMoment(r, id), extra, 's' + n);
  });

  /* time: sleep rules (week and weekend, none, crossing midnight), commitments of every kind and pattern, rotations, routines, set-up */
  plans.filter((k, i) => i % 3 === 1).slice(0, 140).concat(['seed/lived', 'seed/fresh', 'seed/unfinished', 'seed/v0']).forEach((id, n) => {
    const r = random(n * 4409 + 17), set = [];
    timeChanges(r, id, set);
    if (r() < 0.4) settingsChanges(r, id, set, {});
    if (r() < 0.2) set.push([['availability', 'timezone'], pick(r, ZONES)]);
    add('time', TIME + ' ' + WORDS, id, anyMoment(r, id), { set }, 't' + n);
  });

  /* forms: the commitment sheet for every rule (new, rotating, light, with unwind), the goal kinds, every goal form, an activity at a
     held time in every zone, and ids that are gone or malformed */
  plans.filter((k, i) => i % 3 === 2).slice(0, 130).concat(['seed/lived', 'seed/fresh']).forEach((id, n) => {
    const r = random(n * 5501 + 19), set = [], now = anyMoment(r, id);
    if (r() < 0.5) timeChanges(r, id, set);
    if (r() < 0.3) {
      const rules = ((states.get(id).availability || {}).rules || []).map(x => Object.assign({}, x, r() < 0.5 ? { decompressMin: pick(r, [0, 15, 45, null]) } : {}, r() < 0.3 ? { load: 'LIGHT' } : {}));
      set.push([['availability', 'rules'], rules]);
    }
    const forms = formsOf(r, id, set, now).concat([['goalType', ''], ['goalForm', ARCHS[n % ARCHS.length]], ['goalForm', 'CUSTOM']]);
    add('forms', FORMS + ' ' + WORDS, id, now, { set, forms }, 'f' + n);
  });

  /* move: every half hour of free time a session could move to, from sessions on every day, done, moved and gone */
  plans.filter((k, i) => i % 3 === 0).slice(0, 150).forEach((id, n) => {
    const r = random(n * 6607 + 23), s = sched(id), set = [], list = s.scheduledInstances || [];
    if (list.length && r() < 0.4) for (let k = 0; k < 3; k++) set.push([['schedule', 'scheduledInstances', Math.floor(r() * list.length), 'lifecycleState'], pick(r, STATES)]);
    const p = list.filter(i => i.lifecycleState === 'PLANNED');
    const now = p.length && r() < 0.5 ? ms(r, pick(r, p).startEpoch - pick(r, [0, 600, 901, 3600, 86400])) : anyMoment(r, id);
    add('move', MOVE + ' ' + WORDS, id, now, { set, moves: movesOf(r, id, set) }, 'm' + n);
  });

  /* shifts: work rules over windows (valid dates, rotations, no title, no days), one-off shifts of every kind, blackouts over them */
  plans.filter((k, i) => i % 3 === 1).slice(0, 120).concat(['seed/lived', 'seed/planned']).forEach((id, n) => {
    const r = random(n * 7703 + 29), set = [], now = anyMoment(r, id);
    shiftChanges(r, id, set);
    add('shifts', SHIFTS, id, now, { set, shifts: shiftsOf(r, id, now) }, 'w' + n);
  });

  /* handles: the dial's handles and stops on the first and later days, sleep crossing midnight, rotations, odd zones */
  plans.filter((k, i) => i % 3 === 2).slice(0, 140).forEach((id, n) => {
    const r = random(n * 8803 + 31), set = [];
    if (r() < 0.5) timeChanges(r, id, set);
    else {
      /* one more rule of a kind the dial has handles for (or not: a preferred window and an unknown kind have none) */
      const rules = ((states.get(id).availability || {}).rules || []).concat([{ id: 'x' + n, kind: pick(r, ['APPOINTMENT', 'CLASS', 'UNAVAILABLE', 'WORK', 'SLEEP', 'PREFERRED', 'GYM']),
        title: pick(r, ['Choir', '', 'Nap']), days: pick(r, [[0, 1, 2, 3, 4, 5, 6], [1, 3, 5], [6]]), startMin: pick(r, [0, 15, 600, 1380, 1435]), durationMin: pick(r, [15, 60, 90, 600, 1440]) }]);
      if (states.get(id).availability) set.push([['availability', 'rules'], rules]);
    }
    if (r() < 0.15) set.push([['availability', 'timezone'], pick(r, ZONES)]);
    add('handles', HANDLES + ' ' + WORDS, id, anyMoment(r, id), { set, handleDays: [0, 1 + Math.floor(r() * 5)], forms: [] }, 'h' + n);
  });

  /* dst: every plan made across a clock change, read on the changing day: its handles and stops (a stop every minute), shifts, move */
  for (const id of keys('plan/dst/')) {
    const s = sched(id), z = zoneOf(id), days = U.buildHorizon(s.horizonStartEpoch, Math.round((s.horizonEndEpoch - s.horizonStartEpoch) / 86400), z);
    const k = days.findIndex(x => x.slots !== 96), r = random(id.length * 37 + 3);
    if (k < 0) continue;
    const now = ms(r, days[k].startEpoch + 3600 * (1 + Math.floor(r() * 3)));
    add('dst', HANDLES + ' ' + SHIFTS + ' ' + MOVE + ' ' + WORDS, id, now, { handleDays: [0, 1], shifts: [[days[k].startEpoch, days[k].endEpoch]], moves: movesOf(r, id, []),
      forms: [['rule', ''], ['goalForm', 'at:' + (days[k].startEpoch + 2 * 3600)]] }, 'change');
  }

  /* host: LifeMax not connected, partly connected, milestones earned and locked, for the quests and the diagnostics line */
  plans.filter((k, i) => i % 7 === 3).slice(0, 60).forEach((id, n) => {
    const r = random(n * 9901 + 37), set = [];
    ledger(r, set);
    const connected = n % 3 === 0;
    const host = { connected, missing: connected ? [] : CAPS.filter((c, i) => (i + n) % 4 === 0),
      milestones: connected ? [{ id: 'a', title: 'Earned one', desc: 'Done it.', done: true, locked: false }, { id: 'b', title: 'Locked one', desc: '', done: false, locked: true },
        { id: 'c', title: 'Going', desc: 'Keep at it.', done: false, locked: false }].slice(0, 1 + n % 3) : [] };
    add('host', QUESTS + ' ' + SETTINGS, id, anyMoment(r, id), { set, host }, 'h' + n);
  });

  /* fuzz: a seeded state, moment, zone and changes from every family */
  const all = plans.concat(['seed/planned', 'seed/lived', 'seed/imported', 'seed/fresh']);
  for (let n = 0; n < 200; n++) {
    const r = random(n * 7919 + 41), id = pick(r, all), set = [], now = anyMoment(r, id), extra = { set };
    if (r() < 0.4) goalChanges(r, id, set);
    if (r() < 0.4) questChanges(r, id, set);
    if (r() < 0.4) settingsChanges(r, id, set, extra); else if (r() < 0.5) ledger(r, set);
    if (r() < 0.3) timeChanges(r, id, set);
    if (r() < 0.2) set.push([['availability', 'timezone'], pick(r, ZONES)]);
    extra.host = hostOf(r);
    extra.forms = formsOf(r, id, set, now); extra.moves = movesOf(r, id, set); extra.shifts = shiftsOf(r, id, now);
    extra.handleDays = [Math.floor(r() * 3)];
    if (r() < 0.05) extra.noLedger = true;
    add('fuzz', ALL, id, now, extra, 'n' + n);
  }
  /* only the states the cases name are kept */
  const used = new Set(cases.map(c => c.state));
  for (const k of [...states.keys()]) if (!used.has(k)) states.delete(k);
  return { states, cases };
}

module.exports = { defineCases, FAMILIES };
