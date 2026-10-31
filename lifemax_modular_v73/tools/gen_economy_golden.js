/* Golden cases for the economy, recorded from the real page: every rule in docs/specs/ECONOMY_SPEC.md driven through the page's
   own functions on a fixed clock, so a fresh implementation (the logic-phase trial's Kotlin) can be accepted only when it gives
   the same output for every case. Regenerate when the page's economy changes on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_economy_golden.js [built-page.html]
   Output: tests/fixtures/economy-golden.json, one case per line. tests/economy-golden.test.js records the cases again in memory
   and fails when the page and the file disagree, so the file cannot go stale.

   A case is {id, rule, input, output}:
     rule    the spec rule IDs it exercises, separated by spaces (AWARD-1 D05)
     input   {now, zone, state, calls}. now is the clock (ISO, frozen while the case runs) and zone the device time zone.
             state holds every save field the economy reads (FIELDS below) whose value differs from the fresh save; a field
             not listed is as the fresh save has it. The fresh save is the input state of the first case, fresh/save: a new
             save (freshState(), SAVE-5) with no quests and xpSchemaV2 true, drawn once at T0 in UTC (drawing fills in the
             money account, its paycheck settings dated that day, the meal settings and the food log; the paycheck settings
             are outside the economy, but a case on another day shows its own). The state is set up as load()
             leaves it: the stored fields, rules = defaults merged with customRules (RULES-1), and attrXP and skillXP rebuilt
             from the ledger when it is not empty (DERIVE-1, DERIVE-2); the state recorded is the state after that. A case
             marked stale: true skips that rebuild, so its attrXP and skillXP are as given (the rebuild cases).
             calls are run in order: {fn, args, at?, env?}. fn is a name in the page's global scope (a constant is returned as
             it is); five adaptors take an id in place of an object: questLocked, questKey, isQuestDone and ecoQuestXP take a
             quest id (looked up in state.quests), actStatus a catalog id; skillLevel(name) is levelData(skillXP[name]) and
             levelData(total, 'charXpForLevel') uses the character curve. at moves the clock for that call. env is what the
             call sees around it: failSave 'storage' (the browser refuses the write) or 'readonly' (the save is read-only),
             health (what the Health Connect plugin returns: status, the exercise, steps and sleep records and the weight
             reading, times in epoch ms, and fail, the name of a read that throws; null for no plugin), confirm false (the
             owner says no), prompt (what the owner types), dom (values of the page's input fields, by element id), stored
             (the save in storage before load()).
     output  {returns, state, deleted?, toasts?}: each call's return value (null for none; {threw} if it threw), every
             field of FIELDS (plus the case's extra fields) the calls changed, with its value after, the fields they removed,
             and the messages shown, in order.
   Pure cases (functions of their arguments) have input {calls} and output {returns}.

   What is held still so a case means one thing: the clock is Playwright's, paused; crypto.randomUUID counts per case
   (00000000-0000-4000-8000-000000000001, ...); Math.random restarts its sequence at every call; toasts, confirm and prompt are
   captured. The Daily Hand is switched off (its auto-verify inside save() and its drawing of cards), because it is outside
   the economy (spec 1.2) and pays only through toggleQuest, which has its own cases; a case marked hand: true has its cards
   (the reroll price cases), and one marked verify: true also has the auto-verify inside save() (SAVE-2).
   health.snap (the sync's display data) is left out. */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'economy-golden.json');
const T0 = '2026-10-28T12:00:00.000Z', TODAY = '2026-10-28'; // a Wednesday, midday
const NY = 'America/New_York'; // daylight saving ends there on Sunday 2026-11-01
const BOOT = '2026-10-28T06:00:00.000Z';
/* the save fields this area reads or writes (spec 2.3), and fields only some cases look at */
const FIELDS = ['activities', 'attrXP', 'skillXP', 'restedXP', 'sparks', 'perk', 'perksOwned', 'attrStars', 'ascended', 'decayTotal',
  'decayApplied', 'decayLog', 'decay', 'legacyCharXPBonus', 'xpSchemaV2', 'customRules', 'quests', 'questDone', 'restDays', 'restTokens',
  'restBoughtAt', 'streakShields', 'shieldTick', 'shieldMiss', 'shieldDays', 'shieldMilestone', 'shieldBoughtAt', 'themesOwned', 'theme',
  'toysBought', 'nutriDone', 'careDay', 'mealsMeta', 'foodLog', 'money', 'actXP', 'plan', 'health', 'devMode'];
const EXTRA = ['journalCoach', 'dayReflections', 'dailyDraw'];

/* Spec rules no case records, and why. tests/economy-golden.test.js requires every other rule ID in the spec to have a case. */
const NOT_RECORDED = {
  'NUM-5': 'a property of every case (no XP value is ever fractional), not a behaviour of its own',
  'DERIVE-4': 'says which in-memory edits need not be reproduced; the save replaces them (every save case shows the rebuilt values)',
  'HEALTH-7': 'when a sync runs (timers, resume) is the app shell, not a rule with an output',
  'HEALTH-8': 'the settings text is presentation (D12 compares it with the rules)',
  'DECAY-6': 'when decay runs (boot, page shown) is the app shell; applyDecay itself has cases',
  'SAVE-6': 'boot order is the app shell; load() and applyDecay() have cases',
  D02: 'about a trigger nothing calls; ecoNutritionCheck itself has cases',
  D12: 'settings text',
  D15: 'in-memory edits the save overwrites',
  D19: 'a source comment',
  D25: 'moot while D02 stands',
  D26: 'paychecks are outside the economy',
  D27: 'about how the messages are written; the messages themselves are in the cases',
  D29: 'boot order',
  D30: 'a display',
  D31: 'needs a toy id the shop never offers, and NaN cannot be written in JSON',
  D32: 'unreachable code',
};

/* ---------- building cases ---------- */
const CASES = [];
let seq = 0;
function add(id, rule, spec) {
  if (CASES.some(c => c.id === id)) throw new Error('two cases are called ' + id);
  CASES.push({ id, rule, zone: 'UTC', calls: [], ...spec });
  seq = 0;
}
const C = (fn, ...args) => ({ fn, args });
const at = (t, c) => ({ ...c, at: t });
const withEnv = (env, c) => ({ ...c, env });
const pure = (id, rule, fn, ...args) => add(id, rule, { pure: true, calls: [C(fn, ...args)] });
const scase = (id, rule, state, ...calls) => add(id, rule, { state, calls });
const ms = iso => Date.parse(iso);
const dayKey = n => new Date(ms(TODAY + 'T00:00:00Z') + n * 864e5).toISOString().slice(0, 10); // n days from TODAY
const dayN = n => Math.round(ms(TODAY + 'T00:00:00Z') / 864e5) + n; // dayNum (TIME-6) of n days from TODAY
const iso = (day, time = '09:00:00') => day + 'T' + time + '.000Z';
const hoursBefore = (h, from = T0) => new Date(ms(from) - h * 3600e3).toISOString();
/* default attributes of each rule (RULES-2), as the web form fills them */
const RULE_ATTRS = { reading: ['intelligence', 'discipline'], business: ['business', 'discipline'], strength: ['physique', 'discipline'],
  running: ['endurance', 'wellbeing'], language: ['intelligence', 'social'], recovery: ['wellbeing', 'endurance'],
  practical: ['adventure', 'intelligence'], task: ['discipline', 'business'], custom: ['adventure', ''] };
const TYPES = Object.keys(RULE_ATTRS);

/* a ledger record already in the save (Sparks already credited unless sp: false) */
function rec(type, xp, o = {}) {
  const primary = o.primary || 'discipline';
  const r = { id: o.id || 's' + (++seq), type, name: o.name || 'Seed ' + type, duration: o.dur ?? 30, skill: o.skill ?? 'Seed', primary,
    secondary: o.secondary ?? '', xp, date: o.date || iso(o.day || TODAY, o.time), xpSplit: o.split || [{ attr: primary, xp }] };
  for (const k of ['src', 'epic', 'cad', 'ldBonus', 'ldEventId', 'link', 'kind', 'actId', 'opId', 'quality', 'result', 'planDate'])
    if (o[k] !== undefined) r[k] = o[k];
  if (o.sp !== false) r.sp = 1;
  return r;
}
const qrec = (cad, xp, o = {}) => rec('quest', xp, { dur: 0, cad, epic: cad === 'epic', skill: 'Quest Completion', ...o });
const quest = (id, cadence, xp, o = {}) => ({ id, name: 'Quest ' + id, desc: '', cadence, xp, ...o });
/* a Log draft as the web form gives it after choosing the preset */
const draft = (type, dur, o = {}) => ({ type, customName: '', duration: String(dur), quality: '1', skill: '', primary: (RULE_ATTRS[type] || RULE_ATTRS.custom)[0],
  secondary: (RULE_ATTRS[type] || RULE_ATTRS.custom)[1], notes: '', ...o });
const perk = id => ({ perk: id, perksOwned: [id] });

/* ---------- the cases, by spec section ---------- */
function curves() {
  for (const L of [1, 2, 3, 10, 20, 100]) pure(`curve/need-L${L}`, 'CURVE-1', 'xpForLevel', L);
  for (const t of [0, 1, 99, 100, 101, 224, 225, 226, 450, 575, 2350, 6175]) pure(`curve/level-${t}`, 'CURVE-2', 'levelData', t);
  pure('curve/level-negative', 'CURVE-2', 'levelData', -5);
  pure('curve/level-string', 'CURVE-2 NUM-3', 'levelData', '250');
  pure('curve/level-ceiling', 'CURVE-2', 'levelData', 1e9);
  for (const L of [1, 2, 5, 10]) pure(`curve/char-need-L${L}`, 'CURVE-3', 'charXpForLevel', L);
  for (const L of [1, 2, 3, 5, 10, 20]) pure(`curve/char-cum-L${L}`, 'CURVE-4', 'charCumXP', L);
  for (const t of [0, 199, 200, 474, 475, 1250, 4500]) pure(`curve/char-level-${t}`, 'CURVE-3 CURVE-4', 'levelData', t, 'charXpForLevel');
  const ledger = () => [rec('reading', 300, { primary: 'intelligence', day: dayKey(-3) }), rec('business', 180, { primary: 'business', day: dayKey(-2) })];
  scase('curve/character-from-ledger', 'CURVE-5 DERIVE-3', { activities: ledger() }, C('characterLevel'), C('totalXP'));
  scase('curve/character-legacy-bonus', 'CURVE-5', { activities: ledger(), legacyCharXPBonus: 200 }, C('characterLevel'));
  scase('curve/character-ascended', 'CURVE-5 CURVE-6', { activities: [rec('strength', 6400, { primary: 'physique', day: dayKey(-5) })], ascended: { physique: 6175 }, attrStars: { physique: 1 } },
    C('characterLevel'), C('attrLevel', 'physique'), C('totalXP'));
  scase('curve/attribute-levels', 'CURVE-6', { activities: [rec('reading', 230, { primary: 'intelligence', secondary: 'discipline', day: dayKey(-1), split: [{ attr: 'intelligence', xp: 161 }, { attr: 'discipline', xp: 69 }] })] },
    C('attrLevel', 'intelligence'), C('attrLevel', 'discipline'), C('attrLevel', 'social'));
  scase('curve/skill-level', 'CURVE-7 DERIVE-2', { activities: [rec('reading', 130, { skill: 'Chess', day: dayKey(-2) }), rec('reading', 100, { skill: ' Chess ', day: dayKey(-1) })] },
    C('skillLevel', 'Chess'));
  for (const [t, n] of [[0, 1], [0, 2], [0, 3], [50, 1], [99, 1], [100, 2], [1000, 1], [-20, 1], ['abc', 2]]) pure(`curve/gain-levels-${t}-by-${n}`, 'CURVE-8', 'xpToGainLevels', t, n);
  for (const s of [0, 1, 2, 3, 4, 5, 6]) pure(`curve/stage-level-${s}`, 'CURVE-9', 'stageLvl', s);
  const staged = [quest('st3', 'daily', 20, { stage: 3 }), quest('st2', 'daily', 20, { stage: 2 }), quest('st1', 'daily', 20, { stage: 1 }), quest('st0', 'daily', 20)];
  const locks = ['st3', 'st2', 'st1', 'st0'].map(id => C('questLocked', id));
  scase('curve/stage-locked-at-level-1', 'CURVE-9', { quests: staged }, ...locks);
  scase('curve/stage-level-2-just-short', 'CURVE-9', { quests: staged, activities: [rec('reading', 474, { day: dayKey(-4) })] }, C('characterLevel'), ...locks);
  scase('curve/stage-level-3-opens-stage-2', 'CURVE-9', { quests: staged, activities: [rec('reading', 475, { day: dayKey(-4) })] }, C('characterLevel'), ...locks);
  scase('curve/stage-level-5-opens-stage-3', 'CURVE-9', { quests: staged, activities: [rec('reading', 1250, { day: dayKey(-4) })] }, C('characterLevel'), ...locks);
}

function time() {
  for (const k of ['1970-01-01', '2026-10-28', '2026-03-29', '2024-02-29']) pure(`time/day-number-${k}`, 'TIME-6', 'dayNum', k);
  for (const n of [0, dayN(0), 19782]) pure(`time/day-key-${n}`, 'TIME-6', 'ecoDayKey', n);
  scase('time/today-utc', 'TIME-1 TIME-3', {}, C('getToday'));
  add('time/today-new-york-evening', 'TIME-2 TIME-3', { zone: NY, now: '2026-10-28T03:00:00.000Z', calls: [C('getToday')] });
  add('time/record-day-new-york', 'TIME-2 TIME-4', { zone: NY, calls: [C('activityDate', { date: '2026-10-28T02:30:00.000Z' }), C('activityDate', { date: '2026-10-28T04:30:00.000Z' })] });
  scase('time/record-day-invalid', 'TIME-4', {}, C('activityDate', { date: 'not a date' }), C('activityDate', {}));
}

function scoring() {
  for (const d of [0, 4, 4.99, 5, 10, 15, 20, 29, 30, 31, 45, 59, 60, 61, 78, 90, 119, 120, 121, 240, -10]) pure(`dur/${d}-min`, 'DUR-1', 'ecoDur', d);
  for (const [name, d] of [['string', '30'], ['empty', ''], ['null', null], ['text', 'abc']]) pure(`dur/${name}`, 'DUR-1 NUM-3', 'ecoDur', d);
  pure('dur/in-words', 'DUR-2', 'ECO_DURATION_RULES');
  pure('rules/defaults', 'RULES-2', 'DEFAULT_RULES');
  pure('rules/constants', 'RULES-2', 'ECO');
  for (const t of TYPES) pure(`preview/${t}-30`, 'PREV-1', 'activityPreview', draft(t, 30));
  for (const q of ['0.8', '1', '1.1', '1.2']) for (const d of [4, 5, 15, 19, 20, 45, 60, 120, 150]) pure(`preview/reading-${d}-q${q}`, 'PREV-1', 'activityPreview', draft('reading', d, { quality: q }));
  pure('preview/unknown-type', 'PREV-1', 'activityPreview', draft('yoga', 30));
  pure('preview/attributes-given', 'PREV-1', 'activityPreview', draft('reading', 30, { primary: 'adventure', secondary: 'social' }));
  pure('preview/blank-attributes', 'PREV-1 D23', 'activityPreview', draft('reading', 30, { primary: '', secondary: '' }));
  pure('preview/blank-skill', 'PREV-1', 'activityPreview', draft('reading', 30, { skill: '   ' }));
  pure('preview/padded-skill', 'PREV-1', 'activityPreview', draft('reading', 30, { skill: '  Chess ' }));
  for (const [name, q] of [['empty', ''], ['zero', '0'], ['text', 'abc']]) pure(`preview/quality-${name}`, 'PREV-1 NUM-3', 'activityPreview', draft('reading', 30, { quality: q }));
  pure('preview/negative-duration', 'PREV-1', 'activityPreview', draft('reading', -5));
  /* section 19: where binary doubles and exact decimals disagree */
  pure('preview/business-78-min', 'PREV-1 NUM-1 NUM-2', 'activityPreview', draft('business', 78));
  for (const d of [61, 63, 67, 71, 77, 79, 93, 107, 109, 113, 115, 119]) pure(`preview/business-${d}-q1.2`, 'PREV-1 NUM-1 NUM-2', 'activityPreview', draft('business', d, { quality: '1.2' }));
  /* the native Log's draft clean-up (LOG-1) */
  const clean = (id, d) => pure('log-clean/' + id, 'LOG-1', 'NativeLogProjection.clean', d);
  clean('as-given', draft('strength', 45, { quality: '1.1', notes: 'Push day' }));
  clean('unknown-type', draft('yoga', 30));
  clean('long-duration', draft('reading', 1500));
  clean('fractional-duration', draft('reading', 12.6));
  clean('negative-duration', draft('reading', -3));
  clean('odd-quality', draft('reading', 30, { quality: '1.3' }));
  clean('unknown-attributes', draft('reading', 30, { primary: 'chess', secondary: 'chess' }));
  clean('long-texts', draft('custom', 30, { customName: 'n'.repeat(100), skill: 's'.repeat(70), notes: 'x'.repeat(2100) }));
  clean('empty', {});
}

