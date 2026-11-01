/* Life Director's glance on the Overview (Today's plan), one decision two drawings (plans/OVERVIEW_BODY.md, phase 0 step 3):
   - the page's own glance is byte-identical to tests/fixtures/overview-glance-golden.json, recorded before its decisions were split
     out of glanceHtml into glanceData (RECORD=1 rewrites it, only ever from code known to be right);
   - LifeDirector.native.glance() says what the page's glance draws: the head, the empty state, the foot, the core's cleaned title
     and the dial's sessions;
   - LifeDirector.native.fromGlance(act, id) goes to Life Director's Today and runs the tap there (a session opens its sheet).
   States: a new player (set-up not done), and the lived-in day of tests/fixtures/director-day.js at several times (work in
   progress, a session about to start, a timer running, the evening). Run: NODE_PATH=$(npm root -g) node tests/overview-glance.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'overview-glance-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

async function start(b, planned) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned });
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { } });
  if (planned) await directorDay.play(p); else await p.clock.runFor(3000);
  return p;
}
/* the glance as the page draws it now; the dial's paint id counts renders, so it is read as a placeholder */
const glanceHtml = p => p.evaluate(() => { ovPlanGlance(); return document.getElementById('ovPlan').innerHTML.replace(/ldf\d+/g, 'ldfN') });
const text = s => String(s).replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/\s+/g, ' ').trim();

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const html = {}, data = {};
  try {
    let p = await start(b, false);
    html.setup = await glanceHtml(p);
    data.setup = await p.evaluate(() => LifeDirector.native.glance && LifeDirector.native.glance());
    await p.context().close();

    p = await start(b, true);
    const times = [['work', '15:10'], ['evening', '17:40'], ['late', '21:30']];
    for (const [name, hm] of times) {
      await p.clock.pauseAt(directorDay.at(hm));
      html[name] = await glanceHtml(p);
      data[name] = await p.evaluate(() => LifeDirector.native.glance && LifeDirector.native.glance());
    }
    // a timer running on the evening's next session (a fresh day, so the states before it saw none)
    await p.context().close();
    p = await start(b, true);
    await p.clock.pauseAt(directorDay.at('17:45'));
    const timer = await p.evaluate(() => {
      const t = LifeDirector.native.today(0), s = t.dial.arcs.find(a => a.act === 'inst' && a.state === 'plan' && a.s > Date.now() / 1000);
      return s ? LifeDirector.native.todayRun('timer', { act: 'start', id: s.id }) : { ok: false, message: 'no session' };
    });
    ok('a timer starts for the timer state', timer && timer.ok, timer);
    await p.clock.runFor(65000);
    html.timer = await glanceHtml(p);
    data.timer = await p.evaluate(() => LifeDirector.native.glance && LifeDirector.native.glance());
    { // the glance's clock ticks each second with Life Director's own, not only when the glance redraws (every 30 s)
      const read = () => p.evaluate(() => { const c = document.querySelector('.ld-glance [data-fclock]'); return c ? c.textContent : null });
      const before = await read(); await p.clock.runFor(3000); const after = await read();
      const secs = (t) => t ? t.split(':').reduce((a, n) => a * 60 + Number(n), 0) : NaN;
      ok("the glance's running clock ticks each second", before !== null && secs(after) - secs(before) === 3, { before, after });
    }

    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    for (const k of Object.keys(golden)) ok('the glance (' + k + ') is byte-identical to the golden', html[k] === golden[k], { k, got: html[k] && html[k].length, want: golden[k].length });

    if (data.setup) {
      const g = data.setup, h = html.setup;
      ok('glance data: set-up', g.state === 'setup' && h.includes('<h3>' + g.title + '</h3>') && h.includes('>' + g.link + '</button>') && h.includes('<p>' + g.intro + '</p>') && h.includes('>' + g.button + '</button>') && g.dial === null && g.core === null && g.foot === null, g);
      for (const k of ['work', 'evening', 'late', 'timer']) {
        const d = data[k], page = html[k];
        const foot = (page.match(/<div class="ld-gl-foot">(.*?)<\/div>/) || [])[1] || '';
        const coreTitle = text((page.match(/<b class="ld-core-t[^"]*"[^>]*>(.*?)<\/b>/) || [])[1] || '');
        const ses = (page.match(/data-act="inst" data-id="[^"]+"/g) || []).length; // sessions on the dial (and the core's own tap)
        const dialSes = d.dial.arcs.filter(a => a.act === 'inst').length;
        ok('glance data (' + k + ') says what the page draws', d.state === 'day' && text(foot).startsWith(d.foot.text) && (d.foot.done ? text(foot).endsWith(d.foot.done) : true)
          && d.core.title === coreTitle && ses >= dialSes && dialSes > 0 && !d.dial.handles && !d.dial.build
          && !(d.core.next && /nothing else planned/i.test(d.core.next.label)), { d: { foot: d.foot, core: d.core, dialSes }, foot, coreTitle, ses });
      }
      ok('glance data: the running timer is the core\'s tap', data.timer.core.timer && data.timer.core.timer.act === 'focusOpen' && /Open running timer/.test(html.timer), data.timer.core);
      ok('glance data: the core keeps the full title for TalkBack', typeof data.work.core.fullTitle === 'string' && data.work.core.fullTitle.length >= data.work.core.title.length, data.work.core);

      // a tap on a session: Life Director's Today, with that session's sheet
      const r = await p.evaluate(async () => {
        const id = LifeDirector.native.glance().dial.arcs.find(a => a.act === 'inst').id;
        const acted = LifeDirector.native.fromGlance('inst', id);
        return { acted, view: lmaView(), showing: LifeDirector.native.showing(), tab: LifeDirector.native.frame().tab };
      });
      ok('fromGlance opens the session sheet on Today', r.acted === true && r.view === 'life-director' && r.showing === 'sheet' && r.tab === 'today', r);
      const r2 = await p.evaluate(() => { navigate('dashboard'); return { acted: LifeDirector.native.fromGlance('', ''), view: lmaView() } });
      ok('fromGlance with no act only opens Life Director', r2.acted === true && r2.view === 'life-director', r2);
    } else ok('LifeDirector.native.glance exists', false);
    ok('no page errors', !p.errors.length, p.errors);
    await p.context().close();
  } finally { await b.close() }
  console.log(pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})();
