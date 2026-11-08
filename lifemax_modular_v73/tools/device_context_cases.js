/* The cases of the device-context golden file (see gen_device_context_golden.js): the clock and time zone matrix of the quest goldens
   (daylight saving, a midnight that does not exist, UTC+14 and UTC-11 at the new year, a leap day, month ends), manual clock and zone
   changes between calls, record dates in every form the page parses, and the Daily Hand's outside facts (water, cat, plan, luck, rest
   days) in the shapes dhVerifyMet reads them. */
'use strict';

/* frozen clocks: [zone, now, what is special] */
const CLOCKS = [
  ['UTC', '2026-11-08T12:00:00.000Z', 'a Sunday'],
  ['America/New_York', '2026-11-01T05:30:00.000Z', 'the day daylight saving ends, 01:30 before the repeat'],
  ['America/New_York', '2026-03-08T07:30:00.000Z', 'the day daylight saving starts, 03:30'],
  ['Europe/London', '2026-03-29T00:30:00.000Z', 'just before the clocks go forward'],
  ['Europe/Berlin', '2026-10-25T00:30:00.000Z', 'the night the clocks go back, 02:30 before the repeat'],
  ['Australia/Sydney', '2026-04-04T14:30:00.000Z', '01:30 on the day daylight saving ends'],
  ['Australia/Lord_Howe', '2026-04-04T15:15:00.000Z', 'a half-hour daylight saving change'],
  ['America/Santiago', '2026-09-06T04:30:00.000Z', 'a day whose local midnight does not exist'],
  ['America/Havana', '2026-03-08T05:30:00.000Z', 'daylight saving that starts at midnight'],
  ['Pacific/Kiritimati', '2026-12-31T10:30:00.000Z', 'already 2027-01-01 at UTC+14'],
  ['Pacific/Pago_Pago', '2027-01-01T10:30:00.000Z', 'still 2026-12-31 at UTC-11'],
  ['Pacific/Apia', '2011-12-30T10:30:00.000Z', 'the day that never happened (30 December 2011)'],
  ['Asia/Kolkata', '2028-02-29T18:00:00.000Z', '23:30 on a leap day'],
  ['Asia/Kathmandu', '2026-12-31T18:30:00.000Z', 'UTC+5:45, 00:15 on the new year'],
  ['UTC', '2028-02-28T23:59:30.000Z', 'thirty seconds before a leap day'],
  ['UTC', '2027-02-28T23:59:30.000Z', 'thirty seconds before 1 March (not a leap year)'],
  ['UTC', '2026-12-31T23:59:30.000Z', 'thirty seconds before the new year'],
  ['UTC', '2026-04-30T23:30:00.000Z', 'the last day of April'],
  ['America/Los_Angeles', '2026-02-01T07:30:00.000Z', '23:30 on the last day of January'],
  ['Asia/Tokyo', '2026-06-30T15:30:00.000Z', '00:30 on the first of July'],
  ['America/Sao_Paulo', '2026-10-28T02:30:00.000Z', 'UTC-3, 23:30 the evening before'],
];
const MIN = 60000;
const plus = (iso, min) => new Date(Date.parse(iso) + min * MIN).toISOString();
const slug = z => z.toLowerCase().replace(/[^a-z]+/g, '-');

/* record dates as the page parses them: the first group is what the app writes (toISOString) */
const DATES = ['{T-30h}', '{T-12h}', '{T-1h}', '{T+0h}', '{T+1h}', '{T+12h}', '{T+30h}', '{M-12h}', '{M+0h}', '{M+12h}'];
const FORMS = ['2026-10-28', '2026-10-28T23:30:00', '2026-10-28T00:00:00', '2026-03-08T02:30:00', '2026-11-01T01:30:00',
  '2026-10-28T10:00:00Z', '2026-10-28T10:00:00+02:00', '2026-10-28T23:59:59.999-11:00'];
/* forms the economy reads differently (or not at all): recorded to pin the difference, not to hide it */
const ODD = ['not a date', '', null, '<absent>', '2026-02-30T10:00:00.000Z', '2026-10-28 10:00', '2026-10-28T10:00', '10/28/2026', '2026-10-28T10:00:00.12345Z'];