function capAndRested() {
  for (const t of [0, 100, 150, 170, 179, 180, 181, 200, 230, 239, 240, 260]) for (const x of [1, 2, 10, 30, 61, 100]) pure(`cap/t${t}-x${x}`, 'CAP-2', 'ecoCap', x, TODAY, t);
  pure('cap/zero', 'CAP-2', 'ecoCap', 0, TODAY, 0);
  pure('cap/negative', 'CAP-2', 'ecoCap', -5, TODAY, 0);
  scase('cap/day-total-sources', 'CAP-1', { activities: [rec('steps', 15, { src: 'health', skill: 'Walking', dur: 0 }), rec('coach', 30, { dur: 0 }), rec('plan', 40, { dur: 0 }), rec('catalog', 35),
    qrec('daily', 20), qrec('epic', 75), rec('task', 45, { ldBonus: 5 }), rec('reading', 24, { day: dayKey(-1) })] }, C('ecoDayXP', TODAY), C('ecoDayXP', dayKey(-1)), C('ecoCap', 30, TODAY));
  scase('cap/day-total-negative-and-text', 'CAP-1 D33 NUM-3', { activities: [rec('business', -50), rec('business', 200), rec('business', '12')] }, C('ecoDayXP', TODAY), C('ecoCap', 30, TODAY));
  for (const pool of [0, -10, 5, 12, 100]) for (const x of [0, 1, 25, 51]) {
    const st = pool ? { restedXP: pool } : {};
    scase(`rested/peek-pool${pool}-x${x}`, 'REST-1', st, C('ecoRestedPeek', x));
    scase(`rested/take-pool${pool}-x${x}`, 'REST-2', st, C('ecoRestedTake', x));
  }
  const hazard115 = [50, 90, 110, 170, 190, 210, 330, 350, 370, 390, 410, 430]; // round(x * 1.15) differs from exact decimals (section 19)
  for (const stars of [0, 1, 2, 3]) for (const xp of [1, 10, 20, 45, 50, 90, 110])
    scase(`stars/${stars}-on-${xp}`, 'STAR-1' + (stars === 3 && hazard115.includes(xp) ? ' NUM-1' : ''), stars ? { attrStars: { physique: stars } } : {}, C('ecoStarXP', 'physique', xp));
  for (const xp of hazard115.slice(3)) scase(`stars/3-on-${xp}`, 'STAR-1 NUM-1', { attrStars: { physique: 3 } }, C('ecoStarXP', 'physique', xp));
  scase('stars/other-attribute', 'STAR-1', { attrStars: { physique: 2 } }, C('ecoStarXP', 'endurance', 50));
}

function award() {
  const R = rec, A = (id, rule, state, ...args) => scase('award/' + id, rule, state, C('ecoAward', ...args));
  const m = { manual: true };
  A('empty-day', 'AWARD-1', {}, 'reading', 24, 30, m);
  A('zero-total', 'AWARD-1', {}, 'reading', 0, 3, m);
  A('negative-total', 'AWARD-1', {}, 'reading', -4, 30, {});
  A('text-total', 'AWARD-1 NUM-4', {}, 'reading', 'abc', 30, {});
  A('no-options', 'AWARD-1', {}, 'running', 26, 30);
  A('second-of-type', 'AWARD-1', { activities: [R('reading', 24)] }, 'reading', 24, 30, m);
  A('third-of-type-half', 'AWARD-1', { activities: [R('reading', 24), R('reading', 24)] }, 'reading', 25, 30, m);
  A('third-of-type-at-least-1', 'AWARD-1', { activities: [R('reading', 24), R('reading', 24)] }, 'reading', 1, 5, {});
  A('fourth-of-type', 'AWARD-1', { activities: [R('reading', 24), R('reading', 24), R('reading', 12)] }, 'reading', 24, 30, m);
  A('fourth-counts-zero-xp-records', 'AWARD-1 D18', { activities: [R('reading', 0), R('reading', 0), R('reading', 0)] }, 'reading', 24, 30, m);
  A('per-type-counts-every-source', 'AWARD-1', { activities: [R('running', 20, { src: 'health' }), R('running', 20, { src: 'voice' }), R('running', 0, { ldEventId: 'e0' })] }, 'running', 26, 30, {});
  A('per-type-yesterday-ignored', 'AWARD-1', { activities: [R('reading', 24, { day: dayKey(-1) }), R('reading', 24, { day: dayKey(-1) }), R('reading', 24, { day: dayKey(-1) })] }, 'reading', 24, 30, m);
  A('per-type-other-types-ignored', 'AWARD-1', { activities: [R('business', 25), R('business', 25), R('business', 25)] }, 'reading', 24, 30, m);
  A('time-limit-exactly-360', 'AWARD-1', { activities: [R('business', 50, { dur: 330 })] }, 'reading', 24, 30, m);
  A('time-limit-over', 'AWARD-1', { activities: [R('business', 50, { dur: 331 })] }, 'reading', 24, 30, m);
  A('time-limit-not-manual', 'AWARD-1', { activities: [R('business', 50, { dur: 331 })] }, 'reading', 24, 30, {});
  A('time-limit-skips-quest-and-health', 'AWARD-1 AWARD-5', { activities: [qrec('daily', 20, { dur: 300 }), R('running', 20, { src: 'health', dur: 300 }), R('business', 30, { dur: 60 })] }, 'reading', 24, 30, m);
  A('time-limit-counts-director-voice-catalog', 'AWARD-5', { activities: [R('task', 40, { ldEventId: 'e1', dur: 120 }), R('running', 30, { src: 'voice', dur: 120 }), R('catalog', 20, { dur: 100 })] }, 'reading', 24, 30, m);
  A('time-limit-text-durations', 'AWARD-1 NUM-3', { activities: [R('business', 20, { dur: '200' }), R('business', 20, { dur: '' })] }, 'reading', 24, 161, m);
  A('refusal-order-type-before-time', 'AWARD-2', { activities: [R('reading', 24, { dur: 120 }), R('reading', 24, { dur: 120 }), R('reading', 12, { dur: 120 })] }, 'reading', 24, 30, m);
  A('refusal-order-total-first', 'AWARD-2', { activities: [R('reading', 24, { dur: 120 }), R('reading', 24, { dur: 120 }), R('reading', 12, { dur: 120 })] }, 'reading', 0, 2, m);
  /* Scholar's Flow */
  A('scholar-45-min', 'AWARD-1 PERK-1', perk('scholar'), 'reading', 43, 45, m);
  A('scholar-44-min', 'AWARD-1 PERK-1', perk('scholar'), 'reading', 43, 44, m);
  A('scholar-language', 'AWARD-1 PERK-1', perk('scholar'), 'language', 48, 60, m);
  A('scholar-other-type', 'AWARD-1 PERK-1', perk('scholar'), 'business', 50, 60, m);
  A('scholar-owned-not-equipped', 'AWARD-1 PERK-1', { perksOwned: ['scholar'] }, 'reading', 43, 45, m);
  A('scholar-equipped-not-owned', 'AWARD-1 PERK-1', { perk: 'scholar' }, 'reading', 43, 45, m);
  A('scholar-too-small-to-add', 'AWARD-1 PERK-1', perk('scholar'), 'reading', 1, 45, {});
  A('scholar-after-half', 'AWARD-1 PERK-1', { ...perk('scholar'), activities: [R('reading', 24), R('reading', 24)] }, 'reading', 60, 45, m);
  /* rested XP */
  A('rested', 'AWARD-1 REST-1 REST-2 REST-3', { restedXP: 40 }, 'language', 24, 30, m);
  A('rested-peek', 'AWARD-1 AWARD-4 REST-1', { restedXP: 40 }, 'language', 24, 30, { manual: true, peek: true });
  A('rested-pool-smaller-than-bonus', 'AWARD-1 REST-2', { restedXP: 5 }, 'reading', 24, 30, m);
  A('rested-pool-equal-to-bonus', 'AWARD-1 REST-2', { restedXP: 12 }, 'reading', 24, 30, m);
  A('rested-odd-bonus-rounds-up', 'AWARD-1 REST-2 NUM-2', { restedXP: 100 }, 'reading', 25, 30, m);
  A('rested-negative-pool', 'AWARD-1 REST-2', { restedXP: -20 }, 'reading', 24, 30, m);
  A('rested-cap-straddled', 'AWARD-1 REST-3 D05', { restedXP: 100, activities: [R('business', 230)] }, 'reading', 24, 30, m);
  A('rested-cap-straddled-peek', 'AWARD-4 D05', { restedXP: 100, activities: [R('business', 230)] }, 'reading', 24, 30, { manual: true, peek: true });
  A('rested-cap-reached', 'AWARD-1 REST-3', { restedXP: 100, activities: [R('business', 240)] }, 'reading', 24, 30, m);
  A('scholar-rested-soft-cap', 'AWARD-1 PERK-1', { ...perk('scholar'), restedXP: 40, activities: [R('business', 150)] }, 'reading', 43, 45, m);
  /* the daily cap */
  A('soft-cap-straddled', 'AWARD-1 CAP-2', { activities: [R('business', 170)] }, 'reading', 24, 30, m);
  A('above-soft-cap', 'AWARD-1 CAP-2', { activities: [R('business', 200)] }, 'reading', 24, 30, m);
  A('soft-cap-exactly-reached', 'AWARD-1 CAP-2', { activities: [R('business', 156)] }, 'reading', 24, 30, m);
  A('halved-just-under-cap', 'AWARD-1 CAP-2', { activities: [R('business', 225)] }, 'reading', 24, 30, m);
  A('reaches-cap', 'AWARD-1 CAP-2', { activities: [R('business', 230)] }, 'reading', 24, 30, m);
  A('cap-exactly-240', 'AWARD-1 CAP-2', { activities: [R('business', 180)] }, 'reading', 120, 120, {});
  A('cap-239', 'AWARD-1 CAP-2', { activities: [R('business', 239)] }, 'reading', 24, 30, m);
  A('cap-full', 'AWARD-1 CAP-2', { activities: [R('business', 240)] }, 'reading', 24, 30, m);
  A('cap-over', 'AWARD-1 CAP-2', { activities: [R('business', 300)] }, 'reading', 24, 30, m);
  A('cap-epic-exempt', 'AWARD-1 CAP-1', { activities: [qrec('epic', 100), R('business', 150)] }, 'reading', 24, 30, m);
  A('cap-negative-record', 'CAP-1 D33', { activities: [R('business', -50), R('business', 200)] }, 'reading', 24, 30, m);
  A('cap-counts-every-source', 'CAP-1', { activities: [R('steps', 15, { src: 'health', skill: 'Walking', dur: 0 }), R('coach', 30, { dur: 0 }), R('plan', 40, { dur: 0 }), R('catalog', 35),
    qrec('daily', 20), R('task', 45, { ldBonus: 5 })] }, 'reading', 24, 30, m);
  /* another day */
  const y = dayKey(-1);
  A('other-day-limit', 'AWARD-3', { activities: [R('reading', 24, { day: y }), R('reading', 24, { day: y }), R('reading', 24, { day: y })] }, 'reading', 24, 30, { manual: true, day: y });
  A('other-day-cap', 'AWARD-3', { activities: [R('business', 240, { day: y })] }, 'reading', 24, 30, { day: y });
  A('other-day-time', 'AWARD-3', { activities: [R('business', 50, { dur: 340, day: y })] }, 'reading', 24, 30, { manual: true, day: y });
  A('other-day-rested', 'AWARD-3', { restedXP: 30, activities: [R('business', 240)] }, 'reading', 24, 30, { day: y });
  scase('award/peek-then-commit', 'AWARD-4', { restedXP: 30, activities: [R('business', 170)] },
    C('ecoAward', 'strength', 42, 45, { manual: true, peek: true }), C('ecoAward', 'strength', 42, 45, { manual: true }));
  scase('award/peek-twice-changes-nothing', 'AWARD-4', { restedXP: 30 }, C('ecoAward', 'reading', 24, 30, { peek: true }), C('ecoAward', 'reading', 24, 30, { peek: true }));
}

