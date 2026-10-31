/* Native tab swipe, the page's half (phase 1). Native screens slide between neighbouring tabs; the page decides. Fake native plugins
   stand in for Android: they log every call in one list, in the order the page makes them (the order Capacitor hands them to
   Android), and send 'swipe' as a native screen does when a release commits. Checked:
   - the swipe state in the bar scenes: the bar's tabs in order (reordered, hidden, the Food and Library panes), what each tab is to
     a swipe (from the tab registry), when a swipe may start, motion, haptics, the theme's colours and the tuning;
   - every reason the page refuses a swipe: answered at once, and nothing else done;
   - a swipe the page takes: answered before the destination's screen is asked to open, then the page goes there as a bar tap does
     (no View Transition, no web slide, the page covered, recorded for Back, one tab lit);
   - the Settings switch (off by default, usable only on an APK that draws the swipe) and the tuning in Developer mode.
   Run: NODE_PATH=$(npm root -g) node tests/native-swipe.test.js [built html] */
const { chromium } = require('playwright'); const file = require('./current-page')(process.argv[2]);
const directorDay = require('./fixtures/director-day.js');
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 600) : '')) } };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
// the defaults ADR 0011 records, which native's SwipeTune.kt holds too (SwipeTuneTest checks the page's against it)
const ADR_TUNE = { knee: 0.18, breakAt: 0.45, heavy: 0.25, hyst: 0.06, popDamping: 0.65, popStiffness: 1200, wallMax: 0.08, wallSlope: 0.35, flingDp: 1200, flingMin: 0.12, backFlingDp: 600, commitK: 1000, cancelK: 800, slopFactor: 1, lockRatio: 1.3, edgeDp: 24, haptic: 'threshold' };

