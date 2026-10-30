/* Hand-over simulation (CONTRACT A23, A28). Fake native plugins with seeded random delays stand in for Android, the page runs on
   virtual time (Playwright's clock) so a run is exactly repeatable, and many seeded tab-hopping sequences are replayed. After each
   sequence settles, exactly the screen of the last tab must be showing, and no tab may ever have been left with nothing while another
   opened. A failing seed prints its sequence so it can be replayed.
   Needs Playwright (not part of the CI node tests).
   Run: NODE_PATH=$(npm root -g) node tests/native-handover-sim.test.js [seeds=80] */
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path'); const os = require('os');
const { execFileSync } = require('child_process');
const root = path.resolve(__dirname, '..'), out = path.join(os.tmpdir(), 'lm_handover_sim_' + process.pid + '.html');
execFileSync('python3', [path.join(root, 'build.py')], { cwd: root, stdio: 'ignore' });
execFileSync('python3', [path.join(root, 'forge_features.py'), path.join(root, 'dist', 'Life_Max_RPG_v73_modular_build.html'), out], { cwd: root, stdio: 'ignore' });
const SEEDS = Number(process.argv[2]) || 80;
const WANT = { dashboard: 'overview', character: 'character', meals: 'food', plan: 'plan' };
(async () => {
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await (await b.newContext({ viewport: { width: 412, height: 860 } })).newPage(); const errs = []; p.on('pageerror', e => errs.push(String(e)));
  await p.clock.install({ time: new Date('2026-10-28T12:00:00Z') });
  await p.addInitScript(`
    localStorage.setItem('lifemax_rpg_prototype_v1', JSON.stringify({onboardingComplete:true,name:'N',tutorialDone:true,classId:'athlete',
      plan:{id:'athlete_foundation8',classId:'athlete',start:'2026-10-26',from:'2026-10-26',checks:{}}}));
    window.__seed=1;const rnd=()=>{let t=window.__seed+=0x6D2B79F5;t=Math.imul(t^t>>>15,t|1);t^=t+Math.imul(t^t>>>7,t|61);return((t^t>>>14)>>>0)/4294967296};
    window.__rnd=(a,b)=>a+rnd()*(b-a);window.__vis={overview:false,character:false,food:false,plan:false};window.__busy=0;window.__log=[];window.__t=()=>Math.round(performance.now());
    const wait=ms=>new Promise(r=>setTimeout(r,ms));
    /* Capacitor hands a plugin's calls to the main thread in the order they were made, so each fake does its calls one after another */
    /* Overview and Food only hide on a close that names the session they currently hold (as the Kotlin plugins do); the close of Character
       and Plan (ComposeTabPlugin) is unconditional */
    const mk=(name,vis,sessions)=>{const ls={};let current='';let chain=Promise.resolve();const inOrder=fn=>{window.__busy++;const r=chain.then(fn);chain=r.catch(()=>{});return r.finally(()=>{window.__busy--})};const emit=(e,d)=>(ls[e]||[]).forEach(f=>{try{f(d)}catch(x){}});return {
      addListener:async(e,f)=>{(ls[e]=ls[e]||[]).push(f);return {remove(){}}},
      open:a=>inOrder(async()=>{current=a&&a.session;window.__log.push(window.__t()+' '+name+' open>');await wait(window.__rnd(10,120));window.__vis[vis]=true;window.__log.push(window.__t()+' '+name+' open<')}),
      update:async()=>{},
      close:a=>inOrder(async()=>{const stale=sessions&&(!a||a.session!==current);window.__log.push(window.__t()+' '+name+' close>'+(stale?' (stale session, ignored)':''));await wait(window.__rnd(5,40));if(!stale)window.__vis[vis]=false;window.__log.push(window.__t()+' '+name+' close<')}).then(()=>emit('closed',{session:a&&a.session})),
      capabilities:async()=>({liveWeb:true,version:1,authoritativeData:true}),
      handshake:async()=>({protocol:1,capabilities:['character.projection.v1','plan.projection.v1']})}};
    window.Capacitor={isNativePlatform:()=>true,isPluginAvailable:()=>true,getPlatform:()=>'android',Plugins:{NativeOverview:mk('overview','overview',true),NativeCharacter:mk('character','character',false),NativeNutrition:mk('food','food',true),NativePlan:mk('plan','plan',false)},registerPlugin:()=>({}),convertFileSrc:u=>u};
  `);
  await p.goto('file://' + out);
  for (let i = 0; i < 40; i++) { await p.clock.runFor(500); await p.waitForTimeout(60); }
  if (process.env.TRACE) await p.evaluate(() => { const T = window.NativeTabs, L = m => window.__log.push(window.__t() + ' ' + m); for (const k of ['leave', 'cancel', 'flush']) { const f = T[k]; T[k] = function () { const a = [...arguments].filter(x => typeof x !== 'function'); L('   NT.' + k + '(' + a.join(',') + ')' + (k === 'leave' ? ' covered=' + T.covered(a[1]) : '')); return f.apply(this, arguments) } } });
  await p.evaluate(() => { try { lmaThumbWarmBusy = false } catch (e) { } const L = lmaLoad(); L.nativeOverview = true; L.nativeCharacter = true; L.nativeNutrition = true; L.nativePlan = true; lmaSave(); navigate('dashboard') });
  const settle = async (ms) => { for (let t = 0; t < ms; t += 50) { await p.clock.runFor(50); if (t % 400 === 0) await p.waitForTimeout(5) } await p.waitForTimeout(20) };
  await settle(4000);
  const failures = []; let hops = 0;
  const rng = (s => () => { let t = s += 0x6D2B79F5; t = Math.imul(t ^ t >>> 15, t | 1); t ^= t + Math.imul(t ^ t >>> 7, t | 61); return ((t ^ t >>> 14) >>> 0) / 4294967296 })(Number(process.env.SEED_BASE) || 12345);
  for (let seed = 1; seed <= SEEDS; seed++) {
    await p.evaluate(s => { window.__seed = s; window.__log = [] }, seed + (Number(process.env.SEED_BASE) || 0));
    const tabs = Object.keys(WANT), n = 3 + Math.floor(rng() * 9), seq = [];
    for (let i = 0; i < n; i++) seq.push({ to: tabs[Math.floor(rng() * tabs.length)], gap: Math.floor(rng() * 4) * 25 });
    for (const step of seq) { await p.evaluate(v => { window.__log.push(window.__t() + ' >> go ' + v); lmaNavTo(v, { fast: true }) }, step.to); if (step.gap) await settle(step.gap); hops++ }
    await settle(4500);
    const r = await p.evaluate(() => ({ vis: Object.keys(window.__vis).filter(k => window.__vis[k]).join('+'), busy: window.__busy, view: lmaView(), log: window.__log }));
    const last = seq[seq.length - 1].to;
    if (r.vis !== WANT[last] || r.busy !== 0) failures.push({ seed, seq: seq.map(s => s.to + '+' + s.gap).join(' '), showing: r.vis || 'NONE', want: WANT[last], busy: r.busy, view: r.view, log: r.log });
  }
  console.log((failures.length ? 'FAIL' : 'PASS') + ' ' + SEEDS + ' seeded sequences (' + hops + ' hops): the last tab alone is showing after each');
  if (process.env.TRACE) failures.slice(0, 2).forEach(f => console.log('  trace seed ' + f.seed + ':\n    ' + f.log.join('\n    ')));
  failures.slice(0, 6).forEach(f => console.log('  seed ' + f.seed + ': ' + f.seq + ' -> showing ' + f.showing + ', wanted ' + f.want + (f.busy ? ' (busy ' + f.busy + ')' : '') + ' view=' + f.view));
  if (errs.length) console.log('FAIL script errors', errs.slice(0, 3));
  await b.close(); fs.unlinkSync(out); process.exit(failures.length || errs.length ? 1 : 0);
})();