function log() {
  const L = (id, rule, state, d, env) => scase('log/' + id, rule, state, env ? withEnv(env, C('recordActivity', d)) : C('recordActivity', d));
  for (const t of TYPES) L(`${t}-30`, 'LOG-2 LOGAWARD-1 SPLIT-1 SPARK-1', {}, draft(t, 30));
  L('strength-long-high-quality', 'LOG-2 LOGAWARD-1', {}, draft('strength', 150, { quality: '1.2' }));
  L('custom-named', 'LOG-2', {}, draft('custom', 45, { customName: '  Pottery ' }));
  L('custom-unnamed', 'LOG-2', {}, draft('custom', 45));
  L('unknown-type', 'LOG-2', {}, draft('yoga', 40));
  L('no-secondary', 'LOG-2 SPLIT-1 D23', {}, draft('reading', 30, { secondary: '' }));
  L('secondary-same-as-primary', 'LOGAWARD-1 SPLIT-1', {}, draft('reading', 30, { secondary: 'intelligence' }));
  L('primary-given', 'LOG-2', {}, draft('reading', 30, { primary: 'adventure' }));
  L('skill-given', 'LOG-2 DERIVE-2', {}, draft('reading', 30, { skill: '  Chess ' }));
  L('quality-empty', 'LOG-2 D24', {}, draft('reading', 30, { quality: '' }));
  L('quality-text', 'LOG-2 D24', {}, draft('reading', 30, { quality: 'abc' }));
  L('quality-ignored-under-20', 'LOG-2 PREV-1', {}, draft('reading', 15, { quality: '1.2' }));
  L('quality-lowers-under-20', 'LOG-2 PREV-1', {}, draft('reading', 15, { quality: '0.8' }));
  L('notes-trimmed', 'LOG-2', {}, draft('reading', 30, { notes: '  chapter 3  ' }));
  L('split-45', 'LOGAWARD-1 SPLIT-1 NUM-1 NUM-2', {}, draft('business', 54));
  L('business-78-min', 'LOG-2 NUM-1 NUM-2', {}, draft('business', 78));
  L('business-61-min-q1.2', 'LOG-2 NUM-1 NUM-2', {}, draft('business', 61, { quality: '1.2' }));
  L('star-on-primary', 'LOGAWARD-1 STAR-1', { attrStars: { intelligence: 1 } }, draft('reading', 60));
  L('stars-on-both', 'LOGAWARD-1 STAR-1 NUM-1', { attrStars: { intelligence: 3, discipline: 2 } }, draft('reading', 90));
  L('star-too-small-to-add', 'LOGAWARD-1 STAR-1', { attrStars: { discipline: 1 } }, draft('reading', 5));
  L('star-on-unknown-attribute', 'LOGAWARD-1', { attrStars: { chess: 2 } }, draft('reading', 60, { primary: 'chess' }));
  L('stars-worked-example', 'LOGAWARD-1 STAR-1 PERK-1', { ...perk('scholar'), restedXP: 40, attrStars: { intelligence: 1 }, activities: [rec('business', 150)] }, draft('reading', 45, { quality: '1.2' }));
  L('stars-after-cap', 'LOGAWARD-1 D06', { attrStars: { physique: 3, discipline: 3 }, activities: [rec('business', 200)] }, draft('strength', 120));
  L('too-short', 'LOGAWARD-1 LOG-2', { restedXP: 20 }, draft('reading', 2));
  L('no-duration', 'LOGAWARD-1 LOG-2', {}, draft('reading', ''));
  L('fourth-of-type', 'LOG-2', { restedXP: 20, activities: [rec('reading', 24), rec('reading', 24), rec('reading', 12)] }, draft('reading', 30));
  L('active-time-used-up', 'LOG-2', { activities: [rec('business', 20, { dur: 340 })] }, draft('reading', 30));
  L('daily-cap', 'LOG-2 REST-3', { restedXP: 40, activities: [rec('business', 240)] }, draft('reading', 30));
  L('rested', 'LOG-2 REST-2', { restedXP: 40 }, draft('language', 30));
  L('operation-id-new', 'LOG-2', {}, draft('reading', 30, { opId: 'op-7' }));
  L('operation-id-replayed', 'LOG-2', { activities: [rec('reading', 24, { opId: 'op-7', primary: 'intelligence' })] }, draft('reading', 30, { opId: 'op-7' }));
  L('save-fails', 'LOG-2 SAVE-3 SAVE-4', { restedXP: 40 }, draft('reading', 30), { failSave: 'storage' });
  L('save-read-only', 'LOG-2 SAVE-3 SAVE-4', { restedXP: 40 }, draft('reading', 30), { failSave: 'readonly' });
  scase('log/save-fails-then-retry', 'LOG-2 SAVE-4', { restedXP: 40 }, withEnv({ failSave: 'storage' }, C('recordActivity', draft('reading', 30, { opId: 'op-r' }))),
    C('recordActivity', draft('reading', 30, { opId: 'op-r' })));
  add('log/four-in-a-row', 'LOG-2 AWARD-1', { calls: [0, 1, 2, 3].map(i => at(hoursBefore(-i / 60), C('recordActivity', draft('reading', 30)))) });
  const P = (id, state, d) => scase('log/preview-is-save-' + id, 'LOG-3', state, C('activityAward', d, true), C('recordActivity', d));
  P('rested', { restedXP: 40 }, draft('language', 30));
  P('soft-cap', { activities: [rec('business', 170)] }, draft('reading', 60));
  P('stars', { attrStars: { physique: 2 } }, draft('strength', 60));
  P('refused', { activities: [rec('business', 240)] }, draft('reading', 30));
  scase('log/award-without-peek-spends', 'LOGAWARD-1 REST-2', { restedXP: 40 }, C('activityAward', draft('language', 30)));
  /* the native Log screen's Save: the draft cleaned (LOG-1), an operation id required, then recordActivity (LOG-2) */
  const submit = (d, operationId) => C('NativeLogProjection.commands.submit', { draft: d, ...(operationId ? { operationId } : {}) });
  scase('log/native-submit', 'LOG-1 LOG-2', {}, submit(draft('reading', 30.4)), submit(draft('reading', 30.4), 'op-n1'), submit(draft('reading', 30.4), 'op-n1'));
  scase('log/native-submit-refused', 'LOG-1 LOG-2', { activities: [rec('business', 240)] }, submit(draft('reading', 30), 'op-n2'));
}

function quests() {
  const Q = (id, rule, state, ...calls) => scase('quest/' + id, rule, state, ...calls);
  const T = (id, checked = true) => C('toggleQuest', id, checked);
  const daily = quest('d1', 'daily', 15), weekly = quest('w1', 'weekly', 35), epic = quest('e1', 'epic', 75);
  Q('keys', 'QUEST-1 TIME-8', { quests: [daily, weekly, epic] }, C('questKey', 'd1'), C('questKey', 'w1'), C('questKey', 'e1'), C('isQuestDone', 'd1'));
  Q('daily-first', 'QUEST-2 QUEST-3 SPARK-1', { quests: [daily] }, T('d1'));
  Q('daily-attribute', 'QUEST-2', { quests: [quest('d2', 'daily', 20, { attribute: 'physique' })] }, T('d2'));
  Q('daily-second', 'QUEST-3', { quests: [daily], activities: [qrec('daily', 15)] }, T('d1'));
  Q('daily-fourth', 'QUEST-2 QUEST-3 D20', { quests: [daily], activities: [qrec('daily', 15), qrec('daily', 15), qrec('daily', 0)] }, T('d1'));
  Q('daily-yesterday-not-counted', 'QUEST-3', { quests: [daily], activities: [qrec('daily', 15, { day: dayKey(-1) }), qrec('daily', 15, { day: dayKey(-1) }), qrec('daily', 15, { day: dayKey(-1) })] }, T('d1'));
  Q('daily-other-cadences-not-counted', 'QUEST-3', { quests: [daily], activities: [qrec('weekly', 35), qrec('weekly', 35), qrec('epic', 75), qrec(undefined, 10)] }, T('d1'));
  Q('weekly-third', 'QUEST-3', { quests: [weekly], activities: [qrec('weekly', 35, { day: dayKey(-6) }), qrec('weekly', 35, { day: dayKey(-1) })] }, T('w1'));
  Q('weekly-fourth-within-7-days', 'QUEST-3 D09 TIME-5 TIME-7', { quests: [weekly], activities: [qrec('weekly', 35, { day: dayKey(-6) }), qrec('weekly', 35, { day: dayKey(-3) }), qrec('weekly', 35)] }, T('w1'));
  Q('weekly-fourth-after-7-days', 'QUEST-3 D09', { quests: [weekly], activities: [qrec('weekly', 35, { day: dayKey(-7) }), qrec('weekly', 35, { day: dayKey(-3) }), qrec('weekly', 35)] }, T('w1'));
  Q('weekly-limit-from-last-week', 'QUEST-1 QUEST-3 D09', { quests: [weekly], activities: [qrec('weekly', 35, { day: dayKey(-4) }), qrec('weekly', 35, { day: dayKey(-3) }), qrec('weekly', 35, { day: dayKey(-3) })] }, T('w1'));
  Q('epic-first', 'QUEST-2 QUEST-3', { quests: [epic] }, T('e1'));
  Q('epic-exempt-from-cap', 'QUEST-3 CAP-1', { quests: [epic], activities: [rec('business', 240)] }, T('e1'));
  Q('epic-second-today', 'QUEST-2 QUEST-3', { quests: [epic, quest('e2', 'epic', 50)], activities: [qrec('epic', 75)] }, T('e2'));
  Q('epic-yesterday', 'QUEST-3', { quests: [epic], activities: [qrec('epic', 75, { day: dayKey(-1) })] }, T('e1'));
  Q('epic-rested', 'QUEST-3 REST-3', { quests: [epic], restedXP: 100 }, T('e1'));
  Q('epic-rested-at-cap', 'QUEST-3 REST-3', { quests: [epic], restedXP: 100, activities: [rec('business', 240)] }, T('e1'));
  Q('compound', 'QUEST-3 PERK-1 NUM-1 NUM-2', { quests: [quest('d50', 'daily', 50)], ...perk('compound') }, T('d50'));
  Q('compound-rested', 'QUEST-3 PERK-1 REST-3', { quests: [quest('d50', 'daily', 50)], ...perk('compound'), restedXP: 10 }, T('d50'));
  for (const xp of [90, 110, 170, 190]) Q(`compound-${xp}`, 'QUEST-3 PERK-1 NUM-1 NUM-2', { quests: [quest('dc', 'daily', xp)], ...perk('compound') }, T('dc'));
  Q('compound-not-on-weekly', 'QUEST-3 PERK-1', { quests: [quest('w50', 'weekly', 50)], ...perk('compound') }, T('w50'));
  Q('compound-not-equipped', 'QUEST-3 PERK-1', { quests: [quest('d50', 'daily', 50)], perksOwned: ['compound'] }, T('d50'));
  Q('rested', 'QUEST-3 REST-3', { quests: [quest('d20', 'daily', 20)], restedXP: 30 }, T('d20'));
  Q('rested-at-cap', 'QUEST-3 REST-3 D20', { quests: [quest('d20', 'daily', 20)], restedXP: 30, activities: [rec('business', 240)] }, T('d20'));
  Q('cap-straddled', 'QUEST-3 CAP-2', { quests: [quest('d20', 'daily', 20)], activities: [rec('business', 230)] }, T('d20'));
  Q('rested-cap-straddled', 'QUEST-3 REST-3 D05', { quests: [quest('d20', 'daily', 20)], restedXP: 30, activities: [rec('business', 230)] }, T('d20'));
  Q('rested-soft-cap', 'QUEST-3 REST-3 CAP-2', { quests: [quest('d40', 'daily', 40)], restedXP: 5, activities: [rec('business', 170)] }, T('d40'));
  Q('epic-pool-smaller-than-bonus', 'QUEST-3 REST-2', { quests: [epic], restedXP: 10 }, T('e1'));
  Q('soft-cap', 'QUEST-3 CAP-2', { quests: [quest('d40', 'daily', 40)], activities: [rec('business', 170)] }, T('d40'));
  Q('weekly-at-cap-message', 'QUEST-2 D20', { quests: [weekly], activities: [rec('business', 240)] }, T('w1'));
  Q('zero-xp', 'QUEST-2', { quests: [quest('d0', 'daily', 0)] }, T('d0'));
  Q('text-xp', 'QUEST-3 NUM-3', { quests: [quest('dx', 'daily', 'abc')] }, T('dx'));
  Q('no-cadence', 'QUEST-1 QUEST-3', { quests: [quest('n1', undefined, 30)] }, T('n1'), C('questKey', 'n1'));
  Q('uncheck', 'QUEST-2 UNDO-1 D14', { quests: [daily], questDone: { 'd1_2026-10-28': 's1' }, activities: [qrec('daily', 15, { id: 's1' })], sparks: 15 }, T('d1', false));
  Q('uncheck-not-done', 'QUEST-2 D16', { quests: [daily] }, T('d1', false));
  Q('check-already-done', 'QUEST-2 D16', { quests: [daily], questDone: { 'd1_2026-10-28': 's1' }, activities: [qrec('daily', 15, { id: 's1' })] }, T('d1'));
  Q('locked', 'QUEST-2 CURVE-9', { quests: [quest('st3', 'daily', 20, { stage: 3 })] }, T('st3'));
  Q('locked-at-level-3', 'QUEST-2 CURVE-9', { quests: [quest('st3', 'daily', 20, { stage: 3 })], activities: [rec('reading', 475, { day: dayKey(-4) })] }, T('st3'));
  Q('unlocked', 'QUEST-2 CURVE-9', { quests: [quest('st2', 'daily', 20, { stage: 2 })], activities: [rec('reading', 475, { day: dayKey(-4) })] }, T('st2'));
  Q('locked-uncheck-allowed', 'QUEST-2 CURVE-9', { quests: [quest('st3', 'daily', 20, { stage: 3 })], questDone: { 'st3_2026-10-28': 's1' }, activities: [qrec('daily', 20, { id: 's1' })] }, T('st3', false));
  Q('unknown-id', 'QUEST-2', { quests: [daily] }, T('nope'));
  Q('save-fails-kept', 'QUEST-2 D13', { quests: [daily] }, withEnv({ failSave: 'storage' }, T('d1')));
  Q('ecoQuestXP-direct', 'QUEST-3', { quests: [daily], restedXP: 4 }, C('ecoQuestXP', 'd1'));
  add('quest/check-uncheck-check', 'QUEST-2 D14', { state: { quests: [daily], restedXP: 30 }, calls: [T('d1'), at(hoursBefore(-0.1), T('d1', false)), at(hoursBefore(-0.2), T('d1'))] });
  Q('same-millisecond-ids', 'QUEST-2 D17', { quests: [daily, quest('d2', 'daily', 20)] }, T('d1'), T('d2'), T('d1', false));
  add('quest/daily-again-after-midnight', 'QUEST-1 TIME-1', { now: '2026-10-28T23:59:30.000Z', state: { quests: [daily] }, calls: [T('d1'), at('2026-10-29T00:00:30.000Z', T('d1'))] });
  add('quest/weekly-key-changes-on-monday', 'QUEST-1 TIME-8', { now: '2026-11-01T23:59:00.000Z', state: { quests: [weekly] }, calls: [C('questKey', 'w1'), at('2026-11-02T00:01:00.000Z', C('questKey', 'w1'))] });
  /* Life Director's quest bounty through toggleQuest */
  const ref = (o = {}) => ({ questId: 'd1', key: 'd1_2026-10-28', ...o });
  Q('director-bounty', 'LD-4 QUEST-5 D20', { quests: [daily], ...perk('compound') }, C('LifeMaxHost.completeQuest', ref()));
  Q('director-bounty-period-changed', 'LD-4', { quests: [daily] }, C('LifeMaxHost.completeQuest', ref({ key: 'd1_2026-10-27' })));
  Q('director-bounty-done', 'LD-4', { quests: [daily], questDone: { 'd1_2026-10-28': 's1' }, activities: [qrec('daily', 15, { id: 's1' })] }, C('LifeMaxHost.completeQuest', ref()));
  Q('director-bounty-gone', 'LD-4', { quests: [daily] }, C('LifeMaxHost.completeQuest', ref({ questId: 'zz' })));
  Q('director-bounty-future', 'LD-4', { quests: [daily] }, C('LifeMaxHost.completeQuest', ref({ future: true })));
  Q('director-bounty-locked', 'LD-4 CURVE-9', { quests: [quest('st3', 'daily', 20, { stage: 3 })] }, C('LifeMaxHost.completeQuest', { questId: 'st3', key: 'st3_2026-10-28' }));
  Q('director-bounty-no-reference', 'LD-4', { quests: [daily] }, C('LifeMaxHost.completeQuest', null), C('LifeMaxHost.completeQuest', { key: 'd1_2026-10-28' }));
  Q('director-bounty-not-connected', 'LD-4', { quests: null }, C('LifeMaxHost.completeQuest', ref()));
  Q('custom-quest-xp-floor', 'QUEST-4', {}, withEnv({ dom: { newQuestName: '', newQuestCadence: 'daily', newQuestXP: '40' } }, C('createQuest')),
    ...[['Zero', '0'], ['Negative', '-5'], ['Fraction', '0.4']].map(([n, v], i) => withEnv({ dom: { newQuestName: n, newQuestCadence: 'daily', newQuestXP: v } }, at(hoursBefore(-0.01 * (i + 1)), C('createQuest')))));
  Q('custom-quest-xp', 'QUEST-4', {}, withEnv({ dom: { newQuestName: 'Stretch', newQuestCadence: 'daily', newQuestXP: '900' } }, C('createQuest')),
    withEnv({ dom: { newQuestName: 'Walk', newQuestCadence: 'weekly', newQuestXP: '' } }, at(hoursBefore(-0.01), C('createQuest'))));
}

