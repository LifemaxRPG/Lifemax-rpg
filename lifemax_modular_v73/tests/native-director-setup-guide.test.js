/* Native Life Director, phase 4B: set-up's guide as data for a native screen that draws My time, the ? from Goals or Settings
   playing a guide on the screen, and a new player's whole set-up on the screen.
   Checked:
   - the page's own set-up guide (the switch off) shows the same steps, words and mini-dial name tags as before it became data
     (tests/fixtures/director-setup-guide-golden.json; RECORD=1 rewrites it, only ever from code known to be right), and its ? still
     picks the same guide;
   - an APK that draws My time (director.setup.v1) gets set-up's guide in the projection: the page's own steps, words and buttons
     ("Let’s go" on the last), a part of My time to point at for each step, the mini dial's mode and the very Bedtime and Wake up tags
     the page pins; the page starts none of its own and the screen keeps covering My time; tourSeen records it;
   - without director.setup.v1 the page plays set-up's guide itself, as before;
   - help: the guide ? plays (tourHelp's pick), sent only when the screen draws that guide;
   - a fresh player on a screen that draws everything: the first-run card, My time, set-up's guide, sleep, a commitment, a goal,
     Build my week, the first build on the screen's Today (the page plays none) and Today's guide due on the screen.
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through tests/fixtures/director-day.js.
   Run: NODE_PATH=$(npm root -g) node tests/native-director-setup-guide.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'director-setup-guide-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const SHEETS = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1'];
const ALL = SHEETS.concat(['director.celebrations.v1', 'director.setup.v1']);
const ANCHORS = ['time.steps', 'time.sleep', 'time.commitments', 'time.routines', 'time.goals', 'time.build'];

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
/* new ids are counted, so a plan (whose order follows ids) is the same on every run */
const COUNTED_IDS = `(() => { let n = 0; crypto.randomUUID = () => String(++n).padStart(8, '0') + '-0000-4000-8000-000000000000' })();`;

