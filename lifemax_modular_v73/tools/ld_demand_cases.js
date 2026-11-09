/* The Life Director demand layer's golden cases (WP-LD3): what tools/gen_ld_demand_golden.js records from the page and the native demand
   code (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce. Rules are LIFE_DIRECTOR_ENGINE_SPEC.md's
   part 3 (LDD tags and goals, LDQ quests, LDC candidates, LDE energy and preparation, LDF floating point).

   Every case is {id, family, rule, fn, args}; the recorder adds out. As in tools/ld_kernel_cases.js the definitions use no Intl: every
   instant comes from Date.UTC and the clock-change table of that file, and only the strict zones of the WP-LD1 matrix are used. Inputs are
   JSON values as the page's store and host hand them to the engine; a number JSON cannot hold is written {$num: ...}. */
'use strict';
const { MATRIX, FIXED, CHANGES } = require('./ld_kernel_cases.js');

const FAMILIES = ['tag', 'goal', 'template', 'expand', 'goalinfo', 'quest', 'cycle', 'ids', 'candidates', 'coverage', 'prep', 'drain', 'float',
  'scenario', 'fuzz'];
const ZONES = MATRIX;
const SCENARIO_NAMES = ['office', 'night', 'rotating', 'student', 'abundant', 'limited', 'manygoals', 'deadlines', 'misses', 'workchange'];

