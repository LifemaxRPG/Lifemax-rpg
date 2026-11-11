/* The Life Director read-model golden cases (WP-LD8a): what tools/gen_ld_readmodels_golden.js records from the page and the native code
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector/readmodels) must reproduce: from a saved Life Director state ST,
   a moment and the device's zone, the plan's days (horizonDays), the availability preview of a day (unplannedSegments), each day's blocks
   (dayBlocks), the calendar's days off the plan (calendarDays), today and tomorrow as one list (liveTimeline), what is happening now
   (nowState), the week (weekModel) and the week as the projection carries it (nativeWeek). Rules are LIFE_DIRECTOR_READMODELS_SPEC.md's
   (RM-n).

   States are never typed here. They come from the page and the engine:
     store seeds   the texts the real page writes (tools/ld_store_seeds.js, captured in Chromium by the recorder and passed in);
     plans         generateSchedule's answers recorded in tests/fixtures/ld-schedule-golden.json.gz, with their inputs' availability,
                   commitments and preferences as the page stores them beside the plan;
     reschedules   the last good step of cases in tests/fixtures/ld-reschedule-golden.json.gz (moved, missed, skipped sessions, history).
   A case names one state and may change it first with `set`: a list of [path, value] (the member at path set to value) and [path] (the
   member removed); paths are arrays of member names and list indexes. The changes are seeded (their own generator, not the engine's) and
   only pick values the page itself stores (lifecycle states, actual minutes, a timer, a zone).

   Every case is {id, family, rule, state, set, nowMs, deviceZone, host, calendar}; the recorder adds out. nowMs is the page's Date.now()
   (milliseconds, often with a fraction of a second, so the floor to seconds is exercised), deviceZone the zone the device reports (the
   recording context's, UTC), host 'page' (the page's own XP preview) or 'off' (LifeMax not connected), calendar a list of day offsets
   for calendarDays or absent. */
'use strict';
const fs = require('fs'), path = require('path'), zlib = require('zlib');
const engineVm = require('./ld_engine_vm.js');
const { ZONES } = require('./ld_schedule_cases.js');

const FIX = path.join(__dirname, '..', 'tests', 'fixtures');
const FAMILIES = ['seed', 'scenario', 'dst', 'plan', 'reschedule', 'now', 'focus', 'zone', 'life', 'calendar', 'host', 'week', 'fuzz'];
const SEEDS = ['fresh', 'planned', 'lived', 'lived-bak', 'imported', 'unfinished', 'v0'];
const STATES = ['PLANNED', 'COMPLETED_ON_TIME', 'COMPLETED_EARLY', 'COMPLETED_LATE', 'PARTIALLY_COMPLETED', 'SKIPPED_VOLUNTARY',
  'MISSED_UNEXPLAINED', 'EXCUSED_BY_TOKEN', 'EXCUSED_DISPLACED', 'CANCELLED_BY_QUEST'];
const DEVICE = 'UTC';

const DAYS = 'RM-1 RM-2 RM-3';
const BLOCKS = 'RM-5 RM-6 RM-7 RM-8 RM-9 RM-10';
const LIVE = 'RM-11 RM-12 RM-13';
const WEEK = 'RM-14 RM-15 RM-16 RM-17 RM-18 RM-19 RM-20';
const ALL = [DAYS, BLOCKS, LIVE, WEEK].join(' ');

