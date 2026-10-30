/* Native tab hand-over: the tab you go to opens first, then the one you leave closes, so there is never a moment with no native
   screen when going between native tabs, two bodies never end up showing, and rapid switching always ends on the right tab.
   Fake native plugins with random delays stand in for the Android side. Needs Playwright (not part of the CI node tests).
   Run: NODE_PATH=$(npm root -g) node tests/native-handover.test.js [built html] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(require('os').tmpdir(), 'lm_handover_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
let pass = 0, fail = 0; const ok = (n, c, x) => { if (c) { pass++; console.log('PASS ' + n) } else { fail++; console.log('FAIL ' + n + (x !== undefined ? '  ' + JSON.stringify(x).slice(0, 400) : '')) } };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete'}));
    window.__log=[];window.__vis={overview:false,character:false,food:false};window.__busy=0;
    const rnd=(a,b)=>a+Math.random()*(b-a);const wait=ms=>new Promise(r=>setTimeout(r,ms));
    const L=(n,m)=>window.__log.push([Math.round(performance.now()),n,m,Object.keys(window.__vis).filter(k=>window.__vis[k]).join('+')||'NONE']);
    /* As on the device: a plugin runs its calls in the order they were made, and Overview and Food ignore a close naming a session
       they no longer hold (Character's close is unconditional). */
    const mk=(name,vis,sessions)=>{const ls={};let chain=Promise.resolve(),current='';const emit=(e,d)=>(ls[e]||[]).forEach(f=>{try{f(d)}catch(x){}});
      const inOrder=fn=>{window.__busy++;const r=chain.then(fn);chain=r.catch(()=>{});return r.finally(()=>{window.__busy--})};return {
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:a=>inOrder(async()=>{current=a&&a.session;L(name,'open>');await wait(rnd(10,120));window.__vis[vis]=true;L(name,'open<')}),
      update:async()=>{},
      close:a=>inOrder(async()=>{const stale=sessions&&(!a||a.session!==current);L(name,'close>');await wait(rnd(5,40));if(!stale)window.__vis[vis]=false;L(name,'close<')}).then(()=>emit('closed',{session:a&&a.session})),
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1']})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:()=>true,getPlatform:()=>'android',Plugins:{NativeOverview:mk('overview','overview',true),NativeCharacter:mk('character','character',false),NativeNutrition:mk('food','food',true)},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + out); await p.waitForTimeout(9000);
  const r = await p.evaluate(async () => {
    const wait = ms => new Promise(r => setTimeout(r, ms));
    /* settled = nothing in flight in any plugin and no new call for a quiet spell (a loaded machine delays timers, so no fixed sleep) */
    const settle = async () => { let quiet = 0, last = window.__log.length; for (let i = 0; i < 400 && quiet < 6; i++) { await wait(50); if (window.__busy === 0 && window.__log.length === last) quiet++; else { quiet = 0; last = window.__log.length } } };
    try { lmaThumbWarmBusy = false } catch (e) { }
    const L = lmaLoad(); L.nativeOverview = true; L.nativeCharacter = true; L.nativeNutrition = true; lmaSave();
    navigate('dashboard'); await wait(1500);
    const want = { dashboard: 'overview', character: 'character', meals: 'food' }, problems = [];
    for (const v of ['character', 'meals', 'dashboard', 'character', 'dashboard', 'meals', 'character', 'meals', 'dashboard']) {
      const before = window.__log.length; lmaNavTo(v, { fast: true }); await wait(100); await settle();
      const vis = Object.keys(window.__vis).filter(k => window.__vis[k]);
      if (vis.join() !== want[v]) problems.push({ step: 'settled', to: v, vis });
      const hop = window.__log.slice(before), fo = hop.findIndex(e => e[2] === 'open>');
      if (fo >= 0 && hop[fo][3] !== 'NONE') { const gap = hop.slice(fo).find((e, i, a) => e[3] === 'NONE' && !a.slice(0, i).some(x => x[2] === 'open<')); if (gap) problems.push({ step: 'gap', to: v, gap }) }
    }
    const rapid = ['character', 'meals', 'character', 'dashboard', 'meals', 'character', 'dashboard', 'meals', 'meals', 'character'];
    for (const v of rapid) { lmaNavTo(v, { fast: true }); await wait(25) }
    await wait(100); await settle();
    const vis = Object.keys(window.__vis).filter(k => window.__vis[k]);
    return { problems, rapidFinal: vis.join(), rapidWant: want[rapid[rapid.length - 1]] };
  });
  ok('every settled hop shows exactly the tab you went to', !r.problems.some(x => x.step === 'settled'), r.problems);
  ok('no moment with no native screen while one opens to replace another', !r.problems.some(x => x.step === 'gap'), r.problems);
  ok('rapid switching ends on the last tab with one screen showing', r.rapidFinal === r.rapidWant, r);
  ok('no script errors', errs.length === 0, errs);
  console.log('\n' + pass + ' passed, ' + fail + ' failed'); await b.close(); fs.unlinkSync(out); process.exit(fail ? 1 : 0);
})();