const CASES = [];
const add = (family, id, rule, c) => CASES.push({ id: family + '/' + id, family, rule, ...c });
const probe = (extra, at, zone) => ({ ...(at ? { at } : {}), ...(zone ? { zone } : {}), probe: { dates: extra } });

/* ---------- clock: every frozen clock, a day either side and the minutes around it ---------- */
CLOCKS.forEach(([zone, now], i) => {
  const calls = [-1500, -90, -30, 0, 30, 90, 1500].map(m => probe(DATES.concat(FORMS), plus(now, m)));
  add('clock', slug(zone) + '_' + i, 'TIME-1 TIME-2 TIME-3 TIME-7 TIME-8', { zone, now, calls });
});

/* ---------- clockmove: the owner sets the clock back and forward by hand ---------- */
const moves = [
  ['forward-over-month-end', 'UTC', '2026-01-30T12:00:00.000Z', [0, 2 * 1440, 3 * 1440, 40 * 1440]],
  ['back-over-month-end', 'UTC', '2026-03-02T12:00:00.000Z', [0, -2 * 1440, -3 * 1440, -40 * 1440, 0]],
  ['back-over-new-year', 'Pacific/Kiritimati', '2027-01-01T11:00:00.000Z', [0, -1440, -2 * 1440, 0, 1440]],
  ['over-daylight-saving', 'America/New_York', '2026-11-01T08:00:00.000Z', [0, -600, -1200, 600, 1200, 1500]],
  ['a-year-back', 'Europe/London', '2026-10-28T12:00:00.000Z', [0, -365 * 1440, -366 * 1440, 365 * 1440, 0]],
  ['a-minute-either-side-of-midnight', 'Asia/Tokyo', '2026-06-30T14:59:30.000Z', [0, 1, 2, 0, -1, -2]],
];
for (const [name, zone, now, offsets] of moves)
  add('clockmove', name, 'TIME-2 TIME-3 TIME-5', { zone, now, calls: offsets.map(m => probe(DATES, plus(now, m))) });

/* ---------- zonemove: the owner travels, the clock stays still ---------- */
const trips = [
  ['new-york-to-tokyo', 'America/New_York', '2026-10-28T20:00:00.000Z', ['Asia/Tokyo', 'America/New_York', 'Pacific/Kiritimati', 'Pacific/Pago_Pago', 'UTC']],
  ['over-the-date-line', 'Pacific/Pago_Pago', '2026-12-31T11:00:00.000Z', ['Pacific/Kiritimati', 'Pacific/Pago_Pago']],
  ['into-a-missing-midnight', 'America/Santiago', '2026-09-06T03:30:00.000Z', ['America/Santiago', 'UTC', 'America/Santiago']],
];
for (const [name, zone, now, zones] of trips)
  add('zonemove', name, 'TIME-1 TIME-2 TIME-3', { zone, now, calls: [probe(DATES, now, zone)].concat(zones.map(z => probe(DATES, now, z))) });

/* ---------- exotic: record dates the economy does not read the way the page does ---------- */
for (const [zone, now] of [CLOCKS[0], CLOCKS[2], CLOCKS[9], CLOCKS[10]])
  add('exotic', slug(zone), 'TIME-4', { zone, now, calls: [probe(ODD, now)] });

/* ---------- facts ---------- */
const NOW = '2026-11-04T12:00:00.000Z'; // a Wednesday
const planState = (extra = {}) => ({ plan: { id: 'athlete_beginner8', classId: 'athlete', start: '{W}', from: '{W}', checks: {}, ...extra } });
const WATER = ['water:25', 'water:50', 'water:75', 'water:100'];
const PET = ['pet:feed', 'pet:pet', 'pet:play'];
const VERIFY = WATER.concat(PET, ['plan', 'self', 'act:running:10', 'any:5']);
const DAYS = ['{D-1}', '{D+0}', '{D+1}', '{D+4}', '{D-20}'];
const facts = (id, rule, state, extra = {}) =>
  add('facts', id, rule, { zone: 'UTC', now: NOW, state: { activities: [], ...state }, tick: extra.tick, calls: [{ facts: { verify: VERIFY, rest: DAYS, plan: DAYS } }].concat(extra.more || []) });
const ml = (n, day = '{D+0}') => ({ date: day, ml: n });

