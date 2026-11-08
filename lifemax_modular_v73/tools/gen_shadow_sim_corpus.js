/* The economy check's proof corpus (docs/specs/ECONOMY_SHADOW_PLAN.md WP6f): the real built page, with the check switched on,
   an app that says every comparing capability (tests/native-shadow-mock.js) and a Health Connect stand-in that answers with
   injected data, is driven through seeded random days of economy actions, and every base part and envelope text the page sends is
   written down. The Kotlin ShadowSimCorpusTest replays each seed through native's decoder, mirror, runner and comparison and
   requires every action compared (or skipped for a reason the plan names) and none different without an allowlist id.
   Two corpora:
   - the seeds (tests/fixtures/shadow-sim-corpus.json.gz, 40 seeds): a seed is a pure function of its number (plan(seed) below):
     a starting save (one of the fixture saves, Health on or off, Sparks given or not), a zone and locale, a start instant, and two
     to four page sessions, each a boot after some closed days (and sometimes in another zone) followed by random actions over
     every compared kind a tap reaches: Logs (some refused by the storage, then taken back), spoken Logs, undos confirmed and
     declined, quests ticked and unticked, Daily Hand cards claimed and rerolled, rest days, the shop (themes, perks, toys, rest
     tokens, shields), ascensions, cat care, money, Health syncs with injected Health Connect data (some failing partway), the
     day rolling over with the app open, the clock moved back or forward, bare saves, the nutrition check, welcome chips, Life
     Director sessions, quests made, class switches, the decay settings and the rules reset;
   - the fixture days (tests/fixtures/shadow-fixture-days.json.gz, --fixtures): every save in tests/fixtures/saves with Health
     switched on, booted on a fixed morning and taken through one scripted day (FIXTURE_DAY), then booted again two days later.
   The page's clock is Playwright's and runs (the transport posts on a timer), so instants and minted ids differ from run to run:
   the files are regenerated on purpose, not compared byte for byte; the actions of a seed are the same on every run.
   - the defects (tests/fixtures/shadow-defect-corpus.json.gz, --defects): the defects of the plan's 5.1 point 3 whose golden cases
     call only pure rules, each recorded through a shadow point on a save that shows it (defectRuns), its page check required.
     NODE_PATH=$(npm root -g) node tools/gen_shadow_sim_corpus.js [built-page.html] [--fixtures|--defects] [--seeds N] [--seed N] [--out FILE]
   --seed N records that one seed only (into --out, by default the system temp folder), to rerun a seed the Kotlin test names:
     (then) ./gradlew :app:testDebugUnitTest --tests '*ShadowSimCorpusTest' -Dshadow.sim.file=FILE
   --plan N prints seed N's actions without a browser. */
'use strict';
const fs = require('fs'), os = require('os'), path = require('path'), zlib = require('zlib');
const mock = require('../tests/native-shadow-mock.js');

const FIX = path.join(__dirname, '..', 'tests', 'fixtures');
const FILE = path.join(FIX, 'shadow-sim-corpus.json.gz');
const FIXTURES_FILE = path.join(FIX, 'shadow-fixture-days.json.gz');
const DEFECTS_FILE = path.join(FIX, 'shadow-defect-corpus.json.gz');
const CONTENT = JSON.parse(fs.readFileSync(path.join(FIX, 'quest-content.json'), 'utf8'));
const SAVES = ['new', 'extras', 'legacy', 'established'];
const saveOf = name => JSON.parse(fs.readFileSync(path.join(FIX, 'saves', name + '.json'), 'utf8')).state;
const SEEDS = 40;
const H = 3600000, D = 24 * H;
const ZONES = ['Europe/London', 'America/New_York', 'UTC', 'Australia/Sydney', 'Asia/Kolkata', 'America/Santiago', 'America/Los_Angeles', 'Asia/Tokyo'];
const LOCALES = ['en-GB', 'en-US', 'fr-FR'];
const CAPTURE = `(() => { const P = window.Capacitor.Plugins.NativeShadow; window.__corpus = [];
  for (const m of ['base', 'observe']) { const f = P[m]; P[m] = function (a) { window.__corpus.push({ m, text: a.text }); return f.apply(this, arguments) } } })();`;

