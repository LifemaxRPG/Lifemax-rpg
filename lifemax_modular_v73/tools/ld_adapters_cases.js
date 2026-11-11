/* The cases of the Life Director host adapter golden file (WP-LD11; tools/gen_ld_adapters_golden.js records them from the real page).
   Each case is {id, family, rule, zone, now, state, calls}: zone is the device's time zone, now the clock, state the save fields that
   differ from the fresh save, calls the host functions run in order ({fn, args, at?, env?}; see the generator for the names).
   The quest texts come from the page's own content (android-wrapper/native/src/main/resources/quests/quest-content.json, written from
   the page by tools/gen_quest_content.js), so the class quests and the Daily Hand templates are the ones players see. */
'use strict';
const fs = require('fs'), path = require('path');

const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'android-wrapper', 'native', 'src', 'main', 'resources', 'quests', 'quest-content.json'), 'utf8'));
const TEMPLATES = CONTENT.hand.templates;
const CLASS_QUESTS = CONTENT.classQuests;
const DEFAULT_QUESTS = CONTENT.defaultQuests;

const FAMILIES = ['tags', 'week', 'player', 'xp', 'award', 'bounty', 'quests', 'plan', 'milestones', 'retired', 'since', 'describe', 'meals', 'flow',
  'verify', 'fuzz'];
const T0 = '2026-10-28T12:00:00.000Z', TODAY = '2026-10-28'; // a Wednesday, midday UTC
const ZONES = ['UTC', 'America/New_York', 'Asia/Kolkata', 'Pacific/Auckland', 'Australia/Lord_Howe', 'Asia/Tokyo', 'America/Los_Angeles', 'Europe/London'];
const ms = iso => Date.parse(iso);
const ep = iso => ms(iso) / 1000;
const dayKey = n => new Date(ms(TODAY + 'T00:00:00Z') + n * 864e5).toISOString().slice(0, 10);
const iso = (day, time = '09:00:00') => day + 'T' + time + '.000Z';
const C = (fn, ...args) => ({ fn, args });
const at = (t, c) => ({ ...c, at: t });
const withEnv = (env, c) => ({ ...c, env });

/* a ledger record already in the save */
let seq = 0;
function rec(type, xp, o = {}) {
  const primary = o.primary || 'discipline';
  const r = { id: o.id || 's' + (++seq), type, name: o.name || 'Seed ' + type, duration: o.dur ?? 30, skill: o.skill ?? 'Seed', primary,
    secondary: o.secondary ?? '', xp, date: o.date || iso(o.day || TODAY, o.time), xpSplit: o.split || [{ attr: primary, xp }] };
  for (const k of ['epic', 'cad', 'ldBonus', 'ldEventId', 'kind', 'planDate', 'src']) if (o[k] !== undefined) r[k] = o[k];
  r.sp = 1;
  return r;
}
const qrec = (cad, xp, o = {}) => rec('quest', xp, { dur: 0, cad, epic: cad === 'epic', skill: 'Quest Completion', ...o });
const quest = (id, cadence, xp, o = {}) => ({ id, name: o.name || 'Quest ' + id, desc: o.desc ?? '', cadence, xp, ...o });
const perk = id => ({ perk: id, perksOwned: [id] });
const hand = (t, date = TODAY, slot = 0) => ({ id: 'dh_' + date + '_' + slot + '_0', hand: true, slot, tpl: t.id, name: t.name, desc: t.desc, cadence: 'daily', xp: t.xp,
  kind: 'hand', attribute: t.attr, verify: t.verify, tier: t.tier });
const tpl = id => TEMPLATES.find(t => t.id === id);
const ev = (o = {}) => ({ eventId: 'e1', activityTypeTag: 'fitness.strength', actualStartEpoch: ep('2026-10-28T10:00:00Z'), actualDurationMinutes: 45, title: 'Push day',
  lifecycleState: 'COMPLETED', ...o });
const plan = (id, start, o = {}) => ({ id, classId: o.classId || 'athlete', start, from: o.from || start, checks: o.checks || {} });