function health() {
  const H = (o = {}) => ({ on: true, ex: true, steps: true, sleep: true, wt: false, seen: {}, last: 0, since: ms(T0) - 2 * 864e5, msg: '', sdk: '', granted: [], src: '', today: null, ...o });
  const granted = ['exercise', 'steps', 'sleep'], ok = { sdk: 'available', granted };
  const sync = (env, manual = false) => withEnv({ health: { status: ok, ...env } }, C('lmaHealthSync', manual));
  const sess = (id, type, startIso, minutes, o = {}) => ({ id, type, start: ms(startIso), end: ms(startIso) + minutes * 60e3, ...o });
  const award = (o) => ({ at: ms(hoursBefore(3)), ...o });
  /* lmaHealthAward: the award rule */
  scase('health/award-workout', 'HEALTH-1 SPLIT-1', {}, C('lmaHealthAward', award({ type: 'running', name: 'Run', duration: 45, hc: 'x1', note: 'Imported from Health Connect' })));
  scase('health/award-steps', 'HEALTH-1', {}, C('lmaHealthAward', award({ type: 'steps', rule: { icon: 'target', skill: 'Walking', primary: 'endurance', secondary: '' }, name: 'Daily steps: 10,000', duration: 0, hc: 'p2026-10-27', skill: 'Walking', primary: 'endurance', secondary: '', xp: 12 })));
  scase('health/award-secondary-from-rule', 'HEALTH-1', {}, C('lmaHealthAward', award({ type: 'strength', name: 'Lift', duration: 50, hc: 'x2' })));
  scase('health/award-secondary-none', 'HEALTH-1', {}, C('lmaHealthAward', award({ type: 'strength', name: 'Lift', duration: 50, hc: 'x2', secondary: '' })));
  scase('health/award-stars-return-before-stars', 'HEALTH-1 STAR-1 D11', { attrStars: { endurance: 2, wellbeing: 1 } }, C('lmaHealthAward', award({ type: 'running', name: 'Run', duration: 60, hc: 'x3' })));
  scase('health/award-per-type-refused', 'HEALTH-1 AWARD-1', { activities: [rec('running', 26), rec('running', 26), rec('running', 13)] }, C('lmaHealthAward', award({ type: 'running', name: 'Run', duration: 45, hc: 'x4' })));
  scase('health/award-past-day', 'HEALTH-1 AWARD-3', { activities: [rec('business', 230, { day: dayKey(-2) })] }, C('lmaHealthAward', { at: ms(iso(dayKey(-2), '18:00:00')), type: 'running', name: 'Run', duration: 45, hc: 'x5' }));
  scase('health/award-steps-spend-rested', 'HEALTH-1 REST-3 D34', { restedXP: 20 }, C('lmaHealthAward', award({ type: 'steps', rule: { icon: 'target', skill: 'Walking', primary: 'endurance', secondary: '' }, name: 'Daily steps', duration: 0, skill: 'Walking', primary: 'endurance', secondary: '', xp: 10 })));
  for (const xp of [45, 85, 165, 175, 325, 335, 345, 355, 365]) scase(`health/award-split-${xp}`, 'HEALTH-1 SPLIT-1 NUM-1 NUM-2', {}, C('lmaHealthAward', award({ type: 'reading', name: 'Split ' + xp, duration: 0, xp })));
  /* lmaHealthSync: which records pay */
  scase('health/sync-no-plugin', 'HEALTH-0', { health: H() }, withEnv({ health: null }, C('lmaHealthSync', true)));
  scase('health/sync-off', 'HEALTH-0', { health: H({ on: false }) }, sync({}));
  scase('health/sync-unavailable', 'HEALTH-0', { health: H() }, withEnv({ health: { status: { sdk: 'unavailable', granted: [] } } }, C('lmaHealthSync', false)));
  scase('health/sync-needs-update', 'HEALTH-0', { health: H() }, withEnv({ health: { status: { sdk: 'update', granted: [] } } }, C('lmaHealthSync', false)));
  scase('health/sync-nothing-granted', 'HEALTH-0', { health: H() }, withEnv({ health: { status: { sdk: 'available', granted: [] } } }, C('lmaHealthSync', false)));
  scase('health/sync-partly-granted', 'HEALTH-0', { health: H() }, withEnv({ health: { status: { sdk: 'available', granted: ['steps'] },
    exercise: [sess('a', 'running', hoursBefore(5), 40)], steps: [{ date: dayKey(-1), count: 9000 }] } }, C('lmaHealthSync', false)));
  scase('health/sync-nothing-wanted', 'HEALTH-0 HEALTH-6', { health: H({ ex: false, steps: false, sleep: false }) }, withEnv({ health: { status: { sdk: 'available', granted: [] } } }, C('lmaHealthSync', true)));
  scase('health/sync-fails-partway', 'HEALTH-0 HEALTH-6', { health: H() }, sync({ exercise: [sess('a', 'running', hoursBefore(5), 40)], steps: [{ date: dayKey(-1), count: 9000 }], fail: 'steps' }, true));
  add('health/weight', 'HEALTH-6', { fields: ['weight'], state: { health: H({ wt: true }) }, calls: [0, 1].map(i => at(hoursBefore(-i), withEnv({ health: { status: { sdk: 'available', granted: [...granted, 'weight'] },
    weight: { kg: 75, time: ms(hoursBefore(10)) } } }, C('lmaHealthSync', false)))) });
  scase('health/sync-sets-since', 'HEALTH-2', { health: H({ since: 0 }) }, sync({ exercise: [sess('a', 'running', hoursBefore(5), 40)] }, true));
  scase('health/workout-kinds', 'HEALTH-3', { health: H() }, sync({ exercise: [sess('y', 'yoga', hoursBefore(30), 30), sess('l', 'weightlifting', hoursBefore(10), 50),
    sess('b', 'biking_stationary', hoursBefore(8), 40), sess('h', 'high_intensity_interval_training', hoursBefore(6), 25)] }, true));
  scase('health/workout-walking-is-passive', 'HEALTH-3 D10 PASSIVE-1', { health: H() }, sync({ exercise: [sess('w', 'walking', hoursBefore(6), 40)] }), C('ecoPassive', { type: 'running', src: 'health', skill: 'Walking' }), C('streak'));
  scase('health/workout-names', 'HEALTH-3', { health: H() }, sync({ exercise: [sess('t', 'running_treadmill', hoursBefore(30), 35, { origin: 'com.sec.android.app.shealth' }),
    sess('o', 'other_workout', hoursBefore(20), 30, { title: 'Morning circuit', origin: 'com.example.tracker' }), sess('s', 'swimming_pool', hoursBefore(6), 30, { origin: 'com.garmin.android.apps.connectmobile' })] }));
  scase('health/workout-filters', 'HEALTH-3 HEALTH-2', { health: H({ seen: { xseen: ms(hoursBefore(20)) } }) }, sync({ exercise: [sess('short', 'running', hoursBefore(5), 4),
    sess('future', 'running', hoursBefore(0.2), 30), sess('old', 'running', hoursBefore(60), 30), sess('seen', 'running', hoursBefore(20), 30), sess('fine', 'strength_training', hoursBefore(4), 4.6)] }));
  scase('health/workout-logged-by-hand', 'HEALTH-3', { health: H(), activities: [rec('running', 26, { date: hoursBefore(4), primary: 'endurance' })] },
    sync({ exercise: [sess('m', 'running', hoursBefore(5), 40), sess('n', 'running', hoursBefore(1.5), 30)] }));
  scase('health/workout-long-logged-by-hand', 'HEALTH-3 D37', { health: H(), activities: [rec('running', 78, { date: hoursBefore(0.5), dur: 150, primary: 'endurance' })] },
    sync({ exercise: [sess('h', 'hiking', hoursBefore(3), 150)] }));
  scase('health/workout-fourth-of-kind', 'HEALTH-3 AWARD-1', { health: H() }, sync({ exercise: [sess('r1', 'running', hoursBefore(9), 30), sess('r2', 'biking', hoursBefore(7), 30),
    sess('r3', 'rowing', hoursBefore(5), 30), sess('r4', 'hiking', hoursBefore(3), 30)] }));
  const day = n => ({ date: dayKey(n), count: 0 });
  scase('health/steps', 'HEALTH-4', { health: H({ since: ms(T0) - 20 * 864e5 }) }, sync({ steps: [
    { ...day(-9), count: 10000 }, { ...day(-8), count: 1999 }, { ...day(-7), count: 6000 }, { ...day(-6), count: 5999 }, { ...day(-5), count: 3999 },
    { ...day(-4), count: 30000 }, { ...day(-3), count: 40000 }, { ...day(-2), count: 4000 }, { ...day(-1), count: 9000 }, { date: TODAY, count: 3000 }] }, true));
  scase('health/steps-before-since', 'HEALTH-4 HEALTH-2', { health: H() }, sync({ steps: [{ ...day(-5), count: 10000 }, { ...day(-2), count: 10000 }, { ...day(-1), count: 10000 }] }));
  scase('health/steps-minus-cardio', 'HEALTH-4', { health: H() }, sync({ exercise: [sess('c', 'running', iso(dayKey(-1), '07:00:00'), 30), sess('k', 'yoga', iso(dayKey(-1), '18:00:00'), 30)],
    steps: [{ ...day(-1), count: 9000 }] }));
  scase('health/steps-huge-with-workout', 'HEALTH-4', { health: H() }, sync({ exercise: [sess('l', 'weightlifting', iso(dayKey(-1), '07:00:00'), 45)], steps: [{ ...day(-1), count: 40000 }] }));
  scase('health/steps-seen', 'HEALTH-4', { health: H({ seen: { ['p' + dayKey(-1)]: ms(iso(dayKey(-1), '23:59:00')) } }) }, sync({ steps: [{ ...day(-1), count: 10000 }] }));
  scase('health/steps-workouts-off', 'HEALTH-4', { health: H({ ex: false }) }, sync({ exercise: [sess('c', 'running', iso(dayKey(-1), '07:00:00'), 30)], steps: [{ ...day(-1), count: 9000 }] }));
  const night = (id, endIso, hours) => ({ id, start: ms(endIso) - hours * 3600e3, end: ms(endIso) });
  /* one night per case: the sync only looks back to max(since, now - 3 days) (HEALTH-2), so a week of nights in one sync would test the window, not the bands */
  for (const h of [2.9, 3, 5.4, 5.5, 6.49, 6.5, 7.49, 7.5, 9.5, 9.51, 12, 12.01])
    scase(`health/sleep-${h}h`, 'HEALTH-5', { health: H() }, sync({ sleep: [night('n', iso(TODAY, '07:00:00'), h)] }));
  scase('health/sleep-window', 'HEALTH-5 HEALTH-2', { health: H({ since: ms(T0) - 10 * 864e5 }) }, sync({ sleep: [
    night('a', iso(dayKey(-4), '07:00:00'), 8), night('b', iso(dayKey(-3), '07:00:00'), 8), night('c', iso(dayKey(-3), '13:00:00'), 4), night('d', iso(dayKey(-2), '07:00:00'), 7),
    night('e', iso(dayKey(-1), '07:00:00'), 6), night('f', iso(TODAY, '07:00:00'), 12.5)] }, true));
  scase('health/sleep-longest-of-day', 'HEALTH-5', { health: H() }, sync({ sleep: [night('nap', iso(TODAY, '06:00:00'), 3.5), night('main', iso(TODAY, '07:30:00'), 8), night('tiny', iso(TODAY, '09:00:00'), 2.5)] }));
  scase('health/sleep-filters', 'HEALTH-5 HEALTH-2', { health: H({ seen: { zs: ms(iso(dayKey(-1), '07:00:00')) } }) }, sync({ sleep: [night('s', iso(dayKey(-1), '07:00:00'), 8),
    night('old', iso(dayKey(-4), '07:00:00'), 8), night('later', hoursBefore(-1), 8)] }));
  scase('health/sleep-same-length-first-wins', 'HEALTH-5', { health: H() }, sync({ sleep: [night('a', iso(TODAY, '07:00:00'), 8), night('b', iso(TODAY, '08:00:00'), 8)] }));
  const nightS = (id, endIso, s) => ({ id, start: ms(endIso) - s * 1000, end: ms(endIso) }); // s seconds long
  scase('health/sleep-name-60m', 'HEALTH-5 D36', { health: H() }, sync({ sleep: [nightS('n', iso(TODAY, '07:00:00'), 7 * 3600 + 59 * 60 + 45)] }));
  scase('health/sleep-name-rounding', 'HEALTH-5 NUM-1 NUM-2', { health: H() }, sync({ sleep: [nightS('n', iso(TODAY, '07:00:00'), 6 * 3600 + 8 * 60 + 30)] }));
  scase('health/sleep-busy-workout', 'HEALTH-5', { health: H() }, sync({ exercise: [sess('n', 'running', iso(TODAY, '03:00:00'), 40)], sleep: [night('z', iso(TODAY, '07:00:00'), 8)] }));
  scase('health/sleep-busy-logged', 'HEALTH-5', { health: H(), activities: [rec('reading', 24, { date: iso(TODAY, '02:00:00') })] }, sync({ sleep: [night('z', iso(TODAY, '07:00:00'), 8)] }));
  scase('health/sleep-edge-not-busy', 'HEALTH-5', { health: H(), activities: [rec('reading', 24, { date: iso(TODAY, '06:30:00') })] },
    sync({ exercise: [sess('e', 'running', iso(TODAY, '06:30:00'), 40)], sleep: [night('z', iso(TODAY, '07:00:00'), 8)] }));
  scase('health/seen-pruned', 'HEALTH-6', { health: H({ seen: { xold: ms(T0) - 22 * 864e5, xnew: ms(T0) - 20 * 864e5, pold: ms(T0) - 21 * 864e5 - 1 } }) }, sync({}));
  /* D01: a step day paid again once its seen mark is pruned */
  const d01 = (t) => at(t, withEnv({ health: { status: ok, steps: [{ date: '2026-10-01', count: 10000 }] } }, C('lmaHealthSync', false)));
  add('health/steps-paid-again', 'D01 HEALTH-4 HEALTH-6', { now: '2026-10-02T12:00:00.000Z', state: { health: H({ ex: false, sleep: false, since: ms('2026-09-30T12:00:00.000Z') }) },
    calls: ['2026-10-02', '2026-10-23', '2026-10-24', '2026-10-25', '2026-10-26'].map(d => d01(d + 'T12:00:00.000Z')) });
}

function voice() {
  const y = dayKey(-1);
  scase('voice/peek', 'VOICE-1 AWARD-4', { restedXP: 20 }, C('lmvLogXp', 'running', 30, TODAY, true));
  scase('voice/commit', 'VOICE-1', { restedXP: 20 }, C('lmvLogXp', 'running', 30, TODAY, false));
  scase('voice/scholar', 'VOICE-1 PERK-1', perk('scholar'), C('lmvLogXp', 'reading', 45, TODAY, true));
  scase('voice/yesterday-limit', 'VOICE-1 AWARD-3', { activities: [rec('reading', 24, { day: y }), rec('reading', 24, { day: y }), rec('reading', 12, { day: y })] }, C('lmvLogXp', 'reading', 30, y, true), C('lmvLogXp', 'reading', 30, TODAY, true));
  scase('voice/active-time', 'VOICE-1 AWARD-1', { activities: [rec('business', 40, { dur: 340 })] }, C('lmvLogXp', 'reading', 30, TODAY, true));
  scase('voice/unknown-type', 'VOICE-1', {}, C('lmvLogXp', 'yoga', 30, TODAY, true));
  const now = (o) => ({ type: 'running', name: 'Run', duration: 30, day: TODAY, atMs: ms(hoursBefore(1)), ...o });
  scase('voice/log-now', 'VOICE-2 SPLIT-1', {}, C('lmvLogNow', now()));
  scase('voice/log-yesterday', 'VOICE-2 AWARD-3', { activities: [rec('business', 230)] }, C('lmvLogNow', now({ day: y, atMs: ms(iso(y, '12:00:00')) })));
  scase('voice/log-refused', 'VOICE-2', { activities: [rec('running', 26), rec('running', 26), rec('running', 13)] }, C('lmvLogNow', now()));
  scase('voice/log-stars', 'VOICE-2 STAR-1', { attrStars: { endurance: 1, wellbeing: 3 } }, C('lmvLogNow', now({ duration: 60 })));
  scase('voice/log-custom', 'VOICE-2', {}, C('lmvLogNow', now({ type: 'custom', name: 'Chess', duration: 40 })));
  scase('voice/log-save-fails-kept', 'VOICE-2 D13', {}, withEnv({ failSave: 'storage' }, C('lmvLogNow', now())));
  for (const s of ['i went for a run', 'did 30 minutes of yoga', 'studied spanish for an hour', 'practiced my french', 'deep work on a client proposal', 'cleaned the garage', 'walked the dog', 'played chess', 'lifted weights at the gym'])
    pure('voice/type-' + s.replace(/\s+/g, '-'), 'VOICE-3', 'lmvActType', s);
}

