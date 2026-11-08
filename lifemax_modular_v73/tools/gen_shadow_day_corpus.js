/* The economy check's day corpus (docs/specs/ECONOMY_SHADOW_PLAN.md WP6d): the real built page, with the check switched on, an
   app that says economy.shadow.quests.v1 and economy.shadow.day.v1 (tests/native-shadow-mock.js) and a HealthSync stand-in that
   answers with injected Health Connect data, is driven through the Health sync and the day roll, one scenario a page session,
   and every base part and envelope text the page sends is written to tests/fixtures/shadow-day-corpus.json as
   [{name, zone, messages}]. The Kotlin ShadowDayCorpusTest replays each scenario through native's decoder, mirror, runner and
   comparison and expects every action compared and the same, but for the interleaved sync it names.
   Scenarios:
   - the day with the app open (Europe/London, the established save, health sync on): a sync that pays a run, a yoga session,
     a strength session, days of steps, a night of sleep and a weight; the same sync again (nothing new); a sync whose steps
     read throws; a rest day; the day rolling over with the app open (the return to the app decays, saves and syncs again); two
     syncs at once (the second finds the first running);
   - a boot after several closed days (UTC): ten days without the app, so the boot's decay walks every idle attribute, banks
     rested XP and a streak shield covers a missed day; then a return to the app the same day;
   - the paused-clock zone matrix of tools/quests_golden_cases.js (daylight saving starting and ending, a missing local
     midnight, the date line, a leap day, a year's end): a boot after three closed days, a sync, the next day's return, and the
     clock moved back a day (info.clockJump).
   The page's clock is Playwright's and runs (the transport posts on a timer), so the file is regenerated on purpose:
     NODE_PATH=$(npm root -g) node tools/gen_shadow_day_corpus.js [built-page.html] */
'use strict';
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const mock = require('../tests/native-shadow-mock.js');

const FILE = path.join(__dirname, '..', 'tests', 'fixtures', 'shadow-day-corpus.json');
const CONTENT = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'quest-content.json'), 'utf8'));
const ESTABLISHED = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'tests', 'fixtures', 'saves', 'established.json'), 'utf8')).state;
const H = 3600000, D = 24 * H;
const CAPTURE = `(() => { const P = window.Capacitor.Plugins.NativeShadow; window.__corpus = [];
  for (const m of ['base', 'observe']) { const f = P[m]; P[m] = function (a) { window.__corpus.push({ m, text: a.text }); return f.apply(this, arguments) } } })();`;

/* the local calendar date of an instant in a zone, as the page's toLocaleDateString('en-CA') gives it */
const localDay = (ms, zone) => new Date(ms).toLocaleDateString('en-CA', { timeZone: zone });
const HEALTH_ON = since => ({ on: true, ex: true, steps: true, sleep: true, wt: true, seen: {}, last: 0, since, msg: '', sdk: '', granted: [], src: '', today: null });

/* Health Connect as the plugin answers it, the days before `now` in `zone`: a run, yoga and strength yesterday, days of steps
   (today's too), last night's sleep and a weight */
function healthData(now, zone) {
  const days = [3, 2, 1, 0].map(n => localDay(now - n * D, zone));
  return {
    status: { sdk: 'available', granted: ['exercise', 'steps', 'sleep', 'weight'] },
    exercise: [
      { id: 'w-run', start: now - 26 * H, end: now - 25 * H - 15 * 60000, type: 'running', title: '', origin: 'com.sec.android.app.shealth' },
      { id: 'w-yoga', start: now - 30 * H, end: now - 29 * H - 25 * 60000, type: 'yoga', title: 'Morning flow', origin: 'com.google.android.apps.fitness' },
      { id: 'w-lift', start: now - 50 * H, end: now - 49 * H, type: 'strength_training', title: '', origin: 'com.garmin.android.apps.connectmobile' },
    ],
    steps: days.map((date, i) => ({ date, count: [9400, 12800, 40200, 3100][i] })),
    sleep: [{ id: 'z-night', start: now - 14 * H, end: now - 6 * H - 20 * 60000, origin: 'com.sec.android.app.shealth' }],
    weight: { kg: 81.4, time: now - 5 * H, origin: 'com.sec.android.app.shealth' },
  };
}

/* a small save whose last training was `idle` days before `now`: a streak before it, decay applied the day it stopped, the
   shield tick at that day, one shield in stock */
function idleSave(now, zone, idle) {
  const dayNum = k => Math.round(Date.parse(k + 'T00:00:00Z') / D);
  const acts = [];
  for (let n = idle + 8; n >= idle; n--) {
    const at = new Date(now - n * D).toISOString();
    acts.push({ id: 'seed-' + n, type: n % 2 ? 'reading' : 'strength', name: 'Seed', icon: 'book', duration: 30, quality: 1, skill: 'Seed', primary: n % 2 ? 'intelligence' : 'physique', secondary: n % 3 ? '' : 'discipline', xp: 180, date: at, notes: '', xpSplit: [{ attr: n % 2 ? 'intelligence' : 'physique', xp: 180 }] });
  }
  const last = dayNum(localDay(now - idle * D, zone));
  const attrXP = { physique: 900, endurance: 0, intelligence: 900, business: 0, social: 0, discipline: 0, adventure: 0, wellbeing: 0 };
  return {
    schema: 1, xpSchemaV2: true, activities: acts, attrXP, skillXP: { Seed: 1620 }, restedXP: 12, sparks: 40,
    decayApplied: { physique: last, intelligence: last }, shieldTick: last, shieldMiss: 0, streakShields: 1, shieldMilestone: 1,
    restDays: { [localDay(now - (idle + 3) * D, zone)]: true }, health: HEALTH_ON(now - 2 * D),
  };
}