/* mulberry32: the seed's own sequence, the same on every run and machine */
function rng(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const HEALTH_ON = since => ({ on: true, ex: true, steps: true, sleep: true, wt: true, seen: {}, last: 0, since, msg: '', sdk: '', granted: [], src: '', today: null });
const localDay = (ms, zone) => new Date(ms).toLocaleDateString('en-CA', { timeZone: zone });

/* Health Connect as the plugin answers it at `now`: workouts, steps, a night and a weight, varied by `r` (a number in [0, 1)) */
function healthData(now, zone, r, n) {
  const pick = (list, k) => list[Math.floor(((r * 7919 * (k + 1)) % 1) * list.length)];
  const types = ['running', 'yoga', 'strength_training', 'walking', 'biking', 'swimming'];
  const apps = ['com.sec.android.app.shealth', 'com.google.android.apps.fitness', 'com.garmin.android.apps.connectmobile'];
  const id = k => 'w-' + n + '-' + k;
  return {
    status: { sdk: 'available', granted: ['exercise', 'steps', 'sleep', 'weight'] },
    exercise: [0, 1, 2].slice(0, 1 + Math.floor(r * 3)).map(k => ({
      id: id(k), start: now - (6 + 9 * k) * H, end: now - (6 + 9 * k) * H + (20 + Math.floor(r * 70) + 10 * k) * 60000,
      type: pick(types, k), title: k === 1 ? 'Morning flow' : '', origin: pick(apps, k),
    })),
    steps: [2, 1, 0].map(k => ({ date: localDay(now - k * D, zone), count: Math.floor(1500 + ((r * 104729 * (k + 3)) % 1) * 24000) })),
    sleep: r < 0.8 ? [{ id: 'z-' + n, start: now - 15 * H, end: now - 15 * H + (5 + Math.floor(r * 5)) * H, origin: pick(apps, 4) }] : [],
    weight: r < 0.5 ? { kg: 70 + Math.round(r * 300) / 10, time: now - 3 * H, origin: pick(apps, 5) } : {},
  };
}

/* What a tap does, run in the page (installed once per page session): each takes the seed's numbers and does one thing */
function simActions() {
  const at = (list, r) => list.length ? list[Math.min(list.length - 1, Math.floor(r * list.length))] : null;
  const field = (id, v) => { const el = document.getElementById(id) || Object.assign(document.body.appendChild(document.createElement('input')), { id }); if (typeof v === 'boolean') { el.type = 'checkbox'; el.checked = v } else el.value = v };
  const refusing = fn => {
    const real = Storage.prototype.setItem;
    Storage.prototype.setItem = function (k) { if (k === STORE) throw new Error('quota'); return real.apply(this, arguments) };
    try { return fn() } finally { Storage.prototype.setItem = real }
  };
  const TYPES = ['reading', 'business', 'strength', 'running', 'language', 'recovery', 'practical'];
  const ATTRS = ['physique', 'endurance', 'intelligence', 'business', 'social', 'discipline', 'adventure', 'wellbeing'];
  window.__sim = {
    log: a => {
      const own = a.r[3] < 0.3, d = { type: at(TYPES, a.r[0]), customName: '', duration: String(5 + Math.floor(a.r[1] * 115)), quality: at(['0.8', '1', '1.2', '1.5'], a.r[2]), skill: '', primary: own ? at(ATTRS, a.r[4]) : '', secondary: own && a.r[5] < 0.5 ? at(ATTRS, a.r[5] * 2) : '', notes: a.r[5] < 0.3 ? 'A private note' : '' };
      return a.refused ? refusing(() => recordActivity(d)) : recordActivity(d);
    },
    voice: a => lmvLogNow({ type: at(TYPES, a.r[0]), name: 'Spoken session', duration: 10 + Math.floor(a.r[1] * 50), day: getToday(), atMs: Date.now() - 60000 }),
    undo: a => { const x = at(state.activities, a.r[0]); if (x) deleteActivity(x.id) },
    toggle: a => { const list = state.quests.filter(q => !q.hand), q = at(list, a.r[0]); if (q) toggleQuest(q.id, !isQuestDone(q)) },
    claim: a => { const q = at(dhCards().filter(c => !isQuestDone(c)), a.r[0]); if (q) toggleQuest(q.id, true) },
    reroll: a => dhReroll(Math.floor(a.r[0] * 3)),
    rest: () => ecoTakeRest(),
    buy: a => {
      const kind = at(['theme', 'perk', 'toy', 'rest', 'shield'], a.r[0]);
      const ids = { theme: Object.keys(ECO_THEMES), perk: Object.keys(ECO_PERKS), toy: Object.keys(ECO_TOY_PRICE), rest: [''], shield: [''] }[kind];
      ecoBuy(kind, at(ids, a.r[1]));
    },
    ascend: a => ecoAscend(at(ATTRS, a.r[0])),
    care: a => ecoCare(at(['feed', 'play', 'pet'], a.r[0])),
    money: a => recordMoneyTransaction(at(['deposit', 'withdraw'], a.r[0]), 5 + Math.floor(a.r[1] * 200), 'note'),
    sync: () => lmaHealthSync(true),
    visible: () => document.dispatchEvent(new Event('visibilitychange')),
    save: () => save(),
    nutrition: () => ecoNutritionCheck(),
    chip: a => jcClaim(at(['pet', 'fresh'], a.r[0])),
    director: a => LifeMaxHost.awardActivity({ eventId: 'ev-sim-' + a.n, activityTypeTag: at(['deep_work', 'exercise', 'learning', 'admin'], a.r[0]), actualStartEpoch: Math.floor(Date.now() / 1000) - 3600, actualDurationMinutes: 10 + Math.floor(a.r[1] * 80), title: 'Planned block', lifecycleState: 'COMPLETED' }, { title: 'Planned block' }),
    create: a => { field('newQuestName', a.r[0] < 0.15 ? '  ' : 'My quest ' + a.n); field('newQuestCadence', at(['daily', 'weekly', 'epic'], a.r[1])); field('newQuestXP', String(Math.floor(a.r[2] * 90))); createQuest() },
    classSwitch: a => switchClassTo(at(['athlete', 'scholar'].filter(c => c !== state.classId), a.r[0]) || 'athlete'),
    decaySettings: a => { field('decayOn', a.r[0] < 0.7); field('decayGrace', String(1 + Math.floor(a.r[1] * 6))); field('decayRate', String(1 + Math.floor(a.r[2] * 4))); saveDecay() },
    rulesReset: () => resetRules(),
    render: () => renderQuests(),
    js: a => (0, eval)(a.code),
  };
}

/* the weights of the random actions; 'sync' only with Health on */
const MENU = [['log', 18], ['voice', 3], ['undo', 5], ['toggle', 10], ['claim', 5], ['reroll', 5], ['rest', 3], ['buy', 5], ['ascend', 2], ['care', 5],
  ['money', 3], ['sync', 7], ['dayRoll', 4], ['clockBack', 2], ['clockOn', 2], ['save', 3], ['nutrition', 1], ['chip', 1], ['director', 2], ['create', 2],
  ['classSwitch', 1], ['decaySettings', 1], ['rulesReset', 1]];

/** A seed's whole run, without a browser: the save, zone, sessions and every step's numbers. */
function plan(seed) {
  const r = rng(seed * 7919 + 17), pick = list => list[Math.floor(r() * list.length)];
  const saveName = seed % 8 === 0 ? 'established' : pick(SAVES.slice(0, 3));
  const health = r() < 0.7, sparks = r() < 0.6 ? Math.floor(r() * 3000) : null;
  let zone = pick(ZONES), start = Date.parse('2026-11-08T12:00:00.000Z') + Math.floor((r() - 0.7) * 300) * D + Math.floor(r() * 24) * H;
  const locale = pick(LOCALES), sessions = [], total = MENU.reduce((s, [op, w]) => s + (op === 'sync' && !health ? 0 : w), 0);
  const count = 2 + Math.floor(r() * 3);
  let n = 0;
  for (let k = 0; k < count; k++) {
    if (k > 0) { start += Math.floor(r() * 12) * D + Math.floor(r() * 20) * H; if (r() < 0.3) zone = pick(ZONES) }
    const steps = [], length = 6 + Math.floor(r() * 10);
    for (let i = 0; i < length; i++) {
      let x = r() * total, op = MENU[0][0];
      for (const [o, w] of MENU) { if (o === 'sync' && !health) continue; if (x < w) { op = o; break } x -= w }
      const step = { op, n: ++n, r: [r(), r(), r(), r(), r(), r()] };
      if (op === 'log' && r() < 0.06) step.refused = true;
      if (op === 'undo') step.confirm = r() < 0.75;
      if (op === 'sync') step.fail = r() < 0.12 ? pick(['readSteps', 'readExercise', 'status']) : null;
      if (op === 'dayRoll') step.hours = 12 + Math.floor(r() * 18);
      if (op === 'clockBack') step.hours = -(1 + Math.floor(r() * 26));
      if (op === 'clockOn') step.hours = 1 + Math.floor(r() * 5);
      steps.push(step);
    }
    sessions.push({ zone, start, steps });
  }
  return { seed, name: 'seed ' + seed, save: saveName, health, sparks, locale, sessions };
}

/* The scripted day each fixture save goes through */
const FIXTURE_DAY = [
  { op: 'sync' }, { op: 'log', r: [0.05, 0.25, 0.3, 0.9, 0, 0.9] }, { op: 'toggle', r: [0.1] }, { op: 'claim', r: [0.2] }, { op: 'reroll', r: [0.5] },
  { op: 'care', r: [0] }, { op: 'care', r: [0.5] }, { op: 'care', r: [0.9] }, { op: 'buy', r: [0.1, 0.1] }, { op: 'money', r: [0.1, 0.4] },
  { op: 'log', r: [0.45, 0.6, 0.6, 0.1, 0.2, 0.7], refused: true }, { op: 'log', r: [0.6, 0.4, 0.3, 0.9, 0, 0.1] }, { op: 'undo', r: [0.99], confirm: true },
  { op: 'director', r: [0.3, 0.5] }, { op: 'save' }, { op: 'rest' }, { op: 'dayRoll', hours: 17 }, { op: 'claim', r: [0.7] }, { op: 'log', r: [0.3, 0.5, 0.5, 0.9, 0, 0.9] },
];
function fixturePlans() {
  return SAVES.map((name, i) => ({
    seed: 1000 + i, name: 'fixture ' + name, save: name, health: true, sparks: null, locale: 'en-GB',
    sessions: [{ zone: 'Europe/London', start: Date.parse('2026-11-08T07:30:00.000Z'), steps: FIXTURE_DAY.map((s, k) => Object.assign({ n: k + 1, r: [0, 0, 0, 0, 0, 0] }, s)) },
      { zone: 'Europe/London', start: Date.parse('2026-11-10T08:15:00.000Z'), steps: [{ op: 'sync', n: 99 }, { op: 'log', n: 100, r: [0.2, 0.3, 0.3, 0.9, 0, 0.9] }] }],
  }));
}

/* Defects of ECONOMY_SHADOW_PLAN 5.1 point 3 whose golden cases call only pure rules (no shadow point), recorded here through the
   real page as envelopes (--defects, tests/fixtures/shadow-defect-corpus.json.gz). Each run's `check` runs in the page at its end
   and must be true: the page really showed the defect. ShadowDefectTest requires native to reproduce each one. */
function defectRuns() {
  const t = Date.parse('2026-11-08T09:00:00.000Z'), zone = 'Europe/London', day = ms => localDay(ms, zone);
  const dayNum = k => Math.round(Date.parse(k + 'T00:00:00Z') / D);
  const only = part => Object.assign(HEALTH_ON(t - 2 * D), { ex: part === 'ex', steps: part === 'steps', sleep: false, wt: false });
  const run = (i, defect, name, state, steps, check, extra) => Object.assign({ seed: 2000 + i, name: defect + ' ' + name, defect, save: 'established', state, health: false, sparks: null, locale: 'en-GB',
    sessions: [{ zone, start: t, steps: steps.map((s, k) => Object.assign({ n: k + 1, r: [0.1, 0.2, 0.3, 0.4, 0.5, 0.6] }, s)) }], check }, extra || {});
  const est = saveOf('established'), fresh = saveOf('new');
  const workout = { exercise: [{ id: 'w-d11', start: t - 5 * H, end: t - 4 * H, type: 'running', title: '', origin: 'com.sec.android.app.shealth' }], steps: [], sleep: [], weight: {} };
  const steps = { exercise: [], steps: [{ date: day(t - D), count: 14200 }, { date: day(t), count: 2500 }], sleep: [], weight: {} };
  const recs = [{ id: 'd33-neg', type: 'reading', name: 'Negative', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: 'discipline', xp: -40, date: new Date(t - H).toISOString(), notes: '', xpSplit: [{ attr: 'intelligence', xp: -40 }], sp: 1 },
    { id: 'd33-text', type: 'reading', name: 'Text', icon: 'book', duration: 30, quality: 1, skill: 'Knowledge', primary: 'intelligence', secondary: 'discipline', xp: '35', date: new Date(t - 2 * H).toISOString(), notes: '', xpSplit: [{ attr: 'intelligence', xp: 35 }], sp: 1 }];
  const undated = Object.assign({}, est.activities[est.activities.length - 1], { id: 'qd20-undated', xp: 900, primary: 'adventure', secondary: '', xpSplit: [{ attr: 'adventure', xp: 900 }] });
  delete undated.date;
  const health = h => ({ op: 'js', code: 'window.__health = Object.assign({ status: { sdk: "available", granted: ["exercise", "steps", "sleep", "weight"] } }, ' + JSON.stringify(h) + ')' });
  const healthToast = 'window.__shadowMock.envelopes.filter(e => e.kind === "health.sync").slice(-1)[0].toasts.join(" ")';
  return [
    run(1, 'D11', 'a workout on a starred attribute: the record keeps the starred XP, the sync summary the plain', Object.assign({}, est, { attrStars: { endurance: 3, wellbeing: 2 }, health: only('ex'), restedXP: 0 }),
      [health(workout), { op: 'sync' }],
      '(() => { const r = state.activities.find(a => a.hc === "xw-d11"); const m = /\\+(\\d+) XP/.exec(' + healthToast + '); return !!r && !!m && Number(m[1]) < r.xp })()'),
    run(2, 'D33', 'the day cap counts a negative and a text record as they are', Object.assign({}, est, { activities: est.activities.concat(recs) }),
      [{ op: 'log', r: [0.05, 0.3, 0.3, 0.9, 0, 0.9] }],
      'state.activities.some(a => a.id === "d33-neg") && state.activities.some(a => a.id === "d33-text")'),
    run(3, 'D34', 'daily steps spend rested XP', Object.assign({}, est, { restedXP: 200, decay: { on: false }, health: only('steps') }),
      [health(steps), { op: 'sync' }],
      'Number(state.restedXP) < 200 && state.activities.some(a => a.src === "health" && a.type === "steps")'),
    run(4, 'QD15', 'the next day\'s draw dealt by a redraw is not saved', JSON.parse(JSON.stringify(fresh)),
      [{ op: 'render' }, { op: 'clockOn', hours: 20 }, { op: 'render' }],
      '(() => { const s = JSON.parse(localStorage.getItem(STORE)); return state.dailyDraw.date === getToday() && (!s.dailyDraw || s.dailyDraw.date !== getToday()) })()', { visibleless: true }),
    run(5, 'QD20', 'a record without a date counts in the recent XP of the deal', Object.assign({}, est, { activities: est.activities.concat([undated]) }),
      [{ op: 'render' }, { op: 'clockOn', hours: 20 }, { op: 'render' }],
      'state.activities.some(a => a.id === "qd20-undated" && !a.date) && state.dailyDraw.date === getToday()', { visibleless: true }),
    run(6, 'D29', 'the boot leaves the shield bookkeeping unsaved when decay is off', Object.assign({}, fresh, { decay: { on: false }, shieldTick: dayNum(day(t)) - 5, streakShields: 1 }),
      [{ op: 'log', r: [0.05, 0.3, 0.3, 0.9, 0, 0.9], refused: true }],
      '(() => { const s = JSON.parse(localStorage.getItem(STORE)); return s.shieldTick !== state.shieldTick })()'),
  ];
}

/** The first save of a run, as the plan says. */
function startingSave(run) {
  const s = JSON.parse(JSON.stringify(run.state || saveOf(run.save)));
  if (run.health) s.health = HEALTH_ON(run.sessions[0].start - 2 * D);
  if (run.sparks !== null) s.sparks = run.sparks;
  return s;
}

async function session(browser, file, run, s, stored, k) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: run.locale, timezoneId: s.zone });
  try {
    const p = await ctx.newPage(), errors = [];
    let answer = true;
    p.on('pageerror', e => errors.push(String(e)));
    p.on('dialog', d => (answer ? d.accept() : d.dismiss()).catch(() => { }));
    await p.clock.install({ time: new Date(s.start) });
    await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(stored) + ');');
    await p.addInitScript('window.__health = ' + JSON.stringify(healthData(s.start, s.zone, 0.5, 's' + k)) + ';');
    await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash, quests: true, day: true, report: true, health: true }));
    await p.addInitScript(CAPTURE);
    await p.goto('file://' + file);
    await p.waitForFunction(() => typeof window.LmaShadow === 'object' && LmaShadow.status().live, null, { timeout: 60000 });
    await p.waitForTimeout(1500);
    await p.evaluate('(' + simActions.toString() + ')()');
    for (const step of s.steps) {
      answer = step.confirm !== false;
      let op = step.op, wait = 150;
      if (op === 'dayRoll' || op === 'clockBack' || op === 'clockOn') {
        const now = await p.evaluate(() => Date.now());
        await p.clock.setSystemTime(new Date(now + step.hours * H));
        await p.waitForTimeout(50);
        if (run.visibleless) { await p.waitForTimeout(wait); continue }
        op = 'visible'; wait = 800;
      }
      if (op === 'sync' && !run.defect) { // a defect run sets its own Health Connect answers
        const now = await p.evaluate(() => Date.now());
        const h = Object.assign(healthData(now, s.zone, step.r ? step.r[0] : 0.37, step.n), { fail: step.fail || null });
        await p.evaluate(x => { window.__health = x }, h);
        wait = 400;
      }
      await p.evaluate(([o, a]) => Promise.resolve(window.__sim[o](a)).then(() => null), [op, step]);
      await p.waitForTimeout(wait);
    }
    await p.waitForTimeout(400);
    const where = run.name + ' session ' + (k + 1);
    if (errors.length) throw new Error(where + ': the page threw: ' + errors.slice(0, 3).join(' | '));
    const refused = await p.evaluate(() => window.__shadowMock.refused);
    if (refused.length) throw new Error(where + ': the stand-in refused: ' + refused.join());
    if (run.check && k === run.sessions.length - 1 && !(await p.evaluate(run.check))) throw new Error(where + ': the page did not show ' + run.defect + ': ' +
      JSON.stringify(await p.evaluate(() => ({ envelopes: window.__shadowMock.envelopes.map(e => [e.kind, e.toasts]), last: state.activities.slice(-2), rested: state.restedXP, health: state.health }))).slice(0, 3000));
    const status = await p.evaluate(() => LmaShadow.status());
    if (!status.live || status.stats.faults) throw new Error(where + ': the page side stopped: ' + JSON.stringify(status.stopped) + ' faults ' + status.stats.faults);
    return { zone: s.zone, start: s.start, steps: s.steps.map(x => x.op), messages: await p.evaluate(() => window.__corpus), stored: await p.evaluate(() => localStorage.getItem(STORE)), costs: status.stats.costs };
  } finally { await ctx.close() }
}

