/* Native Life Director, phase 4A, web half: Today's guide as data for a native screen that draws it, and the first build after
   set-up on that screen.
   Checked:
   - the page's own Today guide pins the same name tags on its dial as before the tags were split out of tourLabels
     (tests/fixtures/director-guide-golden.json; RECORD=1 rewrites it, only ever from code known to be right);
   - an APK that draws Today's sheets (director.sheets.v1) gets Today's guide in the projection: the page's own steps, words and
     buttons, a part to point at for each step, the dial's mode, spotlight shape and the very name tags the page pins; the page
     starts none of its own and the screen keeps covering Today; tourSeen records it;
   - an APK without director.sheets.v1 sees the page play Today's guide itself, as before;
   - the first build after the page's set-up: the screen covers Today and gets the build to play, the page's own build stops as the
     screen comes over it, and the page starts no guide after it (the screen plays Today's guide once its build is over).
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through the lived-in day of
   tests/fixtures/director-day.js. Run: NODE_PATH=$(npm root -g) node tests/native-director-guide.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'director-guide-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const SHEETS = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1'];

const FAKES = caps => `
  window.__calls = [];
  window.__since = n => window.__calls.slice(n).filter(c => c[0] === 'director');
  const mk = (name, caps) => { const ls = {}; return {
    __emit: (e, d) => (ls[e] || []).map(f => f(d)),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: async a => { window.__calls.push([name, 'open', a]) },
    update: async a => { window.__calls.push([name, 'update', a]) },
    close: async a => { window.__calls.push([name, 'close', a]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => { window.__calls.push([name, 'handshake']); return { protocol: 1, capabilities: caps } } } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => n === 'NativeLifeDirector' || n === 'NativeOverview', getPlatform: () => 'android',
    Plugins: { NativeLifeDirector: mk('director', ${JSON.stringify(caps)}), NativeOverview: mk('overview', []) }, registerPlugin: () => ({}), convertFileSrc: u => u };`;

/* Life Director with Today's guide not seen yet; { setup: true } leaves it at the end of set-up, the week not built yet */
async function start(b, caps, { switchOn = true, setup = false } = {}) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: true });
  await p.addInitScript(setup => {
    const k = 'lifemax_director_state_v1', s = JSON.parse(localStorage.getItem(k));
    if (!s || s.__guideTest) return; // once: a reload keeps what the page saved since
    delete s.ui.tours.today; s.__guideTest = true;
    if (setup) { s.setupComplete = false; s.ui.tab = 'setup' }
    localStorage.setItem(k, JSON.stringify(s));
  }, setup);
  await p.addInitScript(FAKES(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  if (switchOn) await p.evaluate(() => setNativeDirectorEnabled(true));
  if (setup) { await p.clock.runFor(3000); await p.clock.pauseAt(directorDay.at('15:10')) } else await directorDay.play(p);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
async function command(p, command, payload) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, 400);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null } }, n);
}
const lastOpen = p => p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o.length ? o[o.length - 1][2].snapshot : null });
/* the name tags of one step as the page drew them in SVG: centre, words and colour, in dial units */
function pills(svg) {
  const out = [], re = /<g class="ld-tl-p(?: ld-c-(\w+))?"><rect x="([\d.-]+)" y="([\d.-]+)" width="([\d.-]+)" height="24" rx="12"(?:\/>|><\/rect>)<text x="([\d.-]+)" y="([\d.-]+)">([^<]*)<\/text><\/g>/g;
  let m; while ((m = re.exec(svg || ''))) out.push({ x: +m[5], y: +m[3] + 12, text: m[7].replace(/&amp;/g, '&'), kind: m[1] || '' });
  return out;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  let golden = null;

  /* 1. the page's own Today guide (the switch off): its steps and the name tags it pins, against the golden */
  {
    const p = await start(b, SHEETS, { switchOn: false });
    await go(p, 'life-director', 1500);
    const steps = [];
    for (let i = 0; i < 8; i++) {
      const s = await p.evaluate(() => {
        const ov = document.querySelector('#view-life-director .ld-tour.on'); if (!ov) return null;
        const dial = document.querySelector('#view-life-director .ld-dial'), tl = dial && dial.querySelector('svg .ld-tl');
        return { n: ov.querySelector('.ld-tour-n').textContent, title: ov.querySelector('h3').textContent, body: ov.querySelector('.ld-tour-body').innerHTML,
          next: ov.querySelector('[data-act=tourNext]').textContent, mode: dial ? dial.dataset.tour || '' : '', tags: tl ? tl.innerHTML : '' };
      });
      if (!s) break;
      steps.push(s);
      await p.evaluate(() => document.querySelector('#view-life-director [data-act=tourNext]').click());
      await settle(p, 400);
    }
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify({ today: steps }, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8')).today;
    ok('the page plays Today\'s guide by itself on its first visit', steps.length >= 6 && steps[0].n === '1 of ' + steps.length, steps.map(s => s.n));
    ok('the page pins the same name tags on its dial, step by step, as before they were split out of tourLabels', same(steps.map(s => [s.mode, s.tags]), golden.map(s => [s.mode, s.tags])),
      steps.map(s => s.tags.length) + ' vs ' + golden.map(s => s.tags.length));
    ok('...with tags on the clock, outer, inner and handles steps', ['clock', 'outer', 'inner', 'handles'].every(m => pills((steps.find(s => s.mode === m) || {}).tags).length > 0), steps.map(s => [s.mode, pills(s.tags).length]));
    ok('no script errors on the page\'s own guide', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. an APK that draws Today's sheets draws Today's guide */
  {
    const p = await start(b, SHEETS);
    await go(p, 'life-director', 1500);
    await settle(p, 900); // longer than the page's own guide waits before it starts (500 ms)
    const s = await lastOpen(p);
    const g = s && s.guide;
    const page = await p.evaluate(() => ({ tour: !!document.querySelector('#view-life-director .ld-tour.on'), shows: LifeDirector.native.showing() }));
    ok('the screen covers Today while its guide is due: the page starts none of its own', !!s && s.tab === 'today' && !page.tour && page.shows === 'today', { page, tab: s && s.tab });
    ok('the projection carries Today\'s guide, due to start', !!g && g.key === 'today' && g.auto === true && g.steps.length === golden.length, g && { key: g.key, auto: g.auto, n: g.steps.length });
    if (g) {
      const text = st => st.text.map(x => x.b ? '<b>' + x.t + '</b>' : x.t).join('');
      ok('every step in the page\'s words: its title, its text (bold where the page is) and "Tap anywhere to continue"',
        same(g.steps.map(st => [st.title, '<p>' + text(st) + '</p>' + (st.hint ? '<p class="ld-tour-hint">' + g.labels.tap + '</p>' : '')]), golden.map(x => [x.title, x.body])), g.steps.map(st => st.title));
      ok('the buttons in the page\'s words, "Got it" on the last step', g.labels.skip === 'Skip guide' && g.labels.back === 'Back' && g.labels.next === golden[0].next && g.labels.last === golden[golden.length - 1].next && g.labels.last === 'Got it', g.labels);
      const anchors = g.steps.map(st => st.anchor);
      ok('a part to point at for each step: the dial, its middle, up next and the footer', anchors.every(a => ['dial', 'dial.centre', 'upNext', 'footer'].includes(a)) &&
        anchors.filter(a => a === 'dial').length === golden.filter(x => x.mode && x.mode !== 'center').length && anchors.includes('dial.centre') && anchors[anchors.length - 1] === 'footer', anchors);
      ok('each step says how the dial looks (the page\'s data-tour) and how the spotlight is cut (round, padding)', same(g.steps.map(st => st.mode), golden.map(x => x.mode)) &&
        g.steps.every(st => typeof st.round === 'boolean' && typeof st.pad === 'number') && g.steps[0].round === true && g.steps[0].pad === 2 && g.steps[g.steps.length - 1].round === false && g.steps[g.steps.length - 1].pad === 8,
        g.steps.map(st => [st.mode, st.round, st.pad]));
      const near = (a, b) => a.length === b.length && a.every((t, k) => t.text === b[k].text && t.kind === b[k].kind && Math.abs(t.x - b[k].x) <= 1 && Math.abs(t.y - b[k].y) <= 1);
      ok('each step\'s name tags are the very tags the page pins (words, colour and place in dial units)', g.steps.every((st, k) => near(st.tags, pills(golden[k].tags))),
        g.steps.map((st, k) => [st.tags, pills(golden[k].tags)]).filter(([a, c]) => !near(a, c)));
      ok('tags carry plain data: numbers and text', g.steps.every(st => st.tags.every(t => typeof t.x === 'number' && typeof t.y === 'number' && typeof t.text === 'string' && typeof t.kind === 'string')));
    }
    let r = await command(p, 'tourSeen', { key: 'today', finished: true });
    const seen = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tours.today);
    ok('tourSeen (finished): Today\'s guide is seen and saved; the next projection keeps it for ? but no longer starts it', r.reply && r.reply.ok && seen === true && r.snapshot.guide && r.snapshot.guide.key === 'today' && r.snapshot.guide.auto === false && r.snapshot.notice === null,
      { reply: r.reply, seen });
    r = await command(p, 'tourSeen', { key: 'today', finished: false });
    ok('tourSeen (skipped): the page\'s words say how to see it again', r.reply && r.reply.ok && r.snapshot.notice && /Tap \? at the top/.test(r.snapshot.notice.text), r.snapshot && r.snapshot.notice);
    r = await command(p, 'section', { id: 'week' });
    ok('Week keeps its own guide in the projection (no step lost a field)', r.snapshot.guide && r.snapshot.guide.key === 'week' && r.snapshot.guide.steps.every(st => st.mode === '' && st.round === false && st.pad === 8 && same(st.tags, [])), r.snapshot.guide);
    ok('no script errors with Today\'s native guide', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 3. an APK without director.sheets.v1: the page plays Today's guide itself, as before */
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1']);
    await go(p, 'life-director', 1500);
    const page = await p.evaluate(() => ({ tour: !!document.querySelector('#view-life-director .ld-tour.on'), shows: LifeDirector.native.showing(), guide: NativeDirectorTabProjection.snapshot().guide }));
    const opened = await p.evaluate(() => __since(0).filter(c => c[1] === 'open').length);
    ok('without director.sheets.v1 the page plays Today\'s guide itself and the screen stays away', page.tour && page.shows === 'guide' && page.guide === null && opened === 0, { page, opened });
    ok('no script errors with the page\'s guide under a Today-only screen', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 4. the first build after set-up plays on the native Today */
  {
    const p = await start(b, SHEETS, { setup: true });
    await go(p, 'life-director', 1500);
    const before = await p.evaluate(() => ({ shows: LifeDirector.native.showing(), opened: __since(0).filter(c => c[1] === 'open').length }));
    ok('set-up is still the page\'s (4B): the screen stays away', before.shows === 'setup' && before.opened === 0, before);
    const n = await mark(p);
    const building = await p.evaluate(() => { document.querySelector('#view-life-director [data-act=finishSetup]').click(); return !!document.querySelector('#view-life-director .ld-dial.ld-building') });
    await settle(p, 600);
    const after = await p.evaluate(n => {
      const o = __since(n).filter(c => c[1] === 'open'), s = o.length ? o[o.length - 1][2].snapshot : null;
      return { opened: o.length, tab: s && s.tab, build: !!(s && s.today && s.today.dial.build), full: !!(s && s.today && s.today.dial.build && s.today.dial.build.full), guide: s && s.guide && { key: s.guide.key, auto: s.guide.auto },
        pageBuild: !!document.querySelector('#view-life-director .ld-dial.ld-building'), shows: LifeDirector.native.showing() };
    }, n);
    ok('Build my week starts the build on the page, as before the screen comes', building, building);
    ok('the screen covers Today straight after set-up, with the full build to play and Today\'s guide due after it', after.opened === 1 && after.tab === 'today' && after.build && after.full && after.guide && after.guide.key === 'today' && after.guide.auto === true && after.shows === 'today', after);
    ok('the page\'s own build stops as the screen comes over it', !after.pageBuild, after);
    await settle(p, 4000); // longer than the page's build and its guide's 500 ms wait
    const later = await p.evaluate(() => ({ tour: !!document.querySelector('#view-life-director .ld-tour.on'), shows: LifeDirector.native.showing(), seen: !!(JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tours || {}).today }));
    ok('the page starts no guide after the build: the screen plays Today\'s guide', !later.tour && later.shows === 'today' && !later.seen, later);
    ok('no script errors through set-up and the first build', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
