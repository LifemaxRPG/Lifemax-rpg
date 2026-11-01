/* Native Life Director, phase 4A, web half: celebrations in replies, the level-up stepping the screen aside, and the glance's intent.
   Checked:
   - a screen that draws celebrations (director.celebrations.v1): a write that pays XP answers with celebrations [{xp, gains}] in
     the shape Workout plan's reply has, the projection carries celebrationTarget (NativeTabKit's, shared with Workout plan), the
     page plays only the sound and buzz (xpCue) and never its own celebration, and the screen is not stepped aside;
   - a screen that does not (director.sheets.v1 alone): exactly as before, the reply carries no celebrations, the page plays the
     celebration, the screen steps aside for it and comes back afterwards;
   - a level-up over the page (.luov) steps the screen aside, and it comes back by itself once the level-up is dismissed;
   - the Overview's glance: with Today's sheets drawn by the screen, a tap on a session asks for the screen's own sheet (intent
     {id, sheet, target}) and the page opens none; Start starts the timer and asks for its sheet; an intent does not outlive its
     moment; without Today's sheets the tap is replayed on the page as before.
   A fake NativeLifeDirector plugin stands in for Android; the page runs on Playwright's clock through the lived-in day of
   tests/fixtures/director-day.js. Run: NODE_PATH=$(npm root -g) node tests/native-director-celebrate.test.js [built html] */
const { chromium } = require('playwright');
const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };

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

async function start(b, caps) {
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
  p.errors = []; p.on('pageerror', e => p.errors.push(String(e)));
  await directorDay.seed(p, { planned: true });
  await p.addInitScript(FAKES(caps));
  await p.goto('file://' + file, { timeout: 120000 });
  await p.evaluate(() => {
    lmaThumbWarm = async () => { }; showLevelUp = function () { }; lmaLoad().nativeOverview = false; lmaSave();
    // what the page plays: its sound and buzz (xpCue), and its own celebration (the rising +N XP it appends)
    window.__cues = []; xpCue = d => window.__cues.push(d);
    window.__fx = 0; new MutationObserver(ms => ms.forEach(m => m.addedNodes.forEach(n => { if (n.classList && n.classList.contains('fx-xp')) window.__fx++ }))).observe(document.body, { childList: true });
  });
  await p.evaluate(() => setNativeDirectorEnabled(true)); // the Settings switch, as the player turns it on
  await directorDay.play(p);
  return p;
}
async function settle(p, ms = 500) { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 200 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(10) }
async function go(p, v, ms) { await p.evaluate(v => lmaNavTo(v), v); await p.waitForFunction(v => lmaView() === v, v); await settle(p, ms) }
const mark = p => p.evaluate(() => window.__calls.length);
const calls = (p, n) => p.evaluate(n => __since(n).map(c => c[1]), n);
const lastOpen = (p, n = 0) => p.evaluate(n => { const o = __since(n).filter(c => c[1] === 'open'); return o.length ? o[o.length - 1][2].snapshot : null }, n);
/* the screen's command, as the native side sends it, and the reply that comes back with the next update */
async function command(p, command, payload, ms = 400) {
  const n = await mark(p);
  await p.evaluate(([c, pl]) => Capacitor.Plugins.NativeLifeDirector.__emit('command', { command: c, payload: pl, requestId: 'r-' + c }), [command, payload]);
  await settle(p, ms);
  return p.evaluate(n => { const u = __since(n).filter(c => c[1] === 'update' && c[2].reply); return { reply: u.length ? u[0][2].reply : null, snapshot: u.length ? u[0][2].snapshot : null, calls: __since(n).map(c => c[1]) } }, n);
}
const nextSession = p => p.evaluate(() => { const t = LifeDirector.native.today(0), a = t.upNext && t.upNext.actions.find(x => x.act === 'focusStart'); return a && a.id });
/* a celebration in the shape Workout plan's reply has: {xp, gains: [{attribute, short, xp, color}]} */
const shaped = c => Number.isInteger(c.xp) && c.xp > 0 && Array.isArray(c.gains) && c.gains.length > 0 && Object.keys(c).sort().join() === 'gains,xp' &&
  c.gains.every(g => typeof g.attribute === 'string' && typeof g.short === 'string' && g.short.length > 0 && Number.isInteger(g.xp) && Number.isInteger(g.color) && g.color > 0xffffff && Object.keys(g).sort().join() === 'attribute,color,short,xp');

