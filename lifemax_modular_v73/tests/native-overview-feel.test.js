/* Native Overview cards, phase 3 "feel", web half (plans/OVERVIEW_BODY.md §2, §3.3, §4 phase 3), on a fake clock:
   - arrival: the projection's counter rises when the screen opens on a real arrival (the cold start under the veil, the page going
     to the Overview from another view and the screen opening within 600 ms), and stays when the screen comes back after the web
     editor or anything else it stepped aside for, when the Overview is chosen again while the screen shows, and when an arrival
     was held away (an overlay) for longer than 600 ms; it rises with the clock, never repeating a played one;
   - the cold-start veil: html.native-boot hides #view-dashboard from the first frame while the cards are wanted, and is lifted when
     the screen opens, when the boot open declines (blocked, or a missing capability), or at 1500 ms if the open never answers;
     never set with the switch off or on a wide screen;
   - the thumbnail pass takes no picture while the veil is up, and does once it is lifted.
   Fake NativeOverviewCards, NativeOverview and PageThumb plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-overview-feel.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
const golden = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'saves', 'established.json'), 'utf8'));

/* the fakes: window.__cards = {caps, hold} (hold: the cards' open never answers); window.__calls records [plugin, call, arg, ms] */
const FAKES = `
  window.__calls = []; window.__cards = { caps: ['overview.cards.v1'], hold: false };
  window.__t = () => Math.round(performance.now());
  const mk = (name, cards) => { const ls = {}; return {
    __emit: (e, d) => Promise.all((ls[e] || []).map(f => f(d))),
    addListener: async (e, f) => { (ls[e] = ls[e] || []).push(f); return { remove() {} } },
    open: a => { window.__calls.push([name, 'open', a, __t()]); return cards && window.__cards.hold ? new Promise(() => {}) : Promise.resolve() },
    update: async a => { window.__calls.push([name, 'update', a, __t()]) },
    close: async a => { window.__calls.push([name, 'close', a, __t()]); (ls.closed || []).forEach(f => f({ session: a && a.session })) },
    capabilities: async () => ({ liveWeb: true, version: 1, authoritativeData: true }),
    handshake: async () => ({ protocol: 1, capabilities: cards ? window.__cards.caps : [] }) } };
  const P = { NativeOverview: mk('overview', false), NativeOverviewCards: mk('cards', true),
    PageThumb: { snap: async () => { window.__calls.push(['thumb', 'snap', null, __t()]); return { img: 'data:image/png;base64,' + 'A'.repeat(600) } } } };
  window.Capacitor = { isNativePlatform: () => true, isPluginAvailable: n => !!P[n], getPlatform: () => 'android', Plugins: P, registerPlugin: () => ({}), convertFileSrc: u => u };
  /* when the veil was set and lifted, from the first frame */
  window.__veil = []; new MutationObserver(() => { const on = document.documentElement.classList.contains('native-boot'); if (!__veil.length || __veil[__veil.length - 1][0] !== on) __veil.push([on, __t()]) })
    .observe(document, { subtree: true, attributes: true, attributeFilter: ['class'] });`;