/* a seeded generator for the fuzz family (its own, not the engine's) */
function rng(seed) { let a = seed >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

const TEXTS = ['Complete 3 workouts', 'Read 20 pages', 'Run twice this week', '30-minute walk', 'Study Spanish for 15 min', 'Log 5 sessions', 'Meal prep 2 meals',
  'Do 10 push-ups', 'Train three days', 'Write 500 words', '2 deep work blocks', 'Read one chapter', 'Review finances once', '45 min focus', '120 minutes cardio',
  '1000 min of reading', 'Drink water', 'Sleep 8 hours', 'Stretch 5 mins', 'Lift heavy four times', 'Go for 2 long runs', '4 x 25 min study blocks',
  'Complete 20 sessions', 'Walk 7 days', 'Practice vocab twelve times', 'Do 15 sets of 3 reps', 'Run 3 easy-pace runs', 'ten lessons', 'Take 3 short walks',
  '3 MINUTES', '90-min', '5min', '1 hr', 'two hrs of study', '20 secs plank', 'Hold a 60 second plank', 'Read a book', 'Study the course notes', 'Cardio day',
  'Language exchange', 'Call a friend', 'Journal tonight', '', '0 sessions', '15 times', '99 runs', 'Three chapters, one day', 'Read for 3 days in total',
  'Maintain a 7 day streak', '50 Sessions Logged', 'Cover to cover', 'First Training Block Complete', 'Hundred hours', 'twenty completed sessions',
  'Accumulate 10 hours', 'Finish the deload week', 'Record and review your form', '5 consecutive days', 'Workout 3 times', 'Run twice', 'LiftK', 'WorKout',
  'café 3 sessions', '3 séssions', 'Réad 2 books', 'Étude 30 min', 'Stretch-10-minutes', '7-day', '1,000 minutes', 'x3 sessions', '3rd session',
  'twice a day, three times', 'once', 'ONCE weekly', 'Every day', 'in total 4 runs', '200 minutes of reading', '999 min', '181-min ride', '240 min',
  '241 minutes'];
const KINDS = Object.keys({ learning: 1, knowledge: 1, recall: 1, synthesis: 1, research: 1, logic: 1, focus: 1, problem_solving: 1, reading: 1, writing: 1,
  articulation: 1, skill: 1, movement: 1, fitness: 1, strength: 1, reps: 1, active: 1, cardio: 1, endurance: 1, business: 1, building: 1, shipping: 1, outreach: 1,
  networking: 1, connection: 1, brainstorming: 1, finance: 1, audit: 1, review: 1, reflection: 1, planning: 1, organization: 1, environment: 1, exploration: 1, gear: 1,
  safety: 1, recovery: 1, custom: 1, milestone: 1 }).concat(['hydration', 'nutrition', 'sleep', 'rest', 'warmup', 'check', 'consistency', 'minutes', 'seconds', 'hand', 'Cardio', '']);
const ATTRS = ['physique', 'endurance', 'intelligence', 'business', 'social', 'discipline', 'adventure', 'wellbeing', 'charisma', ''];
const CADENCES = ['daily', 'weekly', 'once', 'epic', 'custom', undefined];

const CASES = [];
function add(family, id, rule, spec) {
  const full = family + '/' + id;
  if (CASES.some(c => c.id === full)) throw new Error('two cases are called ' + full);
  CASES.push({ id: full, family, rule, zone: spec.zone || 'UTC', now: spec.now || T0, state: spec.state || {}, ...(spec.tick ? { tick: spec.tick } : {}),
    ...(spec.meals ? { meals: spec.meals } : {}), calls: spec.calls });
  seq = 0;
}
const pure = (family, id, rule, ...calls) => add(family, id, rule, { calls });

function tags() {
  for (const t of ['fitness.strength', 'fitness.strength.upper', 'fitness.cardio', 'fitness', 'fitness.mobility', 'fitnessx', 'language', 'language.speaking', 'reading',
    'study', 'study.deep', 'business', 'admin.finance', 'admin.review', 'admin', 'wellbeing', 'nutrition', 'practical.outdoor', 'creative.writing', 'chores', 'social',
    'general', '', 'Fitness', 'fitness.', '.fitness'])
    pure('tags', 'typeForTag/' + (t || 'empty'), 'HA-3', C('LifeMaxHost.typeForTag', t));
  for (const t of ['strength', 'running', 'language', 'reading', 'business', 'recovery', 'practical', 'task', 'custom', 'plan', 'quest', 'yoga', '', 'Strength'])
    pure('tags', 'tagForType/' + (t || 'empty'), 'HA-3', C('LifeMaxHost.tagForType', t));
  TEXTS.forEach((t, i) => {
    pure('tags', 'parseCount/' + i, 'HA-6', C('LifeMaxHost._test.parseCount', t));
    pure('tags', 'parseMinutes/' + i, 'HA-6', C('LifeMaxHost._test.parseMinutes', t));
  });
  pure('tags', 'parseCount/null', 'HA-6', C('LifeMaxHost._test.parseCount', null));
  pure('tags', 'parseMinutes/null', 'HA-6', C('LifeMaxHost._test.parseMinutes', null));
  KINDS.forEach(k => pure('tags', 'mapQuestTag/kind-' + (k || 'empty'), 'HA-5', C('LifeMaxHost.mapQuestTag', { name: 'Thing', desc: '', kind: k })));
  TEXTS.forEach((t, i) => ATTRS.slice(i % 3, i % 3 + 1).forEach(a =>
    pure('tags', 'mapQuestTag/text-' + i, 'HA-5', C('LifeMaxHost.mapQuestTag', { name: t, desc: i % 4 ? '' : 'More detail', kind: i % 5 ? 'hand' : undefined, attribute: a || undefined }))));
  ATTRS.forEach(a => pure('tags', 'mapQuestTag/attr-' + (a || 'empty'), 'HA-5', C('LifeMaxHost.mapQuestTag', { name: 'Do the thing', attribute: a })));
  pure('tags', 'mapQuestTag/desc-only', 'HA-5', C('LifeMaxHost.mapQuestTag', { name: 'Morning', desc: 'Go for a run' }));
  pure('tags', 'mapQuestTag/name-null', 'HA-5', C('LifeMaxHost.mapQuestTag', { name: null, desc: null }));
  TEXTS.forEach((t, i) => {
    const kind = i % 3 === 0 ? 'milestone' : 'hand', cadence = i % 3 === 1 ? 'epic' : i % 3 === 2 ? 'daily' : 'once';
    pure('tags', 'milestoneKind/' + i, 'HA-7', C('LifeMaxHost.isMilestone', { name: t, desc: i % 2 ? 'with every day of practice' : '', kind, cadence }),
      C('LifeMaxHost.isProjectMilestone', { name: t, desc: '', kind, cadence }));
    pure('tags', 'untimed/' + i, 'HA-8', C('LifeMaxHost.isUntimedQuest', { name: t, desc: i % 2 ? '' : 'Ten minutes', hand: true, cadence: 'daily', verify: 'self' }));
  });
  for (const [id, q] of Object.entries({ notHand: { name: 'Drink water', hand: false, cadence: 'daily', verify: 'self' }, weekly: { name: 'Drink water', hand: true, cadence: 'weekly', verify: 'self' },
    verified: { name: 'Drink water', hand: true, cadence: 'daily', verify: 'act:running:10' }, handText: { name: 'Drink water', hand: 'true', cadence: 'daily', verify: 'self' },
    noName: { hand: true, cadence: 'daily', verify: 'self' }, hrs: { name: 'Walk', desc: 'two hrs', hand: true, cadence: 'daily', verify: 'self' } }))
    pure('tags', 'untimed/' + id, 'HA-8', C('LifeMaxHost.isUntimedQuest', q));
  pure('tags', 'untimed/null', 'HA-8', C('LifeMaxHost.isUntimedQuest', null));
  TEMPLATES.forEach(t => pure('tags', 'untimed/template-' + t.id, 'HA-8', C('LifeMaxHost.isUntimedQuest', hand(t))));
  pure('tags', 'milestone/null', 'HA-7', C('LifeMaxHost.isMilestone', null), C('LifeMaxHost.isProjectMilestone', null));
}

function week() {
  const nows = ['2026-10-28T12:00:00Z', '2026-10-26T00:00:00Z', '2026-10-25T23:59:59Z', '2026-11-01T05:30:00Z', '2026-11-01T06:30:00Z', '2026-11-02T04:59:00Z',
    '2026-03-08T07:00:00Z', '2026-03-29T00:30:00Z', '2026-04-05T15:00:00Z', '2026-09-27T14:00:00Z', '2026-12-31T23:00:00Z', '2027-01-04T00:00:00Z'];
  for (const z of ZONES.concat(['Asia/Kathmandu', 'America/Sao_Paulo', 'Etc/GMT+12']))
    nows.forEach((n, i) => { if ((i + z.length) % 2 === 0) pure('week', z + '/' + i, 'HA-9', C('LifeMaxHost._test.weekBounds', ep(n), z)); });
  pure('week', 'zone-invalid', 'HA-9', C('LifeMaxHost._test.weekBounds', ep(T0), 'Mars/Base'));
  pure('week', 'zone-lower', 'HA-9', C('LifeMaxHost._test.weekBounds', ep(T0), 'america/new_york'));
}

function player() {
  const P = (id, state) => add('player', id, 'HA-2', { state, calls: [C('LifeMaxHost.playerId'), C('LifeMaxHost.connected')] });
  P('fresh', {});
  P('named', { name: 'Ada', created: '2025-01-02T03:04:05.000Z' });
  P('no-created', { name: 'Ada', created: '' });
  P('no-name', { name: '', created: '2025-01-02T03:04:05.000Z' });
  P('unicode', { name: 'Zoë 🚀', created: '2024-12-31T23:59:59.999Z' });
  P('long-name', { name: 'x'.repeat(300), created: '2026-01-01T00:00:00.000Z' });
  P('pipe-name', { name: 'a|b', created: 'c|d' });
  P('number-created', { name: 'Ada', created: 1700000000000 });
  P('not-connected-quests', { quests: null });
  P('not-connected-activities', { activities: null });
  P('quests-object', { quests: {} });
}

function xp() {
  const X = (id, rule, state, args, zone) => add('xp', id, rule, { state, zone, calls: [C('LifeMaxHost.xpFor', ...args)] });
  X('peek', 'HA-10', { restedXP: 20 }, ['reading', 30, 1, TODAY, false]);
  X('commit', 'HA-10', { restedXP: 20 }, ['reading', 30, 1, TODAY, true]);
  X('preview-four-args', 'HA-10', { restedXP: 20 }, ['reading', 30, 1, TODAY]);
  X('no-day-device-today', 'HA-10 HA-11', { activities: [rec('reading', 24), rec('reading', 24)] }, ['reading', 30, 1, null, false]);
  X('empty-day-device-today', 'HA-10 HA-11', { activities: [rec('reading', 24), rec('reading', 24)] }, ['reading', 30, 1, '', false]);
  X('other-day', 'HA-10', { activities: [rec('reading', 24), rec('reading', 24)] }, ['reading', 30, 1, dayKey(-1), false]);
  X('fourth-of-type', 'HA-10', { activities: [rec('strength', 42), rec('strength', 42), rec('strength', 21)] }, ['strength', 45, 1, TODAY, false]);
  X('cap', 'HA-10', { activities: [rec('business', 220)] }, ['strength', 45, 1, TODAY, true]);
  X('stars', 'HA-10', { attrStars: { physique: 3, discipline: 1 } }, ['strength', 45, 1, TODAY, false]);
  X('unknown-type', 'HA-10', {}, ['yoga', 30, 1, TODAY, false]);
  X('zero-minutes', 'HA-10', {}, ['reading', 0, 1, TODAY, false]);
  X('text-minutes', 'HA-10', {}, ['reading', '45', 1, TODAY, false]);
  for (const m of [3, 5, 10, 19, 20, 29, 30, 31, 60, 90, 119, 120, 121, 240, 480])
    X('minutes-' + m, 'HA-10', {}, ['running', m, 1, TODAY, false]);
  /* D1: the preview is dated in the plan's zone (logic.js:1217), here a day the device zone has not reached */
  for (const z of ['America/New_York', 'America/Los_Angeles', 'Pacific/Auckland', 'Asia/Kolkata']) {
    const s = { activities: [rec('reading', 24, { date: '2026-10-28T01:00:00.000Z' }), rec('reading', 24, { date: '2026-10-28T02:00:00.000Z' }), rec('business', 200, { date: '2026-10-27T15:00:00.000Z' })] };
    X('d1/' + z + '/device-day', 'HA-10 HA-11', s, ['reading', 30, 1, null, false], z);
    X('d1/' + z + '/utc-day', 'HA-10 HA-11', s, ['reading', 30, 1, '2026-10-28', false], z);
    X('d1/' + z + '/yesterday', 'HA-10 HA-11', s, ['reading', 30, 1, '2026-10-27', false], z);
  }
}

function award() {
  const W = (id, rule, state, e, info, o = {}) => add('award', id, rule, { state, zone: o.zone, now: o.now,
    calls: [o.env ? withEnv(o.env, C('LifeMaxHost.awardActivity', e, info)) : C('LifeMaxHost.awardActivity', e, info)] });
  W('basic', 'HA-12', {}, ev(), { title: 'Push day A' });
  W('title-from-event', 'HA-12', {}, ev(), null);
  W('empty-info-title', 'HA-12', {}, ev(), { title: '' });
  W('info-no-title', 'HA-12', {}, ev(), {});
  W('partial', 'HA-12', {}, ev({ lifecycleState: 'PARTIALLY_COMPLETED' }), {});
  W('completed-late', 'HA-12', {}, ev({ lifecycleState: 'COMPLETED_LATE' }), {});
  W('already-in-lifemax', 'HA-12', { activities: [rec('strength', 46, { id: 'ld_e1', ldEventId: 'e1' })] }, ev(), {});
  W('already-other-id', 'HA-12', { activities: [rec('strength', 46, { id: 'x9', ldEventId: 'e1' })] }, ev(), {});
  W('small-bonus-2', 'HA-13', {}, ev({ activityTypeTag: 'reading', actualDurationMinutes: 10 }), {});
  W('big-bonus-5', 'HA-13', {}, ev({ actualDurationMinutes: 120 }), {});
  W('exec-30', 'HA-13', perk('exec'), ev({ activityTypeTag: 'reading', actualDurationMinutes: 30 }), {});
  W('bonus-almost-used', 'HA-13', { activities: [rec('reading', 30, { ldBonus: 5 }), rec('business', 30, { ldBonus: 5 }), rec('task', 25, { ldBonus: 3 })] }, ev(), {});
  W('bonus-used-up', 'HA-13', { activities: [rec('reading', 30, { ldBonus: 5 }), rec('business', 30, { ldBonus: 5 }), rec('task', 30, { ldBonus: 5 })] }, ev(), {});
  W('too-short', 'HA-12', {}, ev({ actualDurationMinutes: 3 }), {});
  W('fourth-zero', 'HA-12', { activities: [rec('strength', 42), rec('strength', 42), rec('strength', 21)] }, ev(), {});
  W('rested', 'HA-12', { restedXP: 50 }, ev(), {});
  W('save-fails', 'HA-14', { restedXP: 50 }, ev(), {}, { env: { failSave: 'storage' } });
  W('read-only', 'HA-14', { restedXP: 50 }, ev(), {}, { env: { failSave: 'readonly' } });
  W('not-connected', 'HA-2 HA-12', { quests: null }, ev(), {});
  for (const tag of ['fitness.cardio', 'language.speaking', 'study.deep', 'admin.finance', 'admin.review', 'wellbeing', 'nutrition', 'creative.writing', 'chores', 'social', 'general'])
    W('tag-' + tag, 'HA-3 HA-12', {}, ev({ activityTypeTag: tag, actualDurationMinutes: 40 }), { title: 'S ' + tag });
  W('fractional-start', 'HA-12', {}, ev({ actualStartEpoch: ep('2026-10-28T10:00:00Z') + 0.75 }), {});
  W('fractional-minutes', 'HA-12', {}, ev({ actualDurationMinutes: 37.5 }), {});
  /* D1: the award's day (and its bonus day) is the device's local date of the session's start */
  for (const z of ['America/New_York', 'America/Los_Angeles', 'Pacific/Auckland', 'Asia/Kolkata', 'Australia/Lord_Howe']) {
    const late = ev({ actualStartEpoch: ep('2026-10-28T03:30:00Z'), activityTypeTag: 'reading', actualDurationMinutes: 30 });
    const s = { activities: [rec('reading', 24, { date: '2026-10-27T20:00:00.000Z', ldBonus: 5 }), rec('reading', 24, { date: '2026-10-27T21:00:00.000Z', ldBonus: 5 }),
      rec('business', 30, { date: '2026-10-28T11:00:00.000Z', ldBonus: 5 }), rec('reading', 24, { date: '2026-10-28T11:30:00.000Z' })] };
    W('d1/' + z + '/late-start', 'HA-11 HA-12 HA-13', s, late, {}, { zone: z });
    W('d1/' + z + '/midday', 'HA-11 HA-12 HA-13', s, ev({ activityTypeTag: 'reading', actualDurationMinutes: 30 }), {}, { zone: z });
    W('d1/' + z + '/cap-other-day', 'HA-11 HA-12', { activities: [rec('business', 230, { date: '2026-10-27T23:30:00.000Z' })] }, late, {}, { zone: z });
  }
  /* across the autumn clock change in New York (01:00 to 02:00 happens twice on 2026-11-01) */
  for (const t of ['2026-11-01T04:30:00Z', '2026-11-01T05:30:00Z', '2026-11-01T06:30:00Z'])
    W('dst/' + t.slice(11, 16), 'HA-11 HA-12', {}, ev({ actualStartEpoch: ep(t) }), {}, { zone: 'America/New_York', now: '2026-11-01T12:00:00.000Z' });
}

function bounty() {
  const daily = quest('d1', 'daily', 15, { attribute: 'physique' });
  const ref = (o = {}) => ({ questId: 'd1', key: 'd1_2026-10-28', cadence: 'daily', done: false, ...o });
  const B = (id, rule, state, r, o = {}) => add('bounty', id, rule, { state, zone: o.zone, now: o.now, calls: [C('LifeMaxHost.completeQuest', r)].concat(o.more || []) });
  B('daily', 'HA-15', { quests: [daily] }, ref());
  B('compound', 'HA-15', { quests: [daily], ...perk('compound') }, ref());
  B('period-changed', 'HA-15', { quests: [daily] }, ref({ key: 'd1_2026-10-27' }));
  B('done', 'HA-15', { quests: [daily], questDone: { 'd1_2026-10-28': 's1' }, activities: [qrec('daily', 15, { id: 's1' })] }, ref());
  B('gone', 'HA-15', { quests: [daily] }, ref({ questId: 'zz' }));
  B('future', 'HA-15', { quests: [daily] }, ref({ future: true }));
  B('locked', 'HA-15', { quests: [quest('st3', 'daily', 20, { stage: 3 })] }, { questId: 'st3', key: 'st3_2026-10-28' });
  B('null', 'HA-15', { quests: [daily] }, null);
  B('no-quest-id', 'HA-15', { quests: [daily] }, { key: 'd1_2026-10-28' });
  B('not-connected', 'HA-2 HA-15', { quests: null }, ref());
  B('weekly', 'HA-15', { quests: [quest('w1', 'weekly', 40)] }, { questId: 'w1', key: 'w1_2026-10-26', cadence: 'weekly' });
  B('weekly-next', 'HA-15', { quests: [quest('w1', 'weekly', 40)] }, { questId: 'w1@next', key: 'w1_2026-11-02', cadence: 'weekly', future: true });
  B('weekly-next-not-future', 'HA-15', { quests: [quest('w1', 'weekly', 40)] }, { questId: 'w1', key: 'w1_2026-11-02', cadence: 'weekly' });
  B('once', 'HA-15', { quests: [quest('o1', 'once', 60)] }, { questId: 'o1', key: 'o1_once' });
  B('epic', 'HA-15', { quests: [quest('ep1', 'epic', 100, { kind: 'milestone' })] }, { questId: 'ep1', key: 'ep1_once' });
  B('epic-second-today', 'HA-15', { quests: [quest('ep1', 'epic', 100), quest('ep2', 'epic', 100)], questDone: { ep2_once: 'q2' }, activities: [qrec('epic', 100, { id: 'q2' })] },
    { questId: 'ep1', key: 'ep1_once' });
  B('daily-limit', 'HA-15', { quests: [daily], activities: [qrec('daily', 15), qrec('daily', 15), qrec('daily', 15)] }, ref());
  B('zero-xp', 'HA-15', { quests: [quest('d1', 'daily', 0)] }, ref());
  B('text-xp', 'HA-15', { quests: [quest('d1', 'daily', '25')] }, ref());
  B('rested', 'HA-15', { quests: [daily], restedXP: 40 }, ref());
  B('cap', 'HA-15', { quests: [daily], activities: [rec('business', 236)] }, ref());
  B('twice', 'HA-15', { quests: [daily] }, ref(), { more: [C('LifeMaxHost.completeQuest', ref())] });
  B('custom-cadence', 'HA-15', { quests: [quest('c1', 'custom', 30)] }, { questId: 'c1', key: 'c1_once' });
  B('hand-card', 'HA-15', { quests: [hand(tpl('t4'))] }, { questId: 'dh_2026-10-28_0_0', key: 'dh_2026-10-28_0_0_2026-10-28' });
  B('save-fails', 'HA-15', { quests: [daily] }, ref(), { more: [] });
  CASES[CASES.length - 1].calls = [withEnv({ failSave: 'storage' }, C('LifeMaxHost.completeQuest', ref()))];
  /* the period key is the device's day (questKey), whatever zone the plan is in */
  for (const z of ['America/New_York', 'Pacific/Auckland', 'Asia/Kolkata', 'America/Los_Angeles']) {
    B('zone/' + z + '/utc-key', 'HA-11 HA-15', { quests: [daily] }, ref(), { zone: z, now: '2026-10-28T02:00:00.000Z' });
    B('zone/' + z + '/device-key', 'HA-11 HA-15', { quests: [daily] }, ref({ key: 'd1_2026-10-27' }), { zone: z, now: '2026-10-28T02:00:00.000Z' });
    B('zone/' + z + '/weekly-monday', 'HA-11 HA-15', { quests: [quest('w1', 'weekly', 40)] }, { questId: 'w1', key: 'w1_2026-10-26' }, { zone: z, now: '2026-10-26T03:00:00.000Z' });
  }
}

function questStates() {
  const athlete = CLASS_QUESTS.athlete, scholar = CLASS_QUESTS.scholar, ent = CLASS_QUESTS.entrepreneur, exp = CLASS_QUESTS.explorer;
  return {
    athlete, scholar, entrepreneur: ent, explorer: exp, defaults: DEFAULT_QUESTS,
    hand: [hand(tpl('t1'), TODAY, 0), hand(tpl('t4'), TODAY, 1), hand(tpl('t6'), TODAY, 2)],
    mixed: [quest('d1', 'daily', 15, { name: 'Morning run', kind: 'cardio' }), quest('d2', 'daily', 10, { name: 'Drink water', kind: 'hydration' }),
      quest('d3', 'daily', 10, { name: 'Read', day: 'mon' }), quest('d4', 'daily', 10, { name: 'Gym', day: 'wed' }), quest('d5', 'daily', 10, { name: 'Gym', day: 'funday' }),
      quest('d6', 'daily', 10, { name: 'Gym', day: 'any' }), quest('w1', 'weekly', 40, { name: 'Complete 3 workouts', kind: 'strength' }),
      quest('w2', 'weekly', 30, { name: 'Read 20 pages twice', desc: '2 sessions of 25 min', kind: 'reading' }),
      quest('w3', 'weekly', 30, { name: 'Study Spanish', desc: 'four lessons, 30 minutes each' }), quest('o1', 'once', 60, { name: 'Set up a budget', kind: 'finance' }),
      quest('m1', 'once', 100, { name: '50 Sessions Logged', kind: 'milestone' }), quest('m2', 'epic', 100, { name: 'Record and review your form', kind: 'milestone' }),
      quest('m3', 'epic', 100, { name: 'Climb a mountain', desc: '' }), quest('s3', 'weekly', 50, { name: 'Advanced: 5 runs', stage: 3 }),
      quest('c1', 'custom', 25, { name: 'Clean the garage', kind: 'custom' }), quest('n1', 'daily', 10, { name: 'Eat greens', kind: 'nutrition' })],
  };
}

function quests() {
  const S = questStates(), now = ep(T0);
  const R = (id, state, args, o = {}) => add('quests', id, o.rule || 'HA-16', { state, zone: o.zone, now: o.now, calls: [C('LifeMaxHost.readQuests', ...args)] });
  for (const [name, qs] of Object.entries(S)) for (const z of ['UTC', 'America/New_York', 'Pacific/Auckland'])
    R(name + '/' + z, { quests: qs }, [now, z, {}, {}], { zone: z === 'UTC' ? 'UTC' : 'Asia/Kolkata' });
  const mixed = S.mixed;
  const done = { 'd1_2026-10-28': 'a1', 'w1_2026-10-26': 'a2', o1_once: 'a3', 'd3_2026-10-27': 'a4' };
  R('done', { quests: mixed, questDone: done }, [now, 'UTC', {}, {}]);
  const overlay = { 'd1_2026-10-28': { eventIds: ['e1'] }, 'w1_2026-10-26': { eventIds: ['e1', 'e2', 'e3', 'e4'] }, 'w2_2026-10-26': { eventIds: ['e5', 'e6'] },
    'w3_2026-10-26': { eventIds: ['e7'] }, o1_once: { eventIds: ['e8', 'e9'] }, 'w1_2026-11-02': { eventIds: ['e10'] } };
  R('overlay', { quests: mixed }, [now, 'UTC', overlay, {}]);
  R('overlay-revoked', { quests: mixed }, [now, 'UTC', overlay, { e2: { reason: 'x' }, e5: { reason: 'y' }, e8: {} }]);
  R('overlay-revoked-null', { quests: mixed }, [now, 'UTC', overlay, null]);
  R('overlay-empty-ids', { quests: mixed }, [now, 'UTC', { 'd1_2026-10-28': {}, 'w1_2026-10-26': { eventIds: [] } }, {}]);
  R('overlay-done-and-count', { quests: mixed, questDone: done }, [now, 'UTC', overlay, {}]);
  R('locked-level', { quests: mixed, attrXP: {}, activities: [rec('business', 5000, { day: dayKey(-30) }), rec('business', 5000, { day: dayKey(-31) })] }, [now, 'UTC', {}, {}]);
  R('falsy-entries', { quests: [null, 0, { name: 'no id' }, { id: '', name: 'empty id' }].concat(mixed.slice(0, 2)) }, [now, 'UTC', {}, {}]);
  R('number-id', { quests: [{ id: 7, name: 'Seven', cadence: 'daily', xp: 5 }] }, [now, 'UTC', {}, {}]);
  R('odd-xp', { quests: [quest('x1', 'daily', 'abc'), quest('x2', 'weekly', null), quest('x3', 'once', -5), quest('x4', 'daily', '12')] }, [now, 'UTC', {}, {}]);
  R('no-desc', { quests: [{ id: 'nd', name: 'Run 3 times', cadence: 'weekly', xp: 20 }] }, [now, 'UTC', {}, {}]);
  R('locked-daily-count', { quests: [quest('ld1', 'daily', 10, { name: 'Complete 3 workouts', stage: 3 }), quest('ld2', 'once', 10, { name: '2 deep work blocks', stage: 4 })] }, [now, 'UTC', {}, {}]);
  R('no-cadence', { quests: [{ id: 'nc', name: 'Run 3 times', xp: 20 }] }, [now, 'UTC', {}, {}]);
  R('not-connected', { quests: null }, [now, 'UTC', {}, {}], { rule: 'HA-2 HA-16' });
  R('empty', { quests: [] }, [now, 'UTC', {}, {}]);
  /* the week's bounds are the plan zone's, the keys the device's (D1 for quests) */
  for (const z of ZONES) for (const dz of ['UTC', 'America/New_York', 'Pacific/Auckland']) for (const t of ['2026-10-26T02:00:00.000Z', '2026-11-01T23:30:00.000Z', '2026-11-02T03:00:00.000Z'])
    R('zones/' + dz + '/' + z + '/' + t.slice(5, 13), { quests: mixed.filter(q => q.cadence !== 'once'), questDone: { 'w1_2026-10-26': 'a', 'w1_2026-11-02': 'b', 'd1_2026-11-01': 'c' } },
      [ep(t), z, {}, {}], { zone: dz, now: t, rule: 'HA-9 HA-11 HA-16' });
  R('zone-invalid', { quests: mixed }, [now, 'Mars/Base', {}, {}]);
  R('dst-week', { quests: mixed }, [ep('2026-11-01T12:00:00Z'), 'America/New_York', {}, {}], { zone: 'America/New_York', now: '2026-11-01T12:00:00.000Z', rule: 'HA-9 HA-16' });
  R('spring-week', { quests: mixed }, [ep('2026-03-08T12:00:00Z'), 'America/New_York', {}, {}], { zone: 'America/New_York', now: '2026-03-08T12:00:00.000Z', rule: 'HA-9 HA-16' });
  TEXTS.forEach((t, i) => R('text/' + i, { quests: [quest('t' + i, CADENCES[i % 6], 10 + i, { name: t, kind: KINDS[i % KINDS.length], attribute: ATTRS[i % ATTRS.length] || undefined })] },
    [now, 'UTC', {}, {}], { rule: 'HA-5 HA-6 HA-7 HA-8 HA-16' }));
  TEMPLATES.forEach((t, i) => { if (i % 2 === 0) R('hand/' + t.id, { quests: [hand(t, TODAY, i % 3)] }, [now, 'UTC', {}, {}], { rule: 'HA-8 HA-16' }); });
}

function planCases() {
  const P = (id, state, args, o = {}) => add('plan', id, o.rule || 'HA-17', { state, zone: o.zone, now: o.now, tick: o.tick, calls: [C('LifeMaxHost.readPlanSessions', ...args)].concat(o.more || []) });
  const plans = { foundation: plan('athlete_foundation8', '2026-10-26'), beginner: plan('athlete_beginner8', '2026-10-19'), scholar: plan('scholar_light8', '2026-10-26', { classId: 'scholar' }),
    entrepreneur: plan('entrepreneur_light8', '2026-10-26', { classId: 'entrepreneur' }), explorer: plan('explorer_outdoor8', '2026-10-26', { classId: 'explorer' }),
    later: plan('athlete_foundation8', '2026-11-02'), from: plan('athlete_foundation8', '2026-10-26', { from: '2026-10-30' }), ended: plan('athlete_beginner8', '2026-08-24'),
    unknown: plan('mystery_plan', '2026-10-26') };
  for (const [name, p] of Object.entries(plans)) for (const [z, dz] of [['UTC', 'UTC'], ['America/New_York', 'America/New_York'], ['Pacific/Auckland', 'UTC']])
    P(name + '/' + z, { plan: p }, [ep(T0), z, 14], { zone: dz });
  for (const d of [0, 1, 2, 7, 28, -1, 2.5, '3', null]) P('days/' + d, { plan: plans.foundation }, [ep(T0), 'UTC', d]);
  P('no-plan', {}, [ep(T0), 'UTC', 7]);
  P('not-connected', { quests: null, plan: plans.foundation }, [ep(T0), 'UTC', 7], { rule: 'HA-2 HA-17' });
  /* a day ticked complete (dayDone) */
  const ticks = keys => Object.fromEntries(keys.map(k => [k, [true, true, true]]));
  const pd = { warm0: [true], warm1: [true], warm2: [true], E0: [true, true, true], E1: [true, true, true], E2: [true, true, true], E3: [true, true, true], E4: [true, true], cool0: [true], cool1: [true] };
  P('done-day', { plan: plan('athlete_foundation8', '2026-10-26', { checks: { '2026-10-28': { s: pd } } }) }, [ep(T0), 'UTC', 7],
    { more: [C('LifeMaxHost.isPlanDayDone', '2026-10-28'), C('LifeMaxHost.isPlanDayDone', '2026-10-29'), C('LifeMaxHost.isPlanDayDone', '2026-11-01')] });
  P('partly-done', { plan: plan('athlete_foundation8', '2026-10-26', { checks: { '2026-10-28': { s: ticks(['warm0', 'E0']) } } }) }, [ep(T0), 'UTC', 7],
    { more: [C('LifeMaxHost.isPlanDayDone', '2026-10-28')] });
  P('done-days-beginner', { plan: plan('athlete_beginner8', '2026-10-26', { checks: { '2026-10-28': { s: ticks(['warm', 'B1', 'B2', 'B3', 'B4', 'B5', 'B6']) } } }) }, [ep(T0), 'UTC', 7],
    { more: [C('LifeMaxHost.isPlanDayDone', '2026-10-28'), C('LifeMaxHost.isPlanDayDone', 'not-a-day')] });
  for (const z of ['Asia/Tokyo', 'America/Los_Angeles', 'Australia/Lord_Howe', 'Asia/Kolkata'])
    for (const t of ['2026-10-28T15:30:00.000Z', '2026-11-01T07:30:00.000Z', '2026-10-31T23:00:00.000Z'])
      P('zones/' + z + '/' + t.slice(5, 13), { plan: plans.foundation }, [ep(t), z, 9], { zone: 'UTC', now: t, rule: 'HA-11 HA-17' });
  /* days ticked done on the Plan screen (the setup ticks every required item of those days) */
  for (const [name, p] of Object.entries(plans)) {
    if (name === 'unknown' || name === 'ended') continue;
    P('ticked/' + name, { plan: p }, [ep(T0), 'UTC', 10], { tick: ['2026-10-28', '2026-10-29', '2026-10-30', '2026-11-02', '2026-11-04'],
      more: ['2026-10-28', '2026-10-29', '2026-11-01', '2026-11-02'].map(k => C('LifeMaxHost.isPlanDayDone', k)) });
  }
  for (const z of ['America/Los_Angeles', 'Pacific/Auckland'])
    P('ticked-zone/' + z, { plan: plans.foundation }, [ep('2026-10-29T03:00:00Z'), z, 5], { tick: ['2026-10-28', '2026-10-29'], zone: 'UTC', now: '2026-10-29T03:00:00.000Z', rule: 'HA-11 HA-17' });
  P('zone-invalid', { plan: plans.foundation }, [ep(T0), 'Mars/Base', 3]);
  P('dst-new-york', { plan: plans.foundation }, [ep('2026-10-31T12:00:00Z'), 'America/New_York', 4], { zone: 'America/New_York', now: '2026-10-31T12:00:00.000Z' });
  P('dst-london', { plan: plans.foundation }, [ep('2026-10-24T12:00:00Z'), 'Europe/London', 4], { zone: 'Europe/London', now: '2026-10-24T12:00:00.000Z' });
  /* Cairo changes its clocks at midnight on a Thursday (autumn) and a Friday (spring): a weekday plan session on a 25- or 23-hour day */
  for (const [name, p] of [['foundation', plans.foundation], ['scholar', plans.scholar], ['beginner', plans.beginner]])
    P('dst-cairo-autumn/' + name, { plan: p }, [ep('2026-10-27T12:00:00Z'), 'Africa/Cairo', 4], { zone: 'Africa/Cairo', now: '2026-10-27T12:00:00.000Z', rule: 'HA-9 HA-17' });
  for (const [name, p] of [['foundation', plan('athlete_foundation8', '2026-04-20')], ['scholar', plan('scholar_light8', '2026-04-20', { classId: 'scholar' })]])
    P('dst-cairo-spring/' + name, { plan: p }, [ep('2026-04-22T12:00:00Z'), 'Africa/Cairo', 4], { zone: 'Africa/Cairo', now: '2026-04-22T12:00:00.000Z', rule: 'HA-9 HA-17' });
  P('plan-falsy', { plan: 0 }, [ep(T0), 'UTC', 7]);
}

function milestones() {
  const M = (id, state, o = {}) => add('milestones', id, 'HA-18', { state, zone: o.zone, now: o.now, calls: [C('LifeMaxHost.milestones')].concat(o.more || []) });
  const ms = TEXTS.map((t, i) => quest('m' + i, i % 2 ? 'epic' : 'once', 50, { name: t, kind: i % 3 ? 'milestone' : 'hand', desc: i % 4 ? '' : 'Keep a streak', stage: i % 7 === 0 ? 3 : undefined }));
  for (let i = 0; i < ms.length; i += 8) M('texts/' + i, { quests: ms.slice(i, i + 8) });
  for (const [name, qs] of Object.entries(questStates())) M('set/' + name, { quests: qs });
  M('done', { quests: ms.slice(16, 32), questDone: Object.fromEntries(ms.slice(16, 32).filter((q, i) => i % 2).map(q => [q.id + '_once', 'x'])) });
  M('locked-then-level', { quests: ms.slice(0, 16), activities: [rec('business', 5000, { day: dayKey(-30) })] });
  M('not-connected', { quests: null });
  M('null-entry', { quests: [null, quest('m1', 'epic', 10, { name: '50 Sessions Logged' })] });
  M('no-desc', { quests: [{ id: 'nd', name: 'Ten sessions in total', cadence: 'epic', xp: 5 }] });
}

function retired() {
  const T = (id, state, ref) => add('retired', id, 'HA-19', { state, calls: [C('LifeMaxHost.retiredUntimedQuest', ref)] });
  TEMPLATES.forEach((t, i) => T('template/' + t.id, {}, { questId: 'dh_2026-10-27_' + (i % 3) + '_0', questTitle: t.name }));
  T('still-on-board', { quests: [hand(tpl('t4'), '2026-10-27', 0)] }, { questId: 'dh_2026-10-27_0_0', questTitle: tpl('t4').name });
  T('not-hand-id', {}, { questId: 'cc_athlete_01', questTitle: tpl('t4').name });
  T('unknown-title', {}, { questId: 'dh_2026-10-27_0_0', questTitle: 'Not a template' });
  T('no-title', {}, { questId: 'dh_2026-10-27_0_0' });
  T('null-ref', {}, null);
  T('number-id', {}, { questId: 42, questTitle: tpl('t4').name });
  T('prefix-only', {}, { questId: 'dh_', questTitle: tpl('t4').name });
  T('case', {}, { questId: 'DH_2026-10-27_0_0', questTitle: tpl('t4').name });
  T('title-case', {}, { questId: 'dh_2026-10-27_0_0', questTitle: tpl('t4').name.toUpperCase() });
  T('board-has-number-id', { quests: [{ id: 5, name: 'x', cadence: 'daily', xp: 1 }] }, { questId: '5', questTitle: tpl('t4').name });
  T('not-connected', { quests: null }, { questId: 'dh_2026-10-27_0_0', questTitle: tpl('t4').name });
}

function since() {
  const acts = [rec('reading', 20, { id: 'a1', date: '2026-10-27T08:00:00.000Z' }), rec('strength', 30, { id: 'a2', date: '2026-10-28T08:00:00.000Z', dur: 45 }),
    rec('plan', 40, { id: 'a3', kind: 'strength', planDate: '2026-10-28', date: '2026-10-28T09:00:00.000Z' }), rec('plan', 40, { id: 'a4', kind: 'yoga', date: '2026-10-28T09:30:00.000Z' }),
    rec('plan', 20, { id: 'a4b', kind: 'cardio', date: '2026-10-28T09:40:00.000Z' }),
    qrec('daily', 15, { id: 'a5', date: '2026-10-28T10:00:00.000Z' }), rec('strength', 46, { id: 'ld_e1', ldEventId: 'e1', date: '2026-10-28T10:30:00.000Z' }),
    rec('running', 20, { id: 'a6', dur: '25', date: '2026-10-28T11:00:00.000Z' }), rec('yoga', 20, { id: 'a7', dur: 'abc', date: '2026-10-28T11:10:00.000Z' }),
    rec('custom', 10, { id: 'a8', date: '2026-10-28T11:20:00.000Z', ldEventId: '' }), rec('task', 10, { id: 'a9', date: '2026-10-28T11:20:00.000Z' }),
    rec('business', 10, { id: 'a10', date: '2026-10-28T11:20:00.000+02:00' }), rec('recovery', 10, { id: 'a11', date: '2026-10-28' }), rec('steps', 5, { id: 'a12', src: 'health' })];
  const S = (id, state, calls) => add('since', id, 'HA-20', { state, calls });
  S('all', { activities: acts }, [C('LifeMaxHost.activitiesSince', null)]);
  S('empty-cursor', { activities: acts }, [C('LifeMaxHost.activitiesSince', '')]);
  for (const c of ['2026-10-27T08:00:00.000Z', '2026-10-28T08:00:00.000Z', '2026-10-28T09:30:00.000Z', '2026-10-28T11:20:00.000Z', '2026-10-28T11', '2026-10-28', '2027', '1', 'Z'])
    S('cursor/' + c, { activities: acts }, [C('LifeMaxHost.activitiesSince', c)]);
  S('none', {}, [C('LifeMaxHost.activitiesSince', null)]);
  S('not-connected', { quests: null, activities: acts }, [C('LifeMaxHost.activitiesSince', null), C('LifeMaxHost.activityExists', 'a1')]);
  for (const id of ['a1', 'a5', 'ld_e1', 'zz', '', 'A1']) S('exists/' + (id || 'empty'), { activities: acts }, [C('LifeMaxHost.activityExists', id)]);
  S('exists-none', {}, [C('LifeMaxHost.activityExists', 'a1')]);
  for (const t of ['strength', 'running', 'language', 'reading', 'business', 'recovery', 'practical', 'task', 'custom', 'yoga', 'catalog', 'coach', 'sleep'])
    S('type/' + t, { activities: [rec(t, 10, { id: 'x-' + t })] }, [C('LifeMaxHost.activitiesSince', null)]);
  for (const k of ['strength', 'cardio', 'recovery', 'reading', 'mobility', 'rest', ''])
    S('plan-kind/' + (k || 'empty'), { activities: [rec('plan', 10, { id: 'p-' + k, kind: k })] }, [C('LifeMaxHost.activitiesSince', null)]);
}

function describe() {
  const D = (id, state, inst, o = {}) => add('describe', id, 'HA-21', { state, zone: o.zone, calls: [C('LifeMaxHost.activityDescription', inst)] });
  const p = plan('athlete_foundation8', '2026-10-26');
  const inst = (questId, title = 'Session', questTitle) => ({ title, linkedQuestRequirementIds: [{ questId, ...(questTitle ? { questTitle } : {}) }] });
  D('plan-day', { plan: p }, inst('plan:2026-10-28'));
  D('plan-day-next', { plan: p }, inst('plan:2026-10-28@next'));
  D('plan-rest-day', { plan: p }, inst('plan:2026-11-01'));
  D('plan-no-day', { plan: p }, inst('plan:2027-06-01'));
  D('plan-none', {}, inst('plan:2026-10-28'));
  D('plan-beginner', { plan: plan('athlete_beginner8', '2026-10-26') }, inst('plan:2026-10-28'));
  D('plan-scholar', { plan: plan('scholar_light8', '2026-10-26', { classId: 'scholar' }) }, inst('plan:2026-10-28'));
  D('quest', { quests: CLASS_QUESTS.athlete }, inst(CLASS_QUESTS.athlete[0].id));
  D('quest-next', { quests: [quest('w1', 'weekly', 10, { desc: '  Three runs.  ' })] }, inst('w1@next'));
  D('quest-blank-desc', { quests: [quest('w1', 'weekly', 10, { desc: '   ' })] }, inst('w1'));
  D('quest-no-desc', { quests: [{ id: 'w1', name: 'x', cadence: 'weekly', xp: 1 }] }, inst('w1'));
  D('quest-number-desc', { quests: [{ id: 'w1', name: 'x', cadence: 'weekly', xp: 1, desc: 5 }] }, inst('w1'));
  D('unknown', {}, inst('zz'));
  D('two-refs', { quests: [quest('w1', 'weekly', 10, { desc: 'Second wins' })] }, { title: 'S', linkedQuestRequirementIds: [{ questId: 'zz' }, { questId: 'w1' }] });
  D('no-refs', {}, { title: 'S' });
  D('null', {}, null);
  D('not-connected', { quests: null }, inst('w1'));
  TEMPLATES.forEach((t, i) => { if (i % 3 === 0) D('retired/' + t.id, {}, inst('dh_2026-10-27_0_0', t.name)); });
  D('retired-ref-title', {}, inst('dh_2026-10-27_0_0', 'Session', tpl('t4').name));
  D('retired-unknown', {}, inst('dh_2026-10-27_0_0', 'Nothing'));
  D('hand-on-board', { quests: [hand(tpl('t4'))] }, inst('dh_2026-10-28_0_0'));
}

function meals() {
  const M = (id, ref, o = {}) => add('meals', id, 'HA-22', { state: o.state || {}, zone: o.zone, now: o.now, meals: o.meals, calls: [C('LifeMaxMeals.resolveScheduled', ref)] });
  const B = (id, block, tz, o = {}) => add('meals', id, 'HA-22 HA-23', { state: o.state || {}, zone: o.zone, now: o.now, calls: [C('mealFor', block, tz)] });
  for (const slot of ['Breakfast', 'Lunch', 'Dinner', 'Snack', 'Snacks', 'Before training', 'After training', 'pre-workout', 'Post  workout', ' lunch ', 'Brunch', '', 'LUNCH', 'constructor', 'Lunch '])
    M('slot/' + JSON.stringify(slot), { title: slot, day: TODAY });
  M('slot-over-title', { slot: 'dinner', title: 'Lunch', day: TODAY });
  for (const d of ['2026-10-29', '2026-11-05', '2026-02-29', '2026-02-30', '2026-13-01', '2026-1-01', '2026-10-28T00:00', 'tomorrow', '0000-01-01', '2028-02-29'])
    M('day/' + d, { title: 'Lunch', day: d });
  M('epoch', { title: 'Dinner', startEpoch: ep('2026-10-28T18:00:00Z') });
  M('epoch-text', { title: 'Dinner', startEpoch: String(ep('2026-10-28T18:00:00Z')) });
  M('epoch-zero', { title: 'Dinner', startEpoch: 0 });
  M('epoch-bad', { title: 'Dinner', startEpoch: 'abc' });
  M('epoch-huge', { title: 'Dinner', startEpoch: 1e15 });
  M('null', null);
  for (const z of ['America/New_York', 'Pacific/Auckland', 'Asia/Kolkata'])
    M('epoch-zone/' + z, { title: 'Breakfast', startEpoch: ep('2026-10-28T03:00:00Z') }, { zone: z });
  M('apia-missing-day', { title: 'Lunch', day: '2011-12-30' }, { zone: 'Pacific/Apia' });
  M('apia-day-after', { title: 'Lunch', day: '2011-12-31' }, { zone: 'Pacific/Apia' });
  /* the meal block a plan holds, as logic.js:1076-1081 asks for it (the day is the plan zone's date of the meal's start) */
  for (const z of ['UTC', 'America/New_York', 'Pacific/Auckland', 'Asia/Kolkata']) for (const t of ['2026-10-28T07:30:00Z', '2026-10-28T12:30:00Z', '2026-10-28T23:30:00Z'])
    for (const title of ['Breakfast', 'Lunch', 'Dinner'])
      B('block/' + z + '/' + t.slice(11, 16) + '/' + title, { title, startEpoch: ep(t), endEpoch: ep(t) + 1800 }, z, { zone: 'UTC' });
  B('block-meal-start', { title: 'Dinner', startEpoch: ep('2026-10-28T23:30:00Z'), mealStartEpoch: ep('2026-10-29T00:15:00Z'), endEpoch: ep('2026-10-29T00:45:00Z') }, 'UTC');
  B('block-device-differs', { title: 'Lunch', startEpoch: ep('2026-10-28T02:00:00Z'), endEpoch: ep('2026-10-28T02:30:00Z') }, 'UTC', { zone: 'America/New_York' });
  M('logged-meal', { title: 'Lunch', day: TODAY }, { state: { foodLog: [{ id: 'f1', date: TODAY, slot: 'lunch', name: 'Leftover Chicken Spinach Pasta', grams: 300, kcal: 539, p: 0, c: 0, f: 0 }] } });
  /* meals logged and skipped on the Food screen (the set-up logs or skips them through Smart Diet itself) */
  for (const slot of ['Breakfast', 'Lunch', 'Snack', 'Dinner']) {
    M('logged/' + slot, { title: slot, day: TODAY }, { meals: { log: [[TODAY, 0], [TODAY, 1], [TODAY, 2]] } });
    M('skipped/' + slot, { title: slot, day: TODAY }, { meals: { skip: [[TODAY, 1], [TODAY, 2], [TODAY, 4]] } });
  }
}

function flow() {
  const daily = quest('d1', 'daily', 15, { name: 'Morning run', kind: 'cardio' }), weekly = quest('w1', 'weekly', 40, { name: 'Complete 3 workouts', kind: 'strength' });
  const F = (id, state, calls, o = {}) => add('flow', id, o.rule || 'HA-24', { state, zone: o.zone, now: o.now, calls });
  const rq = (z = 'UTC') => C('LifeMaxHost.readQuests', ep(T0), z, {}, {});
  F('award-then-quests-then-bounty', { quests: [daily, weekly] }, [C('LifeMaxHost.awardActivity', ev({ activityTypeTag: 'fitness.cardio' }), { title: 'Run' }), rq(),
    C('LifeMaxHost.completeQuest', { questId: 'd1', key: 'd1_2026-10-28' }), rq(), C('LifeMaxHost.activitiesSince', null), C('LifeMaxHost.activityExists', 'ld_e1')]);
  F('award-twice', {}, [C('LifeMaxHost.awardActivity', ev(), {}), C('LifeMaxHost.awardActivity', ev(), { title: 'Again' })]);
  F('three-awards-bonus', {}, [1, 2, 3, 4].map(i => C('LifeMaxHost.awardActivity', ev({ eventId: 'e' + i, actualDurationMinutes: 60, activityTypeTag: ['reading', 'business', 'study', 'language'][i - 1] }), {})));
  F('awards-to-cap', {}, [1, 2, 3, 4, 5, 6].map(i => C('LifeMaxHost.awardActivity', ev({ eventId: 'c' + i, actualDurationMinutes: 120, activityTypeTag: ['reading', 'business', 'fitness', 'language', 'wellbeing', 'practical'][i - 1] }), {})));
  F('preview-then-award', { restedXP: 30 }, [C('LifeMaxHost.xpFor', 'strength', 45, 1, TODAY), C('LifeMaxHost.awardActivity', ev(), {}), C('LifeMaxHost.xpFor', 'strength', 45, 1, TODAY)]);
  F('bounty-then-locked-read', { quests: [daily, quest('s3', 'weekly', 50, { stage: 3 })] }, [C('LifeMaxHost.completeQuest', { questId: 'd1', key: 'd1_2026-10-28' }), rq(), C('LifeMaxHost.milestones')]);
  F('day-moves-on', { quests: [daily] }, [C('LifeMaxHost.completeQuest', { questId: 'd1', key: 'd1_2026-10-28' }), at('2026-10-29T12:00:00.000Z', rq()),
    at('2026-10-29T12:00:00.000Z', C('LifeMaxHost.completeQuest', { questId: 'd1', key: 'd1_2026-10-28' })),
    at('2026-10-29T12:00:00.000Z', C('LifeMaxHost.completeQuest', { questId: 'd1', key: 'd1_2026-10-29' }))]);
  F('award-at-later-clock', {}, [at('2026-10-29T01:00:00.000Z', C('LifeMaxHost.awardActivity', ev(), {})), at('2026-10-29T01:00:00.000Z', C('LifeMaxHost.xpFor', 'strength', 45, 1, null))],
    { zone: 'America/New_York' });
  F('level-unlocks', { quests: [quest('s2', 'daily', 20, { stage: 2 })], activities: [rec('business', 230, { day: dayKey(-3) })] },
    [rq(), ...[1, 2, 3].map(i => C('LifeMaxHost.awardActivity', ev({ eventId: 'l' + i, actualDurationMinutes: 120, activityTypeTag: ['reading', 'business', 'fitness'][i - 1] }), {})), rq(),
      C('LifeMaxHost.completeQuest', { questId: 's2', key: 's2_2026-10-28' })]);
  F('weekly-bounty-and-next', { quests: [weekly] }, [C('LifeMaxHost.readQuests', ep(T0), 'UTC', { 'w1_2026-10-26': { eventIds: ['a', 'b', 'c'] } }, {}),
    C('LifeMaxHost.completeQuest', { questId: 'w1', key: 'w1_2026-10-26' }), C('LifeMaxHost.readQuests', ep(T0), 'UTC', {}, {})]);
  F('save-fails-then-ok', { restedXP: 50 }, [withEnv({ failSave: 'storage' }, C('LifeMaxHost.awardActivity', ev(), {})), C('LifeMaxHost.awardActivity', ev(), {})]);
  F('plan-and-describe', { plan: plan('athlete_foundation8', '2026-10-26'), quests: CLASS_QUESTS.athlete },
    [C('LifeMaxHost.readPlanSessions', ep(T0), 'UTC', 3), C('LifeMaxHost.activityDescription', { title: 'P', linkedQuestRequirementIds: [{ questId: 'plan:2026-10-28' }] }), rq()]);
}

function verifyCases() {
  /* the Daily Hand checks its cards after the award's save (SAVE-2): a card the session meets is paid in the same award */
  const cards = ids => ids.map((id, i) => hand(tpl(id), TODAY, i));
  const draw = qs => ({ dhMig: 1, dailyDraw: { date: TODAY, nonce: 0, rerollsUsed: 0, cards: qs.map((q, i) => ({ tpl: q.tpl, id: q.id, n: 0 })) } });
  const V = (id, ids, e, extra = {}) => { const qs = cards(ids); add('verify', id, 'HA-25', { state: { quests: qs, ...draw(qs), ...extra }, calls: [C('LifeMaxHost.awardActivity', e, {})] }); };
  V('strength-10', ['t1', 't4', 't6'], ev({ actualDurationMinutes: 15 }));
  V('strength-25', ['t1', 't2', 't3'], ev({ actualDurationMinutes: 30 }));
  V('strength-45', ['t1', 't2', 't3'], ev({ actualDurationMinutes: 45 }));
  V('running-15', ['t8', 't9', 't4'], ev({ activityTypeTag: 'fitness.cardio', actualDurationMinutes: 20 }));
  V('running-25', ['t8', 't9', 't4'], ev({ activityTypeTag: 'fitness.cardio', actualDurationMinutes: 25 }));
  V('wrong-type', ['t8', 't9', 't4'], ev({ actualDurationMinutes: 40 }));
  V('self-cards-only', ['t4', 't5', 't6'], ev({ actualDurationMinutes: 40 }));
  V('already-done', ['t1', 't2', 't3'], ev({ actualDurationMinutes: 50 }), { questDone: { 'dh_2026-10-28_0_0_2026-10-28': 'q0' }, activities: [qrec('daily', 10, { id: 'q0' })] });
  V('yesterday-session', ['t1', 't2', 't3'], ev({ actualStartEpoch: ep('2026-10-27T10:00:00Z'), actualDurationMinutes: 50 }));
  for (const t of TEMPLATES.filter(t => /^(act|any):/.test(t.verify))) {
    const v = t.verify.split(':'), mins = Number(v[v.length - 1]) || 10;
    const tag = v[0] === 'any' ? 'study' : { strength: 'fitness.strength', running: 'fitness.cardio', reading: 'reading', language: 'language', business: 'business', recovery: 'wellbeing',
      practical: 'practical', task: 'chores', custom: 'social' }[v[1]] || 'general';
    V('template/' + t.id, [t.id, 't4', 't6'], ev({ activityTypeTag: tag, actualDurationMinutes: mins }));
  }
}

function fuzz() {
  const r = rng(11011);
  const pick = a => a[Math.floor(r() * a.length)];
  const S = questStates();
  const pool = [].concat(S.mixed, S.athlete, S.scholar, S.entrepreneur, S.explorer, S.defaults, TEXTS.slice(0, 30).map((t, i) => quest('f' + i, pick(CADENCES), Math.floor(r() * 80), { name: t, kind: pick(KINDS), attribute: pick(ATTRS) || undefined })));
  const tagsList = ['fitness.strength', 'fitness.cardio', 'reading', 'study', 'language', 'business', 'admin', 'wellbeing', 'practical', 'chores', 'social', 'general', 'nutrition'];
  for (let n = 0; n < 300; n++) {
    const zone = pick(ZONES), tz = r() < 0.6 ? zone : pick(ZONES);
    const now = new Date(ms('2026-10-20T00:00:00Z') + Math.floor(r() * 20 * 96) * 900e3).toISOString();
    const nowE = ep(now), today = now.slice(0, 10);
    const qs = []; const k = 1 + Math.floor(r() * 7);
    for (let i = 0; i < k; i++) { const q = pick(pool); if (!qs.some(x => x.id === q.id)) qs.push(q); }
    const acts = []; const a = Math.floor(r() * 6);
    for (let i = 0; i < a; i++) acts.push(rec(pick(['reading', 'strength', 'running', 'business', 'task', 'plan', 'custom']), Math.floor(r() * 60), { date: new Date(ms(now) - Math.floor(r() * 72) * 3600e3).toISOString(),
      ldBonus: r() < 0.2 ? 2 + Math.floor(r() * 4) : undefined, dur: 5 + Math.floor(r() * 90), kind: r() < 0.3 ? pick(['strength', 'cardio', 'recovery']) : undefined }));
    const state = { quests: qs, activities: acts };
    if (r() < 0.3) state.restedXP = Math.floor(r() * 120);
    let tick;
    if (r() < 0.3) { state.plan = plan(pick(['athlete_foundation8', 'athlete_beginner8', 'scholar_light8']), pick(['2026-10-12', '2026-10-19', '2026-10-26'])); if (r() < 0.5) tick = [today, dayKey(Math.floor(r() * 6) - 2)]; }
    if (r() < 0.15) Object.assign(state, perk(pick(['exec', 'compound', 'scholar'])));
    const calls = [];
    const steps = 1 + Math.floor(r() * 5);
    for (let s = 0; s < steps; s++) {
      const kind = Math.floor(r() * 9);
      const q = qs.length ? pick(qs) : null;
      if (kind === 0) calls.push(C('LifeMaxHost.awardActivity', ev({ eventId: 'fz' + n + '_' + s, activityTypeTag: pick(tagsList), actualDurationMinutes: 5 + Math.floor(r() * 120),
        actualStartEpoch: nowE - Math.floor(r() * 30) * 900, lifecycleState: r() < 0.2 ? 'PARTIALLY_COMPLETED' : 'COMPLETED' }), r() < 0.5 ? { title: 'Fuzz ' + s } : null));
      else if (kind === 1 && q) calls.push(C('LifeMaxHost.completeQuest', { questId: q.id, key: q.cadence === 'daily' ? q.id + '_' + today : q.cadence === 'weekly' ? q.id + '_' + pick(['2026-10-19', '2026-10-26', '2026-11-02']) : q.id + '_once', future: r() < 0.1 }));
      else if (kind === 2) { const ov = {}; for (const x of qs) if (r() < 0.5) ov[x.id + '_' + (x.cadence === 'daily' ? today : x.cadence === 'weekly' ? '2026-10-26' : 'once')] = { eventIds: ['e1', 'e2', 'e3'].slice(0, 1 + Math.floor(r() * 3)) };
        calls.push(C('LifeMaxHost.readQuests', nowE, tz, ov, r() < 0.3 ? { e2: {} } : {})); }
      else if (kind === 3) calls.push(C('LifeMaxHost.readPlanSessions', nowE, tz, Math.floor(r() * 15)));
      else if (kind === 4) calls.push(C('LifeMaxHost.milestones'));
      else if (kind === 5) calls.push(C('LifeMaxHost.xpFor', pick(['reading', 'strength', 'running', 'business', 'custom', 'task']), Math.floor(r() * 150), 1, r() < 0.5 ? null : today, r() < 0.3));
      else if (kind === 6) calls.push(C('LifeMaxHost.activitiesSince', r() < 0.5 ? null : new Date(ms(now) - Math.floor(r() * 48) * 3600e3).toISOString()));
      else if (kind === 7) calls.push(C('LifeMaxHost.activityExists', acts.length && r() < 0.7 ? pick(acts).id : 'nope'));
      else calls.push(C('mealFor', { title: pick(['Breakfast', 'Lunch', 'Dinner', 'Snack']), startEpoch: nowE + Math.floor(r() * 96) * 900 }, tz));
    }
    add('fuzz', String(n), 'HA-24', { state, zone, now, tick, calls });
  }
}

function defineCases() {
  if (CASES.length) return CASES;
  [tags, week, player, xp, award, bounty, quests, planCases, milestones, retired, since, describe, meals, flow, verifyCases, fuzz].forEach(f => f());
  return CASES;
}

module.exports = { defineCases, FAMILIES, ZONES, T0 };