function director() {
  for (const t of ['fitness.strength', 'fitness.strength.upper', 'fitness.cardio', 'fitness', 'fitness.mobility', 'language', 'language.speaking', 'reading', 'study', 'study.deep',
    'business', 'admin.finance', 'admin', 'admin.review', 'wellbeing', 'nutrition', 'practical', 'practical.outdoor', 'creative.writing', 'chores', 'social', 'general', '', 'fitnessx', null])
    pure('director/type-' + (t === null ? 'null' : t || 'empty'), 'LD-1', 'LifeMaxHost.typeForTag', t);
  const X = (id, rule, state, ...args) => scase('director/xp-' + id, rule, state, C('LifeMaxHost.xpFor', ...args));
  X('peek', 'LD-2 LD-5', { restedXP: 20 }, 'reading', 30, 1, TODAY, false);
  X('commit', 'LD-2', { restedXP: 20 }, 'reading', 30, 1, TODAY, true);
  X('quality-under-20', 'LD-2', {}, 'strength', 15, 1.2, TODAY, false);
  X('quality-over-20', 'LD-2', {}, 'strength', 60, 1.2, TODAY, false);
  X('quality-zero', 'LD-2', {}, 'strength', 60, 0, TODAY, false);
  X('no-minutes', 'LD-2', {}, 'reading', 0, 1, TODAY, false);
  X('custom', 'LD-2', {}, 'custom', 30, 1, TODAY, false);
  X('unknown-type', 'LD-2', {}, 'yoga', 30, 1, TODAY, false);
  X('no-day', 'LD-2', { activities: [rec('reading', 24), rec('reading', 24)] }, 'reading', 30, 1, null, false);
  X('other-day', 'LD-2 AWARD-3', { activities: [rec('reading', 24), rec('reading', 24)] }, 'reading', 30, 1, dayKey(-1), false);
  X('stars', 'LD-2 STAR-1', { attrStars: { physique: 3, discipline: 1 } }, 'strength', 45, 1, TODAY, false);
  X('scholar', 'LD-2 PERK-1', perk('scholar'), 'language', 50, 1, TODAY, false);
  X('cap', 'LD-2 CAP-2', { activities: [rec('business', 220)] }, 'strength', 45, 1, TODAY, true);
  const start = hoursBefore(2);
  const ev = (o = {}) => ({ eventId: 'e1', activityTypeTag: 'fitness.strength', actualStartEpoch: ms(start) / 1000, actualDurationMinutes: 45, title: 'Push day', lifecycleState: 'COMPLETED', ...o });
  const W = (id, rule, state, e, info, env) => scase('director/award-' + id, rule, state, env ? withEnv(env, C('LifeMaxHost.awardActivity', e, info)) : C('LifeMaxHost.awardActivity', e, info));
  W('basic', 'LD-3 SPARK-1', {}, ev(), { title: 'Push day A' });
  W('title-from-event', 'LD-3', {}, ev(), null);
  W('small-session-bonus-at-least-2', 'LD-3', {}, ev({ activityTypeTag: 'reading', actualDurationMinutes: 10 }), {});
  W('big-session-bonus-at-most-5', 'LD-3', {}, ev({ actualDurationMinutes: 120 }), {});
  W('executive-edge-reading-30', 'LD-3 PERK-1', perk('exec'), ev({ activityTypeTag: 'reading', actualDurationMinutes: 30 }), {});
  W('executive-edge-reading-20', 'LD-3 PERK-1', perk('exec'), ev({ activityTypeTag: 'reading', actualDurationMinutes: 20 }), {});
  W('executive-edge-reading-15', 'LD-3 PERK-1', perk('exec'), ev({ activityTypeTag: 'reading', actualDurationMinutes: 15 }), {});
  W('bonus-day-almost-used', 'LD-3', { activities: [rec('reading', 30, { ldBonus: 5 }), rec('business', 30, { ldBonus: 5 }), rec('task', 25, { ldBonus: 3 })] }, ev(), {});
  W('bonus-day-used-up', 'LD-3', { activities: [rec('reading', 30, { ldBonus: 5 }), rec('business', 30, { ldBonus: 5 }), rec('task', 30, { ldBonus: 5 })] }, ev(), {});
  W('bonus-yesterday-not-counted', 'LD-3', { activities: [rec('reading', 30, { ldBonus: 5, day: dayKey(-1) }), rec('business', 30, { ldBonus: 5, day: dayKey(-1) }), rec('task', 30, { ldBonus: 5, day: dayKey(-1) })] }, ev(), {});
  W('session-yesterday', 'LD-3 AWARD-3', { activities: [rec('business', 230, { day: dayKey(-1) })] }, ev({ actualStartEpoch: ms(iso(dayKey(-1), '18:00:00')) / 1000 }), {});
  W('already-in-lifemax', 'LD-3', { activities: [rec('strength', 46, { id: 'ld_e1', ldEventId: 'e1' })] }, ev(), {});
  W('partial', 'LD-3', {}, ev({ lifecycleState: 'PARTIALLY_COMPLETED' }), {});
  W('fourth-records-zero', 'LD-3 D18', { activities: [rec('strength', 42), rec('strength', 42), rec('strength', 21)] }, ev(), {});
  W('too-short-records-zero', 'LD-3 D18', {}, ev({ actualDurationMinutes: 3 }), {});
  W('bonus-after-cap', 'LD-3 D07 D06', { attrStars: { physique: 2 }, activities: [rec('business', 236)] }, ev(), {});
  W('social-is-custom', 'LD-3', {}, ev({ activityTypeTag: 'social', actualDurationMinutes: 60 }), {});
  W('rested', 'LD-3 REST-3', { restedXP: 50 }, ev(), {});
  W('save-fails', 'LD-3 SAVE-4', { restedXP: 50 }, ev(), {}, { failSave: 'storage' });
  W('read-only', 'LD-3 SAVE-4', { restedXP: 50 }, ev(), {}, { failSave: 'readonly' });
  W('not-connected', 'LD-3', { quests: null }, ev(), {});
}

function bonuses() {
  const food = (kcal, day = TODAY) => ({ id: 'f' + kcal + day, date: day, slot: 'lunch', name: 'Meal', grams: 100, kcal, p: 0, c: 0, f: 0 });
  const N = (id, rule, state) => scase('nutrition/' + id, rule, state, C('ecoNutritionCheck'));
  N('no-goal', 'NUTRI-1', { foodLog: [food(2000)] });
  N('on-goal', 'NUTRI-1 SPLIT-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(1200), food(800)] });
  N('lower-bound', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(1700)] });
  N('below', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(1699)] });
  N('upper-bound-2299', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2299)] });
  N('upper-bound-2300', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2300)] }); // 2000 * 1.15 is exactly 2300 in binary
  N('upper-bound-goal-1500', 'NUTRI-1 NUM-1', { mealsMeta: { calGoal: 1500 }, foodLog: [food(1725)] }); // 1500 * 1.15 is 1724.9999999999998
  N('other-day-ignored', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2000, dayKey(-1))] });
  N('already-today', 'NUTRI-1', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2000)], nutriDone: { [TODAY]: 1 } });
  N('rested-then-cap', 'NUTRI-1 REST-3 D03', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2000)], restedXP: 20, activities: [rec('business', 200)] });
  N('cap-reached', 'NUTRI-1 D03 D04', { mealsMeta: { calGoal: 2000 }, foodLog: [food(2000)], restedXP: 20, activities: [rec('business', 240)] });
  const K = (id, rule, state, ...kinds) => scase('care/' + id, rule, state, ...kinds.map(k => C('ecoCare', k)));
  K('all-three', 'CARE-1 SPLIT-1', {}, 'feed', 'play', 'pet');
  K('two-of-three', 'CARE-1', {}, 'feed', 'pet', 'feed');
  K('already-paid', 'CARE-1', { careDay: { day: TODAY, feed: 1, play: 1, pet: 1, paid: 1 } }, 'pet');
  K('new-day-resets', 'CARE-1', { careDay: { day: dayKey(-1), feed: 1, play: 1 } }, 'pet');
  K('rested-then-cap', 'CARE-1 REST-3 D03', { restedXP: 20, activities: [rec('business', 200)] }, 'feed', 'play', 'pet');
  K('cap-reached', 'CARE-1 D03 D04', { restedXP: 20, activities: [rec('business', 240)] }, 'feed', 'play', 'pet');
  const M = (id, rule, state, ...calls) => scase('money/' + id, rule, state, ...calls);
  const dep = (amount, note = '') => C('recordMoneyTransaction', 'deposit', amount, note);
  const money = (start, tx = [], peak) => ({ money: { account: '', start, goal: 0, tx, ...(peak !== undefined ? { peakBalance: peak } : {}) } });
  for (const [name, a] of [['zero', 0], ['negative', -5], ['text', 'abc'], ['huge', 2e12], ['empty', '']]) M('invalid-' + name, 'MONEY-1', {}, dep(a));
  M('bad-type', 'MONEY-1', {}, C('recordMoneyTransaction', 'gift', 50, ''));
  M('withdraw-too-much', 'MONEY-1', money(100), C('recordMoneyTransaction', 'withdraw', 150, ''));
  M('withdraw', 'MONEY-1', money(100), C('recordMoneyTransaction', 'withdraw', 60, 'rent'));
  M('deposit-under-50', 'MONEY-1', money(100), dep(40));
  M('deposit-50', 'MONEY-1 SPLIT-1', money(100), dep(50, 'savings'));
  M('deposit-rounds-cents', 'MONEY-1 NUM-2', money(100), dep(49.995));
  M('deposit-120', 'MONEY-1', money(100), dep(120));
  M('deposit-300-capped-25', 'MONEY-1', money(100), dep(300));
  M('deposit-below-peak', 'MONEY-1', money(100, [], 150), dep(80));
  M('deposit-above-peak', 'MONEY-1', money(100, [], 150), dep(120));
  M('week-cap', 'MONEY-1', { ...money(100, [], 100), activities: [rec('money', 25, { day: dayKey(-3), dur: 0, primary: 'business' }), rec('money', 20, { day: dayKey(-1), dur: 0, primary: 'business' })] }, dep(300));
  M('week-cap-rolling', 'MONEY-1', { ...money(100, [], 100), activities: [rec('money', 25, { day: dayKey(-7), dur: 0 }), rec('money', 20, { day: dayKey(-1), dur: 0 })] }, dep(300));
  M('done-today', 'MONEY-1', { ...money(100, [], 100), activities: [rec('money', 5, { dur: 0 })] }, dep(300));
  M('done-today-in-catalog', 'MONEY-1', { ...money(100, [], 100), activities: [rec('catalog', 15, { link: 'business.transfer-money-into-savings', actId: 'business.transfer-money-into-savings' })] }, dep(300));
  M('rested-then-cap', 'MONEY-1 REST-3 D03', { ...money(100), restedXP: 30 }, dep(200));
  M('cap-reached', 'MONEY-1 D03', { ...money(100), restedXP: 30, activities: [rec('business', 240)] }, dep(200));
  M('note-trimmed', 'MONEY-1', money(0), dep(100, '   ' + 'n'.repeat(80) + '   '));
  M('remove-deposit', 'UNDO-1', { ...money(100, [{ id: 'tx1', type: 'deposit', amount: 100, note: '', date: iso(TODAY), actId: 's1' }], 200), activities: [rec('money', 10, { id: 's1', dur: 0 })] },
    C('removeMoneyTransaction', 'tx1'), C('moneyBalance'));
  M('remove-deposit-pay-again', 'UNDO-1', { ...money(100, [], 200) }, dep(100));
  /* section 19: net = balance + amount - peak in binary doubles */
  M('deposit-125-on-cents', 'MONEY-1 NUM-1 NUM-2', money(3.01, [], 3.01), dep(125)); // net 124.99999999999999: 12 XP, not 13
  M('deposit-threshold-on-cents', 'MONEY-1 NUM-1', money(0.01, [], 0.02), dep(50.01)); // net 49.99999999999999: below 50, no XP
}

