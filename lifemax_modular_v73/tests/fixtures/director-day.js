/* A lived-in Life Director for the native Director fixtures and tests, planned by the real engine: sleep 11 PM to 7 AM, work
   9 to 5 on weekdays with a 30-minute commute, meals, and three goals from the engine's archetypes (Build muscle, Learn
   Spanish, Read more). seed(page) installs it under Playwright's clock before the page loads ({ planned: false } leaves Life
   Director as a new player finds it); play(page) then lives Wednesday 28 October 2026 (UTC) up to mid-afternoon: the
   morning session is done, a dentist appointment is added by voice for the evening, and an evening session is skipped.
   The clock is left paused at 3:10 PM, in the middle of work. */
const DAY = '2026-10-28';
const at = (hm) => new Date(DAY + 'T' + hm + ':00Z');
const epoch = (hm) => at(hm).getTime() / 1000;

function state() {
  const since = epoch('06:30') - 20 * 86400;
  const goal = (id, title, archetypeId, priority, level, extra) => Object.assign({ id, title, archetypeId, priority, level, status: 'ACTIVE', createdEpoch: since }, extra);
  return {
    schemaVersion: 1, playerId: 'player', createdEpoch: since, updatedEpoch: since, setupComplete: true,
    availability: {
      timezone: 'UTC', sleepRequirementMinutes: 480, blackouts: [],
      rules: [
        { id: 'sleep', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4, 5, 6], startMin: 23 * 60, durationMin: 480 },
        { id: 'work', kind: 'WORK', title: 'Work', days: [1, 2, 3, 4, 5], startMin: 9 * 60, durationMin: 480, commuteMin: 30, load: 'COGNITIVE_HEAVY' },
      ],
    },
    goals: [
      goal('g-muscle', 'Build muscle', 'FITNESS_HYPERTROPHY', 4, 'INTERMEDIATE'),
      goal('g-spanish', 'Learn Spanish', 'SKILL_LANGUAGE', 3, 'NOVICE', { subject: 'spanish' }),
      goal('g-read', 'Read more', 'HABIT_READING', 2, 'NOVICE'),
    ],
    fixedCommitments: [], prefs: {}, schedule: null, ledger: null, changeLog: [], energyCheckin: null, questOverlay: {}, labQuests: [],
    bridge: { enabled: false, source: null, mapping: null }, lastGeneratedWeekKey: null,
    ui: { tab: 'today', tours: { setup: true, today: true, week: true } },
  };
}

/* set-up not finished, as My time's numbered steps show it: different sleep on Friday and Saturday nights, a rotating night shift and
   a class, no goals yet, and set-up's guide not seen (phase 4B's tests and fixtures) */
function unfinished() {
  const s = state();
  s.setupComplete = false; s.goals = []; s.ui.tab = 'setup'; delete s.ui.tours.setup;
  s.availability.rules = [
    { id: 'sleep-wk', kind: 'SLEEP', title: 'Sleep', days: [0, 1, 2, 3, 4], startMin: 22 * 60 + 45, durationMin: 495 },
    { id: 'sleep-we', kind: 'SLEEP', title: 'Sleep', days: [5, 6], startMin: 30, durationMin: 540 },
    { id: 'r-rot', kind: 'WORK', title: 'Nurse shift', days: [0, 1, 2, 3, 4, 5, 6], startMin: 19 * 60, durationMin: 720, commuteMin: 20, decompressMin: 45, load: 'PHYSICAL_HEAVY',
      rotation: { anchorDate: '2026-10-26', cycleLength: 7, activeDays: [0, 1, 2] } },
    { id: 'r-class', kind: 'CLASS', title: 'Pottery', days: [2, 4], startMin: 18 * 60, durationMin: 90, commuteMin: 0, decompressMin: 0, load: 'LIGHT', rotation: null }];
  return s;
}

async function seed(page, { planned = true, setup = 'done' } = {}) {
  // installed a minute early: the installed clock runs until it is paused, and on a loaded machine pausing at the very time it was
  // installed at can already be in its past
  await page.clock.install({ time: new Date(at('06:30').getTime() - 60000) });
  await page.clock.pauseAt(at('06:30'));
  await page.addInitScript((s) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({ onboardingComplete: true, name: 'N', tutorialDone: true, classId: 'athlete' }));
    if (s) localStorage.setItem('lifemax_director_state_v1', s);
  }, planned ? JSON.stringify(setup === 'unfinished' ? unfinished() : state()) : '');
}

async function play(page) {
  await page.clock.runFor(3000); // the page loads and Life Director plans the week
  await page.clock.pauseAt(at('08:05'));
  await page.evaluate(({ day, work, evening, dentist }) => {
    const V = LifeDirector.voice, sessions = () => V.sessions(day);
    const morning = sessions().find((s) => s.endEpoch <= work);
    if (!morning || !V.completeSession(morning.id).ok) throw Error('no morning session to complete');
    if (!V.add({ id: 'dentist', title: 'Dentist', startEpoch: dentist[0], endEpoch: dentist[1] }).ok) throw Error('the appointment was not planned');
    const late = sessions().find((s) => s.startEpoch >= evening);
    if (!late || !V.skipSession(late.id).ok) throw Error('no evening session to skip');
  }, { day: DAY, work: epoch('09:00'), evening: epoch('20:00'), dentist: [epoch('18:30'), epoch('19:15')] });
  await page.clock.pauseAt(at('15:10'));
}

module.exports = { DAY, at, epoch, state, unfinished, seed, play };
