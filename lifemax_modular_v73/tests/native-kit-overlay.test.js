/* NativeTabKit and what the page puts over a native tab: every kit tab's screen steps aside at once (not at its next refresh)
   when the page shows something over its view, as the launch update prompt does after its network round trip, and a tab whose
   screen comes back by itself (Library, Workout plan) comes back once it is gone. Fake plugins stand in for Android.
   Run: NODE_PATH=$(npm root -g) node tests/native-kit-overlay.test.js [built html] */
const { chromium } = require('playwright');
const page = require('./current-page')(process.argv[2]);
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 900) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const ctx = await b.newContext({ viewport: { width: 412, height: 860 }, locale: 'en-US', timezoneId: 'UTC' });
  const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') }); // a Wednesday in week 1 of the plan below
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',tipsSeen:{media:true},
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    window.__calls=[];
    const mk=(name,caps)=>{const ls={};return {
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:async a=>{window.__calls.push([name,'open'])},
      update:async a=>{window.__calls.push([name,'update'])},
      close:async a=>{window.__calls.push([name,'close']);(ls.closed||[]).forEach(f=>f({}))},
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:caps})}};
    const tabs={NativeCharacter:['character','character.projection.v1'],NativeLog:['log','log.projection.v1'],NativeRecipes:['recipes','recipes.projection.v1'],
      NativeMedia:['media','library.media.projection.v1'],NativePlan:['plan','plan.projection.v1']};
    const plugins={NativeOverview:mk('overview',[])};for(const [n,[id,cap]] of Object.entries(tabs))plugins[n]=mk(id,[cap]);
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:n=>n in plugins,getPlatform:()=>'android',Plugins:plugins,registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + page); await p.waitForTimeout(3000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms)), frame = () => new Promise(r => requestAnimationFrame(() => r()));
    const calls = (name, k) => window.__calls.filter(c => c[0] === name && c[1] === k).length;
    const at = (name, k) => window.__calls.map(c => c[0] + c[1]).lastIndexOf(name + k);
    const showing = name => at(name, 'open') > at(name, 'close');
    const until = async (f, ms = 8000) => { for (let t = 0; t < ms && !f(); t += 50) await wait(50); return f() };
    lmaThumbWarm = async () => { }; try { lmaThumbWarmBusy = false } catch (e) { } // the one-off thumbnail walk visits every tab
    const S = lmaLoad(); Object.assign(S, { nativeOverview: false, nativeCharacter: true, nativeRecipes: true, nativeLibrary: true, nativePlan: true }); lmaSave(); // Log is on by default
    const out = {};
    for (const [view, comesBack] of [['character', false], ['log', false], ['recipes', false], ['media', true], ['plan', true]]) {
      navigate(view);
      const opened = await until(() => showing(view)); // the first capture of the shared bars is slow headless
      await wait(2000); // the settled-bar refresh after an open, and NativeTabs' safety net (heal) after the tab change, are done
      const closes = calls(view, 'close'), pop = document.createElement('div');
      pop.className = 'updpop'; document.body.appendChild(pop); // as lmaUpdPopOpen does, long after the screen opened
      await frame(); await frame();
      const stepped = calls(view, 'close') === closes + 1 && !showing(view);
      pop.remove();
      const back = await until(() => showing(view), 1500);
      out[view] = { opened, stepped, back, comesBack };
      navigate('dashboard'); await until(() => !showing(view));
    }
    // a noscroll body with nothing added (a sheet that was already in the page) is an overlay too
    navigate('recipes'); await until(() => showing('recipes')); await wait(2000);
    document.body.classList.add('noscroll'); await frame(); await frame();
    out.noscroll = !showing('recipes');
    document.body.classList.remove('noscroll');
    return out;
  });
  for (const [view, x] of Object.entries(r).filter(([k]) => k !== 'noscroll')) {
    ok(view + ': the screen opens, steps aside within a frame of the update prompt appearing, and ' + (x.comesBack ? 'comes back once it is gone' : 'stays closed until the next visit'),
      x.opened && x.stepped && x.back === x.comesBack, x);
  }
  ok('a body that turns noscroll without a new child also puts the screen aside', r.noscroll, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); process.exit(fail ? 1 : 0);
})();