function uncapped() {
  const plan = (checks = {}) => ({ plan: { id: 'foundation', start: '2026-10-26', from: '2026-10-26', checks } });
  scase('plan/complete-yesterday', 'PLAN-1 SPLIT-1', plan(), C('planToggle', '2026-10-27', 'C1', 0, true));
  add('plan/complete-today', 'PLAN-1', { now: '2026-10-27T18:00:00.000Z', state: plan(), calls: [C('planToggle', '2026-10-27', 'C1', 0, true)] });
  add('plan/complete-recovery-day', 'PLAN-1', { now: '2026-10-29T18:00:00.000Z', state: plan(), calls: ['M1', 'M2', 'M3', 'M4', 'M5', 'M6'].map(k => C('planToggle', '2026-10-29', k, 0, true)) });
  scase('plan/linked-catalog-pays-zero', 'PLAN-1', { ...plan(), activities: [rec('catalog', 25, { day: '2026-10-27', link: 'endurance.complete-a-cardio-session', actId: 'endurance.complete-a-cardio-session', primary: 'endurance' })] },
    C('planToggle', '2026-10-27', 'C1', 0, true));
  scase('plan/reopen', 'PLAN-1 UNDO-1', plan(), C('planToggle', '2026-10-27', 'C1', 0, true), C('planToggle', '2026-10-27', 'C1', 0, false));
  scase('plan/uncapped-but-counted', 'PLAN-1 D08', { ...plan(), activities: [rec('business', 230)] }, C('planToggle', '2026-10-27', 'C1', 0, true), C('ecoAward', 'reading', 24, 30, { manual: true, peek: true }));
  add('plan/today-counts-toward-cap', 'PLAN-1 D08', { now: '2026-10-27T18:00:00.000Z', state: { ...plan(), activities: [rec('business', 200, { day: '2026-10-27' })] },
    calls: [C('planToggle', '2026-10-27', 'C1', 0, true), C('recordActivity', draft('reading', 30))] });
  const S = (id, rule, state, ...calls) => scase('catalog/' + id, rule, state, ...calls);
  const sw = 'physique.complete-a-strength-workout', cat = (actId, o = {}) => rec('catalog', 35, { actId, link: actId, primary: 'physique', ...o });
  S('status-open', 'CATALOG-1', {}, C('actStatus', sw));
  S('status-second-half', 'CATALOG-1 NUM-2', { activities: [cat(sw)] }, C('actStatus', sw));
  S('status-third-quarter', 'CATALOG-1', { activities: [cat(sw), cat(sw)] }, C('actStatus', sw));
  S('status-daily-limit', 'CATALOG-1', { activities: [cat(sw), cat(sw), cat(sw)] }, C('actStatus', sw));
  S('status-yesterday-not-counted', 'CATALOG-1', { activities: [cat(sw, { day: dayKey(-1) }), cat(sw, { day: dayKey(-1) }), cat(sw, { day: dayKey(-1) })] }, C('actStatus', sw));
  S('status-once-a-day', 'CATALOG-1', { activities: [cat('business.review-your-spending')] }, C('actStatus', 'business.review-your-spending'));
  S('status-once-a-week', 'CATALOG-1', { activities: [cat('business.create-or-update-a-budget', { day: '2026-10-26' })] }, C('actStatus', 'business.create-or-update-a-budget'));
  S('status-last-week', 'CATALOG-1', { activities: [cat('business.create-or-update-a-budget', { day: '2026-10-25' })] }, C('actStatus', 'business.create-or-update-a-budget'));
  S('status-one-time', 'CATALOG-1', { activities: [cat('business.earn-your-first-customer-or-sale', { day: dayKey(-40) })] }, C('actStatus', 'business.earn-your-first-customer-or-sale'));
  S('status-plan-paid', 'CATALOG-1', { activities: [rec('plan', 40, { kind: 'strength', dur: 0 })] }, C('actStatus', sw));
  S('status-plan-paid-zero', 'CATALOG-1', { activities: [rec('plan', 0, { kind: 'strength', dur: 0 })] }, C('actStatus', sw));
  S('status-money-today', 'CATALOG-1', { activities: [rec('money', 10, { dur: 0 })] }, C('actStatus', 'business.transfer-money-into-savings'));
  S('status-custom-base', 'CATALOG-1', { actXP: { [sw]: 50 } }, C('actStatus', sw));
  S('status-text-base', 'CATALOG-1', { actXP: { [sw]: 'abc' } }, C('actStatus', sw));
  S('record', 'CATALOG-1', {}, withEnv({ dom: { actNote: ' heavy day ' } }, C('confirmAct', sw)));
  S('record-second-half', 'CATALOG-1', { activities: [cat(sw, { date: hoursBefore(1) })] }, C('confirmAct', sw));
  S('record-minimum-short', 'CATALOG-1', {}, withEnv({ dom: { actDur: '15' } }, C('confirmAct', 'endurance.complete-a-cardio-session')));
  S('record-minimum-met', 'CATALOG-1', {}, withEnv({ dom: { actDur: '25' } }, C('confirmAct', 'endurance.complete-a-cardio-session')));
  S('record-milestone-unnamed', 'CATALOG-1', {}, C('confirmAct', 'physique.reach-a-strength-milestone'));
  S('record-milestone', 'CATALOG-1', {}, withEnv({ dom: { actRes: '10 pull-ups' } }, C('confirmAct', 'physique.reach-a-strength-milestone')));
  S('record-milestone-again', 'CATALOG-1', { activities: [cat('physique.reach-a-strength-milestone', { result: '10 Pull-ups', day: dayKey(-3) })] },
    withEnv({ dom: { actRes: '10 pull-ups' } }, C('confirmAct', 'physique.reach-a-strength-milestone')));
  S('record-repeat-within-2.5s', 'CATALOG-1', { activities: [cat(sw, { date: new Date(ms(T0) - 2000).toISOString() })] }, C('confirmAct', sw));
  S('record-blocked', 'CATALOG-1', { activities: [cat('business.review-your-spending')] }, C('confirmAct', 'business.review-your-spending'));
  S('undo-last-today', 'CATALOG-1 UNDO-1', { activities: [cat(sw, { day: dayKey(-1) }), cat(sw, { date: hoursBefore(3) }), cat(sw, { date: hoursBefore(2) })] }, C('undoCat', sw));
  S('custom-base-saved', 'CATALOG-1', {}, withEnv({ dom: { actBase: '250' } }, C('saveBase', sw)), C('actStatus', sw));
  S('custom-base-reset', 'CATALOG-1', { actXP: { [sw]: 50 } }, C('saveBase', sw, 1), C('actStatus', sw));
  S('dev-boost', 'DEV-1 CURVE-8', { devMode: true, activities: [rec('strength', 150, { primary: 'physique', day: dayKey(-1) })] }, C('actStatus', 'dev.boost1.physique'), C('confirmAct', 'dev.boost2.physique'), C('attrLevel', 'physique'));
  const jc = ['journalCoach', 'dayReflections'];
  const J = (id, rule, state, ...calls) => add('coach/' + id, rule, { state, calls, fields: jc });
  J('award', 'COACH-1 PASSIVE-1', {}, C('jcAward', 15, 'Journal reflection'), C('streak'));
  J('reflect-today', 'COACH-1', {}, C('jcOnReflect', TODAY, 'x'.repeat(40)));
  J('reflect-too-short', 'COACH-1', {}, C('jcOnReflect', TODAY, ' ' + 'x'.repeat(39) + ' '));
  J('reflect-yesterday', 'COACH-1', {}, C('jcOnReflect', dayKey(-1), 'x'.repeat(40)));
  J('reflect-two-days-ago', 'COACH-1', {}, C('jcOnReflect', dayKey(-2), 'x'.repeat(40)));
  J('reflect-once', 'COACH-1', {}, C('jcOnReflect', TODAY, 'x'.repeat(40)), C('jcOnReflect', TODAY, 'y'.repeat(60)));
  J('weekly-review', 'COACH-1', {}, C('jcSaveReview', 'W:2026-W43', 'w'.repeat(49)), C('jcSaveReview', 'W:2026-W43', 'w'.repeat(50)), C('jcSaveReview', 'W:2026-W43', 'w'.repeat(80)));
  J('monthly-chronicle', 'COACH-1', {}, C('jcSaveReview', 'M:2026-09', 'm'.repeat(60)));
  const welcome = { journalCoach: { days: {}, reviews: {}, cooldown: {}, pats: {}, welcome: { [TODAY]: { gap: 12, claimed: {} } } } };
  J('welcome-back', 'COACH-1', welcome, C('jcClaim', 'fresh'), C('jcClaim', 'fresh'), C('jcClaim', 'pet'));
  J('welcome-catch-up', 'COACH-1', welcome, withEnv({ prompt: 'too short' }, C('jcCatchUp')), withEnv({ prompt: 'Moved house and started a new job.' }, C('jcCatchUp')));
  J('no-welcome-no-claim', 'COACH-1', {}, C('jcClaim', 'fresh'));
}

function sparks() {
  const S = (id, rule, state, ...calls) => scase('sparks/' + id, rule, state, ...calls);
  S('sync', 'SPARK-1', { sparks: 7, activities: [rec('reading', 10, { sp: false }), rec('reading', 5, { sp: false }), rec('reading', -3, { sp: false }), rec('reading', 'abc', { sp: false }), rec('reading', 40)] }, C('ecoSparksSync'));
  S('sync-nothing-new', 'SPARK-1', { sparks: 7, activities: [rec('reading', 10)] }, C('ecoSparksSync'));
  S('sync-sp-zero', 'SPARK-1', { activities: [rec('reading', 10, { sp: false }), { ...rec('reading', 6, { sp: false }), sp: 0 }] }, C('ecoSparksSync'));
  S('sync-from-text', 'SPARK-1 NUM-3', { sparks: '5', activities: [rec('reading', 10, { sp: false })] }, C('ecoSparksSync'));
  for (const [name, v] of [['fraction', 12.6], ['half', 12.5], ['negative', -5], ['text', 'abc']]) S('balance-' + name, 'SPARK-2', { sparks: v }, C('ecoSparks'));
  S('balance-none', 'SPARK-2', {}, C('ecoSparks'));
  S('spend', 'SPARK-3', { sparks: 100 }, C('ecoSpend', 60));
  S('spend-all', 'SPARK-3', { sparks: 100 }, C('ecoSpend', 100));
  S('spend-too-much', 'SPARK-3', { sparks: 100 }, C('ecoSpend', 101));
  S('spend-rounds-balance', 'SPARK-3 SPARK-2', { sparks: 99.6 }, C('ecoSpend', 100));
  const B = (id, rule, state, ...args) => S('buy-' + id, rule, state, C('ecoBuy', ...args));
  B('theme', 'SPARK-4', { sparks: 400 }, 'theme', 'gold');
  B('theme-owned-turns-off', 'SPARK-4', { sparks: 10, themesOwned: ['gold'], theme: 'gold' }, 'theme', 'gold');
  B('theme-owned-switch', 'SPARK-4', { sparks: 10, themesOwned: ['gold', 'violet'], theme: 'gold' }, 'theme', 'violet');
  B('theme-too-dear', 'SPARK-4', { sparks: 299 }, 'theme', 'gold');
  B('perk', 'SPARK-4 PERK-1', { sparks: 700 }, 'perk', 'scholar');
  B('perk-equip-other-owned', 'SPARK-4 PERK-1', { sparks: 0, perksOwned: ['scholar', 'iron'], perk: 'scholar' }, 'perk', 'iron');
  B('perk-unequip', 'SPARK-4 PERK-1', { sparks: 0, perksOwned: ['scholar'], perk: 'scholar' }, 'perk', 'scholar');
  B('perk-too-dear', 'SPARK-4', { sparks: 599, perk: 'iron', perksOwned: ['iron'] }, 'perk', 'scholar');
  B('toy', 'SPARK-4', { sparks: 300 }, 'toy', 'spring');
  B('toy-owned', 'SPARK-4', { sparks: 300, toysBought: { spring: 1 } }, 'toy', 'spring');
  B('toy-too-dear', 'SPARK-4', { sparks: 449 }, 'toy', 'wand');
  B('rest-token', 'SPARK-4', { sparks: 300, restTokens: 1 }, 'rest', 'x');
  B('rest-token-this-week', 'SPARK-4', { sparks: 300, restBoughtAt: ms(T0) - 6 * 864e5 }, 'rest', 'x');
  B('rest-token-a-week-ago', 'SPARK-4', { sparks: 300, restBoughtAt: ms(T0) - 7 * 864e5 }, 'rest', 'x');
  B('shield', 'SPARK-4', { sparks: 300, streakShields: 0 }, 'shield', 'x');
  B('shield-this-month', 'SPARK-4', { sparks: 300, streakShields: 0, shieldBoughtAt: ms(T0) - 29 * 864e5 }, 'shield', 'x');
  B('shield-full', 'SPARK-4', { sparks: 300, streakShields: 2 }, 'shield', 'x');
  B('shield-too-dear', 'SPARK-4', { sparks: 249, streakShields: 1 }, 'shield', 'x');
  B('unknown-kind', 'SPARK-4', { sparks: 100 }, 'badge', 'x');
  add('sparks/daily-hand-reroll', 'SPARK-5', { hand: true, state: { sparks: 60 }, calls: [C('dhRerollCost'), C('dhReroll', 0), C('dhRerollCost'), C('dhReroll', 1), C('dhReroll', 2)], omit: ['quests'] });
  for (const [name, st] of [['equipped', perk('iron')], ['not-owned', { perk: 'iron' }], ['not-equipped', { perksOwned: ['iron'] }], ['other', perk('exec')]])
    scase('perk/' + name, 'PERK-1', st, C('ecoPerk', 'iron'));
  const lv20 = (extra = {}) => ({ activities: [rec('strength', 6175, { primary: 'physique', day: dayKey(-1) })], ...extra });
  scase('ascend/below-20', 'ASCEND-1', { activities: [rec('strength', 6174, { primary: 'physique', day: dayKey(-1) })] }, C('ecoAscend', 'physique'));
  scase('ascend/at-20', 'ASCEND-1 CURVE-5 SPARK-1', lv20(), C('ecoAscend', 'physique'), C('attrLevel', 'physique'), C('characterLevel'));
  scase('ascend/second-star', 'ASCEND-1', { activities: [rec('strength', 6175, { primary: 'physique', day: dayKey(-9) }), rec('strength', 6175, { primary: 'physique', day: dayKey(-1) })], ascended: { physique: 6175 }, attrStars: { physique: 1 } },
    C('ecoAscend', 'physique'));
  scase('ascend/three-stars', 'ASCEND-1', lv20({ attrStars: { physique: 3 } }), C('ecoAscend', 'physique'));
  scase('ascend/not-confirmed', 'ASCEND-1', lv20(), withEnv({ confirm: false }, C('ecoAscend', 'physique')));
  scase('ascend/unknown-attribute', 'ASCEND-1', lv20(), C('ecoAscend', 'chess'));
}

function decay() {
  const D = (id, rule, state, ...calls) => scase('decay/' + id, rule, state, ...(calls.length ? calls : [C('applyDecay')]));
  const trained = (n, xp = 150, o = {}) => rec('strength', xp, { primary: 'physique', day: dayKey(-n), ...o });
  D('off', 'DECAY-1 DECAY-3', { activities: [trained(10)], decay: { on: false } });
  D('settings-merged', 'DECAY-1', { decay: { grace: 6 } }, C('getDecay'));
  D('settings-saved', 'DECAY-1', {}, withEnv({ dom: { decayOn: true, decayGrace: '20', decayRate: '0' } }, C('saveDecay')), C('getDecay'));
  D('settings-saved-low', 'DECAY-1', {}, withEnv({ dom: { decayOn: false, decayGrace: '-2', decayRate: '4' } }, C('saveDecay')), C('getDecay'));
  D('within-grace', 'DECAY-3', { activities: [trained(4)] });
  D('first-day-past-grace', 'DECAY-3 DECAY-4', { activities: [trained(5)] });
  D('six-days', 'DECAY-3 DECAY-4', { activities: [trained(10)] });
  D('applied-before', 'DECAY-3', { activities: [trained(10)], decayApplied: { physique: dayN(-2) } });
  D('applied-today', 'DECAY-3', { activities: [trained(10)], decayApplied: { physique: dayN(0) } });
  D('stops-at-level-start', 'DECAY-3', { activities: [trained(10, 102)] });
  D('level-start-safe', 'DECAY-3', { activities: [trained(10, 100)] });
  D('level-1-to-zero', 'DECAY-3', { activities: [trained(10, 2)] });
  D('level-17-ceiling', 'DECAY-3 DECAY-4', { activities: [trained(6, 4800)], decay: { rate: 10 } });
  for (const [xp, L] of [[50, 1], [150, 2], [300, 3], [700, 5], [2400, 10]]) D(`per-day-level-${L}`, 'DECAY-4', { activities: [trained(5, xp)] });
  D('rate-and-grace', 'DECAY-1 DECAY-3', { activities: [trained(5, 700)], decay: { grace: 2, rate: 7 } });
  D('rest-day-skipped', 'DECAY-3 REST-DAY-1', { activities: [trained(10)], restDays: { [dayKey(-3)]: true, [dayKey(-1)]: true } });
  D('recovery-day-skipped', 'DECAY-3 REST-DAY-1', { activities: [trained(10), rec('recovery', 16, { day: dayKey(-2), dur: 20, primary: 'wellbeing', secondary: 'endurance' }),
    rec('recovery', 16, { day: dayKey(-1), dur: 19, primary: 'wellbeing', secondary: 'endurance' }), rec('recovery', 16, { day: dayKey(-3), dur: 40, src: 'health', skill: 'Sleep', primary: 'wellbeing' })] });
  D('plan-rest-day-skipped', 'DECAY-3 REST-DAY-1', { activities: [trained(10)], plan: { id: 'foundation', start: '2026-10-19', from: '2026-10-19', checks: {} } });
  D('iron-grace', 'DECAY-3 PERK-1', { ...perk('iron'), activities: [trained(6), rec('running', 150, { primary: 'endurance', day: dayKey(-6) }), rec('reading', 150, { primary: 'intelligence', day: dayKey(-6) })] });
  D('rested-after-7-idle', 'DECAY-5', { activities: [trained(7, 700)] });
  D('rested-not-before-7', 'DECAY-5', { activities: [trained(6, 700)] });
  D('rested-capped-300', 'DECAY-5 REST-4', { activities: [trained(12, 700)], restedXP: 295 });
  D('rested-per-attribute', 'DECAY-5', { activities: [rec('reading', 700, { primary: 'intelligence', secondary: 'discipline', day: dayKey(-9), split: [{ attr: 'intelligence', xp: 490 }, { attr: 'discipline', xp: 210 }] })] });
  D('log-keeps-30', 'DECAY-3', { activities: [trained(5)], decayLog: Array.from({ length: 30 }, (_, i) => ({ date: dayKey(-1 - i), attr: 'social', xp: 3 })) });
  D('passive-not-training', 'DECAY-2 PASSIVE-1', { activities: [trained(10), rec('steps', 12, { primary: 'physique', day: dayKey(-1), src: 'health', skill: 'Walking', dur: 0 }), rec('coach', 15, { primary: 'physique', day: dayKey(-1), dur: 0 })] });
  D('quest-counts-as-training', 'DECAY-2 D35', { activities: [trained(10), qrec('daily', 15, { primary: 'physique', day: dayKey(-1) })] });
  D('care-keeps-social', 'DECAY-2 D35', { activities: [rec('reading', 300, { primary: 'social', day: dayKey(-12) }), rec('catcare', 10, { primary: 'wellbeing', secondary: 'social', day: dayKey(-1), dur: 0 })] });
  D('decay-total-adds-up', 'DECAY-3 DERIVE-1', { activities: [trained(10, 300)], decayTotal: { physique: 20 } }, C('applyDecay'), C('save'));
  D('last-trained', 'DECAY-2', { activities: [trained(10), rec('reading', 24, { primary: 'intelligence', secondary: 'physique', day: dayKey(-3) }), rec('reading', 0, { primary: 'physique', day: dayKey(-2) })] },
    C('lastTrained', 'physique'), C('lastTrained', 'intelligence'), C('lastTrained', 'social'));
  /* D21: the same idle days, decayed every day or once at the end */
  const idle = { activities: [rec('strength', 700, { primary: 'physique', day: '2026-10-16' })] };
  add('decay/rested-opened-daily', 'D21 DECAY-5', { now: '2026-10-21T12:00:00.000Z', state: idle, calls: Array.from({ length: 8 }, (_, i) => at(new Date(ms('2026-10-21T12:00:00.000Z') + i * 864e5).toISOString(), C('applyDecay'))) });
  add('decay/rested-opened-once', 'D21 DECAY-5', { now: '2026-10-28T12:00:00.000Z', state: idle, calls: [C('applyDecay')] });
  const R = (id, rule, state, ...calls) => scase('rest/' + id, rule, state, ...calls);
  R('is-rest-day', 'REST-DAY-1', { restDays: { [TODAY]: true }, activities: [rec('recovery', 16, { day: dayKey(-1), dur: 20 }), rec('recovery', 16, { day: dayKey(-2), dur: 19 })] },
    C('ecoRestDay', TODAY), C('ecoRestDay', dayKey(-1)), C('ecoRestDay', dayKey(-2)), C('ecoRestDay', dayKey(-3)));
  R('used', 'REST-DAY-2 TIME-7', { restDays: { [dayKey(-7)]: true, [dayKey(-6)]: true, [TODAY]: true } }, C('ecoRestUsed'));
  R('take', 'REST-DAY-2', {}, C('ecoTakeRest'));
  R('take-already', 'REST-DAY-2', { restDays: { [TODAY]: true } }, C('ecoTakeRest'));
  R('take-two-used', 'REST-DAY-2', { restDays: { [dayKey(-5)]: true, [dayKey(-2)]: true } }, C('ecoTakeRest'));
  R('take-two-used-token', 'REST-DAY-2', { restDays: { [dayKey(-5)]: true, [dayKey(-2)]: true }, restTokens: 1 }, C('ecoTakeRest'));
  R('take-week-old-free', 'REST-DAY-2', { restDays: { [dayKey(-7)]: true, [dayKey(-2)]: true } }, C('ecoTakeRest'));
}

