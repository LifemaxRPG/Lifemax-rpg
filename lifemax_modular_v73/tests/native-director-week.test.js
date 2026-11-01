/* Native Life Director, phase 3A, web half: Week and Quests as data for a native screen, and the section rules around them.
   Checked:
   - the page's own Week and Quests are byte-identical to the goldens recorded before their data was split out of their
     renderers (tests/fixtures/director-week-golden.json; RECORD=1 rewrites it, only ever from code known to be right);
   - nativeWeek and nativeQuests say what the page's own Week and Quests draw;
   - an APK that draws Week (director.week.v1): the kit asks it what it draws as soon as the switch is on, the screen covers Week
     and Quests, the section, gotoDay and day commands each render once and answer, a section the screen does not draw steps it
     aside as soon as the reply is sent, a day chosen on a page-drawn Week survives to the next open, and every arrival lands on
     today;
   - an APK that draws only Today (director.today.v2) sees exactly phase 2: Week stays the page's.
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through the lived-in day of
   tests/fixtures/director-day.js. Run: NODE_PATH=$(npm root -g) node tests/native-director-week.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
const GOLDEN = path.join(__dirname, 'fixtures', 'director-week-golden.json');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

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

async function start(b, caps, { switchOn = true, weekGuideSeen = true } = {}) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: true });
  if (!weekGuideSeen) await p.addInitScript(() => { const k = 'lifemax_director_state_v1', s = JSON.parse(localStorage.getItem(k)); delete s.ui.tours.week; localStorage.setItem(k, JSON.stringify(s)) });
  await p.addInitScript(FAKES(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => { lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave() });
  if (switchOn) await p.evaluate(() => setNativeDirectorEnabled(true)); // the Settings switch, as the player turns it on
  await directorDay.play(p);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
/* a section of the page's own Life Director, drawn by its own tab button, then its main area's HTML */
async function pageSection(p, tab) {
  return p.evaluate(tab => { document.querySelector('#view-life-director .ld-tab[data-tab="' + tab + '"]').click(); return document.querySelector('#view-life-director .ld-main').innerHTML }, tab);
}
/* the screen's command, as the native side sends it, and the reply that comes back with the next update */
async function command(p, command, payload) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, 400);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null, calls: __since(n).map(c => c[1]) } }, n);
}

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. the page's own Week and Quests, against the goldens; the data the screen gets, against the page's drawing */
  {
    const p = await start(b, ['director.today.v2'], { switchOn: false });
    await go(p, 'life-director', 1500);
    const html = { week: await pageSection(p, 'week'), quests: await pageSection(p, 'quests'), goals: await pageSection(p, 'goals'), settings: await pageSection(p, 'settings') };
    if (process.env.RECORD) { fs.writeFileSync(GOLDEN, JSON.stringify(html, null, 1) + '\n'); console.log('recorded ' + GOLDEN) }
    const golden = JSON.parse(fs.readFileSync(GOLDEN, 'utf8'));
    ok('the page draws Week byte for byte as before its data was split out', html.week === golden.week, html.week.length + ' vs ' + golden.week.length);
    ok('the page draws Quests byte for byte as before its data was split out', html.quests === golden.quests, html.quests.length + ' vs ' + golden.quests.length);
    ok('the page draws Goals byte for byte as before its data was split out', html.goals === golden.goals, html.goals.length + ' vs ' + golden.goals.length);
    ok('the page draws Settings byte for byte as before its data was split out', html.settings === golden.settings, html.settings.length + ' vs ' + golden.settings.length);

    await pageSection(p, 'week');
    const w = await p.evaluate(() => {
      const W = LifeDirector.native.week(), m = document.querySelector('#view-life-director .ld-main'), all = s => [...m.querySelectorAll(s)], t = e => e ? e.textContent : '';
      const rows = all('.ld-wrow');
      return {
        stats: same(W.stats.map(x => [x.value, x.label]), all('.ld-stats > div').map(d => [t(d.querySelector('b')), t(d.querySelector('small'))])),
        ticks: same(W.ticks, all('.ld-wticks b').map(t)),
        days: W.days.length === rows.length && W.days.every((d, k) => {
          const r = rows[k], bars = [...r.querySelectorAll('.ld-wbar i')];
          return d.index === k && d.label === t(r.querySelector('.ld-wd b')) && String(d.date) === t(r.querySelector('.ld-wd small')) && d.aria === r.getAttribute('aria-label') &&
            d.count + (d.done ? d.done : '') === t(r.querySelector('.ld-wst b')) && d.minutes === t(r.querySelector('.ld-wst small')) && d.today === (k === 0) &&
            d.segs.length === bars.length && d.segs.every((g, j) => bars[j].className === 'ld-c-' + g.kind + (g.state ? ' ld-s-' + g.state : '') &&
              parseFloat(bars[j].style.left).toFixed(2) === g.left.toFixed(2) && parseFloat(bars[j].style.width).toFixed(2) === g.width.toFixed(2));
        }),
        legend: same(W.legend.map(x => [x.kind, x.label]), all('.ld-legend li').map(e => [e.className.replace('ld-c-', ''), t(e)])),
        yield: !!W.yield && W.yield.title === t(m.querySelector('.ld-card-yield h4')) && W.yield.note === t(m.querySelector('.ld-card-yield p')) &&
          same(W.yield.rows.map(r => [r.label, r.total]), all('.ld-yield li').map(e => [t(e.querySelector('span')), t(e.querySelector('b'))])) &&
          W.yield.rows.every(r => Number.isInteger(r.color) && r.done >= 0 && r.plan >= 0 && r.done + r.plan <= 1.0001),
        fit: W.fit.title === t(m.querySelector('.ld-card-fit h4')) && W.fit.text === t(m.querySelector('.ld-card-fit > p')) &&
          (W.fit.unscheduled ? W.fit.unscheduled.summary === t(m.querySelector('.ld-card-fit summary')) && same(W.fit.unscheduled.groups.map(g => [g.title, g.tier, g.text]), all('.ld-why li').map(e => [t(e.querySelector('b')), t(e.querySelector('.ld-tierlbl')), t(e.querySelector('p'))]))
            : W.fit.allFit === t(m.querySelector('.ld-card-fit .ld-muted'))),
        size: JSON.stringify(W).length,
      };
      function same(a, b) { return JSON.stringify(a) === JSON.stringify(b) }
    });
    ok('Week as data: the three numbers say what the page shows', w.stats, w);
    ok('Week as data: the hour ticks', w.ticks, w);
    ok('Week as data: every day row (its name, date, count, done, minutes, what TalkBack reads) and every bar segment in place', w.days, w);
    ok('Week as data: the legend', w.legend, w);
    ok('Week as data: where the XP goes, with each attribute\'s colour and shares', w.yield, w);
    ok('Week as data: how the week fits, and what could not fit', w.fit, w);
    ok('Week as data stays small (under 20 KB on the lived-in week)', w.size < 20000, w.size);

    await pageSection(p, 'quests');
    const q = await p.evaluate(() => {
      const Q = LifeDirector.native.quests(), m = document.querySelector('#view-life-director .ld-main'), all = s => [...m.querySelectorAll(s)], t = e => e ? e.textContent.trim() : '';
      const card = m.querySelector('.ld-card'), lists = all('.ld-qlist'), first = lists[0] ? [...lists[0].children] : [];
      const qRows = first.filter(e => e.classList.contains('ld-q') && !e.classList.contains('pas') || (e.classList.contains('ld-q') && Q.rows.some(r => r.title === t(e.querySelector('b')) && r.tone === 'pas')));
      return {
        head: Q.head.title === t(card.querySelector('h4')) && Q.head.text === t(card.querySelector('p')) && (Q.head.open || '') === t(card.querySelector('button')),
        rows: same(Q.rows.map(r => [r.title, r.when, r.status, r.tone, r.why]), first.slice(0, Q.rows.length).map(e => [t(e.querySelector('b')), t(e.querySelector('small')), t(e.querySelector(':scope > span')), e.classList[1], t(e.querySelector('p'))])),
        passive: same(Q.passive.map(r => [r.title, r.when, r.status]), first.slice(Q.rows.length).filter(e => e.classList.contains('ld-q')).map(e => [t(e.querySelector('b')), t(e.querySelector('small')), t(e.querySelector(':scope > span'))])),
        empty: Q.rows.length || Q.passive.length || Q.empty === t(lists[0] && lists[0].querySelector('.ld-muted')),
        miles: !Q.milestones ? !m.querySelector('.ld-sec') : Q.milestones.summary === t(m.querySelector('.ld-sec small')) && Q.milestones.note === t(m.querySelector('.ld-sec + .ld-muted')) &&
          same(Q.milestones.rows.map(r => [r.title, r.desc, r.status, r.earned]), [...(lists[1] ? lists[1].children : [])].map(e => [t(e.querySelector('b')), t(e.querySelector('small')), t(e.querySelector(':scope > span')), e.classList.contains('ok')])),
        n: Q.rows.length, np: Q.passive.length,
      };
      function same(a, b) { return JSON.stringify(a) === JSON.stringify(b) }
    });
    ok('Quests as data: the Quest Board card', q.head, q);
    ok('Quests as data: every quest row (title, due, status, tone, why it could not fit)', q.rows && q.n > 0, q);
    ok('Quests as data: the all-day reminders', q.passive, q);
    ok('Quests as data: the empty list\'s words when there are no quests', q.empty, q);
    ok('Quests as data: the milestones', q.miles, q);
    ok('no script errors on the page-drawn sections', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. an APK that draws Week and Quests */
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1']);
    const asked = await p.evaluate(() => __since(0).filter(c => c[1] === 'handshake').length);
    ok('the kit asks the screen what it draws as soon as the switch is turned on, before Life Director is ever opened', asked === 1, asked);

    // a day chosen on the page while the screen is away survives to the next open
    await p.evaluate(() => { LifeDirector.native.section('week') });
    await go(p, 'life-director', 1500);
    let s = await p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o.length ? o[o.length - 1][2].snapshot : null });
    ok('arriving lands on today, and the screen covers Week: the projection names the sections it may draw and carries Week', s && s.tab === 'week' && same(s.covers, ['today', 'week', 'quests']) && !!s.week && s.today === null, s && { tab: s.tab, covers: s.covers, week: !!s.week, today: s.today });

    let r = await command(p, 'section', { id: 'quests' });
    ok('section: the screen switches to Quests, the page shows Quests underneath, and the reply carries Quests', r.reply && r.reply.ok && r.snapshot.tab === 'quests' && !!r.snapshot.quests && !r.snapshot.week &&
      await p.evaluate(() => document.querySelector('#view-life-director .ld-root').dataset.tab === 'quests'), r);
    const saved = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tab);
    ok('section saves nothing by itself (the tab rides on the next save)', saved !== 'quests', saved);

    r = await command(p, 'gotoDay', { index: 2 });
    ok('gotoDay: Today on that day, the page too, in one reply', r.reply && r.reply.ok && r.snapshot.tab === 'today' && r.snapshot.today && r.snapshot.today.dayIndex === 2 &&
      await p.evaluate(() => document.querySelector('#view-life-director .ld-root').dataset.tab === 'today'), r.reply);
    r = await command(p, 'day', { index: 1 });
    ok('day: another day of the strip, the page\'s strip follows', r.reply && r.reply.ok && r.snapshot.today.dayIndex === 1 &&
      await p.evaluate(() => document.querySelector('#view-life-director .ld-chipday.on, #view-life-director .ld-chipday[aria-selected="true"]') !== null), r.reply);
    r = await command(p, 'day', { index: 99 });
    ok('day: a day off the plan is refused and changes nothing', r.reply && r.reply.ok === false && r.snapshot.today.dayIndex === 1, r.reply);
    r = await command(p, 'section', { id: 'nowhere' });
    ok('section: an unknown section is refused', r.reply && r.reply.ok === false, r.reply);

    r = await command(p, 'section', { id: 'goals' });
    ok('section to one the screen does not draw (Goals): the reply goes, then the screen steps aside at once, not at the next refresh', r.reply && r.reply.ok &&
      r.calls.indexOf('close') > r.calls.indexOf('update'), r.calls);
    ok('...and the page shows Goals', await p.evaluate(() => document.querySelector('#view-life-director .ld-root').dataset.tab === 'goals'));

    // back to Week on the page: the screen comes back by itself; then leave and come back: today again
    await p.evaluate(() => document.querySelector('#view-life-director .ld-tab[data-tab="week"]').click());
    await settle(p, 600);
    const back = await p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o.length && o[o.length - 1][2].snapshot.tab });
    ok('choosing Week on the page brings the screen back over it', back === 'week', back);
    await command(p, 'gotoDay', { index: 3 });
    await go(p, 'dashboard', 800);
    await go(p, 'life-director', 1500);
    s = await p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o[o.length - 1][2].snapshot });
    ok('every arrival lands on today', s.tab === 'today' && s.today && s.today.dayIndex === 0, { tab: s.tab, day: s.today && s.today.dayIndex });
    ok('no script errors with the Week screen', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 3. an APK that draws only Today: phase 2 unchanged */
  {
    const p = await start(b, ['director.today.v2']);
    await p.evaluate(() => LifeDirector.native.section('week'));
    await go(p, 'life-director', 1500);
    const o = await p.evaluate(() => __since(0).filter(c => c[1] === 'open').length);
    const s = await p.evaluate(() => NativeDirectorTabProjection.snapshot());
    ok('without director.week.v1 the screen never covers Week', o === 0, o);
    ok('...and its projection says it covers Today only', same(s.covers, ['today']), s.covers);
    await p.evaluate(() => document.querySelector('#view-life-director .ld-tab[data-tab="today"]').click());
    await settle(p, 600);
    const t = await p.evaluate(() => { const o = __since(0).filter(c => c[1] === 'open'); return o.length ? o[o.length - 1][2].snapshot : null });
    ok('Today is covered as in phase 2, with Today in the projection', t && t.tab === 'today' && t.today && t.today.dayIndex === 0 && !('week' in t && t.week), t && t.tab);
    ok('no script errors with a Today-only screen', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 4. Week's guide, not seen yet: the screen that draws Week draws it; the page starts none of its own */
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1'], { weekGuideSeen: false });
    await go(p, 'life-director', 1500);
    let r = await command(p, 'section', { id: 'week' });
    await settle(p, 900); // longer than the page's own guide waits before it starts (500 ms)
    const g = r.snapshot && r.snapshot.guide;
    ok('the projection carries Week\'s guide, due to start, with a part to point at for each step', !!g && g.key === 'week' && g.auto === true && g.steps.length === 3 &&
      same(g.steps.map(x => x.anchor), ['week.rows', 'week.stats', 'week.yield']) && g.steps.every(x => x.title && x.text.length && x.text.every(t => typeof t.t === 'string' && typeof t.b === 'boolean')) && g.labels.last === 'Got it', g);
    const page = await p.evaluate(() => ({ tour: !!document.querySelector('#view-life-director .ld-tour.on'), shows: LifeDirector.native.showing() }));
    ok('the page starts no guide of its own, and the screen keeps covering Week', !page.tour && page.shows === 'week', page);
    r = await command(p, 'tourSeen', { key: 'week', finished: true });
    const seen = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.tours.week);
    ok('tourSeen (finished): the guide is seen and saved, and the next projection no longer starts it', r.reply && r.reply.ok && seen === true && r.snapshot.guide && r.snapshot.guide.auto === false && r.snapshot.notice === null, { reply: r.reply, seen, guide: r.snapshot && r.snapshot.guide });
    r = await command(p, 'tourSeen', { key: 'week', finished: false });
    ok('tourSeen (skipped, as Skip or Back): the page\'s words say how to see it again', r.reply && r.reply.ok && r.snapshot.notice && /Tap \? at the top/.test(r.snapshot.notice.text), r.snapshot && r.snapshot.notice);
    r = await command(p, 'tourSeen', { key: 'nowhere', finished: true });
    ok('tourSeen of a guide that does not exist is refused', r.reply && r.reply.ok === false, r.reply);
    ok('no script errors with the native guide', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  {
    const p = await start(b, ['director.today.v2'], { weekGuideSeen: false });
    await p.evaluate(() => LifeDirector.native.section('week'));
    await go(p, 'life-director', 1500);
    const page = await p.evaluate(() => ({ tour: !!document.querySelector('#view-life-director .ld-tour.on'), shows: LifeDirector.native.showing(), guide: NativeDirectorTabProjection.snapshot().guide }));
    ok('without director.week.v1 the page plays Week\'s guide itself, as before', page.tour && page.shows === 'guide' && page.guide === null, page);
    await p.context().close();
  }

  /* 5. Goals and Settings (phase 3B, director.goals.v1): the data says what the page draws, and every change runs the page's own */
  {
    const caps = ['director.today.v2', 'director.week.v1', 'director.goals.v1'];
    const p = await start(b, caps);
    await go(p, 'life-director', 1500);
    let r = await command(p, 'section', { id: 'goals' });
    ok('a screen that draws Goals covers it, and the reply carries Goals', r.reply && r.reply.ok && r.snapshot.tab === 'goals' && !!r.snapshot.goals && same(r.snapshot.covers, ['today', 'week', 'quests', 'goals', 'settings']) && !r.calls.includes('close'), r.reply);
    const g = await p.evaluate(() => {
      const G = LifeDirector.native.goals(), m = document.querySelector('#view-life-director .ld-main'), t = e => e ? e.textContent : '';
      const cards = [...m.querySelectorAll('.ld-goal')];
      return {
        explain: G.explain.title === t(m.querySelector('.ld-explain h4')) && G.explain.text === t(m.querySelector('.ld-explain p')),
        cards: G.cards.length === cards.length && cards.length > 0 && G.cards.every((c, k) => {
          const e = cards[k], shares = [...e.querySelectorAll('.ld-share b')].map(t);
          return c.title === t(e.querySelector('h4')) && c.sub === t(e.querySelector('small')) && String(c.priority) === e.querySelector('select').value &&
            (c.share ? c.share.targetText === shares[0] && c.share.scheduledText === shares[1] && c.share.line === t(e.querySelector('.ld-share + .ld-muted')) : shares.length === 0) &&
            (c.warning || '') === t(e.querySelector('.ld-warn-t')) && c.pause === t(e.querySelector('[data-act=goalPause]')) &&
            (c.clarify ? JSON.stringify(c.clarify.options.map(o => o.label)) === JSON.stringify([...e.querySelectorAll('[data-act=clarify]')].map(t)) : !e.querySelector('[data-act=clarify]'));
        }),
        priorities: JSON.stringify(G.priorities.map(x => x.label)) === JSON.stringify([...m.querySelector('.ld-goal select').options].map(o => o.textContent)),
        id: G.cards[0].id, n: G.cards.length,
      };
    });
    ok('Goals as data: the explanation, every card (title, kind and level, priority, shares, sessions line, warning, clarify, pause) and the priority names', g.explain && g.cards && g.priorities, g);

    r = await command(p, 'do', { name: 'goalPriority', id: g.id, value: 1 });
    const prio = await p.evaluate((id) => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).goals.find(x => x.id === id).priority, g.id);
    ok('do goalPriority: the page re-plans and saves, the reply says ok and carries the new card', r.reply && r.reply.ok && prio === 1 && r.snapshot.goals.cards.find(c => c.id === g.id).priority === 1, { reply: r.reply, prio });
    r = await command(p, 'do', { name: 'goalPause', id: g.id, paused: true });
    const p1 = r.snapshot && r.snapshot.goals.cards.find(c => c.id === g.id);
    r = await command(p, 'do', { name: 'goalPause', id: g.id, paused: true });
    const p2 = r.snapshot && r.snapshot.goals.cards.find(c => c.id === g.id);
    ok('do goalPause with the state wanted: paused once, and the same request again changes nothing', p1 && p1.paused && p1.pause === 'Resume' && r.reply.ok && p2.paused, { p1, p2 });
    r = await command(p, 'do', { name: 'goalPriority', id: g.id, value: 9 });
    ok('do goalPriority with a priority that does not exist is refused', r.reply && r.reply.ok === false, r.reply);
    r = await command(p, 'do', { name: 'nowhere' });
    ok('an unknown change is refused', r.reply && r.reply.ok === false, r.reply);

    // a refused write: the page's "Not saved" banner, and the reply says so
    await p.evaluate(() => { window.__realSet = Storage.prototype.setItem; Storage.prototype.setItem = function (k) { if (k === 'lifemax_director_state_v1') throw new DOMException('The quota has been exceeded.', 'QuotaExceededError'); return window.__realSet.apply(this, arguments) } });
    r = await command(p, 'do', { name: 'goalPriority', id: g.id, value: 4 });
    await p.evaluate(() => { Storage.prototype.setItem = window.__realSet });
    ok('a refused save is not ok: the reply has the page\'s words and the banner shows them', r.reply && r.reply.ok === false && /^Not saved/.test(r.reply.message) && r.snapshot.banner && /^Not saved/.test(r.snapshot.banner.text), r.reply);

    r = await command(p, 'do', { name: 'goalRemove', id: g.id });
    ok('do goalRemove: the goal and its card are gone (the screen asks first, with the page\'s words)', r.reply && r.reply.ok && r.snapshot.goals.cards.length === g.n - 1 && !r.snapshot.goals.cards.some(c => c.id === g.id) && /^Remove this goal\?/.test(r.snapshot.goals.removeConfirm), r.reply);

    r = await command(p, 'section', { id: 'settings' });
    const st = await p.evaluate(() => {
      const S = LifeDirector.native.settings(), m = document.querySelector('#view-life-director .ld-main'), t = e => e ? e.textContent : '';
      const labels = [...m.querySelectorAll('.ld-range')];
      return {
        intensity: JSON.stringify(S.intensity.options.map(o => o.label)) === JSON.stringify([...m.querySelectorAll('.ld-seg')][0].querySelectorAll('button').length ? [...[...m.querySelectorAll('.ld-seg')][0].querySelectorAll('button')].map(t) : []) &&
          S.intensity.options.find(o => o.value === S.intensity.value).label === t([...m.querySelectorAll('.ld-seg')][0].querySelector('.on')),
        ranges: S.ranges.concat([S.misses.grace]).every((r, k) => { const e = labels[k], i = e.querySelector('input'); return String(r.value) === i.value && r.stops[(r.value - r.min) / r.step] === t(e.querySelector('b')) && r.stops.length === (r.max - r.min) / r.step + 1 && r.label === e.firstChild.textContent.trim() }),
        days: JSON.stringify(S.restDays.days.map(d => [d.label, d.on])) === JSON.stringify([...m.querySelectorAll('.ld-dchip-btn')].map(e => [t(e), e.classList.contains('on')])),
        misses: S.misses.text.map(x => x.t).join('') === t([...m.querySelectorAll('.ld-card')][3].querySelector('.ld-muted')),
        diag: S.diagnostics.line === t(m.querySelector('.ld-mono')),
        sizes: S.ranges.map(r => r.stops.length).concat(S.misses.grace.stops.length),
      };
    });
    ok('Settings as data: intensity, the three ranges and the grace period (value, label at every stop), rest days, what a miss costs, diagnostics', st.intensity && st.ranges && st.days && st.misses && st.diag && same(st.sizes, [31, 23, 15, 16]), st);
    r = await command(p, 'do', { name: 'prefRange', key: 'maxDailyGoalMinutes', value: 120 });
    ok('do prefRange: saved, and the label at the new stop is the page\'s', r.reply && r.reply.ok && r.snapshot.settings.ranges[0].value === 120 && r.snapshot.settings.ranges[0].stops[6] === '2h', r.snapshot && r.snapshot.settings.ranges[0].stops[6]);
    r = await command(p, 'do', { name: 'prefRange', key: 'maxDailyGoalMinutes', value: 121 });
    ok('do prefRange off its steps is refused', r.reply && r.reply.ok === false, r.reply);
    r = await command(p, 'do', { name: 'restDay', day: 0, on: true });
    const rd = r.snapshot && r.snapshot.settings.restDays.days[0].on;
    r = await command(p, 'do', { name: 'restDay', day: 0, on: true });
    ok('do restDay with the state wanted: on, and on again changes nothing', rd === true && r.reply.ok && r.snapshot.settings.restDays.days[0].on === true, r.reply);
    r = await command(p, 'do', { name: 'exportBackup' });
    const backup = r.reply && r.reply.text;
    ok('do exportBackup answers the backup text for the screen to copy', r.reply && r.reply.ok && typeof backup === 'string' && backup.length > 1000 && JSON.parse(backup), backup && backup.length);
    r = await command(p, 'do', { name: 'importBackup', text: 'not a backup' });
    ok('do importBackup of something that is not a backup is refused with the page\'s words, nothing changed', r.reply && r.reply.ok === false && r.reply.message.length > 0, r.reply);
    r = await command(p, 'do', { name: 'importBackup', text: backup });
    ok('do importBackup of a real backup restores it', r.reply && r.reply.ok && r.reply.message === 'Backup restored', r.reply);
    ok('no script errors with Goals and Settings', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 6. Today's session sheet and actions (phase 4A, director.sheets.v1): the sheet as data, and every write answering honestly,
     with a repeat of one that landed recorded once */
  {
    const caps = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1'];
    const p = await start(b, caps);
    await go(p, 'life-director', 1500);
    const ids = await p.evaluate(() => {
      const t = LifeDirector.native.today(0), next = t.upNext && t.upNext.actions.find(a => a.act === 'focusStart');
      const tomorrow = LifeDirector.native.today(1).rows.ahead.filter(r => r.act === 'inst').map(r => r.id);
      return { next: next && next.id, later: tomorrow[0], later2: tomorrow[1], later3: tomorrow[2] };
    });
    const acts = () => p.evaluate(() => state.activities.length);
    let r = await command(p, 'sheet', { kind: 'session', id: ids.next });
    let sh = r.snapshot && r.snapshot.sheet;
    ok('sheet: the session sheet comes with the reply, as the page\'s instSheet has it (now: Start timer, Mark done, Move, Pin, Skip, why)', r.reply && r.reply.ok && sh && sh.kind === 'session' && sh.id === ids.next && sh.mode === 'now' &&
      sh.primary.label === 'Start timer' && sh.complete.command === 'done' && JSON.stringify(sh.row.map(x => x.label).slice(-3)) === JSON.stringify(['Move', 'Pin here', 'Skip']) && sh.minutes.max === 480 && sh.why.summary.length > 0, sh);

    let before = await acts();
    r = await command(p, 'today', { name: 'done', id: ids.next, minutes: 20 });
    let after = await acts();
    const hist = await p.evaluate(id => (JSON.parse(localStorage.getItem('lifemax_director_state_v1')).schedule.historyInstances || []).find(h => h.instanceId === id), ids.next);
    ok('today done: saved, the session is in the history, one LifeMax activity, the page\'s notice says so', r.reply && r.reply.ok && hist && /COMPLETED/.test(hist.lifecycleState) && hist.actualMinutes === 20 && after === before + 1 && r.snapshot.notice && /done/.test(r.snapshot.notice.text), { reply: r.reply, after, before, notice: r.snapshot && r.snapshot.notice });
    await settle(p, 3000); // the XP celebration plays on the page and the screen comes back
    r = await command(p, 'today', { name: 'done', id: ids.next, minutes: 20 });
    ok('today done again: "Already recorded.", nothing paid twice', r.reply && r.reply.ok === true && r.reply.changed === false && r.reply.message === 'Already recorded.' && await acts() === after, r.reply);
    r = await command(p, 'sheet', { kind: 'session', id: ids.next });
    ok('the sheet of a session done shows what happened (past)', r.snapshot.sheet.mode === 'past' && /^(Partly done|Done)/.test(r.snapshot.sheet.past), r.snapshot.sheet);

    r = await command(p, 'sheet', { kind: 'session', id: ids.later });
    sh = r.snapshot.sheet;
    ok('a session tomorrow: the later sheet, Log it for today with its small print', sh.mode === 'later' && sh.complete.command === 'logToday' && /stays on/.test(sh.complete.smallPrint) && sh.note.some(x => x.b) && !sh.primary, sh);
    before = await acts();
    r = await command(p, 'today', { name: 'logToday', id: ids.later, minutes: 15, op: 'op-log-1' });
    await settle(p, 3000);
    const once = await acts();
    r = await command(p, 'today', { name: 'logToday', id: ids.later, minutes: 15, op: 'op-log-1' });
    ok('today logToday: logged once; the same operation again answers "Already recorded." and pays nothing', once === before + 1 && r.reply.ok && r.reply.changed === false && r.reply.message === 'Already recorded.' && await acts() === once, { once, before, reply: r.reply });
    r = await command(p, 'today', { name: 'logToday', id: ids.later, minutes: 15, op: 'op-log-2' });
    await settle(p, 3000);
    ok('a new operation logs it again (doing it twice on purpose still works)', r.reply.ok && r.reply.changed !== false && await acts() === once + 1, r.reply);
    r = await command(p, 'today', { name: 'logToday', id: ids.later, minutes: 15 });
    ok('logToday without an operation id is refused', r.reply.ok === false, r.reply);

    // the timer on the next session that can be started now
    const next2 = await p.evaluate(() => { const a = LifeDirector.native.today(0).upNext; return a && (a.actions.find(x => x.act === 'focusStart') || {}).id });
    if (next2) {
      r = await command(p, 'today', { name: 'timer', act: 'start', id: next2 });
      const f = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.focus);
      r = await command(p, 'today', { name: 'timer', act: 'start', id: next2 });
      ok('timer start: one timer, and starting it again changes nothing', f && f.id === next2 && r.reply.ok && r.reply.changed === false, { f, reply: r.reply });
      r = await command(p, 'today', { name: 'timer', act: 'finish', id: next2, start: f.start + 1 });
      ok('timer finish naming another start is refused', r.reply.ok === false && /not running/.test(r.reply.message), r.reply);
      await p.clock.runFor(10 * 60000);
      r = await command(p, 'today', { name: 'timer', act: 'finish', id: next2, start: f.start });
      await settle(p, 3000);
      const a2 = await acts();
      r = await command(p, 'today', { name: 'timer', act: 'finish', id: next2, start: f.start });
      ok('timer finish: logged; the same finish again answers "Already recorded."', r.reply.ok && r.reply.message === 'Already recorded.' && await acts() === a2, r.reply);
    } else ok('a session to time is on the plan', false);

    r = await command(p, 'today', { name: 'pin', id: ids.later2, on: true });
    const pinned = await p.evaluate(id => LifeDirector.native.sheet('session', id).row.find(x => x.command === 'pin').label, ids.later2);
    r = await command(p, 'today', { name: 'pin', id: ids.later2, on: true });
    ok('today pin: pinned (the sheet now offers Unpin), and pinning again changes nothing', pinned === 'Unpin' && r.reply.ok && r.reply.changed === false, { pinned, reply: r.reply });
    r = await command(p, 'today', { name: 'skip', id: ids.later3 });
    const sk = r.reply;
    r = await command(p, 'today', { name: 'skip', id: ids.later3 });
    ok('today skip: skipped, and skipping again answers "Already skipped."', sk.ok && r.reply.ok && r.reply.message === 'Already skipped.', { sk, reply: r.reply });
    r = await command(p, 'sheet', { kind: 'session', id: 'nowhere' });
    ok('the sheet of a session that left the plan says it is gone', r.snapshot.sheet && r.snapshot.sheet.gone === true, r.snapshot.sheet);
    r = await command(p, 'today', { name: 'nowhere' });
    ok('an unknown action is refused', r.reply.ok === false, r.reply);
    ok('no script errors with Today\'s actions', p.errors.length === 0, p.errors);
    await p.context().close();
  }
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1', 'director.goals.v1']);
    await go(p, 'life-director', 1500);
    const r = await command(p, 'sheet', { kind: 'session', id: 'x' });
    ok('without director.sheets.v1 a screen gets no sheets', r.reply && r.reply.ok === false && r.snapshot.sheet === null, r.reply);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