const START = new Date('2026-10-28T12:00:00Z');
async function start(b, { cardsOn = true, cards = {}, width = 412, before = '' } = {}) {
  const ctx = await b.newContext({ viewport: { width, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await p.clock.install({ time: START });
  await p.clock.pauseAt(new Date(START.getTime() + 10)); // the page's timers wait for the test's clock, from the first frame
  const save = Object.assign({}, golden.state, { tutorialDone: true, tipsSeen: { dashboard: true }, name: 'Ada' });
  await p.addInitScript(([save, extras, prefs]) => {
    localStorage.setItem('lifemax_rpg_prototype_v1', save); for (const k in extras) localStorage.setItem(k, extras[k]);
    localStorage.setItem('lifemax_android_prefs_v1', prefs);
  }, [JSON.stringify(save), golden.extras || {}, JSON.stringify({ nativeOverviewCards: cardsOn })]);
  await p.addInitScript(FAKES);
  await p.addInitScript(c => { Object.assign(window.__cards, c) }, cards);
  if (before) await p.addInitScript(before);
  await p.goto('file://' + file, { timeout: 120000 });
  // the first-install thumbnail pass would walk every tab; the thumbnail checks call the schedule themselves
  await p.evaluate(() => { lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } });
  await p.evaluate(`
    window.count = (n, k) => __calls.filter(c => c[0] === n && c[1] === k).length;
    window.last = (n, k) => [...__calls].reverse().find(c => c[0] === n && c[1] === k);
    window.showing = () => { const o = last('cards', 'open'), c = last('cards', 'close'); return !!o && (!c || __calls.lastIndexOf(o) > __calls.lastIndexOf(c)) };
    window.veiled = () => document.documentElement.classList.contains('native-boot');
    window.hidden = () => getComputedStyle(document.getElementById('view-dashboard')).visibility === 'hidden';`);
  return p;
}
/* the fake clock moves in 50-ms steps, letting the page's promises run between them */
async function run(p, ms) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(Math.min(50, ms - t)); await p.waitForTimeout(2) } await p.waitForTimeout(10) }
const opens = p => p.evaluate(() => __calls.filter(c => c[0] === 'cards' && c[1] === 'open').map(c => ({ arrival: c[2].snapshot.arrival, at: c[3] })));

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    /* 1. the cold start: the veil from the first frame, lifted by the open; the boot open is an arrival */
    let p = await start(b);
    await run(p, 1300);
    let o = await opens(p), v = await p.evaluate(() => ({ veil: __veil.slice(), veiled: veiled(), hidden: hidden(), arrival: NativeOverviewCards.diagnostics().arrival }));
    ok('veil: set while the page loads, before the screen is asked', v.veil.length === 2 && v.veil[0][0] === true && o.length === 1 && v.veil[0][1] <= o[0].at, { o, v });
    ok('veil: lifted as the screen opens, before 1500 ms', !v.veiled && !v.hidden && v.veil[1][0] === false && v.veil[1][1] >= o[0].at && v.veil[1][1] < 1500, { o, v });
    ok('arrival: the cold start under the veil is an arrival, a number from the clock', o[0].arrival >= START.getTime() && v.arrival === o[0].arrival, { o, v });
    ok('arrival: the updates after the open carry the same counter', await p.evaluate(a => { const u = __calls.filter(c => c[0] === 'cards' && c[1] === 'update'); return u.every(c => c[2].snapshot.arrival === a) }, o[0].arrival));
    const boot = o[0].arrival;

    /* 2. coming back after something the screen stepped aside for: the counter stays */
    await p.evaluate(() => Capacitor.Plugins.NativeOverviewCards.__emit('command', { command: 'edit', payload: {}, requestId: 'r-edit' }));
    await run(p, 400);
    const aside = await p.evaluate(() => !showing());
    await p.evaluate(() => ovEditClose());
    await run(p, 600);
    o = await opens(p);
    ok('arrival: the screen back after the web editor keeps the counter (no arrival motion)', aside && o.length === 2 && o[1].arrival === boot, { aside, o });

    /* 3. the Overview chosen again while the screen shows: no open, no new arrival */
    await p.evaluate(() => { navigate('dashboard'); renderOverview() });
    await run(p, 400);
    o = await opens(p);
    ok('arrival: choosing the Overview again while it shows is not an arrival', o.length === 2 && await p.evaluate(() => last('cards', 'update')[2].snapshot.arrival) === boot, o);

    /* 4. a real arrival from another view: the screen opens within 600 ms and the counter rises */
    await p.evaluate(() => navigate('quests'));
    await run(p, 800);
    const away = await p.evaluate(() => !showing());
    await run(p, 3000);
    await p.evaluate(() => navigate('dashboard'));
    await run(p, 600);
    o = await opens(p);
    const back = o[2];
    ok('arrival: going to the Overview from another view raises the counter', away && back && back.arrival > boot && back.arrival >= START.getTime() + 4000, { away, o });

    /* 5. an arrival held away longer than 600 ms (an overlay over the page): the screen comes back still */
    await p.evaluate(() => navigate('quests'));
    await run(p, 800);
    await p.evaluate(() => { const d = document.createElement('div'); d.className = 'luov'; d.id = 'feelBlock'; document.body.appendChild(d); navigate('dashboard') });
    await run(p, 1200);
    const held = (await opens(p)).length;
    await p.evaluate(() => document.getElementById('feelBlock').remove());
    await run(p, 600);
    o = await opens(p);
    ok('arrival: an arrival the page held away for over 600 ms comes back with the counter unchanged', held === 3 && o.length === 4 && o[3].arrival === back.arrival, { held, o });
    ok('veil: never set again after the cold start', await p.evaluate(() => __veil.length) === 2);
    ok('no page errors (1)', p.errors.length === 0, p.errors);
    await p.context().close();

    /* 6. an open that never answers: the veil lifts at 1500 ms; no thumbnail while it is up, one after */
    p = await start(b, { cards: { hold: true } });
    await p.evaluate(() => lmaThumbSchedule(lmaTabKey(lmaView())));
    await run(p, 1200);
    v = await p.evaluate(() => ({ veiled: veiled(), hidden: hidden(), opened: count('cards', 'open'), snaps: count('thumb', 'snap'), t: __t() }));
    ok('veil: still up at 1200 ms while the open has not answered, the Overview hidden', v.veiled && v.hidden && v.opened === 1, v);
    ok('thumbnails: no picture is taken while the veil hides the Overview', v.snaps === 0, v);
    await run(p, 400);
    v = await p.evaluate(() => ({ veil: __veil.slice(), veiled: veiled(), hidden: hidden() }));
    ok('veil: lifted at 1500 ms when the open never answers', !v.veiled && !v.hidden && v.veil[1][1] >= 1500 && v.veil[1][1] < 1560, v);
    await p.evaluate(() => lmaThumbSchedule(lmaTabKey(lmaView())));
    await run(p, 1300);
    ok('thumbnails: the picture is taken once the veil is lifted', await p.evaluate(() => count('thumb', 'snap')) === 1);
    await p.context().close();

    /* 7. the boot open declines: blocked by an overlay, or the screen lacks the capability; the veil lifts at the decline */
    p = await start(b, { before: `addEventListener('DOMContentLoaded', () => { document.getElementById('ovEdit').hidden = false })` });
    await run(p, 1000);
    v = await p.evaluate(() => ({ veil: __veil.slice(), veiled: veiled(), showing: showing() }));
    ok('veil: lifted when the boot open declines (the web editor open over the cards)', !v.veiled && !v.showing && v.veil.length === 2 && v.veil[1][1] >= 880 && v.veil[1][1] < 1000, v);
    await p.context().close();
    p = await start(b, { cards: { caps: [] } });
    await run(p, 1000);
    v = await p.evaluate(() => ({ veil: __veil.slice(), veiled: veiled(), gaveUp: NativeOverviewCards.diagnostics().gaveUp }));
    ok('veil: lifted when the screen lacks the capability (gave up)', !v.veiled && v.gaveUp && v.veil.length === 2 && v.veil[1][1] < 1000, v);
    await p.context().close();

    /* 8. no veil with the switch off, or on a wide screen (the page's own wide Overview shows there) */
    p = await start(b, { cardsOn: false });
    await run(p, 2000);
    v = await p.evaluate(() => ({ veil: __veil.slice(), hidden: hidden(), cards: count('cards', 'open') }));
    ok('switch off: never veiled, the cards never asked', !v.veil.some(x => x[0]) && !v.hidden && v.cards === 0, v);
    await p.context().close();
    p = await start(b, { width: 1000 });
    await run(p, 1000);
    v = await p.evaluate(() => ({ veil: __veil.slice(), hidden: hidden() }));
    ok('wide screen: never veiled', !v.veil.some(x => x[0]) && !v.hidden, v);
    await p.context().close();
  } catch (e) {
    fail++; console.log('FAIL exception ' + (e && e.stack || e));
  } finally {
    await b.close();
  }
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