function streaks() {
  const day = (n, o = {}) => rec('reading', 24, { day: dayKey(-n), ...o });
  const S = (id, rule, state, ...calls) => scase('streak/' + id, rule, state, ...(calls.length ? calls : [C('streak')]));
  S('none', 'STREAK-1', {});
  S('today-and-yesterday', 'STREAK-1', { activities: [day(0), day(1)] });
  S('from-yesterday', 'STREAK-1', { activities: [day(1), day(2), day(3)] });
  S('single-miss', 'STREAK-1', { activities: [day(0), day(2), day(3)] });
  S('double-miss', 'STREAK-1', { activities: [day(0), day(3), day(4)] });
  S('passive-ignored', 'STREAK-1 PASSIVE-1', { activities: [day(0), rec('steps', 12, { day: dayKey(-1), src: 'health', skill: 'Walking' }), rec('sleep', 15, { day: dayKey(-2) }), day(3)] });
  S('rest-and-shield-days', 'STREAK-1', { activities: [day(0), day(3)], restDays: { [dayKey(-1)]: true }, shieldDays: { [dayKey(-2)]: true } });
  S('ten-days', 'STREAK-1', { activities: Array.from({ length: 10 }, (_, i) => day(i)) });
  for (const [name, a] of [['steps', { type: 'steps' }], ['coach', { type: 'coach' }], ['sleep', { type: 'sleep' }], ['health-walking', { type: 'running', src: 'health', skill: 'Walking' }],
    ['health-sleep', { type: 'recovery', src: 'health', skill: 'Sleep' }], ['health-run', { type: 'running', src: 'health', skill: 'Running' }], ['manual-walking', { type: 'running', skill: 'Walking' }]])
    pure('passive/' + name, 'PASSIVE-1', 'ecoPassive', a);
  const T = (id, rule, state, ...calls) => scase('shield/' + id, rule, state, ...(calls.length ? calls : [C('ecoShieldTick')]));
  T('first-tick', 'SHIELD-2', { activities: [day(1)] });
  T('active-days', 'SHIELD-1 SHIELD-2', { activities: [day(1), day(2), day(3)], shieldTick: dayN(-4) }, C('ecoActive', dayKey(-1)), C('ecoShieldTick'));
  T('gap-covered', 'SHIELD-2', { activities: [day(5), day(6)], shieldTick: dayN(-6), streakShields: 1 });
  T('gap-no-shield', 'SHIELD-2', { activities: [day(5), day(6)], shieldTick: dayN(-6), streakShields: 0 });
  T('gap-two-shields', 'SHIELD-2', { activities: [day(6), day(7)], shieldTick: dayN(-8), streakShields: 2 });
  T('gap-nothing-recent', 'SHIELD-2', { activities: [day(40), day(41)], shieldTick: dayN(-6), streakShields: 1 });
  T('rest-day-is-present', 'SHIELD-2', { activities: [day(5), day(6)], shieldTick: dayN(-6), streakShields: 1, restDays: { [dayKey(-3)]: true } });
  T('passive-day-is-missed', 'SHIELD-1 SHIELD-2', { activities: [day(5), day(6), rec('steps', 12, { day: dayKey(-3), src: 'health', skill: 'Walking' })], shieldTick: dayN(-6), streakShields: 1 });
  T('milestone-earned', 'SHIELD-2', { activities: Array.from({ length: 7 }, (_, i) => day(i)), shieldTick: dayN(-1) });
  T('milestone-two', 'SHIELD-2', { activities: Array.from({ length: 14 }, (_, i) => day(i)), shieldTick: dayN(-1), streakShields: 1 });
  T('milestone-held', 'SHIELD-2', { activities: Array.from({ length: 8 }, (_, i) => day(i)), shieldTick: dayN(-1), shieldMilestone: 1, streakShields: 1 });
  T('milestone-follows-streak-down', 'SHIELD-2 D28', { activities: [day(0), day(1)], shieldTick: dayN(-1), shieldMilestone: 2, streakShields: 0 });
  T('runs-inside-decay', 'SHIELD-2 DECAY-3', { activities: [day(5), day(6)], shieldTick: dayN(-6), streakShields: 1, decay: { on: false } }, C('applyDecay'));
}

function saveAndMigrate() {
  add('save/credits-sparks-and-rebuilds', 'SAVE-1 SPARK-1 DERIVE-1 DERIVE-2', { stale: true, calls: [C('save')], state: { sparks: 10, decayTotal: { intelligence: 30 }, ascended: { business: 100 },
    attrXP: { physique: 0, endurance: 0, intelligence: 999, business: 999, social: 0, discipline: 0, adventure: 0, wellbeing: 0 }, skillXP: { Knowledge: 999, Stale: 5 },
    activities: [rec('reading', 60, { sp: false, primary: 'intelligence', secondary: 'discipline', skill: ' Knowledge ', split: [{ attr: 'intelligence', xp: 42 }, { attr: 'discipline', xp: 18 }] }),
      rec('business', 150, { primary: 'business', skill: 'Entrepreneurship', day: dayKey(-1) }),
      rec('task', 20, { sp: false, split: [], primary: 'discipline', skill: '' }),
      rec('custom', 12, { sp: false, split: [{ attr: 'chess', xp: 5 }, { attr: 'adventure', xp: -4 }, null, { attr: 'social', xp: '7' }], primary: 'adventure', skill: 'Chess' })] } });
  /* SAVE-2: the save wrapper lets the Daily Hand check today's cards; a Log that meets a card's verification pays that quest too */
  const card = (slot, tpl, name, desc, attribute, tier, verify) => ({ id: 'dh_' + TODAY + '_' + slot + '_0', hand: true, slot, tpl, name, desc, cadence: 'daily',
    xp: [0, 10, 20, 30][tier], kind: 'hand', attribute, verify, tier });
  const hand = [card(0, 't2', 'Proper strength session', '25 minutes of resistance training.', 'physique', 2, 'act:strength:25'),
    card(1, 't4', 'Posture reset', 'Stand tall, roll the shoulders, 20 slow squats.', 'physique', 1, 'self'),
    card(2, 't11', 'Take the stairs', 'Use stairs every chance today.', 'endurance', 1, 'self')];
  add('save/daily-hand-pays-inside-save', 'SAVE-2 QUEST-5 SPARK-5', { hand: true, verify: true, fields: ['dailyDraw', 'dhMig'],
    state: { dhMig: 1, quests: hand, dailyDraw: { date: TODAY, nonce: 0, rerollsUsed: 0, cards: hand.map(q => ({ tpl: q.tpl, id: q.id, n: 0 })) } },
    calls: [C('recordActivity', draft('strength', 20)), C('recordActivity', draft('strength', 25)), C('dhReroll', 0)] });
  scase('save/fails', 'SAVE-1 SAVE-3', { activities: [rec('reading', 24, { sp: false })] }, withEnv({ failSave: 'storage' }, C('save')));
  scase('save/read-only', 'SAVE-3', { activities: [rec('reading', 24, { sp: false })] }, withEnv({ failSave: 'readonly' }, C('save')));
  const staleAttr = { physique: 7, endurance: 7, intelligence: 7, business: 7, social: 7, discipline: 7, adventure: 7, wellbeing: 7, chess: 7 };
  add('derive/rebuild-attributes', 'DERIVE-1', { stale: true, calls: [C('rebuildAttrXP')], state: { attrXP: staleAttr,
    activities: [rec('reading', 50, { split: [{ attr: 'intelligence', xp: 35 }, { attr: 'discipline', xp: 15 }] }), rec('quest', 20, { primary: 'physique', split: null }), rec('task', 'abc', { primary: 'business', split: [] }),
      rec('custom', 30, { primary: 'chess', split: [] }), rec('custom', 12, { primary: 'adventure', split: [{ attr: 'adventure', xp: -4 }, null, { attr: 'social', xp: '7' }, { attr: 'social', xp: 'x' }] })],
    decayTotal: { intelligence: 50, discipline: -5 }, ascended: { physique: 5 } } });
  add('derive/rebuild-attributes-floor', 'DERIVE-1', { stale: true, calls: [C('rebuildAttrXP')], state: { attrXP: staleAttr, activities: [rec('reading', 40, { primary: 'intelligence' })],
    decayTotal: { intelligence: 30, social: 10 }, ascended: { intelligence: 20 } } });
  add('derive/rebuild-skills', 'DERIVE-2', { stale: true, calls: [C('rebuildSkillXP')], state: { skillXP: { Knowledge: 999, Gone: 3 },
    activities: [rec('reading', 50, { skill: 'Knowledge' }), rec('reading', -5, { skill: 'Knowledge' }), rec('reading', 10, { skill: '  ' }), rec('reading', '7', { skill: ' Chess' }), rec('reading', 4, { skill: '' })] } });
  add('derive/load-rebuilds', 'DERIVE-1 DERIVE-2 SAVE-5', { stale: true, calls: [withEnv({ stored: { name: 'Stored', xpSchemaV2: true, attrXP: staleAttr, skillXP: { Gone: 3 },
    activities: [rec('reading', 50, { split: [{ attr: 'intelligence', xp: 35 }, { attr: 'discipline', xp: 15 }], skill: 'Knowledge' })] } }, C('load')), C('totalXP')] });
  const old = (o = {}) => ({ xpSchemaV2: false, activities: [rec('reading', 700, { primary: 'intelligence', day: dayKey(-10) }), rec('strength', 534, { primary: 'physique', day: dayKey(-9) })], ...o });
  scase('migrate/character-level-kept', 'MIG-1 MIG-2 MIG-3 MIG-6 SPARK-6', old(), C('ecoMigrate'), C('characterLevel'));
  scase('migrate/empty', 'MIG-1 MIG-2', { xpSchemaV2: false }, C('ecoMigrate'));
  scase('migrate/already-done', 'MIG-6', { activities: [rec('reading', 700, { sp: false })] }, C('ecoMigrate'));
  scase('migrate/old-decay-defaults-dropped', 'MIG-3', old({ decay: { on: true, grace: 3, rate: 2 } }), C('ecoMigrate'));
  scase('migrate/own-decay-kept', 'MIG-3', old({ decay: { on: true, grace: 3, rate: 5 } }), C('ecoMigrate'));
  scase('migrate/old-rules-rewritten', 'MIG-4 RULES-1', old({ customRules: { reading: { name: 'Focused reading / study', icon: 'book', base: 20, skill: 'Knowledge', primary: 'discipline', secondary: 'discipline', desc: '' },
    language: { name: 'Language practice', icon: 'chat', base: 20, skill: 'Spanish', primary: 'intelligence', secondary: 'discipline', desc: '' } } }), C('ecoMigrate'), C('activityPreview', draft('reading', 30)));
  scase('migrate/money-peak', 'MIG-5', old({ money: { account: '', start: 100, goal: 0, tx: [{ id: 't1', type: 'deposit', amount: 50, note: '', date: iso(dayKey(-3)) }] } }), C('ecoMigrate'), C('recordMoneyTransaction', 'deposit', 60, ''));
  scase('migrate/inside-first-save', 'D22 MIG-6 SAVE-1', { xpSchemaV2: false }, C('recordActivity', draft('reading', 30)));
  scase('load/stored-save-migrated', 'SAVE-5 RULES-1 MIG-6', {}, withEnv({ stored: { name: 'Stored', activities: [rec('reading', 300, { primary: 'intelligence', day: dayKey(-2), sp: false })], attrXP: { intelligence: 9999 },
    customRules: { reading: { name: 'Reading', icon: 'book', base: 40, skill: 'Knowledge', primary: 'intelligence', secondary: 'discipline', desc: '' } } } }, C('load')), C('activityPreview', draft('reading', 30)));
  scase('load/stored-save-empty-ledger', 'SAVE-5', {}, withEnv({ stored: { name: 'Stored', xpSchemaV2: true, activities: [], attrXP: { social: 120 } } }, C('load')), C('attrLevel', 'social'));
  scase('load/newer-save-read-only', 'SAVE-5 SAVE-3', {}, withEnv({ stored: { name: 'Newer', schema: 99, xpSchemaV2: true, activities: [] } }, C('load')), C('save'));
  scase('rules/saved', 'RULES-1', {}, withEnv({ dom: { 'rule-reading': '500', 'rule-business': '0', 'rule-strength': '35' } }, C('saveRules')), C('activityPreview', draft('reading', 30)));
  scase('rules/reset', 'RULES-1', { customRules: { reading: { name: 'Reading', icon: 'book', base: 40, skill: 'Knowledge', primary: 'intelligence', secondary: 'discipline', desc: '' } } }, C('resetRules'), C('activityPreview', draft('reading', 30)));
  scase('undo/delete-quest-record', 'UNDO-1 D14', { quests: [quest('d1', 'daily', 15)], questDone: { 'd1_2026-10-28': 's1' }, activities: [qrec('daily', 15, { id: 's1' })], sparks: 15, restedXP: 3 },
    C('deleteActivity', 's1'), C('isQuestDone', 'd1'));
  scase('undo/delete-deposit-record', 'UNDO-1', { money: { account: '', start: 0, goal: 0, tx: [{ id: 'tx1', type: 'deposit', amount: 100, note: '', date: iso(TODAY), actId: 's1' }], peakBalance: 100 },
    activities: [rec('money', 10, { id: 's1', dur: 0 })] }, C('deleteActivity', 's1'));
  scase('undo/delete-plan-record', 'UNDO-1', { plan: { id: 'foundation', start: '2026-10-26', from: '2026-10-26', checks: { '2026-10-27': { s: { C1: [true] }, v: {}, actId: 'plan_2026-10-27' } } },
    activities: [rec('plan', 30, { id: 'plan_2026-10-27', planDate: '2026-10-27', kind: 'cardio', day: '2026-10-27', dur: 0 })] }, C('deleteActivity', 'plan_2026-10-27'));
  scase('undo/delete-not-confirmed', 'UNDO-1', { activities: [rec('reading', 24, { id: 's1' })] }, withEnv({ confirm: false }, C('deleteActivity', 's1')));
}