/* swipe: whether the APK draws the swipe (its kit handshake and Food's capabilities say so); native: whether there is an APK at all */
const fakes = ({ swipe = true, native = true } = {}) => `
  localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
    plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
  localStorage.setItem('lifemax_director_state_v1', ${JSON.stringify(JSON.stringify(directorDay.state()))});
  localStorage.setItem('lma_qdeal','off');
  window.__calls=[];window.__scenes={};
  const mk=name=>{const ls={};let current='';
    const log=(m,a)=>{const s=a&&(a.navScene||a.chromeScene);if(s)window.__scenes[name]=s.swipe;window.__calls.push({p:name,m,view:typeof lmaView==='function'?lmaView():'',token:a&&a.token,outcome:a&&a.outcome,reason:a&&a.reason,session:a&&a.session,swipe:s&&s.swipe})};
    const emit=(e,d)=>(ls[e]||[]).forEach(f=>f(d));
    return {__emit:emit,__session:()=>current,
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{current=a&&a.session;log('open',a)},
      update:async a=>{log('update',a)},
      close:async a=>{log('close',a);setTimeout(()=>emit('closed',{session:a&&a.session}),0)},
      swipeSettle:async a=>{log('swipeSettle',a)},
      capabilities:async()=>(${swipe}?{liveWeb:true,version:1,authoritativeData:true,swipe:1}:{liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','log.projection.v1','plan.projection.v1','director.today.v1','recipes.projection.v1','library.media.projection.v1'].concat(${swipe}?['shell.swipe.v1']:[])})}};
  const P={};for(const n of ['NativeOverview','NativeCharacter','NativeNutrition','NativeLog','NativePlan','NativeLifeDirector','NativeRecipes','NativeMedia','NativeCash'])P[n]=mk(n);
  if(${native})window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const open = async (opts) => { const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); p.errs = []; p.on('pageerror', e => p.errs.push(String(e))); await p.addInitScript(fakes(opts)); await p.goto('file://' + file); return p };
  const p = await open();
  await p.waitForFunction(() => window.NativeSwipe && !document.getElementById('nativeSwipeToggle').disabled, null, { timeout: 20000 }).catch(() => { });

  // Settings, before anything is changed: the switch is off by default, and usable because this APK draws the swipe
  const row = await p.evaluate(async () => {
    const input = document.getElementById('nativeSwipeToggle'), line = document.getElementById('nativeSwipeSetting'), scene = await NativeOverviewSnapshot.snapshot();
    return { stored: lmaLoad().nativeSwipe, checked: input.checked, disabled: input.disabled, label: line.querySelector('strong').textContent, hint: document.getElementById('nativeSwipeHint').textContent,
      card: line.closest('.card').id, after: !!line.previousElementSibling.querySelector('#lmaSwipeToggle'), state: NativeSwipe.state() === undefined ? 'none' : 'some', scene: scene.swipe === undefined ? 'none' : 'some', bar: scene.nav.nodes.length > 0 };
  });
  ok('Settings, Navigation has "Native tab swipe (beta)" right after "Swipe to switch tabs"', row.card === 'lmaNavCard' && row.after && row.label === 'Native tab swipe (beta)', row);
  ok('its hint says what it does and what it needs', row.hint === 'Swipe between native tabs. The page holds back until you pull past the halfway catch. Needs Swipe to switch tabs.', row.hint);
  ok('it is off by default (nothing stored, unchecked)', row.stored === undefined && !row.checked, row);
  ok('switched off, the page builds no swipe state and the bar scene goes without one (native does nothing of the swipe)', row.state === 'none' && row.scene === 'none' && row.bar, row);
  ok('it is usable once a plugin reports shell.swipe.v1', !row.disabled, row);

  // the native tabs on, as an owner trying the beta has them, and the switch turned on by tapping it
  await p.evaluate(() => {
    lmaThumbWarm = async () => { }; // the first-install thumbnail pass would walk the page through every tab
    Object.assign(lmaLoad(), { nativeCharacter: true, nativeNutrition: true, nativeLog: true, nativePlan: true, nativeLibrary: true, nativeRecipes: true, nativeDirector: true }); lmaSave();
    document.getElementById('nativeSwipeToggle').click();
  });
  ok('tapping the switch turns the swipe on and keeps it', await p.evaluate(() => lmaLoad().nativeSwipe === true && document.getElementById('nativeSwipeToggle').checked));

  // the page goes to a tab and waits until its screen is open and the calls have stopped
  const go = (v) => p.evaluate(async (v) => {
    const wait = ms => new Promise(r => setTimeout(r, ms)); lmaNavTo(v, { fast: true }); const want = lmaResolve(v);
    for (let i = 0; i < 200 && !NativeTabs.isOpen(want); i++) await wait(25);
    for (let n = window.__calls.length, quiet = 0; quiet < 8; n = window.__calls.length) { await wait(50); quiet = window.__calls.length === n ? quiet + 1 : 0 }
    return NativeTabs.isOpen(want);
  }, v);
  // in the page: a native screen sends a committed swipe; what was called at once, and where the page is
  await p.evaluate(() => {
    window.__swipe = (plugin, e) => {
      const P = Capacitor.Plugins[plugin], n = window.__calls.length, view = lmaView(), stack = lmaNavStack.length;
      if (e && e.session === true) e.session = P.__session(); // the session of the screen showing now
      P.__emit('swipe', e);
      return { calls: window.__calls.slice(n), view: lmaView(), moved: view !== lmaView(), stack: lmaNavStack.length - stack };
    };
    window.__lit = () => [...document.querySelectorAll('.navbtn.active')].map(b => b.dataset.view);
  });
  const state = () => p.evaluate(() => NativeSwipe.state());

  /* ---------- the swipe state in the bar scenes ---------- */
  ok('LMA_TABSWIPE is exactly the defaults ADR 0011 records', await p.evaluate((t) => JSON.stringify(Object.entries(LMA_TABSWIPE).sort()) === JSON.stringify(Object.entries(t).sort()) && Object.isFrozen(LMA_TABSWIPE), ADR_TUNE));
  ok('Food opens', await go('food'));
  const food = await p.evaluate(() => window.__calls.filter(c => c.p === 'NativeNutrition' && c.m === 'open').pop());
  ok('Food\'s chromeScene carries the swipe state, on Meals under the Food tab', food && food.swipe && food.swipe.v === 1 && food.swipe.view === 'meals' && food.swipe.active === 'food' && food.swipe.on === true, food);
  ok('Character opens', await go('character'));
  const ch = await p.evaluate(() => window.__calls.filter(c => c.p === 'NativeCharacter' && c.m === 'open').pop());
  ok('Character\'s navScene carries the swipe state', ch && ch.swipe && ch.swipe.v === 1 && ch.swipe.view === 'character' && ch.swipe.active === 'character' && ch.swipe.on === true, ch);
  let s = await state();
  ok('the tabs are the bar in order', same(s.tabs.map(t => t.key), ['dashboard', 'character', 'food', 'pet', 'log', 'quests', 'plan', 'library', 'money', 'life-director']), s.tabs.map(t => t.key));
  ok('each with the view it opens (Food on Meals, Library on Media)', same(s.tabs.map(t => t.view), ['dashboard', 'character', 'meals', 'pet', 'log', 'quests', 'plan', 'media', 'money', 'life-director']), s.tabs.map(t => t.view));
  ok('and its label and icon', same(s.tabs.map(t => t.label), ['Overview', 'Character', 'Food', 'Cat', 'Log activity', 'Quests', 'Workout plan', 'Library', 'Money', 'Life Director']) && same(s.tabs.map(t => t.icon), ['grid', 'person', 'heart', 'paw', 'plus', 'flag', 'list', 'book', 'coin', 'dial']), s.tabs.map(t => [t.label, t.icon]));
  // Life Director's page is put on the page at its first visit, and until then its screen has nothing to cover: a web page to a swipe
  ok('kinds come from the registry: native screens, web pages, the Overview\'s web body, Money a stop', same(Object.fromEntries(s.tabs.map(t => [t.key, t.kind])), { dashboard: 'web', character: 'native', food: 'native', pet: 'web', log: 'native', quests: 'web', plan: 'native', library: 'native', money: 'stop', 'life-director': 'web' }), s.tabs.map(t => t.key + ':' + t.kind));
  ok('Life Director, and only it, is never a source', same(s.tabs.filter(t => 'source' in t).map(t => [t.key, t.source]), [['life-director', false]]), s.tabs);
  ok('the Overview is covered by its native bars, yet a web page to a swipe', await p.evaluate(() => NativeTabs.covered('dashboard') && NativeTabs.webBody('dashboard') && NativeSwipe.kind('dashboard') === 'web'));
  ok('the theme colours as ARGB, opaque', await p.evaluate(() => { const c = NativeSwipe.state().colors, css = getComputedStyle(document.documentElement), at = k => NativeTabKit.color(css.getPropertyValue('--' + k).trim()); return c.bg === at('bg') && c.text === at('text') && c.muted === at('muted') && [c.bg, c.text, c.muted].every(x => x >>> 24 === 255) && c.bg !== c.text }));
  ok('and they follow the theme (light mode)', await p.evaluate(() => { const dark = NativeSwipe.state().colors, html = document.documentElement; html.classList.add('light-theme'); const light = NativeSwipe.state().colors; html.classList.remove('light-theme'); return light.bg !== dark.bg && light.text !== dark.text }));
  ok('motion, haptics and tuning as the page has them: motion on, normal haptics, no tuning', s.motion === true && s.haptics === 'normal' && s.tune === null, s);

  const order = await p.evaluate(() => {
    const keys = () => NativeSwipe.state().tabs.map(t => t.key);
    lmaTabMove('pet', 1); const moved = keys();
    lmaTabShow('quests', false); const hidden = keys();
    lmaTabsReset(); return { moved, hidden, reset: keys() };
  });
  ok('a reordered bar is followed (Cat moved after Log)', same(order.moved.slice(0, 5), ['dashboard', 'character', 'food', 'log', 'pet']), order.moved);
  ok('a hidden tab is left out (Quests hidden)', !order.hidden.includes('quests') && order.hidden.length === 9, order.hidden);
  ok('and the bar put back is followed too', same(order.reset, s.tabs.map(t => t.key)), order.reset);
  const panes = await p.evaluate(() => {
    const tab = k => NativeSwipe.state().tabs.find(t => t.key === k), was = [state.foodPane, state.libPane];
    state.foodPane = 'recipes'; state.libPane = 'history'; const r = { food: tab('food'), library: tab('library') };
    [state.foodPane, state.libPane] = was; return r;
  });
  ok('the Food tab on Recipes goes to Recipes, natively', panes.food.view === 'recipes' && panes.food.kind === 'native', panes.food);
  ok('the Library tab on the Journal goes to the Journal, a web page', panes.library.view === 'history' && panes.library.kind === 'web', panes.library);
  const kinds = await p.evaluate(() => {
    const k = v => NativeSwipe.kind(v), L = lmaLoad(), r = {};
    L.nativeCharacter = false; r.characterOff = k('character'); L.nativeCharacter = true;
    const plan = state.plan; state.plan = null; r.planBlocked = k('plan'); r.planCovered = NativeTabs.covered('plan'); state.plan = plan;
    const cash = Capacitor.Plugins.NativeCash; delete Capacitor.Plugins.NativeCash; r.noCash = k('money'); Capacitor.Plugins.NativeCash = cash;
    return r;
  });
  ok('a native tab switched off is a web page to a swipe', kinds.characterOff === 'web', kinds);
  ok('a native tab whose screen would not open now (Workout plan with no plan) is a web page to a swipe', kinds.planBlocked === 'web' && kinds.planCovered === true, kinds);
  ok('Money without the Cash plugin is a web page, not a stop', kinds.noCash === 'web', kinds);

  const flags = await p.evaluate(() => {
    const on = () => NativeSwipe.state().on, L = lmaLoad(), html = document.documentElement, body = document.body, r = { base: on() };
    L.nativeSwipe = false; r.switchOff = NativeSwipe.state() === undefined ? 'none' : 'some'; L.nativeSwipe = true;
    L.swipe = false; r.swipeOff = on(); L.swipe = true;
    state.onboardingComplete = false; r.onboarding = on(); state.onboardingComplete = true;
    lmaThumbWarmBusy = true; r.warm = on(); lmaThumbWarmBusy = false;
    const sheet = document.createElement('div'); sheet.className = 'sheet-ov show'; body.appendChild(sheet); r.overlay = on(); sheet.remove();
    body.classList.add('noscroll'); r.noscroll = on(); body.classList.remove('noscroll');
    const view = document.querySelector('.view.active'); view.classList.add('jlocked'); r.lock = on(); view.classList.remove('jlocked');
    html.classList.add('reduce-motion'); r.reduced = NativeSwipe.state().motion; html.classList.remove('reduce-motion');
    L.swipeAnim = false; r.noAnim = NativeSwipe.state().motion; delete L.swipeAnim;
    const fx = state.fx; state.fx = Object.assign({}, fx, { hapticLevel: 'strong' }); r.strong = NativeSwipe.state().haptics;
    state.fx = Object.assign({}, fx, { haptics: false }); r.noHaptics = NativeSwipe.state().haptics; state.fx = fx;
    r.after = on(); return r;
  });
  ok('the swipe is on for a native screen with the switch on', flags.base === true && flags.after === true, flags);
  ok('no swipe state with the switch off, and off with Swipe to switch tabs off', flags.switchOff === 'none' && flags.swipeOff === false, flags);
  ok('off before onboarding is done and during the thumbnail pass', flags.onboarding === false && flags.warm === false, flags);
  ok('off under a sheet, a body that must not scroll, or a lock cover', flags.overlay === false && flags.noscroll === false && flags.lock === false, flags);
  ok('motion off with reduced motion or Swipe animation off', flags.reduced === false && flags.noAnim === false, flags);
  ok('haptics follow the page: its level, or off', flags.strong === 'strong' && flags.noHaptics === 'off', flags);

  /* ---------- every refusal: answered at once, nothing else done ---------- */
  const refused = (r, plugin, token, reason) => r.calls.length === 1 && r.calls[0].p === plugin && r.calls[0].m === 'swipeSettle' && r.calls[0].token === token && r.calls[0].outcome === 'refused' && r.calls[0].reason === reason && !r.moved && r.stack === 0;
  const fromCharacter = (extra) => Object.assign({ token: 'character#1', from: 'character', to: 'food', dir: 1, sequence: 1, via: 'drag' }, extra);
  let r = await p.evaluate((e) => { lmaLoad().nativeSwipe = false; const r = __swipe('NativeCharacter', e); lmaLoad().nativeSwipe = true; return r }, fromCharacter());
  ok('refused "off" with the switch off', refused(r, 'NativeCharacter', 'character#1', 'off'), r);
  r = await p.evaluate((e) => { lmaLoad().swipe = false; const r = __swipe('NativeCharacter', e); lmaLoad().swipe = true; return r }, fromCharacter({ token: 'character#2' }));
  ok('refused "off" with Swipe to switch tabs off', refused(r, 'NativeCharacter', 'character#2', 'off'), r);
  r = await p.evaluate((e) => { const sheet = document.createElement('div'); sheet.className = 'sheet-ov show'; document.body.appendChild(sheet); const r = __swipe('NativeCharacter', e); sheet.remove(); return r }, fromCharacter({ token: 'character#3' }));
  ok('refused "overlay" under a sheet', refused(r, 'NativeCharacter', 'character#3', 'overlay'), r);
  r = await p.evaluate((e) => { document.body.classList.add('noscroll'); const r = __swipe('NativeCharacter', e); document.body.classList.remove('noscroll'); return r }, fromCharacter({ token: 'character#4' }));
  ok('refused "overlay" while the body must not scroll', refused(r, 'NativeCharacter', 'character#4', 'overlay'), r);
  r = await p.evaluate((e) => { const v = document.querySelector('.view.active'); v.classList.add('jlocked'); const r = __swipe('NativeCharacter', e); v.classList.remove('jlocked'); return r }, fromCharacter({ token: 'character#5' }));
  ok('refused "overlay" under a lock cover', refused(r, 'NativeCharacter', 'character#5', 'overlay'), r);
  r = await p.evaluate((e) => __swipe('NativeCharacter', e), fromCharacter({ token: 'character#6', from: 'meals' }));
  ok('refused "stale" when the swipe names another source view', refused(r, 'NativeCharacter', 'character#6', 'stale'), r);
  r = await p.evaluate((e) => __swipe('NativeCharacter', e), fromCharacter({ token: 'character#7', to: 'pet' }));
  ok('refused "stale" when its tab is not the one next to the source on the bar now', refused(r, 'NativeCharacter', 'character#7', 'stale'), r);
  r = await p.evaluate((e) => __swipe('NativeCharacter', e), fromCharacter({ token: 'character#8', to: 'pet', dir: 2 }));
  ok('refused "stale" for a step other than one tab', refused(r, 'NativeCharacter', 'character#8', 'stale'), r);
  r = await p.evaluate((e) => __swipe('NativeNutrition', e), { token: 'meals#1', from: 'meals', to: 'character', dir: -1, sequence: 1, via: 'drag', session: true });
  ok('refused "stale" from a screen whose view the page has left (Food, under Character), with Food\'s session in the answer', refused(r, 'NativeNutrition', 'meals#1', 'stale') && !!r.calls[0].session, r);
  r = await p.evaluate((e) => { Capacitor.Plugins.NativeCharacter.__emit('closed', {}); return __swipe('NativeCharacter', e) }, fromCharacter({ token: 'character#9' }));
  ok('refused "stale" when the source screen is no longer open', refused(r, 'NativeCharacter', 'character#9', 'stale'), r);
  ok('Character opens again', await go('character'));
  r = await p.evaluate((e) => __swipe('NativeCharacter', e), fromCharacter({ token: 'character#10', to: 'dashboard', dir: -1 }));
  ok('refused "kind" toward the Overview (its body is the web page)', refused(r, 'NativeCharacter', 'character#10', 'kind'), r);
  r = await p.evaluate((e) => { lmaLoad().nativeNutrition = false; const r = __swipe('NativeCharacter', e); lmaLoad().nativeNutrition = true; return r }, fromCharacter({ token: 'character#11' }));
  ok('refused "kind" toward a tab drawn by the web (Food switched off)', refused(r, 'NativeCharacter', 'character#11', 'kind'), r);
  r = await p.evaluate((e) => { const t = lmaTabList; lmaTabList = () => { throw Error('test') }; try { return __swipe('NativeCharacter', e) } finally { lmaTabList = t } }, fromCharacter({ token: 'character#12' }));
  ok('a failure while deciding answers "refused" (reason "error") and goes nowhere', refused(r, 'NativeCharacter', 'character#12', 'error'), r);
  const scene = await p.evaluate(async () => { const t = lmaTabList; lmaTabList = () => { throw Error('test') }; try { const s = await NativeOverviewSnapshot.snapshot(); return { version: s.version, sequence: s.sequence, bar: s.nav.nodes.length > 0, swipe: s.swipe === undefined ? 'none' : s.swipe } } finally { lmaTabList = t } });
  ok('a swipe state that cannot be built leaves the bar scene without it, and the scene still goes out', scene.version === 2 && scene.sequence > 0 && scene.bar && scene.swipe === 'none', scene);
  r = await p.evaluate(() => __swipe('NativeCharacter', null));
  ok('a malformed event is refused, not thrown', r.calls.length === 1 && r.calls[0].outcome === 'refused' && !r.moved, r);
  ok('Food opens', await go('food'));
  r = await p.evaluate((e) => __swipe('NativeNutrition', e), { token: 'meals#2', from: 'meals', to: 'character', dir: -1, sequence: 1, via: 'drag', session: 'nutrition_ended' });
  ok('refused "stale" from a Food screen session that has ended, answered to that session', refused(r, 'NativeNutrition', 'meals#2', 'stale') && r.calls[0].session === 'nutrition_ended', r);
  ok('Library opens', await go('library'));
  r = await p.evaluate((e) => __swipe('NativeMedia', e), { token: 'media#1', from: 'media', to: 'money', dir: 1, sequence: 1, via: 'fling' });
  ok('refused "kind" toward Money, a stop', refused(r, 'NativeMedia', 'media#1', 'kind'), r);
  r = await p.evaluate((e) => { const plan = state.plan; state.plan = null; try { return __swipe('NativeMedia', e) } finally { state.plan = plan } }, { token: 'media#2', from: 'media', to: 'plan', dir: -1, sequence: 1, via: 'drag' });
  ok('refused "kind" toward a native tab whose screen would not open now', refused(r, 'NativeMedia', 'media#2', 'kind'), r);

  /* ---------- a swipe the page takes ---------- */
  ok('Character opens', await go('character'));
  const taken = await p.evaluate(() => {
    const html = document.documentElement, seen = { vt: false, dir: false }, vt = document.startViewTransition; let vtCalls = 0;
    document.startViewTransition = function () { vtCalls++; return vt.apply(this, arguments) };
    window.__taken = { vtCalls: () => vtCalls, seen, vtAvailable: typeof vt === 'function' && lmaVTok() }; // a bar tap without fast would slide
    new MutationObserver(() => { if (html.dataset.vt) seen.vt = true; if (html.dataset.swipeDir) seen.dir = true }).observe(html, { attributes: true });
    html.classList.remove('native-cover'); html.dataset.swipeDir = 'next'; // as a web swipe leaves them
    const before = lmaNavStack.slice(-1)[0];
    const r = __swipe('NativeCharacter', { token: 'character#20', from: 'character', to: 'food', dir: 1, sequence: 1, via: 'drag' });
    return Object.assign(r, { cover: html.classList.contains('native-cover'), dir: html.dataset.swipeDir, top: lmaNavStack.slice(-1)[0], before, lit: __lit() });
  });
  ok('taken: answered "native" at once, and nothing else called yet', taken.calls.length === 1 && taken.calls[0].m === 'swipeSettle' && taken.calls[0].p === 'NativeCharacter' && taken.calls[0].token === 'character#20' && taken.calls[0].outcome === 'native' && !taken.calls[0].reason, taken.calls);
  ok('the page went to the tab (Food, on Meals)', taken.view === 'meals', taken);
  ok('the page under the screens is covered, and the web slide is off', taken.cover && taken.dir === undefined, taken);
  ok('Back returns to Character (the swipe is recorded like a tab change)', taken.stack === 1 && taken.top === 'character', taken);
  ok('exactly one tab is lit, the destination\'s', same(taken.lit, ['food']), taken.lit);
  await p.evaluate(async () => { const wait = ms => new Promise(r => setTimeout(r, ms)); for (let i = 0; i < 200 && !NativeTabs.isOpen('meals'); i++) await wait(25); await wait(500) });
  const hop = await p.evaluate(() => {
    const at = (p, m) => window.__calls.findIndex(c => c.p === p && c.m === m && (m !== 'swipeSettle' || c.token === 'character#20'));
    const settled = at('NativeCharacter', 'swipeSettle'), calls = window.__calls.slice(settled);
    return { settle: 0, open: calls.findIndex(c => c.p === 'NativeNutrition' && c.m === 'open'), close: calls.findIndex(c => c.p === 'NativeCharacter' && c.m === 'close'), vt: window.__taken.vtCalls(), vtAvailable: window.__taken.vtAvailable, seen: window.__taken.seen, lit: __lit(), open2: NativeTabs.isOpen('meals') };
  });
  ok('the answer reached native before the destination\'s open', hop.open > hop.settle, hop);
  ok('and the screen left closed only after the new one opened (the hand-over)', hop.open2 && hop.close > hop.open, hop);
  ok('no View Transition was started (this browser has them), and no data-vt or data-swipe-dir was set', hop.vtAvailable && hop.vt === 0 && !hop.seen.vt && !hop.seen.dir, hop);
  ok('still exactly one tab lit once it settled', same(hop.lit, ['food']), hop.lit);
  const back = await p.evaluate(() => {
    const r = __swipe('NativeNutrition', { token: 'meals#3', from: 'meals', to: 'character', dir: -1, sequence: 1, via: 'fling', session: true });
    return Object.assign(r, { session: Capacitor.Plugins.NativeNutrition.__session(), lit: __lit() });
  });
  ok('from Food back to Character: answered "native" to Food\'s session, then gone', back.calls.length === 1 && back.calls[0].outcome === 'native' && back.calls[0].session === back.session && back.view === 'character' && same(back.lit, ['character']), back);
  await p.evaluate(async () => { const wait = ms => new Promise(r => setTimeout(r, ms)); for (let i = 0; i < 200 && !NativeTabs.isOpen('character'); i++) await wait(25); await wait(300) });
  ok('Workout plan opens', await go('plan'));
  r = await p.evaluate(() => __swipe('NativePlan', { token: 'plan#1', from: 'plan', to: 'library', dir: 1, sequence: 1, via: 'drag' }));
  ok('Workout plan to Library: taken, and the page is on the Media pane', r.calls.length === 1 && r.calls[0].outcome === 'native' && r.view === 'media', r);

  /* ---------- the state on views a swipe never starts from ---------- */
  ok('Life Director opens', await go('life-director'));
  s = await state();
  ok('on Life Director the swipe is off (its own sideways gestures), though, visited, it can be a destination', s.on === false && s.view === 'life-director' && s.active === 'life-director' && s.tabs.find(t => t.key === 'life-director').kind === 'native', s);
  s = await p.evaluate(() => { lmaNavTo('settings', { fast: true }); return NativeSwipe.state() });
  ok('on a page not on the bar (Settings) nothing is active', s.active === '' && s.view === 'settings', s);

  /* ---------- Developer mode: Swipe feel (testing) ---------- */
  const tune = await p.evaluate(() => {
    const block = document.querySelector('#devModeTools #swipeTuneBlock'), v = k => document.getElementById('swipeTune-' + k), text = k => document.getElementById('swipeTune-' + k + '-value').textContent;
    return { title: block && block.querySelector('strong').textContent, values: ['knee', 'breakAt', 'heavy', 'flingDp', 'haptic'].map(k => v(k) && v(k).value), texts: ['knee', 'breakAt', 'heavy', 'flingDp'].map(text),
      reset: !![...block.querySelectorAll('button')].find(b => /Reset/.test(b.textContent)), options: [...v('haptic').options].map(o => o.value) };
  });
  ok('Developer mode has "Swipe feel (testing)": tension, catch point, heaviness, flick speed, detent haptic and a reset', tune.title === 'Swipe feel (testing)' && tune.reset && same(tune.options, ['threshold', 'tick', 'click', 'off']), tune);
  ok('showing the defaults', same(tune.values, ['0.18', '0.45', '0.25', '1200', 'threshold']) && tune.texts[0] === '18% of the screen width' && tune.texts[1] === '45% of the screen width', tune);
  const set = (k, v) => p.evaluate(([k, v]) => { const i = document.getElementById('swipeTune-' + k); i.value = v; i.dispatchEvent(new Event(i.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); return { stored: lmaLoad().swipeTune, tune: NativeSwipe.state().tune } }, [k, v]);
  let t = await set('knee', '0.22');
  ok('a change is kept in lmaLoad().swipeTune and sent as swipe.tune', same(t.stored, { knee: 0.22 }) && same(t.tune, { knee: 0.22 }), t);
  t = await set('haptic', 'tick');
  ok('the detent haptic too', same(t.tune, { knee: 0.22, haptic: 'tick' }), t);
  t = await set('breakAt', '0.2');
  const shown = await p.evaluate(() => [document.getElementById('swipeTune-breakAt').value, document.getElementById('swipeTune-breakAt-value').textContent]);
  ok('a catch point too near the knee shows where native will keep it (10% past the knee)', same(shown, ['0.32', '32% of the screen width']) && t.tune.breakAt === 0.2, [shown, t]);
  t = await set('knee', '0.18');
  ok('a value put back to its default is dropped', same(t.tune, { haptic: 'tick', breakAt: 0.2 }), t);
  ok('Character opens', await go('character'));
  const refresh = await p.evaluate(async () => {
    const n = window.__calls.length; Capacitor.Plugins.NativeCharacter.__emit('refresh', {});
    for (let i = 0; i < 100 && !window.__calls.slice(n).some(c => c.p === 'NativeCharacter' && c.m === 'update'); i++) await new Promise(r => setTimeout(r, 30));
    return window.__calls.slice(n).filter(c => c.p === 'NativeCharacter' && c.m === 'update').map(c => c.swipe && c.swipe.tune).pop();
  });
  ok('the open tab takes the tuning at its next refresh', same(refresh, { haptic: 'tick', breakAt: 0.2 }), refresh);
  t = await p.evaluate(() => { lmaResetSwipeTune(); return { stored: lmaLoad().swipeTune, tune: NativeSwipe.state().tune, values: ['knee', 'breakAt', 'haptic'].map(k => document.getElementById('swipeTune-' + k).value) } });
  ok('Reset puts every value back and sends no tuning', t.stored === undefined && t.tune === null && same(t.values, ['0.18', '0.45', 'threshold']), t);
  ok('no script errors', p.errs.length === 0, p.errs);

  /* ---------- an APK that does not draw the swipe, and a browser ---------- */
  for (const [name, opts, hint] of [['an older APK', { swipe: false }, 'Install the latest Android update to use this.'], ['a browser', { native: false }, 'Available in the Android app.']]) {
    const q = await open(opts);
    const o = await q.evaluate(async () => { await new Promise(r => setTimeout(r, 2500)); const i = document.getElementById('nativeSwipeToggle'); return { disabled: i.disabled, checked: i.checked, hint: document.getElementById('nativeSwipeHint').textContent } });
    ok('in ' + name + ' the switch stays disabled and says why', o.disabled && !o.checked && o.hint === hint, o);
    ok('no script errors in ' + name, q.errs.length === 0, q.errs);
  }

  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