/* ---------- small helpers (no Intl) ---------- */
const utc = (y, mo, d, h = 0, mi = 0, s = 0) => Date.UTC(y, mo - 1, d, h, mi, s) / 1000;
const num = v => (Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' } : Object.is(v, -0) ? { $num: '-0' } : v);
function offsetAt(zone, t) {
  if (zone in FIXED) return FIXED[zone];
  let off = null;
  for (const c of CHANGES.filter(c => c.zone === zone)) { if (off === null) off = c.before; if (t >= c.at) off = c.after; }
  return off;
}
const localInstant = (zone, y, mo, d, min = 0) => { const w = utc(y, mo, d, 0, min); return w - offsetAt(zone, w - offsetAt(zone, w) * 60) * 60; };
/* Seeded generator (xorshift32; not the engine's mulberry32). */
function random(seed) {
  let x = seed >>> 0 || 1;
  return () => { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; };
}
const short = v => String(JSON.stringify(v)).replace(/[^\x20-\x7e]/g, '?').slice(0, 50);

/* Odd JSON values a stored field can hold. */
const ODD = [null, true, false, 0, num(-0), 1, -1, 0.5, 2.5, 3.5, 7, 99999, '', ' ', '3', ' 4 ', '1e1', 'abc', 'ACTIVE', 'SIDE', 'DAILY', 'NONE',
  [], [2], ['a', 'b'], {}, { a: 1 }, 'Español', '\u{1F600}', 'fitness.strength'];

const ARCH_IDS = ['FITNESS_HYPERTROPHY', 'SKILL_LANGUAGE', 'HABIT_READING', 'ADMIN_FINANCE', 'FITNESS_CARDIO', 'STUDY_GENERAL', 'CREATIVE_PRACTICE', 'MINDFULNESS'];
const LEVELS = ['NOVICE', 'INTERMEDIATE', 'ADVANCED'];
const INTENSITIES = ['LIGHT', 'BALANCED', 'AGGRESSIVE', 'HARDCORE'];
const TAGS = ['fitness', 'fitness.*', 'fitness.strength', 'fitness.strength.*', 'fitness.cardio', 'fitness.assessment', 'fit', 'fitness.', 'Fitness.strength',
  'language', 'language.*', 'language.vocab', 'language.spanish', 'language.spanish.*', 'language.spanish.vocab', 'language.spanish.assessment',
  'reading', 'reading.*', 'reading.focused', 'reading.checkpoint', 'study', 'study.deep', 'study.review', 'admin', 'admin.finance', 'admin.finance.audit',
  'creative', 'creative.practice', 'wellbeing', 'wellbeing.mindfulness', 'nutrition.mealprep', 'chores', 'social', 'general', '', '.', '..', '.*', '*',
  'a.*.b', 'x.*.*', '.*.*', 'fitness..strength', 'é', 'é.*', 'é.x', '日本語.読む', '\u{1F600}.*', '\u{1F600}.run', 'ﬁtness', 'FITNESS', 'fitness.strength.heavy'];

function defineCases(engine) {
  const cases = [], ids = new Set();
  const add = (family, rule, fn, args, tag) => {
    let id = family + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args });
  };
  const monday = zone => localInstant(zone, 2026, 10, 26, 360);

  /* tag: tagMatches over every pair, tagDepth where the engine would ask it, tagProfile of each */
  const REQ_ODD = [null, 0, 1, true, false, ['fitness'], {}, 'fitness.strength'];
  for (const a of TAGS) for (const b of TAGS) add('tag', 'LDD-1', 'tagMatches', [a, b], short(a) + '|' + short(b));
  for (const a of REQ_ODD) for (const b of ['fitness.strength', '', null, 7, ['fitness.strength']]) add('tag', 'LDD-1', 'tagMatches', [a, b], 'odd:' + short(a) + '|' + short(b));
  for (const a of TAGS.filter(t => t)) for (const b of ['fitness.strength', 'language.spanish.vocab', 'a.b', a]) add('tag', 'LDD-2', 'tagDepth', [a, b], short(a) + '|' + short(b));
  for (const t of TAGS.concat([null, 0, 5, true, ['study', 'x'], {}, 'constructorx', 'study.deep.x'])) add('tag', 'LDD-3', 'tagProfile', [t], short(t));

  /* goal: normalizeGoal for each field's odd values and the legacy names; suggestTemplates on titles */
  const GBASE = { id: 'g-1', title: 'Get strong', archetypeId: 'FITNESS_HYPERTROPHY', priority: 4, level: 'INTERMEDIATE', status: 'ACTIVE', createdEpoch: 1790000000 };
  const GFIELDS = ['id', 'goalId', 'title', 'archetypeId', 'domainArchetype', 'subject', 'priority', 'level', 'experienceLevel', 'customTemplates', 'disabledTemplates',
    'target', 'targetMetricValue', 'currentMetricValue', 'metricUnit', 'deadlineEpoch', 'unitsPerHour', 'createdEpoch', 'startEpoch', 'status', 'schedulingState'];
  for (const f of GFIELDS) {
    ODD.forEach((v, i) => add('goal', 'LDD-4', 'normalizeGoal', [Object.assign({}, GBASE, { [f]: v })], f + '=' + i + ':' + short(v)));
    const without = Object.assign({}, GBASE); delete without[f];
    add('goal', 'LDD-4', 'normalizeGoal', [without], f + '-missing');
  }
  const LEGACY_GOALS = [{ goalId: 'old', title: 'Old', domainArchetype: 'STUDY_GENERAL', experienceLevel: 'ADVANCED', targetMetricValue: 100, currentMetricValue: 20,
    metricUnit: 'pages', deadlineEpoch: 1800000000, unitsPerHour: 12, startEpoch: 1780000000, schedulingState: 'PAUSED' },
  { goalId: 'c', domainArchetype: 'CUSTOM' }, { id: 'both', goalId: 'x', archetypeId: 'MINDFULNESS', domainArchetype: 'STUDY_GENERAL', level: 'nope', experienceLevel: 'ADVANCED' },
  { id: 't', targetMetricValue: 0, unitsPerHour: 5 }, { id: 't2', targetMetricValue: 50 }, { id: 't3', target: { units: 1 }, targetMetricValue: 9 }, {},
  { id: 0, goalId: 0 }, { id: 'p', priority: 0 }, { id: 'p', priority: 5.5 }, { id: 'p', priority: 4.5 }, { id: 'p', priority: -3 }, { id: 'p', priority: '2' },
  { id: 'p', priority: 'x' }, { id: 's', subject: 'Español!' }, { id: 's', subject: 'İSTANBUL' }, { id: 's', subject: 'Ǆemal' }, { id: 's', subject: 'KELVIN_K' },
  { id: 's', subject: '日本語' }, { id: 's', subject: 'ŞOK 9_9' }, { id: 's', subject: 'ΣΑΣ' }, { id: 's', subject: 'ß' }];
  LEGACY_GOALS.forEach((g, i) => add('goal', 'LDD-4', 'normalizeGoal', [g], 'legacy' + i + ':' + short(g)));
  for (const v of [null, 5, 'x', true, [], [1]]) add('goal', 'LDD-4', 'normalizeGoal', [v], 'whole:' + short(v));
  const TITLES = ['', 'Get fit', 'Build muscle and strength', 'Gym lift workout', 'Learn Spanish', 'learn japanese vocab', 'Read more books', 'Save money, budget',
    'Run a 10K', 'Study for my exam', 'Play guitar and piano', 'Meditate', 'mindful journal', 'Health', 'HEALTH and WEIGHT', 'Lose weight', 'Write a novel',
    'Speak French', 'Pay off debt and invest', 'Walk daily', 'Swim', 'Calm down stress', 'Draw and paint art', 'Cert course class', 'German speak vocab language',
    'Strong fit healthy gym lift muscle workout strength', 'Re-read', 'Bread', 'readme', 'unrelated', 'Ｇym', 'GYM', 'Muscleİ', 'reading pages novel book',
    'create music', 'endurance cycle', 'learnt', 'Fit', 'fitness', 'Sauvegarder', 'Ideas\u{1F600}', 'İnvest', 'KELVIN money', 'ﬁt', 'Learn', 'learn to read',
    'save', 'budget', 'swim cycle run walk', 'exam study', 'class course cert', 'book', 'piano',
    'gym run read budget study draw meditate spanish', 'muscle book money exam'];
  TITLES.forEach((t, i) => add('goal', 'LDD-5', 'suggestTemplates', [t], i + ':' + short(t)));
  for (const t of [null, 5, true, ['gym'], { gym: 1 }]) add('goal', 'LDD-5', 'suggestTemplates', [t], 'odd:' + short(t));

  /* template: goalTemplates of every archetype (disabled templates, subjects) and of custom templates with odd fields */
  for (const a of ARCH_IDS) {
    add('template', 'LDD-6', 'goalTemplates', [{ id: 'g-' + a, title: a, archetypeId: a }], a);
    add('template', 'LDD-6', 'goalTemplates', [{ id: 'g', archetypeId: a, disabledTemplates: ['strength', 'vocab', 'reading', 'audit', 'cardio', 'study', 'practice'] }], a + '@disabled-first');
    add('template', 'LDD-6', 'goalTemplates', [{ id: 'g', archetypeId: a, subject: 'Spanish' }], a + '@subject');
  }
  for (const s of ['spanish', 'Español', '日本語', 'x-y z', 'İ', 'japanese_2', '', 0, true, ['de'], { a: 1 }])
    add('template', 'LDD-6', 'goalTemplates', [{ id: 'g', archetypeId: 'SKILL_LANGUAGE', subject: s }], 'subject:' + short(s));
  for (const d of [[], ['vocab'], ['vocab', 'grammar', 'speaking', 'check'], 'vocab', [null], ['VOCAB']])
    add('template', 'LDD-6', 'goalTemplates', [{ id: 'g', archetypeId: 'SKILL_LANGUAGE', disabledTemplates: d }], 'disabled:' + short(d));
  for (const a of ['NOPE', ['MINDFULNESS'], 'mindfulness', '', null, 7]) add('template', 'LDD-6', 'goalTemplates', [{ id: 'g', archetypeId: a }], 'arch:' + short(a));
  const CBASE = { title: 'Piano', tag: 'creative.piano', minutes: 40, perWeek: 4, IP: 1, IC: 3 };
  const CFIELDS = { tag: ODD, minutes: ODD, perWeek: ODD, IP: ODD, IC: ODD, title: [null, '', 'x', 5, ['t']], key: ODD, d: [[30, 45, 60], [10, 20], [45], [], 'abc', 30, { 1: 50 }, null],
    f: [[1, 2, 3], [5], [], [0, 0, 0], 'xyz', 4, { 1: 9 }, null], spacingMinH: [0, null, 6, '6', -1, 'x'], spacingIdealH: [0, 24, '24', null, 1e6],
    maxPerBwc: [0, 2, '3', null], phases: [null, [], ['PEAK'], 'WIND'], volumeDriver: [0, 1, 'yes', null, true] };
  for (const [k, vals] of Object.entries(CFIELDS)) vals.forEach((v, i) => add('template', 'LDD-7', 'goalTemplates',
    [{ id: 'g-c', title: 'Custom goal', customTemplates: [Object.assign({}, CBASE, { [k]: v })] }], 'custom:' + k + '=' + i + ':' + short(v)));
  for (const f of [1, 2, 3, 6, 7, 8, 13, 14, 15, 0.4, 6.5, 168, 200]) add('template', 'LDD-7', 'goalTemplates',
    [{ id: 'g-c', customTemplates: [Object.assign({}, CBASE, { perWeek: f })] }], 'perWeek:' + f);
  for (const m of [1, 14, 15, 16, 29, 30, 31, 44, 45, 46, 90, 0.5, 480]) add('template', 'LDD-7', 'goalTemplates',
    [{ id: 'g-c', customTemplates: [Object.assign({}, CBASE, { minutes: m })] }], 'minutes:' + m);
  const CLISTS = [[null, CBASE], [{ tag: 'x' }], [{ minutes: 30 }], [{ tag: 'a', d: [15, 15, 15] }], [CBASE, CBASE], [Object.assign({}, CBASE, { key: 'K!ey ö.-_9' })],
    [Object.assign({}, CBASE, { key: '' })], [Object.assign({}, CBASE, { key: null, tag: 'study.é x' })], 'x', {}, [5, 'y', true]];
  CLISTS.forEach((l, i) => add('template', 'LDD-7', 'goalTemplates', [{ id: 'g-c', title: 'T', customTemplates: l }], 'list' + i));
  add('template', 'LDD-6 LDD-7', 'goalTemplates', [{ id: 'g-mix', archetypeId: 'MINDFULNESS', customTemplates: [CBASE] }], 'archetype+custom');

  /* expand: expandGoalActivities for every archetype, level and intensity; Mode A pacing; clarifications; status and order */
  const NOW = utc(2026, 10, 26, 10);
  for (const a of ARCH_IDS) for (const lvl of LEVELS) for (const int of INTENSITIES)
    add('expand', 'LDD-8 LDD-9', 'expandGoalActivities', [[{ id: 'g', title: a, archetypeId: a, level: lvl }], { intensityLevel: int }, NOW], a + '@' + lvl + '@' + int);
  const PACE = [[3600, 500, 300, 30], [3600, 500, 30, 30], [3600, 3500, 300, 30], [3600, 3600, 300, 30], [3600, 4000, 300, 30], [100, 0, 7, 1], [100, 0, 7, 100],
    [100, 0, 1, 5], [1000, 0, 0.5, 10], [1000, 0, -10, 10], [1000, 0, 70, 0.5], [50, 49.5, 14, 20], [1e6, 0, 365, 1], [600, 0, 14, 60], [600, 0, 3, 60], [10, 0, 2, 1],
    [100, 0, 279, 1], [100, 0, 233, 1]];
  for (const [units, done, days, rate] of PACE) for (const a of ['HABIT_READING', 'STUDY_GENERAL', 'CREATIVE_PRACTICE']) for (const int of ['LIGHT', 'BALANCED', 'HARDCORE'])
    add('expand', 'LDD-8 LDD-10', 'expandGoalActivities', [[{ id: 'g-p', title: 'Pace ' + a, archetypeId: a, level: 'INTERMEDIATE',
      target: { units, completed: done, unit: 'u', unitsPerHour: rate, deadlineEpoch: NOW + days * 86400 } }], { intensityLevel: int }, NOW],
    'pace:' + [units, done, days, rate].join('/') + '@' + a + '@' + int);
  const ODD_TARGETS = [null, {}, { units: 0, deadlineEpoch: NOW + 86400, unitsPerHour: 5 }, { units: 10, unitsPerHour: 5 }, { units: 10, deadlineEpoch: NOW + 86400 },
    { units: '100', completed: '10', deadlineEpoch: NOW + 30 * 86400, unitsPerHour: '10' }, { units: 100, completed: null, deadlineEpoch: NOW + 30 * 86400, unitsPerHour: 10 },
    { units: 100, completed: 200, deadlineEpoch: NOW - 86400, unitsPerHour: 10 }];
  ODD_TARGETS.forEach((t, i) => add('expand', 'LDD-10', 'expandGoalActivities', [[{ id: 'g-o', title: 'Odd', archetypeId: 'HABIT_READING', target: t }], {}, NOW], 'target' + i + ':' + short(t)));
  const GOAL_SETS = [[], [{ id: 'g-x', title: 'Write a book' }], [{ id: 'g-x', title: 'Gym and health' }, { id: 'g-y', title: 'Nothing here' }],
    [{ id: 'b', archetypeId: 'MINDFULNESS' }, { id: 'a', archetypeId: 'FITNESS_CARDIO' }, { id: 'C', archetypeId: 'HABIT_READING' }, { goalId: 'aa', archetypeId: 'STUDY_GENERAL' }],
    [{ id: 'p', archetypeId: 'MINDFULNESS', status: 'PAUSED' }, { id: 'q', archetypeId: 'MINDFULNESS', status: 'active' }, { id: 'r', archetypeId: 'MINDFULNESS', schedulingState: 'ACTIVE' }],
    [{ archetypeId: 'MINDFULNESS' }, { archetypeId: 'FITNESS_CARDIO' }], [{ id: 'd', archetypeId: 'FITNESS_HYPERTROPHY', disabledTemplates: ['strength', 'mealprep', 'assess'], title: 'Gym' }],
    [{ id: 'n', archetypeId: 'FITNESS_HYPERTROPHY', level: 'NOVICE' }, { id: 'i', archetypeId: 'FITNESS_HYPERTROPHY', level: 'INTERMEDIATE' }],
    [{ id: 'c', title: 'Custom', customTemplates: [{ tag: 'chores.clean', minutes: 20, perWeek: 7 }, { tag: 'social.call', d: [30, 60, 90], f: [1, 2, 3], volumeDriver: true }],
      target: { units: 40, deadlineEpoch: NOW + 20 * 86400, unitsPerHour: 2 } }],
    [{ id: 'c', customTemplates: [{ tag: 'x.y', minutes: 30, perWeek: 3, spacingMinHNovice: 99 }], level: 'NOVICE' }]];
  GOAL_SETS.forEach((gs, i) => { for (const int of ['BALANCED', 'AGGRESSIVE']) add('expand', 'LDD-8', 'expandGoalActivities', [gs, { intensityLevel: int }, NOW], 'set' + i + '@' + int); });
  add('expand', 'LDD-8', 'expandGoalActivities', ['x', {}, NOW], 'goals-text');
  add('expand', 'LDD-8', 'expandGoalActivities', [[null], {}, NOW], 'goal-null');

  /* goalinfo: goalInfoMap's lag from elapsed and achieved fractions */
  const GI = [[0, 0], [0.5, 0], [0.5, 0.5], [0.25, 0.75], [1, 0], [2, 0], [-1, 0], [0.3, 0.1], [0.1, 0.3], [0.9, 0.95], [0.5, 1.2]];
  GI.forEach(([pe, pa], i) => {
    const created = NOW - 100 * 86400, deadline = created + 200 * 86400, now = created + pe * 200 * 86400;
    add('goalinfo', 'LDE-1', 'goalInfoMap', [[{ id: 'g' + i, title: 'L', priority: 2, createdEpoch: created, target: { units: 1000, completed: pa * 1000, deadlineEpoch: deadline } }], now],
      'elapsed' + pe + '-done' + pa);
  });
  const GI_ODD = [{ id: 'a' }, { id: 'a', target: { units: 10, deadlineEpoch: NOW + 1e6 } }, { id: 'a', createdEpoch: NOW, target: { units: 10, deadlineEpoch: NOW } },
    { id: 'a', createdEpoch: NOW - 10, target: { units: 0, deadlineEpoch: NOW + 10 } }, { id: 'a', createdEpoch: NOW - 1e5, targetMetricValue: 100, currentMetricValue: 70, deadlineEpoch: NOW + 1e5 },
    { id: 'a', createdEpoch: 'x', target: { units: 10, deadlineEpoch: NOW } }, { id: 'a', createdEpoch: '1790000000', target: { units: 10, deadlineEpoch: '1800000000', completed: '5' } },
    { id: 'a', createdEpoch: NOW - 1e5, target: { units: 10, deadlineEpoch: NOW + 1e5, completed: -5 } }, { id: 'a', priority: 9, title: 7 }];
  GI_ODD.forEach((g, i) => add('goalinfo', 'LDE-1', 'goalInfoMap', [[g], NOW], 'odd' + i + ':' + short(g)));
  add('goalinfo', 'LDE-1', 'goalInfoMap', [[{ id: 'a', priority: 1 }, { id: 'a', priority: 5 }, { goalId: 'b' }, {}], NOW], 'duplicates');
  add('goalinfo', 'LDE-1', 'goalInfoMap', [null, NOW], 'none');
  add('goalinfo', 'LDE-1', 'goalInfoMap', [[], NOW], 'empty');

  /* quest: normalizeQuest for each field's odd values, the legacy names, requirement fields */
  const QBASE = { id: 'q-1', title: 'Quest', tier: 'CORE', tag: 'fitness.strength', requiredCount: 3, deadlineEpoch: 1793600000 };
  const QFIELDS = ['id', 'questId', 'title', 'tier', 'questTier', 'status', 'deadlineEpoch', 'prerequisiteQuestIds', 'exclusive', 'requiresDedicatedExclusiveTime',
    'schedulingMode', 'recurrence', 'minSessionMinutes', 'sessionMinutes', 'minIntensity', 'appliedEventIds', 'bounty', 'source', 'onlyDays', 'windowStartEpoch',
    'hostRef', 'requirements', 'tag', 'activityTypeTag', 'requiredCount', 'targetCount', 'progressCount', 'currentCount', 'requiredMinutes', 'progressMinutes'];
  for (const f of QFIELDS) {
    ODD.forEach((v, i) => add('quest', 'LDQ-1', 'normalizeQuest', [Object.assign({}, QBASE, { [f]: v })], f + '=' + i + ':' + short(v)));
    const without = Object.assign({}, QBASE); delete without[f];
    add('quest', 'LDQ-1', 'normalizeQuest', [without], f + '-missing');
  }
  const RBASE = { requirementId: 'r-a', activityTypeTag: 'study.deep', targetCount: 2, currentCount: 1, targetMinutes: 60, currentMinutes: 15 };
  for (const f of ['requirementId', 'id', 'activityTypeTag', 'tag', 'targetCount', 'currentCount', 'targetMinutes', 'currentMinutes', 'minSessionMinutes', 'sessionMinutes'])
    ODD.forEach((v, i) => add('quest', 'LDQ-1 LDQ-2', 'normalizeQuest', [{ id: 'q', minSessionMinutes: 20, sessionMinutes: 40, requirements: [Object.assign({}, RBASE, { [f]: v })] }],
      'req.' + f + '=' + i + ':' + short(v)));
  const QODD = [{ id: 'q', requirements: [null] }, { id: 'q', requirements: [5, 'x', true, [], {}] }, { id: 'q', requirements: [RBASE, RBASE, { id: 7 }] }, {},
    { questId: 'legacy', questTier: 'MAIN_STORY', activityTypeTag: 'reading', targetCount: 4, currentCount: 1, requiresDedicatedExclusiveTime: 1 },
    { id: 'b', tier: 'BOUNTY', questTier: 'MANDATORY' }, { id: 'b', tier: 'main_story' }, { id: 'p', prerequisiteQuestIds: [1, null, 'a', ['b']] }, { id: 0, questId: 0 },
    { id: 'm', requiredMinutes: 90, progressMinutes: 100 }, { id: 'n', requiredCount: -2, progressCount: -1 }];
  QODD.forEach((q, i) => add('quest', 'LDQ-1 LDQ-2', 'normalizeQuest', [q], 'odd' + i + ':' + short(q)));
  for (const v of [null, 5, 'x', true, []]) add('quest', 'LDQ-1', 'normalizeQuest', [v], 'whole:' + short(v));

  /* cycle: findQuestCycles on hand graphs and seeded ones */
  const G = (...edges) => edges.map(([id, pre]) => ({ id, prerequisiteQuestIds: pre }));
  const GRAPHS = { none: G(['a', []], ['b', ['a']]), self: G(['a', ['a']], ['b', []]), two: G(['a', ['b']], ['b', ['a']], ['c', ['a']]),
    three: G(['a', ['c']], ['b', ['a']], ['c', ['b']], ['d', ['c']]), diamond: G(['a', []], ['b', ['a']], ['c', ['a']], ['d', ['b', 'c']]),
    nested: G(['a', ['b']], ['b', ['c', 'a']], ['c', ['b']], ['d', ['e']], ['e', ['d', 'd']]), unknown: G(['a', ['zz']], ['b', ['a', 'zz']]),
    duplicate: G(['a', ['b']], ['a', []], ['b', ['a']]), duplicate2: G(['a', []], ['a', ['b']], ['b', ['a']]), order: G(['z', ['y']], ['y', ['z']], ['B', ['a']], ['a', ['B']], ['10', ['9']], ['9', ['10']]),
    chain: Array.from({ length: 60 }, (_, i) => ({ id: 'c' + i, prerequisiteQuestIds: i ? ['c' + (i - 1)] : ['c59'] })),
    unicode: G(['é', ['\u{1F600}']], ['\u{1F600}', ['é']], ['e', ['é']]), legacy: [{ questId: 'a', prerequisiteQuestIds: ['b'] }, { questId: 'b', prerequisiteQuestIds: ['a'] }],
    empty: [], numbers: [{ id: 1, prerequisiteQuestIds: [2] }, { id: 2, prerequisiteQuestIds: [1] }], twoloops: G(['a', ['a']], ['b', ['b']], ['c', ['a', 'b']]) };
  for (const [name, g] of Object.entries(GRAPHS)) add('cycle', 'LDQ-3', 'findQuestCycles', [g], name);
  for (let s = 1; s <= 220; s++) {
    const r = random(9000 + s), n = 1 + Math.floor(r() * 14), p = r() * 0.35, names = Array.from({ length: n }, (_, i) => 'q' + Math.floor(r() * 3) + String.fromCharCode(97 + i));
    add('cycle', 'LDQ-3', 'findQuestCycles', [names.map(id => ({ id, prerequisiteQuestIds: names.filter(() => r() < p).concat(r() < 0.1 ? ['ghost'] : []) }))], 'seed' + s);
  }

  /* ids: freshIds (determinism, collisions with used ids, the 200-skip limit) and weeksOf */
  const BASES = ['g-1:strength|2026-10-26|', 'q|q-1|r0|once|', 'qx|q|r|2026-10-27|', '', 'é|\u{1F600}|', 'g:k|2026-10-26|'];
  for (const base of BASES) for (const count of [0, 1, 2, 3, 7, 2.5, 0.2, -1, num(NaN), 20]) add('ids', 'LDC-9', 'freshIds', [null, base, count], short(base) + '#' + short(count));
  const S = engine.util.stableId, first = (base, n) => Array.from({ length: n }, (_, k) => 'i' + S('t', base + k).slice(1));
  for (const [skip, count] of [[1, 3], [3, 3], [5, 2], [199, 2], [200, 2], [201, 2], [250, 5], [10, 0]]) add('ids', 'LDC-9', 'freshIds', [first('b|', skip), 'b|', count], 'used-first' + skip + '#' + count);
  add('ids', 'LDC-9', 'freshIds', [first('b|', 6).filter((_, i) => i % 2), 'b|', 4], 'used-alternate');
  add('ids', 'LDC-9', 'freshIds', [first('b|', 3).map(x => 't' + x.slice(1)), 'b|', 3], 'used-t-prefix-ignored');
  add('ids', 'LDC-9', 'freshIds', [['i_x', 'other'], 'b|', 2], 'used-unrelated');
  for (const zone of ZONES) for (const days of [1, 6, 7, 8, 13, 14, 15, 21, 28]) add('ids', 'LDC-1', 'weeksOf', [monday(zone), days, zone], zone + '@' + days);
  for (const days of [3, 7, 10]) add('ids', 'LDC-1', 'weeksOf', [localInstant('America/New_York', 2026, 11, 1, 30), days, 'America/New_York'], 'clock-change@' + days);

  /* candidates: compileCandidates by hand, one behaviour at a time */
  const T0 = monday('UTC'), DAY = 86400;
  const ctx = o => Object.assign({ goals: [], prefs: {}, quests: [], start: T0, days: 7, tz: 'UTC', now: T0, history: [], kept: [], used: [] }, o);
  const CC = (tag, rule, o) => add('candidates', rule, 'compileCandidates', [ctx(o)], tag);
  const muscle = (lvl, extra) => Object.assign({ id: 'g-m', title: 'Muscle', archetypeId: 'FITNESS_HYPERTROPHY', level: lvl || 'INTERMEDIATE', priority: 4 }, extra || {});
  const spanish = { id: 'g-s', title: 'Spanish', archetypeId: 'SKILL_LANGUAGE', subject: 'spanish', priority: 3 };
  const reading = { id: 'g-r', title: 'Read', archetypeId: 'HABIT_READING', level: 'ADVANCED' };
  const study = { id: 'g-st', title: 'Study', archetypeId: 'STUDY_GENERAL', level: 'INTERMEDIATE', priority: 5 };
  CC('goals-only', 'LDC-2 LDC-3', { goals: [muscle(), spanish, reading] });
  for (const d of [1, 3, 6, 7, 8, 10, 14, 15, 28]) CC('horizon' + d, 'LDC-2', { goals: [muscle(), spanish], days: d });
  for (const lvl of LEVELS) for (const int of INTENSITIES) CC('tiers@' + lvl + '@' + int, 'LDC-3', { goals: [muscle(lvl)], prefs: { intensityLevel: int } });
  CC('now-midweek', 'LDC-2', { goals: [muscle(), study], now: T0 + 3.5 * DAY });
  CC('now-after-horizon', 'LDC-2', { goals: [muscle()], now: T0 + 9 * DAY });
  CC('history-counts', 'LDC-2', { goals: [muscle()], history: [{ startEpoch: T0 - DAY, templateId: 'g-m:strength' }, { startEpoch: T0 + 100, templateId: 'g-m:strength' },
    { startEpoch: T0 + 200, templateId: 'g-m:strength' }, { startEpoch: T0 + 300, templateId: 'g-m:mealprep' }, { startEpoch: T0 + 400 }, { startEpoch: T0 + 8 * DAY, templateId: 'g-m:assess' }], days: 14 });
  CC('history-more-than-stretch', 'LDC-2', { goals: [muscle()], history: Array.from({ length: 9 }, (_, i) => ({ startEpoch: T0 + i * 3600, templateId: 'g-m:strength' })) });
  CC('kept-counts', 'LDC-2', { goals: [muscle()], kept: [{ startEpoch: T0 + DAY, endEpoch: T0 + DAY + 3600, templateId: 'g-m:strength' }, { startEpoch: T0 + 8 * DAY, endEpoch: T0 + 8 * DAY + 60, templateId: 'g-m:strength' }], days: 14 });
  CC('used-ids', 'LDC-9', { goals: [muscle()], used: first('g-m:strength|2026-10-26|', 2) });
  const deadline = T0 + 6 * DAY;
  const Q = (id, o) => Object.assign({ id, title: 'Quest ' + id, tier: 'CORE', tag: 'fitness.strength', requiredCount: 2, deadlineEpoch: deadline }, o || {});
  CC('quest-alone', 'LDC-4 LDC-7', { quests: [Q('a')] });
  CC('quest-merged', 'LDC-4 LDC-6', { goals: [muscle()], quests: [Q('a')] });
  CC('quest-merged-union', 'LDC-6', { goals: [muscle('NOVICE')], quests: [Q('a', { requiredCount: 6 })] });
  for (const tier of ['MANDATORY', 'CORE', 'OPTIONAL', 'MAIN_STORY', 'SIDE', 'BOUNTY', 'weird']) CC('tier-' + tier, 'LDC-5', { goals: [muscle()], quests: [Q('a', { tier })] });
  for (const h of [1, 23, 24, 24.25, 24.5, 25, 26, 48]) for (const tier of ['CORE', 'OPTIONAL']) CC('deadline-in-' + h + 'h@' + tier, 'LDC-5', { quests: [Q('a', { deadlineEpoch: T0 + h * 3600, sessionMinutes: 30, tier })] });
  CC('deadline-passed', 'LDC-4', { quests: [Q('a', { deadlineEpoch: T0 })] });
  CC('deadline-beyond', 'LDC-4', { quests: [Q('a', { deadlineEpoch: T0 + 30 * DAY })] });
  CC('window-start', 'LDC-4', { quests: [Q('a', { windowStartEpoch: T0 + 2 * DAY })] });
  CC('window-empty', 'LDC-4', { quests: [Q('a', { windowStartEpoch: T0 + 7 * DAY })] });
  for (const st of ['PAUSED', 'COMPLETED', 'FAILED', 'active']) CC('status-' + st, 'LDC-4', { quests: [Q('a', { status: st })] });
  CC('passive', 'LDC-4', { quests: [Q('p', { schedulingMode: 'NONE', anchor: 'SLEEP' }), Q('p2', { schedulingMode: 'NONE' })] });
  CC('minutes-only', 'LDC-4', { quests: [Q('m', { requiredCount: 0, requiredMinutes: 100, progressMinutes: 10, tag: 'study.deep' })] });
  CC('count-and-minutes', 'LDC-4', { quests: [Q('m', { requiredCount: 2, requiredMinutes: 300, tag: 'study.deep' })] });
  CC('nothing-left', 'LDC-4', { quests: [Q('m', { requiredCount: 2, progressCount: 2 })] });
  CC('fractional-count', 'LDC-4 LDC-9', { quests: [Q('f', { requiredCount: 2.5 })] });
  for (const ses of [0, 10, 15, 20, 45, 60, 90]) CC('session' + ses, 'LDC-7', { quests: [Q('s', { sessionMinutes: ses, tag: 'admin.finance' })] });
  for (const ms of [0, 20, 30, 60, 75, 80]) CC('min-session' + ms, 'LDC-6 LDC-7', { goals: [muscle()], quests: [Q('s', { minSessionMinutes: ms })] });
  for (const mi of [0, 3, 4, 5]) CC('min-intensity' + mi, 'LDC-6', { goals: [muscle(), spanish], quests: [Q('i', { minIntensity: mi, tag: 'language.*' })] });
  CC('exclusive', 'LDC-6 LDC-7', { goals: [muscle()], quests: [Q('x', { exclusive: true, sessionMinutes: 45 })] });
  CC('exclusive-daily', 'LDC-8', { goals: [spanish], quests: [Q('x', { exclusive: true, recurrence: 'DAILY', tag: 'language.spanish.vocab', requiredCount: 1 })] });
  CC('assessment-exact', 'LDC-6', { goals: [muscle()], quests: [Q('as', { tag: 'fitness.assessment', requiredCount: 1 })] });
  CC('assessment-wildcard', 'LDC-6', { goals: [muscle()], quests: [Q('as', { tag: 'fitness.*', requiredCount: 4 })] });
  CC('checkpoint', 'LDC-6', { goals: [reading], quests: [Q('ck', { tag: 'reading', requiredCount: 3 }), Q('ck2', { tag: 'reading.checkpoint', requiredCount: 1 })] });
  CC('depth-ranking', 'LDC-6', { goals: [spanish, Object.assign({}, spanish, { id: 'g-s2', subject: 'french' })], quests: [Q('v', { tag: 'language.*', requiredCount: 3 }),
    Q('v2', { tag: 'language.spanish.vocab', requiredCount: 2 }), Q('v3', { tag: 'language', requiredCount: 2 })] });
  CC('ranking-in-window', 'LDC-6', { goals: [Object.assign(muscle('NOVICE'), { id: 'a' }), Object.assign(muscle('ADVANCED'), { id: 'b' })], days: 14,
    quests: [Q('w', { requiredCount: 2, deadlineEpoch: T0 + 5 * DAY })] });
  CC('ranking-ties', 'LDC-6', { goals: [Object.assign(muscle(), { id: 'b' }), Object.assign(muscle(), { id: 'a' })], quests: [Q('t', { requiredCount: 3 })] });
  for (const tag of ['language.spanish.vocab', 'language.*', 'fitness.strength', 'wellbeing', 'chores.dishes'])
    CC('daily:' + tag, 'LDC-8', { goals: [spanish, muscle()], quests: [Q('d', { recurrence: 'DAILY', tag, requiredCount: 1, deadlineEpoch: null })] });
  CC('daily-minutes', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'wellbeing', requiredCount: 0, requiredMinutes: 60, deadlineEpoch: null })] });
  CC('daily-progress-today', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'wellbeing', requiredCount: 3, progressCount: 2, deadlineEpoch: null })] });
  CC('daily-only-days', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'chores', onlyDays: [0, 3, 6, '1'], deadlineEpoch: null })] });
  CC('daily-midday', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'chores', requiredCount: 1, deadlineEpoch: null })], now: T0 + 15 * 3600 });
  CC('daily-optional', 'LDC-8 LDC-5', { quests: [Q('d', { recurrence: 'DAILY', tier: 'OPTIONAL', tag: 'chores', requiredCount: 1, deadlineEpoch: null })] });
  CC('daily-deadline', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'chores', requiredCount: 1, deadlineEpoch: T0 + 3 * DAY })] });
  CC('daily-kept', 'LDC-8', { quests: [Q('d', { recurrence: 'DAILY', tag: 'chores', requiredCount: 2, deadlineEpoch: null })],
    kept: [{ startEpoch: T0 + 3600, endEpoch: T0 + 5400, linkedQuestRequirementIds: [{ questId: 'd', reqId: 'r0' }] }, { startEpoch: T0 + DAY + 3600, endEpoch: T0 + DAY + 5400, linkedQuestRequirementIds: [{ questId: 'd', reqId: 'r0' }] }] });
  CC('kept-quest', 'LDC-4', { quests: [Q('k', { requiredCount: 3, requiredMinutes: 120 })], kept: [{ startEpoch: T0 + 3600, endEpoch: T0 + 3600 + 2700, linkedQuestRequirementIds: [{ questId: 'k', reqId: 'r0' }] },
    { startEpoch: T0 + 7200, endEpoch: T0 + 9000, linkedQuestRequirementIds: [{ questId: 'k', reqId: 'other' }] }] });
  CC('two-requirements', 'LDC-4', { goals: [study], quests: [Q('two', { requirements: [{ requirementId: 'a', activityTypeTag: 'study.deep', targetCount: 2 },
    { requirementId: 'b', activityTypeTag: 'study.review', targetCount: 1, targetMinutes: 60 }, { requirementId: 'c', activityTypeTag: 'social', targetMinutes: 50, minSessionMinutes: 25 }] })] });
  CC('two-quests-same-tag', 'LDC-6', { goals: [muscle()], quests: [Q('a', { requiredCount: 2 }), Q('b', { requiredCount: 2, deadlineEpoch: T0 + 3 * DAY })] });
  CC('prereq-chain', 'LDC-10', { goals: [spanish], quests: [Q('boss', { tier: 'MANDATORY', tag: 'language.spanish.assessment', requiredCount: 1, exclusive: true }),
    Q('next', { tag: 'language.spanish.grammar', requiredCount: 1, prerequisiteQuestIds: ['boss'] })] });
  CC('prereq-same-tag', 'LDC-10', { goals: [muscle('ADVANCED')], quests: [Q('A', { requiredCount: 2, deadlineEpoch: T0 + 5 * DAY }), Q('B', { requiredCount: 2, prerequisiteQuestIds: ['A'] })] });
  CC('prereq-done', 'LDC-10', { quests: [Q('A', { status: 'COMPLETED' }), Q('B', { prerequisiteQuestIds: ['A'] })] });
  CC('prereq-paused', 'LDC-10', { quests: [Q('A', { status: 'PAUSED' }), Q('B', { prerequisiteQuestIds: ['A'] })] });
  CC('prereq-no-tasks', 'LDC-10', { quests: [Q('A', { deadlineEpoch: T0 - 10 }), Q('B', { prerequisiteQuestIds: ['A'] })] });
  CC('prereq-satisfied-no-tasks', 'LDC-10', { quests: [Q('A', { requiredCount: 1, progressCount: 1 }), Q('B', { prerequisiteQuestIds: ['A'] })] });
  CC('prereq-unknown', 'LDC-10', { quests: [Q('B', { prerequisiteQuestIds: ['ghost'] })] });
  CC('prereq-cycle', 'LDC-4', { quests: [Q('A', { prerequisiteQuestIds: ['B'] }), Q('B', { prerequisiteQuestIds: ['A'] }), Q('C', { prerequisiteQuestIds: ['A'] })] });
  CC('prereq-two', 'LDC-10', { quests: [Q('A', { tag: 'study.deep' }), Q('B', { tag: 'reading' }), Q('C', { prerequisiteQuestIds: ['A', 'B', 'A'] })] });
  CC('legacy-quest', 'LDC-4', { goals: [reading], quests: [{ questId: 'L', questTier: 'MAIN_STORY', activityTypeTag: 'reading.focused', targetCount: 2, currentCount: 1, deadlineEpoch: deadline }] });
  CC('unordered-quests', 'LDC-4', { goals: [muscle()], quests: [Q('z'), Q('a', { tier: 'OPTIONAL' }), Q('M', { tier: 'MANDATORY' })] });
  CC('custom-goal-quest', 'LDC-6', { goals: [{ id: 'g-c', title: 'Clean', customTemplates: [{ tag: 'chores.clean', minutes: 20, perWeek: 5 }] }], quests: [Q('c', { tag: 'chores', requiredCount: 3 })] });
  CC('zone-new-york', 'LDC-2 LDC-8', { tz: 'America/New_York', start: localInstant('America/New_York', 2026, 10, 30, 360), now: localInstant('America/New_York', 2026, 10, 30, 360), days: 7,
    goals: [muscle(), spanish], quests: [Q('d', { recurrence: 'DAILY', tag: 'language.spanish.vocab', requiredCount: 1, deadlineEpoch: null })] });
  CC('zone-kolkata', 'LDC-2', { tz: 'Asia/Kolkata', start: monday('Asia/Kolkata'), now: monday('Asia/Kolkata'), goals: [study], quests: [Q('e', { tag: 'study.deep', requiredCount: 5 })] });

  /* coverage: questCoverage of normalised quests against planned sessions */
  const COV = [[Q('a'), []], [Q('a'), [['a']]], [Q('a'), [['a'], ['a'], ['a']]], [Q('a', { recurrence: 'DAILY' }), []], [Q('a', { recurrence: 'DAILY' }), [['a']]],
    [Q('a', { schedulingMode: 'NONE' }), [['a']]], [Q('a', { status: 'PAUSED' }), [['a']]], [Q('a', { requiredCount: 0 }), []], [Q('a', { requiredCount: 2, progressCount: 5 }), []],
    [Q('a', { requirements: [{ targetCount: 2 }, { targetCount: 3, currentCount: 1 }, { targetMinutes: 60 }] }), [['a'], ['a'], ['a'], ['a']]], [Q('a'), [['b'], ['a', 'b']]]];
  COV.forEach(([q, planned], i) => {
    const pl = planned.map(qs => ({ linkedQuestRequirementIds: qs.map(questId => ({ questId, reqId: 'r0' })) }));
    add('coverage', 'LDQ-4', 'questCoverage', [[q], pl, {}], 'case' + i);
    add('coverage', 'LDQ-4', 'questCoverage', [[q], pl, { a: 'SCHEDULING' }], 'case' + i + '@status');
  });
  add('coverage', 'LDQ-4', 'questCoverage', [[Q('b'), Q('a'), Q('c', { status: 'DONE' })], [], { a: 'PASSIVE', b: '' }], 'several');
  add('coverage', 'LDQ-4', 'questCoverage', [[], [], {}], 'none');

  /* prep: prepTask's per-task constants */
  const H0 = T0, HN = T0 + 7 * DAY;
  const P = (tag, task, gi, o) => add('prep', 'LDE-2', 'prepTask', [o && o.H0 !== undefined ? o.H0 : H0, o && o.HN !== undefined ? o.HN : HN, o && o.now !== undefined ? o.now : T0 + 3600, task, gi || {}], tag);
  const TASK = { dOpt: 45, dMin: 30, priority: 3, questWeight: 0, goalId: null, deadlineEpoch: null, IP: 2, IC: 2 };
  for (const [o, d] of [[45, 30], [60, 45], [15, 15], [20, 10], [30, 60], [46, 44], [0, 0], [1, 1], [100, 15], [num(NaN), 30]]) P('slots' + short(o) + '/' + short(d), Object.assign({}, TASK, { dOpt: o, dMin: d }));
  for (const pr of [1, 2, 3, 4, 5, 0, null, 6]) for (const qw of [0, 2, 4, 5, null]) P('prio' + short(pr) + '/' + short(qw), Object.assign({}, TASK, { priority: pr, questWeight: qw }));
  for (const [ip, ic] of [[1, 1], [3, 3], [4, 1], [1, 4], [5, 5], ['4', 0], [3.99, 3.99]]) P('high' + short(ip) + '/' + short(ic), Object.assign({}, TASK, { IP: ip, IC: ic }));
  P('goal-lag', Object.assign({}, TASK, { goalId: 'g' }), { g: { lag: 0.8, priority: 3, title: 'x' } });
  P('goal-missing', Object.assign({}, TASK, { goalId: 'h' }), { g: { lag: 0.8 } });
  P('goal-lag-zero', Object.assign({}, TASK, { goalId: 'g' }), { g: { lag: 0 } });
  for (let k = 0; k <= 60; k++) {
    const dl = T0 + 3600 + 45 * 60 + Math.round((k - 6) * 0.05 * 7 * DAY);
    P('urgency' + k, Object.assign({}, TASK, { deadlineEpoch: dl }));
  }
  for (const h of [1, 2, 7, 14, 28]) for (const k of [0.1, 0.5, 1, 2, 3.7]) P('urgency-h' + h + '@' + k, Object.assign({}, TASK, { deadlineEpoch: T0 + 3600 + 2700 + k * h * DAY }), {}, { HN: H0 + h * DAY });
  const rp = random(4242);
  for (let k = 0; k < 220; k++) {
    const days = 1 + Math.floor(rp() * 28), now = H0 + Math.floor(rp() * days * 96) * 900, dOpt = 15 * (1 + Math.floor(rp() * 8));
    const dl = rp() < 0.15 ? null : now + Math.floor((rp() * 1.3 - 0.1) * days * DAY);
    P('seed' + k, { dOpt, dMin: 15 * (1 + Math.floor(rp() * 4)), priority: 1 + Math.floor(rp() * 5), questWeight: [0, 2, 4, 5][Math.floor(rp() * 4)],
      goalId: rp() < 0.5 ? 'g' : null, deadlineEpoch: dl, IP: 1 + Math.floor(rp() * 5), IC: 1 + Math.floor(rp() * 5) }, { g: { lag: Math.round(rp() * 100) / 100 } }, { HN: H0 + days * DAY, now });
  }

  /* drain: recomputeBwc on seeded grids (drain from shifts and tasks, relief from recovery and restorative blocks, free runs after now) */
  const rd = random(777);
  const pick = (a) => a[Math.floor(rd() * a.length)];
  for (let k = 0; k < 260; k++) {
    const days = 1 + Math.floor(rd() * 2), n = days * 96, blocks = Array.from({ length: 1 + Math.floor(rd() * 4) }, () => ({ restorative: rd() < 0.5 }));
    const startSlot = Math.floor(rd() * 40), endSlot = Math.min(n, startSlot + 20 + Math.floor(rd() * (n - startSlot - 20 + 1))), nowSlot = Math.floor(rd() * n);
    const slots = [];
    let i = 0;
    while (i < n) {
      const len = 1 + Math.floor(rd() * 10), code = pick([0, 0, 0, 2, 3, 4, 5, 6, 7, 9, 9, 1, 8]);
      for (let j = 0; j < len && i < n; j++, i++) slots.push([code, code === 2 ? pick([0, 1, 2, 3, 4]) : 0, [3, 5].includes(code) ? pick([-1, 0, blocks.length - 1]) : -1,
        [6, 9].includes(code) ? pick([0, 1, 2, 3, 4, 5]) : 0, [6, 9].includes(code) ? pick([0, 1, 2, 3, 4, 5]) : 0]);
    }
    add('drain', 'LDE-3', 'recomputeBwc', [{ n, nowSlot, startSlot, endSlot, blocks, slots }], 'seed' + k);
  }
  const plain = (n, code, extra) => Array.from({ length: n }, () => [code].concat(extra || [0, -1, 0, 0]));
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 0, endSlot: 96, blocks: [], slots: plain(96, 0) }], 'all-free');
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 96, startSlot: 0, endSlot: 96, blocks: [], slots: plain(96, 0) }], 'all-past');
  for (const load of [1, 2, 3, 4]) add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 10, endSlot: 90, blocks: [], slots: plain(96, 2, [load, -1, 0, 0]) }], 'shift-load' + load);
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 0, endSlot: 96, blocks: [], slots: plain(40, 9, [0, -1, 5, 5]).concat(plain(56, 7)) }], 'tasks-then-recovery');
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 0, endSlot: 96, blocks: [{ restorative: true }], slots: plain(20, 9, [0, -1, 4, 4]).concat(plain(76, 5, [0, 0, 0, 0])) }], 'restorative-maint');
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 0, endSlot: 96, blocks: [], slots: plain(96, 2, [5, -1, 0, 0]) }], 'load-5-throws');
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 0, endSlot: 96, blocks: [], slots: plain(96, 3, [0, 4, 0, 0]) }], 'tag-past-blocks-throws');
  add('drain', 'LDE-3', 'recomputeBwc', [{ n: 96, nowSlot: 0, startSlot: 50, endSlot: 40, blocks: [], slots: plain(96, 0) }], 'empty-cycle');

  /* float: the engine's exp and pow, over the arguments its formulas give */
  const fr = random(31337);
  for (let k = 0; k <= 400; k++) add('float', 'LDF-1', 'exp', [-3 * (k / 100)], 'urgency' + k);
  for (let k = 0; k < 300; k++) add('float', 'LDF-1', 'exp', [-3 * fr() * 1.5], 'urgency-seed' + k);
  for (let k = 0; k <= 400; k++) add('float', 'LDF-2', 'pow', [0.5, (k * 900) / 21600], 'checkin' + k);
  for (let k = 0; k < 300; k++) add('float', 'LDF-2', 'pow', [0.5, fr() * 112], 'checkin-seed' + k);
  for (let k = 0; k < 400; k++) {
    const ideal = [24, 48, 72, 96, 168, 12, 33.6, 56][k % 8], near = Math.abs(fr() * 400 - 100) * (k % 3 === 0 ? 1 : 0.25 * Math.round(fr() * 16)) / (k % 3 === 0 ? 1 : 4);
    const sig = Math.max(4, ideal * 0.5);
    add('float', 'LDF-1', 'exp', [-((near - ideal) ** 2) / (2 * sig * sig)], 'spacing-near' + k);
    add('float', 'LDF-1', 'exp', [-((near - ideal) ** 2) / (2 * (sig * 2) ** 2)], 'spacing-far' + k);
    add('float', 'LDF-2', 'square', [near - ideal], 'square' + k);
  }
  for (let k = 0; k <= 200; k++) add('float', 'LDF-1', 'exp', [-((24 + k * 0.25 * (1 + (k % 7))) - 24) / 72], 'movement' + k);
  for (const x of [0, num(-0), 1, -1, 709.78, 709.79, -745.13, -745.14, -708.4, 1e-300, -1e-300, num(Infinity), num(-Infinity), num(NaN), 0.5, -0.5, 2.302585092994046, 88.72, -103.97])
    add('float', 'LDF-1', 'exp', [x], 'edge:' + short(x));
  for (const [b, e] of [[0.5, 0], [0.5, num(-0)], [0.5, 1], [0.5, 1074], [0.5, 1075], [0.5, -1], [0.5, num(Infinity)], [0.5, num(NaN)], [2, 0.5], [0, 0], [num(-0), -1], [-8, 1 / 3], [10, -2], [1, num(Infinity)], [-1, num(-Infinity)], [1, num(NaN)], [num(NaN), 0], [2, 1024], [2, -1075]])
    add('float', 'LDF-2', 'pow', [b, e], 'edge:' + short(b) + '^' + short(e));
  for (let k = 0; k < 400; k++) { const x = (fr() - 0.5) * Math.pow(2, Math.floor(fr() * 20) - 8); add('float', 'LDF-1', 'exp', [x], 'random' + k); }

  /* scenario: the ten built-in scenarios' demand, in every strict zone of the matrix */
  for (const name of SCENARIO_NAMES) for (const zone of ZONES) {
    const s = engine.buildScenario(name, 1), inp = JSON.parse(JSON.stringify(s.input)), t = monday(zone), shift = t - inp.currentTimeEpoch;
    const move = v => (typeof v === 'number' ? v + shift : v);
    inp.timezone = zone; inp.playerAvailability.timezone = zone; inp.currentTimeEpoch = t; inp.horizonStartEpoch = t;
    for (const g of inp.goals) { if (g.createdEpoch) g.createdEpoch = move(g.createdEpoch); if (g.target && g.target.deadlineEpoch) g.target.deadlineEpoch = move(g.target.deadlineEpoch); }
    for (const q of inp.activeQuests) if (q.deadlineEpoch) q.deadlineEpoch = move(q.deadlineEpoch);
    add('scenario', 'LDC-2 LDC-3 LDC-4 LDC-6 LDC-8 LDC-10 LDE-1 LDE-2 LDE-4', 'demand', [inp, [], []], name + '@' + zone);
  }

  /* fuzz: seeded demand inputs */
  for (let s = 1; s <= 400; s++) add('fuzz', 'LDC-2 LDC-4 LDC-6 LDC-8 LDC-10 LDE-2 LDE-4', 'demand', fuzzDemand(s, engine), 'seed' + s);
  return cases;
}

