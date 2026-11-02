/* Native tab swipe, phase 2: the edges (plans/TAB_SWIPE.md §5, §7). Fake native plugins stand in for Android, as in
   native-swipe.test.js (whose harness this copies); their swipes say they reveal a web tab (reveal: true), as a phase 2 APK's do,
   and their handshake says it draws the edges (shell.swipe.v2). Checked:
   - native -> web: a swipe into a tab the page draws (Cat, the Overview's live shell, a native tab whose screen would not open
     now) is answered "web" at once, before the page navigates; the page goes there with html.swipe-reveal set, so no fade-in or
     slide of its cards runs under the veil (getAnimations), and the mark goes at the next navigate or after its 2.5 s cap. Money stays a
     wall, and a swipe from an APK that does not reveal (no reveal flag) is still refused "kind";
   - web -> native: the page's own swipe (lmaSwipeGo) ending on a native tab passes enter and velocity to that screen's open (the
     kit's and Food's), with html.native-enter hiding the page's copy until the screen is in place, and no web slide; from the
     Overview's web body too. With the switch off, on an APK from before phase 2, or with reduced motion, the web swipe is as before.
   Run: NODE_PATH=$(npm root -g) node tests/native-swipe-edges.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };

/* edges: whether the APK draws the edges (its handshake and Food's capabilities say so) */
const fakes = ({ edges = true } = {}) => `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,tipsSeen:{dashboard:1},classId:'athlete',
    plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
  localStorage.setItem('lifemax_director_state_v1', ${JSON.stringify(JSON.stringify(directorDay.state()))});
  localStorage.setItem('lma_qdeal','off');
  window.__calls=[];
  const mk=name=>{const ls={};let current='';
    const log=(m,a)=>window.__calls.push({p:name,m,view:typeof lmaView==='function'?lmaView():'',token:a&&a.token,outcome:a&&a.outcome,reason:a&&a.reason,
      enter:a&&a.enter,velocity:a&&a.velocity,hidden:document.documentElement.classList.contains('native-enter'),reveal:!!(a&&(a.navScene||a.chromeScene)&&(a.navScene||a.chromeScene).swipe&&(a.navScene||a.chromeScene).swipe.reveal)});
    const emit=(e,d)=>(ls[e]||[]).forEach(f=>f(d));
    return {__emit:emit,__session:()=>current,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{current=a&&a.session;log('open',a)},
      update:async a=>{log('update',a)},
      close:async a=>{log('close',a);setTimeout(()=>emit('closed',{session:a&&a.session}),0)},
      swipeSettle:async a=>{log('swipeSettle',a)},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true,swipe:${edges}?2:1}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v2','recipes.projection.v1','library.media.projection.v1','shell.swipe.v1'].concat(${edges}?['shell.swipe.v2']:[])})}};
  const P={};for(const n of ['NativeOverview','NativeCharacter','NativeNutrition','NativeLog','NativePlan','NativeLifeDirector','NativeRecipes','NativeMedia','NativeCash'])P[n]=mk(n);
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const open = async (opts) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e)));
    await p.addInitScript(fakes(opts)); await p.goto('file://' + file, { timeout: 120000 });
    await p.waitForFunction(() => window.NativeSwipe && !document.getElementById('nativeSwipeToggle').disabled, null, { timeout: 20000 }).catch(() => { });
    await p.evaluate(() => {
      lmaThumbWarm = async () => { }; // the first-install thumbnail pass would walk the page through every tab
      Object.assign(lmaLoad(), { nativeCharacter: true, nativeNutrition: true, nativeLog: true, nativePlan: true, nativeLibrary: true, nativeRecipes: true, nativeDirector: true, nativeSwipe: true }); lmaSave();
      window.wait = ms => new Promise(r => setTimeout(r, ms));
      window.quiet = async () => { for (let n = __calls.length, q = 0; q < 8; n = __calls.length) { await wait(50); q = __calls.length === n ? q + 1 : 0 } };
      window.__go = async v => { lmaNavTo(v, { fast: true }); const want = lmaResolve(v); for (let i = 0; i < 200 && NativeTabs.has(want) && !NativeTabs.isOpen(want); i++) await wait(25); await quiet(); return lmaView() === want };
      window.__swipe = (plugin, e) => {
        const P = Capacitor.Plugins[plugin], n = __calls.length, view = lmaView(), stack = lmaNavStack.length, html = document.documentElement;
        if (e.session === true) e.session = P.__session();
        P.__emit('swipe', Object.assign({ sequence: 1, via: 'drag' }, e));
        return { calls: __calls.slice(n), view: lmaView(), moved: view !== lmaView(), stack: lmaNavStack.length - stack, reveal: html.classList.contains('swipe-reveal'), cover: html.classList.contains('native-cover'), dir: html.dataset.swipeDir };
      };
      /* the fade-in and slide animations of the page's cards that are running now */
      window.__slides = () => { const v = document.querySelector('.view.active'); return v ? v.getAnimations({ subtree: true }).filter(a => /^view(In|Slide)/.test(a.animationName || '')).length : -1 };
    });
    return p;
  };
  const p = await open();
  ok('the swipe state says the page takes a swipe into a tab it draws (reveal)', await p.evaluate(() => NativeSwipe.state().reveal === true));

  /* ---------- native -> web: answered "web", revealed ---------- */
  ok('Food opens', await p.evaluate(() => __go('food')));
  ok('its chromeScene carries reveal', await p.evaluate(() => __calls.filter(c => c.p === 'NativeNutrition' && c.m === 'open').pop().reveal));
  let r = await p.evaluate(() => __swipe('NativeNutrition', { token: 'meals#1', from: 'meals', to: 'pet', dir: 1, reveal: true, session: true }));
  ok('Food -> Cat is answered "web" at once, before anything else, to Food\'s session', r.calls.length >= 1 && r.calls[0].m === 'swipeSettle' && r.calls[0].outcome === 'web' && r.calls[0].token === 'meals#1' && r.calls[0].view === 'meals' && !r.calls[0].reason, r.calls);
  ok('the page went to Cat, recorded for Back, with swipe-reveal on and no web slide', r.view === 'pet' && r.stack === 1 && r.reveal && r.dir === undefined, r);
  ok('and Food was closed at once (a web tab: nothing takes over)', r.calls.some(c => c.p === 'NativeNutrition' && c.m === 'close'), r.calls);
  let anim = await p.evaluate(async () => { await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))); return { slides: __slides(), reveal: document.documentElement.classList.contains('swipe-reveal') } });
  ok('no fade-in or slide of Cat\'s cards runs under the veil', anim.slides === 0 && anim.reveal, anim);
  anim = await p.evaluate(async () => { await wait(2600); return document.documentElement.classList.contains('swipe-reveal') });
  ok('swipe-reveal goes after its 2.5 s cap', anim === false);
  anim = await p.evaluate(async () => { lmaNavTo('quests'); await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))); return __slides() });
  ok('(control: an ordinary change of page does fade its cards in, so the check above can see one)', anim > 0, anim);

  ok('Character opens', await p.evaluate(() => __go('character')));
  r = await p.evaluate(() => { const r = __swipe('NativeCharacter', { token: 'character#1', from: 'character', to: 'food', dir: 1, reveal: true }); return r });
  ok('a native neighbour is still answered "native" (phase 1 unchanged), with no swipe-reveal', r.calls[0].outcome === 'native' && r.view === 'meals' && !r.reveal && r.cover, r);
  ok('Food opens', await p.evaluate(async () => { for (let i = 0; i < 200 && !NativeTabs.isOpen('meals'); i++) await wait(25); await quiet(); return NativeTabs.isOpen('meals') }));
  r = await p.evaluate(() => __swipe('NativeNutrition', { token: 'meals#2', from: 'meals', to: 'pet', dir: 1, reveal: true, session: true }));
  ok('swipe-reveal is on again for the next reveal', r.calls[0].outcome === 'web' && r.reveal, r);
  r = await p.evaluate(async () => { lmaNavTo('dashboard', { fast: true }); return document.documentElement.classList.contains('swipe-reveal') });
  ok('and goes at once at the next navigate', r === false);

  ok('Character opens', await p.evaluate(() => __go('character')));
  r = await p.evaluate(() => __swipe('NativeCharacter', { token: 'character#2', from: 'character', to: 'dashboard', dir: -1, reveal: true }));
  ok('Character -> Overview (the live shell, the page\'s body) is answered "web", and the page goes to the Overview', r.calls[0].outcome === 'web' && r.view === 'dashboard' && r.reveal, r);
  ok('the Overview\'s live shell opens', await p.evaluate(async () => { for (let i = 0; i < 100 && !NativeTabs.isOpen('dashboard'); i++) await wait(25); return __calls.some(c => c.p === 'NativeOverview' && c.m === 'open') }));
  ok('Workout plan opens', await p.evaluate(() => __go('plan')));
  r = await p.evaluate(() => __swipe('NativePlan', { token: 'plan#1', from: 'plan', to: 'library', dir: 1, reveal: true }));
  ok('Workout plan -> Library is native', r.calls[0].outcome === 'native', r);
  ok('Library opens', await p.evaluate(async () => { for (let i = 0; i < 200 && !NativeTabs.isOpen('media'); i++) await wait(25); await quiet(); return NativeTabs.isOpen('media') }));
  r = await p.evaluate(() => { const plan = state.plan; state.plan = null; try { return __swipe('NativeMedia', { token: 'media#1', from: 'media', to: 'plan', dir: -1, reveal: true }) } finally { state.plan = plan } });
  ok('a native tab whose screen would not open now (Workout plan with no plan) is answered "web"', r.calls[0].outcome === 'web' && r.view === 'plan', r);
  ok('Library opens', await p.evaluate(() => __go('library')));
  r = await p.evaluate(() => __swipe('NativeMedia', { token: 'media#2', from: 'media', to: 'money', dir: 1, reveal: true }));
  ok('Money stays a wall: refused "kind", even from an APK that reveals', r.calls.length === 1 && r.calls[0].outcome === 'refused' && r.calls[0].reason === 'kind' && !r.moved, r);
  ok('Food opens', await p.evaluate(() => __go('food')));
  r = await p.evaluate(() => __swipe('NativeNutrition', { token: 'meals#3', from: 'meals', to: 'pet', dir: 1, session: true }));
  ok('a swipe into a web tab from an APK that does not say it reveals is refused "kind", as in phase 1', r.calls.length === 1 && r.calls[0].outcome === 'refused' && r.calls[0].reason === 'kind' && !r.moved && !r.reveal, r);

  /* ---------- web -> native: the screen slides in ---------- */
  ok('the page goes to Cat (a web page)', await p.evaluate(() => __go('pet')));
  const enter = (to, dir, speed) => p.evaluate(async ([to, dir, speed]) => {
    const html = document.documentElement, n = __calls.length; html.classList.remove('native-cover');
    let hiddenAfterNav = false;
    lmaSwipeGo(to, dir, speed); hiddenAfterNav = html.classList.contains('native-enter');
    const r = { view: lmaView(), dir: html.dataset.swipeDir, cover: html.classList.contains('native-cover'), hiddenAfterNav };
    const want = lmaResolve(to); for (let i = 0; i < 200 && !NativeTabs.isOpen(want); i++) await wait(10);
    r.open = __calls.slice(n).find(c => c.m === 'open'); r.hiddenAtOpen = html.classList.contains('native-enter');
    await wait(600); r.hiddenLater = html.classList.contains('native-enter');
    return r;
  }, [to, dir, speed]);
  r = await enter('food', -1, 1.4);
  ok('Cat -> Food by the page\'s swipe: Food\'s open says enter "prev" with the finger\'s speed (px/s)', r.view === 'meals' && r.open && r.open.p === 'NativeNutrition' && r.open.enter === 'prev' && r.open.velocity === 1400, r);
  ok('the page\'s copy of Food is hidden from the swipe until the screen is in place, and no web slide is set', r.hiddenAfterNav && r.open.hidden && r.dir === undefined && r.cover, r);
  ok('native-enter is cleared once the screen\'s slide is done', r.hiddenLater === false, r);
  ok('the page goes to Cat again', await p.evaluate(() => __go('pet')));
  r = await enter('log', 1, 0.6);
  ok('Cat -> Log: the kit\'s open says enter "next" with the speed', r.view === 'log' && r.open && r.open.p === 'NativeLog' && r.open.enter === 'next' && r.open.velocity === 600 && r.hiddenLater === false, r);
  r = await p.evaluate(async () => { const n = __calls.length; Capacitor.Plugins.NativeLog.__emit('refresh', {}); await quiet(); lmaNavTo('character', { fast: true }); await quiet(); return __calls.slice(n).filter(c => c.m === 'open').map(c => [c.p, c.enter]) });
  ok('a later plain open passes no enter', r.length >= 1 && r.every(([, e]) => e === undefined), r);
  ok('the page goes to the Overview', await p.evaluate(() => __go('dashboard')));
  r = await enter('character', 1, 0);
  ok('from the Overview\'s web body to Character the screen slides in too', r.view === 'character' && r.open && r.open.p === 'NativeCharacter' && r.open.enter === 'next' && r.open.velocity === 0, r);
  ok('the page goes to Cat', await p.evaluate(() => __go('pet')));
  r = await p.evaluate(async () => { lmaSwipeGo('food', -1, 1); const hidden = document.documentElement.classList.contains('native-enter'); lmaNavTo('quests'); await wait(100); return { hidden, after: document.documentElement.classList.contains('native-enter'), view: lmaView() } });
  ok('another navigate meanwhile shows the page at once', r.hidden && r.after === false, r);
  await p.evaluate(() => quiet());
  ok('the page goes to Cat', await p.evaluate(() => __go('pet')));
  r = await p.evaluate(async () => { const html = document.documentElement; html.classList.add('reduce-motion'); const n = __calls.length; lmaSwipeGo('food', -1, 1); const r = { hidden: html.classList.contains('native-enter') }; await quiet(); html.classList.remove('reduce-motion'); r.open = __calls.slice(n).find(c => c.m === 'open'); return r });
  ok('with reduced motion the screen just opens (no enter)', !r.hidden && r.open && r.open.enter === undefined, r);
  ok('the page goes to Cat', await p.evaluate(() => __go('pet')));
  r = await p.evaluate(async () => { lmaLoad().nativeSwipe = false; const html = document.documentElement, n = __calls.length; lmaSwipeGo('food', -1, 1); const r = { hidden: html.classList.contains('native-enter'), dir: html.dataset.swipeDir }; await quiet(); lmaLoad().nativeSwipe = true; r.open = __calls.slice(n).find(c => c.m === 'open'); return r });
  ok('with Native tab swipe off nothing changes: the web slide, and a plain open', !r.hidden && r.dir === 'prev' && r.open && r.open.enter === undefined, r);
  ok('the page goes to Cat', await p.evaluate(() => __go('pet')));
  r = await p.evaluate(async () => { const html = document.documentElement; lmaSwipeGo('quests', 1, 1); return { hidden: html.classList.contains('native-enter'), dir: html.dataset.swipeDir, view: lmaView() } });
  ok('web -> web stays the web swipe', r.view === 'quests' && !r.hidden && r.dir === 'next', r);
  ok('no script errors', p.errs.length === 0, p.errs);
  await p.context().close();

  /* ---------- an APK from before phase 2 ---------- */
  const q = await open({ edges: false });
  ok('(older APK) the page goes to Cat', await q.evaluate(async () => { await wait(800); return __go('pet') }));
  r = await q.evaluate(async () => { const html = document.documentElement, n = __calls.length; lmaSwipeGo('food', -1, 1); const r = { hidden: html.classList.contains('native-enter'), dir: html.dataset.swipeDir }; await quiet(); r.open = __calls.slice(n).find(c => c.m === 'open'); return r });
  ok('on an APK that does not draw the edges the web swipe is as before (no enter)', !r.hidden && r.dir === 'prev' && r.open && r.open.enter === undefined, r);
  ok('no script errors (older APK)', q.errs.length === 0, q.errs);
  await q.context().close();

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