facts('empty', 'SAVE-2', {});
facts('water-none', 'SAVE-2', { waterLog: [] });
facts('water-default-goal', 'SAVE-2', { waterLog: [ml(500), ml(500), ml(250), ml(250)] });
facts('water-half-exact', 'SAVE-2', { waterLog: [ml(1000)] });
facts('water-just-under-half', 'SAVE-2', { waterLog: [ml(999)] });
facts('water-full-exact', 'SAVE-2', { waterLog: [ml(2000)] });
facts('water-just-under-full', 'SAVE-2', { waterLog: [ml(1999)] });
facts('water-over', 'SAVE-2', { waterLog: [ml(5000)] });
facts('water-goal-3000', 'SAVE-2', { mealsMeta: { waterGoalMl: 3000 }, waterLog: [ml(1500), ml(750)] });
facts('water-goal-3000-quarter-exact', 'SAVE-2', { mealsMeta: { waterGoalMl: 3000 }, waterLog: [ml(750)] });
facts('water-goal-string', 'SAVE-2', { mealsMeta: { waterGoalMl: '2500' }, waterLog: [ml(1250)] });
facts('water-goal-zero', 'SAVE-2', { mealsMeta: { waterGoalMl: 0 }, waterLog: [ml(1000)] });
facts('water-goal-negative', 'SAVE-2', { mealsMeta: { waterGoalMl: -5 }, waterLog: [ml(1)] });
facts('water-goal-junk', 'SAVE-2', { mealsMeta: { waterGoalMl: 'lots' }, waterLog: [ml(1000)] });
facts('water-goal-fraction', 'SAVE-2', { mealsMeta: { waterGoalMl: 333.5 }, waterLog: [ml(250.125)] });
facts('water-meta-null', 'SAVE-2', { mealsMeta: null, waterLog: [ml(1000)] });
facts('water-other-days', 'SAVE-2', { waterLog: [ml(3000, '{D-1}'), ml(3000, '{D+1}'), ml(400)] });
facts('water-string-ml', 'SAVE-2', { waterLog: [ml('1000'), ml('x'), ml(null), ml(true)] });
facts('water-negative-ml', 'SAVE-2', { waterLog: [ml(2000), ml(-1500)] });
facts('water-fractions', 'SAVE-2', { waterLog: [ml(333.3), ml(333.3), ml(333.4)] });
facts('water-date-with-time', 'SAVE-2', { waterLog: [{ date: '{D+0}T08:00', ml: 2000 }] });
facts('pet-fed', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 2, petsToday: 0, playsToday: 0 } });
facts('pet-petted', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 0, petsToday: 1, playsToday: 0 } });
facts('pet-played', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 0, petsToday: 0, playsToday: 3 } });
facts('pet-all', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 1, petsToday: 1, playsToday: 1 } });
facts('pet-none-today', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 0, petsToday: 0, playsToday: 0 } });
facts('pet-yesterday', 'SAVE-2', { pet: { alive: true, day: '{D-1}', fedToday: 4, petsToday: 4, playsToday: 4 } });
facts('pet-dead', 'SAVE-2', { pet: { alive: false, day: '{D+0}', fedToday: 4, petsToday: 4, playsToday: 4 } });
facts('pet-alive-truthy', 'SAVE-2', { pet: { alive: 1, day: '{D+0}', fedToday: '2', petsToday: null, playsToday: true } });
facts('pet-counts-junk', 'SAVE-2', { pet: { alive: true, day: '{D+0}', fedToday: 'abc', petsToday: -1, playsToday: [] } });
facts('pet-missing', 'SAVE-2', {});
facts('pet-null', 'SAVE-2', { pet: null });
facts('plan-none', 'SAVE-2 REST-DAY-1', {});
facts('plan-today-done', 'SAVE-2 REST-DAY-1', planState(), { tick: [{ day: '{D+0}', mode: 'all' }] });
facts('plan-today-partial', 'SAVE-2 REST-DAY-1', planState(), { tick: [{ day: '{D+0}', mode: 'allButOne' }] });
facts('plan-yesterday-done', 'SAVE-2 REST-DAY-1', planState(), { tick: [{ day: '{D-1}', mode: 'all' }] });
facts('plan-untouched', 'SAVE-2 REST-DAY-1', planState());
facts('plan-starts-tomorrow', 'SAVE-2 REST-DAY-1', planState({ from: '{D+1}' }), { tick: [{ day: '{D+1}', mode: 'all' }] });
facts('plan-foundation-done', 'SAVE-2 REST-DAY-1', planState({ id: 'athlete_foundation8' }), { tick: [{ day: '{D+0}', mode: 'all' }, { day: '{D+4}', mode: 'all' }] });
facts('plan-ended', 'SAVE-2 REST-DAY-1', { plan: null });
facts('luck-today', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3, 4], score: 6200, ver: 2 } });
facts('luck-yesterday', 'QST-34', { luck: { date: '{D-1}', dice: [1, 2, 3, 4], score: 6200, ver: 2 } });
facts('luck-old-version', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3, 4], score: 6200, ver: 1 } });
facts('luck-three-dice', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3], score: 6200, ver: 2 } });
facts('luck-zero-score', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3, 4], score: 0, ver: 2 } });
facts('luck-fraction-score', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3, 4], score: 4999.5, ver: 2 } });
facts('luck-text-score', 'QST-34', { luck: { date: '{D+0}', dice: [1, 2, 3, 4], score: '7000', ver: 2 } });
facts('luck-text-dice', 'QST-34', { luck: { date: '{D+0}', dice: 'abcd', score: 6200, ver: 2 } });
facts('luck-no-dice', 'QST-34', { luck: { date: '{D+0}', score: 6200, ver: 2 } });
facts('luck-null', 'QST-34', { luck: null });
facts('rest-taken-today', 'REST-DAY-1', { restDays: { '{D+0}': true } });
facts('rest-taken-yesterday', 'REST-DAY-1', { restDays: { '{D-1}': true, '{D+1}': false } });
facts('rest-truthy-value', 'REST-DAY-1', { restDays: { '{D+0}': 1, '{D-1}': 0, '{D+1}': 'yes' } });
facts('rest-recovery-25', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'recovery', duration: 25, date: '{I+0 09:00:00}', xp: 10 }] });
facts('rest-recovery-19', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'recovery', duration: 19, date: '{I+0 09:00:00}', xp: 10 }] });
facts('rest-recovery-exactly-20', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'recovery', duration: 20, date: '{I-1 22:00:00}', xp: 10 }] });
facts('rest-recovery-passive', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'recovery', duration: 60, date: '{I+0 09:00:00}', xp: 10, src: 'health' }] });
facts('rest-recovery-late-night', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'recovery', duration: 30, date: '{I+0 23:59:59}', xp: 10 }] });
facts('rest-running-only', 'REST-DAY-1', { activities: [{ id: 'a1', type: 'running', duration: 60, date: '{I+0 09:00:00}', xp: 10 }] });
facts('rest-plan-rest-day', 'REST-DAY-1', planState());
facts('acts-logged', 'SAVE-2', { activities: [{ id: 'a1', type: 'running', duration: 12, date: '{I+0 07:00:00}', xp: 10 }, { id: 'a2', type: 'quest', duration: 99, date: '{I+0 08:00:00}', xp: 10 }] });

/* ---------- factsodd: checks the economy reads differently from the page (pinned as known differences) ---------- */
add('factsodd', 'water-without-percent', 'SAVE-2', { zone: 'UTC', now: NOW, state: { activities: [], waterLog: [ml(100)] }, calls: [{ facts: { verify: ['water:', 'water:0', 'water:abc'], rest: [], plan: [] } }] });
add('factsodd', 'pet-unknown-care', 'SAVE-2', { zone: 'UTC', now: NOW, state: { activities: [], pet: { alive: true, day: '{D+0}', fedToday: 0, petsToday: 0, playsToday: 2 } }, calls: [{ facts: { verify: ['pet:groom', 'pet:', 'pet'], rest: [], plan: [] } }] });
add('factsodd', 'water-null-entry', 'SAVE-2', { zone: 'UTC', now: NOW, state: { activities: [], waterLog: [null, ml(2000)] }, calls: [{ facts: { verify: ['water:100'], rest: [], plan: [] } }] });

function defineCases() { return CASES.map(c => ({ ...c })) }
module.exports = { defineCases, CLOCKS };