/* One seeded demand input: goals of every archetype and custom ones, quests of every kind, history, kept sessions and used ids. */
function fuzzDemand(seed, engine) {
  const r = random(seed * 7919 + 13), pick = a => a[Math.floor(r() * a.length)], chance = p => r() < p;
  const zone = pick(ZONES), days = pick([1, 2, 3, 5, 7, 7, 7, 8, 10, 14, 21, 28]);
  const start = localInstant(zone, 2026, pick([3, 10, 11, 12]), 1 + Math.floor(r() * 28), Math.floor(r() * 96) * 15);
  const now = start + (chance(0.3) ? Math.floor(r() * days * 48) * 900 : 0);
  const goals = [], nGoals = Math.floor(r() * 6);
  for (let i = 0; i < nGoals; i++) {
    const g = { id: 'g-' + seed + '-' + i, title: pick(['Gym', 'Spanish', 'Books', 'Money', 'Run', 'Exam', 'Guitar', 'Calm', 'Custom thing']), priority: pick([1, 2, 3, 4, 5, 3]),
      level: pick(LEVELS), status: pick(['ACTIVE', 'ACTIVE', 'ACTIVE', 'PAUSED']), createdEpoch: now - Math.floor(r() * 90) * 86400 };
    if (chance(0.8)) g.archetypeId = pick(ARCH_IDS);
    else if (chance(0.7)) g.customTemplates = [{ title: g.title, tag: pick(TAGS.filter(t => t && !t.includes('*'))), minutes: pick([15, 20, 30, 45, 60, 90]), perWeek: pick([1, 2, 3, 5, 7, 10]),
      IP: pick([1, 2, 3, 4, 5]), IC: pick([1, 2, 3, 4, 5]), volumeDriver: chance(0.3) }];
    if (g.archetypeId === 'SKILL_LANGUAGE' && chance(0.7)) g.subject = pick(['spanish', 'french', 'japanese']);
    if (chance(0.25)) g.disabledTemplates = [pick(['strength', 'mealprep', 'assess', 'vocab', 'check', 'checkpoint', 'review'])];
    if (chance(0.35)) g.target = { units: pick([100, 600, 3600, 50]), completed: Math.floor(r() * 400), unit: 'u', unitsPerHour: pick([1, 5, 10, 30, 60]),
      deadlineEpoch: now + Math.floor((r() * 120 - 5) * 86400) };
    goals.push(g);
  }
  const quests = [], nQuests = Math.floor(r() * 6), qids = Array.from({ length: nQuests }, (_, i) => 'q-' + i);
  for (let i = 0; i < nQuests; i++) {
    const q = { id: qids[i], title: 'Quest ' + i, tier: pick(['MANDATORY', 'CORE', 'CORE', 'OPTIONAL', 'MAIN_STORY', 'SIDE', 'BOUNTY']),
      tag: pick(['fitness.strength', 'fitness.*', 'fitness', 'language.spanish.vocab', 'language.*', 'reading', 'reading.checkpoint', 'study.deep', 'study.*', 'admin.finance',
        'creative.practice', 'wellbeing', 'chores', 'social', 'fitness.assessment', 'language.spanish.assessment']), requiredCount: pick([0, 1, 1, 2, 3, 5]) };
    if (chance(0.25)) { q.requiredMinutes = pick([30, 60, 90, 120]); if (chance(0.3)) q.progressMinutes = pick([10, 30]); }
    if (chance(0.3)) q.progressCount = pick([0, 1, 2]);
    if (chance(0.3)) { q.recurrence = 'DAILY'; if (chance(0.3)) q.onlyDays = [pick([0, 1, 2, 3, 4, 5, 6]), pick([0, 1, 2, 3, 4, 5, 6])]; }
    if (q.recurrence !== 'DAILY' || chance(0.2)) q.deadlineEpoch = chance(0.1) ? now - 3600 : now + Math.floor(r() * (days + 2) * 86400);
    if (chance(0.15)) q.exclusive = true;
    if (chance(0.2)) q.sessionMinutes = pick([15, 30, 45, 60, 20]);
    if (chance(0.15)) q.minSessionMinutes = pick([20, 30, 45, 60]);
    if (chance(0.15)) q.minIntensity = pick([2, 3, 4]);
    if (chance(0.1)) q.schedulingMode = 'NONE';
    if (chance(0.1)) q.status = pick(['PAUSED', 'COMPLETED']);
    if (chance(0.1)) q.windowStartEpoch = now + Math.floor(r() * days * 86400);
    if (chance(0.25) && i > 0) q.prerequisiteQuestIds = [pick(qids.slice(0, i))].concat(chance(0.15) ? [pick(qids)] : []);
    if (chance(0.1)) q.requirements = [{ requirementId: 'a', activityTypeTag: q.tag, targetCount: 1 + Math.floor(r() * 3) }, { requirementId: 'b', activityTypeTag: 'wellbeing', targetMinutes: 30 }];
    quests.push(q);
  }
  const templateIds = [].concat(...goals.map(g => (g.archetypeId ? engine.ARCHETYPES[g.archetypeId].templates.map(t => g.id + ':' + t.key) : [g.id + ':x'])));
  const history = [], kept = [], used = [];
  for (let i = 0, n = Math.floor(r() * 5); i < n && templateIds.length; i++) history.push({ startEpoch: start - Math.floor(r() * 10 * 86400) + Math.floor(r() * days * 86400), templateId: pick(templateIds), spacingKey: 'k' });
  for (let i = 0, n = Math.floor(r() * 3); i < n; i++) {
    const st = start + Math.floor(r() * days * 96) * 900, k = { startEpoch: st, endEpoch: st + pick([15, 30, 45, 60]) * 60 };
    if (templateIds.length && chance(0.6)) k.templateId = pick(templateIds);
    if (quests.length && chance(0.5)) k.linkedQuestRequirementIds = [{ questId: pick(qids), reqId: 'r0' }];
    kept.push(k);
  }
  if (chance(0.3) && templateIds.length) {
    const loc = new Date((start + offsetAt(zone, start) * 60) * 1000);
    const wk = loc.getUTCFullYear() + '-' + String(loc.getUTCMonth() + 1).padStart(2, '0') + '-' + String(loc.getUTCDate()).padStart(2, '0');
    used.push('i' + engine.util.stableId('t', pick(templateIds) + '|' + wk + '|0').slice(1));
  }
  const inp = { timezone: zone, currentTimeEpoch: now, horizonStartEpoch: start, horizonDays: days,
    playerAvailability: { timezone: zone, sleepRequirementMinutes: 480, rules: [{ id: 'sleep', kind: 'SLEEP', startMin: 1380, durationMin: 480 }] },
    goals, activeQuests: quests, completionHistory: history, playerPreferences: { intensityLevel: pick(INTENSITIES) } };
  return [inp, kept, used];
}

module.exports = { defineCases, FAMILIES, ZONES, SCENARIO_NAMES };