const visible = () => document.dispatchEvent(new Event('visibilitychange'));
const sync = () => lmaHealthSync(true);

/* [name, zone, start, save, health data, steps]; a step is [name, fn, {at, health}] */
function scenarios() {
  const out = [];
  const t0 = Date.parse('2026-11-08T12:00:00.000Z');
  const london = 'Europe/London';
  out.push(['the day with the app open', london, t0, Object.assign({}, ESTABLISHED, { health: HEALTH_ON(t0 - 2 * D) }), healthData(t0, london), [
    ['a sync that pays', sync],
    ['the same sync again', sync],
    ['a sync whose steps read throws', sync, { health: { fail: 'readSteps' } }],
    ['a rest day', () => ecoTakeRest(), { health: { fail: null } }],
    ['the day rolls over with the app open', visible, { at: t0 + 20 * H, wait: 800 }],
    ['two syncs at once', () => Promise.all([lmaHealthSync(true), lmaHealthSync(true)])],
  ]]);
  const t1 = Date.parse('2026-11-18T09:30:00.000Z');
  out.push(['a boot after several closed days', 'UTC', t1, idleSave(t1, 'UTC', 10), healthData(t1, 'UTC'), [
    ['the return to the app the same day', visible, { at: t1 + 2 * H, wait: 800 }],
  ]]);
  const ZONES = [
    ['America/New_York', '2026-11-01T05:30:00.000Z'], ['America/New_York', '2026-03-08T07:30:00.000Z'],
    ['Europe/London', '2026-03-29T00:30:00.000Z'], ['Australia/Sydney', '2026-04-04T14:30:00.000Z'],
    ['America/Santiago', '2026-09-06T04:30:00.000Z'], ['Pacific/Kiritimati', '2026-12-31T10:30:00.000Z'],
    ['Pacific/Pago_Pago', '2027-01-01T10:30:00.000Z'], ['Asia/Kolkata', '2028-02-29T18:00:00.000Z'],
    ['UTC', '2026-12-31T23:59:30.000Z'], ['America/Los_Angeles', '2026-02-01T07:30:00.000Z'], ['Asia/Tokyo', '2026-06-30T15:30:00.000Z'],
  ];
  for (const [zone, iso] of ZONES) {
    const t = Date.parse(iso);
    out.push([zone + ' ' + iso, zone, t, idleSave(t, zone, 3), healthData(t, zone), [
      ['a sync', sync],
      ['the next day', visible, { at: t + D, wait: 600 }],
      ['the clock moved back a day', visible, { at: t, wait: 600 }],
    ]]);
  }
  return out;
}

async function run(browser, file, [name, zone, start, save, health, steps]) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-GB', timezoneId: zone });
  try {
    const p = await ctx.newPage(), errors = [];
    p.on('pageerror', e => errors.push(String(e)));
    p.on('dialog', d => d.accept().catch(() => { }));
    await p.clock.install({ time: new Date(start) });
    await p.addInitScript('localStorage.setItem("lifemax_rpg_prototype_v1", ' + JSON.stringify(JSON.stringify(save)) + ');');
    await p.addInitScript('window.__health = ' + JSON.stringify(health) + ';');
    await p.addInitScript(mock.script({ fingerprint: CONTENT.contentHash, quests: true, day: true, health: true }));
    await p.addInitScript(CAPTURE);
    await p.goto('file://' + file);
    await p.waitForFunction(() => typeof window.LmaShadow === 'object' && LmaShadow.status().live, null, { timeout: 60000 });
    await p.waitForTimeout(1500);
    for (const [, step, opts] of steps) {
      if (opts && opts.health) await p.evaluate(h => Object.assign(window.__health, h), opts.health);
      if (opts && opts.at) { await p.clock.setSystemTime(new Date(opts.at)); await p.waitForTimeout(50) }
      await p.evaluate('(' + step.toString() + ')()');
      await p.waitForTimeout((opts && opts.wait) || 300);
    }
    await p.waitForTimeout(300);
    if (errors.length) throw new Error(name + ': the page threw: ' + errors.slice(0, 3).join(' | '));
    const refused = await p.evaluate(() => window.__shadowMock.refused);
    if (refused.length) throw new Error(name + ': the stand-in refused: ' + refused.join());
    return { name, zone, messages: await p.evaluate(() => window.__corpus) };
  } finally { await ctx.close() }
}

async function generate(file) {
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    const out = [];
    for (const s of scenarios()) out.push(await run(browser, file, s));
    return out;
  } finally { await browser.close() }
}

module.exports = { generate, FILE, scenarios };

if (require.main === module) {
  const file = require('../tests/current-page.js')(process.argv[2]);
  generate(file).then(list => {
    fs.writeFileSync(FILE, '[\n' + list.map(s => JSON.stringify(s)).join(',\n') + '\n]\n');
    console.log('wrote ' + path.relative(process.cwd(), FILE) + ': ' + list.map(s => s.name + ' ' + s.messages.filter(m => m.m === 'observe').length).join(', '));
  }).catch(e => { console.error(e); process.exit(1) });
}