function boundaries() {
  const late = '2026-10-28T23:59:30.000Z', early = '2026-10-29T00:00:30.000Z';
  add('day/log-just-before-midnight', 'TIME-1 AWARD-1', { now: late, state: { activities: [rec('reading', 24), rec('reading', 24), rec('reading', 12)] }, calls: [C('recordActivity', draft('reading', 30))] });
  add('day/log-just-after-midnight', 'TIME-1 AWARD-1', { now: late, state: { activities: [rec('reading', 24), rec('reading', 24), rec('reading', 12)] }, calls: [at(early, C('recordActivity', draft('reading', 30)))] });
  add('day/cap-resets-at-midnight', 'TIME-1 CAP-1', { now: late, state: { activities: [rec('business', 240)] }, calls: [C('ecoAward', 'reading', 24, 30, { peek: true }), at(early, C('ecoAward', 'reading', 24, 30, { peek: true }))] });
  add('day/epic-again-next-day', 'TIME-1 QUEST-3', { now: late, state: { quests: [quest('e1', 'epic', 75), quest('e2', 'epic', 60)] }, calls: [C('toggleQuest', 'e1', true), at(early, C('toggleQuest', 'e2', true))] });
  /* a time zone with daylight saving: local days, Monday weeks and "the last 7 days" follow the wall clock */
  const nyNow = '2026-11-03T17:00:00.000Z'; // Tuesday 12:00 EST; clocks went back on Sunday 2026-11-01
  const nyRec = (type, xp, isoDate, o = {}) => rec(type, xp, { date: isoDate, ...o });
  add('zone/week-key-after-dst', 'TIME-8 QUEST-1', { zone: NY, now: nyNow, state: { quests: [quest('w1', 'weekly', 35), quest('d1', 'daily', 15)] }, calls: [C('questKey', 'w1'), C('questKey', 'd1')] });
  add('zone/weekly-window-across-dst', 'TIME-7 QUEST-3 D09', { zone: NY, now: nyNow, state: { quests: [quest('w1', 'weekly', 35)],
    activities: [qrec('weekly', 35, { date: '2026-10-28T04:30:00.000Z' }), qrec('weekly', 35, { date: '2026-10-28T16:00:00.000Z' }), qrec('weekly', 35, { date: '2026-11-02T15:00:00.000Z' })] }, calls: [C('toggleQuest', 'w1', true)] });
  add('zone/record-day-local', 'TIME-2 TIME-4 AWARD-1', { zone: NY, now: '2026-10-28T03:00:00.000Z', state: { activities: [nyRec('reading', 24, '2026-10-28T01:00:00.000Z'), nyRec('reading', 24, '2026-10-27T05:00:00.000Z'), nyRec('reading', 12, '2026-10-27T03:00:00.000Z')] },
    calls: [C('getToday'), C('ecoAward', 'reading', 24, 30, { peek: true })] });
  add('zone/money-week-across-dst', 'TIME-7 MONEY-1', { zone: NY, now: nyNow, state: { money: { account: '', start: 100, goal: 0, tx: [], peakBalance: 100 },
    activities: [nyRec('money', 25, '2026-10-28T16:00:00.000Z', { dur: 0 }), nyRec('money', 20, '2026-10-27T16:00:00.000Z', { dur: 0 })] }, calls: [C('recordMoneyTransaction', 'deposit', 300, '')] });
  add('zone/rest-used-across-dst', 'TIME-7 REST-DAY-2', { zone: NY, now: nyNow, state: { restDays: { '2026-10-28': true, '2026-10-27': true } }, calls: [C('ecoRestUsed'), C('ecoTakeRest')] });
  add('zone/steps-at-local-evening', 'HEALTH-4 TIME-2', { zone: NY, now: nyNow, state: { health: { on: true, ex: false, steps: true, sleep: false, wt: false, seen: {}, last: 0, since: ms(nyNow) - 3 * 864e5, msg: '', sdk: '', granted: [], src: '', today: null } },
    calls: [withEnv({ health: { status: { sdk: 'available', granted: ['steps'] }, steps: [{ date: '2026-11-01', count: 9000 }, { date: '2026-11-02', count: 12000 }, { date: '2026-11-03', count: 4000 }] } }, C('lmaHealthSync', false))] });
  add('zone/streak-across-dst', 'STREAK-1 TIME-7', { zone: NY, now: nyNow, state: { activities: [nyRec('reading', 24, '2026-11-03T14:00:00.000Z'), nyRec('reading', 24, '2026-11-02T14:00:00.000Z'),
    nyRec('reading', 24, '2026-11-01T14:00:00.000Z'), nyRec('reading', 24, '2026-10-31T14:00:00.000Z')] }, calls: [C('streak')] });
}

function defineCases() {
  if (CASES.length) return CASES;
  add('fresh/save', 'SAVE-5', { fresh: true });
  [curves, time, scoring, capAndRested, award, log, quests, health, voice, director, bonuses, uncapped, sparks, decay, streaks, saveAndMigrate, boundaries].forEach(f => f());
  return CASES;
}

/* ---------- the page side: runs in the built page, against its own functions ---------- */
function pageRunner(fieldsAll) {
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));
  const realSetItem = Storage.prototype.setItem, realPlugin = window.lmaPlugin, library = window.QUEST_LIBRARY;
  let uuid = 0, seed = 1, toasts = [], before = null, fields = [], answer = true, typed = null;
  window.toast = m => { toasts.push(String(m)) };
  window.confirm = () => answer;
  window.prompt = () => typed;
  window.alert = () => { };
  crypto.randomUUID = () => '00000000-0000-4000-8000-' + String(++uuid).padStart(12, '0');
  Math.random = () => (seed = seed * 16807 % 2147483647) / 2147483647;
  dhBusy = true; // the Daily Hand verifies no cards inside save() unless a case says so (fresh)
  const pick = keys => { const o = {}; for (const k of keys) if (state[k] !== undefined) o[k] = clone(state[k]); if (o.health) delete o.health.snap; return o };
  const quest = id => state.quests.find(q => q.id === id);
  const ADAPT = {
    levelData: (t, step) => levelData(t, step === 'charXpForLevel' ? charXpForLevel : undefined),
    skillLevel: name => levelData(state.skillXP[name]),
    questLocked: id => questLocked(quest(id)), questKey: id => questKey(quest(id)), isQuestDone: id => isQuestDone(quest(id)), ecoQuestXP: id => ecoQuestXP(quest(id)),
    actStatus: id => actStatus(actById(id)),
  };
  const SHEET = ['confirmAct', 'undoCat', 'saveBase'];
  const healthPlugin = h => {
    const read = (name, v) => async () => { if (h.fail === name) throw new Error('Health Connect stopped answering'); return v };
    return { status: async () => h.status, readExercise: read('exercise', { sessions: h.exercise || [] }), readSteps: read('steps', { days: h.steps || [] }),
      readSleep: read('sleep', { sessions: h.sleep || [] }), readWeight: read('weight', h.weight || {}) };
  };
  function fresh(hand, verify) {
    window.QUEST_LIBRARY = hand ? library : undefined; // without its library the Daily Hand draws no cards
    saveReadOnly = ''; saveReadOnlyTold = false; lmaHealthBusy = false; shBusy = false;
    dhBusy = !verify; // only a verify case lets the Daily Hand check its cards inside save()
    state = Object.assign(freshState(), { xpSchemaV2: true, quests: [] });
    rules = structuredClone(DEFAULT_RULES); state.customRules = rules;
  }
  /* The page is drawn once before a case begins, as it is after load(), so the defaults drawing fills in (money, meal settings) are
     in place and do not show up as a change the case made. Drawing the journal saves (jcChip): that save is held back here, or it
     would credit Sparks, mark records and migrate before the case begins. A malformed save (quests: null, the not-connected case)
     cannot be drawn; the economy still runs. */
  function draw() {
    const real = save;
    save = () => true;
    try { renderAll() } catch { /* see above */ } finally { save = real }
  }
  async function call(c) {
    const env = c.env || {}, undo = [];
    seed = 1; shBusy = false; lmaHealthBusy = false; answer = env.confirm !== false; typed = env.prompt === undefined ? null : env.prompt;
    if (env.failSave === 'storage') { Storage.prototype.setItem = function () { throw new Error('quota') }; undo.push(() => { Storage.prototype.setItem = realSetItem }) }
    if (env.failSave === 'readonly') { saveReadOnly = 'read-only'; saveReadOnlyTold = true; undo.push(() => { saveReadOnly = ''; saveReadOnlyTold = false }) }
    if ('health' in env) { window.lmaPlugin = n => (env.health && n === 'HealthSync' ? healthPlugin(env.health) : null); undo.push(() => { window.lmaPlugin = realPlugin }) }
    if (env.stored !== undefined) localStorage.setItem(STORE, JSON.stringify(env.stored));
    for (const [id, v] of Object.entries(env.dom || {})) {
      let el = document.getElementById(id);
      if (!el) { el = document.createElement('input'); el.id = id; el.type = typeof v === 'boolean' ? 'checkbox' : 'text'; el.hidden = true; document.body.appendChild(el); undo.push(() => el.remove()) }
      else { const was = { value: el.value, checked: el.checked }; undo.push(() => { el.value = was.value; el.checked = was.checked }) }
      if (typeof v === 'boolean') el.checked = v; else el.value = v;
    }
    /* the attribute sheet's actions run with the sheet open on the entry's attribute, as the owner reaches them */
    if (SHEET.includes(c.fn)) { shAttr = (actById((c.args || [])[0]) || {}).attr || null; undo.push(() => { shAttr = null }) }
    let out;
    try { const f = ADAPT[c.fn] || (0, eval)(c.fn); out = typeof f === 'function' ? await f(...(c.args || [])) : f } catch (e) { out = { threw: String((e && e.message) || e) } } finally { undo.reverse().forEach(f => f()) }
    return out === undefined ? null : clone(out);
  }
  const eco = {
    base() { fresh(false); draw(); return pick(fieldsAll) },
    setup(spec) {
      fresh(spec.hand, spec.verify);
      const s = clone(spec.state || {});
      if (s.customRules) { rules = Object.assign(structuredClone(DEFAULT_RULES), s.customRules); delete s.customRules }
      Object.assign(state, s); state.customRules = rules;
      if (!spec.stale && Array.isArray(state.activities) && state.activities.length) { rebuildAttrXP(); rebuildSkillXP() }
      draw();
      uuid = 0; toasts = []; fields = spec.fields; before = pick(fields);
      return before;
    },
    call,
    finish() {
      const after = pick(fields), changed = {}, deleted = [];
      for (const k of fields) if (JSON.stringify(after[k]) !== JSON.stringify(before[k])) { if (after[k] === undefined) deleted.push(k); else changed[k] = after[k] }
      return { state: changed, deleted, toasts };
    },
    async run(spec) { const b = eco.setup(spec), returns = []; for (const c of spec.calls) returns.push(await call(c)); return { before: b, returns, ...eco.finish() } },
    async pure(calls) { fresh(false); toasts = []; const returns = []; for (const c of calls) returns.push(await call(c)); return { returns, toasts } },
  };
  window.__eco = eco;
}

/* ---------- recording ---------- */
async function openPage(browser, file, zone) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: zone, locale: 'en-US' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: new Date(BOOT) });
  await p.addInitScript("localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));");
  await p.goto('file://' + file);
  await p.waitForFunction(() => typeof ecoAward === 'function' && typeof dhAutoVerify === 'function' && typeof LifeMaxHost === 'object' && typeof NativeLogProjection === 'object', null, { timeout: 120000 });
  await p.clock.pauseAt(new Date(T0)); // the boot's own timers have run; from here time moves only when a case moves it
  await p.evaluate(pageRunner, FIELDS.concat(EXTRA));
  return p;
}

async function record(p, c, base) {
  const now = c.now || T0;
  await p.clock.setSystemTime(new Date(now));
  if (c.fresh) return { id: c.id, rule: c.rule, input: { now, zone: c.zone, state: base, calls: [] }, output: { returns: [], state: {} } };
  if (c.pure) {
    const r = await p.evaluate(calls => window.__eco.pure(calls), c.calls);
    return { id: c.id, rule: c.rule, input: { calls: c.calls }, output: { returns: r.returns, ...(r.toasts.length ? { toasts: r.toasts } : {}) } };
  }
  const spec = { state: c.state, hand: !!c.hand, verify: !!c.verify, stale: !!c.stale, calls: c.calls, fields: FIELDS.concat(c.fields || []).filter(f => !(c.omit || []).includes(f)) };
  let r;
  if (!c.calls.some(x => x.at)) r = await p.evaluate(s => window.__eco.run(s), spec);
  else {
    const before = await p.evaluate(s => window.__eco.setup(s), spec), returns = [];
    for (const x of c.calls) { await p.clock.setSystemTime(new Date(x.at || now)); returns.push(await p.evaluate(x => window.__eco.call(x), x)) }
    r = { before, returns, ...await p.evaluate(() => window.__eco.finish()) };
  }
  const state = {};
  for (const [k, v] of Object.entries(r.before)) if (JSON.stringify(v) !== JSON.stringify(base[k])) state[k] = v;
  const output = { returns: r.returns, state: r.state };
  if (r.deleted.length) output.deleted = r.deleted;
  if (r.toasts.length) output.toasts = r.toasts;
  return { id: c.id, rule: c.rule, input: { now, zone: c.zone, ...(c.stale ? { stale: true } : {}), state, calls: c.calls }, output };
}

/* Records every case in the built page `file`; resolves to the list of cases. */
async function generate(file, log = () => { }) {
  const cases = defineCases(), browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const pages = {}, out = [], page = async zone => pages[zone] || (pages[zone] = await openPage(browser, file, zone));
    const utc = await page('UTC');
    await utc.clock.setSystemTime(new Date(T0));
    const base = await utc.evaluate(() => window.__eco.base()); // the fresh save every case's state is written against
    for (const c of cases) {
      out.push(JSON.parse(JSON.stringify(await record(await page(c.zone), c, base)))); // as the file will hold it
      if (out.length % 100 === 0) log(out.length + ' of ' + cases.length + ' cases recorded');
    }
    const errors = Object.values(pages).flatMap(p => p.errors);
    if (errors.length) throw new Error('the page threw while recording: ' + errors.slice(0, 3).join(' | '));
    return out;
  } finally { await browser.close() }
}

/* one case per line, so a change to the page shows up in a diff as the cases it changed */
const serialize = cases => '[\n' + cases.map(c => JSON.stringify(c)).join(',\n') + '\n]\n';

module.exports = { generate, defineCases, serialize, FILE, NOT_RECORDED };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file, m => console.log(m)).then(cases => {
    fs.writeFileSync(FILE, serialize(cases));
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + cases.length + ' cases');
  }).catch(e => { console.error(e); process.exit(1) });
}
