/* Native tab swipe, phase 3: prime() and the screen's title. While the swipe switch is on and the page has been left alone, the
   native neighbours of the current tab are asked, one at a time, to compose themselves (shell.prime.v1), so a swipe to them meets a
   screen that is ready. Fake native plugins stand in for Android and log every call in the order the page makes it. Checked:
   - what is primed and when: only a never-primed native neighbour of the current tab, only with the switch on and the APK
     reporting shell.prime.v1, only after a quiet 800 ms (here shortened), never during a touch, one at a time;
   - what priming is not: no open, close or update, no bars, the screen is not "open" to the page, no toast taken;
   - priming and opening: an open after a prime is an ordinary open (a later sequence), and says the tab's name for TalkBack;
   - a native side that is busy or refuses is asked again a few times, then left alone.
   Run: NODE_PATH=$(npm root -g) node tests/native-swipe-prime.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/* prime: the APK reports shell.prime.v1. native: there is an APK at all */
const fakes = ({ prime = true, native = true } = {}) => `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
    plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
  localStorage.setItem('lifemax_director_state_v1', ${JSON.stringify(JSON.stringify(directorDay.state()))});
  localStorage.setItem('lma_qdeal','off');
  window.__calls=[];window.__primeReply=()=>({primed:true,javaKb:812,nativeKb:3400,ms:700});
  const mk=name=>{
    const ls={};
    const log=(m,a)=>window.__calls.push({p:name,m,view:typeof lmaView==='function'?lmaView():'',args:a,at:Date.now()});
    const emit=(e,d)=>(ls[e]||[]).forEach(f=>f(d));
    return {__emit:emit,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{log('open',a)},
      update:async a=>{log('update',a)},
      close:async a=>{log('close',a);setTimeout(()=>emit('closed',{}),0)},
      swipeSettle:async a=>{log('swipeSettle',a)},
      prime:async a=>{log('prime',a);await new Promise(r=>setTimeout(r,40));return window.__primeReply(name,a)},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,swipe:1}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v2','recipes.projection.v1','library.media.projection.v1','shell.swipe.v1','shell.swipe.v2'].concat(${prime}?['shell.prime.v1']:[])})}};
  const P={};for(const n of ['NativeOverview','NativeCharacter','NativeNutrition','NativeLog','NativePlan','NativeLifeDirector','NativeRecipes','NativeMedia','NativeCash'])P[n]=mk(n);
  if(${native})window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const open = async (opts) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, hasTouch: true })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e) + ' ' + String(e.stack || '').split('\n').slice(1, 4).join(' | ')));
    await p.addInitScript(fakes(opts)); await p.goto('file://' + file, { timeout: 120000 });
    await p.waitForFunction(() => window.NativePrime && window.NativeSwipe, null, { timeout: 20000 });
    await p.evaluate(() => {
      lmaThumbWarm = async () => { }; // the first-install thumbnail pass would walk the page through every tab
      NativePrime.config.idleMs = 150; // 800 ms in the app
      Object.assign(lmaLoad(), { nativeCharacter: true, nativeNutrition: true, nativeLog: true, nativePlan: true, nativeLibrary: true, nativeRecipes: true, nativeDirector: true }); lmaSave();
      // the bar in its old order (an owner's own saved order), so Workout plan sits between Quests (a page) and Library as these checks were written for
      lmaLoad().tabs = { order: ['dashboard', 'character', 'food', 'pet', 'log', 'quests', 'plan', 'library', 'money', 'life-director'], hidden: [], extra: [], qEnd: 1 }; lmaSave(); lmaTabsApply();
    });
    return p;
  };
  const turnOn = (p) => p.evaluate(() => { lmaSetNativeSwipe(true) });
  const calls = (p, m) => p.evaluate((m) => window.__calls.filter(c => !m || c.m === m).map(c => ({ p: c.p, m: c.m, view: c.view, args: c.args && Object.keys(c.args), seq: c.args && c.args.snapshot && c.args.snapshot.sequence, at: c.at })), m);
  const go = (p, v) => p.evaluate(async (v) => {
    const wait = ms => new Promise(r => setTimeout(r, ms)); lmaNavTo(v, { fast: true }); const want = lmaResolve(v);
    for (let i = 0; i < 200 && !NativeTabs.isOpen(want); i++) await wait(25);
    for (let n = window.__calls.length, quiet = 0; quiet < 6; n = window.__calls.length) { await wait(50); quiet = window.__calls.length === n ? quiet + 1 : 0 }
    return NativeTabs.isOpen(want);
  }, v);
  const quiet = (p, ms) => p.waitForTimeout(ms);
  // wait for the call log to hold n prime calls, up to a limit
  const primes = async (p, n, ms = 4000) => { await p.waitForFunction((n) => window.__calls.filter(c => c.m === 'prime').length >= n, n, { timeout: ms }).catch(() => { }); return calls(p, 'prime') };

  /* ---------- what is primed ---------- */
  const p = await open();
  // reach Workout plan with the swipe switch still off: nothing is primed (it is a beta feature)
  ok('Workout plan opens', await go(p, 'plan'));
  await quiet(p, 700);
  ok('with the swipe switch off nothing is primed', (await calls(p, 'prime')).length === 0, await calls(p));
  await turnOn(p);
  const first = await primes(p, 1);
  ok('switched on, the one native neighbour of Workout plan is primed after a quiet moment: Library (Media), not the Quests page', first.length === 1 && first[0].p === 'NativeMedia', first);
  ok('with the projection an open would send and nothing else (no bars: no navScene)', same(first[0].args, ['snapshot']), first[0]);
  const snap = await p.evaluate(() => window.__calls.find(c => c.m === 'prime').args.snapshot);
  ok('that projection is the Media kit\'s own, with a session, a sequence and the theme', snap.protocol === 1 && typeof snap.webSessionId === 'string' && snap.webSessionId.startsWith('media-web-') && snap.sequence > 0 && !!snap.tokens && 'light' in snap && Array.isArray(snap.items || snap.media || []), Object.keys(snap));
  await quiet(p, 900);
  ok('it is primed once: the next quiet stretch asks for nothing more', (await calls(p, 'prime')).length === 1, await calls(p, 'prime'));
  const during = await calls(p);
  ok('priming opened, closed and updated nothing, and sent no swipe answer', during.filter(c => c.m !== 'prime').every(c => c.p === 'NativePlan') && !during.some(c => c.p === 'NativeMedia' && c.m !== 'prime'), during.map(c => c.p + '.' + c.m));
  ok('the page does not take a primed screen for an open one', await p.evaluate(() => !NativeTabs.isOpen('media') && lmaView() === 'plan' && NativeTabs.isOpen('plan')));
  ok('the cost the native side measured is kept for the owner (the Media screen)', await p.evaluate(() => JSON.stringify(window.__nativePrimePerf) === JSON.stringify({ media: { javaKb: 812, nativeKb: 3400, ms: 700 } })), await p.evaluate(() => window.__nativePrimePerf));

  /* ---------- opening a primed screen ---------- */
  ok('Library opens as any tab does', await go(p, 'library'));
  const opens = (await calls(p, 'open')).filter(c => c.p === 'NativeMedia');
  ok('with one ordinary open, a later sequence than the prime\'s, carrying the bar\'s name for the tab', opens.length === 1 && opens[0].seq > snap.sequence, opens);
  ok('and the name is "Library", the bar\'s label (TalkBack announces the screen by it)', await p.evaluate(() => window.__calls.filter(c => c.p === 'NativeMedia' && c.m === 'open').pop().args.title === 'Library'));
  ok('Workout plan took its title too when it opened', await p.evaluate(() => window.__calls.filter(c => c.p === 'NativePlan' && c.m === 'open').pop().args.title === 'Workout plan'));
  ok('on Library the native neighbour is Workout plan, which was opened already and is primed too (it is asked, once)', await (async () => { await primes(p, 2); return (await calls(p, 'prime')).filter(c => c.p === 'NativePlan').length <= 1 })());

  /* ---------- a touch holds it back; one at a time ---------- */
  const q = await open();
  // Quests hidden: Log, Workout plan and Library sit side by side. From Workout plan both sides are native
  await q.evaluate(() => { lmaTabShow('quests', false); window.__gate = null; window.__primeReply = (name) => new Promise(r => { window.__gate = window.__gate || []; window.__gate.push([name, Date.now(), r]) }).then(() => ({ primed: true, javaKb: 1, nativeKb: 2, ms: 3 })) });
  ok('Workout plan opens (Quests hidden: both neighbours native)', await go(q, 'plan'));
  await turnOn(q);
  // a finger is down: nothing is asked for as long as it stays there
  await q.evaluate(() => { const t = new Touch({ identifier: 1, target: document.body, clientX: 100, clientY: 300 }); document.body.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [t], changedTouches: [t] })) });
  await quiet(q, 500);
  ok('with a finger on the screen nothing is primed', (await calls(q, 'prime')).length === 0, await calls(q, 'prime'));
  await q.evaluate(() => { const t = new Touch({ identifier: 1, target: document.body, clientX: 100, clientY: 300 }); document.body.dispatchEvent(new TouchEvent('touchend', { bubbles: true, touches: [], changedTouches: [t] })) });
  await quiet(q, 60);
  ok('and not at the moment it lifts: the page waits the quiet stretch first', (await calls(q, 'prime')).length === 0, await calls(q, 'prime'));
  const one = await primes(q, 1);
  ok('then one neighbour is asked', one.length === 1, one);
  await quiet(q, 700);
  ok('the other waits while the first has not been answered (one at a time)', (await calls(q, 'prime')).length === 1, await calls(q, 'prime'));
  await q.waitForFunction(() => window.__gate && window.__gate.length >= 1); await q.evaluate(() => window.__gate[0][2]());
  const two = await primes(q, 2);
  ok('and is asked after the first answer and another quiet stretch', two.length === 2 && two[0].p !== two[1].p && two[1].at - two[0].at >= 150, two);
  ok('the two are Library (next) first, then Log (previous)', same(two.map(c => c.p), ['NativeMedia', 'NativeLog']), two.map(c => c.p));
  await q.waitForFunction(() => window.__gate && window.__gate.length >= 2); await q.evaluate(() => window.__gate[1][2]());
  await quiet(q, 700);
  ok('nothing more once both are primed', (await calls(q, 'prime')).length === 2);
  ok('no errors', q.errs.length === 0, q.errs);

  /* ---------- gates ---------- */
  const old = await open({ prime: false });
  ok('Workout plan opens on an APK without shell.prime.v1', await go(old, 'plan'));
  await turnOn(old);
  await quiet(old, 900);
  ok('an APK without shell.prime.v1 is never asked to prime', (await calls(old, 'prime')).length === 0, await calls(old, 'prime'));
  const web = await open({ native: false });
  await quiet(web, 300);
  ok('in a browser (no APK) there is nothing to prime and no error', web.errs.length === 0 && (await calls(web)).length === 0, web.errs);
  const cover = await open();
  await go(cover, 'plan'); await turnOn(cover);
  await cover.evaluate(() => document.body.classList.add('noscroll'));
  await quiet(cover, 700);
  ok('under a sheet or overlay (noscroll) nothing is primed', (await calls(cover, 'prime')).length === 0, await calls(cover, 'prime'));
  await cover.evaluate(() => { document.body.classList.remove('noscroll'); NativePrime.poke() });
  ok('and once the page is clear it is', (await primes(cover, 1)).length === 1);
  const hush = await open();
  await go(hush, 'plan'); await turnOn(hush);
  await hush.evaluate(() => { lmaSetSwipe(false) });
  await hush.evaluate(() => NativePrime.poke());
  await quiet(hush, 700);
  ok('with "Swipe to switch tabs" off the neighbours are not primed either (no swipe, no use)', (await calls(hush, 'prime')).length === 0);
  const jr = await open();
  await jr.evaluate(() => { Object.assign(lmaLoad(), { nativeJournal: true }); lmaSave() });
  await go(jr, 'media'); await turnOn(jr);
  await quiet(jr, 700);
  ok('the Journal is never primed: its kit does not ask for it (its words are private)', !(await jr.evaluate(() => window.__calls.some(c => c.p === 'NativeJournal'))));

  /* ---------- a native side that cannot ---------- */
  const busy = await open();
  await busy.evaluate(() => { window.__primeReply = () => ({ primed: false, reason: 'busy' }) });
  await go(busy, 'plan'); await turnOn(busy);
  const tried = await primes(busy, 6, 8000);
  await quiet(busy, 1200);
  const tries = await calls(busy, 'prime');
  ok('a "busy" answer is asked again, a few times, then left alone until the next change of tab', tries.length >= 2 && tries.length <= 6, tries.length);
  const bad = await open();
  await bad.evaluate(() => { window.__primeReply = () => { throw new Error('projection rejected') } });
  await go(bad, 'plan'); await turnOn(bad);
  await primes(bad, 2, 3000); await quiet(bad, 1200);
  ok('a rejected projection is not sent again and again', (await calls(bad, 'prime')).length <= 2, await calls(bad, 'prime'));
  ok('and the tab still opens normally afterwards', await go(bad, 'library'));

  ok('no page errors', p.errs.length === 0 && old.errs.length === 0 && cover.errs.length === 0, [p.errs, old.errs, cover.errs]);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
