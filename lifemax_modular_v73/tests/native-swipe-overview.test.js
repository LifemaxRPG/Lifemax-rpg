/* Native tab swipe and the Overview (plans/OVERVIEW_BODY.md §3.8, phase 1 "Native swipe neighbour"; ADR 0012 amending ADR 0011
   item 5 and CONTRACT A34 item 6). The kind rule reads the Overview's composite registration (50-native-overview.js): its `body`
   getter and `wouldOpen`. So:
   - cards off (the live shell draws the Overview over the web body): the Overview is a wall. A swipe toward it is refused "kind",
     and its live shell, which is not a kit tab, never sends one;
   - cards on (Native Overview cards (beta)): the Overview is a native neighbour, both ways, with no Overview-specific swipe code.
     Character -> Overview is answered "native" before the cards are asked to open, the cards open before Character closes (the
     hand-over), and the live shell never opens; Overview -> Character from the cards' screen likewise;
   - the cards would not open now (a sheet over the page, or turned off again): a wall again, as any native tab that would not open.
   Fake plugins stand in for Android, as in native-swipe.test.js (whose harness this copies), plus NativeOverviewCards.
   Run: NODE_PATH=$(npm root -g) node tests/native-swipe-overview.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };

const fakes = `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,tipsSeen:{dashboard:1},classId:'athlete'}));
  localStorage.setItem('lma_qdeal','off');
  window.__calls=[];
  const mk=name=>{const ls={};let current='';
    const log=(m,a)=>window.__calls.push({p:name,m,view:typeof lmaView==='function'?lmaView():'',token:a&&a.token,outcome:a&&a.outcome,reason:a&&a.reason,session:a&&a.session});
    const emit=(e,d)=>(ls[e]||[]).forEach(f=>f(d));
    return {__emit:emit,__session:()=>current,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{current=a&&a.session;log('open',a)},
      update:async a=>{log('update',a)},
      close:async a=>{log('close',a);setTimeout(()=>emit('closed',{session:a&&a.session}),0)},
      swipeSettle:async a=>{log('swipeSettle',a)},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,swipe:1}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','overview.cards.v1','shell.swipe.v1']})}};
  const P={};for(const n of ['NativeOverview','NativeOverviewCards','NativeCharacter'])P[n]=mk(n);
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(fakes); await p.goto('file://' + file, { timeout: 120000 });
  await p.waitForFunction(() => window.NativeSwipe && window.NativeOverviewCards && !document.getElementById('nativeSwipeToggle').disabled, null, { timeout: 20000 }).catch(() => { });
  await p.evaluate(() => {
    lmaThumbWarm = async () => { }; // the first-install thumbnail pass would walk the page through every tab
    Object.assign(lmaLoad(), { nativeCharacter: true, nativeOverview: true, nativeSwipe: true }); lmaSave();
    window.wait = ms => new Promise(r => setTimeout(r, ms));
    window.quiet = async () => { for (let n = __calls.length, q = 0; q < 8; n = __calls.length) { await wait(50); q = __calls.length === n ? q + 1 : 0 } };
    window.__swipe = (plugin, e) => {
      const P = Capacitor.Plugins[plugin], n = __calls.length, view = lmaView();
      P.__emit('swipe', Object.assign({ sequence: 1, via: 'drag' }, e));
      return { calls: __calls.slice(n), view: lmaView(), moved: view !== lmaView() };
    };
    window.__go = async v => { lmaNavTo(v, { fast: true }); for (let i = 0; i < 200 && !NativeTabs.isOpen(lmaResolve(v)); i++) await wait(25); await quiet(); return NativeTabs.isOpen(lmaResolve(v)) };
  });
  const refused = (r, plugin, token, reason) => r.calls.length === 1 && r.calls[0].p === plugin && r.calls[0].m === 'swipeSettle' && r.calls[0].token === token && r.calls[0].outcome === 'refused' && r.calls[0].reason === reason && !r.moved;
  const kinds = () => p.evaluate(() => ({ kind: NativeSwipe.kind('dashboard'), body: NativeTabs.webBody('dashboard'), tab: NativeSwipe.state().tabs.find(t => t.key === 'dashboard').kind }));

  /* ---------- cards off: the live shell draws the Overview, a wall ---------- */
  let k = await kinds();
  ok('cards off: the Overview\'s body is the web page, so it is a web page to a swipe (a wall)', k.kind === 'web' && k.body === true && k.tab === 'web', k);
  ok('cards off: Character opens', await p.evaluate(() => __go('character')));
  let r = await p.evaluate(() => __swipe('NativeCharacter', { token: 'character#1', from: 'character', to: 'dashboard', dir: -1 }));
  ok('cards off: Character -> Overview is refused "kind" and goes nowhere', refused(r, 'NativeCharacter', 'character#1', 'kind'), r);
  ok('cards off: the Overview opens its live shell, not the cards', await p.evaluate(async () => { const n = __calls.length; await __go('dashboard'); const s = __calls.slice(n); return s.some(c => c.p === 'NativeOverview' && c.m === 'open') && !s.some(c => c.p === 'NativeOverviewCards' && c.m === 'open') }));

  /* ---------- cards on: a native neighbour, both ways ---------- */
  await p.evaluate(async () => { await setNativeOverviewCardsEnabled(true); await wait(300); await quiet() });
  k = await kinds();
  ok('cards on: the composite\'s body getter says native, and wouldOpen is the cards\' coverable(): a native neighbour', k.kind === 'native' && k.body === false && k.tab === 'native', k);
  ok('cards on: Character opens', await p.evaluate(() => __go('character')));
  const toOv = await p.evaluate(async () => {
    const at = __calls.length; const r = __swipe('NativeCharacter', { token: 'character#2', from: 'character', to: 'dashboard', dir: -1 });
    for (let i = 0; i < 200 && !NativeOverviewCards.active(); i++) await wait(25); await quiet();
    const s = __calls.slice(at), i = (p, m) => s.findIndex(c => c.p === p && c.m === m);
    return Object.assign(r, { settle: i('NativeCharacter', 'swipeSettle'), open: i('NativeOverviewCards', 'open'), close: i('NativeCharacter', 'close'), shell: i('NativeOverview', 'open'), active: NativeOverviewCards.active(), stack: lmaNavStack.slice(-1)[0] });
  });
  ok('cards on: Character -> Overview is answered "native" at once, and the page goes to the Overview', toOv.calls.length === 1 && toOv.calls[0].outcome === 'native' && toOv.view === 'dashboard', toOv);
  ok('cards on: the answer reached native before the cards\' open, and Character closed only after it (the hand-over)', toOv.settle === 0 && toOv.open > toOv.settle && toOv.close > toOv.open && toOv.active, toOv);
  ok('cards on: the live shell never opened on that swipe', toOv.shell === -1, toOv);
  ok('cards on: Back returns to Character (recorded like a tab change)', toOv.stack === 'character', toOv);
  const fromOv = await p.evaluate(async () => {
    const at = __calls.length; const r = __swipe('NativeOverviewCards', { token: 'dashboard#1', from: 'dashboard', to: 'character', dir: 1 });
    for (let i = 0; i < 200 && !NativeTabs.isOpen('character'); i++) await wait(25); await quiet();
    const s = __calls.slice(at), i = (p, m) => s.findIndex(c => c.p === p && c.m === m);
    return Object.assign(r, { settle: i('NativeOverviewCards', 'swipeSettle'), open: i('NativeCharacter', 'open'), close: i('NativeOverviewCards', 'close'), active: NativeOverviewCards.active() });
  });
  ok('cards on: Overview -> Character from the cards\' screen is answered "native" on the cards\' plugin', fromOv.calls.length === 1 && fromOv.calls[0].p === 'NativeOverviewCards' && fromOv.calls[0].outcome === 'native' && fromOv.view === 'character', fromOv);
  ok('cards on: Character opens before the cards close', fromOv.open > fromOv.settle && fromOv.close > fromOv.open && !fromOv.active, fromOv);

  /* ---------- the cards would not open now: a wall again ---------- */
  ok('Character opens again', await p.evaluate(() => __go('character')));
  r = await p.evaluate(() => { const ld = document.createElement('div'); ld.className = 'updpop'; document.body.appendChild(ld); const kind = NativeSwipe.kind('dashboard'); ld.remove(); return kind });
  ok('with the page covered (a popup), the cards\' coverable() is false: a web page to a swipe', r === 'web', r);
  await p.evaluate(async () => { await setNativeOverviewCardsEnabled(false); await quiet() });
  r = await p.evaluate(() => __swipe('NativeCharacter', { token: 'character#3', from: 'character', to: 'dashboard', dir: -1 }));
  ok('cards turned off again: Character -> Overview is refused "kind" (the live shell is a wall)', refused(r, 'NativeCharacter', 'character#3', 'kind'), r);
  ok('no script errors', errs.length === 0, errs);

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