const SHEETS = ['director.today.v2', 'director.week.v1', 'director.goals.v1', 'director.sheets.v1'];
const CELEBRATES = SHEETS.concat('director.celebrations.v1');

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  /* 1. a screen that draws celebrations */
  {
    const p = await start(b, CELEBRATES);
    await go(p, 'life-director', 1500);
    const s = await lastOpen(p);
    const target = await p.evaluate(() => NativeTabKit.celebrationTarget());
    ok('the projection carries celebrationTarget, NativeTabKit\'s (the top bar\'s logo)', s && s.celebrationTarget && Math.abs(s.celebrationTarget.x - target.x) < 0.5 && Math.abs(s.celebrationTarget.y - target.y) < 0.5 && target.x > 0, s && s.celebrationTarget);
    const id = await nextSession(p);
    await p.evaluate(() => { window.__cues = []; window.__fx = 0 }); // the lived-in morning's own celebrations played on the page
    const n = await mark(p);
    const r = await command(p, 'today', { name: 'done', id, minutes: 20 }, 200);
    const cues = await p.evaluate(() => window.__cues.slice());
    ok('done pays XP: the reply carries celebrations [{xp, gains: [{attribute, short, xp, color}]}]', r.reply && r.reply.ok && Array.isArray(r.reply.celebrations) && r.reply.celebrations.length > 0 && r.reply.celebrations.every(shaped), r.reply);
    ok('...and nothing the screen does not read (no after)', r.reply && !('after' in r.reply), r.reply && Object.keys(r.reply));
    ok('the page plays the sound and buzz once the reply has gone (xpCue with the XP of each award)', cues.length === r.reply.celebrations.length && cues.every((d, i) => d === r.reply.celebrations[i].xp), cues);
    await settle(p, 3000);
    const after = await calls(p, n);
    ok('the page plays no celebration of its own', await p.evaluate(() => window.__fx) === 0);
    ok('the screen is not stepped aside: no close, through the whole celebration', !after.includes('close'), after);
    const again = await command(p, 'today', { name: 'done', id, minutes: 20 });
    ok('a repeat ("Already recorded.") pays nothing and carries no celebrations', again.reply && again.reply.changed === false && !('celebrations' in again.reply), again.reply);
    const pin = await command(p, 'today', { name: 'pin', id: (await p.evaluate(() => LifeDirector.native.today(1).rows.ahead.find(r => r.act === 'inst').id)), on: true });
    ok('a write that pays no XP carries no celebrations', pin.reply && pin.reply.ok && !('celebrations' in pin.reply), pin.reply);

    /* the level-up is not held: it comes over the page, the screen steps aside, and comes back once it is dismissed */
    let m = await mark(p);
    await p.evaluate(() => { const ov = document.createElement('div'); ov.className = 'luov'; document.body.appendChild(ov) });
    await settle(p, 300);
    let c = await calls(p, m);
    ok('a level-up over the page (.luov) steps the screen aside at once', c.includes('close'), c);
    await settle(p, 1000);
    c = await calls(p, m);
    ok('...and it stays aside while the level-up shows', !c.includes('open'), c);
    m = await mark(p);
    await p.evaluate(() => document.querySelector('.luov').remove());
    await settle(p, 400);
    c = await calls(p, m);
    ok('the level-up dismissed: the screen comes back by itself', c.includes('open'), c);
    ok('no script errors with celebrations drawn by the screen', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 2. a screen that does not draw celebrations: as before */
  {
    const p = await start(b, SHEETS);
    await go(p, 'life-director', 1500);
    const id = await nextSession(p);
    const n = await mark(p);
    const r = await command(p, 'today', { name: 'done', id, minutes: 20 }, 200);
    ok('without director.celebrations.v1 done answers ok with no celebrations', r.reply && r.reply.ok && !('celebrations' in r.reply) && !('after' in r.reply), r.reply);
    ok('...the reply goes first, then the screen steps aside for the page\'s celebration', r.calls.indexOf('close') > r.calls.indexOf('update'), r.calls);
    ok('...which the page plays itself', await p.evaluate(() => window.__fx) > 0);
    await settle(p, 3000);
    const after = await calls(p, n);
    ok('...and the screen comes back once it is over', after.lastIndexOf('open') > after.indexOf('close'), after);
    ok('no script errors with celebrations on the page', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 3. the glance's intent, with Today's sheets drawn by the screen */
  {
    const p = await start(b, SHEETS);
    await go(p, 'dashboard', 800);
    const id = await p.evaluate(() => { const a = document.querySelector('#ovPlan .ld-dial [data-act="inst"]'); return a && a.dataset.id });
    ok('the Overview\'s glance shows a session to tap', !!id, id);
    let n = await mark(p);
    await p.evaluate(id => document.querySelector('#ovPlan .ld-dial [data-act="inst"][data-id="' + CSS.escape(id) + '"]').dispatchEvent(new MouseEvent('click', { bubbles: true })), id);
    await settle(p, 800);
    let s = await lastOpen(p, n);
    const first = s && s.intent;
    ok('a session tapped on the glance: the screen opens on Today with the intent for its sheet', s && s.tab === 'today' && first && first.sheet === 'session' && first.target === id && Number.isInteger(first.id) && Object.keys(first).sort().join() === 'id,sheet,target', s && { tab: s.tab, intent: s.intent });
    ok('...and the page opens no sheet of its own, so the screen stays', await p.evaluate(() => !document.querySelector('#view-life-director .ld-sheet.open') && LifeDirector.native.showing() === 'today') && !(await calls(p, n)).includes('close'));
    await settle(p, 10500);
    ok('an intent does not outlive its moment', await p.evaluate(() => LifeDirector.native.intent()) === null);

    // Start on the glance (its core's button before a session) starts the timer and asks for its sheet; mid-afternoon the core shows
    // work, so the button is put there as the glance draws it before the evening session
    const start1 = await nextSession(p);
    await go(p, 'dashboard', 800);
    if (start1) {
      n = await mark(p);
      await p.evaluate(id => { const b = document.createElement('button'); b.className = 'ld-core-btn ld-core-btn-go'; b.dataset.act = 'focusStart'; b.dataset.id = id; document.querySelector('#ovPlan').appendChild(b); b.click() }, start1);
      await settle(p, 800);
      s = await lastOpen(p, n);
      const f = await p.evaluate(() => JSON.parse(localStorage.getItem('lifemax_director_state_v1')).ui.focus);
      ok('Start on the glance: the timer runs (saved) and the intent asks for its sheet, a new id', f && f.id === start1 && s && s.intent && s.intent.sheet === 'timer' && s.intent.target === start1 && s.intent.id > first.id, { f, intent: s && s.intent });
      ok('...with no page sheet', await p.evaluate(() => !document.querySelector('#view-life-director .ld-sheet.open')));
    } else ok('the lived-in afternoon has a session to start', false);
    ok('no script errors with the glance\'s intent', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  /* 4. the glance without Today's sheets on the screen: the tap is replayed on the page, as before */
  {
    const p = await start(b, ['director.today.v2', 'director.week.v1']);
    await go(p, 'dashboard', 800);
    const n = await mark(p);
    await p.evaluate(() => document.querySelector('#ovPlan .ld-dial [data-act="inst"]').dispatchEvent(new MouseEvent('click', { bubbles: true })));
    await settle(p, 800);
    ok('without director.sheets.v1 the page opens its own session sheet', await p.evaluate(() => !!document.querySelector('#view-life-director .ld-sheet.open') && LifeDirector.native.showing() === 'sheet'));
    ok('...the projection carries no intent', await p.evaluate(() => NativeDirectorTabProjection.snapshot().intent) === null);
    const c = await calls(p, n);
    ok('...and the screen does not stay over the page\'s sheet', c.lastIndexOf('close') >= c.lastIndexOf('open'), c);
    ok('no script errors with the glance replayed', p.errors.length === 0, p.errors);
    await p.context().close();
  }

  await b.close();
  console.log('\n' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1) });
