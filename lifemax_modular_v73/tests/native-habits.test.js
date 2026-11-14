/* Habits' place in the shell (50-native-habits.js). Habits is native from its data up (its rules and its file are tested in the
   Android source: feature/habits); the page holds only the bar's button, an empty view and the kit's open and close. Checked:
   - the page has no habits data and no rules: no window.Habits, no state.habits, nothing of it in the save;
   - in a browser (no plugin) the bar has no Habits button, so the tab is never an empty page;
   - in the app the button is on the bar, going there opens the screen with no switch stored, and what the page sends is only the
     screen's surroundings (palette, theme, motion, haptics), no data and no command;
   - Habits has its own sideways gestures: a tab swipe never starts on it, and it can be swiped to; leaving closes the screen.
   Run: NODE_PATH=$(npm root -g) node tests/native-habits.test.js [built html] */
const { chromium } = require('playwright');
const currentPage = require('./current-page'); const file = currentPage(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 700) : '')) } };
const SAVE = `if(!localStorage.getItem('lifemax_rpg_prototype_v1'))localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));`;
const FAKE = `
  window.__calls=[]; addEventListener('DOMContentLoaded',()=>{lmaThumbWarm=async()=>{}});
  const mk=caps=>{const ls={};return {__emit:(e,d)=>(ls[e]||[]).map(f=>f(d)),addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
    open:async a=>{window.__calls.push(['open',a])},update:async a=>{window.__calls.push(['update',a])},prime:async()=>({primed:false,reason:'busy'}),
    close:async()=>{window.__calls.push(['close']);(ls.closed||[]).forEach(f=>f({}))},swipeSettle:async()=>{},
    handshake:async()=>({protocol:1,capabilities:caps})}};
  const P={NativeHabits:mk(['habits.v1','shell.swipe.v1','shell.swipe.v2']),NativeCharacter:mk(['character.projection.v1','shell.swipe.v1'])};
  window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>!!P[n],getPlatform:()=>'android',Plugins:P,registerPlugin:()=>({}),convertFileSrc:u=>u};`;

(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const srv = await currentPage.serve(file);
  const page = async (script) => {
    const p = await (await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' })).newPage();
    p.errs = []; p.on('pageerror', e => p.errs.push(String(e)));
    await p.addInitScript(SAVE + script); await p.goto(srv.url); await p.waitForTimeout(2500);
    return p;
  };

  const web = await page('');
  ok('the page has no habits data and no rules', await web.evaluate(() => window.Habits === undefined && state.habits === undefined && !('habits' in JSON.parse(localStorage.getItem('lifemax_rpg_prototype_v1'))) && !(window.LifeMaxFeatures && LifeMaxFeatures.list().includes('habits'))));
  ok('in a browser the bar has no Habits button', await web.evaluate(() => !document.querySelector('.navbtn[data-view=habits]') && NativeHabitsShell.available() === false));
  ok('nor do the tab editor, the scrub and the swipe see one', await web.evaluate(() => !lmaTabsButtons().some(x => x.dataset.view === 'habits') && !document.querySelector('#tabsEditBody [data-view=habits]')));
  ok('the view is not blank if something goes there anyway', await web.evaluate(() => { navigate('habits'); return document.querySelector('#view-habits.active h2').textContent === 'Habits' && document.querySelector('#view-habits p').textContent.length > 20 }));
  ok('no script errors in a browser', web.errs.length === 0, web.errs);
  await web.close();

  const p = await page(FAKE);
  ok('in the app Habits is on the bar, after Money, Quests last', await p.evaluate(() => { const v = lmaTabsButtons().map(x => x.dataset.view), i = v.indexOf('habits'); return i > 0 && v[i - 1] === 'money' && v[v.length - 1] === 'quests' && document.querySelector('.navbtn[data-view=habits]').textContent.trim() === 'Habits' }), await p.evaluate(() => lmaTabsButtons().map(x => x.dataset.view)));
  ok('there is no switch for it in Settings, and nothing is stored', await p.evaluate(() => !document.getElementById('nativeHabitsToggle') && lmaLoad().nativeHabits === undefined));
  await p.evaluate(() => navigate('habits')); await p.waitForTimeout(900);
  const opened = await p.evaluate(() => { const c = window.__calls.find(x => x[0] === 'open'); return c && c[1] });
  ok('going to Habits opens the screen, with the bars and the tab\'s name', !!opened && opened.title === 'Habits' && !!opened.navScene && opened.snapshot.protocol === 1 && opened.snapshot.sequence >= 1, opened && Object.keys(opened));
  const s = opened.snapshot;
  ok('the page sends the screen\'s surroundings only: palette, theme, motion, haptics, the class name, whether there is a cat', JSON.stringify(Object.keys(s).sort()) === JSON.stringify(['className', 'haptics', 'light', 'pet', 'protocol', 'reducedMotion', 'sequence', 'tokens', 'v', 'webSessionId']) && s.v === 1 && s.className === 'Athlete' && typeof s.pet === 'boolean' && typeof s.reducedMotion === 'boolean' && typeof s.tokens.bg === 'number' && s.light === false && (s.haptics === null || s.haptics.amp > 0), Object.keys(s));
  const r = await p.evaluate(async () => { await Promise.all(Capacitor.Plugins.NativeHabits.__emit('command', { command: 'lay', payload: { id: 'h1' }, requestId: 'x' })); const u = [...window.__calls].reverse().find(c => c[0] === 'update' && c[1].reply); return u && u[1].reply });
  ok('and runs no command for it', !!r && r.ok === false && r.message === 'Unknown command' && await p.evaluate(() => state.habits === undefined), r);
  const sw = await p.evaluate(async () => { lmaLoad().nativeSwipe = true; lmaSave(); await NativeSwipe.ready?.(); const t = (NativeSwipe.state() || { tabs: [] }).tabs.find(x => x.key === 'habits'); return { t, kind: NativeSwipe.kind('habits') } });
  ok('to a swipe Habits is a native screen that is never a source', sw.kind === 'native' && (!sw.t || (sw.t.source === false && sw.t.icon === 'stack' && sw.t.label === 'Habits')), sw);
  await p.evaluate(() => navigate('character')); await p.waitForTimeout(900);
  ok('leaving the tab closes the screen', await p.evaluate(() => window.__calls.some(c => c[0] === 'close') && !NativeHabitsShell.tab.active()));
  ok('no script errors', p.errs.length === 0, p.errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); srv.close(); process.exit(fail ? 1 : 0);
})();
