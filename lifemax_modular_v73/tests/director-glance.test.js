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

  /* 2. native.glance() against the page's own glance, drawn at the same moment */
  for (const [name, p] of Object.entries(pages)) {
    const r = await p.evaluate(() => {
      ovPlanGlance();
      const el = document.getElementById('ovPlan'), q = s => el.querySelector(s), tx = s => (q(s) || {}).textContent;
      const store = localStorage.getItem('lifemax_director_state_v1'), html = el.innerHTML;
      const g = LifeDirector.native.glance(); LifeDirector.native.glance();
      const t = q('.ld-core-t'), foot = q('.ld-gl-foot');
      return { g, pure: store === localStorage.getItem('lifemax_director_state_v1') && html === el.innerHTML,
        dom: { title: tx('.ld-gl-h h3'), link: tx('.ov-link'), intro: tx('.ld-gl-empty p') || tx('.ld-muted'), button: tx('.ld-gl-empty .ld-btn'),
          foot: foot ? { text: foot.firstChild.textContent, done: tx('.ld-gl-foot span') || '' } : null,
          name: t && t.textContent, full: t && (t.title || t.textContent), next: tx('.ld-core-nx span') || null, sessions: el.querySelectorAll('.ld-ses').length,
          clock: !!q('[data-fclock]'), kick: tx('.ld-core-k') } };
    });
    const { g, dom } = r;
    ok(name + ': native.glance() is a pure read (nothing saved, the page\'s glance not redrawn)', r.pure);
    ok(name + ': the head says what the page\'s says', g.title === dom.title && g.link === dom.link, [g.title, g.link, dom]);
    if (g.state !== 'day') {
      ok(name + ': an empty state with the page\'s words and no dial', g.intro === dom.intro && (g.button || undefined) === dom.button && !g.dial && !g.core && !g.foot, [g, dom]);
      continue;
    }
    ok(name + ': the foot says what the page\'s says', JSON.stringify(g.foot) === JSON.stringify(dom.foot), [g.foot, dom.foot]);
    ok(name + ': the middle\'s name is the page\'s, cleaned, with the full one kept', g.core.title === dom.name && g.core.fullTitle === dom.full, [g.core.title, g.core.fullTitle, dom.name, dom.full]);
    ok(name + ': the middle\'s kick is the page\'s', g.core.kick === dom.kick, [g.core.kick, dom.kick]);
    ok(name + ': the line under the middle is the page\'s ("Nothing else planned today" left out)', (g.core.next ? g.core.next.label : null) === dom.next, [g.core.next, dom.next]);
    ok(name + ': the running timer shows as the page shows it', !!g.core.timer === dom.clock, [g.core.timer, dom.clock]);
    ok(name + ': the dial\'s sessions are the page\'s, with no handles and no build', g.dial.arcs.filter(a => a.style === 'session').length === dom.sessions && !g.dial.handles.length && !g.dial.stops.length && g.dial.build === null,
      [g.dial.arcs.length, dom.sessions, g.dial.handles.length]);
  }

  /* 3. a tap on a native glance opens Life Director's Today as the page's glance does */
  {
    const p = pages.day;
    const ids = await p.evaluate(() => { const a = LifeDirector.native.glance().dial.arcs; return { inst: a.find(x => x.act === 'inst' && x.state === 'plan').id, meal: a.find(x => x.act === 'meal').id } });
    const refused = await p.evaluate(() => LifeDirector.native.fromGlance('doneQuick', 'x'));
    ok('an act the glance does not have is refused with words', refused.ok === false && typeof refused.message === 'string' && refused.message.length > 0 && await p.evaluate(() => lmaView() === 'dashboard'), refused);
    let reply = await p.evaluate(() => LifeDirector.native.fromGlance('', ''));
    await settle(p, 400);
    ok('an empty tap only opens Life Director\'s Today', reply.ok === true && await p.evaluate(() => lmaView() === 'life-director' && LifeDirector.native.showing() === 'today'), reply);
    await go(p, 'dashboard');
    reply = await p.evaluate(id => LifeDirector.native.fromGlance('inst', id), ids.inst);
    await settle(p, 400);
    ok('a session tapped on the glance opens its sheet on the page', reply.ok === true && await p.evaluate(() => lmaView() === 'life-director' && LifeDirector.native.showing() === 'sheet'), reply);
    await p.evaluate(() => document.querySelector('#view-life-director .ld-sheet.open .ld-sheet-bg').click());
    await go(p, 'dashboard');
    /* a native screen that draws Today's sheets: the tap becomes the intent it opens its own sheet from */
    await p.evaluate(() => LifeDirector.native.intendedBy(() => true));
    const intent = async (act, id) => { await p.evaluate(([a, i]) => LifeDirector.native.fromGlance(a, i), [act, id]); await settle(p, 200); const it = await p.evaluate(() => LifeDirector.native.intent()); await go(p, 'dashboard'); return it };
    let it = await intent('inst', ids.inst);
    ok('with a screen drawing the sheets, a session asks for its sheet', it && it.sheet === 'session' && it.target === ids.inst, it);
    it = await intent('meal', ids.meal);
    ok('with a screen drawing the sheets, a meal asks for its sheet', it && it.sheet === 'meal' && String(it.target) === String(ids.meal), it);
    it = await intent('glanceOpen', '');
    ok('the head opens Life Director and asks for no sheet', it === null, it);
    const t = pages.timer;
    await t.evaluate(() => LifeDirector.native.intendedBy(() => true));
    await t.evaluate(() => LifeDirector.native.fromGlance('focusOpen', ''));
    await settle(t, 200);
    it = await t.evaluate(() => LifeDirector.native.intent());
    ok('with a screen drawing the sheets, the running timer asks for its sheet', it && it.sheet === 'timer', it);
  }

  for (const p of Object.values(pages)) ok('no page errors', !p.errors.length, p.errors);
  await b.close();
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
