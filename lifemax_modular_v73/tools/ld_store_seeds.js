/* The Life Director store's seed texts for the store golden cases (WP-LD7, tools/gen_ld_store_golden.js): what the real page itself
   writes under lifemax_director_state_v1 (and its .bak) in Chromium, so that every case starts from a text the page made, not one typed
   here. capture(file) loads the built page under Playwright's clock (tests/fixtures/director-day.js holds the clock and the lived-in day)
   and reads the store's keys at fixed moments:
     fresh       a new player's state: no Life Director data, then Life Director's own reset (logic.js Do.reset, engine.js clear()).
     planned     the lived-in day's state as the page plans it at boot (setup done, three goals, the week planned).
     lived       the same at mid-afternoon after the day is played (a session done, an appointment added, a session skipped).
     lived-bak   the .bak the page left beside `lived` (the copy before its last save).
     lived-export  Life Director's backup text of `lived` (Do.exportBackup, engine.js exportJson).
     imported    the main key after that backup text is restored (Do.importBackup) on a page that held only a fresh state.
     unfinished  set-up not finished (director-day's unfinished()), after a guide is marked seen (the page saves).
     v0          a pre-release prototype state ({sleep, work, goals}) after the page has loaded it (migrate) and saved once.
   Each capture runs twice on fresh pages and must give the same texts (the recorder checks). */
'use strict';
const path = require('path');
const directorDay = require(path.join(__dirname, '..', 'tests', 'fixtures', 'director-day.js'));

const KEY = 'lifemax_director_state_v1', BAK = KEY + '.bak';
/* A prototype state as the pre-release Life Director stored it (engine.js:2266, "v0 = pre-release prototype shape"). */
const V0 = { playerId: 'player', timezone: 'UTC', sleep: { bed: 1380, wake: 420 }, work: [{ title: 'Work', days: [1, 2, 3, 4, 5], startMin: 540, durationMin: 480 }],
  goals: [{ id: 'g-read', title: 'Read more', archetypeId: 'HABIT_READING', priority: 2, level: 'NOVICE', status: 'ACTIVE', createdEpoch: 1790000000 }], prefs: { mealsEnabled: false } };

async function page(browser, file, seed, setup) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 860 }, timezoneId: 'UTC', locale: 'en-US', bypassCSP: true });
  const p = await ctx.newPage(), errors = [];
  p.on('pageerror', e => errors.push(String(e)));
  await seed(p);
  await p.goto('file://' + file);
  /* the clock is paused, so nothing that polls on a timer may be used to wait; the load event has fired and the scripts have run */
  if (!(await p.evaluate(() => typeof LifeDirectorApp !== 'undefined' && !!window.LifeDirectorStore))) throw new Error('the page has no Life Director');
  if (setup) await setup(p);
  const keys = await p.evaluate(([k, b]) => ({ main: localStorage.getItem(k), bak: localStorage.getItem(b) }), [KEY, BAK]);
  await ctx.close();
  if (errors.length) throw new Error('the page threw: ' + errors.slice(0, 3).join(' | '));
  return keys;
}

/* Under the director-day clock, with the usual test save; `text` (if any) installed as the store's main key before the page loads. */
const seeded = text => async p => {
  await directorDay.seed(p, { planned: false });
  if (text != null) await p.addInitScript(([k, t]) => localStorage.setItem(k, t), [KEY, text]);
};
const run = (name, payload) => p => p.evaluate(([n, x]) => {
  const r = LifeDirectorApp.native.run(n, x);
  if (!r || !r.ok) throw new Error(n + ' was refused: ' + JSON.stringify(r));
  return r;
}, [name, payload]);

async function captureOnce(browser, file) {
  const out = {};
  out.fresh = (await page(browser, file, seeded(null), run('reset', {}))).main;
  out.planned = (await page(browser, file, p => directorDay.seed(p), p => p.clock.runFor(3000))).main;
  const lived = await page(browser, file, p => directorDay.seed(p), async p => {
    await directorDay.play(p);
    out['lived-export'] = (await run('exportBackup', {})(p)).text;
  });
  out.lived = lived.main;
  out['lived-bak'] = lived.bak;
  out.imported = (await page(browser, file, seeded(out.fresh), run('importBackup', { text: out['lived-export'] }))).main;
  const tour = p => p.evaluate(() => { if (!LifeDirectorApp.native.tourSeen('today', true).ok) throw new Error('tourSeen refused'); });
  out.unfinished = (await page(browser, file, seeded(JSON.stringify(directorDay.unfinished())), tour)).main;
  out.v0 = (await page(browser, file, seeded(JSON.stringify(V0)), tour)).main;
  for (const [k, v] of Object.entries(out)) if (typeof v !== 'string') throw new Error('the page wrote no ' + k);
  return out;
}

/* The seeds, captured twice in Chromium (browser is a Playwright browser) and required equal. */
async function capture(browser, file) {
  const a = await captureOnce(browser, file), b = await captureOnce(browser, file);
  const differ = Object.keys(a).filter(k => a[k] !== b[k]);
  if (differ.length) throw new Error('the page wrote different seed texts on two runs: ' + differ.join(', '));
  return a;
}

module.exports = { capture, V0, KEY, BAK };
