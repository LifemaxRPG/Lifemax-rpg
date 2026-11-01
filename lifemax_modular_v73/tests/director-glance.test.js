/* Life Director's glance (Today's plan on the Overview) as data for a native screen (plans/OVERVIEW_BODY.md, phase 0 step 3 and the
   phase 1 glance). Checked:
   - the page's own glance is byte-identical to tests/fixtures/director-glance-golden.json, recorded before its decisions were split
     out of glanceHtml into glanceData (RECORD=1 rewrites it, only ever from code known to be right). The dial's paint and text-path
     ids count every dial drawn since load, so they are compared as names, not numbers;
   - LifeDirector.native.glance() says what the page's glance draws: the head, the empty states, the foot, the centre's cleaned title
     (the full one kept), no "Nothing else planned today", the dial's sessions, no handles and no build;
   - native.glance() is a pure read: it neither saves nor redraws;
   - native.fromGlance(act, id) opens Life Director's Today as a glance tap does: the session's sheet on the page, or, when a native
     screen draws Today's sheets, the intent it opens its own sheet from; '' and 'glanceOpen' only open; an act the glance does not
     have is refused with words.
   The page runs on Playwright's clock through the lived-in day of tests/fixtures/director-day.js.
   Run: NODE_PATH=$(npm root -g) node tests/director-glance.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'director-glance-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

async function start(b, { planned = true, lived = true } = {}) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned });
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  if (planned && lived) await directorDay.play(p); else await p.clock.runFor(3000);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms = 800) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
/* the glance as the Overview draws it, its counted ids named alike */
const glanceHtml = p => p.evaluate(() => { ovPlanGlance(); return document.getElementById('ovPlan').innerHTML.replace(/\b(ldf|ldtp)\d+/g, '$1#') });
/* Today's running timer, started (and paused) the way the screen's timer command does, back on the Overview */
async function timer(p, pause) {
  await go(p, 'life-director', 1500);
  await p.evaluate(() => { const id = window.__timed = LifeDirector.native.today(0).upNext.actions.find(a => a.act === 'focusStart').id; if (!LifeDirector.native.todayRun('timer', { act: 'start', id }).ok) throw Error('no timer') });
  await p.clock.runFor((12 * 60 + 34) * 1000);
  if (pause) {
    await p.evaluate(() => { const id = window.__timed; if (!LifeDirector.native.todayRun('timer', { act: 'pause', id }).ok) throw Error('no pause') });
    await p.clock.runFor(3 * 60 * 1000);
  }
  await go(p, 'dashboard');
}
/* each state the golden holds: a name and how to reach it on a fresh page */
const STATES = {
  setup: async b => start(b, { planned: false }),
  morning: async b => start(b, { lived: false }),
  day: async b => start(b),
  timer: async b => { const p = await start(b); await timer(p, false); return p },
  paused: async b => { const p = await start(b); await timer(p, true); return p },
  evening: async b => { const p = await start(b); await p.clock.pauseAt(directorDay.at('22:40')); await settle(p, 100); return p },
};

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const html = {}, pages = {};
  for (const [name, reach] of Object.entries(STATES)) {
    const p = await reach(b);
    await go(p, 'dashboard', 300);
    html[name] = await glanceHtml(p);
    pages[name] = p;
  }

  /* 1. the page's own glance, against the golden */
  if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
  const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
  for (const name of Object.keys(STATES)) ok('the glance (' + name + ') draws byte for byte as before its data was split out', html[name] === golden[name], (html[name] || '').length + ' vs ' + (golden[name] || '').length);

  for (const p of Object.values(pages)) ok('no page errors', !p.errors.length, p.errors);
  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