/** Records one run (a seed or a fixture day): its sessions in order, the save carried from one to the next as the page stored it. */
async function record(browser, file, run) {
  let stored = JSON.stringify(startingSave(run));
  const sessions = [];
  for (let k = 0; k < run.sessions.length; k++) {
    const out = await session(browser, file, run, run.sessions[k], stored, k);
    stored = out.stored;
    delete out.stored;
    sessions.push(out);
  }
  return Object.assign({ seed: run.seed, name: run.name, save: run.save, sessions }, run.defect ? { defect: run.defect } : {});
}

async function generate(file, runs, log) {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const out = [];
    for (const run of runs) {
      const r = await record(browser, file, run);
      if (log) log(r.name + ' (' + run.save + '): ' + r.sessions.map(s => s.messages.filter(m => m.m === 'observe').length).join('+') + ' envelopes');
      out.push(r);
    }
    return out;
  } finally { await browser.close() }
}

const write = (out, list) => fs.writeFileSync(out, zlib.gzipSync(Buffer.from('[\n' + list.map(x => JSON.stringify(x)).join(',\n') + '\n]\n'), { level: 9 }));

module.exports = { generate, plan, fixturePlans, defectRuns, FILE, FIXTURES_FILE, DEFECTS_FILE, write };

if (require.main === module) {
  const args = process.argv.slice(2), flag = n => { const i = args.indexOf(n); return i < 0 ? null : args[i + 1] };
  if (flag('--plan') !== null) { console.log(JSON.stringify(plan(Number(flag('--plan'))), null, 1)); process.exit(0) }
  const one = flag('--seed'), seeds = Number(flag('--seeds')) || SEEDS, fixtures = args.includes('--fixtures'), defects = args.includes('--defects');
  const valued = new Set(['--seed', '--seeds', '--out', '--plan'].map(f => args.indexOf(f) + 1).filter(i => i > 0));
  const file = require('../tests/current-page.js')(args.find((a, i) => !a.startsWith('--') && !valued.has(i)));
  const runs = one !== null ? [plan(Number(one))] : fixtures ? fixturePlans() : defects ? defectRuns() : Array.from({ length: seeds }, (_, i) => plan(i + 1));
  const out = flag('--out') || (one !== null ? path.join(os.tmpdir(), 'shadow-sim-seed-' + one + '.json.gz') : fixtures ? FIXTURES_FILE : defects ? DEFECTS_FILE : FILE);
  generate(file, runs, console.log).then(list => {
    write(out, list);
    console.log('wrote ' + out + ': ' + list.length + ' runs, ' + list.reduce((s, r) => s + r.sessions.reduce((t, x) => t + x.messages.filter(m => m.m === 'observe').length, 0), 0) + ' envelopes');
  }).catch(e => { console.error(e); process.exit(1) });
}