/* mulberry32, the cases' own generator */
function random(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const pick = (r, list) => list[Math.floor(r() * list.length)];

/* The case lines of a golden fixture, each parsed only when keep(index, line) says so. */
function fixtureCases(file, keep) {
  const text = zlib.gunzipSync(fs.readFileSync(path.join(FIX, file))).toString('utf8'), out = [];
  let index = 0, at = 0;
  while (at < text.length) {
    let end = text.indexOf('\n', at); if (end < 0) end = text.length;
    const line = text.slice(at, end); at = end + 1;
    if (!line.startsWith('{"id"')) continue;
    if (keep(index++, line)) out.push(JSON.parse(line.endsWith(',') ? line.slice(0, -1) : line));
  }
  return out;
}

/* ST as the page stores it beside a plan made from input (logic.js buildInput takes these members from ST). */
const stateOf = (input, schedule) => ({
  schemaVersion: 1, playerId: 'player', setupComplete: true, availability: input.playerAvailability, fixedCommitments: input.fixedCommitments || [],
  goals: input.goals || [], prefs: input.playerPreferences || {}, schedule, ledger: null, changeLog: [], energyCheckin: null, questOverlay: {},
  ui: { tab: 'today', tours: { setup: true, today: true, week: true } } });
const usable = out => out && typeof out === 'object' && !out.threw && out.status !== 'INVARIANT_VIOLATION' && Array.isArray(out.scheduledInstances);

/* The states: { id: ST }, in a fixed order. */
function defineStates(seeds) {
  const states = new Map();
  for (const name of SEEDS) states.set('seed/' + name, JSON.parse(seeds[name]));
  const every = (fam, k) => (i, l) => l.includes('"family":"' + fam + '"') && l.includes('"fn":"generate"') && (k === 1 || i % k === 0);
  const plans = fixtureCases('ld-schedule-golden.json.gz', (i, l) => every('scenario', 1)(i, l) || every('dst', 2)(i, l)
    || every('small', 15)(i, l) || every('tight', 15)(i, l) || every('replan', 10)(i, l) || every('seeds', 7)(i, l) || every('fuzz', 25)(i, l));
  for (const c of plans) if (usable(c.out)) states.set('plan/' + c.id.replace('/generate/', '/'), stateOf(c.args[0], c.out));
  const fams = ['miss', 'move', 'disrupt', 'sequence', 'time', 'hold', 'radius', 'quest', 'residual'];
  const res = fixtureCases('ld-reschedule-golden.json.gz', (i, l) => fams.some(f => l.includes('"family":"' + f + '"')) && i % 17 === 0);
  for (const c of res) {
    let input = JSON.parse(JSON.stringify(c.args[0])), last = null;
    c.args[1].forEach((step, k) => {
      if (step.set) Object.assign(input, step.set);
      const o = c.out.steps[k];
      if (!o || o.threw) return;
      if (o.engineInput && !o.engineInput.$equal && !o.engineInput.$same) input = o.engineInput;
      if (usable(o.updatedSchedule)) last = stateOf(input, o.updatedSchedule);
    });
    if (last && JSON.stringify(last).length < 400000) states.set('res/' + c.id.replace('/reschedule/', '/'), last);
  }
  return states;
}

function defineCases(seeds) {
  const E = engineVm.load(), U = E.util;
  const states = defineStates(seeds), cases = [], ids = new Set();
  const add = (family, rule, state, nowMs, extra, tag) => {
    let id = family + '/' + state.replace(/^(plan|res|seed)\//, '') + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + state.replace(/^(plan|res|seed)\//, '') + '/' + tag + '#' + k;
    ids.add(id);
    cases.push(Object.assign({ id, family, rule, state, set: [], nowMs, deviceZone: DEVICE, host: 'page' }, extra || {}));
  };
  const ids_ = prefix => [...states.keys()].filter(k => k.startsWith(prefix));
  const sched = id => states.get(id).schedule;
  const planned = id => (sched(id).scheduledInstances || []).filter(i => i.lifecycleState === 'PLANNED');
  const zoneOf = id => states.get(id).availability.timezone;
  const ms = (r, sec) => sec * 1000 + Math.floor(r() * 1000);

  /* seed: the page's own stored states, at moments of the lived-in day (28 October 2026, UTC) and around it */
  const D0 = Date.UTC(2026, 9, 28) / 1000;
  const SEED_AT = [['0630', 6.5 * 3600], ['0805', 8 * 3600 + 300], ['1200', 12 * 3600], ['1510', 15 * 3600 + 600], ['1845', 18.75 * 3600],
    ['2359', 86399], ['d1-0700', 86400 + 7 * 3600], ['d3-1200', 3 * 86400 + 12 * 3600], ['d8-1200', 8 * 86400 + 12 * 3600], ['d-1-1200', -86400 + 12 * 3600]];
  for (const name of SEEDS) for (const [tag, sec] of SEED_AT) add('seed', ALL, 'seed/' + name, (D0 + sec) * 1000 + 250, null, tag);

  /* scenario: the ten scenarios in every strict zone, at the start of the plan, five minutes before a session, and a seeded moment */
  for (const id of ids_('plan/scenario/')) {
    const r = random(id.length * 7919 + 1), s = sched(id), p = planned(id);
    add('scenario', ALL, id, ms(r, s.horizonStartEpoch + 60), null, 'start');
    if (p.length) add('scenario', ALL, id, ms(r, pick(r, p).startEpoch - 300), null, 'before');
    add('scenario', ALL, id, ms(r, Math.floor(s.horizonStartEpoch + r() * (s.horizonEndEpoch - s.horizonStartEpoch))), null, 'any');
  }

  /* dst: plans across a clock change, at the start of the changing day, either side of the change, and its last second */
  for (const id of ids_('plan/dst/')) {
    const s = sched(id), days = U.buildHorizon(s.horizonStartEpoch, Math.round((s.horizonEndEpoch - s.horizonStartEpoch) / 86400), zoneOf(id));
    const d = days.find(x => x.slots !== 96);
    const r = random(id.length * 31 + 7);
    if (!d) { add('dst', ALL, id, ms(r, s.horizonStartEpoch), null, 'start'); continue; }
    let change = d.startEpoch;
    while (change < d.endEpoch && U.tzOffsetSec(change, zoneOf(id)) === U.tzOffsetSec(d.startEpoch, zoneOf(id))) change += 900;
    add('dst', ALL, id, d.startEpoch * 1000, null, 'day-start');
    add('dst', ALL, id, (change - 1) * 1000 + 999, null, 'before-change');
    add('dst', ALL, id, change * 1000, null, 'at-change');
    add('dst', ALL, id, (d.endEpoch - 1) * 1000 + 500, null, 'day-end');
  }

  /* plan: short, tight, re-planned and seeded plans */
  for (const id of ids_('plan/').filter(k => !/^plan\/(scenario|dst)\//.test(k))) {
    const r = random(id.length * 131 + 3), s = sched(id), p = planned(id);
    add('plan', ALL, id, ms(r, Math.floor(s.horizonStartEpoch + r() * (s.horizonEndEpoch - s.horizonStartEpoch))), null, 'any');
    if (p.length) add('plan', ALL, id, ms(r, pick(r, p).endEpoch - 120), null, 'ending');
  }

  /* reschedule: plans after moves, misses, skips and disruptions */
  for (const id of ids_('res/')) {
    const r = random(id.length * 17 + 11), s = sched(id), hist = s.historyInstances || [];
    add('reschedule', ALL, id, ms(r, Math.floor(s.horizonStartEpoch + r() * (s.horizonEndEpoch - s.horizonStartEpoch))), null, 'any');
    add('reschedule', ALL, id, ms(r, hist.length ? hist[0].startEpoch + 60 : s.horizonStartEpoch + 3600), null, 'history');
  }

  /* now: around a session's start and end, at every threshold of nowState (SOON 15 min, ENDING 10 min, LATE 10 min, "Starting now") */
  const START = [-901, -900, -899, -91, -90, -89, -61, -60, -59, -1, 0, 1, 599, 600, 601];
  const END = [-601, -600, -599, -61, -60, -1, 0, 59, 60, 61];
  const nowStates = ids_('plan/scenario/').filter((k, i) => i % 5 === 0).concat(ids_('res/').filter((k, i) => i % 6 === 0)).slice(0, 26);
  for (const id of nowStates) {
    const p = planned(id); if (!p.length) continue;
    const r = random(id.length * 53 + 5), i = p[Math.floor(r() * p.length)];
    for (const o of START) add('now', LIVE, id, (i.startEpoch + o) * 1000, null, 'start' + o);
    for (const o of END) add('now', LIVE, id, (i.endEpoch + o) * 1000, null, 'end' + o);
  }

  /* focus: the page's timer (ST.ui.focus) on the session now on, paused, on another session, on one that left the plan */
  const focusStates = ids_('plan/scenario/').filter((k, i) => i % 3 === 1).slice(0, 30);
  for (const id of focusStates) {
    const p = planned(id); if (p.length < 2) continue;
    const r = random(id.length * 71 + 9), k = Math.floor(r() * (p.length - 1)), i = p[k], other = p[k + 1];
    const at = (i.startEpoch + 700) * 1000, start = (i.startEpoch + 30) * 1000;
    const focus = f => ({ set: [[['ui', 'focus'], f]] });
    add('focus', LIVE, id, at, focus({ id: i.instanceId, start, paused: null, pausedMs: 0, plannedMs: (i.endEpoch - i.startEpoch) * 1000 }), 'running');
    add('focus', LIVE, id, at, focus({ id: i.instanceId, start, paused: start + 60000, pausedMs: 0, plannedMs: 600000 }), 'paused');
    add('focus', LIVE, id, at, focus({ id: other.instanceId, start, paused: null, pausedMs: 0, plannedMs: 600000 }), 'other');
    add('focus', LIVE, id, at, focus({ id: 'gone', start, paused: null, pausedMs: 0, plannedMs: 600000 }), 'gone');
    add('focus', LIVE, id, (i.endEpoch - 300) * 1000, focus({ id: i.instanceId, start, paused: null, pausedMs: 0, plannedMs: 600000 }), 'ending');
  }

  /* zone: the plan read in another zone than it was made in (travel), no zone stored (the device's), an empty one, one spelled in
     other letter case, and one that does not exist */
  const zoneStates = ids_('plan/scenario/').filter((k, i) => i % 4 === 2).slice(0, 25);
  zoneStates.forEach((id, n) => {
    const r = random(n * 97 + 13), s = sched(id), at = ms(r, s.horizonStartEpoch + 5 * 3600);
    const other = ZONES.filter(z => z !== zoneOf(id))[n % (ZONES.length - 1)];
    add('zone', ALL, id, at, { set: [[['availability', 'timezone'], other]] }, 'travel-' + other);
    if (n % 3 === 0) add('zone', ALL, id, at, { set: [[['availability', 'timezone']]] }, 'device');
    if (n % 3 === 1) add('zone', ALL, id, at, { set: [[['availability', 'timezone'], '']] }, 'empty');
    if (n % 5 === 2) add('zone', ALL, id, at, { set: [[['availability', 'timezone'], zoneOf(id).toLowerCase()]] }, 'lower');
    if (n % 6 === 3) add('zone', ALL, id, at, { set: [[['availability', 'timezone'], 'Mars/Olympus_Mons']] }, 'unknown');
  });

  /* life: sessions in every lifecycle state, with actual minutes, pinned, moved over downtime and over each other, in history */
  const lifeStates = ids_('plan/').filter((k, i) => i % 3 === 0).concat(ids_('res/').filter((k, i) => i % 2 === 0)).slice(0, 150);
  lifeStates.forEach((id, n) => {
    const r = random(n * 389 + 17), s = sched(id), list = s.scheduledInstances || [], set = [];
    if (!list.length) return;
    const count = 1 + Math.floor(r() * Math.min(8, list.length));
    for (let k = 0; k < count; k++) {
      const j = Math.floor(r() * list.length), st = pick(r, STATES), base = ['schedule', 'scheduledInstances', j];
      set.push([base.concat('lifecycleState'), st]);
      if (/COMPLETED/.test(st) && r() < 0.6) set.push([base.concat('actualMinutes'), pick(r, [0, 1, 7, 25, 45, 61, 90, 120])]);
      if (r() < 0.15) set.push([base.concat('isUserLocked'), true]);
      if (r() < 0.25) {
        const seg = pick(r, (s.segments || []).filter(g => g.code !== 'FREE' && g.code !== 'SCHEDULED_TASK'));
        if (seg) { const len = list[j].endEpoch - list[j].startEpoch, at = seg.startEpoch + 900 * Math.floor(r() * 4);
          set.push([base.concat('startEpoch'), at], [base.concat('endEpoch'), at + len]); }
      }
    }
    const hist = s.historyInstances || [];
    if (hist.length && r() < 0.5) set.push([['schedule', 'historyInstances', Math.floor(r() * hist.length), 'lifecycleState'], pick(r, STATES)]);
    add('life', BLOCKS + ' ' + LIVE + ' ' + WEEK, id, ms(r, Math.floor(s.horizonStartEpoch + r() * (s.horizonEndEpoch - s.horizonStartEpoch))), { set }, 'mix' + count);
  });

  /* calendar: days off the plan (before it, after it, far ahead), which show the availability preview */
  const calStates = ids_('plan/scenario/').filter((k, i) => i % 2 === 0).slice(0, 40).concat(['seed/planned', 'seed/lived']);
  calStates.forEach((id, n) => {
    const r = random(n * 211 + 19), s = sched(id), off = [-10, -3, -1, 0, 1, 6, 7, 9, 14, 21, 40];
    const calendar = [pick(r, off), pick(r, off), pick(r, off)].filter((v, i, a) => a.indexOf(v) === i);
    add('calendar', DAYS + ' RM-4 RM-5 ' + BLOCKS, id, ms(r, s.horizonStartEpoch + 9 * 3600), { calendar }, 'off' + calendar.join('_'));
  });

  /* host: LifeMax not connected (no XP previews) */
  const hostStates = ids_('plan/scenario/').filter((k, i) => i % 3 === 0).slice(0, 30).concat(['seed/lived']);
  hostStates.forEach((id, n) => { const r = random(n * 7 + 23), s = sched(id); add('host', 'RM-14 RM-16 RM-17 RM-19', id, ms(r, s.horizonStartEpoch + 3600), { host: 'off' }, 'off'); });

  /* week: what could not fit, grouped and ordered by tier; the protected downtime and free time; the coverage rounding */
  const weekStates = ids_('plan/').filter((k, i) => i % 7 === 3).slice(0, 40);
  weekStates.forEach((id, n) => {
    const r = random(n * 1009 + 29), s = sched(id), set = [];
    const diag = (title, tier, reasonCode) => ({ taskId: 't' + n + title, title, tier, reasonCode, humanExplanation: 'Could not place ' + title + ' (' + reasonCode + ').' });
    const ds = [];
    const titles = ['Read', 'Spanish', 'Gym', 'Run'], reasons = ['NO_FEASIBLE_SLOT', 'DEADLINE_INFEASIBLE', 'CAPACITY'];
    for (let k = 0, m = Math.floor(r() * 7); k < m; k++) ds.push(diag(pick(r, titles), pick(r, [0, 1, 2, 3, 4, null]), pick(r, reasons)));
    set.push([['schedule', 'unscheduledDiagnostics'], ds]);
    set.push([['schedule', 'validation', 'qualityMetrics', 'coverageRequired'], pick(r, [0, 0.005, 0.125, 0.375, 0.5, 0.625, 0.875, 0.995, 1])]);
    if (s.capacity && s.capacity.length) set.push([['schedule', 'capacity', 0, 'recoveryMin'], pick(r, [0, 15, 59, 60, 61, 135])]);
    add('week', WEEK, id, ms(r, s.horizonStartEpoch + 3600), { set }, 'diag' + ds.length);
  });

  /* fuzz: a seeded moment anywhere from two days before the plan to two days after, with seeded changes */
  const all = [...states.keys()].filter(k => !k.startsWith('seed/') || ['seed/planned', 'seed/lived', 'seed/imported'].includes(k));
  for (let n = 0; n < 300; n++) {
    const r = random(n * 7907 + 31), id = all[Math.floor(r() * all.length)], s = sched(id), set = [];
    const list = s.scheduledInstances || [];
    const at = Math.floor(s.horizonStartEpoch - 2 * 86400 + r() * (s.horizonEndEpoch - s.horizonStartEpoch + 4 * 86400));
    const extra = { set };
    if (r() < 0.2) set.push([['availability', 'timezone'], pick(r, ZONES)]);
    if (r() < 0.2 && list.length) { const i = pick(r, list); set.push([['ui', 'focus'], { id: i.instanceId, start: (i.startEpoch + 60) * 1000, paused: r() < 0.3 ? (i.startEpoch + 120) * 1000 : null, pausedMs: 0, plannedMs: 900000 }]); }
    if (r() < 0.3 && list.length) for (let k = 0; k < 3; k++) set.push([['schedule', 'scheduledInstances', Math.floor(r() * list.length), 'lifecycleState'], pick(r, STATES)]);
    if (r() < 0.1) extra.host = 'off';
    if (r() < 0.1) extra.calendar = [Math.floor(r() * 30) - 10];
    add('fuzz', ALL, id, ms(r, at), extra, 'n' + n);
  }
  return { states, cases };
}

module.exports = { defineCases, defineStates, FAMILIES, SEEDS, STATES, DEVICE };