/* state: 'mixed' (set-up not finished, its guide not seen), 'lived' (the lived-in day, every guide seen) or 'fresh' (a new player) */
async function start(b, caps, { state = 'mixed', switchOn = true } = {}) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: state !== 'fresh', setup: state === 'mixed' ? 'unfinished' : 'done' });
  await p.addInitScript(COUNTED_IDS);
  await p.addInitScript(FAKES(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  if (switchOn) await p.evaluate(() => setNativeDirectorEnabled(true));
  if (state === 'lived') await directorDay.play(p); else await p.clock.runFor(3000);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
async function command(p, command, payload) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, 400);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null, calls: __since(n).map(c => c[1]) } }, n);
}
const lastOpen = p => p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o.length ? o[o.length - 1][2].snapshot : null });
const pageTour = p => p.evaluate(() => { const ov = document.querySelector('#view-life-director .ld-tour.on'); return ov && !ov.hidden ? ov.querySelector('h3').textContent : null });
/* the name tags of one step as the page drew them in SVG: centre, words and colour, in dial units */
function pills(svg) {
  const out = [], re = /<g class="ld-tl-p(?: ld-c-(\w+))?"><rect x="([\d.-]+)" y="([\d.-]+)" width="([\d.-]+)" height="24" rx="12"(?:\/>|><\/rect>)<text x="([\d.-]+)" y="([\d.-]+)">([^<]*)<\/text><\/g>/g;
  let m; while ((m = re.exec(svg || ''))) out.push({ x: +m[5], y: +m[3] + 12, text: m[7].replace(/&amp;/g, '&'), kind: m[1] || '' });
  return out;
}
/* the page's own guide, step by step: its counter, title, body, button, and the tags pinned on the mini dial */
async function pageGuide(p) {
  const steps = [];
  for (let i = 0; i < 10; i++) {
    const s = await p.evaluate(() => {
      const ov = document.querySelector('#view-life-director .ld-tour.on'); if (!ov) return null;
      const mini = document.querySelector('#view-life-director .ld-mini'), tl = mini && mini.querySelector('svg .ld-tl');
      return { n: ov.querySelector('.ld-tour-n').textContent, title: ov.querySelector('h3').textContent, body: ov.querySelector('.ld-tour-body').innerHTML,
        next: ov.querySelector('[data-act=tourNext]').textContent, tags: tl ? tl.innerHTML : '' };
    });
    if (!s) break;
    steps.push(s);
    await p.evaluate(() => document.querySelector('#view-life-director [data-act=tourNext]').click());
    await settle(p, 400);
  }
  return steps;
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  let golden = null;

  /* 1. the page's own set-up guide (the switch off), against the golden; and its ? from Goals */
  {
    const p = await start(b, ALL, { switchOn: false });
    await go(p, 'life-director', 1500);
    const steps = await pageGuide(p);
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify({ setup: steps }, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8')).setup;
    ok('the page plays set-up\'s guide by itself on its first visit', steps.length === 6 && steps[0].n === '1 of 6' && steps[5].next === 'Let’s go', steps.map(s => s.n + ' ' + s.next));
    ok('the page\'s own set-up guide is the same, step by step, as before it became data (words, buttons and mini-dial tags)', same(steps, golden), steps.map(s => s.title));
    ok('...with the Bedtime and Wake up tags on the mini dial', same(pills(golden[1].tags).map(t => t.text), ['Bedtime', 'Wake up']), pills(golden[1].tags));
    await p.evaluate(() => document.querySelector('#view-life-director .ld-tab[data-tab="goals"]').click());
    await settle(p, 300);
    await p.evaluate(() => document.querySelector('#view-life-director [data-act=tour]').click());
    await settle(p, 300);
    const help = await p.evaluate(() => ({ tab: LifeDirector.native.frame().tab }));
    ok('the page\'s ? on Goals before set-up is finished still goes to My time and plays set-up\'s guide', help.tab === 'setup' && (await pageTour(p)) === golden[0].title, [help, await pageTour(p)]);
    ok('no script errors on the page\'s own set-up guide', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. an APK that draws My time draws set-up's guide */
  {
    const p = await start(b, ALL);
    await go(p, 'life-director', 1500);
    await p.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('setup')); // the handshake answers in its own time
    await settle(p, 900); // longer than the page's own guide waits before it starts (500 ms)
    const s = await p.evaluate(() => NativeDirectorTabProjection.snapshot());
    const g = s.guide;
    const page = { tour: await pageTour(p), shows: await p.evaluate(() => LifeDirector.native.showing()) };
    ok('the screen covers My time while set-up\'s guide is due: the page starts none of its own', s.tab === 'setup' && !page.tour && page.shows === 'setup', { page, tab: s.tab });
    ok('the projection carries set-up\'s guide, due to start', !!g && g.key === 'setup' && g.auto === true && g.steps.length === golden.length, g && { key: g.key, auto: g.auto, n: g.steps.length });
    if (g) {
      const text = st => st.text.map(x => x.b ? '<b>' + x.t + '</b>' : x.t).join('');
      ok('every step in the page\'s words: its title, its text (bold where the page is) and "Tap anywhere to continue"',
        same(g.steps.map(st => [st.title, '<p>' + text(st) + '</p>' + (st.hint ? '<p class="ld-tour-hint">' + g.labels.tap + '</p>' : '')]), golden.map(x => [x.title, x.body])), g.steps.map(st => st.title));
      ok('the buttons in the page\'s words, "Let’s go" on the last step', g.labels.skip === 'Skip guide' && g.labels.next === golden[0].next && g.labels.last === golden[golden.length - 1].next && g.labels.last === 'Let’s go', g.labels);
      ok('a part of My time to point at for each step, in the page\'s order', same(g.steps.map(st => st.anchor), ANCHORS), g.steps.map(st => st.anchor));
      ok('the sleep step is the mini dial\'s: mode mini, a round spotlight with the page\'s 4 of padding; the rest plain', g.steps[1].mode === 'mini' && g.steps[1].round === true && g.steps[1].pad === 4 &&
        g.steps.filter((st, k) => k !== 1).every(st => st.mode === '' && st.round === false && st.pad === 8 && st.tags.length === 0), g.steps.map(st => [st.mode, st.round, st.pad, st.tags.length]));
      const near = (a, c) => a.length === c.length && a.every((t, k) => t.text === c[k].text && t.kind === c[k].kind && Math.abs(t.x - c[k].x) <= 1 && Math.abs(t.y - c[k].y) <= 1);
      ok('the mini dial\'s name tags are the very tags the page pins (Bedtime, Wake up: words, colour and place in dial units)', near(g.steps[1].tags, pills(golden[1].tags)) && g.steps[1].tags.every(t => t.kind === 'sleep'),
        [g.steps[1].tags, pills(golden[1].tags)]);
    }
    let r = await command(p, 'tourSeen', { key: 'setup', finished: true });
    const seen = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tours.setup);
    ok('tourSeen (finished): set-up\'s guide is seen and saved; the projection keeps it for ? but no longer starts it', r.reply && r.reply.ok && seen === true && r.snapshot.guide && r.snapshot.guide.key === 'setup' && r.snapshot.guide.auto === false,
      { reply: r.reply, seen });
    ok('help on My time is set-up\'s guide, drawn here', r.snapshot.help === 'setup', r.snapshot.help);
    r = await command(p, 'section', { id: 'goals' });
    ok('help on Goals before set-up is finished: set-up\'s guide, which the screen draws (no guide of Goals\' own)', r.snapshot.tab === 'goals' && r.snapshot.guide === null && r.snapshot.help === 'setup', [r.snapshot.tab, r.snapshot.help]);
    r = await command(p, 'section', { id: 'setup' });
    ok('the ? from Goals: back on My time, set-up\'s guide is there to play', r.snapshot.tab === 'setup' && r.snapshot.guide && r.snapshot.guide.key === 'setup' && !(await pageTour(p)), r.snapshot.tab);
    ok('no script errors with set-up\'s native guide', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 3. without director.setup.v1 the page plays set-up's guide itself, and help names no guide the screen does not draw */
  {
    const p = await start(b, SHEETS);
    await go(p, 'life-director', 1500);
    await p.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('goals'));
    await settle(p, 900);
    const page = { tour: await pageTour(p), shows: await p.evaluate(() => LifeDirector.native.showing()), s: await p.evaluate(() => NativeDirectorTabProjection.snapshot()) };
    ok('without director.setup.v1 the page plays set-up\'s guide itself', page.tour === golden[0].title && page.shows === 'guide' && page.s.guide === null, { tour: page.tour, shows: page.shows });
    ok('...and help from anywhere is not set-up\'s (the screen does not draw it)', page.s.help === null, page.s.help);
    await p.context().close();
  }

  /* 4. help after set-up: Today's guide, from Goals, Settings and Quests; Week's own on Week; none for a screen without Today's guide */
  {
    const p = await start(b, ALL, { state: 'lived' });
    await go(p, 'life-director', 1500);
    await p.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('setup'));
    const helps = {};
    for (const t of ['goals', 'settings', 'quests', 'week', 'today']) { const r = await command(p, 'section', { id: t }); helps[t] = [r.snapshot.help, r.snapshot.guide && r.snapshot.guide.key] }
    ok('help after set-up: Today\'s guide from Goals, Settings and Quests; Week\'s and Today\'s own where they are', same(helps, { goals: ['today', null], settings: ['today', null], quests: ['today', null], week: ['week', 'week'], today: ['today', 'today'] }), helps);
    const r = await command(p, 'section', { id: 'setup' });
    ok('help on My time once set-up is finished: none the screen draws (the page plays it, as before)', r.snapshot.help === null && r.snapshot.guide === null, [r.snapshot.help, r.snapshot.guide]);
    ok('no script errors with help', p.errors.length === 0, p.errors);
    await p.context().close();
    const q = await start(b, ['director.today.v2', 'director.week.v1', 'director.goals.v1'], { state: 'lived' });
    await go(q, 'life-director', 1500);
    await q.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('goals'));
    const g = await command(q, 'section', { id: 'goals' });
    ok('a screen without Today\'s guide gets no help on Goals (its ? hands over, as before)', g.snapshot.help === null, g.snapshot.help);
    await q.context().close();
  }

  /* 5. a fresh player, all on the screen: first-run card, My time, set-up's guide, the writes, the first build, Today's guide */
  {
    const p = await start(b, ALL, { state: 'fresh' });
    await go(p, 'life-director', 1500);
    await p.waitForFunction(() => NativeDirectorTabProjection.snapshot().covers.includes('setup'));
    await settle(p, 300);
    const first = await command(p, 'section', { id: 'today' });
    ok('a fresh player: Today is the first-run card, on the screen', first.reply.ok && first.snapshot.firstRun && first.snapshot.firstRun.section === 'setup' && !first.calls.includes('close'), [first.reply, first.calls]);
    const mine = await command(p, 'section', { id: first.snapshot.firstRun.section });
    const g = mine.snapshot.guide;
    ok('its button: My time on the screen, with set-up\'s guide due there and none on the page', mine.reply.ok && mine.snapshot.tab === 'setup' && mine.snapshot.time && g && g.key === 'setup' && g.auto === true &&
      !(await pageTour(p)) && (await p.evaluate(() => LifeDirector.native.showing())) === 'setup', [mine.reply, mine.snapshot.tab, g && g.key]);
    await settle(p, 900);
    ok('the page still starts none of its own once its 500 ms are up', !(await pageTour(p)));
    await command(p, 'tourSeen', { key: 'setup', finished: true });
    const steps = [];
    const sleep = await command(p, 'do', { name: 'sleepSave', bed: 1380, wake: 420, weekend: null }); steps.push(['sleepSave', sleep.reply]);
    const rule = await command(p, 'sheet', { kind: 'rule', id: '' });
    const values = Object.fromEntries(rule.snapshot.sheet.fields.concat(rule.snapshot.sheet.fold ? rule.snapshot.sheet.fold.fields : []).map(f => [f.key, f.value]));
    const work = await command(p, 'do', Object.assign(values, { name: 'ruleSave', op: '1b6f3c2a-0000-4000-8000-0000000000aa', title: 'Work', kind: 'WORK', days: [1, 2, 3, 4, 5], start: 540, end: 1020 })); steps.push(['ruleSave', work.reply]);
    await command(p, 'sheet', { kind: '' });
    const form = await command(p, 'sheet', { kind: 'goalForm', id: 'HABIT_READING' });
    const gv = Object.fromEntries(form.snapshot.sheet.fields.concat(form.snapshot.sheet.fold ? form.snapshot.sheet.fold.fields : []).map(f => [f.key, f.value]));
    const goal = await command(p, 'do', Object.assign(gv, { name: 'goalAdd', op: '2c7a4d3b-0000-4000-8000-0000000000bb', arch: 'HABIT_READING', title: 'Read more' })); steps.push(['goalAdd', goal.reply]);
    await command(p, 'sheet', { kind: '' });
    ok('sleep, a commitment and a goal saved from the screen', steps.every(([, r]) => r && r.ok && r.changed), steps);
    ok('...and My time shows them', goal.snapshot.time && goal.snapshot.time.sleep.saved && goal.snapshot.time.commitments.rows.some(r => r.title === 'Work') && goal.snapshot.time.goals.chips.length === 1 && goal.snapshot.time.build && goal.snapshot.time.build.enabled,
      goal.snapshot.time && { saved: goal.snapshot.time.sleep.saved, rows: goal.snapshot.time.commitments.rows, chips: goal.snapshot.time.goals.chips, build: goal.snapshot.time.build });
    const built = await command(p, 'do', { name: 'finishSetup' });
    const pagePlayed = await p.evaluate(() => [...document.querySelectorAll('#view-life-director .ld-dial *')].some(e => e.getAnimations().some(a => !(a instanceof CSSAnimation) && !(a instanceof CSSTransition))));
    const s = built.snapshot;
    ok('Build my week: saved, Today on the screen with its first build to play, and the page plays none', built.reply.ok && built.reply.changed && s.tab === 'today' && s.today && s.today.dial.build && !pagePlayed && !built.calls.includes('close'),
      [built.reply, built.calls, pagePlayed, s && s.tab]);
    ok('...with Today\'s guide due on the screen after it', s.guide && s.guide.key === 'today' && s.guide.auto === true, s.guide && { key: s.guide.key, auto: s.guide.auto });
    await settle(p, 4000); // longer than the page's build and its guide's 500 ms wait
    const later = await p.evaluate(() => ({ shows: LifeDirector.native.showing(), seen: !!(JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tours || {}).today, closes: __since(0).filter(c => c[1] === 'close').length }));
    ok('the page starts no guide of its own and the screen stays: Today\'s guide is the screen\'s to play', !(await pageTour(p)) && later.shows === 'today' && !later.seen && later.closes === 0, later);
    ok('no script errors through a fresh player\'s set-up on the screen', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
